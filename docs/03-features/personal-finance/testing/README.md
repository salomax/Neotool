# Test Project — Financial Movements

This directory centralizes the test project for the `finance-transactions` feature in the personal finance app.

## Objective

Organize the existing coverage by relating:

- functional requirements and business rules;
- already implemented E2E files;
- coverage gaps;
- divergences between documentation and currently tested behavior.

## Current Scope

- financial accounts;
- transactions;
- credit card bills;
- monthly summary and snapshots;
- transaction imports;
- multi-tenant isolation and feature security.

## Sources of Truth Used

- `docs/03-features/personal-finance/requirements/01-financial-accounts.md`
- `docs/03-features/personal-finance/requirements/02-transactions.md`
- `docs/03-features/personal-finance/requirements/03-credit-card-bills.md`
- `docs/03-features/personal-finance/requirements/04-monthly-summaries.md`
- `web/tests/e2e/personal-finance/financial-accounts.e2e.spec.ts`
- `web/tests/e2e/personal-finance/transactions.e2e.spec.ts`
- `web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts`
- `web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts`
- `web/tests/e2e/personal-finance/monthly-summary.e2e.spec.ts`
- `web/tests/e2e/personal-finance/transaction-import.e2e.spec.ts`
- `web/tests/e2e/personal-finance/security.e2e.spec.ts`

## Initial Artifact

- `finance-transactions-e2e-coverage-map.md`

## Status Convention

- `Covered`: there is direct and requirement-aligned E2E validation.
- `Partial`: there is validation, but it is incomplete, conditional, indirect, or misaligned with the requirement.
- `No E2E coverage`: no E2E test was found covering the rule.
