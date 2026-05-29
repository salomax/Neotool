---
name: personal-finance
description: >
  Conhecimento de domínio do módulo Personal Finance do Invistus. Use ao
  trabalhar com transações financeiras, faturas de cartão de crédito, resumos
  mensais, contas financeiras, saldos, importação de lançamentos ou qualquer
  feature de finanças pessoais no backend, web ou mobile. Trigger: Transaction,
  CreditCardBill, MonthSummary, fatura, lançamento, saldo ou personal-finance.
paths: web/src/app/personal-finance/**, web/src/features/personal-finance/**, mobile/src/features/personal-finance/**, service/kotlin/banking/**
---

# Personal Finance - Domain Knowledge

Conhecimento de negócio do módulo principal do Invistus.

Carregue os arquivos de referência conforme o conceito em questão:

- Trabalhando com transações (`Transaction`): leia [references/transaction.md](references/transaction.md)
- Trabalhando com faturas (`CreditCardBill`): leia [references/credit-card-bill.md](references/credit-card-bill.md)
- Trabalhando com resumos mensais (`MonthSummary`/snapshot): leia [references/monthly-summary.md](references/monthly-summary.md)

## Visão Geral do Domínio

O módulo Personal Finance gira em torno destes aggregates principais:

- **FinancialAccount**: conta, cartão, carteira ou veículo financeiro.
- **Transaction**: lançamento financeiro individual, crédito ou débito.
- **CreditCardBill**: fatura mensal de cartão de crédito, agrupando transactions.
- **MonthSummary**: resumo mensal por conta, derivado de snapshots calculados.

## Relações

```text
FinancialAccount
    ├── Transaction (many)
    │       └── CreditCardBill (optional FK, para cartões)
    └── MonthlyBalanceSnapshot (one per month)
```

## Regras Não Negociáveis

1. Transações removidas por soft delete não entram em cálculo nem listagem comum.
2. Mudança em Transaction pode exigir recompute de bill e monthly snapshots.
3. `FUTURE` é visível em listagens, mas não entra em saldo realizado.
4. `TRANSFER` e `BALANCE_ADJUSTMENT` têm tratamento especial em totais gerenciais.
