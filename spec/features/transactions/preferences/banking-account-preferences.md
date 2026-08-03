---
id: feature.transactions.preferences.banking-account-preferences
domain: transactions
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/preferences/AccountPreferencesService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/preferences/UserRuleCreationModeService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/preferences/AccountPreferences.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/preferences/BankingUserPreferences.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/preferences/RuleCreationMode.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/preferences/
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_10__create_account_preferences.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_14__split_user_and_account_preferences.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_24__rename_rule_application_mode_to_rule_creation_mode.sql
  tests: []
  frontend:
    - web/src/shared/providers/PreferencesProvider.tsx
    - web/src/app/profile/page.tsx
    - web/src/lib/hooks/useTransactionSave.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.categorization.rule-engine
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Preferences — Banking Account Preferences

## Glossary

- `accountId` — tenant key for **AccountPreferences** (workspace-level).
- `userId` — key for **BankingUserPreferences** (per-user banking prefs across memberships).
- `financialAccountId` — **not** used by these preference models.
- Security `userPreferences` (language/theme/…) live in the security subgraph — **not** this feature; do not conflate.

## Behavior

### Two banking preference surfaces

| | AccountPreferences | BankingUserPreferences |
|---|---|---|
| Key | `accountId` | `userId` |
| Field | `defaultTransactionStatus` | `ruleCreationMode` |
| Defaults | `POSTED` | `ALWAYS_PROMPT` |
| Table | `banking.account_preferences` | `banking.user_preferences` |
| Update gate | Must be **account owner** (`ACCOUNT_PREFERENCES_OWNER_REQUIRED`) | Caller user |
| Reject | `FUTURE` as default status; null update payload | Missing `ruleCreationMode` on update |

`RuleCreationMode`: `NEVER` | `ALWAYS` | `ALWAYS_PROMPT`.

### Consumers

- `defaultTransactionStatus`: applied when GraphQL create-transaction omits status (`TransactionResolver`).
- `ruleCreationMode`: drives implicit rule learning / similar-edit / bill-import learn-from-manual paths (`TransactionService`, bill import, web `useTransactionSave` / similar-edit preference helpers).

### History

- V0_10 created account prefs (originally included rule mode).
- V0_14 split rule mode to per-user `banking.user_preferences` (migrated from security/account where present).
- V0_24 renamed `rule_application_mode` → `rule_creation_mode`.

## Invariants

- Banking prefs ≠ security profile prefs (may share profile page UX sections).
- Account default status is tenant-wide; rule creation mode is per user.

## Permissions

- Queries `accountPreferences` / `bankingUserPreferences`: `banking:account:view`.
- Mutations `updateAccountPreferences` / `updateBankingUserPreferences`: also wired with `banking:account:view` (not a separate manage permission) — owner check still applies for account prefs updates.

## Data And Contracts

- GraphQL types/inputs above.
- Web: `PreferencesProvider` loads both; profile UI edits **`ruleCreationMode` only** — no control for `defaultTransactionStatus`.

## Impact Surface

- Backend prefs services + resolvers + createTransaction defaulting + rule learning
- Web PreferencesProvider + profile banking section + transaction save similar-edit
- Cross: categorization rule engine; security prefs only as boundary

## Decisions

- Split tenant default status vs per-user rule creation mode (V0_14).
- Default create status POSTED; default rule mode ALWAYS_PROMPT.
- **Product decision:** `defaultTransactionStatus` remains the API/default (POSTED) path — no profile UI required; owners may still change it via GraphQL API if permitted.

## Known Gaps

- Pref mutations wired with `banking:account:view` instead of manage semantics — [#601](https://github.com/invistus/invistus/issues/601).
- No Bruno / E2E for banking prefs (Batch 9 test chore [#599](https://github.com/invistus/invistus/issues/599)).
- No backend unit/IT for preference services → confidence **partial** ([#599](https://github.com/invistus/invistus/issues/599)).

## Legacy Notes

- None used as authority.
