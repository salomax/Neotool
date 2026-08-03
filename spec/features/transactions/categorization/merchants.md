---
id: feature.transactions.categorization.merchants
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/Merchant.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MerchantService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MerchantLogoHydrationService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/merchant/MerchantResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/MerchantLogoBackfillJob.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_6__create_merchants_and_rules.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/MerchantServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/MerchantLogoHydrationServiceTest.kt
    - web/tests/e2e/personal-finance/merchants.e2e.spec.ts
  frontend:
    - web/src/lib/graphql/operations/banking/mutations.ts
    - web/src/app/personal-finance/finance-transactions/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.categorization.rule-engine
  - feature.transactions.categorization.category-catalog
schema: spec-metadata-v1
---

# Categorization — Merchants

## Glossary

- System merchant — `isSystem=true`, `accountId=null`, CSV/migration seeded.
- Custom merchant — `isSystem=false`, `accountId=<tenant>`.
- Logo — not on merchant row; resolved via `domain` → `merchant_logo_cache` → assets federation.

## Behavior

- Visible set: system + tenant customs (`listForAccount` / cursor `search` page size 1–50, default 20).
- `create`: validates name (non-blank, ≤100), optional website → normalized domain; saves custom merchant; async logo hydrate when domain present.
- On create, auto-creates a user `CONTAINS` rule (priority 40, `applicableTo=ANY`, pattern = normalized merchant name) pointing at `merchantId`. Duplicate pattern → enrich existing rule with merchant instead of failing hard.
- `topMerchants` aggregates usage by tenant (optional `financialAccountId` / year / month).
- `merchantLogoPreview(domain)` for hydration preview.
- No GraphQL `updateMerchant` / `deleteMerchant` in schema.

## Invariants

- Merchant suggestion for descriptions goes through the **rule engine**, not a separate merchant matcher.
- Custom merchants are tenant-owned; system merchants are global.

## Permissions

Backend enforcement:

- Queries (`merchants`, `topMerchants`, logo preview): `banking:transaction:view`.
- `createMerchant`: `banking:transaction:create` (not `banking:category:manage`).

UX hints: merchant pickers in transaction UI; create via `CREATE_MERCHANT`.

## Data And Contracts

- DB: `banking.merchants`, `merchant_logo_cache`, rules table peer (V0_6).
- GraphQL: `Merchant`, `MerchantConnection`, `CreateMerchantInput`, `topMerchants`.
- Optional backfill job when `banking.merchant-logo.backfill.enabled=true`.

## Impact Surface

- Backend: MerchantService, logo hydration/job, MerchantResolver
- GraphQL + E2E `merchants.e2e.spec.ts`
- Cross: auto rule → rule-engine; assets service for logos

## Decisions

- Creating a merchant always attempts to register a matching CONTAINS rule.
- Logo identity is domain-keyed and shared across merchants with the same domain.

## Known Gaps

- No update/delete merchant API.
- No Bruno merchant requests under Banking/Transactions.
- Logo hydration depends on external assets/logo providers — partial operational depth.

## Legacy Notes

- None used as authority.
