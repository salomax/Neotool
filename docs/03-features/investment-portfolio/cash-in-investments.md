# Cash em Conta Investimento

Rastrear dinheiro disponível dentro de uma conta de investimento (não alocado em nenhuma posição).

---

## Problema

Dinheiro dentro de uma conta de investimento não tem representação no sistema:

- Aporte/transferência da conta corrente para conta investimento
- Resgate de um ativo
- Recebimento de dividendos ou rendimentos
- Qualquer outro evento que gere ou consuma saldo livre na conta

O total de portfólio exibido hoje é apenas a soma dos `Holding` ativos — saldo livre não entra no cálculo.

---

## Decisão de Design

**Usar `banking.transactions` com `type` + `category` para representar cash em conta investimento.**

### Justificativa

- Infraestrutura já existe: `Transaction`, `TransactionType`, categorias seedadas, transfer linking
- Zero novo domínio: o sistema de transações bancárias já suporta contas do tipo `INVESTMENT`
- Histórico e audit trail gratuitos
- Categorias corretas já seedadas: `Dividendos`, `Resgate`, `Investimento aplicado`, etc.
- Transfer linking (`transferPeerTransactionId`) já resolve o par DEBIT/CREDIT entre contas

### Tipos e categorias mapeados

| Evento                         | `type`              | `creditDebitType` | Category sugerida                    |
|-------------------------------|---------------------|-------------------|--------------------------------------|
| Aporte (banco → investimento) | `TRANSFER`          | CREDIT            | Transferência recebida               |
| Saque (investimento → banco)  | `TRANSFER`          | DEBIT             | Transferência enviada                |
| Compra de ativo               | `TRANSFER`          | DEBIT             | Investimento aplicado > [tipo]       |
| Venda / Resgate               | `INVESTMENT_RETURN` | CREDIT            | Investimentos > Resgate              |
| Dividendo                     | `INVESTMENT_RETURN` | CREDIT            | Renda Extra > Dividendos             |
| Rendimento / Juros            | `INVESTMENT_RETURN` | CREDIT            | Investimentos > Rendimento           |
| Vencimento (Renda Fixa)       | `INVESTMENT_RETURN` | CREDIT            | Investimentos > Resgate              |
| Amortização                   | `INVESTMENT_RETURN` | CREDIT            | Investimentos > Rendimento           |

---

## Modelo de Relacionamento

Um `banking.Transaction` de cash pode ser **originado** por um `InvestmentTransaction` (ex: um resgate gera cash).
Esse vínculo precisa ser rastreável.

```
banking.transactions
  └── investment_transaction_id → banking.investment_transactions.id (nullable)
```

**Regras:**
- Nullable: nem todo cash vem de um investment transaction (ex: aporte externo)
- SET NULL on delete: se o investment transaction for removido, o cash permanece mas perde o vínculo
- Um investment transaction pode originar no máximo um cash transaction

---

## Tarefas

### Tarefa 1 — Adicionar `investment_transaction_id` em `banking.transactions`

**Onde alterar:** modificar os migrations originais, não criar novo migration.

**`V0_7__create_transactions.sql`** — adicionar coluna na tabela (sem FK, pois `investment_transactions` ainda não existe nesse ponto):

```sql
-- Dentro do CREATE TABLE banking.transactions, após transfer_peer_transaction_id:
investment_transaction_id    UUID,
```

E o índice ao final do arquivo:

```sql
CREATE INDEX idx_transactions_investment_transaction
    ON banking.transactions(investment_transaction_id)
    WHERE investment_transaction_id IS NOT NULL;
```

**`V0_8__create_investment_tables.sql`** — adicionar a FK constraint ao final do arquivo (após a criação de `investment_transactions`):

```sql
ALTER TABLE banking.transactions
    ADD CONSTRAINT fk_transactions_investment_transaction
    FOREIGN KEY (investment_transaction_id)
    REFERENCES banking.investment_transactions(id)
    ON DELETE SET NULL;
```

**Kotlin — `Transaction.kt`:**

```kotlin
val investmentTransactionId: UUID? = null,
```

**Kotlin — `TransactionSchema.kt` / mapper:** adicionar o campo no mapeamento de leitura/escrita.

---

### Tarefa 2 — Backend: registrar cash ao gravar eventos de investimento

Quando `InvestmentTransactionService` grava um `DIVIDEND`, `SALE`, `MATURITY`, `INTEREST_PAYMENT` ou `AMORTIZATION`, criar automaticamente o `banking.Transaction` correspondente na mesma `FinancialAccount`, com o `investmentTransactionId` preenchido.

A criação deve ser **atômica** (mesma transação de banco de dados).

---

### Tarefa 3 — Frontend: exibir saldo de cash na conta de investimento

Exibir o saldo disponível (soma de `banking.transactions` CREDIT − DEBIT na conta) separado das posições, na tela de investimentos.

---

### Tarefa 4 — Open Finance: deduplicação

Quando um evento chega via OFB sync como `banking.Transaction` (cash side) E também como `InvestmentTransaction` (investment side), garantir que não há duplicidade. Usar `investmentTransactionId` como elo de dedup.

---

## Em Aberto

- Como tratar cash em moeda estrangeira (USD, EUR) em contas de custódia internacional? *(avaliar benchmark antes de decidir)*

---

## Decisões Fechadas

| # | Questão | Decisão |
|---|---------|---------|
| 1 | Registro manual ou automático? | **Ambos.** Manual pelo usuário quando necessário; automático ao encerrar posição (vencimento, resgate total ou parcial). A lógica automática já existe para Renda Fixa — estender para os demais eventos. |
| 2 | Cash entra no total do portfólio? | **Sim.** Quando Summaries forem implementados, o saldo de cash da conta de investimento deve compor o total. |
