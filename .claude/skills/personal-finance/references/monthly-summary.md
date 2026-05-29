# MonthlyBalanceSnapshot - Domain Model

## O Que É

Snapshot mensal do saldo de uma conta financeira. É pré-calculado e persistido
para performance. Pode ficar dirty e ser recomputado sob demanda ou pelo
sweeper job.

## Fórmula Principal

```text
closingBalance = openingBalance + totalCredit - totalDebit
```

Onde:

- `openingBalance` é o `closingBalance` do mês anterior ou saldo inicial.
- `totalCredit` e `totalDebit` somam transactions `POSTED` ou `PENDING`.
- `FUTURE` é excluído dos cálculos.
- `TRANSFER` e `BALANCE_ADJUSTMENT` são excluídos de `totalIncome` e `totalExpenses`.

## Campos Principais

| Campo | Descrição |
|---|---|
| `openingBalance` | saldo no início do mês |
| `closingBalance` | saldo no fim do mês |
| `totalCredit` | soma de créditos do mês |
| `totalDebit` | soma de débitos do mês |
| `totalIncome` | créditos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `totalExpenses` | débitos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `is_dirty` | true = snapshot desatualizado |

## Mecanismo de Atualização

**Eager/síncrono:** após mutation de Transaction,
`SnapshotComputationService.recomputeChain(fromMonth, accountId)` recomputa o
mês afetado e meses subsequentes, porque o closing balance vira opening balance
do próximo mês.

**Sweeper/assíncrono:** `MonthlySnapshotSweeperJob` recomputa snapshots com
`is_dirty = true`. É safety net, não caminho principal.

## GraphQL

Query disponível: `monthSummary(financialAccountId: ID!, year: Int, month: Int)`.
Omitir `year`/`month` retorna o mês atual quando o resolver seguir o padrão
existente.

## Gotchas

- Não dependa do sweeper como caminho principal de consistência.
- Ao alterar regra de soma, atualize tanto recompute quanto read path se ambos
  materializam totais.
- Mudanças em transações antigas podem afetar todos os meses seguintes da conta.

## Arquivos de Referência

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/MonthlyBalanceSnapshot.kt`
- Read service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt`
- Computation: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/SnapshotComputationService.kt`
- Sweeper: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/MonthlySnapshotSweeperJob.kt`
