---
title: Phase 7 — Transaction Rules UI (Regras de Categorização)
type: feature-spec
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, rules, categorization, automation, transaction-rules, implicit-learning, pattern-matching, tags, merchant, description-override]
ai_optimized: true
search_keywords: [transaction rule, regra, categorização automática, auto-categorize, pattern, CONTAINS, STARTS_WITH, EXACT, REGEX, implicit, isImplicit, priority, reorder, descriptionOverride, tagsOverride, rule engine, matchCount, learnFromManualAssignment]
related:
  - 03-features/personal-finance/backlogs/phase-6-transaction-categories.md
  - 03-features/personal-finance/backlogs/phase-4-v2-import-rules.md
  - 03-features/personal-finance/requirements/02-transactions.md
  - 03-features/security/requirements/01-user-preferences.md
last_updated: 2026-03-24
---

# Phase 7 — Transaction Rules UI (Regras de Categorização)

> **Purpose**: Define the complete UI/UX for the Transaction Rules management page and the missing backend gaps needed to support it. The rule engine itself is already production-ready; this phase surfaces it to the user through a dedicated settings screen and closes the remaining GraphQL schema gaps (`descriptionOverride`, `tagsOverride`, pattern preview, bulk reorder).

---

## Overview

Transaction rules let users automate enrichment of their transactions at the moment they enter the system — whether via manual entry, OFX/CSV/PDF import, or bank feed sync. A rule says: *"whenever a transaction description contains 'IFOOD', assign category Delivery and tag it as food-delivery"*.

The rule engine (`TransactionRuleEngine`) and storage layer (`banking.transaction_rules`) are **already implemented**. What is missing is:

1. A dedicated mobile screen for the user to view, create, edit, reorder, and delete their rules.
2. The `descriptionOverride` and `tagsOverride` fields on the GraphQL schema (present in the DB and domain model since V0_13, but never exposed).
3. A pattern-preview query so the user can test a pattern before saving.
4. A bulk-reorder mutation for drag-and-drop priority management.

---

## Current Implementation Status

### What exists today (backend — production ready)

| Component | File | Status |
|---|---|---|
| DB table `banking.transaction_rules` (V0_6) | `db/migration/V0_6__create_merchants_and_rules.sql` | ✅ Done |
| `description_override`, `tags_override_json` columns (V0_13) | `db/migration/V0_13__add_rule_overrides.sql` | ✅ Done |
| ~40 system seed rules (IFOOD, NETFLIX, etc.) | V0_6 migration | ✅ Done |
| `PatternType` enum (CONTAINS, STARTS_WITH, EXACT, REGEX) | `domain/transaction/TransactionEnums.kt` | ✅ Done |
| `TransactionRule` domain data class | `domain/transaction/TransactionRule.kt` | ✅ Done |
| `RuleSuggestion` DTO | `domain/transaction/RuleSuggestion.kt` | ✅ Done |
| `TransactionRuleEngine` interface + `DefaultTransactionRuleEngine` | `service/transaction/TransactionRuleEngine.kt` | ✅ Done |
| `TransactionRuleService` (CRUD + validation + learnFromManualAssignment) | `service/transaction/TransactionRuleService.kt` | ✅ Done |
| `CategoryRuleEntity` + mapper + repo | `model/transaction/`, `repo/` | ✅ Done |
| `transactionRules` query | `graphql/schema.graphqls` | ✅ Done |
| `createTransactionRule` / `updateTransactionRule` / `deleteTransactionRule` mutations | `graphql/schema.graphqls` | ✅ Done |
| Rule application on `TransactionService.create/update` | `service/transaction/TransactionService.kt` | ✅ Done |
| `learnFromManualAssignment` triggered on manual categorization | `TransactionRuleEngine.kt` | ✅ Done |

### What is missing (backlog — this phase)

| Gap | ID | Severity |
|---|---|---|
| `descriptionOverride` + `tagsOverride` fields on `TransactionRule` GQL type | RUL-01 | 🔴 Critical |
| `descriptionOverride` + `tagsOverride` on `CreateTransactionRuleInput` | RUL-02 | 🔴 Critical |
| `descriptionOverride` + `tagsOverride` + `applicableTo` on `UpdateTransactionRuleInput` | RUL-03 | 🔴 Critical |
| `previewTransactionRule` query (test pattern before saving) | RUL-04 | 🟠 High |
| `reorderTransactionRules` mutation (bulk priority update) | RUL-05 | 🟠 High |
| Mobile: Rules list screen | RUL-10 | 🔴 Critical |
| Mobile: Rule creation / edit form | RUL-11 | 🔴 Critical |
| Mobile: Pattern type picker + live preview | RUL-12 | 🟠 High |
| Mobile: Action builder (category + merchant + description + tags) | RUL-13 | 🔴 Critical |
| Mobile: Implicit rule management (view, promote, delete) | RUL-14 | 🟠 High |
| Mobile: Drag-to-reorder priority | RUL-15 | 🟡 Medium |
| Mobile: Match count badge + "last matched" label | RUL-16 | 🟢 Low |
| Unit tests for new GQL fields and reorder mutation | RUL-20 | 🟠 High |
| Integration test: create rule → import transactions → verify enrichment | RUL-21 | 🟡 Medium |

---

## Data Model

### Existing `banking.transaction_rules` table

```sql
CREATE TABLE banking.transaction_rules (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID,                           -- NULL = system rule; set for user rules
    category_id         UUID        REFERENCES banking.transaction_categories(id),
    merchant_id         UUID        REFERENCES banking.merchants(id),
    description_override VARCHAR(500),                 -- Added V0_13: replace raw description
    tags_override_json  TEXT,                          -- Added V0_13: JSON array e.g. ["food","delivery"]
    pattern             VARCHAR(1000) NOT NULL,
    pattern_type        VARCHAR(20)  NOT NULL
                        CHECK (pattern_type IN ('CONTAINS', 'STARTS_WITH', 'EXACT', 'REGEX')),
    applicable_to       VARCHAR(10)  NOT NULL DEFAULT 'ANY'
                        CHECK (applicable_to IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY')),
    priority            INT          NOT NULL DEFAULT 100,
    is_system           BOOLEAN      NOT NULL DEFAULT false,
    is_implicit         BOOLEAN      NOT NULL DEFAULT false,  -- auto-learned from manual categorization
    match_count         INT          NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- At least one action must be set
    CONSTRAINT chk_rule_has_action CHECK (
        category_id IS NOT NULL
        OR merchant_id IS NOT NULL
        OR description_override IS NOT NULL
        OR tags_override_json IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_transaction_rules_account_pattern
    ON banking.transaction_rules (account_id, lower(pattern), pattern_type)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transaction_rules_account ON banking.transaction_rules (account_id);
CREATE INDEX idx_transaction_rules_system  ON banking.transaction_rules (is_system) WHERE is_system = true;
```

No schema migration is needed for this phase — the columns already exist.

---

## Business Rules

### BR-1 — At least one action required

A rule must specify at least one of: `categoryId`, `merchantId`, `descriptionOverride`, `tagsOverride`. A rule that only changes the pattern with no action is rejected (`ValidationException`).

### BR-2 — System rules are read-only

Rules with `isSystem = true` are never returned by `transactionRules` query. Users cannot create, update, or delete system rules. Attempting to mutate a system rule returns `FORBIDDEN`.

### BR-3 — Implicit rules are user-owned

Rules with `isImplicit = true` are auto-created by `learnFromManualAssignment()` when the user manually categorizes a transaction. They are returned by `transactionRules` and can be:

- **Deleted** — removes the auto-learning entry.
- **Promoted** (edited) — updating any field clears `isImplicit = false`, converting it to an explicit rule.

Implicit rules are visually differentiated in the UI (distinct badge/label) but have full CRUD rights.

### BR-4 — Pattern uniqueness per account

Two rules for the same `account_id` cannot share the same (`pattern` case-insensitive, `patternType`) pair. Duplicate detection is enforced by the unique index and by `TransactionRuleService.create()`. The error surfaces as a user-visible message: *"Uma regra com esse padrão já existe."*

### BR-5 — REGEX patterns are validated server-side

Before persisting a rule with `patternType = REGEX`, the pattern is compiled and rejected with `ValidationException("Expressão regular inválida: <error>")` if it fails. The `previewTransactionRule` query applies the same validation.

### BR-6 — Priority ordering

Rules are applied in ascending priority order (lower number = applied first). When two rules match the same transaction, only the first match (lowest priority number) is applied. Default priority is `100`. Priority must be a positive integer (≥ 1).

### BR-7 — `applicableTo` filters by transaction direction

A rule with `applicableTo = EXPENSE` only fires for DEBIT transactions. `INCOME` only for CREDIT. `TRANSFER` only for transactions with `type = TRANSFER`. `ANY` (default) fires for all.

### BR-8 — `descriptionOverride` replaces `description` on match

When a rule has `descriptionOverride` set, the transaction's `description` field is replaced with the override value after the rule fires. The original value is preserved in `descriptionRaw` (immutable after initial import). For manually entered transactions `descriptionRaw` is set equal to `description` on creation.

### BR-9 — `tagsOverride` merges (does not replace) existing tags

`tagsOverride` adds the rule's tags to any existing tags on the transaction. It never removes tags the user already set.  For imports, the transaction starts with no tags, so `tagsOverride` is effectively the full tag list.

### BR-10 — Match count is a telemetry-only field

`matchCount` is incremented atomically by `suggestFromRulesRecordingMatch()` each time a rule fires. It is read-only to the client (not part of any input). It is used purely for display (usage badge) and future ML prioritization.

### BR-11 — Reorder mutation updates all affected priorities atomically

`reorderTransactionRules(ids: [ID!]!)` reassigns priorities to the ordered list of IDs. Position 0 gets priority 1, position 1 gets priority 2, etc. All IDs must belong to the same `account_id` (from JWT). The operation is wrapped in a single DB transaction; partial success is not possible.

---

## GraphQL API

### Schema additions (RUL-01 through RUL-05)

The following changes are **additive only** — no breaking changes to existing consumers.

#### Updated `TransactionRule` type

```graphql
type TransactionRule {
  id:                 ID!
  categoryId:         ID
  category:           TransactionCategory
  merchantId:         ID
  merchant:           Merchant
  descriptionOverride: String            # ← NEW (RUL-01)
  tagsOverride:       [String!]          # ← NEW (RUL-01)
  pattern:            String!
  patternType:        PatternType!
  applicableTo:       CategoryApplicableTo!
  priority:           Int!
  isSystem:           Boolean!
  isImplicit:         Boolean!           # true = auto-learned from manual categorization
  matchCount:         Int!
  createdAt:          DateTime!
}
```

#### Updated `CreateTransactionRuleInput`

```graphql
input CreateTransactionRuleInput {
  categoryId:          ID
  merchantId:          ID
  descriptionOverride: String            # ← NEW (RUL-02)
  tagsOverride:        [String!]         # ← NEW (RUL-02)
  pattern:             String!
  patternType:         PatternType!
  applicableTo:        CategoryApplicableTo!
  priority:            Int
}
```

#### Updated `UpdateTransactionRuleInput`

```graphql
input UpdateTransactionRuleInput {
  categoryId:          ID
  merchantId:          ID
  descriptionOverride: String            # ← NEW (RUL-03)
  tagsOverride:        [String!]         # ← NEW (RUL-03)
  pattern:             String
  patternType:         PatternType
  applicableTo:        CategoryApplicableTo  # ← NEW (RUL-03)
  priority:            Int
}
```

#### New query — pattern preview (RUL-04)

```graphql
extend type Query {
  """
  Tests whether the given pattern matches the provided sample description.
  Returns true if a match would fire; false otherwise.
  Throws a validation error if patternType is REGEX and the pattern is invalid.
  Does NOT persist anything.
  """
  previewTransactionRule(
    pattern:     String!
    patternType: PatternType!
    sample:      String!
  ): Boolean!
}
```

#### New mutation — bulk reorder (RUL-05)

```graphql
extend type Mutation {
  """
  Reorders user rules by reassigning priority = position+1 for each ID in the list.
  All IDs must belong to the authenticated account. System and implicit rules may be
  included but will be skipped (their priorities are managed automatically).
  Returns the updated list of affected rules.
  """
  reorderTransactionRules(ids: [ID!]!): [TransactionRule!]!
}
```

---

## Backend Architecture (Kotlin / Micronaut)

### Files to modify (no new files required)

```
graphql/schema.graphqls
  → Add descriptionOverride + tagsOverride to TransactionRule type
  → Add descriptionOverride + tagsOverride + applicableTo to Update/CreateInputs
  → Add previewTransactionRule query
  → Add reorderTransactionRules mutation

graphql/dto/TransactionRuleDTO.kt
  → Add descriptionOverride: String?
  → Add tagsOverride: List<String>?

graphql/resolver/TransactionResolver.kt  (or a dedicated TransactionRuleResolver.kt)
  → Wire previewTransactionRule → TransactionRuleService.previewPattern()
  → Wire reorderTransactionRules → TransactionRuleService.reorder()

service/transaction/TransactionRuleService.kt
  → previewPattern(pattern, patternType, sample): Boolean
  → reorder(ids: List<UUID>, accountId: UUID): List<TransactionRule>
  → Update create/update to accept + persist descriptionOverride + tagsOverride

model/transaction/CategoryRuleMapper.kt
  → Map descriptionOverride + tagsOverride (domain ↔ entity)
```

> **Note:** `descriptionOverride` and `tagsOverride` are already columns in the entity and domain model. The mapper and service may already handle them partially — verify before writing new code.

### `TransactionRuleService.reorder()` implementation sketch

```kotlin
@Transactional
fun reorder(ids: List<UUID>, accountId: UUID): List<TransactionRule> {
    require(ids.isNotEmpty()) { "ids must not be empty" }
    val rules = ruleRepository.findAllByIds(ids, accountId)  // validates ownership
    check(rules.size == ids.size) { "Some rule IDs not found or not owned by account" }
    ids.forEachIndexed { index, id ->
        ruleRepository.updatePriority(id, index + 1)
    }
    return ruleRepository.findAllUserRules(accountId)
}
```

---

## Mobile UI

### Screen: Rules List (`/settings/transaction-rules`)

```
┌─────────────────────────────────────────────────┐
│  ← Regras de Categorização               [+ Add] │
├─────────────────────────────────────────────────┤
│  Suas regras são aplicadas automaticamente       │
│  quando uma transação é criada ou importada.     │
├─────────────────────────────────────────────────┤
│  CRIADAS PELO USUÁRIO (3)                       │
│  ┌────────────────────────────────────────────┐ │
│  │ ⠿  "IFOOD"          CONTAINS               │ │
│  │    → 🍔 Delivery · #food-delivery   [47×]  │ │
│  ├────────────────────────────────────────────┤ │
│  │ ⠿  "FARMÁCIA"       CONTAINS               │ │
│  │    → 💊 Saúde                       [12×]  │ │
│  ├────────────────────────────────────────────┤ │
│  │ ⠿  "SALÁRIO"        STARTS_WITH            │ │
│  │    → 💼 Salário  (renda)            [3×]   │ │
│  └────────────────────────────────────────────┘ │
│                                                  │
│  CRIADAS AUTOMATICAMENTE (2)          [?]       │
│  ┌────────────────────────────────────────────┐ │
│  │    "Padaria Beirute"  EXACT                 │ │
│  │    → 🥐 Padaria & Café              [2×]   │ │
│  ├────────────────────────────────────────────┤ │
│  │    "Academia Smart"   EXACT                 │ │
│  │    → 🏋️ Saúde & Bem-estar           [1×]  │ │
│  └────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

**Behaviour:**

- **⠿ drag handle** — visible only on explicit rules; long-press activates reorder mode.
- **[N×] badge** — `matchCount`; hidden when 0.
- **[?] tooltip** on "Aprendidas automaticamente" section: *"Criadas automaticamente quando você categoriza uma transação manualmente. Você pode editá-las ou excluí-las."*
- Tapping a row opens the Edit form.
- Swipe-left on any row reveals a **Delete** action (with confirmation sheet).
- Empty state: illustration + *"Nenhuma regra ainda. Toque em + para criar sua primeira regra."*

### Screen: Rule Form (Create / Edit)

```
┌─────────────────────────────────────────────────┐
│  ← Nova Regra                          [Salvar]  │
├─────────────────────────────────────────────────┤
│  SE a descrição…                                 │
│                                                  │
│  [Tipo de padrão ▼]    CONTAINS                  │
│                                                  │
│  [Padrão ________________]  [Testar ▶]           │
│  ○ Corresponde  ○ Não corresponde  (preview)     │
│                                                  │
│  Aplicar para                                    │
│  ○ Qualquer  ○ Receita  ○ Despesa  ○ Transferência │
├─────────────────────────────────────────────────┤
│  ENTÃO…                                          │
│                                                  │
│  Categoria       [Selecionar categoria ▼]        │
│                                                  │
│  Comerciante     [Selecionar comerciante ▼]      │
│                                                  │
│  Renomear para   [________________]              │
│                  (deixe em branco para manter)   │
│                                                  │
│  Tags            [+ Adicionar tag]               │
│                  #food-delivery  ×               │
├─────────────────────────────────────────────────┤
│  Prioridade      [100]  (menor = primeiro)       │
└─────────────────────────────────────────────────┘
```

**Behaviour:**

- **[Testar ▶]** — fires `previewTransactionRule(pattern, patternType, sample: <typed text>)`. If the pattern field is empty, the button is disabled. Shows live match feedback below the field.
- **Pattern type picker** — bottom sheet with four options: `Contém`, `Começa com`, `Exato`, `Regex`. Selecting `Regex` shows an additional hint: *"Use expressões regulares Java/Kotlin. Ex: `^IFOOD.*`"*.
- **"ENTÃO" section** — at least one action must be filled before Save is enabled (enforced in the form, not only server-side).
- **Tags input** — chip-based multi-value input; tapping `+ Adicionar tag` opens a text field inline. Tags are lower-cased and trimmed.
- **Salvar** — disabled until at least one action is set and pattern is non-empty. On tap: calls `createTransactionRule` or `updateTransactionRule`. On success, navigates back to the list with a Snackbar: *"Regra salva."*. On error: inline field errors.
- **Edit mode** — if editing an implicit rule, a banner is shown: *"Esta regra foi aprendida automaticamente. Ao editar, ela se torna uma regra explícita."*

### Delete confirmation sheet

```
Excluir regra?
"IFOOD" CONTAINS → Delivery

Esta regra não será mais aplicada às próximas
transações. Transações já categorizadas não
são afetadas.

[Cancelar]   [Excluir]
```

### Reorder mode

- Activated by long-press on any drag handle (⠿).
- All rows show drag handles; implicit rules section collapses (cannot be reordered — priorities are auto-managed).
- On drop, fires `reorderTransactionRules` with the new order.
- A `Snackbar` confirms: *"Ordem salva."*
- Exit reorder mode: tap outside, or tap "Concluído" button that replaces [+ Add] while in reorder mode.

### Empty Actions state

If a rule exists with only a `pattern` and no actions (data integrity edge case from older imports), the rule list shows a warning chip: *"Incompleta — toque para editar"*.

---

## Web Desktop UI

### Route & entry point

**Route:** `/personal-finance/transaction-rules`

Entry points:
- **Finance Transactions page** — kebab menu or settings icon in the toolbar links to this page.
- **Global nav sidebar** — under "Finanças Pessoais" section, below Transações.

### Overall layout

The page uses `ManagementLayout` inside a `Container maxWidth="xl"`, matching the Settings → Users/Roles pattern. A right-side `Drawer` (temporary, anchor="right", ~480 px) opens for create and edit without leaving the list.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  [global nav sidebar — persistent, ~64px icon rail]                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  Regras de Categorização                                                         │ │
│  │  ─────────────────────────────────────────────────────────────────────────────  │ │
│  │  Suas regras são aplicadas automaticamente quando uma transação é criada ou      │ │
│  │  importada. Regras explícitas têm prioridade sobre regras aprendidas.            │ │
│  │                                                                                  │ │
│  │  [🔍 Buscar regras...                         ]          [+ Nova Regra]          │ │
│  │  ─────────────────────────────────────────────────────────────────────────────  │ │
│  │                                                                                  │ │
│  │  ▼  REGRAS EXPLÍCITAS  (3)                              [Reordenar]              │ │
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │ ⠿ │ "IFOOD"              CONTAINS  ·  Qualquer    │  → 🍔 Delivery        │  │ │
│  │  │   │                                               │  #food-delivery [47×] │  │ │
│  │  │   │                                               │                  ✏ 🗑 │  │ │
│  │  ├───┼───────────────────────────────────────────────┼───────────────────────┤  │ │
│  │  │ ⠿ │ "FARMÁCIA"           CONTAINS  ·  Despesa     │  → 💊 Saúde     [12×] │  │ │
│  │  │   │                                               │                  ✏ 🗑 │  │ │
│  │  ├───┼───────────────────────────────────────────────┼───────────────────────┤  │ │
│  │  │ ⠿ │ "SALÁRIO"            STARTS_WITH  ·  Receita  │  → 💼 Salário   [3×]  │  │ │
│  │  │   │                                               │  Renomeia: "Salário"  │  │ │
│  │  │   │                                               │                  ✏ 🗑 │  │ │
│  │  └───┴───────────────────────────────────────────────┴───────────────────────┘  │ │
│  │                                                                                  │ │
│  │  ▶  APRENDIDAS AUTOMATICAMENTE  (2)   ⓘ                                         │ │
│  │  ─────────────────────────────────────────────────────────────────────────────  │ │
│  │                                                                                  │ │
└──┴──────────────────────────────────────────────────────────────────────────────────┘
```

**Explicit rules section:**
- Column layout: drag handle `⠿` | pattern + type + scope | actions summary | edit `✏` + delete `🗑` icons.
- `⠿` drag handle uses `@dnd-kit/sortable`. Cursor changes to `grab` on hover. Dragging a row shows a translucent ghost and a blue insertion line. Reorder is committed on drop; fires `reorderTransactionRules`.
- Actions column shows category chip (icon + name), tags as `Chip` components, and "Renomeia:" label when `descriptionOverride` is set.
- `[47×]` match count renders as a small `Chip variant="outlined"` (grey, size small) inline with the actions.
- Row hover state: light `alpha(theme.palette.primary.main, 0.04)` background; edit/delete icons become fully visible (normally `opacity: 0.4`).
- `[Reordenar]` text button in the section header activates drag mode and changes to `[Concluído]` while active.

**Implicit rules section:**
- Collapsible `Accordion` (collapsed by default when empty or count ≤ 2; expanded when ≥ 3).
- `ⓘ` Tooltip: *"Criadas automaticamente quando você categoriza uma transação manualmente. Editar converte em regra explícita."*
- No drag handle — priorities for implicit rules are auto-managed.
- Same edit/delete icon pattern as explicit rows.

**Search input:**
- Filters the list client-side by `pattern` text. No server call needed (list is small).
- Clears with ESC key or × icon.
- While search is active, section headers are hidden and results are shown in a flat list.

**Empty state (no rules yet):**
```
┌────────────────────────────────────────────────────────────────────┐
│                                                                    │
│           [AutoFixHighIcon  ~80px, color: primary.light]           │
│                                                                    │
│              Nenhuma regra de categorização ainda                  │
│                                                                    │
│   Crie regras para categorizar transações automaticamente          │
│   ao importar extratos ou faturas.                                 │
│                                                                    │
│                       [+ Nova Regra]                               │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

### Drawer: Create / Edit Rule

Opens on `[+ Nova Regra]` or row `✏` click. `Drawer` component, `anchor="right"`, width 480 px (`size="sm"`). Temporary variant — clicking outside or pressing ESC closes it (with unsaved-changes guard if the form is dirty).

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  [rules list — dimmed]            ┌─────────────────────────────────────────────┐                  │
│                                   │  ×   Nova Regra                             │                  │
│                                   ├─────────────────────────────────────────────┤                  │
│                                   │                                             │                  │
│                                   │  SE a descrição…                            │                  │
│                                   │                                             │                  │
│                                   │  Tipo de padrão                             │                  │
│                                   │  ┌─────────────────────────────────────┐   │                  │
│                                   │  │  Contém                           ▼ │   │                  │
│                                   │  └─────────────────────────────────────┘   │                  │
│                                   │                                             │                  │
│                                   │  Padrão                                     │                  │
│                                   │  ┌─────────────────────────────┐  [Testar] │                  │
│                                   │  │  IFOOD                      │           │                  │
│                                   │  └─────────────────────────────┘           │                  │
│                                   │  ✓ "IFOOD RESTAURANTE LTDA" — Corresponde  │                  │
│                                   │                                             │                  │
│                                   │  Aplicar para                               │                  │
│                                   │  ● Qualquer  ○ Receita  ○ Despesa  ○ Trans. │                  │
│                                   │                                             │                  │
│                                   │  ─────────────────────────────────────────  │                  │
│                                   │  ENTÃO…                                     │                  │
│                                   │                                             │                  │
│                                   │  Categoria                                  │                  │
│                                   │  ┌─────────────────────────────────────┐   │                  │
│                                   │  │  🍔  Delivery                     ▼ │   │                  │
│                                   │  └─────────────────────────────────────┘   │                  │
│                                   │                                             │                  │
│                                   │  Comerciante                                │                  │
│                                   │  ┌─────────────────────────────────────┐   │                  │
│                                   │  │  iFood                            ▼ │   │                  │
│                                   │  └─────────────────────────────────────┘   │                  │
│                                   │                                             │                  │
│                                   │  Renomear descrição para (opcional)         │                  │
│                                   │  ┌─────────────────────────────────────┐   │                  │
│                                   │  │  iFood                              │   │                  │
│                                   │  └─────────────────────────────────────┘   │                  │
│                                   │                                             │                  │
│                                   │  Tags a adicionar                           │                  │
│                                   │  ┌─────────────────────────────────────┐   │                  │
│                                   │  │ #food-delivery ×  [+ adicionar tag] │   │                  │
│                                   │  └─────────────────────────────────────┘   │                  │
│                                   │                                             │                  │
│                                   │  ─────────────────────────────────────────  │                  │
│                                   │  Prioridade                                 │                  │
│                                   │  ┌───────┐                                  │                  │
│                                   │  │  100  │  ← menor número = maior prior.  │                  │
│                                   │  └───────┘                                  │                  │
│                                   │                                             │                  │
│                                   ├─────────────────────────────────────────────┤                  │
│                                   │  [Cancelar]                  [Salvar Regra] │                  │
│                                   └─────────────────────────────────────────────┘                  │
└────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Drawer behaviour details:**

- **[Testar] button** — `Button variant="outlined" size="small"`, placed inline to the right of the pattern `TextField`. Calls `previewTransactionRule` with the current pattern, patternType, and a hardcoded or user-entered sample (see below). Result renders below the input as a small `Alert`:
  - Match: `Alert severity="success"` — *`✓ "IFOOD RESTAURANTE LTDA" — Corresponde`*
  - No match: `Alert severity="warning"` — *`✗ "IFOOD RESTAURANTE LTDA" — Não corresponde`*
  - Invalid regex: `Alert severity="error"` — *`Expressão regular inválida: …`*
- **Sample text for preview** — a small secondary `TextField` labelled *"Testar com"* appears below the pattern input when `[Testar]` is clicked (or when `patternType = REGEX`), defaulting to an empty string. The user types a sample description there and the preview result updates live (debounced 300 ms).
- **Pattern type `Select`** — when `REGEX` is selected, a helper text appears: *"Use expressões regulares Java/Kotlin. Ex: `^SAL[\s/]`"*
- **Categoria `Select`** — searchable `Autocomplete` showing all `transactionCategories`, grouped by `applicableTo` section header (Despesas / Receitas / Qualquer). Each option shows category icon + name. Has a *"Nenhuma"* option at top to clear.
- **Comerciante `Select`** — searchable `Autocomplete` for merchants. Shows logo (favicon from `logo.dev`) + name. Has *"Nenhum"* option.
- **Tags input** — `TagsInput` component (chip-based); Enter or comma commits a tag; tags are lower-cased + trimmed.
- **[Salvar Regra] button** — `variant="contained"`, disabled while form is invalid (empty pattern OR no actions set). Has glow shadow matching primary colour. Shows spinner while mutation is in-flight.
- **Edit mode** — drawer title changes to *"Editar Regra"*. If editing an implicit rule, an `Alert severity="info"` banner appears above the SE section: *"Esta regra foi aprendida automaticamente. Ao salvar as alterações, ela se tornará uma regra explícita."*
- **Unsaved-changes guard** — if the form is dirty and the user clicks `×` or the backdrop, a MUI `Dialog` confirms: *"Descartar alterações? As mudanças não salvas serão perdidas."* — buttons `[Continuar editando]` and `[Descartar]`.

---

### Dialog: Delete Confirmation

Standard MUI `Dialog` (not a drawer). Triggered by `🗑` icon.

```
┌───────────────────────────────────────────────────────┐
│  Excluir regra?                                        │
│                                                        │
│  "IFOOD"  ·  Contém  ·  Despesa                        │
│  → 🍔 Delivery  ·  #food-delivery                      │
│                                                        │
│  Esta regra não será mais aplicada às próximas         │
│  transações. Transações já categorizadas não são       │
│  afetadas.                                             │
│                                                        │
│                     [Cancelar]   [Excluir]             │
└───────────────────────────────────────────────────────┘
```

`[Excluir]` is `Button variant="contained" color="error"`. Shows spinner while mutation is in-flight; disables both buttons to prevent double-submit.

---

### Component tree (implementation reference)

```
/personal-finance/transaction-rules/page.tsx
  TransactionRulesPage
    ManagementLayout
      ManagementLayout.Header
        Typography (page title + subtitle)
        Stack direction="row"
          SearchInput                        ← client-side filter on rule.pattern
          Button [+ Nova Regra]              ← opens drawer in create mode
      ManagementLayout.Content
        ExplicitRulesSection
          SectionHeader + [Reordenar] toggle
          DndContext (@dnd-kit)
            SortableContext
              TransactionRuleRow[]           ← each row: DragHandle + pattern cols + actions + icon btns
          EmptySection (when count = 0)
        ImplicitRulesSection (Accordion)
          AccordionSummary (count badge + ⓘ tooltip)
          AccordionDetails
            TransactionRuleRow[]             ← same row, no DragHandle
        EmptyState (when both sections empty)
        DeleteRuleDialog
      ManagementLayout.Drawer
        TransactionRuleDrawer               ← Drawer anchor="right" size="sm"
          Drawer.Header (title + × close)
          Drawer.Body
            ImplicitRuleBanner?             ← Alert info, shown when editing implicit
            RuleConditionSection            ← SE…
              PatternTypeSelect
              PatternTextField + TestButton
              PreviewSampleField?           ← shown after first [Testar] click
              PreviewAlert?                 ← success / warning / error
              ApplicableToRadioGroup
            Divider
            RuleActionSection               ← ENTÃO…
              CategoryAutocomplete
              MerchantAutocomplete
              DescriptionOverrideTextField
              TagsInput
            Divider
            PriorityNumberField
          Drawer.Footer
            DrawerActionButtons [Cancelar] [Salvar Regra]
        UnsavedChangesDialog
```

---

## Integration Points

### On transaction creation (manual)

`TransactionService.create()` already calls the rule engine after saving the transaction. With `ruleApplicationMode` (from `AccountPreferences`):

- `NEVER` — rules not applied.
- `ALWAYS` — rules applied silently; `categorySource = RULE`, `merchantId` set.
- `ALWAYS_PROMPT` — rules applied; resolver returns `appliedRules: [TransactionRule!]` in the response payload. Client shows confirmation banner.

### On import (OFX / CSV / PDF)

Each parsed transaction row passes through `TransactionRuleEngine.suggestFromRulesRecordingMatch()` before being persisted. The same `ruleApplicationMode` governs application.

### On manual categorization (implicit learning)

After the user explicitly sets a category on a transaction via `updateTransaction(categoryId: ...)`, `TransactionRuleEngine.learnFromManualAssignment()` checks if an implicit rule already exists for that description. If not, it creates one with `isImplicit = true`, `patternType = EXACT`, and the transaction's normalized description as pattern.

---

## User Scenarios

**Scenario 1 — User creates a rule for a recurring merchant**
User notices IFOOD transactions are always uncategorized. Opens Settings → Regras → [+ Add]. Types "IFOOD", selects CONTAINS, picks category "Delivery", adds tag "food-delivery". Taps Testar with "IFOOD RESTAURANTE LTDA" — sees "Corresponde ✓". Saves. Next import: all IFOOD rows are auto-tagged.

**Scenario 2 — User edits an implicit rule**
User manually categorized "Padaria Beirute" as "Padaria & Café". The system created an EXACT implicit rule. Later the user notices all "PADARIA BEIRUTE" (uppercase variant) imports are not matched. Opens the rule, changes patternType to CONTAINS, pattern to "BEIRUTE". The rule is automatically promoted to explicit. Future transactions with "BEIRUTE" in the description are matched.

**Scenario 3 — User sets a description override**
Bank statements from Bradesco always have "DEB AUTO 00000234" as description for rent payments. User creates a CONTAINS rule for "DEB AUTO 00000234", sets `descriptionOverride = "Aluguel"`, and picks category "Moradia". After the next import, the transaction list shows "Aluguel" instead of the cryptic bank code.

**Scenario 4 — User reorders rules for precedence**
User has two rules: "UBER" CONTAINS → category Transporte (priority 100) and "UBER EATS" CONTAINS → category Delivery (priority 100). Both share the same priority, so the first matched rule wins non-deterministically. User enters reorder mode, drags "UBER EATS" above "UBER" (priority 1 vs 2). Now "UBER EATS" always resolves to Delivery before "UBER" matches.

**Scenario 5 — User deletes an implicit rule to stop auto-categorization**
User categorized "Academia Smart" as "Saúde" once. But next month they cancelled the membership and don't want auto-categorization. They swipe-delete the implicit rule. Future transactions with that description land uncategorized.

**Scenario 6 — User creates a REGEX rule for salary variations**
Salary descriptions vary: "SAL/REF 12/2025", "SAL REF JAN", "SALÁRIO MENSAL". User creates a REGEX rule `^SAL(ÁRIO)?[\s/]` → category "Salário" with `applicableTo = INCOME`. Taps Testar with "SAL/REF 12/2025" — shows "Corresponde ✓". Saves. All salary variants are now auto-categorized.

**Scenario 7 — Import with ALWAYS_PROMPT shows applied rules banner**
Account preference is `ALWAYS_PROMPT`. User imports a CSV with 30 transactions. The import screen shows a step: *"12 transações foram categorizadas automaticamente por 4 regras."* with a breakdown chip per rule. User can tap each chip to review matches. Accepts all → import completes.

---

## Validations & Restrictions

| Field | Rule | Enforced in |
|---|---|---|
| `pattern` | Non-empty, max 1000 chars | Service + DB |
| `patternType = REGEX` | Pattern must be a valid Java regex | Service (`Pattern.compile`) |
| `pattern` + `patternType` | Unique per `account_id` (case-insensitive) | DB unique index + Service |
| `priority` | Must be ≥ 1 | Service |
| At least one action | `categoryId`, `merchantId`, `descriptionOverride`, or `tagsOverride` must be non-null | DB CHECK + Service |
| `descriptionOverride` | Max 500 chars | DB column length + Service |
| `tagsOverride` items | Each tag: max 50 chars, no commas | Service |
| `reorderTransactionRules` | All IDs must belong to the authenticated `account_id` | Service |
| System rules | Cannot be mutated (`isSystem = true`) | Service (`ForbiddenException`) |
| `applicableTo` | Must be `INCOME`, `EXPENSE`, `TRANSFER`, or `ANY` | DB CHECK + GraphQL enum |

---

## Phasing

| Phase | Scope | Dependencies |
|---|---|---|
| **Sprint 1** | RUL-01 to RUL-03 — GraphQL schema additions for `descriptionOverride`, `tagsOverride`, `applicableTo`; backend mapper + service updates | Verify V0_13 mapper already handles these fields before writing new code |
| **Sprint 2** | RUL-04 `previewTransactionRule` query; RUL-10 + RUL-11 Mobile rules list + rule form (create/edit/delete) | Sprint 1 complete |
| **Sprint 3** | RUL-12 Pattern type picker + live preview in form; RUL-13 Action builder (all four action types); RUL-14 Implicit rule section + promotion UX | Sprint 2 complete |
| **Sprint 4** | RUL-05 `reorderTransactionRules` mutation; RUL-15 Drag-to-reorder on list; RUL-16 Match count badge | Sprint 2 complete |
| **Sprint 5** | RUL-20 + RUL-21 Unit + integration tests for all new endpoints | Sprint 1–4 complete |

---

## Open Questions / Gaps

**7.1 Does the mapper already handle `descriptionOverride` and `tagsOverride`?**
`CategoryRuleMapper.kt` may already map these fields since V0_13 added the DB columns. Verify before writing new code. If it does, Sprint 1 reduces to GraphQL schema changes only.

**7.2 Implicit rule deduplication edge case.**
`learnFromManualAssignment()` uses EXACT pattern on the normalized description. If the user categorizes the same description differently across two transactions, two implicit rules with conflicting actions may be created. The current service logic should enforce the uniqueness constraint — but the UX for a conflict (update existing vs. create duplicate) is undefined. Current behavior: the DB unique index rejects the duplicate, the exception is silently swallowed. This may leave the user confused if the implicit rule was not created.

**7.3 `ALWAYS_PROMPT` for bulk import UX is deferred.**
The current spec (see `01-user-preferences.md §11.4`) flags bulk import with `ALWAYS_PROMPT` as out-of-scope for the initial implementation. The batch review screen for import enrichment is a future item (tentatively Phase 7.5 or Phase 8).

**7.4 No rule export / import.**
Power users (Monarch Money, YNAB) value the ability to export their rule sets and import them into a new account or share with others. This is explicitly out of scope for Phase 7 but should be tracked as a future item.

**7.5 `tagsOverride` merge vs. replace.**
Current business rule (BR-9) specifies merge semantics. However, some users may want a rule that replaces all tags (e.g., "remove the auto-tag from import and apply only my tag"). A future `tagsMerge: Boolean` flag could support this; for now merge-only keeps it simple.

**7.6 No per-rule enable/disable toggle.**
Users might want to temporarily disable a rule without deleting it (e.g., during a period of manual categorization). A future `isEnabled: Boolean` column could support this; soft-disable avoids the need to recreate rules after re-enabling.
