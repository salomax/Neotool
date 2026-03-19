---
title: Phase 6 — Transaction Categories (Categorização)
type: feature-spec
category: personal-finance
status: partially-implemented
version: 1.0.0
tags: [feature, personal-finance, categories, categorization, auto-categorize, rule-engine, analytics, MUI-icons]
ai_optimized: true
search_keywords: [category, categoria, TransactionCategory, CategoryApplicableTo, INCOME, EXPENSE, TRANSFER, ANY, icon, MUI icon, auto-categorize, rule-based, bulkCategorize, CategorySource, MANUAL, RULE, IMPORTED, category breakdown, uncategorized, createCategory, updateCategory, deleteCategory]
related:
  - 03-features/personal-finance/phase-2-transactions.md
  - 03-features/personal-finance/phase-2-monthly-summary-snapshots.md
  - 03-features/personal-finance/phase-3-credit-card-bills.md
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-03-19
---

# Phase 6 — Transaction Categories (Categorização)

> **Purpose**: Define the complete categorization experience for transactions — from the existing two-level category hierarchy already in production, through manual CRUD and bulk operations, to automatic rule-based categorization with implicit learning. Covers icon strategy, frontend UX, analytics breakdowns, and the auto-categorization pipeline.

---

## Overview

Transaction categorization is a foundational feature for any personal finance manager. Users must be able to quickly understand where their money is going. The system needs to be:

1. **Fluid** — categorizing a transaction takes one tap/click, not multiple screens.
2. **Assertive** — the right category is surfaced first, not buried.
3. **Automatic** — repetitive patterns (IFOOD*, NETFLIX, Farmácias) are categorized without user action.
4. **Customizable** — users can rename, add colors, create their own categories.
5. **Analytically useful** — categories feed the monthly summary breakdown and expense reports.

---

## Current Implementation Status

The backend category foundation is **already implemented** as part of Phase 2 (transactions). This document describes what exists, what is missing, and the full backlog to deliver the complete experience.

### What exists today (backend — production ready)

| Component | File | Status |
|---|---|---|
| DB table `banking.transaction_categories` (V0_4) | `db/migration/V0_4__create_transaction_categories.sql` | ✅ Done |
| ~63 system seed categories (PT-BR) with emoji icons + hex colors | same migration | ✅ Done |
| `CategoryApplicableTo` enum: INCOME, EXPENSE, TRANSFER, ANY | `domain/transaction/TransactionEnums.kt` | ✅ Done |
| `TransactionCategory` domain data class | `domain/transaction/TransactionCategory.kt` | ✅ Done |
| `TransactionCategoryRepository` interface | `domain/transaction/TransactionRepository.kt` | ✅ Done |
| `TransactionCategoryEntity` JPA entity | `model/transaction/TransactionCategoryEntity.kt` | ✅ Done |
| `TransactionCategoryEntityRepository` JPA repository | `repo/TransactionCategoryEntityRepository.kt` | ✅ Done |
| `DefaultTransactionCategoryRepository` implementation | `repo/DefaultTransactionRepository.kt` | ✅ Done |
| `TransactionCategoryMapper` (domain ↔ entity) | `model/transaction/TransactionMapper.kt` | ✅ Done |
| `TransactionCategoryDTO` | `graphql/dto/TransactionDTO.kt` | ✅ Done |
| `TransactionCategory` GraphQL type + `CategoryApplicableTo` enum | `graphql/schema.graphqls` | ✅ Done |
| `transactionCategories` query (lists all system + user categories) | `graphql/schema.graphqls` | ✅ Done |
| `category` object resolved on each `Transaction` | `graphql/resolver/TransactionResolver.kt` | ✅ Done |
| `categoryId` field on `CreateTransactionInput` / `UpdateTransactionInput` | `graphql/schema.graphqls` | ✅ Done |
| `BANKING_CATEGORY_VIEW` + `BANKING_CATEGORY_MANAGE` permissions defined | `BankingWiringFactory.kt` | ✅ Done (MANAGE not yet wired) |
| Integration test for category repository | `test/integration/TransactionCategoryRepositoryIT.kt` | ✅ Done |
| `listCategories(accountId)` in `TransactionService` | `domain/transaction/TransactionService.kt` | ✅ Done |

### What is missing (backlog)

| Gap | Severity |
|---|---|
| No mutations for custom category CRUD (create/update/delete) | 🔴 Critical |
| No business-rule validations (max depth, isSystem guard, applicable_to inheritance) | 🔴 Critical |
| Icon strategy: emojis break visual consistency (color, dark mode, font-family) | 🟠 High |
| No `bulkCategorize` mutation | 🟠 High |
| No `categoryId` filter on `transactions` query | 🟠 High |
| No `uncategorized` filter (users can't easily find unlabeled transactions) | 🟠 High |
| No auto-categorization rule engine (`category_rules` table exists, engine not yet implemented) | 🟠 High |
| No `CategorySource` distinction (MANUAL vs RULE vs IMPORTED) | 🟡 Medium |
| No category breakdown in `MonthSummary` | 🟡 Medium |
| No `transactionsByCategory` analytics query | 🟡 Medium |
| No implicit rule learning from user actions | 🟡 Medium |
| No Pluggy category mapping (`pluggy_category_id` reserved but unused) | 🟢 Low (Phase 5 dependency) |
| No AI-based suggestion (Claude API) | 🟢 Low (future premium) |

---

## Industry Research

### How leading PFM apps handle categorization

| App | Manual UX | Auto-categorization | Custom categories | Learning |
|---|---|---|---|---|
| **Nubank** | Single-tap category picker on each transaction. Category chip shown inline in transaction list. | Pre-categorized on import from bank feed. No user-configurable rules. | No user-defined categories — fixed system list only. | No visible learning; relies on bank-side categorization. |
| **Organizze** | Category picker in transaction form. Hierarchical (group → subcategory). | Rule-based: user creates explicit rules (description contains X → category Y). | Full CRUD for user categories and groups. Icon + color picker. | Explicit rule creation only (no implicit learning). |
| **Mobills** | Quick-category from swipe gesture in transaction list. | Rule-based with auto-suggestion. Remembers last category for same payee. | User can create categories; icon library (not emojis). | Implicit: "If you always categorize IFOOD as Delivery, auto-assign next time." |
| **Wallet (BudgetBakers)** | Inline category picker with search. | ML-based auto-categorization on import. Category shown with confidence indicator. | Full CRUD. Supports category merging. | ML model improves with user corrections (explicit re-label trains the model). |
| **YNAB** | Category required on every transaction — it is the core budget mechanism, not optional. | No auto-categorization. Intentional friction. | Full hierarchical budget category CRUD. | Payee-based: assigns last used category to same payee automatically. |
| **Monarch Money** | Category picker with recently used at top. Search by name. | Rule-based + ML suggestions. Rule engine powerful (contains, starts with, regex, amount range). | Full CRUD. Import/export. | Implicit: payee → category mapping learned automatically. |

### Key takeaways

1. **Single-tap from list view** is the gold standard — opening a full form to categorize is too slow.
2. **Recently used + applicable_to filter** (show EXPENSE categories when transaction is a debit) dramatically reduces friction.
3. **Implicit learning** (auto-apply last choice for same payee/description pattern) is expected by users, even without explicit rules.
4. **Icon consistency matters** — all top apps use icon libraries, not emojis. Emojis render differently per OS and cannot be tinted to match the app theme.
5. **Bulk categorize** is essential for the import flow — users importing 3 months of OFX/CSV data need to categorize in batches, not one by one.
6. **Category breakdown** in the monthly summary is the primary value driver — the category list without analytics is just data without insight.

### Chosen approach

- **Icon strategy**: MUI (Material UI) icon names stored as string identifiers. Frontend renders `<Icon>{iconName}</Icon>`. The existing `color` field applies the app theme color. No emojis.
- **CRUD**: Full custom category management with validation guards.
- **Auto-categorization**: Rule-based engine as MVP (Sprint 3), Claude API as future premium tier.
- **Implicit learning**: After manual categorization, a rule is created silently (pattern = normalized description, category = chosen). User can manage/delete rules later.
- **Bulk operations**: `bulkCategorize` mutation for post-import workflows.
- **Analytics**: Category breakdown computed on-demand (no snapshot needed at MVP scale).

---

## Data Model

### `banking.transaction_categories` (exists — V0_4)

One row per category. Two-level hierarchy: root categories (parentId = null) and subcategories (parentId = root.id).

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (DB-generated) | Primary key |
| `account_id` | UUID FK → security.accounts | NULL for system categories; tenant-scoped for user categories |
| `parent_id` | UUID FK → self | NULL = root category; non-null = subcategory |
| `name` | TEXT NOT NULL | Display name (e.g., "Alimentação", "Supermercado") |
| `applicable_to` | TEXT NOT NULL | Enum: INCOME, EXPENSE, TRANSFER, ANY |
| `icon` | TEXT | **MUI icon name** (e.g., `"restaurant"`, `"home"`) — see Icon Strategy section |
| `color` | TEXT | Hex color (e.g., `"#2E7D32"`) — applied as tint to MUI icon |
| `pluggy_category_id` | TEXT | Reserved for Phase 5 Open Finance mapping |
| `is_system` | BOOLEAN NOT NULL | true = seed-only, read-only; false = user-defined |
| `sort_order` | INT NOT NULL | Display order within parent group |
| `deleted_at` | TIMESTAMPTZ | Soft-delete timestamp |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

**Constraints**:
- `(account_id, name, parent_id)` should be unique among non-deleted rows (enforced in service layer, not DB — system categories share names across tenants).
- Max depth = 2 (root + one level of subcategory). Enforced in service layer.

### New: `banking.category_rules` (to be created — Sprint 3)

Auto-categorization rules. One row per pattern → category mapping.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (DB-generated) | Primary key |
| `account_id` | UUID FK → security.accounts | NULL = global system rule; non-null = user-specific rule |
| `category_id` | UUID FK → transaction_categories | The category to assign when the rule matches |
| `pattern` | TEXT NOT NULL | Normalized text pattern (see Pattern Types below) |
| `pattern_type` | TEXT NOT NULL | Enum: CONTAINS, STARTS_WITH, EXACT, REGEX |
| `applicable_to` | TEXT NOT NULL | Which transaction side this rule applies to: INCOME, EXPENSE, TRANSFER, ANY |
| `priority` | INT NOT NULL DEFAULT 100 | Lower number = higher priority. Explicit user rules: 40; implicit rules: 60; system rules: 100. |
| `is_system` | BOOLEAN NOT NULL DEFAULT false | System-provided rules; not editable by user |
| `is_implicit` | BOOLEAN NOT NULL DEFAULT false | Created silently from user's manual categorization (see Implicit Learning) |
| `match_count` | INT NOT NULL DEFAULT 0 | How many times this rule has matched. Used for confidence display. |
| `deleted_at` | TIMESTAMPTZ | Soft-delete |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

**Pattern types**:
```
CONTAINS   — normalized(description) ILIKE '%pattern%'
STARTS_WITH — normalized(description) ILIKE 'pattern%'
EXACT      — normalized(description) = pattern
REGEX      — normalized(description) ~* pattern   (PostgreSQL POSIX regex)
```

**Normalization**: strip accents, lowercase, remove extra spaces, remove special chars (except alphanumeric and spaces).

### Modified: `banking.transactions` — new column `category_source`

| Column | Type | Notes |
|---|---|---|
| `category_source` | TEXT | Enum: MANUAL, RULE, IMPORTED, AI. NULL = not yet categorized. Added alongside existing `category_id`. |

This distinguishes user-confirmed categories (MANUAL) from system-suggested ones (RULE, IMPORTED, AI), enabling:
- UI badge "sugerido" on auto-categorized transactions
- Analytics: categorization coverage rate
- ML training data quality (only MANUAL labels are high-confidence)

---

## Icon Strategy: MUI Icons (not emojis)

### Problem with emojis

The current seed data uses emoji characters (e.g., `💰`, `🍽️`, `🏠`) in the `icon` column. Emojis are problematic for a web application:

1. **Color inconsistency**: Emojis are always full-color and cannot be tinted to match the app's palette or respect dark/light mode.
2. **Cross-platform rendering**: The same emoji looks completely different on Windows, macOS, Android, and iOS — breaking visual consistency.
3. **No theming**: Emojis ignore the MUI theme's `palette.primary`, `palette.text`, etc.
4. **Size limitations**: Emojis don't scale cleanly at all icon sizes.
5. **Accessibility**: Screen readers announce emoji characters by name, which can be verbose or confusing.

### Chosen solution: MUI icon names as string identifiers

Store the **MUI icon identifier string** (e.g., `"restaurant"`, `"home"`, `"directions_car"`) in the `icon` column. The existing `color` field provides the tint color.

**Frontend rendering**:
```typescript
// CategoryIcon.tsx
import Icon from '@mui/material/Icon'

interface Props {
  iconName: string
  color: string
  size?: number
}

export function CategoryIcon({ iconName, color, size = 24 }: Props) {
  return (
    <Icon sx={{ color, fontSize: size }}>
      {iconName}
    </Icon>
  )
}
```

MUI's `<Icon>` component uses the Material Symbols font, which supports any icon name. No separate import per icon — just load the font once (`<link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet" />`).

**Alternatively**, for tree-shaking and offline support, use a static map:
```typescript
// categoryIconMap.ts — maps icon name string → MUI SvgIcon component
import RestaurantIcon from '@mui/icons-material/Restaurant'
import HomeIcon from '@mui/icons-material/Home'
// ...

export const categoryIconMap: Record<string, React.ComponentType> = {
  restaurant: RestaurantIcon,
  home: HomeIcon,
  // ...
}
```

---

## Business Rules

### 1. Two-level hierarchy (max depth = 2)

- Root categories have `parentId = null`.
- Subcategories have `parentId` pointing to a root category.
- Creating a subcategory of a subcategory (depth 3) is **forbidden**.
- Validation in service: if `input.parentId != null`, load the parent and assert `parent.parentId == null`. If not, throw `CategoryDepthExceededException`.

### 2. System categories are read-only

- Categories with `isSystem = true` cannot be updated or deleted via mutations.
- The `createTransactionCategory`, `updateTransactionCategory`, `deleteTransactionCategory` mutations must check `isSystem` and throw `SystemCategoryNotEditableException` if true.
- The seed migration is the only source of system categories.

### 3. `applicable_to` inheritance for subcategories

- A subcategory must have `applicable_to` compatible with its parent:
  - If parent is INCOME → child must be INCOME or ANY.
  - If parent is EXPENSE → child must be EXPENSE or ANY.
  - If parent is TRANSFER → child must be TRANSFER or ANY.
  - If parent is ANY → child can be anything.
- Validation in service layer on create and update.

### 4. Name uniqueness within scope

- Among non-deleted categories for the same `account_id` (or global for system), `(name, parentId)` must be unique.
- Enforced in service layer (not DB constraint) to allow soft-delete recovery.

### 5. Soft-delete semantics

- Deleting a root category with subcategories:
  - Option A: **Cascade soft-delete** all children. Chosen approach — simpler UX.
  - Option B: Block deletion if children exist. Too restrictive.
- Deleting a category that is assigned to transactions: allowed. The `category_id` on existing transactions is preserved (category becomes "deleted" but the assignment remains valid as historical data). The UI shows a "Categoria removida" label if the category is not found.

### 6. Auto-categorization rule evaluation order

Priority tiers (lower number = evaluated first):

| Priority | Rule type | `is_implicit` | `is_system` | Rationale |
|---|---|---|---|---|
| **40** | Explicit user rule | false | false | Deliberate user configuration — highest confidence |
| **60** | Implicit rule | true | false | Learned from past manual categorization — fills gaps |
| **100** | System rule | false | true | Global defaults — lowest precedence |

Within the same priority tier, rules are ordered by `match_count DESC, created_at DESC` (more proven rules first).

When a new transaction is created (or its description is updated):

```
1. Load all active rules for the account:
   - Explicit user rules  (account_id = tenant's account_id, is_implicit = false, deleted_at IS NULL)  → priority 40
   - Implicit rules       (account_id = tenant's account_id, is_implicit = true,  deleted_at IS NULL)  → priority 60
   - System rules         (account_id IS NULL, is_system = true, deleted_at IS NULL)                   → priority 100
   ORDER BY priority ASC, match_count DESC, created_at DESC

2. For each rule:
   - Apply applicable_to filter: skip if rule.applicable_to != ANY
     and rule.applicable_to != transaction's creditDebitType-mapped side
   - Apply pattern match against normalize(transaction.description)
   - On first match: assign transaction.categoryId = rule.categoryId,
                     transaction.categorySource = RULE
                     UPDATE rule.match_count += 1
                     BREAK

3. If no rule matches: leave categoryId and categorySource as-is (null for new transactions)
```

**Why explicit user rules beat implicit rules**: an explicit rule is intentional configuration ("always categorize IFOOD as Delivery"). An implicit rule is learned from a past one-time action and should only fire when no deliberate rule covers the description.

### 7. Implicit learning from manual categorization

When a user manually sets a category on a transaction (mutation `updateTransaction` with `categoryId` changed and `categorySource = MANUAL`):

```
1. Check if an implicit rule already exists for this pattern:
   SELECT * FROM category_rules
   WHERE account_id = :accountId
     AND pattern = normalize(transaction.description)
     AND pattern_type = 'CONTAINS'
     AND is_implicit = true
     AND deleted_at IS NULL

2. If not exists: INSERT a new rule with
   - pattern = normalize(transaction.description)
   - pattern_type = CONTAINS
   - category_id = new categoryId
   - is_implicit = true
   - priority = 60  (beats system rules at 100; yields to explicit user rules at 40)

3. If exists (same pattern, different category): UPDATE category_id to new choice.
   User's latest manual choice wins.

4. If exists (same pattern, same category): UPDATE match_count += 1 only.
```

This gives the user a "it just works" experience. The implicit rules are visible and editable in the Rules management screen (Sprint 4).

### 8. `categorySource` on transaction mutations

- `createTransaction` without `categoryId`: run auto-categorization → if matched, `categorySource = RULE`; else `categorySource = null`.
- `createTransaction` with `categoryId` provided: `categorySource = MANUAL`.
- `updateTransaction` with new `categoryId`: `categorySource = MANUAL`; trigger implicit rule learning.
- `updateTransaction` without `categoryId` change: `categorySource` unchanged.
- On import (OFX/CSV with existing Pluggy category): `categorySource = IMPORTED`.

---

## GraphQL Schema

### Current (already in production)

```graphql
enum CategoryApplicableTo { INCOME EXPENSE TRANSFER ANY }

type TransactionCategory {
  id: ID!
  parentId: ID
  name: String!
  applicableTo: CategoryApplicableTo!
  icon: String          # MUI icon name (after migration)
  color: String         # Hex color string
  isSystem: Boolean!
  sortOrder: Int!
}

type Transaction {
  # ... existing fields ...
  categoryId: ID
  category: TransactionCategory   # resolved object
}

input CreateTransactionInput {
  # ... existing fields ...
  categoryId: ID
}

input UpdateTransactionInput {
  # ... existing fields ...
  categoryId: ID
}

extend type Query {
  transactionCategories: [TransactionCategory!]!
}
```

### New additions (planned)

```graphql
# ─── Category CRUD ───────────────────────────────────────────────────────────

input CreateCategoryInput {
  parentId: ID                           # null = root category
  name: String!
  applicableTo: CategoryApplicableTo!
  icon: String                           # MUI icon name
  color: String                          # hex color
  sortOrder: Int
}

input UpdateCategoryInput {
  name: String
  icon: String
  color: String
  sortOrder: Int
  # applicableTo and parentId are immutable after creation
}

extend type Mutation {
  createTransactionCategory(input: CreateCategoryInput!): TransactionCategory!
  updateTransactionCategory(id: ID!, input: UpdateCategoryInput!): TransactionCategory!
  deleteTransactionCategory(id: ID!): Boolean!
}

# ─── Category Rules ──────────────────────────────────────────────────────────

enum PatternType { CONTAINS STARTS_WITH EXACT REGEX }
enum CategorySource { MANUAL RULE IMPORTED AI }

type CategoryRule {
  id: ID!
  categoryId: ID!
  category: TransactionCategory!
  pattern: String!
  patternType: PatternType!
  applicableTo: CategoryApplicableTo!
  priority: Int!
  isSystem: Boolean!
  isImplicit: Boolean!
  matchCount: Int!
  createdAt: DateTime!
}

input CreateCategoryRuleInput {
  categoryId: ID!
  pattern: String!
  patternType: PatternType!
  applicableTo: CategoryApplicableTo!
  priority: Int
}

input UpdateCategoryRuleInput {
  categoryId: ID
  pattern: String
  patternType: PatternType
  priority: Int
}

extend type Query {
  categoryRules: [CategoryRule!]!    # all user-defined rules (incl. implicit)
}

extend type Mutation {
  createCategoryRule(input: CreateCategoryRuleInput!): CategoryRule!
  updateCategoryRule(id: ID!, input: UpdateCategoryRuleInput!): CategoryRule!
  deleteCategoryRule(id: ID!): Boolean!
}

# ─── Bulk Operations ─────────────────────────────────────────────────────────

type BulkOperationResult {
  affectedCount: Int!
  errors: [String!]!
}

extend type Mutation {
  bulkCategorize(transactionIds: [ID!]!, categoryId: ID!): BulkOperationResult!
}

# ─── Transaction filters ─────────────────────────────────────────────────────

extend type Query {
  # Existing query — adding categoryId and uncategorized filters
  transactions(
    financialAccountId: ID
    year: Int
    month: Int
    billId: ID
    categoryId: ID                # filter by category (includes subcategories)
    uncategorized: Boolean        # if true, return only transactions with categoryId = null
  ): [Transaction!]!
}

# ─── Transaction type additions ──────────────────────────────────────────────

type Transaction {
  # ... existing fields ...
  categorySource: CategorySource    # MANUAL | RULE | IMPORTED | AI | null
}

# ─── Analytics ───────────────────────────────────────────────────────────────

type CategoryTotal {
  category: TransactionCategory!
  totalAmount: Float!
  transactionCount: Int!
  percentage: Float!    # percentage of total expenses (or income) for the period
}

extend type Query {
  # Category breakdown for a given month (on-demand, not cached in snapshot)
  transactionsByCategory(
    financialAccountId: ID
    year: Int!
    month: Int!
    applicableTo: CategoryApplicableTo    # INCOME or EXPENSE to filter side
  ): [CategoryTotal!]!
}
```

---

## Write Flow

### Transaction create with auto-categorization

```
TransactionService.create(command):
  1. Validate input (existing)
  2. if command.categoryId != null:
       categorySource = MANUAL
     else:
       suggestion = categorizationService.suggest(accountId, command.description, command.creditDebitType)
       categoryId = suggestion?.categoryId
       categorySource = if suggestion != null then RULE else null
  3. Save transaction with categoryId + categorySource
  4. creditCardBillService.resolveBillForTransaction(...) [if card account]
  5. snapshotComputationService.recomputeChain(...)
  6. Return transaction
```

### Transaction update with implicit learning

```
TransactionService.update(id, command):
  1. Load existing transaction
  2. if command.categoryId != null AND command.categoryId != existing.categoryId:
       categorySource = MANUAL
       categorizationService.learnFromManualCategorization(
           accountId, existing.description, command.categoryId)
  3. Save updated transaction
  4. [bill + snapshot recompute — existing logic]
  5. Return transaction
```

### `bulkCategorize`

```
TransactionService.bulkCategorize(transactionIds, categoryId, accountId):
  1. Verify categoryId exists and is visible to accountId
  2. For each transactionId (in batch):
       - Assert transaction.accountId == accountId (ownership check)
       - UPDATE transaction SET category_id = categoryId, category_source = 'MANUAL'
  3. For each unique (financialAccountId, year, month) affected:
       - snapshotComputationService.recomputeChain(...)  [no-op for snapshots since category is not stored there]
  4. Return { affectedCount, errors }
```

Note: `bulkCategorize` does not trigger implicit rule learning — bulk assignment is considered an explicit batch action, not a pattern for inference.

---

## Implementation Plan

> Stories ordered by dependency. All Kotlin code targets Micronaut + JPA patterns established in this codebase. Backend stories are independently testable via integration tests extending `BankingPostgresIntegrationTest`.

---

### Dependency Graph

```
Sprint 1 — Backend: Category CRUD
  CAT-01 (createTransactionCategory mutation)
  CAT-02 (updateTransactionCategory mutation)
  CAT-03 (deleteTransactionCategory mutation — soft-delete)
  CAT-04 (Business rule validations — depth, isSystem, applicable_to)

Sprint 2 — Backend: Icon migration + Transaction filters
  CAT-05 (MUI icon migration — V0_12 Flyway)    ← no deps
  CAT-06 (categoryId + uncategorized filter on transactions query)  ← no deps
  CAT-07 (bulkCategorize mutation)              ← no deps
  CAT-08 (categorySource column on transactions) ← no deps

Sprint 3 — Backend: Auto-categorization engine
  CAT-09 (category_rules table V0_14 + merchant seed V0_15)  ← no deps
  CAT-10 (CategorizationService — rule engine)  ← depends on CAT-09
  CAT-11 (Hook CategorizationService into TransactionService.create/update) ← depends on CAT-10
  CAT-12 (Implicit learning from manual categorization) ← depends on CAT-11
  CAT-13 (CategoryRule CRUD mutations)          ← depends on CAT-09

Sprint 4 — Analytics
  CAT-14 (transactionsByCategory query)         ← no deps
  CAT-15 (category breakdown in MonthSummary)   ← depends on CAT-14

Sprint 5 — Tests
  CAT-16 (Unit tests — CategorizationService)   ← depends on CAT-10
  CAT-17 (Integration tests — full categorization lifecycle) ← depends on CAT-11, CAT-12
  CAT-18 (Integration tests — bulkCategorize)   ← depends on CAT-07

Sprint 6 — Frontend (parallel stories, all depend on Sprint 1–2 backend)
  CAT-19 (Category picker component — single transaction)
  CAT-20 (Category icon renderer — MUI icon by name)
  CAT-21 (Category management screen — CRUD)
  CAT-22 (Bulk categorize UI — post-import flow)
  CAT-23 (Category rules management screen)
  CAT-24 (Category breakdown chart in MonthSummary)
```

---

### Sprint 1 — Backend: Category CRUD

---

#### CAT-01 — `createTransactionCategory` mutation

**Depends on**: _(nothing — backend foundation exists)_

**Description**: Implement `createTransactionCategory(input: CreateCategoryInput!): TransactionCategory!` mutation. Allows users to create a custom (user-scoped) category.

**New/modified files**:
- `graphql/schema.graphqls` — add `CreateCategoryInput` input type + `createTransactionCategory` mutation
- `domain/transaction/TransactionCategoryService.kt` — **new file** — service layer for category CRUD
- `domain/transaction/TransactionCategoryRepository.kt` — add `save(category): TransactionCategory` (already exists as interface — verify it covers insert)
- `repo/TransactionCategoryEntityRepository.kt` — verify `save` handles insert (JPA default `save` should work; add `existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull` for uniqueness check)
- `graphql/dto/TransactionCategoryDTO.kt` — **new file** — DTO with `toDTO()` extension (currently in `TransactionDTO.kt` — extract to own file)
- `graphql/resolver/TransactionCategoryResolver.kt` — **new file** — resolver for category mutations
- `graphql/BankingWiringFactory.kt` — wire `createTransactionCategory` with `BANKING_CATEGORY_MANAGE` permission

**Service logic** (`TransactionCategoryService.create`):
```
create(input: CreateCategoryInput, accountId: UUID): TransactionCategory:
  1. If input.parentId != null:
       parent = categoryRepository.findById(input.parentId)
         ?: throw CategoryNotFoundException(input.parentId)
       if parent.parentId != null: throw CategoryDepthExceededException()
       if !isCompatible(input.applicableTo, parent.applicableTo):
         throw CategoryApplicableToMismatchException()

  2. Check name uniqueness:
       if categoryRepository.existsByName(input.name, input.parentId, accountId):
         throw DuplicateCategoryNameException(input.name)

  3. sortOrder = input.sortOrder ?: (maxSortOrder(accountId, parentId) + 10)

  4. category = TransactionCategory(
       id = null,  // DB-generated
       accountId = accountId,
       parentId = input.parentId,
       name = input.name,
       applicableTo = input.applicableTo,
       icon = input.icon,
       color = input.color,
       isSystem = false,
       sortOrder = sortOrder,
       deletedAt = null,
       ...
     )
  5. return categoryRepository.save(category)
```

**Acceptance criteria**:
- Creating a root category returns it with `parentId = null`, `isSystem = false`, `accountId = callerAccountId`
- Creating a subcategory with valid parent returns it with `parentId` set
- Attempt to create subcategory of subcategory returns `CategoryDepthExceededException`
- Duplicate name (same parent, same account) returns `DuplicateCategoryNameException`
- System categories are not affected (they are not visible to this flow)
- `transactionCategories` query immediately returns the new category

---

#### CAT-02 — `updateTransactionCategory` mutation

**Depends on**: CAT-01 (service layer established)

**Description**: Implement `updateTransactionCategory(id: ID!, input: UpdateCategoryInput!): TransactionCategory!`. Only allows updating `name`, `icon`, `color`, `sortOrder`. `applicableTo` and `parentId` are immutable.

**New/modified files**:
- `graphql/schema.graphqls` — add `UpdateCategoryInput` + `updateTransactionCategory` mutation
- `domain/transaction/TransactionCategoryService.kt` — add `update` method
- `graphql/resolver/TransactionCategoryResolver.kt` — add `updateTransactionCategory` handler
- `graphql/BankingWiringFactory.kt` — wire with `BANKING_CATEGORY_MANAGE` permission

**Service logic** (`TransactionCategoryService.update`):
```
update(id: UUID, input: UpdateCategoryInput, accountId: UUID): TransactionCategory:
  1. category = categoryRepository.findById(id)
       ?: throw CategoryNotFoundException(id)
  2. Assert category.accountId == accountId (ownership — users cannot edit other tenants' categories)
  3. Assert !category.isSystem → throw SystemCategoryNotEditableException()
  4. If input.name != null && input.name != category.name:
       check uniqueness: (input.name, category.parentId, accountId)
  5. Apply changes (only non-null fields from input):
       category.name    = input.name    ?: category.name
       category.icon    = input.icon    ?: category.icon
       category.color   = input.color   ?: category.color
       category.sortOrder = input.sortOrder ?: category.sortOrder
       category.updatedAt = NOW()
  6. return categoryRepository.save(category)
```

**Acceptance criteria**:
- Can update name, icon, color, sortOrder of a user-defined category
- Attempt to update a system category (isSystem=true) returns error
- Attempt to update a category owned by a different account returns 403/not found
- `applicableTo` and `parentId` cannot be changed (input does not include these fields)

---

#### CAT-03 — `deleteTransactionCategory` mutation (soft-delete)

**Depends on**: CAT-01

**Description**: Implement `deleteTransactionCategory(id: ID!): Boolean!`. Soft-deletes the category and cascades to all its subcategories.

**New/modified files**:
- `graphql/schema.graphqls` — add `deleteTransactionCategory` mutation
- `domain/transaction/TransactionCategoryRepository.kt` — add `softDelete(id)` and `softDeleteChildren(parentId)` methods
- `repo/TransactionCategoryEntityRepository.kt` — add native queries:
  ```sql
  -- softDelete
  UPDATE banking.transaction_categories
  SET deleted_at = NOW(), updated_at = NOW()
  WHERE id = :id AND account_id = :accountId AND deleted_at IS NULL

  -- softDeleteChildren
  UPDATE banking.transaction_categories
  SET deleted_at = NOW(), updated_at = NOW()
  WHERE parent_id = :parentId AND account_id = :accountId AND deleted_at IS NULL
  ```
- `repo/DefaultTransactionCategoryRepository.kt` — implement the new methods
- `domain/transaction/TransactionCategoryService.kt` — add `delete` method
- `graphql/resolver/TransactionCategoryResolver.kt` — add `deleteTransactionCategory` handler
- `graphql/BankingWiringFactory.kt` — wire with `BANKING_CATEGORY_MANAGE` permission

**Service logic**:
```
delete(id: UUID, accountId: UUID): Boolean:
  1. category = categoryRepository.findById(id) ?: throw CategoryNotFoundException(id)
  2. Assert category.accountId == accountId
  3. Assert !category.isSystem → throw SystemCategoryNotEditableException()
  4. if category.parentId == null (root category):
       categoryRepository.softDeleteChildren(id, accountId)  // cascade to subcategories
  5. categoryRepository.softDelete(id, accountId)
  6. return true
```

**Acceptance criteria**:
- Deleting a root category also soft-deletes all its subcategories
- Deleting a subcategory does not affect the parent or sibling subcategories
- Transactions that had this category assigned keep their `category_id` (historical data preserved)
- The deleted category no longer appears in `transactionCategories` query
- Attempt to delete a system category returns error

---

#### CAT-04 — Business rule validations

**Depends on**: CAT-01, CAT-02, CAT-03

**Description**: Ensure all business rules in the service layer are enforced consistently with typed exceptions that the GraphQL layer maps to structured error responses.

**New/modified files**:
- `domain/transaction/CategoryExceptions.kt` — **new file** — define all category-related exceptions:
  - `CategoryNotFoundException(id: UUID)`
  - `CategoryDepthExceededException()`
  - `SystemCategoryNotEditableException()`
  - `CategoryApplicableToMismatchException(childApplicableTo, parentApplicableTo)`
  - `DuplicateCategoryNameException(name: String)`
- Verify GraphQL error handling wires these exceptions to proper GraphQL error codes (check existing exception mapping in `BankingWiringFactory` or error handler)

**Compatibility check logic**:
```kotlin
fun isCompatible(child: CategoryApplicableTo, parent: CategoryApplicableTo): Boolean =
  parent == ANY || child == parent || child == ANY
```

**Acceptance criteria**:
- Each exception produces a clear, user-facing GraphQL error message (not a 500)
- Attempting all forbidden operations returns structured errors, not stack traces

---

### Sprint 2 — Backend: Icon Migration + Transaction Filters

---

#### CAT-05 — MUI icon migration (V0_12)

**Depends on**: _(nothing)_

**Description**: Create Flyway migration `V0_12__update_category_icons_to_mui.sql` to replace all emoji values in the `icon` column with MUI icon name strings, using the mapping table defined in the Icon Strategy section of this document.

**New file**: `src/main/resources/db/migration/V0_12__update_category_icons_to_mui.sql`

**Migration structure**:
```sql
-- Update icon names for system categories (account_id IS NULL, is_system = true)
-- Use name-based matching since UUIDs are DB-generated and unknown at migration time

UPDATE banking.transaction_categories SET icon = 'payments'     WHERE name = 'Salário'                AND is_system = true;
UPDATE banking.transaction_categories SET icon = 'work'         WHERE name = 'Renda Extra'             AND is_system = true;
UPDATE banking.transaction_categories SET icon = 'elderly'      WHERE name = 'Aposentadoria'           AND is_system = true;
UPDATE banking.transaction_categories SET icon = 'trending_up'  WHERE name = 'Investimentos'           AND is_system = true;
-- ... (all 63 categories from the mapping table above)
```

**Acceptance criteria**:
- All system category `icon` values are valid MUI icon name strings
- No emoji characters remain in the `icon` column for system categories
- Migration runs cleanly on a fresh DB and on a DB with existing data
- Frontend `<Icon>{iconName}</Icon>` renders visually without errors for all icon names

---

#### CAT-06 — `categoryId` + `uncategorized` filters on `transactions` query

**Depends on**: _(nothing)_

**Description**: Add two new optional filters to the existing `transactions` query:
- `categoryId: ID` — return only transactions assigned to this category (or any of its subcategories).
- `uncategorized: Boolean` — if `true`, return only transactions with `category_id IS NULL`.

**Modified files**:
- `graphql/schema.graphqls` — add `categoryId: ID` and `uncategorized: Boolean` to `transactions` query input
- `domain/transaction/TransactionQueryService.kt` (or wherever the query service lives) — add filter handling
- `repo/TransactionEntityRepository.kt` — add query methods:
  - `findByCategory(financialAccountId?, accountId, categoryIds: List<UUID>, ...pagingParams): List<TransactionEntity>`
  - `findUncategorized(financialAccountId?, accountId, year?, month?): List<TransactionEntity>`
- `graphql/resolver/TransactionResolver.kt` — pass new params through

**Category filter with subcategory expansion**:
```
When categoryId is provided:
  1. Load the category to determine if it's a root or subcategory
  2. If root: expand to include all its subcategory IDs
       categoryIds = [categoryId] + findChildren(categoryId).map { it.id }
  3. If subcategory: categoryIds = [categoryId]
  4. Query: WHERE category_id = ANY(:categoryIds)
```

**SQL for uncategorized**:
```sql
WHERE category_id IS NULL
  AND financial_account_id = :financialAccountId  -- if provided
  AND account_id = :accountId
  AND deleted_at IS NULL
  AND (year/month filter if provided)
```

**Acceptance criteria**:
- `transactions(categoryId: "uuid-of-alimentação")` returns transactions categorized as Alimentação AND all its subcategories (Supermercado, Restaurante, etc.)
- `transactions(categoryId: "uuid-of-supermercado")` returns only Supermercado transactions (not other Alimentação children)
- `transactions(uncategorized: true)` returns only transactions with `categoryId = null`
- All existing filter combinations (`year`/`month`, `billId`, `financialAccountId`) still work unchanged
- Permission check applies as before

---

#### CAT-07 — `bulkCategorize` mutation

**Depends on**: _(nothing)_

**Description**: Implement `bulkCategorize(transactionIds: [ID!]!, categoryId: ID!): BulkOperationResult!`. Assigns the same category to multiple transactions in a single mutation. Primary use case: post-import categorization.

**New/modified files**:
- `graphql/schema.graphqls` — add `BulkOperationResult` type + `bulkCategorize` mutation
- `domain/transaction/TransactionService.kt` — add `bulkCategorize` method
- `repo/TransactionEntityRepository.kt` — add batch update native query:
  ```sql
  UPDATE banking.transactions
  SET category_id = :categoryId,
      category_source = 'MANUAL',
      updated_at = NOW()
  WHERE id = ANY(:transactionIds)
    AND account_id = :accountId
    AND deleted_at IS NULL
  ```
- `graphql/resolver/TransactionResolver.kt` — add `bulkCategorize` handler
- `graphql/BankingWiringFactory.kt` — wire with `BANKING_TRANSACTION_MANAGE` permission (same as individual update)

**Service logic**:
```
bulkCategorize(transactionIds, categoryId, accountId):
  1. Verify categoryId is accessible to accountId (system or own user category)
  2. Execute batch UPDATE (all IDs in one SQL call, filtered by account_id for safety)
  3. Return { affectedCount: rowsUpdated, errors: [] }
  4. On partial failure: return { affectedCount: successCount, errors: [failedId, reason] }
```

**Acceptance criteria**:
- All provided transaction IDs belonging to the caller's account are updated in one round-trip
- Transaction IDs from other accounts are silently ignored (account_id filter in SQL)
- `categorySource` is set to `MANUAL` for all bulk-updated transactions
- `affectedCount` reflects actual rows updated
- Does NOT trigger implicit rule learning (bulk is not pattern-based)

---

#### CAT-08 — `category_source` column on transactions

**Depends on**: _(nothing)_

**Description**: Add `category_source` column to `banking.transactions` table and expose it in the GraphQL schema.

**New files**:
- `src/main/resources/db/migration/V0_13__add_category_source_to_transactions.sql`:
  ```sql
  ALTER TABLE banking.transactions
    ADD COLUMN category_source TEXT
    CHECK (category_source IN ('MANUAL', 'RULE', 'IMPORTED', 'AI'));

  -- Backfill: all existing transactions with a category_id are assumed MANUAL
  UPDATE banking.transactions
  SET category_source = 'MANUAL'
  WHERE category_id IS NOT NULL AND deleted_at IS NULL;
  ```

**Modified files**:
- `graphql/schema.graphqls` — add `CategorySource` enum + `categorySource` field on `Transaction` type
- `domain/transaction/Transaction.kt` — add `categorySource: CategorySource?` field
- `model/transaction/TransactionEntity.kt` — add `categorySource: String?` column
- `model/transaction/TransactionMapper.kt` — map `categorySource` in both directions
- `graphql/dto/TransactionDTO.kt` — add `categorySource: CategorySource?`

**Acceptance criteria**:
- New transactions created without explicit categoryId have `categorySource = null`
- New transactions created with explicit categoryId have `categorySource = MANUAL`
- Existing transactions with categoryId are backfilled to `categorySource = MANUAL`
- The `categorySource` field is returned in `transactions` and `transaction` queries

---

### Sprint 3 — Backend: Auto-Categorization Engine

---

#### CAT-09 — `category_rules` table + migration

**Depends on**: _(nothing)_

**Description**: Create the `banking.category_rules` table and seed it with ~500 system rules derived from the merchant database at `docs/03-features/banking/transaction-enrichment/merchants-seed.csv`. Each merchant name maps to a specific category via an `EXACT` pattern match against the normalized transaction description.

**New files**:
- `src/main/resources/db/migration/V0_14__create_category_rules.sql` — DDL + indexes
- `src/main/resources/db/migration/V0_15__seed_category_rules.sql` — merchant-based rule seeds (generated)
- `scripts/generate_category_rules_seed.py` (or `.kt`) — generation script that reads the CSV and produces `V0_15`

Splitting DDL and seed into two migrations makes it easy to regenerate the seed as the merchant CSV grows without touching the schema migration.

---

**`V0_14` — DDL**:

```sql
CREATE TABLE banking.category_rules (
  id              UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  account_id      UUID          REFERENCES security.accounts(id),
  category_id     UUID          NOT NULL REFERENCES banking.transaction_categories(id),
  pattern         TEXT          NOT NULL,
  pattern_type    TEXT          NOT NULL
    CHECK (pattern_type IN ('CONTAINS', 'STARTS_WITH', 'EXACT', 'REGEX')),
  applicable_to   TEXT          NOT NULL
    CHECK (applicable_to IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY')),
  -- priority tiers: explicit user rules = 40, implicit rules = 60, system rules = 100
  priority        INT           NOT NULL DEFAULT 100,
  is_system       BOOLEAN       NOT NULL DEFAULT false,
  is_implicit     BOOLEAN       NOT NULL DEFAULT false,
  match_count     INT           NOT NULL DEFAULT 0,
  deleted_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_category_rules_account_id ON banking.category_rules (account_id)
  WHERE deleted_at IS NULL;
CREATE INDEX idx_category_rules_system ON banking.category_rules (is_system)
  WHERE is_system = true AND deleted_at IS NULL;
```

---

**`V0_15` — Merchant seed rules**:

Rules use `EXACT` pattern type (merchant names in the CSV are already normalized: lowercase, no accents, no special chars) and `applicable_to = 'EXPENSE'` unless noted. System rules use `priority = 100`.

#### Sector → Category mapping

The CSV `sector` field maps to categories as follows. Sectors with multiple subcategories use the per-merchant override table below.

| CSV sector | Category name | Notes |
|---|---|---|
| `alimentacao` | `Supermercado` | |
| `delivery` | `Delivery` | |
| `restaurante` | `Restaurante` | |
| `transporte` | _(see sub-mapping)_ | Uber/fuel/toll/parking/bus/rental each → different subcategory |
| `streaming` | _(see sub-mapping)_ | Video/music/cinema/events → different subcategories |
| `saude` | _(see sub-mapping)_ | Pharmacy/plan/gym/clinic → different subcategories |
| `educacao` | _(see sub-mapping)_ | Online courses/schools/bookstores → different subcategories |
| `utilities` | _(see sub-mapping)_ | Electricity/water/gas/telecom/insurance → different subcategories |
| `ecommerce` | `Compras` | Except electronics specialists → `Eletrônicos` |
| `moda` | `Roupas` | |
| `financas` | _(skip)_ | Bank/fintech names in descriptions are ambiguous (fees vs transfers); excluded from seed |

#### `transporte` sub-mapping

| Merchants | Category |
|---|---|
| uber, 99, indrive, cabify | `Uber / Taxi` |
| ipiranga, shell, br distribuidora, ale combustiveis, vibra energia, raizen, texaco | `Combustível` |
| sem parar, conectcar, veloe, via facil, ecovias | `Pedágio` |
| estapar, indigo, multipark | `Estacionamento` |
| sptrans, metro sp, cptm, supervia, metro rio, riocard, vlt rio, trensurb, urbs, buser, flixbus, itapemirim, cometa, real expresso, util, 1001, gontijo, penha, pluma, catarinense, otimo, tembici, grow | `Transporte Público` |
| localiza, movida, unidas, hertz, avis, budget | `Transporte` (car rental) |

#### `streaming` sub-mapping

| Merchants | Category |
|---|---|
| netflix, disney+, disney plus, amazon prime, prime video, hbo max, max, globoplay, paramount+, paramount plus, apple tv, youtube premium, crunchyroll, telecine, looke, mubi, sky, premiere, dazn | `Streaming Vídeo` |
| spotify, deezer, apple music, amazon music, tidal, audible, storytel | `Streaming Música` |
| cinemark, cinepolis, uci cinemas, cinesystem, kinoplex, ingresso.com | `Cinema / Teatro` |
| sympla, eventbrite, ticketmaster | `Lazer e Entretenimento` |

#### `saude` sub-mapping

| Merchants | Category |
|---|---|
| drogasil, droga raia, drogaria sao paulo, ultrafarma, pague menos, panvel, nissei, extrafarma, drogal, araujo, dpsp, drogaria venancio, farma conde, onofre | `Farmácia` |
| unimed, amil, bradesco saude, sulamerica saude, hapvida, notre dame intermedica, prevent senior, golden cross, omint, assim saude, porto seguro saude, cassi, geap, alice saude, sami saude, odontoprev, uniodonto | `Plano de Saúde` |
| smartfit, bodytech, bio ritmo, bluefit, golds gym, gympass, wellhub, totalpass, cia athletica | `Academia` |
| fleury, dasa, hermes pardini, sabin, lavoisier, alta exames, einstein, sirio libanes, rede d'or, bp beneficencia portuguesa, hcor, moinhos de vento, oswaldo cruz, hospital nove de julho, dr. consulta, doctoralia, conexa saude, psicologia viva, zenklub, vittude | `Médico / Consulta` |
| oticas carol, oticas diniz, oticas penha, grandvision, formula e acao | `Saúde` (parent — optician/other) |

#### `educacao` sub-mapping

| Merchants | Category |
|---|---|
| duolingo, udemy, coursera, alura, descomplica, hotmart, eduzz, kiwify, rocketseat, dio, pluralsight, skillshare, linkedin learning, domestika, masterclass, babbel, open english, rosetta stone, empiricus, hashtag treinamentos | `Curso Online` |
| estacio, anhanguera, unopar, unip, fgv, insper, espm, mackenzie, faap, ibmec, wizard, fisk, ccaa, cna, cultura inglesa, berlitz, yazigi, senai, senac, sebrae, maple bear, coc, anglo, objetivo, dante alighieri, santa cruz, bandeirantes, etapa, marista, salesiano, bernoulli, positivo, pitagoras, poliedro, eleva, ph cursinho, cogna, cruzeiro do sul, sao judas, belas artes, puc, uniseb, ampli | `Mensalidade` |
| saraiva, livraria cultura, kalunga | `Material Escolar` |

#### `utilities` sub-mapping

| Merchants | Category |
|---|---|
| enel, eletropaulo, cemig, copel, cpfl, light, energisa, celesc, neoenergia, coelba, elektro, edp, ceee, equatorial, celpe, cosern, coelce, celg, rge | `Luz` |
| sabesp, cedae, sanepar, embasa, caesb, copasa, cagece, corsan, casan, saneago, aegea, brk ambiental, caern, deso, sanesul, caema, cosama, agespisa | `Água` |
| comgas, ceg, gasmig, naturgy, bahiagas, supergasbras, ultragaz, copagaz, liquigas, scgas, copergas, algas | `Gás` |
| claro, vivo, tim, oi, algar telecom, brisanet, unifique, desktop, surf telecom | `Telecomunicações` (parent — plan type unknown) |
| starlink | `Internet` |
| porto seguro seguros, bradesco seguros, mapfre, allianz, liberty seguros, hdi seguros, tokio marine, zurich, sompo | `Seguros` (parent) |

#### `ecommerce` sub-mapping

| Merchants | Category |
|---|---|
| kabum, terabyte, pichau, fastshop | `Eletrônicos` |
| all others | `Compras` |

---

**Sample generated SQL** (representative — full set in `V0_15`):

```sql
-- V0_15__seed_category_rules.sql
-- Auto-generated from docs/03-features/banking/transaction-enrichment/merchants-seed.csv
-- DO NOT EDIT MANUALLY — regenerate using scripts/generate_category_rules_seed.py

INSERT INTO banking.category_rules
  (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
VALUES
  -- alimentacao → Supermercado
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Supermercado' AND is_system = true LIMIT 1), 'carrefour',        'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Supermercado' AND is_system = true LIMIT 1), 'atacadao',         'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Supermercado' AND is_system = true LIMIT 1), 'pao de acucar',    'EXACT', 'EXPENSE', 100, true),
  -- delivery → Delivery
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Delivery'     AND is_system = true LIMIT 1), 'ifood',            'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Delivery'     AND is_system = true LIMIT 1), 'rappi',            'EXACT', 'EXPENSE', 100, true),
  -- restaurante → Restaurante
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Restaurante'  AND is_system = true LIMIT 1), 'mcdonalds',        'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Restaurante'  AND is_system = true LIMIT 1), 'starbucks',        'EXACT', 'EXPENSE', 100, true),
  -- transporte (uber/taxi)
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Uber / Taxi'  AND is_system = true LIMIT 1), 'uber',             'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Uber / Taxi'  AND is_system = true LIMIT 1), '99',               'EXACT', 'EXPENSE', 100, true),
  -- transporte (combustível)
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Combustível'  AND is_system = true LIMIT 1), 'ipiranga',         'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Combustível'  AND is_system = true LIMIT 1), 'shell',            'EXACT', 'EXPENSE', 100, true),
  -- streaming vídeo
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Vídeo' AND is_system = true LIMIT 1), 'netflix',       'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Vídeo' AND is_system = true LIMIT 1), 'disney+',       'EXACT', 'EXPENSE', 100, true),
  -- streaming música
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Streaming Música' AND is_system = true LIMIT 1), 'spotify',      'EXACT', 'EXPENSE', 100, true),
  -- saúde (farmácia)
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Farmácia'     AND is_system = true LIMIT 1), 'drogasil',         'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Farmácia'     AND is_system = true LIMIT 1), 'droga raia',       'EXACT', 'EXPENSE', 100, true),
  -- saúde (academia)
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Academia'     AND is_system = true LIMIT 1), 'smartfit',         'EXACT', 'EXPENSE', 100, true),
  -- utilities (luz)
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Luz'          AND is_system = true LIMIT 1), 'enel',             'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Luz'          AND is_system = true LIMIT 1), 'cemig',            'EXACT', 'EXPENSE', 100, true),
  -- moda → Roupas
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Roupas'       AND is_system = true LIMIT 1), 'renner',           'EXACT', 'EXPENSE', 100, true),
  (NULL, (SELECT id FROM banking.transaction_categories WHERE name = 'Roupas'       AND is_system = true LIMIT 1), 'zara',             'EXACT', 'EXPENSE', 100, true)
  -- ... (~480 more rows for all merchants in the CSV)
;
```

**Acceptance criteria**:
- `V0_14` creates the table and indexes cleanly on fresh and existing DBs
- `V0_15` inserts rules for all non-`financas` merchants in the CSV (~500 rows)
- Each rule uses `EXACT` pattern type and `priority = 100`
- `suggest("netflix", DEBIT)` returns `Streaming Vídeo` category
- `suggest("carrefour", DEBIT)` returns `Supermercado` category
- `suggest("nubank", DEBIT)` returns `null` (financas sector excluded)
- Generation script can be re-run to refresh `V0_15` when the CSV is updated

---

#### CAT-10 — `CategorizationService` — rule engine

**Depends on**: CAT-09

**Description**: Implement the `CategorizationService` that evaluates rules against a transaction description and returns the best matching category.

**New files**:
- `domain/transaction/CategorizationService.kt` — domain service interface
- `domain/transaction/CategoryRule.kt` — domain data class
- `domain/transaction/CategoryRuleRepository.kt` — repository interface with:
  - `findRulesForAccount(accountId: UUID): List<CategoryRule>` — returns all active rules (explicit user priority=40, implicit priority=60, system priority=100) ordered by priority ASC, match_count DESC, created_at DESC
  - `save(rule: CategoryRule): CategoryRule`
  - `softDelete(id: UUID, accountId: UUID)`
  - `findImplicitByPattern(accountId: UUID, pattern: String): CategoryRule?`
  - `incrementMatchCount(id: UUID)`
- `infrastructure/categorization/DefaultCategorizationService.kt` — implementation

**Service logic**:
```kotlin
@Singleton
class DefaultCategorizationService(
  private val ruleRepository: CategoryRuleRepository
) : CategorizationService {

  fun suggest(accountId: UUID, description: String?, creditDebitType: CreditDebitType): CategorySuggestion? {
    if (description.isNullOrBlank()) return null
    val normalized = normalize(description)
    val rules = ruleRepository.findRulesForAccount(accountId)
    val side = if (creditDebitType == CREDIT) INCOME else EXPENSE

    for (rule in rules) {
      if (!isApplicable(rule.applicableTo, side)) continue
      if (matches(normalized, rule.pattern, rule.patternType)) {
        ruleRepository.incrementMatchCount(rule.id!!)
        return CategorySuggestion(rule.categoryId, rule.id!!)
      }
    }
    return null
  }

  fun learnFromManualCategorization(accountId: UUID, description: String?, categoryId: UUID) {
    if (description.isNullOrBlank()) return
    val pattern = normalize(description)
    val existing = ruleRepository.findImplicitByPattern(accountId, pattern)
    if (existing == null) {
      ruleRepository.save(CategoryRule(
        accountId = accountId, categoryId = categoryId, pattern = pattern,
        patternType = CONTAINS, applicableTo = ANY,
        priority = 60, isSystem = false, isImplicit = true
      ))
    } else if (existing.categoryId != categoryId) {
      ruleRepository.save(existing.copy(categoryId = categoryId))
    }
    // else: same category, no change needed
  }

  private fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
      .replace(Regex("[^\\p{ASCII}]"), "")
      .lowercase()
      .trim()
      .replace(Regex("\\s+"), " ")

  private fun matches(normalized: String, pattern: String, type: PatternType): Boolean =
    when (type) {
      CONTAINS    -> normalized.contains(pattern)
      STARTS_WITH -> normalized.startsWith(pattern)
      EXACT       -> normalized == pattern
      REGEX       -> normalized.matches(Regex(pattern, RegexOption.IGNORE_CASE))
    }

  private fun isApplicable(ruleApplicableTo: CategoryApplicableTo, txSide: CategoryApplicableTo): Boolean =
    ruleApplicableTo == ANY || ruleApplicableTo == txSide
}

data class CategorySuggestion(val categoryId: UUID, val ruleId: UUID)
```

**Infrastructure files**:
- `model/transaction/CategoryRuleEntity.kt` — JPA entity
- `repo/CategoryRuleEntityRepository.kt` — JPA repository
- `model/transaction/CategoryRuleMapper.kt`
- `repo/DefaultCategoryRuleRepository.kt`

**Acceptance criteria**:
- `suggest("NETFLIX.COM*BR", CREDIT_DEBIT_DEBIT)` returns the Streaming category via the seeded rule
- `suggest("PADARIA DO ZE", DEBIT)` returns null (no rule matches)
- `normalize("IFOOD*BR  LTDA")` returns `"ifood*br ltda"` (lowercase, trimmed)
- Explicit user rules (priority=40) take precedence over implicit rules (priority=60), which take precedence over system rules (priority=100)

---

#### CAT-11 — Hook `CategorizationService` into `TransactionService`

**Depends on**: CAT-10, CAT-08 (category_source column)

**Description**: Update `TransactionService.create` and `TransactionService.update` to invoke auto-categorization and implicit learning.

**Modified files**:
- `domain/transaction/TransactionService.kt`

**Create flow addition**:
```kotlin
// After validation, before saving:
val (finalCategoryId, finalCategorySource) = when {
  command.categoryId != null -> command.categoryId to CategorySource.MANUAL
  else -> {
    val suggestion = categorizationService.suggest(accountId, command.description, command.creditDebitType)
    (suggestion?.categoryId) to (if (suggestion != null) CategorySource.RULE else null)
  }
}
```

**Update flow addition**:
```kotlin
// If categoryId is changing:
if (command.categoryId != null && command.categoryId != existing.categoryId) {
  finalCategorySource = CategorySource.MANUAL
  categorizationService.learnFromManualCategorization(accountId, existing.description, command.categoryId)
}
```

**Acceptance criteria**:
- Creating a transaction with description "NETFLIX" auto-assigns Streaming category, `categorySource = RULE`
- Creating a transaction with explicit `categoryId` overrides auto-categorization, `categorySource = MANUAL`
- Manually re-categorizing creates an implicit rule for the description pattern
- Auto-categorization failure (exception in `CategorizationService`) does not fail the transaction mutation — log and continue

---

#### CAT-12 — Implicit rule learning (already defined in CAT-10/CAT-11)

**Depends on**: CAT-11

**Description**: Verify and test that the implicit learning path defined in CAT-10 (`learnFromManualCategorization`) and hooked in CAT-11 works end-to-end. This story is primarily about validation and edge cases.

**Edge cases to handle**:
- Description is null or blank → no rule created (guard in service)
- User categorizes then re-categorizes the same transaction → existing implicit rule updated
- User deletes a category that has implicit rules pointing to it → rules remain (soft-deleted category) — the rule is silently skipped during evaluation if the category is deleted

**Acceptance criteria**:
- After manually categorizing "PADARIA DO ZE" as Alimentação, a new implicit rule exists with `pattern = "padaria do ze"`, `isImplicit = true`
- Next transaction with description "PADARIA DO ZE LTDA" matches the implicit rule (CONTAINS match)
- Re-categorizing changes the implicit rule's `category_id` to the new choice

---

#### CAT-13 — `CategoryRule` CRUD mutations

**Depends on**: CAT-09

**Description**: Expose `CategoryRule` CRUD to the GraphQL API so users can manage their explicit rules and view/delete implicit ones.

**New/modified files**:
- `graphql/schema.graphqls` — add `CategoryRule` type, `categoryRules` query, `createCategoryRule`, `updateCategoryRule`, `deleteCategoryRule` mutations
- `graphql/dto/CategoryRuleDTO.kt` — **new file**
- `graphql/resolver/TransactionCategoryResolver.kt` — add rule handlers
- `graphql/BankingWiringFactory.kt` — wire all new data fetchers

**Service logic**:
```
createCategoryRule(input, accountId):
  1. Verify categoryId accessible to accountId
  2. Validate pattern_type + pattern (e.g., REGEX: compile and catch parse errors)
  3. Check no duplicate (pattern, patternType, accountId) among non-deleted rules
  4. Save with is_implicit = false (user-created rules are always explicit)

deleteCategoryRule(id, accountId):
  1. Assert rule.accountId == accountId (cannot delete system or other tenants' rules)
  2. Assert !rule.isSystem
  3. Soft-delete
```

**Acceptance criteria**:
- `categoryRules` query returns all user rules (explicit + implicit) ordered by priority
- System rules are not returned (they are not editable by users and visible only as system behavior)
- Creating a rule with invalid REGEX pattern returns a validation error
- Deleting a system rule returns an error

---

### Sprint 4 — Analytics

---

#### CAT-14 — `transactionsByCategory` query

**Depends on**: _(nothing — reads existing data)_

**Description**: Implement `transactionsByCategory(financialAccountId, year, month, applicableTo)` query that returns category totals for a given period. Primary use case: expense breakdown chart (pizza chart / ranked list) in the monthly summary.

**New/modified files**:
- `graphql/schema.graphqls` — add `CategoryTotal` type + `transactionsByCategory` query
- `domain/transaction/MonthSummaryQueryService.kt` — add `categoryBreakdown(financialAccountId?, year, month, applicableTo?)` method
- `repo/TransactionEntityRepository.kt` — add native SQL query (see below)
- `graphql/dto/CategoryTotalDTO.kt` — **new file**
- `graphql/resolver/TransactionResolver.kt` — add `transactionsByCategory` handler
- `graphql/BankingWiringFactory.kt` — wire with `BANKING_TRANSACTION_VIEW` permission

**SQL query**:
```sql
SELECT
  tc.id             AS category_id,
  tc.name           AS category_name,
  tc.icon           AS category_icon,
  tc.color          AS category_color,
  tc.parent_id      AS parent_id,
  tc.applicable_to  AS applicable_to,
  tc.is_system      AS is_system,
  tc.sort_order     AS sort_order,
  SUM(t.amount)     AS total_amount,
  COUNT(t.id)       AS transaction_count
FROM banking.transactions t
JOIN banking.transaction_categories tc ON t.category_id = tc.id
WHERE t.account_id = :accountId
  AND (:financialAccountId IS NULL OR t.financial_account_id = :financialAccountId)
  AND EXTRACT(YEAR  FROM t.date) = :year
  AND EXTRACT(MONTH FROM t.date) = :month
  AND t.deleted_at IS NULL
  AND t.status IN ('POSTED', 'PENDING')
  AND (:applicableTo IS NULL
       OR tc.applicable_to = :applicableTo
       OR tc.applicable_to = 'ANY')
  AND (:applicableTo IS NULL
       OR t.credit_debit_type = CASE :applicableTo
           WHEN 'INCOME' THEN 'CREDIT'
           WHEN 'EXPENSE' THEN 'DEBIT'
           ELSE t.credit_debit_type END)
GROUP BY tc.id, tc.name, tc.icon, tc.color, tc.parent_id, tc.applicable_to, tc.is_system, tc.sort_order
ORDER BY total_amount DESC;
```

**Percentage computation** (in service layer, not SQL):
```kotlin
val totalForPeriod = rows.sumOf { it.totalAmount }
rows.map { it.copy(percentage = if (totalForPeriod > 0) it.totalAmount / totalForPeriod * 100 else 0.0) }
```

**Acceptance criteria**:
- Returns categories sorted by `totalAmount DESC`
- `percentage` sums to ~100% (floating point tolerance)
- `applicableTo = EXPENSE` returns only debit transactions linked to EXPENSE/ANY categories
- Transactions without a category are excluded from the breakdown
- `financialAccountId = null` aggregates across all accounts of the tenant

---

#### CAT-15 — Category breakdown in `MonthSummary`

**Depends on**: CAT-14

**Description**: Add `categoryBreakdown` as an optional field on the `MonthSummary` GraphQL type, so the frontend can fetch the monthly summary and category breakdown in one query.

**Modified files**:
- `graphql/schema.graphqls`:
  ```graphql
  type MonthSummary {
    # ... existing fields ...
    expensesByCategory: [CategoryTotal!]!   # top expense categories for the month
    incomeByCategory: [CategoryTotal!]!     # top income categories for the month
  }
  ```
- `domain/transaction/MonthSummary.kt` — add `expensesByCategory` + `incomeByCategory` lists
- `domain/transaction/MonthSummaryQueryService.kt` — compute breakdowns alongside the snapshot read
- `graphql/dto/MonthSummaryDTO.kt` — add new fields

**Design note**: These fields are computed on-demand (JOIN query, not stored in snapshot). At MVP scale (< 200 transactions/month) this is < 5ms. The existing snapshot stores only the scalar totals. See the monthly-summary-snapshots doc for rationale.

**Acceptance criteria**:
- `monthSummary` returns `expensesByCategory` ordered by `totalAmount DESC`
- Empty list (not null) when no categorized expenses exist for the month
- Consistent with `transactionsByCategory` standalone query results

---

### Sprint 5 — Tests

---

#### CAT-16 — Unit tests: `CategorizationService`

**Depends on**: CAT-10

**Test class**: `test/unit/CategorizationServiceTest.kt` (JUnit 5 + Mockito-Kotlin, no DB)

| Test | Verify |
|---|---|
| `suggest` — CONTAINS match on exact word | Returns correct categoryId |
| `suggest` — CONTAINS match on partial description | Returns correct categoryId |
| `suggest` — STARTS_WITH match | Returns categoryId; non-match on reversed string |
| `suggest` — EXACT match | Returns categoryId only for exact normalized string |
| `suggest` — REGEX match | Returns categoryId for valid regex |
| `suggest` — REGEX invalid pattern | Does not throw; skips rule gracefully |
| `suggest` — null description | Returns null |
| `suggest` — blank description | Returns null |
| `suggest` — no matching rules | Returns null |
| `suggest` — explicit user rule (priority 40) overrides implicit rule (priority 60) | Returns explicit rule's category |
| `suggest` — implicit rule (priority 60) overrides system rule (priority 100) | Returns implicit rule's category |
| `suggest` — explicit user rule (priority 40) overrides system rule (priority 100) | Returns explicit rule's category |
| `suggest` — applicable_to INCOME rule skipped for EXPENSE transaction | Returns null |
| `suggest` — applicable_to ANY matches both INCOME and EXPENSE | Returns categoryId for both |
| `normalize` — removes accents | `"café"` → `"cafe"` |
| `normalize` — lowercases | `"NETFLIX"` → `"netflix"` |
| `normalize` — trims spaces | `"  iFood  "` → `"ifood"` |
| `normalize` — collapses multiple spaces | `"pao   de   acucar"` → `"pao de acucar"` |
| `learnFromManualCategorization` — no existing rule | Creates new implicit rule |
| `learnFromManualCategorization` — existing rule, same category | Does not create duplicate |
| `learnFromManualCategorization` — existing rule, different category | Updates existing rule's categoryId |
| `learnFromManualCategorization` — null description | No-op, no exception |

---

#### CAT-17 — Integration tests: full categorization lifecycle

**Depends on**: CAT-11, CAT-12

**Test class**: `test/integration/CategorizationLifecycleIT.kt` (extends `BankingPostgresIntegrationTest`)

| Scenario | Verify |
|---|---|
| Create transaction with description matching system rule | `categoryId` set; `categorySource = RULE` |
| Create transaction with explicit `categoryId` | `categoryId` set; `categorySource = MANUAL`; rule NOT created |
| Create transaction with no matching rule | `categoryId = null`; `categorySource = null` |
| Manually re-categorize a transaction | `categorySource = MANUAL`; implicit rule created |
| Create second transaction with same description after manual categorization | Auto-matched to new implicit rule; `categorySource = RULE` |
| Re-categorize again to different category | Implicit rule updated to new category |
| User creates explicit rule via `createCategoryRule` mutation | Next transaction with matching description uses explicit rule |
| Explicit rule (priority 40) overrides system rule (priority 100) for same description | Explicit rule's category is assigned |
| Implicit rule (priority 60) overrides system rule (priority 100) for same description | Implicit rule's category is assigned |
| Explicit rule (priority 40) overrides implicit rule (priority 60) for same description | Explicit rule's category is assigned; implicit rule is not evaluated |
| Delete explicit rule | Subsequent transactions fall through to implicit rule (if any), then system rule |
| `bulkCategorize` with 3 transaction IDs | All 3 updated; `categorySource = MANUAL` for all; no implicit rules created |
| `transactions(uncategorized: true)` | Returns only transactions with `categoryId = null` |
| `transactions(categoryId: <alimentacao-root-id>)` | Returns transactions assigned to Alimentação AND all subcategories |

---

#### CAT-18 — Integration tests: Category CRUD

**Depends on**: CAT-01, CAT-02, CAT-03, CAT-04

**Test class**: `test/integration/TransactionCategoryServiceIT.kt` (extends `BankingPostgresIntegrationTest`)

| Scenario | Verify |
|---|---|
| Create root category | Returned with `parentId = null`, `isSystem = false`, correct `accountId` |
| Create subcategory with valid parent | Returned with correct `parentId` |
| Create subcategory of subcategory | Throws `CategoryDepthExceededException` |
| Create category with duplicate name + same parent | Throws `DuplicateCategoryNameException` |
| Create category with same name, different parent | Succeeds (not a duplicate) |
| Create subcategory with incompatible `applicable_to` | Throws `CategoryApplicableToMismatchException` |
| Update category name | Updated; returned with new name |
| Update system category | Throws `SystemCategoryNotEditableException` |
| Update category from another account | Returns 404 (not found for tenant) |
| Delete subcategory | Soft-deleted; not returned in `transactionCategories` |
| Delete root category with subcategories | Root + all children soft-deleted |
| Delete system category | Throws `SystemCategoryNotEditableException` |
| Transactions with deleted category | Transactions still have `categoryId` (not nulled out); UI shows "removed" |
| `transactionCategories` query | Returns system + user categories; excludes soft-deleted |

---

### Sprint 6 — Frontend

> Frontend stories are independent and can be developed in parallel once Sprint 1–2 backend is deployed and GraphQL codegen is updated.

---

#### CAT-19 — Category picker component

**Depends on**: Sprint 1–2 backend + GraphQL codegen update

**Description**: Reusable `CategoryPicker` component used inline in the transaction form and as a bottom-sheet/popover in the quick-categorize flow from the transaction list.

**Behavior**:
- Fetches `transactionCategories` query once on mount; caches in Apollo
- Displays two-column layout: root categories on the left (or as section headers), subcategories on the right (or below)
- Filters categories by `applicableTo`: if `creditDebitType = DEBIT` → show EXPENSE + ANY; if `creditDebitType = CREDIT` → show INCOME + ANY; TRANSFER transactions → show TRANSFER + ANY
- Shows category icon + color + name per item
- Search input to filter by name (client-side filter, no extra API call)
- Recently used section at top (last 5 categories used by the user, stored in localStorage)
- Selection returns `categoryId: UUID`

**Props**:
```typescript
interface CategoryPickerProps {
  value: string | null           // selected categoryId
  onChange: (id: string | null) => void
  creditDebitType: 'CREDIT' | 'DEBIT'
  onClose: () => void
}
```

**Acceptance criteria**:
- Shows correct categories filtered by transaction side
- Search filters list client-side without API call
- Recently used appears at top of list
- Tapping a category calls `onChange` and closes the picker
- Shows `CategoryIcon` with correct MUI icon + color (CAT-20 dependency)
- "Sem categoria" option clears the selection (`onChange(null)`)

---

#### CAT-20 — `CategoryIcon` component (MUI icon renderer)

**Depends on**: CAT-05 (MUI icon migration)

**Description**: Reusable React component that renders a category's icon using Material UI icons by name.

**Recommended implementation** (static map for tree-shaking):
```typescript
// src/components/category/CategoryIcon.tsx
import RestaurantIcon from '@mui/icons-material/Restaurant'
import HomeIcon from '@mui/icons-material/Home'
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar'
// ... all 50+ icons

const iconMap: Record<string, React.ComponentType<SvgIconProps>> = {
  restaurant: RestaurantIcon,
  home: HomeIcon,
  directions_car: DirectionsCarIcon,
  // ...
  help_outline: HelpOutlineIcon,  // fallback for "Outros"
}

interface CategoryIconProps {
  iconName: string | null | undefined
  color: string | null | undefined
  size?: number
  sx?: SxProps
}

export function CategoryIcon({ iconName, color, size = 24, sx }: CategoryIconProps) {
  const IconComponent = (iconName && iconMap[iconName]) ?? HelpOutlineIcon
  return (
    <IconComponent
      sx={{ color: color ?? 'text.secondary', fontSize: size, ...sx }}
    />
  )
}
```

**Acceptance criteria**:
- All 50+ MUI icon names from the migration mapping are in `iconMap`
- Unknown icon names gracefully fall back to `HelpOutlineIcon`
- Color prop is applied as MUI `sx.color`
- Renders correctly in light and dark mode
- Renders correctly at sizes 16, 20, 24, 32px

---

#### CAT-21 — Category management screen

**Depends on**: CAT-01, CAT-02, CAT-03 backend + GraphQL codegen

**Description**: Settings screen where users can create, edit, delete, and reorder their custom categories.

**Screen layout**:
```
[+] Nova categoria

── Sistema ─────────────────────────────
  🍽️ Alimentação            EXPENSE  [>]
    🛒 Supermercado          EXPENSE
    🍜 Restaurante           EXPENSE
    ...
  🏠 Moradia                EXPENSE  [>]
  ...

── Minhas categorias ────────────────────
  🎸 Música ao vivo         EXPENSE  [✏️] [🗑️]
  💼 Freelance              INCOME   [✏️] [🗑️]
  [+] Nova subcategoria

```

**Features**:
- System categories: read-only (no edit/delete buttons), expandable to show subcategories
- User categories: full CRUD; drag-to-reorder for `sort_order`
- Create form: name, applicableTo (select), parentId (optional — select from roots), icon (icon picker grid), color (color swatches)
- Icon picker: grid of all available MUI icons rendered via `CategoryIcon`; search by name
- Color picker: predefined swatch palette (no free hex input at MVP)
- Mutations: `createTransactionCategory`, `updateTransactionCategory`, `deleteTransactionCategory`

**Acceptance criteria**:
- System categories shown as read-only reference
- User categories are editable and deletable
- Creating a user category reflects immediately in `transactionCategories` query (Apollo cache invalidation)
- Icon picker shows all available icons with correct rendering
- Deleting a category shows a confirmation dialog mentioning that existing transactions keep their category assignment

---

#### CAT-22 — Bulk categorize UI (post-import flow)

**Depends on**: CAT-07 backend + GraphQL codegen

**Description**: After importing transactions (OFX/CSV), surface a "categorize in bulk" flow for uncategorized transactions.

**Entry point**: After import completes, show a banner: "X transações sem categoria — Categorizar agora →"

**Bulk categorize screen**:
```
Selecionar tudo  [ 12 selecionadas ]   [Categorizar selecionadas]

☑ 15/03  NETFLIX.COM*BR        R$ 55,90    ● Streaming (sugerido)
☑ 15/03  IFOOD*RESTAURANTE     R$ 38,70    ● Delivery (sugerido)
☐ 14/03  PADARIA DO ZE LTDA    R$ 12,50    ○ Sem categoria
☐ 13/03  CONTA LUZ ENEL        R$ 185,00   ○ Sem categoria
```

**Behavior**:
- Lists uncategorized transactions via `transactions(uncategorized: true)`
- Auto-suggested categories (from `categorySource = RULE`) shown with "sugerido" badge
- User can accept suggestions (select + "Categorizar selecionadas") or override per-row
- Selecting multiple rows + choosing a category calls `bulkCategorize`
- After bulk categorize, affected rows update inline without full page reload

**Acceptance criteria**:
- Only `uncategorized` transactions are shown initially
- Rows with auto-suggested categories show the suggestion pre-selected but with "sugerido" badge
- `bulkCategorize` called with correct IDs + categoryId
- Rows disappear from the list after successful categorization
- "Tudo categorizado" empty state shown when no more uncategorized transactions exist

---

#### CAT-23 — Category rules management screen

**Depends on**: CAT-13 backend + GraphQL codegen

**Description**: Settings screen for managing auto-categorization rules. Shows both explicit and implicit rules; allows CRUD.

**Screen layout**:
```
[+] Nova regra

── Minhas regras ────────────────────────────────────────────────
  ✓  Contém "padaria do ze"    → 🍞 Padaria     [implícito] [🗑️]
  ✓  Contém "academia"         → 🏋️ Academia    [explícito] [✏️] [🗑️]
  ✓  Contém "salon"            → 💈 Cabeleireiro [explícito] [✏️] [🗑️]

── Regras do sistema (somente leitura) ──────────────────────────
  ✓  Contém "netflix"          → 📺 Streaming
  ✓  Contém "ifood"            → 🛵 Delivery
  ...
```

**Features**:
- Implicit rules shown with "implícito" badge (created by learning engine); can be deleted but not edited
- Explicit rules are fully editable
- System rules shown as read-only reference
- Create form: patternType (select), pattern (text), category (CategoryPicker), applicableTo (select)
- Pattern preview: real-time test against a sample description input

**Acceptance criteria**:
- All user rules (implicit + explicit) shown; system rules in read-only section
- Deleting an implicit rule prevents future auto-matching for that pattern
- Creating an explicit rule triggers auto-categorization for new transactions immediately
- Pattern test input shows "Match ✓" / "No match" in real-time

---

#### CAT-24 — Category breakdown chart in MonthSummary

**Depends on**: CAT-14 backend + GraphQL codegen

**Description**: Add a category breakdown chart (donut/bar) below the `MonthSummaryHeader`. Shows top expense categories for the selected month.

**Chart options** (choose one per design system):
- Donut chart: segments per category, color-coded, legend below
- Ranked list with progress bars: simpler to implement, more readable on mobile

**Recommended for MVP**: Ranked list with progress bars (no charting library dependency)

```
── Maiores despesas ──────────────────────────────
  🍽️ Alimentação      R$ 1.240,00  ████████░░  42%
  🏠 Moradia          R$ 950,00    ██████░░░░  32%
  🚗 Transporte       R$ 420,00    ███░░░░░░░  14%
  💊 Saúde            R$ 180,00    █░░░░░░░░░   6%
  ○ Outros / Sem cat  R$ 180,00    █░░░░░░░░░   6%
  ───────────────────────────────────────────────
  Total despesas      R$ 2.970,00
```

**Data source**: `transactionsByCategory(financialAccountId, year, month, applicableTo: EXPENSE)`

**Acceptance criteria**:
- Renders correctly when no categorized expenses exist (empty state: "Nenhuma despesa categorizada neste mês")
- Clicking a category row navigates to `transactions(categoryId)` filtered list
- "Outros / Sem cat" row aggregates all uncategorized transaction expenses
- Updates when user switches month in `MonthYearCarousel`

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Emojis vs icon library for category icons? | **MUI icon name strings** stored in `icon` column | Emojis render differently across OS/font, cannot be tinted by app theme, ignore dark mode. MUI icons are SVG, themeable, tree-shakeable, consistent across all platforms. No schema change needed — `icon` is already `TEXT`. |
| Max hierarchy depth: 2 vs unlimited? | **2 levels** (root + subcategory only) | Matches all major Brazilian PFM apps. Deeper hierarchies complicate the category picker UX significantly without proportional benefit for personal finance use cases. |
| System category updates: forbidden vs allowed? | **Forbidden** (isSystem = read-only) | System categories are the shared contract between the app and all tenants. Allowing user edits would break "shared understanding" of what "Alimentação" means across the app. Users create their own categories instead. |
| Delete with existing transactions: block vs cascade vs allow? | **Allow with preserved `category_id`** | Financial history should never be altered silently. Deleting a category does not retroactively un-categorize transactions — it just removes the category from future use. UI shows "Categoria removida" label. |
| Auto-categorization: rule-based vs ML vs both? | **Rule-based MVP + implicit learning; Claude API as future premium** | Rule-based is deterministic, explainable, user-controllable. Implicit learning covers 80% of the value without complex ML infrastructure. Claude API deferred until usage data justifies the cost per API call. |
| Implicit rules: always create vs only on N repetitions? | **Always on first manual categorization** | One repetition is enough signal for personal finance (NETFLIX is always NETFLIX). Requiring N repetitions adds friction without proportional quality benefit. User can always delete implicit rules. |
| `categorySource` stored vs derived? | **Stored** (column on transactions) | Cannot be derived retroactively — once a rule is deleted, we lose the information that the category was auto-assigned. Stored value is the ground truth for analytics and future ML training. |
| Category breakdown in snapshot vs on-demand? | **On-demand** (no snapshot) | Too many combinations (account × month × category). Single-month GROUP BY over transactions is < 5ms at MVP scale. If it becomes slow, a `monthly_category_snapshots` table is the right solution — but premature at this stage. See the monthly-summary-snapshots doc for the same decision applied to the main summary. |
| Bulk categorize: trigger implicit learning? | **No** | Bulk assignment is an explicit batch action, not a description-based pattern. Learning from bulk would pollute the rule engine with noise if the user bulk-assigns a diverse set of transactions to one category. |

---

## Open Questions

- Should the category picker support **creating a new category inline** (without leaving the transaction form)? Pro: reduces friction. Con: adds complexity to the picker component. Recommendation: defer to Sprint 6 polish.
- Should `reorderCategories` be a dedicated mutation, or is `updateTransactionCategory(sortOrder: N)` sufficient? Current proposal: individual update is sufficient for MVP; a dedicated `reorderCategories([{ id, sortOrder }])` batch mutation can be added if the drag-to-reorder UX requires it.
- For the category breakdown chart, should **uncategorized transactions** have a dedicated "Sem categoria" bucket, or be excluded from the chart? Recommendation: include as "Outros / Sem cat" bucket — hiding uncategorized expenses from the chart would under-report actual spending.
- Should **Pluggy category mapping** be handled in Phase 5 (Open Finance import) or here? The `pluggy_category_id` column already exists. The mapping logic (Pluggy category → Invistus category) is most natural in the import service. Deferred to Phase 5.
- Should the AI categorization (Claude API) be an opt-in feature (user enables it in settings, billed usage) or always-on? TBD based on cost analysis when Phase 5 usage data is available.
