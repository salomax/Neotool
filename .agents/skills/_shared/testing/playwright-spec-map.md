# Existing Spec Map

Use this map to find the owning Playwright specs before creating new coverage.

## Top-level product areas

| Changed area | Start with these specs |
|---|---|
| `web/src/app/(authentication)/signin` and auth entry flow | `web/tests/e2e/signin.e2e.spec.ts` |
| `web/src/app/profile` | `web/tests/e2e/profile.e2e.spec.ts` |
| authorization users | `web/tests/e2e/user-management.e2e.spec.ts` |
| authorization groups | `web/tests/e2e/group-management.e2e.spec.ts` |
| authorization roles | `web/tests/e2e/role-management.e2e.spec.ts` |
| authorization rules, route protection, access control | `web/tests/e2e/authorization.e2e.spec.ts`, `web/tests/e2e/personal-finance/security.e2e.spec.ts` when finance isolation is involved |

## Personal finance

Start with the owning spec instead of creating another cross-cutting file.

| Changed area | Owning specs |
|---|---|
| finance transactions table, inline form, tags, categories | `web/tests/e2e/personal-finance/transactions.e2e.spec.ts`, `web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts` |
| financial account creation and editing | `web/tests/e2e/personal-finance/financial-accounts.e2e.spec.ts` |
| monthly summary | `web/tests/e2e/personal-finance/monthly-summary.e2e.spec.ts` |
| credit-card bill cycle and payment | `web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts` |
| CSV, OFX, NFC-e, import review | `web/tests/e2e/personal-finance/transaction-import.e2e.spec.ts`, `web/tests/e2e/personal-finance/transaction-import-nfce.e2e.spec.ts` |
| rule learning and transaction rules UI | `web/tests/e2e/personal-finance/transaction-rules.e2e.spec.ts` |
| merchants | `web/tests/e2e/personal-finance/merchants.e2e.spec.ts` |
| tenant isolation and finance permissions | `web/tests/e2e/personal-finance/security.e2e.spec.ts` |

Read these docs when the branch touches transactions or personal finance:

- `spec/features/transactions/README.md`
- `docs/02-development/testing-strategy.md`

Use the spec index plus the existing Playwright specs to avoid duplicating scenarios and to target documented gaps first.

## Investments

| Changed area | Start with these specs |
|---|---|
| investments overview and UI behavior | `web/tests/e2e/personal-finance/investments-twr-ui.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase4-ui.e2e.spec.ts` |
| TWR calculations and portfolio flows | `web/tests/e2e/personal-finance/investments-twr.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-hardening.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-stress.e2e.spec.ts` |
| asset-class-specific TWR behavior | `web/tests/e2e/personal-finance/investments-twr-phase2.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase3-equities.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase3-fixed-income.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase3-funds.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase3-crypto.e2e.spec.ts`, `web/tests/e2e/personal-finance/investments-twr-phase3-reits.e2e.spec.ts` |

## Search heuristics

When ownership is not obvious, search by route segment, `data-testid`, helper name, page object, or GraphQL operation:

```bash
rg -n "term-from-diff" web/tests/e2e
rg -n "data-testid-name" web/tests/e2e
rg -n "GraphQLOperationName" web/tests/e2e
```

Create a new spec only after this search fails to find a clear owner.
