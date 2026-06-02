---
title: Transaction Enrichment Blueprint
type: feature-spec
category: banking
status: draft
version: 1.0.0
tags: [banking, categorization, enrichment, import, csv, ofx, ai, open-finance]
related:
  - 02-architecture/service-architecture.md
  - 03-features/ai/llm-orchestrator/llm-orchestrator-blueprint.md
  - 03-features/b2c/personal_finance_control/personal-finance-control.md
last_updated: 2026-03-02
---

# Transaction Enrichment

## Feature Name

**`transaction-enrichment`**

This name reflects the industry-standard scope of the problem: transforming raw transaction data (free-text descriptions, import files, manual entries) into structured, categorized, and enriched records. It intentionally goes beyond "categorization" to encompass merchant recognition, source normalization, rule learning, and AI-assisted classification.

---

## Problem Statement

Transactions enter the system from multiple sources — manual entry, file imports (OFX, generic CSV), and future provider integrations (Open Finance, payment processors). In all cases, the raw data is unstructured: descriptions are bank-formatted strings, categories are absent or source-specific, and merchant identity is implicit.

Today, every imported transaction is stored as `OTHER_EXPENSE` or `OTHER_INCOME` with no category assigned. This means users must manually categorize every transaction, which is the single largest friction point in personal finance management.

The goal of this feature is to **automatically assign a meaningful category to every transaction**, with high confidence where possible and a graceful fallback path where it is not — always giving the user the ability to correct and teach the system.

---

## Research: Industry Context

### What the market does

Transaction enrichment is an established B2B segment. Leading platforms (Plaid Enrich, Mastercard Transaction Enrichment, Ntropy, Spade) accept raw bank description strings and return structured data: clean merchant name, logo, category, address, and MCC. The core insight is that **the description text alone, combined with amount and direction, is sufficient to categorize the vast majority of transactions** — if you have a large enough labeled dataset and a good matching strategy.

### Categorization strategies in use

| Strategy | Accuracy | Cost | Latency | Requirements |
|---|---|---|---|---|
| MCC lookup | Very high | Zero | Zero | Card transaction data (not available in OFX/CSV) |
| Provider passthrough | High | Zero | Zero | Provider integration (future phase) |
| CNPJ → CNAE lookup | High | Zero | Low | PIX/OFB with CNPJ (future phase) |
| Source-specific passthrough | High | Zero | Zero | Source already provides category (e.g. some CSVs) |
| Curated keyword rules | Medium–High | Zero | Zero | Maintained rule table |
| User pattern learning | High (personal) | Zero | Zero | User history |
| LLM classification | Medium–High | Low | Medium | LLM API access |
| Trained ML classifier | High | Near-zero | Very low | Labeled transaction dataset |

### Brazilian market specifics

- **CNPJ enrichment via PIX**: PIX transactions often carry the recipient's CNPJ. Querying CNAE (Brazil's activity classification, analogous to MCC) from public Receita Federal data enables deterministic category resolution — the most powerful enrichment available without card integration.
- **Nubank open dataset**: Nubank has published anonymized transaction data including merchant categories, usable as seed data for rule and model training.
- **CNAE public database**: ~1,300 economic activity codes, all public, mappable to our category taxonomy.
- **Open Finance Brasil (OFB)**: Defines its own transaction type and category taxonomy. Transactions from OFB integrations include richer metadata (MCC, CNPJ, transaction subtype).

### Key industry lesson

The best systems use a **layered pipeline**: deterministic high-confidence signals first (MCC, provider category, CNPJ/CNAE), then learned rules, then ML/LLM fallback. Each layer has a confidence score. The first layer to exceed a threshold wins. The user always has the final say, and corrections feed back into the system.

---

## Business Decisions

### BD-01: Categorization is automatic by default, manual is always possible
Every transaction must attempt automatic categorization at import or creation time. The user can always override. No transaction should remain uncategorized silently — if automation fails, the transaction is flagged for manual review.

### BD-02: User corrections teach the system
When a user manually sets or changes a category, the system creates a personal enrichment rule scoped to their account. This rule is applied automatically on future transactions with matching patterns. The system gets smarter with use.

### BD-03: Category taxonomy is aligned with Open Finance Brasil
The `TransactionCategory` system taxonomy serves as the single source of truth. External category vocabularies (CSV sources, future providers) are always mapped to this taxonomy via a source mapping layer. The taxonomy itself will carry an `ofb_type_slug` reference column for future OFB alignment.

### BD-04: Provider names are generic throughout
The system uses generic terms (`provider`, `source_category`, `external_id`) rather than coupling to specific integrations (e.g. no "pluggy_category_id"). This avoids refactoring when integrations change. Provider-specific adapters map to generic interfaces at the infra layer.

### BD-05: Confidence is always tracked
Every category assignment carries a `confidence` score (0.0–1.0) and a `source` label indicating how it was derived. This enables UI differentiation (e.g. showing a "suggested" badge) and future model evaluation.

### BD-06: LLM is a fallback, not a primary path
LLM classification runs asynchronously for transactions that passed through all deterministic layers without a confident match. It is never on the critical path of an import or manual creation. Results are surfaced to the user as suggestions to confirm.

### BD-07: The CSV importer is source-agnostic
The system does not hardcode parsers per institution. Instead, it detects column semantics from CSV headers using a recognition strategy, and applies institution-specific column mappings when the source is identified. This supports any bank's CSV export format, including formats that do not include an explicit category column.

---

## Technical Decisions

### TD-01: Enrichment pipeline is a layered chain of resolvers
Each resolver implements a `CategorySuggestionResolver` interface, returning a result with `categoryId`, `confidence`, and `source`. The pipeline iterates resolvers in priority order and stops at the first result exceeding the confidence threshold. This is pure domain logic with no infrastructure dependencies.

**Resolver execution order:**
1. Source-specific category passthrough (for CSVs that include a category column)
2. Import type heuristic (OFX `TRNTYPE`, `creditDebitType` + `TransactionType`)
3. System keyword/pattern rules (curated global rules)
4. User pattern rules (learned from manual corrections, account-scoped)
5. LLM fallback (async, does not block import)

### TD-02: CSV parsing is schema-agnostic with column recognition
The generic CSV importer follows a two-step process:
1. **Institution detection**: identify the source institution from header fingerprint or file metadata
2. **Column mapping**: apply a known column map for the detected institution, or fall back to best-effort column recognition using semantic header matching (e.g. "valor", "amount", "montante" → amount field)

Known institution mappings are stored as configuration, not hardcoded in parsing logic. New institutions can be added without code changes.

When no explicit category column is present, the pipeline falls through to the rule-based and LLM resolvers.

### TD-03: Source category passthrough uses a mapping table, not hardcoded logic
When a CSV source provides a category column (e.g. some institution exports include their own category labels), those labels are normalized through a `category_source_mappings` table: `(source_id, source_category_value) → category_id`. This table is seeded per institution and can be extended without code changes.

### TD-04: Enrichment rules are stored in the database
Both system-level (global) and user-level (account-scoped) rules live in a `category_rules` table with: pattern, match type (PREFIX, CONTAINS, EXACT, REGEX), target field (description, counterparty name, etc.), category, confidence, and priority. Rules are cacheable and evaluated in-process.

### TD-05: LLM enrichment runs as an async Kafka worker
After import completes, transactions without a confident category are published to a Kafka topic. A dedicated worker processes them in batches, calls the LLM with structured context (description, raw description, amount range, credit/debit direction, transaction type), and writes back a suggested `category_id` with `confidence` and `source = 'LLM'`. No blocking of the import pipeline.

### TD-06: The `transactions` table gains enrichment metadata columns
Two new columns are added:
- `category_confidence FLOAT` — confidence of the assigned category (null if manually set by user, implying full confidence)
- `category_source TEXT` — one of: `SOURCE_PASSTHROUGH`, `TYPE_HEURISTIC`, `SYSTEM_RULE`, `USER_RULE`, `LLM`, `MANUAL`

These columns enable UI differentiation, analytics on enrichment quality, and future model evaluation.

### TD-07: A long-term trained classifier replaces LLM for common patterns
As the system accumulates labeled data (user corrections + confirmed LLM suggestions), a lightweight classifier (TF-IDF + logistic regression, or a fine-tuned Portuguese language model) is trained offline and deployed as an internal service. It replaces LLM calls for high-frequency patterns, reducing cost and latency. This is a Phase 4 concern.

### TD-08: Open dataset strategy for bootstrapping the rule table
Before user data accumulates, the system's rule table is bootstrapped from:
- **Top-N Brazilian merchant patterns**: manually curated list of the most common merchants (iFood, Uber, Netflix, Spotify, Amazon, Mercado Livre, etc.) with their canonical categories
- **CNAE public database**: Receita Federal open data, mappable to our category taxonomy
- **Nubank open dataset**: published anonymized transaction data including merchant → category labels
- **Community rule promotion**: when N distinct accounts independently create the same user rule, it is a candidate for promotion to a system rule (future governance process)

---

## Use Cases by Source

### UC-01: Nubank CSV Import (and other CSVs with explicit category)

**Context**: Some institution CSV exports include a category column in the institution's own vocabulary.

**Enrichment path**:
1. Generic CSV importer detects institution from header fingerprint
2. Applies institution-specific column map (date, amount, description, category)
3. Source passthrough resolver maps the institution's category label → our `category_id` via `category_source_mappings`
4. If mapping exists: transaction is categorized with high confidence (`SOURCE_PASSTHROUGH`)
5. If no mapping: falls through to rule-based resolvers

**Key constraint**: The category column value from the source is stored in `description_raw` as an audit trail. The resolved `category_id` is stored separately. These are independent fields.

**Nubank-specific notes**:
- Current parser incorrectly stores the category column in `description_raw`. This must be corrected: `description_raw` should hold the raw merchant name/memo; the category column should feed the source passthrough resolver.
- Known Nubank category vocabulary: "Restaurante", "Mercado", "Delivery", "Transporte", "Saúde", "Educação", "Lazer", "Serviços", "Eletrônicos", "Streaming", "Pet Shop", "Outros".

---

### UC-02: OFX / OFC Import

**Context**: OFX files provide `TRNTYPE`, `NAME`, and `MEMO`. No category. No MCC. Description quality varies significantly by institution.

**Enrichment path**:
1. OFX `TRNTYPE` heuristic resolver maps to `TransactionType` and, where deterministic, to a category:
   - `INT` → Tarifas Bancárias > Juros
   - `FEE` / `SRVCHG` → Tarifas Bancárias
   - `ATM` → `TransactionType.WITHDRAWAL` (no category auto-assigned)
   - `DEP` → `TransactionType.DEPOSIT` (no category auto-assigned)
   - `XFER` → Transferência (direction determines INCOME or EXPENSE subcategory)
2. System keyword/pattern rules evaluated against `NAME` and `MEMO`
3. User pattern rules evaluated (account-scoped)
4. If no confident match: published to LLM enrichment queue

**Key constraint**: OFX `TRNTYPE` mapping determines `TransactionType` (the classification field already on the domain model). It is a secondary signal for category, not the primary driver. The two fields are independent.

---

### UC-03: Manual Entry

**Context**: User creates a transaction manually, typing a free-form description.

**Enrichment path**:
1. As the user types, the frontend queries a suggestion endpoint with the partial description
2. Suggestion endpoint runs system rules + user pattern rules in-process (fast, synchronous)
3. Returns ranked category suggestions with confidence scores
4. User may accept a suggestion or select manually
5. On save: if category was manually selected, `category_source = 'MANUAL'`; if accepted from suggestion, the originating resolver source is recorded
6. Either way, a user pattern rule is created or reinforced for the normalized description

**Key behavior**: Manual categorization always creates or updates a user pattern rule. This is how the system learns from the user's intent even when no import is involved.

---

### UC-04: Category Taxonomy Alignment with Open Finance Brasil

**Context**: The `transaction_categories` system taxonomy must remain stable while being able to map to external category systems (OFB, provider-specific) as integrations are added.

**Design**:
- The current taxonomy is the canonical reference; it does not change to match external systems
- A new column `ofb_type_slug TEXT` on `transaction_categories` stores the corresponding OFB transaction type/subtype for each category
- Provider integrations map their own category vocabulary to our taxonomy via `category_source_mappings`, using a `source_id` of the provider's name
- The `pluggy_category_id` column is renamed to `provider_category_ref` or replaced by the generic mapping table approach

**OFB type alignment examples**:
```
Tarifas Bancárias → ofb_type_slug: 'OUTROS_DEBITOS' / 'ENCARGOS'
Transferência enviada > PIX → ofb_type_slug: 'TRANSFERENCIA_PIX'
Salário → ofb_type_slug: 'CREDITO_SALARIO'
Investimento aplicado → ofb_type_slug: 'INVESTIMENTO'
```

---

## Enrichment Engine: Building Our Own

### Goal
Build an internal enrichment capability that starts with zero cost (curated rules), grows with user feedback (learned rules), leverages LLM for hard cases (async), and eventually matures into a trained classifier (owned model). No dependency on external enrichment services.

### Data sources for bootstrapping

| Source | Type | What it provides | How to use |
|---|---|---|---|
| Top-N merchant curated list | Manual curation | ~50 merchants covering ~40% of Brazilian transaction volume | Seed `category_rules` table |
| Nubank open dataset | Public | Anonymized transactions with category labels | Validate and extend rule table |
| CNAE / Receita Federal | Open government data | ~1,300 economic activity codes → category mapping | CNPJ enrichment (future PIX phase) |
| User corrections | Organic | Real, account-specific labeled data | Promote to user rules, eventually global |
| Confirmed LLM suggestions | LLM output | Broad coverage, varied quality | Training corpus for future classifier |

### Rule quality tiers

| Tier | Description | Confidence range |
|---|---|---|
| **Exact** | Full string match on normalized description | 0.95–1.0 |
| **Prefix** | Known merchant prefix (e.g. "IFOOD *") | 0.85–0.95 |
| **Contains** | Keyword present anywhere in description | 0.65–0.85 |
| **Regex** | Complex pattern (e.g. utility bill formats) | 0.70–0.90 |
| **LLM** | AI classification, no rule match | 0.50–0.85 |

### Confidence threshold policy

- `≥ 0.85`: category is applied automatically, no user prompt
- `0.60–0.84`: category is applied with a "suggested" UI indicator; one-tap to confirm or change
- `< 0.60`: transaction is marked uncategorized; user is prompted

---

## Schema Changes Required

### New columns on `banking.transactions`
- `category_confidence FLOAT` — enrichment confidence (null for user-set)
- `category_source TEXT` — enrichment origin label

### Changes to `banking.transaction_categories`
- Add `ofb_type_slug TEXT` — OFB taxonomy alignment
- The existing `pluggy_category_id` column should be migrated to the generic mapping table approach or renamed to reflect provider-agnosticism

### New table: `banking.category_source_mappings`
Stores per-source vocabulary mappings to our taxonomy.
- `source_id TEXT` — identifier for the source (e.g. `'NUBANK_CSV'`, `'BRADESCO_OFX'`, `'OFB'`)
- `source_value TEXT` — the category label as it appears in the source
- `category_id UUID` → `transaction_categories`
- Primary key: `(source_id, source_value)`

### New table: `banking.csv_institution_profiles`
Stores column mapping configurations for known CSV institutions.
- `institution_id TEXT` — e.g. `'NUBANK_CREDIT'`, `'ITAU_CHECKING'`
- `header_fingerprint TEXT` — normalized header string used for detection
- `column_map JSONB` — field name → column index/header mapping
- `has_category_column BOOLEAN`
- `category_source_id TEXT` → references `category_source_mappings.source_id`

### New table: `banking.category_rules`
The enrichment rule engine table.
- `id UUID`
- `account_id UUID` — null = system/global
- `source_filter TEXT` — null = any source
- `target_field TEXT` — `DESCRIPTION`, `DESCRIPTION_RAW`, `COUNTERPARTY_NAME`
- `pattern TEXT`
- `match_type TEXT` — `EXACT`, `PREFIX`, `CONTAINS`, `REGEX`
- `category_id UUID`
- `confidence FLOAT`
- `priority INT`
- `hit_count INT` — number of times this rule has matched
- `created_at`, `updated_at`

---

## Prioritized Action Plan

### Phase 1 — Foundation (MVP, file import scope only)

**1.1 — Fix Nubank CSV parser**
- Separate the `category` column from `description_raw` in `ParsedTransaction`
- `description_raw` must hold the merchant name/memo text, not the source category label
- Add a dedicated `sourceCategory` field to `ParsedTransaction` for the raw category string from the source file

**1.2 — Generic CSV importer with institution detection**
- Replace the hardcoded Nubank-only CSV parser with a generic `CsvImporter`
- Implement header fingerprint detection to identify known institutions
- Load column maps from `csv_institution_profiles` configuration
- For unknown CSVs: apply best-effort column recognition using normalized header name matching against known field synonyms (e.g. "valor", "amount", "montante" → amount; "data", "date", "dt" → date)
- Emit a `CsvParseResult` that includes detected institution, mapped fields, and any unrecognized columns for audit

**1.3 — Source mapping table and passthrough resolver**
- Create `banking.category_source_mappings` migration
- Seed Nubank CSV category vocabulary → our taxonomy mapping
- Implement `SourcePassthroughResolver`: looks up `(source_id, sourceCategory)` → `category_id`

**1.4 — OFX TRNTYPE heuristic resolver**
- Implement `ImportTypeHeuristicResolver`
- Map OFX `TRNTYPE` values to `TransactionType` and, where deterministic, to `category_id`
- Map `creditDebitType` alone as a last-resort signal (low confidence)

**1.5 — Enrichment pipeline wiring**
- Implement `CategoryEnrichmentPipeline` as an ordered chain of resolvers
- Wire into `ImportProcessor` after transaction assembly, before save
- Add `category_confidence` and `category_source` columns to `transactions` (migration)

**1.6 — OFB taxonomy alignment**
- Add `ofb_type_slug` column to `transaction_categories`
- Populate slug values for existing system categories
- Migrate or rename `pluggy_category_id` to the generic `category_source_mappings` table

---

### Phase 2 — Rule Engine and User Learning

**2.1 — System keyword rule table**
- Create `banking.category_rules` migration
- Seed initial rule set for top-N Brazilian merchants (curated list)
- Implement `SystemRuleResolver`: evaluates rules in priority order against description and counterparty fields

**2.2 — User pattern learning**
- Implement `UserRuleResolver`: same logic, account-scoped rules only
- On manual category set or change: create or update a user rule for the normalized description
- Expose a UI surface to view and manage personal rules

**2.3 — Category suggestion endpoint (manual entry UX)**
- Synchronous endpoint: given a partial description text and `creditDebitType`, return ranked category suggestions
- Runs system rules + user rules in-process; sub-50ms target
- Frontend uses this for typeahead suggestion while user types description

---

### Phase 3 — LLM Fallback

**3.1 — LLM enrichment Kafka worker**
- After import, publish uncategorized transactions (confidence below threshold) to a Kafka topic
- Worker consumes batches, calls LLM with structured context per transaction
- LLM is prompted with: normalized description, raw description, amount range bucket, credit/debit direction, transaction type
- LLM returns: `categorySlug`, `confidence`, `reasoning`
- Worker writes back `category_id`, `category_confidence = LLM result`, `category_source = 'LLM'`

**3.2 — Suggestion confirmation UX**
- Transactions with `category_source = 'LLM'` and confidence below auto-apply threshold surface a confirmation prompt in the UI
- User confirms → `category_source` updated to `MANUAL`; user rule created
- User changes → new category applied; user rule created for the corrected category

**3.3 — Rule promotion from LLM data**
- Confirmed LLM suggestions are candidates for system rule promotion
- When the same pattern + category combination is confirmed by N distinct accounts, flag for governance review
- Approved patterns are promoted to `category_rules` as system rules, removing future LLM dependency for that pattern

---

### Phase 4 — Owned Classifier (Post Open Finance)

**4.1 — Training data pipeline**
- Aggregate labeled transactions: user corrections + confirmed LLM suggestions
- Normalize and deduplicate descriptions
- Build training/validation dataset

**4.2 — Classifier training**
- Train a lightweight text classifier (TF-IDF + logistic regression baseline; upgrade to fine-tuned Portuguese language model if accuracy requires)
- Evaluate against held-out validation set; measure per-category precision and recall
- Target: replace LLM calls for patterns covered with ≥ 90% accuracy

**4.3 — Classifier deployment**
- Deploy as internal inference service
- Integrate into enrichment pipeline as a new resolver between user rules and LLM fallback
- Monitor accuracy continuously; retrain on new labeled data periodically

---

### Phase 5 — CNPJ / PIX Enrichment (alongside Open Finance)

**5.1 — CNPJ → CNAE lookup**
- Ingest CNAE public database from Receita Federal
- When a transaction carries a `counterparty_document` (CNPJ), resolve CNAE and map to `category_id`
- This is the highest-confidence free enrichment available for PIX transactions

**5.2 — Provider category passthrough**
- When transactions arrive from provider integrations (Open Finance, etc.), map provider category vocabulary via `category_source_mappings`
- Apply MCC → category mapping for card transactions when MCC is available

---

## Open Questions

- **Rule governance**: What process approves promotion of user rules to system rules? Who reviews? This needs a lightweight internal process before Phase 3.3.
- **Category suggestion UI**: Should suggestions appear inline (typeahead) during manual entry, or as a post-save notification? Both have merit; the former is more friction-reducing.
- **Multi-language CSV**: Some institution exports may use column headers in English. The column recognition strategy must handle both PT-BR and EN synonyms.
- **Confidence threshold values**: The thresholds proposed (0.85 auto-apply, 0.60 suggest) are initial estimates. They should be validated against real import data once the pipeline is live.
