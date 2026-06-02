# Fix: closingBalance deve excluir transferências internas

## Contexto

O `closingBalance` no `monthSummary` e nos snapshots mensais está sendo calculado com
**todas** as movimentações da conta, incluindo transferências entre contas próprias.
Isso distorce o número: uma conta que recebeu R$46k em receitas, pagou R$20k em despesas
e transferiu R$28k para investimentos termina o mês com `closingBalance = -R$1.913`,
quando a percepção correta do usuário é que ele gerou R$26k de caixa naquele mês.

Benchmark (YNAB, Mint, Mobills, Guiabolso): transferências internas são neutras para
o cálculo de saldo — elas movem patrimônio entre "bolsos", não criam nem destroem caixa.

---

## Definição correta

```
closingBalance = openingBalance
              + SUM(CREDIT, POSTED+PENDING) WHERE type <> 'TRANSFER'
              - SUM(DEBIT,  POSTED+PENDING) WHERE type <> 'TRANSFER'
```

**O que muda em relação ao comportamento atual:**
- `TRANSFER` passa a ser **excluído** do cálculo de closingBalance.
- `BALANCE_ADJUSTMENT` **continua incluído** — ele representa a correção do saldo
  inicial de uma conta e deve afetar o saldo.

**O que não muda:**
- `totalIncome` e `totalExpenses` no snapshot continuam excluindo tanto `TRANSFER`
  quanto `BALANCE_ADJUSTMENT` (comportamento correto, sem alteração).
- A cadeia de meses continua intacta: o `openingBalance` do mês N+1 é o
  `closingBalance` do mês N, calculado com a nova fórmula.

---

## Arquivos a modificar

### 1. `DefaultTransactionRepository.kt` — SQL `MONTH_TOTALS_SQL`

**Arquivo:**
`service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt`

**Localização:** constante `MONTH_TOTALS_SQL` (linha ~830).

Adicionar duas novas colunas ao SELECT (colunas 8 e 9, índices 8 e 9 no array):

```sql
-- col[8]: totalCreditExclTransfer
COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'
                              AND status IN ('POSTED','PENDING')
                              AND type <> 'TRANSFER'), 0),
-- col[9]: totalDebitExclTransfer
COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'
                              AND status IN ('POSTED','PENDING')
                              AND type <> 'TRANSFER'), 0)
```

Inserir **após** as colunas existentes de `totalExpenses` (col[3]) e **antes** das
colunas de `totalFutureCredit` (col[4]). Os índices das colunas future precisam ser
renumerados de 4–7 para 6–9.

O SELECT final terá 10 colunas (índices 0–9):
| Índice | Campo |
|---|---|
| 0 | totalCredit |
| 1 | totalDebit |
| 2 | totalIncome |
| 3 | totalExpenses |
| **4** | **totalCreditExclTransfer** (novo) |
| **5** | **totalDebitExclTransfer** (novo) |
| 6 | totalFutureCredit (era 4) |
| 7 | totalFutureDebit (era 5) |
| 8 | totalFutureIncome (era 6) |
| 9 | totalFutureExpenses (era 7) |

O mapeamento no método `getMonthTotals()` precisa refletir os novos índices.

---

### 2. `TransactionAggregates.kt` — `MonthTransactionTotals`

**Arquivo:**
`service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionAggregates.kt`

Adicionar dois novos campos ao data class `MonthTransactionTotals`:

```kotlin
/** SUM of CREDIT amounts WHERE type <> 'TRANSFER' (includes BALANCE_ADJUSTMENT). */
val totalCreditExclTransfer: BigDecimal,
/** SUM of DEBIT amounts WHERE type <> 'TRANSFER' (includes BALANCE_ADJUSTMENT). */
val totalDebitExclTransfer: BigDecimal,
```

Inserir entre `totalExpenses` e `totalFutureCredit`.

---

### 3. `SnapshotComputationService.kt` — fórmula do `closingBalance`

**Arquivo:**
`service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/SnapshotComputationService.kt`

**Linha atual (recomputeSnapshot):**
```kotlin
val closingBalance = openingBalance + totals.totalCredit - totals.totalDebit
```

**Substituir por:**
```kotlin
val closingBalance = openingBalance + totals.totalCreditExclTransfer - totals.totalDebitExclTransfer
```

Nenhuma outra alteração nesse arquivo.

---

### 4. Migração de dados — Flyway

**Arquivo a criar:**
`service/kotlin/banking/src/main/resources/db/migration/V0_XX__mark_snapshots_dirty_for_closing_balance_fix.sql`

Substituir `XX` pelo próximo número de versão disponível.

Conteúdo:
```sql
-- Marca todos os snapshots como dirty para forçar recomputação
-- com a nova fórmula de closingBalance (exclui transferências internas).
UPDATE banking.monthly_balance_snapshots
SET is_dirty = TRUE;
```

O sweeper job existente (`MonthlySnapshotSweeperJob`, roda a cada 30 min) vai
recomputar todos os snapshots sujos com a nova fórmula automaticamente após o deploy.

> **Atenção:** durante a janela entre o deploy e a conclusão do sweeper (até 30 min),
> os `closingBalance` exibidos ainda serão os antigos. Se isso for inaceitável, o
> sweeper pode ser acionado manualmente logo após o deploy.

---

## Critérios de aceitação

- [ ] `monthSummary` para janeiro/2025 com receitas=46k, despesas=20k,
      transferências_enviadas=28k retorna `closingBalance = +26.086,47`
      (não -1.913,53).
- [ ] Conta sem nenhuma transferência: `closingBalance` permanece igual ao
      comportamento anterior.
- [ ] `BALANCE_ADJUSTMENT` continua afetando `closingBalance` normalmente.
- [ ] A cadeia de snapshots é consistente: `openingBalance` do mês N+1 = novo
      `closingBalance` do mês N.
- [ ] Testes unitários do `SnapshotComputationService` atualizados para refletir
      a nova fórmula.

---

## O que NÃO muda

- `totalIncome`, `totalExpenses` no snapshot — sem alteração.
- `transfersIn`, `transfersOut` no `monthSummary` — calculados separadamente via
  `getMonthTransferTotals()`, sem alteração.
- `totalCredit`, `totalDebit` em `MonthTransactionTotals` — mantidos (outros
  consumidores podem depender deles; verificar antes de remover).
- `portfolioNetWorthSeries` — bug separado, não relacionado a este fix.
