# MonthlyBalanceSnapshot — Domain Model

## O que é

Snapshot mensal do saldo de uma conta financeira. Pré-calculado e persistido
para performance. Pode ficar "dirty" (desatualizado) e é recomputado on-demand
ou pelo sweeper job.

## Fórmula principal

```
closingBalance = openingBalance + totalCredit - totalDebit
```

onde:
- `openingBalance` = `closingBalance` do mês anterior (ou saldo inicial)
- `totalCredit` / `totalDebit` = soma das transactions com status `POSTED` ou `PENDING`
- Status `FUTURE` é **excluído** dos cálculos
- Tipos `TRANSFER` e `BALANCE_ADJUSTMENT` são **excluídos** de `totalIncome`/`totalExpenses`

## Campos principais

| Campo | Descrição |
|---|---|
| `openingBalance` | saldo no início do mês |
| `closingBalance` | saldo no fim do mês |
| `totalCredit` | soma de créditos do mês |
| `totalDebit` | soma de débitos do mês |
| `totalIncome` | créditos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `totalExpenses` | débitos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `is_dirty` | true = snapshot desatualizado, precisa recompute |

## Mecanismo de atualização

**Eager (síncrono):** após cada mutation de Transaction →
`SnapshotComputationService.recomputeChain(fromMonth, accountId)`
recomputa o mês afetado e todos os meses subsequentes (efeito cascata no saldo).

**Sweeper (assíncrono):** `MonthlySnapshotSweeperJob` roda a cada PT30M
e recomputa snapshots com `is_dirty = true`. Safety net — não é o caminho principal.

## GraphQL

Query disponível: `monthSummary(financialAccountId: ID!, year: Int, month: Int)`
Omitir year/month retorna o mês atual.

## Gotcha

O sweeper existe como safety net — se a recomputa síncrona falhar silenciosamente,
o sweeper corrige. Não depender do sweeper como caminho principal.
