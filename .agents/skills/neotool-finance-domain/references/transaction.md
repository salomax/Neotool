# Transaction — Domain Model

## Colunas relevantes (banking.transactions)

| Coluna | Tipo | Valores |
|---|---|---|
| `credit_debit_type` | enum | `CREDIT`, `DEBIT` |
| `status` | enum | `POSTED`, `PENDING`, `FUTURE` |
| `type` | enum | `TRANSFER`, `BALANCE_ADJUSTMENT`, `SALARY`, `CREDIT_CARD`, `CHARGE_CARD`, outros |
| `financial_account_id` | UUID | FK para financial_accounts |
| `account_id` | UUID | tenant ID |
| `deleted_at` | timestamp | NULL = não deletado (soft delete) |
| `bill_id` | UUID? | FK para credit_card_bills (apenas CREDIT_CARD e CHARGE_CARD) |

## Regras de negócio

**Soft delete:** queries SEMPRE devem incluir `WHERE deleted_at IS NULL`.

**Atribuição de fatura:**
- Apenas transações com type `CREDIT_CARD` ou `CHARGE_CARD` recebem `bill_id`
- Transações `BALANCE_ADJUSTMENT` NUNCA são atribuídas a uma fatura

**Status e cálculos:**
- `POSTED` e `PENDING` entram nos cálculos de saldo
- `FUTURE` é excluído dos cálculos de saldo (mas visível na listagem)

**Cálculos de totais:**
- `totalIncome` e `totalExpenses` excluem tipos `TRANSFER` e `BALANCE_ADJUSTMENT`
- Apenas os outros tipos (SALARY, compras, etc.) entram nesses totais

## Write flow

Ao criar/atualizar/deletar Transaction:
1. `TransactionService.create/update/delete`
2. → `CreditCardBillService.recomputeBill()` (se CREDIT_CARD/CHARGE_CARD)
3. → `SnapshotComputationService.recomputeChain()` (recomputa do mês afetado até hoje)
