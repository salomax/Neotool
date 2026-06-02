# Refactor Prompt — Recurring Payment Reminders (v1 → v2)

## Context

A v1 do feature "Recurring Payment Reminders" foi implementada mas precisa de refatoração em pontos críticos antes de ir para produção. O design spec atualizado está em `docs/03-features/personal-finance/recurring-payment-reminders.md`.

Este prompt descreve **exatamente o que mudar**, arquivo por arquivo. Não implemente nada além do que está descrito aqui.

---

## 1. Migration — financialAccountId nullable

**Arquivo:** `service/kotlin/banking/src/main/resources/db/migration/V0_20__create_recurring_payments.sql`

Mude a coluna `financial_account_id` de:
```sql
financial_account_id UUID NOT NULL REFERENCES banking.financial_accounts(id),
```
para:
```sql
financial_account_id UUID REFERENCES banking.financial_accounts(id),  -- nullable
```

Mude o índice de `financial_account_id` para incluir filtro `WHERE financial_account_id IS NOT NULL`.

Remova o `CREATE UNIQUE INDEX uq_transactions_future_recurring` — não é mais necessário (FUTURE projection removida).

---

## 2. Domain model — financialAccountId nullable

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPayment.kt`

Mude:
```kotlin
val financialAccountId: UUID,
```
para:
```kotlin
val financialAccountId: UUID?,
```

---

## 3. Entity — financialAccountId nullable

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/RecurringPaymentEntity.kt`

Mude o campo:
```kotlin
open var financialAccountId: UUID,
```
para:
```kotlin
open var financialAccountId: UUID?,
```
e remova o `nullable = false` da annotation `@Column` se existir.

---

## 4. Mapper — financialAccountId nullable

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/RecurringPaymentMapper.kt`

`financialAccountId` no `toEntity` e `updateEntity` deve aceitar `null` (sem mudança no código se já usa `domain.financialAccountId` diretamente — apenas garante que compila com `UUID?`).

---

## 5. Remover auto-link do TransactionService

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt`

Remova:
- O campo `recurringPaymentRepository: RecurringPaymentRepository` do construtor
- O campo `recurringPaymentService: RecurringPaymentService` do construtor
- A chamada `tryAutoLinkRecurring(saved)` (ou equivalente) em `create` e `update`
- O método privado `tryAutoLinkRecurring` inteiro
- Os imports relacionados que ficarem órfãos

O `pendingActionRepository` pode permanecer se ainda for usado para outros fins; caso contrário, remova também.

---

## 6. Remover tryAutoLink do RecurringPaymentService

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPaymentService.kt`

Remova o método `tryAutoLink` inteiro e seus imports associados (`BigDecimal`, `RoundingMode`, `abs`, etc.) que ficarem órfãos.

Mantenha: `computeNextDueDate`, `confirmPayment`, `upcomingDates`.

---

## 7. Criar RecurringMatchingService

**Arquivo novo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringMatchingService.kt`

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
    /**
     * Returns the best-matching ACTIVE template for the given transaction,
     * or null if no candidate scores at or above [threshold].
     *
     * Matching is on-demand only — never called automatically on transaction write.
     * The caller (GraphQL resolver) presents the result to the user for confirmation.
     */
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

        // Date gate — only waived for past-due templates
        if (!isPastDue && dateDelta > dateGateDays) return 0f
        // Past-due: transaction must be on or after nextDueDate
        if (isPastDue && transaction.date.isBefore(template.nextDueDate)) return 0f

        var s = 0f

        s += descriptionScore(template.name, transaction.description) * 0.55f

        if (template.merchantId != null && template.merchantId == transaction.merchantId)
            s += 0.25f

        val expected = template.expectedAmount
        if (expected == null) {
            s += 0.10f
        } else if (expected.compareTo(BigDecimal.ZERO) != 0) {
            val pct = transaction.amount.subtract(expected).abs()
                .divide(expected.abs(), 6, RoundingMode.HALF_UP)
            if (pct <= BigDecimal("0.15"))
                s += 0.20f * (1f - pct.toFloat())
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

---

## 8. Corrigir existsOpenForEntity — respeitar expires_at

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/pendingaction/AccountPendingActionEntityRepository.kt`

Altere a query `existsOpenForEntity`:

```kotlin
@Query(
    """
    SELECT COUNT(*) > 0
      FROM banking.account_pending_actions p
     WHERE p.entity_type = :entityType
       AND p.entity_id   = CAST(:entityId AS uuid)
       AND p.type        = :type
       AND p.status      = 'PENDING'
       AND (p.expires_at IS NULL OR p.expires_at > NOW())
    """,
    nativeQuery = true,
)
fun existsOpenForEntity(entityType: String, entityId: UUID, type: String): Boolean
```

---

## 9. Corrigir pause/cancel — deletar FUTURE transactions

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/recurring/RecurringPaymentResolver.kt`

Nos métodos `pauseRecurringPayment` e `cancelRecurringPayment`, após atualizar o status do template, adicione:

```kotlin
transactionRepository.deleteFutureByRecurringPaymentId(existing.id!!)
```

O `transactionRepository` já é injetado no resolver.

---

## 10. Adicionar GraphQL query: suggestRecurringForTransaction

### 10a. Schema

**Arquivo:** `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`

Na seção de `RecurringPayment`, mude `financialAccountId: ID!` para `financialAccountId: ID`.

No tipo `Query`, adicione:
```graphql
suggestRecurringForTransaction(transactionId: ID!): RecurringPayment
```

### 10b. Resolver

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/recurring/RecurringPaymentResolver.kt`

Injete `RecurringMatchingService` no construtor.

Adicione o método:
```kotlin
fun suggestRecurringForTransaction(
    transactionId: String,
    principal: RequestPrincipal,
): RecurringPaymentDTO? {
    val accountId = requireAccountId(principal)
    val transaction = transactionRepository.findById(UUID.fromString(transactionId), accountId)
        ?: return null
    return recurringMatchingService.findBestMatch(transaction)?.toDto(accountId)
}
```

### 10c. BankingWiringFactory

**Arquivo:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt`

Wire o novo campo `suggestRecurringForTransaction` na seção de queries, seguindo o padrão existente dos outros campos do resolver.

---

## 11. Remover RecurringFutureTransactionJob

**Arquivo a deletar:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/RecurringFutureTransactionJob.kt`

Delete o arquivo inteiro. O FUTURE projection não tem consumidor frontend.

Os métodos de repositório relacionados podem ser mantidos (são usados pelo pause/cancel via `deleteFutureByRecurringPaymentId`) ou removidos os que só serviam ao job (`upsertFutureRecurring`).

**Remover `upsertFutureRecurring` de:**
- `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/TransactionRepository.kt`
- `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt`

Manter `deleteFutureByRecurringPaymentId` e `deleteFutureByRecurringAndDate` (usados no pause/cancel e no `linkTransactionToRecurring`).

---

## 12. Frontend — financialAccountId opcional no form

**Arquivo:** `web/src/app/personal-finance/recurring-payments/RecurringPaymentForm.tsx`

O campo de conta (`financialAccountId`) não deve ser `required`. Adicione label "Conta de pagamento (opcional)". O valor pode ser string vazia ao submeter — neste caso enviar `null` na mutation (não enviar o campo ou enviar explicitamente `null`).

Verifique o input `CreateRecurringPaymentInput` no schema — `financialAccountId` agora é `ID` (nullable).

---

## 13. Frontend — sugestão no TransactionActionMenu

**Arquivo:** `web/src/app/personal-finance/finance-transactions/transaction/TransactionActionMenu.tsx`

Adicione prop `recurringPaymentSuggestion?: { id: string; name: string; nextDueDate: string } | null` à interface do componente.

Adicione prop `onLinkRecurringPayment?: (tx: Transaction, recurringPaymentId: string) => void`.

No JSX, antes do item "Criar pagamento recorrente", quando `recurringPaymentSuggestion` não for null:

```tsx
{!isBalanceAdjustment && !isTransfer && recurringPaymentSuggestion && (
  <MenuItem
    onClick={() => {
      if (!transaction) return;
      onClose();
      onLinkRecurringPayment?.(transaction, recurringPaymentSuggestion.id);
    }}
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

---

## 14. Frontend — carregar sugestão no useActionMenu

**Arquivo:** `web/src/app/personal-finance/finance-transactions/transaction/hooks/useActionMenu.ts`

Quando o menu abre para uma transação (`activeActionMenuTransactionId` muda), fazer lazy query `suggestRecurringForTransaction` com o `transactionId`. Armazenar o resultado em estado local `recurringPaymentSuggestion`. Passar para `TransactionActionMenu` via prop.

Quando o usuário clica em "Vincular a X":
1. Chamar mutation `linkTransactionToRecurring(transactionId, recurringPaymentId)`
2. Chamar `refetch` das transações

Use o operation `SUGGEST_RECURRING_FOR_TRANSACTION` do arquivo `recurringPaymentOperations.ts`.

**Adicionar ao `recurringPaymentOperations.ts`:**
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

---

## 15. Frontend — PendingActionsDrawer, usar enum gerado

**Arquivo:** `web/src/app/personal-finance/investments/PendingActionsDrawer.tsx`

Substitua qualquer comparação `rawType === "RECURRING_PAYMENT_DUE"` ou `String(rowType) === "RECURRING_PAYMENT_DUE"` por:

```tsx
import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
// ...
rowType === PendingActionType.RecurringPaymentDue
```

Confirme que o codegen foi rodado após o backend adicionar `RECURRING_PAYMENT_DUE` ao enum.

---

## 16. i18n — adicionar chaves de sugestão

**Arquivos:** `web/src/app/personal-finance/i18n/locales/pt.json` e `en.json`

Adicionar em `recurringPayments`:
```json
"suggestion": {
  "link": "Vincular a \"{{name}}\"",
  "nextDue": "Vence em {{date}}"
},
"variableAmountBadge": "Valor variável"
```

---

## 17. Testes unitários a criar

### RecurringMatchingServiceTest

**Arquivo:** `service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringMatchingServiceTest.kt`

Casos obrigatórios:
- `descriptionScore("Comgas", "Transferência comgás")` = 1.0 (substring após normalização)
- `descriptionScore("Netflix", "NETFLIX.COM *BR")` = 1.0 (substring após remover pontuação)
- `descriptionScore("Conta de Luz", "ENEL SP PAGAMENTO")` = 0.0 (sem overlap)
- `score`: apenas descrição match = 0.55 → abaixo de threshold → `findBestMatch` retorna null
- `score`: descrição + merchant = 0.80 → exatamente no threshold → retorna o template
- `score`: template com `expectedAmount = null` + descrição match = 0.65 → abaixo de threshold → null
- Pagamento atrasado: `nextDueDate = today - 31 days`, `txDate = today` → gate waivado, descrição match → sugere
- Pagamento atrasado: `txDate < template.nextDueDate` → score = 0f → null
- Tie-break: dois candidatos acima do threshold → retorna o de `nextDueDate` mais próximo

### RecurringPaymentServiceTest

**Arquivo:** `service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPaymentServiceTest.kt`

Casos obrigatórios:
- `computeNextDueDate` MONTHLY dia 31: Jan 31 → Feb 28 (ou 29 em ano bissexto)
- `computeNextDueDate` MONTHLY dia 31: Mar 31 → Apr 30
- `computeNextDueDate` WEEKLY: avança 7 dias
- `computeNextDueDate` CUSTOM intervalDays=14: avança 14 dias
- `confirmPayment`: pagamento no dia exato → avança um ciclo
- `confirmPayment`: pagamento 1 mês atrasado (ex: nextDueDate=Mai/10, pagamento Jun/15) → avança dois ciclos (nextDueDate = Jul/10)

---

## Checklist de arquivos

### Deletar
- [ ] `job/transaction/RecurringFutureTransactionJob.kt`

### Modificar (backend)
- [ ] `db/migration/V0_20__create_recurring_payments.sql` — financial_account_id nullable, remover uq_future index
- [ ] `domain/transaction/RecurringPayment.kt` — financialAccountId: UUID?
- [ ] `domain/transaction/RecurringPaymentService.kt` — remover tryAutoLink
- [ ] `model/transaction/RecurringPaymentEntity.kt` — financialAccountId: UUID?
- [ ] `model/transaction/RecurringPaymentMapper.kt` — garantir compilação com UUID?
- [ ] `repo/pendingaction/AccountPendingActionEntityRepository.kt` — existsOpenForEntity + expires_at
- [ ] `repo/transaction/TransactionRepository.kt` — remover upsertFutureRecurring
- [ ] `repo/transaction/DefaultTransactionRepository.kt` — remover upsertFutureRecurring
- [ ] `service/transaction/TransactionService.kt` — remover auto-link e dependências
- [ ] `graphql/recurring/RecurringPaymentResolver.kt` — injetar RecurringMatchingService, adicionar suggestRecurringForTransaction, fix pause/cancel
- [ ] `graphql/config/BankingWiringFactory.kt` — wire suggestRecurringForTransaction
- [ ] `resources/graphql/schema.graphqls` — financialAccountId nullable, adicionar query

### Criar (backend)
- [ ] `domain/transaction/RecurringMatchingService.kt`
- [ ] `test/.../RecurringMatchingServiceTest.kt`
- [ ] `test/.../RecurringPaymentServiceTest.kt`

### Modificar (frontend)
- [ ] `recurring-payments/RecurringPaymentForm.tsx` — financialAccountId opcional
- [ ] `transaction/TransactionActionMenu.tsx` — prop suggestion + item inline
- [ ] `transaction/hooks/useActionMenu.ts` — lazy query + link handler
- [ ] `investments/PendingActionsDrawer.tsx` — usar PendingActionType enum gerado
- [ ] `operations/banking/recurringPaymentOperations.ts` — adicionar SUGGEST_RECURRING_FOR_TRANSACTION
- [ ] `i18n/locales/pt.json` + `en.json` — chaves de sugestão
