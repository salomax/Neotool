---
id: feature.transactions.categorization.category-catalog
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionCategory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionCategoryService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionCategoryResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/shared/BankingPermissions.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_4__create_transaction_categories.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_26__add_gifts_and_celebrations_expense_categories.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_29__add_labor_rights_income_categories.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_36__add_beleza_cuidados_pessoais_categories.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_39__add_new_expense_subcategories.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/CategoryRuleServiceTest.kt
  frontend:
    - web/src/app/personal-finance/categories/
    - web/src/app/personal-finance/finance-transactions/category/
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.categorization.rule-engine
  - feature.transactions.categorization.bulk-and-similar-edit
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Categorization — Category Catalog

## Glossary

- `accountId` — tenant that owns custom categories (`NULL` on system rows).
- System category — `isSystem=true`, `accountId=null`, seeded, read-only via API.
- Custom category — `isSystem=false`, `accountId=<tenant>`, soft-deletable.

## Behavior

- Two-level tree: root (`parentId == null`) + one level of children; creating under a subcategory throws `CategoryDepthExceededException`.
- `applicableTo`: INCOME / EXPENSE / TRANSFER / ANY. Child must be compatible with parent (parent ANY, or same side, or child ANY).
- Create: tenant-scoped uniqueness of name under same parent; default `sortOrder` = max+10 or 10; always `isSystem=false`.
- Update/delete: system categories throw `SystemCategoryNotEditableException`; wrong-tenant treated as not found. Delete soft-deletes children when deleting a root.
- List for UI: `transactionCategories` via `TransactionService.listCategories(accountId)` (system + tenant customs).
- Ledger rows store `categoryId` + `categorySource` (MANUAL/RULE/IMPORTED/AI) — assignment mechanics in rule-engine / bulk-similar specs.

## Invariants

- System catalog is global; custom catalog is per tenant `accountId`.
- Max depth = 2 levels.
- Soft delete only for custom categories.

## Permissions

Backend enforcement:

- View: `banking:category:view` (`transactionCategories`).
- Manage CRUD: `banking:category:manage`.

UX hints: categories pages/pickers; not enforcement.

## Data And Contracts

- DB: `banking.transaction_categories` (V0_4 + later seed migrations).
- GraphQL: `TransactionCategory`, `CreateCategoryInput` / `UpdateCategoryInput`, mutations create/update/delete.
- Bruno: `List Transaction Categories.bru` (list only).

## Impact Surface

- Backend: TransactionCategoryService, category repo, TransactionCategoryResolver
- GraphQL + web categories UI + CategoryPicker/Select in finance-transactions
- Cross: rules/merchants/bulk assign categories; imports may map categories (imports batch)

## Decisions

- System taxonomy is migration-seeded and immutable via GraphQL.
- Custom categories never become system.

## Known Gaps

- No dedicated categories E2E file (coverage via transactions/rules/wishlist flows).
- Bruno lacks create/update/delete category requests.
- `pluggy_category_id` reserved in schema, unused in product path.

## Legacy Notes

- None used as authority.
