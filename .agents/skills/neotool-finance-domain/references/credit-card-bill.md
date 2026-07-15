# CreditCardBill — Domain Model

## O que é

Representa a fatura mensal de uma conta de cartão de crédito.
Agrupa as transações do ciclo de faturamento.

## Colunas relevantes (banking.credit_card_bills)

| Coluna | Tipo | Descrição |
|---|---|---|
| `financial_account_id` | UUID | conta do cartão |
| `closing_date` | date | data de fechamento da fatura |
| `due_date` | date | data de vencimento |
| `total_amount` | decimal | total da fatura |
| `status` | enum | `OPEN`, `CLOSED`, `PAID`, `OVERDUE` |

**Constraint unique:** `uq_credit_card_bills_account_closing` em `(financial_account_id, closing_date)`

## Aritmética do ciclo

```
computeClosingDate(transactionDate, closingDay):
  se dia da transação <= closingDay → fecha no closingDay do mês atual
  caso contrário → fecha no closingDay do próximo mês

computeDueDate(closingDate, dueDay):
  primeiro dueDay após closingDate
```

## Cálculo do total

```sql
totalAmount = SUM(DEBIT) - SUM(CREDIT)
FROM transactions
WHERE bill_id = :billId
  AND deleted_at IS NULL
```

## Regras de negócio

- Fatura é criada automaticamente na primeira transação do ciclo (`findOrCreate`)
- `ON CONFLICT DO NOTHING` garante idempotência — nunca duplica
- `BALANCE_ADJUSTMENT` nunca é atribuído a uma fatura
- `TransactionMapper.updateEntity` deve incluir `entity.billId = domain.billId`

## Write flow

Após qualquer mutation em Transaction (CREDIT_CARD/CHARGE_CARD):
`CreditCardBillService.recomputeBill(billId)` → atualiza `total_amount` e `status`
