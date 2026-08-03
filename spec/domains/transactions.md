---
id: domain.transactions
status: canonical
kind: domain
source_of_truth: code
last_verified: 2026-08-03
discovery: complete
schema: spec-metadata-v1
---

# Transactions

Reverse-spec discovery complete. Batches 1–9 feature specs exist under `spec/features/transactions/` (foundation through recurring/attachments/preferences). Planned feature-folder tree covered; refresh or new areas as needed.

## Boundary

Owns personal-finance money movement and adjacent behavior: financial accounts, ledger transactions, categorization, credit-card bills, statement/file imports, bill PDF import, NFC-e import, recurring payments, transaction attachments, banking preferences, and derived summaries.

Primary code owners:

- `service/kotlin/banking/` packages tied to personal finance: `account`, `transaction`, `import`, `billimport`, `attachment`, `preferences`
- `contracts/graphql/subgraphs/banking/schema.graphqls`
- `web/src/app/personal-finance/`
- `web/tests/e2e/personal-finance/`
- `contracts/api-collections/.../Banking/`
- `workers/python/pdf-extractor/` for bill PDF extraction

## Not In Scope

- Investment portfolio positions, TWR/performance, investment imports, come-cotas, brokerage flows, or investment-specific transactions (live in `investment-portfolio`)
- Wishlist and planning-only purchase flows (live in `financial-planning`)
- Public market indicators and financial-data Go jobs (live in `market-data`)
- Tenant `security.accounts` membership model (security domain); only JWT `current_account` is consumed here
- Mobile (no explored implementation yet)
- Legacy `removed historical docs` (historical only)

## Glossary

- `accountId` means tenant/workspace account from security claims unless explicitly qualified.
- `financialAccountId` means a banking financial account such as bank account, cash, credit card, or charge card.
- `Transaction` means the personal-finance ledger row in banking, not an investment portfolio transaction aggregate.

## Subdomains

| Subdomain | Responsibility |
|---|---|
| `financial-accounts` | Banking financial accounts, card/bank metadata, issuer references, tenant ownership guard |
| `ledger-transactions` | Ledger transaction model and CRUD, transfers, installments, idempotency, MCP write metadata/change log |
| `categorization` | Category catalog, merchants, rule engine, bulk categorization, similar edit |
| `credit-card-bills` | Bill lifecycle, reference month/identity, totals/status, pay bill |
| `summaries` | Monthly snapshots, month summary query, dashboard/cashflow analytics |
| `imports` | Statement file import, dedup/job lifecycle, bill PDF pipeline, NFC-e import and product aliases |
| `recurring-payments` | Recurring payment templates, lifecycle, linking/confirmation |
| `attachments` | Transaction file attachments via assets |
| `preferences` | Banking account/user preferences, including rule creation mode |

## Official Feature Tree

```text
spec/features/transactions/
  financial-accounts/
    account-model.md
    account-crud.md
    issuers-reference.md
    tenant-ownership-guard.md
  ledger-transactions/
    transaction-model.md
    create-update-delete.md
    transfers-and-installments.md
    idempotency-and-bulk-caps.md
    mcp-change-log.md
  categorization/
    category-catalog.md
    merchants.md
    rule-engine.md
    bulk-and-similar-edit.md
  credit-card-bills/
    bill-lifecycle.md
    pay-bill.md
    reference-month-and-identity.md
  summaries/
    monthly-balance-snapshots.md
    month-summary-query.md
    dashboard-and-cashflow.md
  imports/
    statement-file-import.md
    dedup-and-duplicate-resolution.md
    import-job-lifecycle.md
    bill-pdf-import-pipeline.md
    bill-import-review-and-confirm.md
    pdf-extractor-worker.md
    nfce-import.md
    nfce-products-and-aliases.md
  recurring-payments/
    recurring-model.md
    lifecycle-and-linking.md
  attachments/
    transaction-attachments.md
  preferences/
    banking-account-preferences.md
```

## Source Entrypoints

- Backend: `service/kotlin/banking/src/main/kotlin/`
- GraphQL: `contracts/graphql/subgraphs/banking/schema.graphqls` and banking service GraphQL resources
- Database: `service/kotlin/banking/src/main/resources/db/migration/`
- Tests: `service/kotlin/banking/src/test/`; web E2E under `web/tests/e2e/personal-finance/`
- Frontend: `web/src/app/personal-finance/` and `web/src/lib/graphql/operations/banking/`
- API collections: `contracts/api-collections/.../Banking/Transactions/` and `Banking/Import/`
- Worker: `workers/python/pdf-extractor/`

## Cross-Domain Dependencies

- **security** — JWT `current_account`, `banking:*` permissions, OAuth scopes `finance:*` to `banking:*`, S2S worker auth
- **assets** — upload/signed URLs for PDFs and attachments; issuer/merchant logos
- **mcp** — ledger writes, bulk caps, change log, connected-agent activity dependency
- **investment-portfolio** — shared banking rows/columns for investment cash legs; document as bridge only
- **market-data** — weak dependency for issuer/institution enrichment; financial-data jobs are not ledger PF
- **financial-planning** — wishlist is outside transactions; NFC-e product aliases stay under imports
- **workers/python/pdf-extractor** — part of bill PDF import

## Known Gaps

- `accountId` vs `financialAccountId` naming collision is high-risk for agents and specs.
- Banking module mixes PF, investment, wishlist, and market-adjacent code; route by subdomain evidence, not by module alone.
- Open Finance / Pluggy fields exist in the model, but product depth was not mapped in discovery.
- Bruno coverage is uneven beyond basic transactions/imports.
- Balances are derived via snapshots/queries, not stored on transaction rows.
- MCP write path is mixed into transaction package; keep as `ledger-transactions/mcp-change-log.md` unless it grows into a cross-domain subdomain.
- NFC-e products and wishlist products overlap semantically; keep NFC-e products scoped to import behavior.
- Issuer ownership is mixed between PF reference data and market/asset enrichment.

## Open Questions

- Whether Open Finance sync deserves its own subdomain after a focused connector discovery.
- Whether banking preferences should be batched with accounts or categorization.
- Whether `INVESTMENT` / `BENEFIT` financial account types should be fully documented here or only referenced as shared model pointers.

## Reverse-spec batches

1. Foundation — `financial-accounts/*`, `ledger-transactions/transaction-model.md`, `ledger-transactions/create-update-delete.md`
2. Money movement edges — transfers/installments, idempotency/bulk caps, MCP change log
3. Categorization — category catalog, merchants, rule engine, bulk/similar edit
4. Credit-card bills — lifecycle, reference month/identity, pay bill
5. Summaries — monthly snapshots, month summary, dashboard/cashflow
6. Imports A — statement file import, dedup, job lifecycle
7. Imports B — bill PDF pipeline, worker, review/confirm
8. Imports C — NFC-e import and products/aliases
9. Recurring, attachments, preferences

## Reverse Spec Notes

- Code, tests, GraphQL contracts, migrations, and UI are authoritative.
- Do not load `removed historical docs` as truth.
- Load this domain file first, then only the relevant subdomain specs for the issue.
- Explicitly distinguish security tenant account from banking financial account in every affected spec.
