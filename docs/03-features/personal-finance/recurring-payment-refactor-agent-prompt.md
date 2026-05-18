# Agent Prompt — Refactor: Recurring Payment Reminders v1 → v2

## O que você vai fazer

Refatorar a implementação existente do feature "Recurring Payment Reminders" conforme as decisões de design descritas abaixo. O código v1 já existe no branch atual. Seu trabalho é aplicar as mudanças listadas, sem adicionar nada além do que está especificado.

O spec completo está em:
- `docs/03-features/personal-finance/recurring-payment-reminders.md`
- `docs/03-features/personal-finance/recurring-payment-refactor-prompt.md`

Este prompt é autossuficiente — use os docs como referência de detalhe, mas as instruções aqui são a fonte de verdade.

---

## Decisões de design (leia antes de tocar no código)

### 1. O template pertence ao `account` (tenant), não à `financialAccount`

`RecurringPayment` representa uma obrigação recorrente (ex: Comgás, aluguel, Netflix). Ela não pertence a uma conta bancária específica — o usuário pode pagar de qualquer conta ou cartão a cada mês.

A entidade de referência é `account_id` (o tenant/workspace do usuário), que já existe no modelo e é obrigatório. `financial_account_id` passa a ser um campo opcional de dica ("costumo pagar por essa conta").

```
account (tenant)
  └── recurring_payments   ← associado por account_id (obrigatório)
        └── financialAccountId?  ← dica opcional de conta preferida
        └── transactions         ← link feito na transação via recurring_payment_id
              └── financialAccountId  ← onde o pagamento realmente caiu
```

Consequência direta: o matching busca por `findAllByAccountId` — varre todos os templates do tenant, independente de qual conta foi usada para pagar.

### 2. Sem auto-link — apenas sugestão com confirmação do usuário

O v1 implementou `tryAutoLink` no `TransactionService` que linkava silenciosamente ao criar/editar uma transação. Isso é problemático: dois templates com valores similares e datas próximas na mesma conta causam falso match.

O v2 substitui por um sistema de sugestão on-demand:
- `RecurringMatchingService.findBestMatch(transaction)` é chamado pelo resolver GraphQL quando o usuário abre o menu da transação.
- Se a pontuação atingir o threshold, uma opção `🔁 Vincular a "Comgás"?` aparece no menu.
- O usuário clica → `linkTransactionToRecurring` mutation → confirmado.

### 3. Score composto com threshold booleano parametrizado

O matching usa quatro sinais com pesos:

| Sinal | Peso | Detalhe |
|---|---|---|
| Descrição | 0.55 | Substring ou interseção de tokens após normalização (lowercase, sem acentos, sem pontuação) |
| Merchant | 0.25 | Igualdade exata de `merchantId` |
| Valor ±15% | até 0.20 | Proporcional à proximidade; 0 se variável |
| Valor variável | 0.10 | Crédito fixo quando `expectedAmount` é null |

- **Threshold:** configurável via `banking.recurring.suggestion-threshold` (default `0.80`). Boolean: ou sugere ou não.
- **Tie-break:** quando múltiplos candidatos atingem o threshold, retorna o com `nextDueDate` mais próximo de `transaction.date`.
- **Date gate:** `|txDate − nextDueDate| > banking.recurring.date-gate-days` (default `10`) → score = 0. **Exceto** quando o template está vencido (`nextDueDate < today`) — ver item 5.

### 4. FUTURE transaction projection removida

`RecurringFutureTransactionJob` não tem consumidor frontend (`projectedBalance` não existe na UI). O job deve ser deletado. Os métodos de repositório exclusivos a ele (`upsertFutureRecurring`) também são removidos.

### 5. Pagamento atrasado — gate de data dispensado para templates vencidos

Se o usuário não pagou em maio e paga em junho:
- Template: `nextDueDate = 2026-05-10`
- Transação: `date = 2026-06-10`
- `|Jun 10 − Mai 10| = 31 dias` → normalmente bloquearia pelo date gate

Regra: quando `template.nextDueDate < today` (template vencido), o gate de data é dispensado. A única restrição é que `transaction.date >= template.nextDueDate`. Description + amount score ainda se aplicam.

`confirmPayment` já tem while loop que avança múltiplos ciclos:
```
nextDueDate = Mai 10, paymentDate = Jun 10
while (!next.isAfter(Jun 10)) → avança: Mai 10 → Jun 10 → (saiu) → Jul 10
```
O template fica com `nextDueDate = Jul 10`.

### 6. Lembretes continuam mesmo após vencimento

`existsOpenForEntity` atualmente verifica apenas `status = 'PENDING'` — não respeita `expires_at`. Resultado: quando o `PendingAction` expira (7 dias após vencimento), o job não cria novo lembrete porque a action antiga ainda existe como PENDING no banco.

Correção: adicionar `AND (expires_at IS NULL OR expires_at > NOW())` à query.

---

## Mudanças por arquivo

### DELETAR

- `job/transaction/RecurringFutureTransactionJob.kt`

---

### CRIAR

#### `domain/transaction/RecurringMatchingService.kt`

```kotlin
package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate

@Singleton
class RecurringMatchingService(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    @Value("\${banking.recurring.suggestion-threshold:0.80}")
    private val threshold: Float,
    @Value("\${banking.recurring.date-gate-days:10}")
    private val dateGateDays: Long,
) {
    fun findBestMatch(transaction: Transaction): RecurringPayment? {
        if (transaction.status != TransactionStatus.POSTED) return null
        if (transaction.type == TransactionType.BALANCE_ADJUSTMENT) return null
        if (transaction.recurringPaymentId != null) return null

        val candidates = recurringPaymentRepository.findAllByAccountId(transaction.accountId)
            .filter { it.status == RecurringPaymentStatus.ACTIVE && it.deletedAt == null }

        return candidates
            .asSequence()
            .map { it to score(transaction, it) }
            .filter { (_, s) -> s >= threshold }
            .minByOrNull { (template, _) ->
                Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())
            }
            ?.first
    }

    internal fun score(transaction: Transaction, template: RecurringPayment): Float {
        val today = LocalDate.now()
        val isPastDue = template.nextDueDate.isBefore(today)
        val dateDelta = Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())

        if (!isPastDue && dateDelta > dateGateDays) return 0f
        if (isPastDue && transaction.date.isBefore(template.nextDueDate)) return 0f

        var s = 0f
        s += descriptionScore(template.name, transaction.description) * 0.55f
        if (template.merchantId != null && template.merchantId == transaction.merchantId) s += 0.25f

        val expected = template.expectedAmount
        if (expected == null) {
            s += 0.10f
        } else if (expected.compareTo(BigDecimal.ZERO) != 0) {
            val pct = transaction.amount.subtract(expected).abs()
                .divide(expected.abs(), 6, RoundingMode.HALF_UP)
            if (pct <= BigDecimal("0.15")) s += 0.20f * (1f - pct.toFloat())
        }

        return s.coerceIn(0f, 1f)
    }

    internal fun descriptionScore(templateName: String, txDescription: String): Float {
        val normTemplate = normalize(templateName)
        val normTx = normalize(txDescription)
        if (normTx.contains(normTemplate)) return 1.0f
        val tTokens = normTemplate.split(" ").filter { it.length >= 3 }.toSet()
        val dTokens = normTx.split(" ").filter { it.length >= 3 }.toSet()
        val hits = tTokens.intersect(dTokens)
        if (hits.isNotEmpty()) return hits.size.toFloat() / tTokens.size.toFloat()
        return 0f
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
```

#### `test/.../RecurringMatchingServiceTest.kt`

Cobrir obrigatoriamente:
- `descriptionScore("Comgas", "Transferência comgás")` → 1.0
- `descriptionScore("Netflix", "NETFLIX.COM *BR")` → 1.0
- `descriptionScore("Conta de Luz", "ENEL SP PAGAMENTO")` → 0.0
- Score só com descrição (0.55) → abaixo de 0.80 → `findBestMatch` retorna null
- Score com descrição + merchant (0.55 + 0.25 = 0.80) → no threshold → retorna template
- Template com `expectedAmount = null` + descrição → 0.65 → null
- Template vencido: `txDate = today`, `nextDueDate = today - 31` → gate dispensado → sugere se descrição bater
- Template vencido: `txDate < nextDueDate` → score = 0 → null
- Tie-break: dois candidatos acima do threshold → retorna o de `nextDueDate` mais próximo de `txDate`

#### `test/.../RecurringPaymentServiceTest.kt`

Cobrir obrigatoriamente:
- `computeNextDueDate` MONTHLY dia 31 → Jan 31 → Fev 28/29
- `computeNextDueDate` MONTHLY dia 31 → Mar 31 → Abr 30
- `computeNextDueDate` WEEKLY
- `computeNextDueDate` CUSTOM intervalDays=14
- `confirmPayment` pagamento no dia exato → avança um ciclo
- `confirmPayment` 1 mês atrasado (nextDueDate=Mai/10, paymentDate=Jun/15) → nextDueDate = Jul/10

---

### MODIFICAR (backend)

#### `db/migration/V0_20__create_recurring_payments.sql`
- `financial_account_id UUID REFERENCES ...` — remover `NOT NULL`
- Índice de `financial_account_id`: adicionar `WHERE financial_account_id IS NOT NULL`
- Remover `CREATE UNIQUE INDEX uq_transactions_future_recurring` (era para FUTURE projection)

#### `domain/transaction/RecurringPayment.kt`
- `financialAccountId: UUID?` (era `UUID`)

#### `model/transaction/RecurringPaymentEntity.kt`
- `financialAccountId: UUID?` (era `UUID`)
- Remover `nullable = false` da `@Column` se existir

#### `model/transaction/RecurringPaymentMapper.kt`
- Garantir que compila com `UUID?` nos três métodos (`toDomain`, `toEntity`, `updateEntity`)

#### `domain/transaction/RecurringPaymentService.kt`
- Remover método `tryAutoLink` inteiro e imports órfãos

#### `service/transaction/TransactionService.kt`
- Remover do construtor: `recurringPaymentRepository`, `recurringPaymentService`
- Remover chamada ao auto-link em `create` e `update`
- Remover método privado de auto-link
- Remover imports órfãos

#### `repo/transaction/TransactionRepository.kt` e `DefaultTransactionRepository.kt`
- Remover `upsertFutureRecurring`
- Manter `deleteFutureByRecurringPaymentId` e `deleteFutureByRecurringAndDate` (usados em pause/cancel e link)

#### `repo/pendingaction/AccountPendingActionEntityRepository.kt`
- Em `existsOpenForEntity`, adicionar condição:
  ```sql
  AND (p.expires_at IS NULL OR p.expires_at > NOW())
  ```

#### `graphql/recurring/RecurringPaymentResolver.kt`
- Injetar `RecurringMatchingService` no construtor
- Adicionar método:
  ```kotlin
  fun suggestRecurringForTransaction(transactionId: String, principal: RequestPrincipal): RecurringPaymentDTO? {
      val accountId = requireAccountId(principal)
      val transaction = transactionRepository.findById(UUID.fromString(transactionId), accountId) ?: return null
      return recurringMatchingService.findBestMatch(transaction)?.toDto(accountId)
  }
  ```
- Em `pauseRecurringPayment`: após atualizar o status, chamar `transactionRepository.deleteFutureByRecurringPaymentId(existing.id!!)`
- Em `cancelRecurringPayment`: idem

#### `graphql/config/BankingWiringFactory.kt`
- Wire `suggestRecurringForTransaction` seguindo o padrão dos outros campos do resolver

#### `resources/graphql/schema.graphqls`
- `RecurringPayment.financialAccountId: ID` (remover `!`)
- `CreateRecurringPaymentInput.financialAccountId: ID` (remover `!`)
- Adicionar na seção Query:
  ```graphql
  suggestRecurringForTransaction(transactionId: ID!): RecurringPayment
  ```

---

### MODIFICAR (frontend)

#### `operations/banking/recurringPaymentOperations.ts`
Adicionar:
```ts
export const SUGGEST_RECURRING_FOR_TRANSACTION = gql`
  query SuggestRecurringForTransaction($transactionId: ID!) {
    suggestRecurringForTransaction(transactionId: $transactionId) {
      id
      name
      nextDueDate
      expectedAmount
      currencyCode
    }
  }
`;
```

#### `transaction/hooks/useActionMenu.ts`
- Quando `activeActionMenuTransactionId` muda para um valor não-null, fazer lazy query `SUGGEST_RECURRING_FOR_TRANSACTION`
- Armazenar resultado em estado local `recurringPaymentSuggestion`
- Quando o usuário confirma o link: chamar `linkTransactionToRecurring(transactionId, recurringPaymentId)` + refetch

#### `transaction/TransactionActionMenu.tsx`
Adicionar props:
```tsx
recurringPaymentSuggestion?: { id: string; name: string; nextDueDate: string } | null;
onLinkRecurringPayment?: (tx: Transaction, recurringPaymentId: string) => void;
```

Antes do item "Criar pagamento recorrente", quando `recurringPaymentSuggestion` não for null:
```tsx
{!isBalanceAdjustment && !isTransfer && recurringPaymentSuggestion && (
  <MenuItem
    onClick={() => { if (!transaction) return; onClose(); onLinkRecurringPayment?.(transaction, recurringPaymentSuggestion.id); }}
    data-testid="transaction-action-link-recurring-suggestion"
    sx={{ color: 'primary.main' }}
  >
    <ListItemIcon><RepeatIcon fontSize="small" color="primary" /></ListItemIcon>
    <ListItemText
      primary={t('recurringPayments.suggestion.link', { name: recurringPaymentSuggestion.name })}
      secondary={t('recurringPayments.suggestion.nextDue', { date: recurringPaymentSuggestion.nextDueDate })}
    />
  </MenuItem>
)}
```

#### `recurring-payments/RecurringPaymentForm.tsx`
- Campo `financialAccountId` não é `required`
- Label: `"Conta de pagamento (opcional)"`
- Se vazio ao submeter, enviar `financialAccountId: null` na mutation

#### `investments/PendingActionsDrawer.tsx`
Substituir comparação de string:
```tsx
// Antes
if (rawType === "RECURRING_PAYMENT_DUE") ...

// Depois
import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
if (rowType === PendingActionType.RecurringPaymentDue) ...
```
Verificar que o codegen foi rodado após o backend adicionar o novo enum.

#### `i18n/locales/pt.json` e `en.json`
Adicionar em `recurringPayments`:
```json
"suggestion": {
  "link": "Vincular a \"{{name}}\"",
  "nextDue": "Vence em {{date}}"
}
```

---

## O que NÃO fazer

- Não implementar auto-link de nenhum tipo em `TransactionService`
- Não criar `RecurringFutureTransactionJob`
- Não criar o índice `uq_transactions_future_recurring`
- Não adicionar `upsertFutureRecurring`
- Não tornar `financialAccountId` obrigatório em nenhum lugar
- Não modificar a lógica de `RecurringReminderJob` além do fix de `existsOpenForEntity`
- Não adicionar feature flags ou backwards-compat shims

## Ordem sugerida de execução

1. Migration SQL
2. Domain model + enums (compilação)
3. Entity + Mapper
4. `RecurringMatchingService` (novo)
5. Remover `tryAutoLink` do `RecurringPaymentService`
6. Remover auto-link do `TransactionService`
7. Remover `RecurringFutureTransactionJob` + `upsertFutureRecurring`
8. Fix `existsOpenForEntity`
9. Resolver + BankingWiringFactory + schema.graphqls
10. Frontend (operations → hooks → components → i18n)
11. Testes unitários
