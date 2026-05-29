# CreditCardBill - Domain Model

## O Que É

Representa a fatura mensal de uma conta de cartão de crédito. Agrupa as
transações do ciclo de faturamento e mantém total e status da fatura.

## Colunas Relevantes (`banking.credit_card_bills`)

| Coluna | Tipo | Descrição |
|---|---|---|
| `financial_account_id` | UUID | conta do cartão |
| `closing_date` | date | data de fechamento da fatura |
| `due_date` | date | data de vencimento |
| `total_amount` | decimal | total da fatura |
| `status` | enum | `OPEN`, `CLOSED`, `PAID`, `OVERDUE` |

A fatura deve ser única por conta e data de fechamento.

## Aritmética do Ciclo

```text
computeClosingDate(transactionDate, closingDay):
  se dia da transação <= closingDay -> fecha no closingDay do mês atual
  caso contrário -> fecha no closingDay do próximo mês

computeDueDate(closingDate, dueDay):
  primeiro dueDay após closingDate
```

## Cálculo do Total

```sql
total_amount = SUM(DEBIT) - SUM(CREDIT)
FROM banking.transactions
WHERE bill_id = :billId
  AND deleted_at IS NULL
```

## Regras de Negócio

- Fatura é criada automaticamente na primeira transação do ciclo.
- Criação deve ser idempotente para não duplicar faturas sob concorrência.
- `BALANCE_ADJUSTMENT` nunca é atribuído a uma fatura.
- Mudanças em `TransactionMapper` precisam preservar `billId` nos dois sentidos.

## Write Flow

Após qualquer mutation em Transaction de cartão:

```text
TransactionService
  -> CreditCardBillService.findOrCreate/assign
  -> CreditCardBillService.recomputeBill(billId)
  -> SnapshotComputationService.recomputeChain(...)
```

## Arquivos de Referência

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBill.kt`
- Service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt`
- Entity/mapper: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/`
- Repository: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultCreditCardBillRepository.kt`
