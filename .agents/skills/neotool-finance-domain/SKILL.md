---
name: neotool-finance-domain
description: >
  Domain knowledge for the Personal Finance module of the Invistus project.
  Use when working with financial transactions, credit card bills, monthly summaries,
  or any personal finance feature — whether on the backend (banking service) or
  frontend (web/mobile). Triggers when the task mentions Transaction, CreditCardBill,
  MonthSummary, statement, financial entry, balance, or involves files under
  personal-finance or banking features.
metadata:
  author: invistus
  version: "1.0"
  paths: "invistus/web/src/features/personal-finance/**, invistus/mobile/src/features/personal-finance/**, invistus/service/kotlin/banking/**"
---

# Personal Finance — Domain Knowledge

Business knowledge for the main Invistus module.

Load reference files based on the concept:

- Working with transactions (Transaction) → [references/transaction.md](references/transaction.md)
- Working with credit card bills (CreditCardBill) → [references/credit-card-bill.md](references/credit-card-bill.md)
- Working with monthly summaries (MonthSummary/Snapshot) → [references/monthly-summary.md](references/monthly-summary.md)

## Domain overview

The Personal Finance module revolves around three main aggregates:

- **Transaction** — individual financial entry (credit or debit)
- **CreditCardBill** — monthly credit card bill (groups transactions)
- **MonthSummary** — monthly account summary (calculated snapshot)

**Relationships:**

```
FinancialAccount
    ├── Transaction (many)
    │       └── CreditCardBill (optional FK, for credit cards)
    └── MonthlyBalanceSnapshot (one per month)
```
