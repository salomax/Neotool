---
id: feature.transactions.imports.nfce-products-and-aliases
domain: transactions
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportJobService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/ProductCatalogService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/nfce/ProductAnalyticsService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/product/ProductResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/import/nfce/ProductAliasRepository.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_12__create_product_price_tracking_tables.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/ProductPriceTrackingRepositoryIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/import/ImportJobServiceTest.kt
  frontend:
    - web/src/lib/graphql/operations/banking/mutations.ts
    - web/src/lib/graphql/operations/banking/queries.ts
    - web/src/app/personal-finance/finance-transactions/transaction/UnmappedItemsDrawer.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/NfceDocumentDrawer.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.nfce-import
  - feature.transactions.categorization.category-catalog
schema: spec-metadata-v1
---

# Imports — NFC-e Products And Aliases

## Glossary

- `accountId` — tenant scope for products, aliases, and purchase items.
- `financialAccountId` — not the product catalog key; products are tenant-scoped, items hang off NFC-e transactions.
- Product catalog here is **NFC-e purchase mapping** (`banking.products` / variants / aliases). Wishlist “product” metadata is a different model — out of Batch 8 depth (no FK to this catalog).
- `Product.category` — free-text product attribute, **not** `TransactionCategory` from categorization Batch 3.

## Behavior

### Model (V0_12)

| Table | Role |
|---|---|
| `products` | Tenant product (`name` unique per account) |
| `product_variants` | Variant under product; optional global unique `ean` |
| `product_alias` | Maps `raw_description` → variant; unique `(account_id, raw_description)`; sources `MANUAL` / `AI_SUGGESTED` / `EAN_MATCH` / `HEURISTIC` |
| `purchase_items` | Line items from NFC-e; optional `product_variant_id` (null = unmapped) |
| `ncm_codes` | Reference NCM descriptions |

### Auto-resolve at NFC-e persist

`ImportJobService.resolveProductVariant` order:

1. EAN → variant by EAN  
2. Alias → `findByAccountIdAndRawDescription(accountId, xProd)`  
3. Issuer continuity → prior mapped item same `issuerCnpj` + issuer product code (`cProd`)  
4. else `null` (unmapped; partial index on unmapped items)

Does **not** invent aliases/products automatically on import (except later manual map).

### Manual catalog ops (`ProductCatalogService`)

| Op | Effect | Permission |
|---|---|---|
| `createProduct` / `updateProduct` | Tenant product CRUD | `banking:transaction:update` |
| `createProductVariant` / `updateProductVariant` | Variants under product | update |
| `mapItemToVariant(rawDescription, variantId)` | Upsert alias `source=MANUAL`, `confirmed=true`; backfill unmapped items with that raw description | update |
| `unmapItem` / unmap alias path | Clear item mappings for alias + delete alias | update |
| `unmappedItems(limit)` | Groups unmapped purchase items by raw description | `banking:transaction:view` |

`suggestedVariant` / `confidence` exist on domain/`UnmappedItemGroup` but are **always null** in current list implementation.

Alias enum sources beyond `MANUAL` are schema/DB-ready; writers today use **`MANUAL` only**.

### Analytics (NFC-e-backed)

`ProductAnalyticsService` queries over purchase_items + purchase_documents (price history, consumption trend, store comparison, spending by NCM). View permission. No Bruno coverage found.

### Interaction with categories / merchants / rules

- Mapping products does **not** set `Transaction.categoryId` or merchant.
- Transaction categorization remains separate (Batch 3); NFC-e import does not run rule engine on create.
- Product `category` string is orthogonal labeling for the catalog.

### UX

- `NfceDocumentDrawer` shows document + items (incl. mapped variant when present).
- `UnmappedItemsDrawer` implements create product/variant + `mapItemToVariant` against GraphQL ops, but is **not wired** into finance-transactions page/dialogs (component + i18n exist; no navigation entry found).

## Invariants

- Backend owns resolve/map/unmap; frontend only drives mutations when UI is wired.
- Unmapped items remain queryable until aliased.
- Wishlist catalog is not this feature’s source of truth.

## Permissions

Product/alias mutations: `banking:transaction:update`.  
Catalog/unmapped/analytics/nfceDocument reads: `banking:transaction:view`.

## Data And Contracts

- GraphQL: `Product`, `ProductVariant`, `ProductAlias`, `ProductAliasSource`, `PurchaseItem`, `UnmappedItemGroup`, map/unmap/create mutations, analytics queries.
- Web ops in banking `mutations.ts` / `queries.ts` (`CREATE_PRODUCT`, `MAP_ITEM_TO_VARIANT`, `UNMAPPED_ITEMS`, …).
- No Bruno product-mapping requests found under Banking/Import.

## Impact Surface

- Backend ProductCatalogService / Analytics / ProductResolver / ImportJobService resolve on persist
- GraphQL + NfceDocumentDrawer + (orphan) UnmappedItemsDrawer + repository IT
- Cross: NFC-e import; categorization only by absence of coupling

## Decisions

- Three-tier auto-match (EAN → alias → issuer continuity) before leaving unmapped.
- Manual map writes confirmed alias and backfills historical unmapped rows with same description.
- Keep product catalog scoped to NFC-e purchase tracking in this batch (not wishlist).
- `ProductAliasSource` values beyond `MANUAL` (`AI_SUGGESTED`, `EAN_MATCH`, `HEURISTIC`) and unmapped `suggestedVariant`/`confidence` are **reserved for future use** — not current gaps.

## Known Gaps

- `UnmappedItemsDrawer` is implemented (create product/variant + `mapItemToVariant`) but **not wired** into finance-transactions page/dialogs. Purpose appears to be manual mapping of unmapped NFC-e purchase lines; product entry point is unclear — documentation gap only (no issue opened).
- No dedicated `ProductCatalogService` / `ProductResolver` unit tests (repo IT + import artifact tests only).
- No E2E for map/unmap / unmapped list / analytics.
- Bruno has Import NFC-e only — no product catalog ops.

## Legacy Notes

- None used as authority.
