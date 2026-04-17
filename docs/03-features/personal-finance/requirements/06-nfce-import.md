# NFC-e Management — Requirements Document

**Service:** Invistus Banking Service  
**Module:** Personal Finance — NFC-e (Consumer Electronic Invoice) Management  
**Document date:** 2026-04-16  
**Status:** Requirements consolidated for end-to-end NFC-e handling: capture, storage, deduplication, re-import after deletion, manual linking, and related product data

**Related documents:** [Product Price Tracking](./05-product-price-tracking.md) (line items, catalog, analytics built on top of NFC-e data)

---

## 1. Purpose of this document

This document explains **what NFC-e is**, **what data the product stores and why**, and **how the system must behave** from capture through storage, deduplication, user correction, and re-import. It is written so that readers without a tax or engineering background can still follow the story; technical sections spell out database and API implications where they matter for correctness.

---

## 2. What is NFC-e? (Reader-friendly background)

### 2.1 Definition

**NFC-e** stands for *Nota Fiscal do Consumidor eletrônica* — Brazil’s **electronic consumer invoice**. When you buy something at a supermarket, pharmacy, or many other retail outlets, the store may issue an NFC-e instead of (or in addition to) a paper receipt.

- It is a **legal fiscal document** regulated by Brazilian tax authorities (rules vary by state; SEFAZ portals are often used for public consultation).
- For the **personal finance product**, we use NFC-e as a **trusted source of truth** for a purchase: who you paid, when, how much, and optionally **line-by-line items** for price and consumption analytics.

### 2.2 How the shopper usually “gets” the NFC-e

Typical paths:

1. **QR code on the receipt** — scanning opens a URL (often a state treasury or issuer portal) that shows or downloads the note.
2. **URL printed on the receipt** — same idea, typed or pasted by the user.
3. **Email or app from the retailer** — less common in our first flows; primary input today is **URL** (and optionally image upload with QR in the UI).

The **access key** (*chave de acesso*, often called **chNFe** in technical XML) is a **44-digit numeric identifier** unique to that note. It is embedded in QR URLs and is the backbone of deduplication in our system.

### 2.3 What NFC-e is *not* (scope boundary for Invistus)

- We do **not** replace official bookkeeping, SPED, or accountant workflows.
- We do **not** guarantee that every retailer or every state portal will always respond; availability depends on third parties.
- We are **not** a tax filing product; we are a **personal ledger** that can **enrich** expenses using fiscal data when the user chooses to import.

---

## 3. Why NFC-e matters in personal finance

| Benefit | Plain explanation |
|--------|-------------------|
| **Accurate amount and date** | The note reflects what was actually charged, not what the user guessed when typing manually. |
| **Merchant identity** | Issuer name and CNPJ improve categorization, merchant logos, and search. |
| **Item-level insight** | When the portal returns item detail, we can track **product prices over time** and **consumption patterns** (see Product Price Tracking). |
| **Single source for “this purchase”** | One access key = one logical purchase; good deduplication story. |

---

## 4. Data the system can have (conceptual model)

After a successful import (or after manual review resolution), the product holds several layers of information. Think of it as: **import audit** → **ledger transaction** → **fiscal document** → **purchase lines**.

### 4.1 Import job (`document_import_jobs` / “Transaction import”)

**What it is:** An internal record that an import happened (or is pending / needs review / failed).

**Why it exists:**

- Audit trail (“when did we try this?”).
- Status for UI (pending, completed, review required).
- **Idempotency:** same logical file or same NFC-e key should not create infinite duplicate jobs without rules.

**Typical fields (conceptual):** financial account, source (`NFC_E`), file hash (for NFC-e we use **chNFe** as the hash), status, counters (imported / skipped / errors), timestamps, optional review payload for duplicate/manual link flows.

### 4.2 Transaction (ledger row)

**What it is:** The expense (or income, if ever supported) line that appears in the user’s account — amount, date, description, category, etc.

**How NFC-e maps to it:**

- Amount and date/time come from the note.
- Description often reflects **issuer trade name** or similar.
- **`provider_transaction_id`** is set to the **access key** so the same note cannot create two **live** rows for the same account (unique constraint at DB level).
- **`import_job_id`** links the transaction back to the import job for “which import created this?”.

**Soft delete:** Users can delete a transaction; the row remains in the database with `deleted_at` set. That has important implications for **re-import** (see Section 7 and 10).

### 4.3 Purchase document (`purchase_documents` / NFC-e document)

**What it is:** A structured copy of **fiscal header** data from the NFC-e: access key, issuer CNPJ and name, emission datetime, totals, buyer CPF when present, municipality, state, optional tax totals, and a JSON blob for source metadata (e.g. original QR URL).

**Why it exists:**

- Powers **“view my receipt”** style UX (full note summary in the app).
- Anchors **line items** to one transaction.
- Enforces **one document per (account, access_key)** at the database level for active semantics (with cleanup rules when re-importing after deletion).

### 4.4 Purchase items (`purchase_items`)

**What it is:** One row per **line item** on the note (product description, quantity, unit, prices, NCM, EAN when present, etc.), linked to the same transaction and account.

**Why it exists:**

- **Product Price Tracking:** normalization, price history, “how much do I pay for X over time?”.
- Even if the user never opens analytics, items support richer search and future features.

### 4.5 Optional: manual link before import

If the system finds a **manual transaction** that looks like the same purchase (same account, same amount, nearby date), the import may go to **review** so the user can **link** the NFC-e to that row instead of creating a duplicate expense.

---

## 5. How we obtain NFC-e data (technical overview, still readable)

### 5.1 Input

- User provides **`qrCodeUrl`** (and selects target **financial account**).
- Backend validates ownership of the account for the authenticated **account (tenant)** and **user**.

### 5.2 Fetching and parsing

- A **client/router** chooses how to fetch/parse depending on the note (e.g. state-specific HTML vs generic XML response).
- The output is normalized into an internal **`NfceData`** structure: access key, emission instant, totals, issuer address blocks, items list, etc.
- Failures (HTTP errors, invalid XML/HTML, cancelled note) surface as **controlled errors** to the user, not silent corruption.

### 5.3 Authorization status

Where the source exposes fiscal status (e.g. `cStat` in XML), **non-authorized** notes must be rejected with a clear message — we do not book illegal or cancelled documents as normal purchases without explicit product rules.

---

## 6. Product lifecycle: “NFC-e management” (not only the first click)

This section describes the **full lifecycle** from a product perspective.

### 6.1 Capture

- User pastes QR URL or uses upload flow (per UI).
- System validates URL shape where applicable.

### 6.2 Import decision tree (high level)

1. Fetch note → normalized `NfceData`.
2. Look up existing import job by **same financial account + same `chNFe` (file hash)**.
3. If job exists **and** a **live** transaction is still tied to that import → treat as **already imported** (return existing job; no second transaction).
4. If job exists but **no live** transaction (user deleted the expense) → **allow re-import**: refresh job row, clean stale DB artifacts, create a **new** live transaction and fresh document/items.
5. If manual duplicate candidates exist → **`REVIEW_REQUIRED`** path; user resolves (link, keep both, keep existing per UX contract).
6. Otherwise → create pending job, create transaction, persist document + items, complete job.

### 6.3 Post-import experience

- User sees the new transaction in the register.
- User can open **NFC-e / receipt detail** (document + items) where implemented.
- User can categorize, tag, split (if product supports), attach other files, etc., like any other transaction.

### 6.4 Correction and deletion

- User may **delete** the transaction (soft delete).  
  **Requirement:** After deletion, importing the **same** NFC-e again must **not** permanently block with “already imported” if there is no live transaction anymore.

### 6.5 Re-import and idempotency

- **True duplicate:** same NFC-e, transaction still alive → must remain idempotent.
- **Valid re-import:** same NFC-e, transaction deleted → must succeed without unique constraint violations (jobs, documents, provider id).

### 6.6 Manual review and merges

- When the system proposes linking to an existing manual expense, the user’s decision must be persisted consistently (single linked transaction, updated amounts/dates if product rules say so, no orphan duplicate).

---

## 7. Business rules (mandatory)

### 7.1 Logical uniqueness of an NFC-e in one financial account

The same physical note is identified by **`chNFe`**. Within one **financial account**, at most one **live** booking should represent that note unless the product explicitly allows “keep both” in a review flow.

### 7.2 Reuse import job when the transaction is still alive

If an import job exists for the same `chNFe` **and** at least one **live** transaction is linked to that import:

- **Do not** create a parallel import job for the same hash.
- **Return** the existing job (idempotent response).
- UI may show **“This NFC-e has already been imported.”**

### 7.3 Re-import after the user deleted the transaction

If an import job exists for the same `chNFe` but **no live** transaction remains:

- **Do not** treat as final “already imported” solely because the old job row exists.
- **Allow** a fresh import outcome:
  - **Reuse/update** the existing import job row (do not `INSERT` a second job with the same `(financial_account_id, file_hash)`).
  - **Create** a new live transaction.
- **Clean up** stale rows that would violate uniqueness:
  - **`purchase_documents`:** remove document rows for the same `(account_id, access_key)` that point only at **soft-deleted** transactions, before inserting the new document.
  - **`transactions.provider_transaction_id`:** clear the provider id on **soft-deleted** rows that still hold the same key, before inserting the new live row (DB unique index does not ignore deleted rows).

### 7.4 Manual candidate match (review required)

If the engine finds compatible **manual** candidates (account + amount + date window):

- Create / transition job to **`REVIEW_REQUIRED`** with a structured review payload.
- User chooses an outcome (link to existing, keep both, keep existing — per implemented resolver).

### 7.5 Multi-tenant security

Users may only:

- import into **their own** financial accounts,
- list and resolve imports for **their tenant**,
- read NFC-e document/items for transactions they are allowed to see.

Cross-tenant attempts must return **not found** or **forbidden** consistently (no data leakage).

---

## 8. Main flows (acceptance narrative)

### 8.1 Happy path — first-time import

1. User submits a valid NFC-e URL for a selected account.
2. System fetches and validates the note.
3. No blocking duplicate and no mandatory review gate.
4. System creates import job + live transaction + purchase document + purchase items.
5. Job ends in **`COMPLETED`** (or equivalent terminal success).
6. User sees a new expense with correct amount/date/merchant context.

**Acceptance:** exactly **one** new live transaction appears for that import attempt; document and items exist as per schema.

### 8.2 Alternative — second import while transaction still exists

1. User submits the same URL again.
2. System finds existing job **with live** transaction.
3. Same job is returned; **no** second transaction.

**Acceptance:** ledger count unchanged; user understands it was a duplicate attempt.

### 8.3 Edge — re-import after user deleted the transaction

1. User had imported, then deleted the transaction.
2. User submits the same URL again.
3. System must **not** fail on:
   - duplicate import job hash,
   - duplicate `(account_id, access_key)` on `purchase_documents`,
   - duplicate `(financial_account_id, provider_transaction_id)` on `transactions`.
4. User ends with a **new** live transaction for the same note.

**Acceptance:** operation completes successfully; no misleading permanent “already imported” if nothing live remains.

### 8.4 Review path — manual duplicate / link

1. System detects plausible manual match.
2. Job enters **`REVIEW_REQUIRED`**.
3. User resolves; balances and summaries reconcile per implementation.

**Acceptance:** user decision is honored; no silent double booking unless “keep both” was explicitly chosen.

---

## 9. Database uniqueness (why the rules exist)

These constraints protect data integrity but **must** be paired with application logic on re-import:

| Constraint / area | Keys (simplified) | Required behavior on re-import after delete |
|------------------|-------------------|-----------------------------------------------|
| Import jobs | `(financial_account_id, file_hash)` | **Update** existing job for same NFC-e hash; do not insert a second row. |
| Purchase documents | `(account_id, access_key)` | **Delete** stale document rows tied to **deleted** transactions for that access key before inserting the new document. |
| Transactions | `(financial_account_id, provider_transaction_id)` | **NULL** `provider_transaction_id` on **deleted** rows that still hold that access key before inserting the new live transaction. |

---

## 10. Scenario matrix (expected behavior)

| Scenario | Expected behavior |
|----------|-------------------|
| First import of a new NFC-e | Create transaction + document + items; job `COMPLETED` (or review if candidates). |
| Same NFC-e again, live transaction exists | Return existing import; **no** new transaction. |
| Same NFC-e again, user deleted transaction | Re-import allowed; reuse/update job; new live transaction; stale artifacts cleaned. |
| Same NFC-e, manual candidate exists | `REVIEW_REQUIRED` until user resolves. |
| Invalid URL | Validation / user-facing error; no partial writes. |
| Wrong tenant / account | Deny with not found / forbidden; no writes. |
| Upstream portal down / timeout | Clear “try again later” style handling; no corrupt rows. |

---

## 11. UX and messaging

### 11.1 Success

- Clear confirmation that the note was imported and where to find the transaction.
- If linked to manual, confirm link outcome in plain language.

### 11.2 Dedup (“already imported”)

- Show **only** when a **live** transaction still represents that NFC-e.
- **Do not** show spurious “already imported” when the user deleted the expense and reasonably expects to add it again.

### 11.3 Errors

- **Invalid URL:** explain expected patterns (e.g. must be a valid URL; may show examples without exposing PII).
- **Unavailable fiscal portal:** explain temporary unavailability; suggest retry.
- **Internal errors:** safe generic message for the user; technical detail in logs.

---

## 12. Security and privacy

### 12.1 Security

- Authenticated APIs only.
- Financial account ownership checks on every mutating path.
- No cross-tenant reads of import jobs, transactions, or NFC-e payloads.

### 12.2 Privacy

- Fiscal payloads may include **buyer CPF**, full issuer address, and item detail — treat as **sensitive**.
- Display minimal necessary fields in list views; full detail in dedicated surfaces.
- Logs must avoid dumping full fiscal payloads unless strictly necessary and access-controlled.

---

## 13. Observability and operations

### 13.1 Logging (minimum)

- Import attempt: account, job id (when known), anonymized / hashed identifiers where possible for keys.
- Dedup branch taken: reused job vs refreshed job vs new insert path.
- Cleanup counts: documents removed, provider ids cleared.
- Terminal status and counters; stack traces on failures.

### 13.2 Metrics (recommended)

- NFC-e import attempts / successes / failures.
- Rate of `REVIEW_REQUIRED`.
- Rate of re-import after delete.
- Incidence of prevented unique violations (sanity check that cleanup paths run).

---

## 14. Testing requirements

### 14.1 Unit / service

- Dedup when live transaction exists.
- Re-import when transaction deleted (job reuse, cleanup, new transaction).
- Parser/router edge cases where applicable.

### 14.2 E2E (when environment provides a real or stubbed NFC-e URL)

- Happy path creates one live transaction.
- Second import with same URL does not duplicate while transaction lives.
- After delete, second import succeeds again.

### 14.3 Negative

- Invalid URL, wrong account, cross-tenant, upstream failure.

---

## 15. Definition of Done

NFC-e management is **done** for a release when:

1. All rows in the scenario matrix (Section 10) behave as specified.
2. Re-import after delete never fails on uniqueness constraints.
3. “Already imported” only applies when a live transaction still exists.
4. Multi-tenant isolation is verified.
5. Minimum logging is in place; critical paths have automated tests.
6. UX copy matches the messaging rules in Section 11.

---

## 16. Risks and mitigations

| Risk | User impact | Mitigation |
|------|-------------|------------|
| State portal or HTML layout changes | Import fails or partial parse | Monitored parsers, tests, graceful errors, fallback strategies where implemented |
| Legal/tax schema changes | Parser gaps | Versioned parsers, incremental fixes |
| Large item lists | Slower imports | Pagination in UX if needed; efficient bulk insert |
| User confusion on dedup | Support noise | Clear copy; correct “after delete” behavior |

---

## 17. Future enhancements

- Broader state coverage and fetcher strategy (beyond current scope).
- Richer in-app “receipt” viewer and export (PDF, share).
- Stronger offline / retry queues for flaky portals.
- Admin health dashboard for NFC-e import success rates by state/source.

---

## 18. Executive summary (one minute)

**NFC-e** is Brazil’s official electronic consumer receipt; its **44-digit access key** uniquely identifies a purchase. Invistus uses it to create a **reliable ledger transaction**, store a **structured fiscal document**, and optionally **line items** for price analytics. **Management** means: import once correctly, show duplicates clearly when a purchase still exists, let users **delete and re-import** without dead ends, resolve **manual matches** in review, and protect **tenant data** while logging enough for support. Database uniqueness on jobs, documents, and provider ids must be honored with explicit **cleanup and reuse** rules so re-import never breaks.
