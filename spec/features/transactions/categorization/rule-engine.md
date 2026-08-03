---
id: feature.transactions.categorization.rule-engine
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/DefaultTransactionRuleEngine.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionRuleMatcher.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionRuleService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionTypeOverrideSupport.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/AppliedRuleAuditSupport.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionRule.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/CategoryRuleEntityRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionCategoryResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_6__create_merchants_and_rules.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_15__create_account_disabled_rules.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_25__add_type_override_to_transaction_rules.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionCategorizationEngineTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/CategoryRuleServiceTest.kt
    - web/tests/e2e/personal-finance/transaction-rules.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/transaction-rules/
    - web/src/lib/graphql/operations/banking/queries.ts
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.categorization.category-catalog
  - feature.transactions.categorization.merchants
  - feature.transactions.categorization.bulk-and-similar-edit
  - feature.transactions.ledger-transactions.create-update-delete
  - feature.transactions.ledger-transactions.idempotency-and-bulk-caps
  - feature.transactions.ledger-transactions.mcp-change-log
schema: spec-metadata-v1
---

# Categorization — Rule Engine

## Glossary

- Explicit rule — user-created (`isImplicit=false`); system rules (`isSystem=true`, `accountId=null`).
- Implicit rule — learned from manual assignment (`isImplicit=true`, priority 60, CONTAINS).
- Disabled system rule — row in `account_disabled_rules` for tenant `accountId` (excluded from engine load).

## Behavior

### Match / precedence (`DefaultTransactionRuleEngine`)

1. Load rules for tenant: user rules + system rules **not** disabled (`findRulesForAccount`), ordered `priority ASC`, then `match_count DESC`, then `created_at DESC`.
2. Filter by `applicableTo` vs tx side (CREDIT→INCOME, DEBIT→EXPENSE); `ANY` always ok.
3. Match description via `TransactionRuleMatcher`: CONTAINS (whole-word after normalize), STARTS_WITH, EXACT, REGEX (invalid regex → no match).
4. First matching rule that still contributes an empty field wins that field; multiple rules may fill different fields (category, merchant, typeOverride, descriptionOverride, tagsOverride).
5. Stop early when all five enrichment slots filled; increment `matchCount` for all matched rule ids when recording.
6. Category id ignored if category row missing.

### When the engine runs

- **Manual create**: suggest with `excludeImplicit=true` (implicit rules skipped). Manual `categoryId` wins (`CategorySource.MANUAL`); else rule category → `RULE`. Tags: manual non-empty tags win; else rule tags. Type may be overridden via `TransactionTypeOverrideSupport`. Builds `appliedRuleHistory` / `appliedRuleIds` on the transaction. Failures are logged and create continues.
- **Skip**: `skipRuleEngine` or `investmentTransactionId` set (cash-leg / internal).
- **Update (manual source)**: may `learnFromManualAssignment` when category/merchant changes and user `RuleCreationMode.ALWAYS` (preferences batch).
- **Imports / bill import**: consume engine (imports batch — pointer only).
- **applyToHistorical**: re-applies one user-owned rule to non-MANUAL, non–BALANCE_ADJUSTMENT txs (see bulk-and-similar for fields + agent path).

### Rule CRUD / system disable

- Create/update/delete user rules: require at least one enrichment target; duplicate `(pattern, patternType)` per tenant forbidden; regex validated; soft delete. System rules not editable/deletable.
- `setSystemTransactionRuleEnabled`: toggle `account_disabled_rules` for system rules only.
- `reorderTransactionRules`: rewrites user-rule priorities to `index+1`.
- Default create priority **40**; implicit learn priority **60**.
- Preview: `previewTransactionRule(pattern, patternType, sample)`.

### Audit

- Per-tx `appliedRuleHistory` (`ruleId` + `appliedAt`) persisted as JSON; GraphQL exposes history + distinct `appliedRuleIds`.
- Create payload may return `appliedRules` objects for rules that contributed.

## Invariants

- Engine never overrides an explicit manual category on create when client sent `categoryId`.
- Historical apply does not overwrite category when `categorySource == MANUAL`.
- Disabled system rules are invisible to suggestion for that tenant.

## Permissions

Backend enforcement:

- View rules / preview / historical count: `banking:category:view`.
- Manage rules / reorder / enable-disable system / apply historical: `banking:category:manage`.
- Agent path for create rule + apply historical uses Batch 2 idempotency/bulk-cap when `AgentCallContext` present.

UX hints: `transaction-rules` management UI.

## Data And Contracts

- DB: `transaction_rules`, `account_disabled_rules` (V0_15), type override (V0_25); tx `applied_rule_ids` / `category_source` (V0_7).
- GraphQL: `transactionRules`, `transactionRulesByIds`, CRUD mutations, `applyTransactionRuleToHistorical`, `setSystemTransactionRuleEnabled`.

## Impact Surface

- Backend: rule engine/service/matcher, TransactionService create/update hooks, TransactionCategoryResolver
- GraphQL + E2E `transaction-rules.e2e.spec.ts` + unit engine tests
- Frontend transaction-rules pages
- Cross: merchants auto-rule; imports; preferences `RuleCreationMode`

## Decisions

- Field-wise first-match merge rather than single-rule-wins-all.
- System rules are disable-per-tenant, not deleted.
- Implicit rules excluded on manual create suggestion to avoid self-feedback loops.

## Known Gaps

- No Bruno for rule CRUD / apply historical.
- Preferences `RuleCreationMode` (NEVER / ALWAYS / ALWAYS_PROMPT) behavior fully owned by preferences batch; only consumption cited here.
- `TransactionRulePatternMatcher` vs `TransactionRuleMatcher` naming split — both used (preview vs engine).

## Legacy Notes

- None used as authority.
