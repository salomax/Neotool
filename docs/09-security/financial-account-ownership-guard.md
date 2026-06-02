# Financial Account Ownership Guard

## Problema

Durante testes, identificou-se que nem todos os serviços que recebem `financialAccountId` como
parâmetro validam se aquela conta financeira pertence ao `accountId` (tenant) do usuário
autenticado. Isso representa uma vulnerabilidade de acesso a dados cross-tenant.

### Estado atual (antes da implementação)

| Serviço | Exposto via GraphQL? | Valida ownership? |
|---|---|---|
| `TransactionService` | Sim | Sim — mas usa `findByAccountId().any {}` (query pesada) |
| `MonthSummaryQueryService.getMonthSummary` | Sim | **Não — vulnerabilidade real** |
| `MonthSummaryQueryService.getCategoryBreakdown` | Sim (via `MonthSummaryQueryService`) | **Não** |
| `MonthSummaryQueryService.getTagBreakdown` | Sim (via `MonthSummaryQueryService`) | **Não** |
| `CreditCardBillService.listBills` | Sim | **Não** |
| `CreditCardBillService.findBill` | Sim | **Não** |
| `SnapshotComputationService` | Não (interno) | N/A — sempre chamado após validação |

---

## Solução: `FinancialAccountGuard`

Padrão: **Guard Service** (Policy Object). Um `@Singleton` injetável que centraliza a verificação
de ownership. Único ponto de verdade: mudar lógica, mensagem de erro, ou adicionar auditoria
requer mudança em apenas um lugar.

### Por que não AOP?

AOP com `@Around` interceptors introduz indireção invisível no stack trace e requer binding por
annotation. O custo de legibilidade não compensa para um check tão simples.

### Por que não confiar só no scoping do repo?

O repo previne vazamento de dados, mas não garante fail-fast com mensagem clara. Além disso,
`getCategoryBreakdown(financialAccountId: UUID?)` aceita `null`, tornando a validação condicional
necessária de forma explícita.

---

## Implementação

### 1. Criar `FinancialAccountGuard.kt`

**Arquivo a criar:**
```
service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountGuard.kt
```

**Conteúdo completo:**

```kotlin
package io.github.salomax.neotool.banking.service.account

import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class FinancialAccountGuard(
    private val financialAccountRepository: FinancialAccountRepository,
) {
    fun requireOwnership(
        financialAccountId: UUID,
        accountId: UUID,
    ) {
        require(financialAccountRepository.existsByIdAndAccountId(financialAccountId, accountId)) {
            "Financial account $financialAccountId does not belong to account $accountId"
        }
    }

    fun requireOwnershipIfPresent(
        financialAccountId: UUID?,
        accountId: UUID,
    ) {
        if (financialAccountId != null) requireOwnership(financialAccountId, accountId)
    }
}
```

**Notas:**
- `existsByIdAndAccountId` **já existe** em `FinancialAccountRepository` (linha 18) e em
  `DefaultFinancialAccountRepository` — usa `existsByIdAndAccountIdAndDeletedAtIsNull` no JPA.
  Nenhuma alteração no repositório é necessária.
- `requireOwnershipIfPresent` é a variante para parâmetros opcionais (filtros de dashboard).

---

### 2. Refatorar `TransactionService`

**Arquivo:**
```
service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
```

**Passo 2a — Adicionar injeção do guard no construtor primário.**

Localizar a declaração de classe (é um construtor longo). Adicionar `financialAccountGuard` como
parâmetro (junto aos demais). Remover `financialAccountRepository` do construtor **somente se**
ele não for usado em mais nenhum lugar além de `requireAccountOwnsFinancialAccount`. Verificar
antes com `grep financialAccountRepository TransactionService.kt` — se for usado em outros lugares,
manter a injeção.

Conforme grep no código atual, `financialAccountRepository` aparece nas linhas 48 e 1043 além do
método privado. A linha 1043 usa `financialAccountRepository.findByAccountId(accountId)` dentro de
`getFinancialAccount`. **Portanto mantenha `financialAccountRepository` no construtor e adicione
`financialAccountGuard` também.**

**Passo 2b — Substituir o método privado.**

Remover completamente (linhas 1057–1065):

```kotlin
// REMOVER este método:
private fun requireAccountOwnsFinancialAccount(
    financialAccountId: UUID,
    accountId: UUID,
) {
    val accounts = financialAccountRepository.findByAccountId(accountId)
    require(accounts.any { it.id == financialAccountId }) {
        "Financial account $financialAccountId does not belong to account $accountId"
    }
}
```

Substituir por delegação ao guard. Manter o mesmo nome de método para que as 4 chamadas
existentes (linhas 85, 390, 450, 997) continuem funcionando sem alteração:

```kotlin
// ADICIONAR este método (mesma assinatura, implementação via guard):
private fun requireAccountOwnsFinancialAccount(
    financialAccountId: UUID,
    accountId: UUID,
) = financialAccountGuard.requireOwnership(financialAccountId, accountId)
```

---

### 3. Adicionar guard em `MonthSummaryQueryService`

**Arquivo:**
```
service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt
```

**Passo 3a — Adicionar injeção no construtor (linha 40–44).**

De:
```kotlin
@Singleton
open class MonthSummaryQueryService(
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotComputationService: SnapshotComputationService,
)
```

Para:
```kotlin
@Singleton
open class MonthSummaryQueryService(
    private val snapshotRepository: MonthlyBalanceSnapshotRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotComputationService: SnapshotComputationService,
    private val financialAccountGuard: FinancialAccountGuard,
)
```

Adicionar import:
```kotlin
import io.github.salomax.neotool.banking.service.account.FinancialAccountGuard
```

**Passo 3b — Adicionar guard em `getMonthSummary` (linha 52–57).**

De:
```kotlin
@Transactional
open fun getMonthSummary(
    financialAccountId: UUID,
    accountId: UUID,
    year: Int,
    month: Int,
): MonthSummary {
    val today = LocalDate.now()
```

Para:
```kotlin
@Transactional
open fun getMonthSummary(
    financialAccountId: UUID,
    accountId: UUID,
    year: Int,
    month: Int,
): MonthSummary {
    financialAccountGuard.requireOwnership(financialAccountId, accountId)
    val today = LocalDate.now()
```

**Passo 3c — Adicionar guard em `getCategoryBreakdown` (linha 74–89).**

`getCategoryBreakdown` é chamada tanto pelo resolver (com input do usuário) quanto internamente
por `getRealMonthSummary`/`getFutureMonthSummary`. Quando chamada internamente, `accountId` e
`financialAccountId` já foram validados pelo `getMonthSummary`. Para evitar dupla validação sem
perder a proteção na chamada direta do resolver, aplicar o guard aqui também — é idempotente e
usa `EXISTS` (leve):

De:
```kotlin
fun getCategoryBreakdown(
    accountId: UUID,
    financialAccountId: UUID?,
    year: Int,
    month: Int,
    applicableTo: CategoryApplicableTo?,
): List<CategoryTotal> =
    toCategoryTotals(
```

Para:
```kotlin
fun getCategoryBreakdown(
    accountId: UUID,
    financialAccountId: UUID?,
    year: Int,
    month: Int,
    applicableTo: CategoryApplicableTo?,
): List<CategoryTotal> {
    financialAccountGuard.requireOwnershipIfPresent(financialAccountId, accountId)
    return toCategoryTotals(
        transactionRepository.getCategoryBreakdown(
            accountId = accountId,
            financialAccountId = financialAccountId,
            year = year,
            month = month,
            applicableTo = applicableTo,
        ),
    )
}
```

**Passo 3d — Adicionar guard em `getTagBreakdown` (linha 95–110). Mesma lógica:**

De:
```kotlin
fun getTagBreakdown(
    accountId: UUID,
    financialAccountId: UUID?,
    year: Int,
    month: Int,
    applicableTo: CategoryApplicableTo?,
): List<TagTotal> =
    toTagTotals(
```

Para:
```kotlin
fun getTagBreakdown(
    accountId: UUID,
    financialAccountId: UUID?,
    year: Int,
    month: Int,
    applicableTo: CategoryApplicableTo?,
): List<TagTotal> {
    financialAccountGuard.requireOwnershipIfPresent(financialAccountId, accountId)
    return toTagTotals(
        transactionRepository.getTagBreakdown(
            accountId = accountId,
            financialAccountId = financialAccountId,
            year = year,
            month = month,
            applicableTo = applicableTo,
        ),
    )
}
```

---

### 4. Adicionar guard em `CreditCardBillService`

**Arquivo:**
```
service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt
```

**Passo 4a — Adicionar injeção no construtor (linha 24–26).**

De:
```kotlin
@Singleton
open class CreditCardBillService(
    private val billRepository: CreditCardBillRepository,
)
```

Para:
```kotlin
@Singleton
open class CreditCardBillService(
    private val billRepository: CreditCardBillRepository,
    private val financialAccountGuard: FinancialAccountGuard,
)
```

Adicionar import:
```kotlin
import io.github.salomax.neotool.banking.service.account.FinancialAccountGuard
```

**Passo 4b — Adicionar guard em `listBills` (linha 186–189).**

De:
```kotlin
fun listBills(
    financialAccountId: UUID,
    accountId: UUID,
): List<CreditCardBill> = billRepository.listByAccount(financialAccountId, accountId)
```

Para:
```kotlin
fun listBills(
    financialAccountId: UUID,
    accountId: UUID,
): List<CreditCardBill> {
    financialAccountGuard.requireOwnership(financialAccountId, accountId)
    return billRepository.listByAccount(financialAccountId, accountId)
}
```

**Passo 4c — Adicionar guard em `findBill` (linha 195–207).**

De:
```kotlin
fun findBill(
    financialAccountId: UUID,
    accountId: UUID,
    closingDate: LocalDate?,
): CreditCardBill? {
    if (closingDate != null) {
```

Para:
```kotlin
fun findBill(
    financialAccountId: UUID,
    accountId: UUID,
    closingDate: LocalDate?,
): CreditCardBill? {
    financialAccountGuard.requireOwnership(financialAccountId, accountId)
    if (closingDate != null) {
```

---

## Testes a escrever

Os testes de integração devem usar a infraestrutura existente:
- Estender `BankingPostgresIntegrationTest`
- Executar via `./gradlew :banking:testIntegration` (requer Docker)

### Cenários obrigatórios (para cada serviço modificado)

Para cada método que recebeu `requireOwnership` ou `requireOwnershipIfPresent`, testar:

1. **Happy path** — `financialAccountId` pertence ao `accountId` → retorna resultado normalmente.
2. **Cross-tenant** — `financialAccountId` de outro tenant → lança `IllegalArgumentException` com
   mensagem contendo `"does not belong to account"`.
3. **ID inexistente** — `financialAccountId` inventado (UUID aleatório) → mesma exceção do caso 2.

### Exemplo de estrutura (JUnit 5 + AssertJ)

```kotlin
// Teste de regressão: MonthSummaryQueryService rejeita fin account de outro tenant
@Test
fun `getMonthSummary throws when financialAccountId belongs to another account`() {
    val otherAccountId = createAccount()
    val otherFinancialAccountId = createFinancialAccount(accountId = otherAccountId)

    assertThatThrownBy {
        monthSummaryQueryService.getMonthSummary(
            financialAccountId = otherFinancialAccountId,
            accountId = currentAccountId,   // tenant do usuário atual
            year = 2026,
            month = 1,
        )
    }.isInstanceOf(IllegalArgumentException::class.java)
     .hasMessageContaining("does not belong to account")
}
```

Replicar o padrão para `getCategoryBreakdown`, `getTagBreakdown`, `listBills`, `findBill`.

---

## Resumo das mudanças

| Arquivo | Tipo de mudança |
|---|---|
| `service/account/FinancialAccountGuard.kt` | **Criar** |
| `service/transaction/TransactionService.kt` | Injetar guard; delegar método privado |
| `service/transaction/MonthSummaryQueryService.kt` | Injetar guard; adicionar guard em 3 métodos |
| `service/transaction/CreditCardBillService.kt` | Injetar guard; adicionar guard em 2 métodos |

Nenhuma alteração em repositórios, migrations, GraphQL schema ou resolver é necessária.
