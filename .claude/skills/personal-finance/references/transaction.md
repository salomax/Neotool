# Transaction - Domain Model

## Colunas Relevantes (`banking.transactions`)

| Coluna | Tipo | Valores |
|---|---|---|
| `credit_debit_type` | enum | `CREDIT`, `DEBIT` |
| `status` | enum | `POSTED`, `PENDING`, `FUTURE` |
| `type` | enum | `TRANSFER`, `BALANCE_ADJUSTMENT`, `SALARY`, `CREDIT_CARD`, `CHARGE_CARD`, outros |
| `financial_account_id` | UUID | FK para financial_accounts |
| `account_id` | UUID | tenant ID |
| `deleted_at` | timestamp | NULL = não deletado |
| `bill_id` | UUID? | FK para credit_card_bills quando aplicável |

## Regras de Negócio

**Soft delete:** queries devem incluir `WHERE deleted_at IS NULL` para fluxos
usuais de leitura e cálculo.

**Atribuição de fatura:**

- Apenas transações `CREDIT_CARD` ou `CHARGE_CARD` recebem `bill_id`.
- Transações `BALANCE_ADJUSTMENT` nunca são atribuídas a uma fatura.

**Status e cálculos:**

- `POSTED` e `PENDING` entram nos cálculos de saldo.
- `FUTURE` é excluído dos cálculos de saldo, mas segue visível em listagens.

**Cálculos de totais:**

- `totalIncome` e `totalExpenses` excluem `TRANSFER` e `BALANCE_ADJUSTMENT`.
- Saldo e fluxo financeiro podem usar regras diferentes dos totais gerenciais;
  confirme no service/repository antes de alterar.

## Write Flow

Ao criar, atualizar ou deletar uma Transaction:

1. `TransactionService` valida e persiste a mudança.
2. Se for cartão, `CreditCardBillService` atribui/recomputa a fatura afetada.
3. `SnapshotComputationService` recomputa a cadeia mensal a partir do mês afetado.

## Arquivos de Referência

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/Transaction.kt`
- Enums: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionEnums.kt`
- Service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt`
- Repository: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt`
