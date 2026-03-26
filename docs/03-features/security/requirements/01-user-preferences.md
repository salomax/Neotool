# User Preferences — Requirements Document

**Services:** Invistus Security Service · Invistus Banking Service
**Module:** Settings — User & Account Preferences
**Document date:** 2026-03-24

---

## 1. Overview

User Preferences is a cross-cutting feature that lets each user control how the application behaves for them across devices. Preferences are split into two tiers:

- **User-level preferences** — personal to the authenticated user; follow them across every account (tenant) they belong to. Stored in the `security` service.
- **Account-level preferences** — scoped to a tenant (account); shared among all members of that account. Stored in the `banking` service because they govern banking-specific behaviour.

Current state:

- Language and theme are already surfaced in the mobile Settings screen but are stored only in local device state — they reset on reinstall and do not sync across devices.
- Transaction rule application behaviour (NEVER / ALWAYS / ALWAYS_PROMPT) is undefined; the rule engine currently applies all matching rules unconditionally.

After this feature:

- Language and theme are persisted server-side and hydrated on every app load.
- The user can blur monetary amounts on the dashboard for privacy.
- Each account can configure how transaction rules are applied when a new transaction is created or imported.
- A sensible default is returned for every preference even when no row exists, avoiding any mandatory onboarding step.

---

## 2. Preference Model

### 2.1 User Preferences

Source (after implementation): `UserPreferences.kt`, `V0_X__create_user_preferences.sql` (security service)

| Field | Type | Default | Notes |
|---|---|---|---|
| `userId` | UUID PK → `security.users` | — | One row per user; auto-created on first explicit save |
| `language` | `Language` | `PORTUGUESE` | `ENGLISH` or `PORTUGUESE`; maps to i18n locale codes `en` / `pt` |
| `theme` | `Theme` | `SYSTEM` | `LIGHT`, `DARK`, or `SYSTEM` (follows device OS setting) |
| `blurBalances` | Boolean | `false` | When `true`, monetary amounts are masked on the dashboard and account list |
| `firstDayOfWeek` | Int (1–7) | `1` (Monday) | ISO weekday; affects weekly summaries and calendar views. 1 = Monday, 7 = Sunday |
| `createdAt` | TIMESTAMPTZ | server time | |
| `updatedAt` | TIMESTAMPTZ | server time | |

### 2.2 Account Preferences

Source (after implementation): `AccountPreferences.kt`, `V0_12__create_account_preferences.sql` (banking service)

| Field | Type | Default | Notes |
|---|---|---|---|
| `accountId` | UUID PK | — | One row per tenant; auto-created on first explicit save |
| `ruleApplicationMode` | `RuleApplicationMode` | `ALWAYS_PROMPT` | Controls how matching transaction rules are applied (see §3.2) |
| `defaultTransactionStatus` | `TransactionStatus` | `POSTED` | Pre-fills the status field when the user creates a transaction manually |
| `createdAt` | TIMESTAMPTZ | server time | |
| `updatedAt` | TIMESTAMPTZ | server time | |

---

## 3. Business Rules

### 3.1 Lazy Initialisation — No Row = Defaults

Neither `user_preferences` nor `account_preferences` requires a row to exist. When the `userPreferences` or `accountPreferences` query is executed and no row is found, the service returns an in-memory object carrying all default values. A row is only inserted when the user explicitly saves a preference via the corresponding mutation. This avoids a backfill migration for existing users and accounts.

### 3.2 Rule Application Mode

`ruleApplicationMode` controls what happens when a transaction matches one or more `TransactionRule` patterns at creation or import time:

| Mode | Behaviour |
|---|---|
| `NEVER` | Matching rules are ignored. Transaction is saved with no automatic enrichment. |
| `ALWAYS` | All matching rules are applied silently (category, merchant, description override, tags). |
| `ALWAYS_PROMPT` | Matching rules are applied optimistically; the UI shows a confirmation banner listing the applied rules so the user can revert individual overrides before finalising. |

The mode is read by `TransactionService` after every `create` and `update` call. It has no effect on the `delete` flow.

For `ALWAYS_PROMPT`, the backend applies the rules exactly as `ALWAYS` does and returns the enriched transaction together with a list of applied rule IDs. The client is responsible for rendering the confirmation UI. If the user reverts a rule, the client fires a follow-up `updateTransaction` mutation with the corrected fields.

### 3.3 Language Hydration on App Load

On every authenticated app launch the mobile client calls `userPreferences` and applies the returned `language` value to `i18n.changeLanguage()` before rendering the main navigation tree. If the query fails (network error), the client falls back to the last known local value stored in `AsyncStorage`.

### 3.4 Theme Hydration on App Load

Same strategy as language. The returned `theme` value maps to the `useTheme` hook:

- `LIGHT` → force light colour scheme
- `DARK` → force dark colour scheme
- `SYSTEM` → follow `Appearance.getColorScheme()` (React Native)

### 3.5 Blur Balances

When `blurBalances = true`:

- All `amount` values on the account list, dashboard cards, transaction list, and month summary are replaced with `••••••` in the UI.
- The preference is applied purely client-side; the API always returns real values.
- A tap-to-reveal gesture may be added in a future phase but is out of scope here.

### 3.6 `firstDayOfWeek`

Affects only presentation logic (calendar/week views). The stored transaction dates are not altered. Validated server-side: value must be between 1 and 7 inclusive; any other value throws `IllegalArgumentException`.

### 3.7 `defaultTransactionStatus`

Pre-fills the status selector in the manual transaction creation form. The user can still override it per-transaction. Only `POSTED` and `PENDING` are valid values here; `FUTURE` is excluded because it implies a scheduled/planned entry and is set explicitly by the user.

### 3.8 Partial Updates

Both mutations accept sparse inputs — only fields present in the input are updated. Missing fields preserve their current value (or their default if no row exists yet). This avoids the client needing to send the full object on every change.

### 3.9 Ownership

- `userPreferences` query and `updateUserPreferences` mutation are scoped to the authenticated `userId` from the JWT. A user cannot read or write another user's preferences.
- `accountPreferences` query and `updateAccountPreferences` mutation are scoped to the authenticated `accountId` (tenant). Any member of the account can read preferences; only account owners (role `OWNER`) may update them (enforced at the service layer, not GraphQL).

---

## 4. Data Model & DB

### 4.1 `security.user_preferences`

Migration: `security/src/main/resources/db/migration/V0_X__create_user_preferences.sql`

```sql
CREATE TABLE security.user_preferences (
    user_id           UUID        PRIMARY KEY REFERENCES security.users(id),
    language          VARCHAR(10) NOT NULL DEFAULT 'PORTUGUESE'
                      CHECK (language IN ('ENGLISH', 'PORTUGUESE')),
    theme             VARCHAR(10) NOT NULL DEFAULT 'SYSTEM'
                      CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    blur_balances     BOOLEAN     NOT NULL DEFAULT false,
    first_day_of_week SMALLINT    NOT NULL DEFAULT 1
                      CHECK (first_day_of_week BETWEEN 1 AND 7),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4.2 `banking.account_preferences`

Migration: `banking/src/main/resources/db/migration/V0_12__create_account_preferences.sql`

```sql
CREATE TABLE banking.account_preferences (
    account_id              UUID        PRIMARY KEY,
    rule_application        VARCHAR(20) NOT NULL DEFAULT 'ALWAYS_PROMPT'
                            CHECK (rule_application IN ('NEVER', 'ALWAYS', 'ALWAYS_PROMPT')),
    default_txn_status      VARCHAR(10) NOT NULL DEFAULT 'POSTED'
                            CHECK (default_txn_status IN ('POSTED', 'PENDING')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

No FK from `banking.account_preferences` to `security.accounts` — the `account_id` is implicitly trusted from the JWT principal, consistent with all other banking tables.

---

## 5. GraphQL API

### 5.1 Enums

```graphql
enum Language           { ENGLISH PORTUGUESE }
enum Theme              { LIGHT DARK SYSTEM }
enum RuleApplicationMode { NEVER ALWAYS ALWAYS_PROMPT }
```

`TransactionStatus` is already defined in `schema.graphqls`; no new enum needed for `defaultTransactionStatus`.

### 5.2 Types

```graphql
type UserPreferences {
  language:        Language!
  theme:           Theme!
  blurBalances:    Boolean!
  firstDayOfWeek:  Int!
}

type AccountPreferences {
  ruleApplicationMode:      RuleApplicationMode!
  defaultTransactionStatus: TransactionStatus!
}
```

### 5.3 Queries

```graphql
extend type Query {
  userPreferences:    UserPreferences!
  accountPreferences: AccountPreferences!
}
```

Both queries always return a non-null object. When no row exists, defaults are returned without side effects.

### 5.4 Mutations

```graphql
extend type Mutation {
  updateUserPreferences(input: UpdateUserPreferencesInput!):       UserPreferences!
  updateAccountPreferences(input: UpdateAccountPreferencesInput!): AccountPreferences!
}

input UpdateUserPreferencesInput {
  language:       Language
  theme:          Theme
  blurBalances:   Boolean
  firstDayOfWeek: Int
}

input UpdateAccountPreferencesInput {
  ruleApplicationMode:      RuleApplicationMode
  defaultTransactionStatus: TransactionStatus
}
```

All input fields are optional (nullable). At least one must be non-null; passing an empty input object throws a `ValidationException`.

---

## 6. Backend Architecture (Kotlin / Micronaut)

Follows the project's clean architecture pattern: `domain` → `Default*` implementation → `*EntityRepository` (JPA).

### 6.1 Security Service — UserPreferences

```
domain/preferences/
  UserPreferences.kt                    data class
  UserPreferencesRepository.kt          interface: get(userId), upsert(prefs)

model/preferences/
  UserPreferencesEntity.kt              @Entity (table = "security.user_preferences")
  UserPreferencesMapper.kt              Entity ↔ domain conversion

repo/
  UserPreferencesEntityRepository.kt   JpaRepository<UserPreferencesEntity, UUID>
  DefaultUserPreferencesRepository.kt  @Singleton implements UserPreferencesRepository

service/preferences/
  UserPreferencesService.kt             get() → lazy-default; update(patch)

graphql/
  UserPreferencesResolver.kt            query + mutation wired via BankingWiringFactory
  dto/UserPreferencesDTO.kt
```

### 6.2 Banking Service — AccountPreferences

```
domain/preferences/
  AccountPreferences.kt                 data class
  AccountPreferencesRepository.kt       interface: get(accountId), upsert(prefs)

model/preferences/
  AccountPreferencesEntity.kt           @Entity (table = "banking.account_preferences")
  AccountPreferencesMapper.kt

repo/
  AccountPreferencesEntityRepository.kt JpaRepository<AccountPreferencesEntity, UUID>
  DefaultAccountPreferencesRepository.kt

service/preferences/
  AccountPreferencesService.kt          get() → lazy-default; update(patch)

graphql/
  AccountPreferencesResolver.kt
  dto/AccountPreferencesDTO.kt
```

### 6.3 Integration with TransactionService

`TransactionService.create()` and `TransactionService.update()` already call `SnapshotComputationService.recomputeChain()` after mutation. Rule application is inserted before that:

```
TransactionService.create(command)
  → save transaction (no enrichment)
  → val mode = accountPreferencesService.get(accountId).ruleApplicationMode
  → if mode != NEVER: val applied = transactionRuleService.applyRules(transaction)
  → if mode == ALWAYS: persist enriched transaction silently
  → if mode == ALWAYS_PROMPT: persist enriched transaction, return applied rule IDs to resolver
  → recomputeChain(...)
```

The GraphQL `createTransaction` response payload is extended with an optional `appliedRules: [TransactionRule!]` field used only when `mode = ALWAYS_PROMPT`.

---

## 7. Mobile UI

### 7.1 Settings Screen Layout

```
Settings
├── Appearance
│   ├── Theme          (Light / Dark / System)
│   └── Language       (English / Português)
├── Privacy
│   └── Blur balances  (toggle)
└── Banking
    ├── Apply transaction rules   (Never / Always / Always ask)
    └── Default transaction status (Posted / Pending)
```

"Banking" section is visible to all account members but the two items within it are disabled with a tooltip (`"Only the account owner can change this setting"`) for non-owner members.

### 7.2 Startup Sequence

```
App launch
  → authenticate (existing flow)
  → userPreferences query
      → i18n.changeLanguage(language)
      → setTheme(theme)
      → setBlurBalances(blurBalances)
  → render navigation tree
```

On failure (network / auth error), fall back to values persisted in `AsyncStorage` under key `@invistus/userPreferences`.

### 7.3 Mutation Flow

Settings are saved immediately on change (no explicit "Save" button), using an optimistic update:

1. Update local state immediately.
2. Fire `updateUserPreferences` / `updateAccountPreferences` mutation.
3. On error: revert local state and show a `Snackbar` error.

---

## 8. User Scenarios

**Scenario 1 — Language sync across devices**
User changes language to English on their phone. On next login from a tablet, the `userPreferences` query returns `ENGLISH` and the app renders in English without any additional action from the user.

**Scenario 2 — Theme follows device (default)**
User never touches the theme setting. On a device in dark mode, the app renders in dark. On a device in light mode, the app renders in light. `theme = SYSTEM` (default) delegates to the OS.

**Scenario 3 — Blur balances for privacy**
User is in a public space. They toggle "Blur balances" on. All monetary amounts on the dashboard and account list are immediately replaced with `••••••`. The preference persists across restarts; no amount is visible until the user toggles it off again.

**Scenario 4 — Rule application: ALWAYS**
Account owner sets `ruleApplicationMode = ALWAYS`. User imports an OFX file with 40 transactions. All matching rules (category, merchant, tags) are applied silently. The transaction list shows enriched data with no confirmation step.

**Scenario 5 — Rule application: ALWAYS_PROMPT**
Account owner leaves the default `ALWAYS_PROMPT`. User creates a new expense. A matching rule assigns category "Restaurantes". The app shows a confirmation banner: *"1 rule applied — Restaurantes · Undo"*. The user accepts (dismisses the banner) or taps Undo to revert to uncategorized.

**Scenario 6 — Rule application: NEVER**
Account owner sets `ruleApplicationMode = NEVER`. User imports transactions. No rules are applied; every transaction lands uncategorized. The user can still apply rules manually from the transaction detail screen (out of scope for this document).

**Scenario 7 — Default status = PENDING**
Account owner sets `defaultTransactionStatus = PENDING`. Every time a team member opens the "New Transaction" form, the status field is pre-filled as "Pending" instead of "Posted". The member can still change it before saving.

**Scenario 8 — Non-owner tries to change Banking preferences**
A non-owner account member opens Settings. The "Apply transaction rules" and "Default transaction status" rows are rendered with a lock icon and are not tappable. Tapping them shows a tooltip explaining that only the account owner can change these settings.

**Scenario 9 — First-time user (no preference row exists)**
A brand new user opens Settings. The `userPreferences` query returns defaults (`PORTUGUESE`, `SYSTEM`, `blurBalances = false`, `firstDayOfWeek = 1`). No row is created in the DB. The user changes theme to `DARK`. The mutation fires, a row is inserted (upsert), and subsequent queries return the saved value.

---

## 9. Validations & Restrictions

| Field | Rule | Enforced in |
|---|---|---|
| `language` | Must be `ENGLISH` or `PORTUGUESE` | DB CHECK + GraphQL enum |
| `theme` | Must be `LIGHT`, `DARK`, or `SYSTEM` | DB CHECK + GraphQL enum |
| `firstDayOfWeek` | 1–7 inclusive | DB CHECK + service layer |
| `ruleApplicationMode` | Must be `NEVER`, `ALWAYS`, or `ALWAYS_PROMPT` | DB CHECK + GraphQL enum |
| `defaultTransactionStatus` | Must be `POSTED` or `PENDING` | DB CHECK + service layer |
| `updateUserPreferences` input | At least one field must be non-null | Service layer (`ValidationException`) |
| `updateAccountPreferences` input | At least one field must be non-null | Service layer (`ValidationException`) |
| `updateAccountPreferences` | Caller must have role `OWNER` in the account | Service layer (role check on JWT principal) |

---

## 10. Phasing

| Phase | Scope | Dependencies |
|---|---|---|
| **Phase 1** | `UserPreferences`: language, theme, blurBalances | Requires security service migration + GraphQL wiring. No banking changes. Unblocks cross-device settings sync immediately. |
| **Phase 2** | `AccountPreferences`: ruleApplicationMode | Requires banking service migration + integration with `TransactionService`. Unblocks smart rule auto-apply during import. |
| **Phase 3** | `firstDayOfWeek`, `defaultTransactionStatus` | Lower priority; only impactful once weekly summaries and fast-entry forms are built. |

---

## 11. Open Questions / Gaps

**11.1 `firstDayOfWeek` has no consumer today.**
No weekly summary view exists yet. The preference can be stored and returned but will have no visible effect until a weekly breakdown screen is built.

**11.2 `blurBalances` is client-only.**
The API always returns real values. If the app is accessed via a browser devtools network panel, amounts are visible regardless of the setting. A server-side masking option could be added in a future privacy-hardening phase.

**11.3 Owner-only restriction for `AccountPreferences` needs a role resolution helper.**
The security service exposes `AccountMembership` with a role field. The banking service needs to call the security service (or use a shared principal enrichment mechanism) to resolve whether the authenticated user is an `OWNER` of the current account. The cross-service call pattern is not yet standardised.

**11.4 `ALWAYS_PROMPT` UX for bulk import is undefined.**
When 100 transactions are imported with `ALWAYS_PROMPT`, surfacing a confirmation banner for each rule application would be unusable. A batched review screen (showing all enriched transactions grouped by applied rule) may be required. This is out of scope for the initial implementation.

**11.5 No notification preferences in this phase.**
Bill due-date reminders, large-transaction alerts, and budget warnings are expected in a future comms/notification feature. They are intentionally excluded here to keep the schema minimal.
