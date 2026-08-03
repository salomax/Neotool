---
id: feature.transactions.financial-accounts.issuers-reference
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/issuer/Issuer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/issuer/IssuerResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/issuer/IssuerRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_2__create_banking_issuers.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/graphql/resolver/IssuerResolverTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/IssuerCategoryMigrationIT.kt
  frontend:
    - web/src/app/personal-finance/finance-transactions/account/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.financial-accounts.account-crud
  - feature.transactions.financial-accounts.account-model
schema: spec-metadata-v1
---

# Financial Accounts — Issuers Reference

## Behavior

- `Issuer` is a financial institution reference row (`banking.issuers`): name, domain, COMPE/ISPB, logoKey, country (default BR), active flag, category (BANK, BROKERAGE, CRYPTO_EXCHANGE, PAYMENT, BENEFIT).
- GraphQL query `issuers` (optional category filter) returns active issuers via `IssuerResolver.listIssuers`.
- When creating/updating a financial account with `issuerId`, service snapshots issuer name/country/COMPE/ISPB onto the financial account; custom `issuerName` path leaves `issuerId` null.
- `logoAsset` on Issuer DTO is a federation stub `Asset` keyed by `logoKey` (hydration may be async elsewhere).

## Invariants

- Issuers are reference data, not tenant-owned.
- Account create requires issuerId **or** issuerName, not both.

## Permissions

Backend enforcement: `banking:issuer:view` on issuers query.

UX hints: institution picker in account create UI.

## Data And Contracts

- GraphQL `type Issuer`, query `issuers`.
- Bruno: `Banking/List Issuers.bru`.
- Migration V0_2 seeds/table.

## Impact Surface

- Backend issuer domain/repo/resolver
- GraphQL + IssuerResolverTest / category migration IT
- Frontend account creation issuer picker
- Cross: assets federation for logos; market-data enrichment workers may update logos (**dep**, not this spec’s authority)

## Decisions

- Catalog-driven issuer pick with escape hatch for custom names.

## Known Gaps

- Logo hydration worker/job ownership not fully traced in Batch 1 (assets / institution enhancement).
- No issuer CRUD GraphQL for admins in this surface (seed/migration driven).

## Legacy Notes

- None used as authority.
