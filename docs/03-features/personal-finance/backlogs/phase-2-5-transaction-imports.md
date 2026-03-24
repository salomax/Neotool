---
title: Phase 2.5 — Transaction Imports
type: milestone
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, transactions, import, ofx, nfc-e, csv, ocr, open-finance, idempotency, brazil]
ai_optimized: true
search_keywords: [import, extrato, ofx, nfc-e, nota fiscal, csv, nubank, receipt, ocr, open finance, pluggy, duplicatas, idempotência]
related:
  - 03-features/personal-finance/phase-2-transactions.md
  - 03-features/personal-finance/market-research-2026.md
last_updated: 2026-02-27
---

# Phase 2.5 — Transaction Imports

> **Purpose**: Allow users to populate their transaction history automatically from external sources — bank statement files, NFC-e receipts from SEFAZ, and (later) live Open Finance sync — without requiring manual data entry. A robust idempotency layer ensures importing the same data twice, or importing overlapping data from different sources, never creates duplicate transactions.

---

## Overview

Manual entry is sufficient for conscious spending tracking but creates a high barrier to adoption. Users who already have months of history in their bank want to see a complete picture from day one. Imports solve the cold-start problem and are the primary onboarding path for financially engaged users.

Three independent import channels are prioritized for Phase 2.5, evaluated on cost, Brazilian market coverage, and implementation complexity:

| Method | Source | Cost | Coverage | Phase |
|---|---|---|---|---|
| **OFX import** | Bank statement file | Free | All major Brazilian banks | 2.5 |
| **NFC-e QR code** | SEFAZ receipt scan | Free | All states, retail purchases | 2.5 |
| **CSV Nubank** | Nubank CSV export | Free | Nubank (100M+ users) | 2.5 |
| **Receipt photo (OCR/AI)** | Camera, any receipt | Per-call AI cost | Offline, no QR code | 3 |
| **CSV outros bancos** | Itaú, Bradesco, BB, Santander | Free | Top 4 banks | 3 |
| **Open Finance (Pluggy/Belvo)** | OFB aggregator API | Per-connection cost | All OFB participants | 5 |

---

## Competitive Benchmark

| Competitor | OFX | CSV | OFB/Sync | Receipt | Notes |
|---|---|---|---|---|---|
| Organizze | ✓ | ✓ (Nubank) | — | — | Market leader for OFX/CSV in BR |
| Mobills | ✓ | ✓ | ✓ (Pluggy) | — | Pluggy integration at extra plan tier |
| Minhas Economias | ✓ | ✓ | — | — | Legacy; strong OFX parser |
| Nubank (app) | — | — | Internal | — | Only shows own transactions |
| YNAB | ✓ | ✓ | ✓ (via partners) | — | Best-in-class OFX parser globally |
| Monarch Money | — | ✓ | ✓ | — | Email parsing as differentiator |
| **Invistus** | ✓ | ✓ | ✓ (Phase 5) | ✓ (Phase 3) | **NFC-e is unique differentiator** |

> **Key gap in the market**: No Brazilian PFM extracts itemized data from NFC-e QR codes. Organizze, Mobills, and Minhas Economias all stop at the transaction level. Invistus can show what was bought (Arroz, Feijão, Detergente) — enabling accurate auto-categorization at the item level.

---

## Import Methods

---

### 1. OFX / OFC File Import

**What it is**: Open Financial Exchange — a structured XML-like format supported by all major Brazilian banks for statement export. Banks offer a download via internet banking (usually under "Extrato" → "Exportar").

**Supported banks (confirmed OFX export)**: Itaú, Bradesco, Banco do Brasil, Santander, Caixa, Nubank, Inter, C6 Bank, Sicoob, BTG Pactual, XP, NuConta.

**OFX transaction fields of interest**:

| OFX field | Maps to |
|---|---|
| `<FITID>` | `provider_transaction_id` |
| `<TRNTYPE>` | Informs `type` (DEBIT, CREDIT, CHECK, etc.) |
| `<DTPOSTED>` | `date` (YYYYMMDD) |
| `<TRNAMT>` | `amount` + `credit_debit_type` (sign) |
| `<NAME>` | `description` |
| `<MEMO>` | `description_raw` |

**Pros**
- Covers virtually every Brazilian bank
- Structured data — no OCR, no AI needed
- `<FITID>` is a natural dedup key per account
- Zero recurring cost
- Users familiar with "baixar extrato OFX"

**Cons**
- Manual step: user exports from bank, uploads to app
- Not real-time (snapshot at export time)
- `<FITID>` uniqueness is guaranteed only within the same institution; different banks may generate collisions across accounts
- Some banks generate poor `<FITID>` values (e.g., sequential integers) requiring fallback fingerprinting

---

### 2. NFC-e QR Code (Nota Fiscal de Consumidor Eletrônica)

**What it is**: Every retail purchase in Brazil generates an NFC-e. The QR code on the receipt links to the SEFAZ portal of the emitting state. The portal returns full structured XML with merchant details, itemized products, quantities, unit prices, and total taxes.

**Availability**: All 26 states + DF. Mandatory for retail since ENCAT standardization (~2019). Not applicable to: MEIs without equipment, informal commerce, some service providers.

**NFC-e data of interest**:

| NFC-e field | Maps to |
|---|---|
| `chNFe` (44-digit access key) | `provider_transaction_id` — globally unique, issued by SEFAZ |
| `dhEmi` (emission datetime) | `date` + `transactionDatetime` |
| `vNF` (total value) | `amount` |
| `CNPJ` emitente | `counterparty_document` |
| `xNome` emitente | `counterparty_name` / `description` |
| `xMun` + `UF` | Merchant city/state |
| `det[].xProd` (item name) | Line-item data (future: stored in separate table for AI categorization) |
| `det[].vProd` (item value) | Line-item amounts |
| `det[].NCM` (tax classification) | Informs category (food, electronics, etc.) |

**Pros**
- `chNFe` is globally unique — perfect idempotency key, no collisions possible
- Itemized data enables precise auto-categorization at item level (no other PFM does this in Brazil)
- Free — SEFAZ API is public
- Natural UX: user scans QR at checkout (same gesture as Pix QR)
- Works offline for scanning; network needed only for SEFAZ lookup

**Cons**
- Only covers retail physical purchases — does not capture PIX, TED, boleto, bank fees, subscriptions
- SEFAZ portals can be slow or temporarily unavailable (SLA is best-effort, not guaranteed)
- Some QR codes link to state-specific URLs that require different parsing per state
- Does not replace bank statement import — it's complementary

**Cross-source conflict**: A user may scan an NFC-e at a supermarket (creating `source=NFC_E`) and later import an OFX containing the same purchase. The two transactions have the same amount and date but different `provider_transaction_id` values. See **Idempotency — Cross-Source** section.

---

### 3. CSV Nubank

**What it is**: Nubank allows users to export their credit card or NuConta transaction history as a CSV. Nubank is Brazil's largest bank by customer count (100M+).

**Nubank CSV format (credit card)**:

| Column | Maps to |
|---|---|
| `date` | `date` |
| `transaction_type` | `credit_debit_type` |
| `title` | `description` |
| `amount` | `amount` |
| `category` | `categoryId` (mapped to system categories) |
| `charges` | `totalInstallments` |
| `Identificador` | `provider_transaction_id` — Nubank UUID, stable |

**Pros**
- `Identificador` is a stable UUID — perfect dedup key
- Category field can be pre-mapped to system categories
- Well-documented, stable format
- Nubank CSV includes installments (`charges` field)

**Cons**
- Nubank-only; each other bank needs a separate parser
- Category mapping is lossy (Nubank categories ≠ our taxonomy)
- Nubank CSV for NuConta (bank account) differs from credit card CSV

---

### 4. Receipt Photo — OCR / AI (Phase 3)

**What it is**: User photographs any physical receipt (paper, thermal). AI extracts merchant, amount, date, and items.

**Pros**
- Catches everything NFC-e misses (informal purchases, restaurants without note, international receipts)
- Works anywhere in the world
- AI can auto-categorize from merchant name + items

**Cons**
- Per-call AI cost (vision model inference)
- Accuracy depends on photo quality and receipt legibility
- No unique natural key — must rely on fingerprinting for dedup
- Requires user review before committing

**Dedup**: Hash of `(financial_account_id + date + amount_cents)` stored as `import_fingerprint`. See Idempotency section.

---

### 5. Open Finance via Pluggy / Belvo (Phase 5)

**What it is**: Automated sync via Open Finance Brasil API, using an aggregator (Pluggy, Belvo, Quanto) as the connectivity layer. User grants consent through OFB-standard flow; aggregator pushes transactions in real-time.

**Pros**
- Best UX — fully automatic, no file exports
- Covers all transaction types for all participating institutions
- Schema already aligned: all OFB fields are present as nullable in `banking.transactions`

**Cons**
- Cost per active connection (Pluggy charges ~R$3–8/connection/month at scale)
- Consent expires (max 12 months per OFB spec) — requires renewal UX
- Regulatory: LGPD consent management, data residency requirements
- Requires OFB technical certification to go direct; aggregator is the practical path

**Dedup**: `provider_transaction_id` = OFB `transactionId`. Already in schema.

---

## Idempotency Strategy

Idempotency is the hardest correctness problem in imports. Two distinct scenarios must be handled:

---

### Level 1 — Same-Source Deduplication (automatic, silent)

**Rule**: Importing the same file twice (or importing two OFX files that overlap in dates) must never create duplicate transactions.

**Mechanism**: Every imported transaction carries a `provider_transaction_id` scoped to `source`. Before insert, check:

```sql
SELECT id FROM banking.transactions
 WHERE financial_account_id = :faId
   AND provider_transaction_id  = :providerTxId
   AND source                   = :source
   AND deleted_at IS NULL
```

On conflict: `DO NOTHING` (silent skip). The existing transaction wins. This is safe because:
- OFX `<FITID>` is stable across re-exports for the same transaction
- NFC-e `chNFe` is globally unique and immutable
- Nubank `Identificador` is a stable UUID

**Edge case — poor FITID quality**: Some banks (notably older Bradesco OFX) emit non-unique or sequential FITIDs. In this case, fall back to a **fingerprint**:

```
fingerprint = SHA-256( financial_account_id || date || amount_cents || normalized_description )
```

Stored in `import_fingerprint` column. Uniqueness check on `(financial_account_id, import_fingerprint)` where `source != 'MANUAL'`.

---

### Level 2 — Cross-Source Deduplication (heuristic, user-reviewed)

**The problem**: A user scans an NFC-e receipt at a supermarket (`source=NFC_E`, `provider_transaction_id=chNFe`). Three days later, they import their OFX bank statement. The OFX contains the same purchase (`source=OFX`, `provider_transaction_id=FITID`). The two transactions share: `financial_account_id`, `date`, `amount`.

Since they have **different `provider_transaction_id` values from different sources**, Level 1 does not catch this. Without Level 2, the user ends up with two entries for the same purchase.

**Mechanism**: During import commit, after Level 1 dedup, run a cross-source candidate query:

```sql
SELECT id, source, description, provider_transaction_id
  FROM banking.transactions
 WHERE financial_account_id = :faId
   AND date                 = :date
   AND amount               = :amount
   AND deleted_at           IS NULL
   AND source               != :incomingSource
```

If one or more candidates are found, the incoming transaction is flagged as a **"possible duplicate"** and held in the import preview — not committed automatically.

**User resolution options** (shown in the import review UI):
- **Keep both** — they are actually different transactions (e.g., two same-value purchases on the same day)
- **Use this one, discard the other** — replaces the existing transaction with the richer data from the new source (e.g., NFC-e has itemized data; prefer it over OFX)
- **Skip (keep existing)** — discard the incoming transaction, keep what's already there

**Enrichment merge**: When user chooses "Use this one", the service merges the best fields from both sources:
- From NFC-e: `counterparty_name`, `counterparty_document`, itemized notes, precise `transactionDatetime`
- From OFX: `description_raw` (bank's description), `provider_transaction_id` of the OFX as secondary reference

---

### Level 3 — Import Job Idempotency

**Rule**: If the network fails mid-import, or the user re-uploads the same file, the import job itself should be idempotent.

**Mechanism**: A `banking.transaction_imports` table (see Data Model below) stores a `file_hash` (SHA-256 of the raw uploaded file). Before processing:

```sql
SELECT id, status FROM banking.transaction_imports
 WHERE financial_account_id = :faId
   AND file_hash            = :hash
```

- If `status = COMPLETED`: return the previous result immediately (no reprocessing)
- If `status = PROCESSING`: return "in progress" status
- If `status = FAILED`: allow retry (create new job)

---

## Data Model

### New column on `banking.transactions`

| Column | Type | Notes |
|---|---|---|
| `import_fingerprint` | `VARCHAR(64)` | SHA-256 fingerprint for cross-bank and poor-FITID dedup. Null for MANUAL transactions. |
| `import_job_id` | `UUID FK → banking.transaction_imports` | Which import job created this transaction. Null for MANUAL. |

**New source values** added to the existing CHECK constraint:

```sql
CHECK (source IN ('MANUAL','OPEN_FINANCE','PLUGGY','OFX','CSV_NUBANK','CSV_ITAU','CSV_BRADESCO','NFC_E','RECEIPT_OCR'))
```

---

### New table: `banking.transaction_imports`

Tracks every import job for audit, retry, and idempotency.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID v7 PK | |
| `financial_account_id` | UUID FK → banking.financial_accounts | Which account the import targets |
| `account_id` | UUID FK → security.accounts | Tenant |
| `created_by_user_id` | UUID FK → security.users | Who triggered the import |
| `source` | VARCHAR CHECK IN ('OFX','CSV_NUBANK','CSV_ITAU','NFC_E','RECEIPT_OCR','OPEN_FINANCE','PLUGGY') | Import channel |
| `file_name` | VARCHAR(255) | Original filename (for display) |
| `file_hash` | VARCHAR(64) | SHA-256 of raw file — Level 3 idempotency key |
| `status` | VARCHAR CHECK IN ('PENDING','PROCESSING','COMPLETED','FAILED','REVIEW_REQUIRED') | Job lifecycle |
| `total_rows` | INT | Rows found in source |
| `imported_count` | INT | Transactions successfully committed |
| `skipped_count` | INT | Skipped (Level 1 dedup — silent) |
| `duplicate_review_count` | INT | Flagged for cross-source review (Level 2) |
| `error_count` | INT | Rows that failed to parse |
| `error_details` | JSONB | Structured list of parse errors with row references |
| `review_payload` | JSONB | Held transactions awaiting cross-source resolution |
| `date_from` | DATE | Earliest transaction date in this import |
| `date_to` | DATE | Latest transaction date in this import |
| `completed_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

**Unique constraint**: `UNIQUE(financial_account_id, file_hash)` — Level 3 idempotency.

---

## GraphQL Schema (new additions)

```graphql
enum ImportSource {
  OFX
  CSV_NUBANK
  CSV_ITAU
  CSV_BRADESCO
  NFC_E
  RECEIPT_OCR
  OPEN_FINANCE
  PLUGGY
}

enum ImportStatus {
  PENDING
  PROCESSING
  COMPLETED
  FAILED
  REVIEW_REQUIRED
}

"""
A held transaction from an import awaiting user resolution (cross-source duplicate candidate).
"""
type ImportDuplicateCandidate {
  """The incoming transaction being imported (not yet committed)."""
  incoming: Transaction!
  """The existing transaction already in the ledger."""
  existing: Transaction!
  """Reason for flagging: same date, same amount, same account, different source."""
  reason: String!
}

type TransactionImport {
  id: ID!
  source: ImportSource!
  fileName: String
  status: ImportStatus!
  totalRows: Int
  importedCount: Int
  skippedCount: Int
  duplicateReviewCount: Int
  errorCount: Int
  errorDetails: JSON
  dateFrom: Date
  dateTo: Date
  """Present when status = REVIEW_REQUIRED."""
  duplicateCandidates: [ImportDuplicateCandidate!]
  createdAt: DateTime!
  completedAt: DateTime
}

type ImportResult {
  importJob: TransactionImport!
}

input ResolveImportDuplicateInput {
  importJobId: ID!
  """Transaction ID of the incoming (held) transaction."""
  incomingId: ID!
  """KEEP_BOTH | USE_INCOMING | KEEP_EXISTING"""
  resolution: String!
}

extend type Query {
  """List import jobs for the authenticated account, newest first."""
  transactionImports(financialAccountId: ID): [TransactionImport!]!
  transactionImport(id: ID!): TransactionImport
}

extend type Mutation {
  """
  Upload an OFX or CSV file and trigger async parsing.
  Returns immediately with a PENDING import job.
  Poll transactionImport(id) for status.
  Requires banking:transaction:create permission.
  """
  importTransactionsFile(
    financialAccountId: ID!
    source: ImportSource!
    fileBase64: String!
    fileName: String!
  ): ImportResult!

  """
  Scan an NFC-e QR code. Fetches data from SEFAZ and creates one transaction.
  Returns immediately (SEFAZ lookup is synchronous; timeout = 5s).
  Requires banking:transaction:create permission.
  """
  importNfce(
    financialAccountId: ID!
    """Full QR code URL from the NFC-e receipt."""
    qrCodeUrl: String!
  ): ImportResult!

  """
  Resolve a cross-source duplicate candidate flagged during import review.
  Requires banking:transaction:create permission.
  """
  resolveImportDuplicate(input: ResolveImportDuplicateInput!): TransactionImport!
}
```

---

## Import Lifecycle

```
User uploads file
       │
       ▼
Parse file header → determine source type (OFX / CSV variant)
       │
       ▼
Create transaction_imports row (status=PENDING, file_hash stored)
       │
       ├─ file_hash already exists + status=COMPLETED → return previous job (Level 3 idempotency)
       │
       ▼
Publish ImportFileRequested event to Kafka topic: banking.import.file-requested
Return ImportResult { importJob { id, status=PENDING } } immediately
       │
       ▼ (Kafka consumer — async)
Consumer picks up event, updates status=PROCESSING
       │
       ▼
For each row in file:
  ├─ Parse → extract (date, amount, description, provider_transaction_id)
  ├─ Level 1 check: provider_transaction_id + source already in DB? → SKIP (skipped_count++)
  ├─ Level 1 fallback: import_fingerprint already in DB? → SKIP
  ├─ Level 2 check: same date + amount + account, different source? → HOLD for review
  └─ No conflict → INSERT (imported_count++)
       │
       ▼
All rows processed:
  ├─ duplicate_review_count > 0 → status=REVIEW_REQUIRED
  └─ duplicate_review_count = 0 → status=COMPLETED

User reviews duplicates (if any):
  KEEP_BOTH      → commit both transactions
  USE_INCOMING   → soft-delete existing, commit incoming (with enrichment merge)
  KEEP_EXISTING  → discard incoming

All duplicates resolved → status=COMPLETED
```

---

## Async Processing Architecture

### Kafka Topics

| Topic | Producer | Consumer | Payload |
|---|---|---|---|
| `banking.import.file-requested` | API (on mutation) | `ImportFileConsumer` | `{ jobId, assetId, source, financialAccountId, accountId }` |
| `banking.import.job-completed` | `ImportFileConsumer` | (future: notifications service) | `{ jobId, status, importedCount, skippedCount }` |

**File storage flow**:
1. API receives `fileBase64` → decodes → stores via `ImportFileStoragePort` (backed by the `assets` service) → receives `assetId: UUID`
2. Kafka message carries only the `assetId` (not the file bytes)
3. Consumer fetches file bytes via `fileStorage.read(assetId)`, processes, then calls `fileStorage.delete(assetId)` (best-effort; assets service TTL handles cleanup on failure)

**Error recovery**: If the consumer crashes mid-processing, the Kafka consumer group offset allows the message to be reprocessed. Level 3 idempotency (`file_hash`) prevents double-counting — re-processing a job that already reached `COMPLETED` is a no-op.

---

## Browser Notification Strategy

After triggering an async file import, the browser must know when processing finishes so it can refresh the transaction list. **Server-Sent Events (SSE)** is the chosen approach from day 1 — real-time, unidirectional, and well-suited for a single user waiting for a job they initiated.

### Architecture Overview

```
Browser
  │ GET /api/imports/{jobId}/stream  (Next.js Route Handler)
  │ Authorization handled by proxy
  ▼
Next.js Route Handler
  │ reads token from localStorage via request context
  │ GET /imports/{jobId}/events  +  Authorization: Bearer {token}
  ▼
Micronaut SSE Endpoint
  │ subscribes to ImportEventBus (in-process Kotlin Sinks.Many)
  │ filters by jobId + accountId
  ▼
ImportEventBus
  ▲ emits on each status change
  │
ImportFileConsumer (Kafka consumer)
```

### Why a Next.js Proxy (not direct SSE to Micronaut)

`EventSource` is a native browser API but **does not support custom headers** — it cannot send `Authorization: Bearer ...`. Since the auth token is stored in `localStorage` (not in httpOnly cookies), the browser cannot attach it to a raw `EventSource` connection.

The solution is a **Next.js Route Handler** acting as an authenticated proxy:
- Browser calls `/api/imports/{jobId}/stream` — a same-origin request where the frontend can attach the token via a cookie or request header that the Route Handler reads
- The Route Handler forwards the request to Micronaut with `Authorization: Bearer {token}` and pipes the SSE stream back to the browser using `ReadableStream`

This pattern aligns with how Next.js already proxies GraphQL requests and avoids exposing the JWT in the URL.

### Backend: Micronaut SSE Endpoint

```kotlin
// In-process event bus — singleton, shared between Kafka consumer and SSE endpoint
@Singleton
class ImportEventBus {
    private val sink: Sinks.Many<ImportJobStatusPayload> =
        Sinks.many().multicast().onBackpressureBuffer()

    fun emit(event: ImportJobStatusPayload) { sink.tryEmitNext(event) }
    fun asFlux(): Flux<ImportJobStatusPayload> = sink.asFlux()
}

// SSE endpoint
@Get("/imports/{jobId}/events", produces = [MediaType.TEXT_EVENT_STREAM])
fun importJobEvents(
    @PathVariable jobId: UUID,
    principal: RequestPrincipal,
): Flux<ServerSentEvent<ImportJobStatusPayload>> {
    val accountId = requireNotNull(principal.currentAccountId)
    // Guard: if job is already terminal, emit one synthetic event and close
    val current = importJobRepository.findById(jobId, accountId)
        ?: return Flux.error(NoSuchElementException("Import job $jobId not found"))
    if (current.status.isTerminal()) {
        return Flux.just(ServerSentEvent.of(ImportJobStatusPayload.from(current)))
    }
    return importEventBus.asFlux()
        .filter { it.jobId == jobId && it.accountId == accountId }
        .map { ServerSentEvent.of(it) }
        .takeUntil { it.status.isTerminal() }
        .timeout(Duration.ofMinutes(10)) // protect against zombie connections
}
```

**Key design points**:
- **Connection-at-completion guard**: if the browser connects after the job is already `COMPLETED`, the endpoint reads the DB and emits a synthetic terminal event immediately instead of waiting forever
- **In-process event bus**: `Sinks.Many<T>` is Reactor's multicast primitive — the Kafka consumer calls `importEventBus.emit(...)` on each status transition; the SSE endpoint subscribes to the resulting `Flux`
- **Timeout**: 10-minute hard limit prevents zombie SSE connections if a job gets stuck

### Frontend: Next.js Proxy + `useImportJobStatus` Hook

```typescript
// app/api/imports/[jobId]/stream/route.ts  — Next.js Route Handler
export async function GET(req: Request, { params }: { params: { jobId: string } }) {
  const token = req.headers.get('x-auth-token') // sent by hook
  const upstream = await fetch(
    `${process.env.BANKING_SERVICE_URL}/imports/${params.jobId}/events`,
    { headers: { Authorization: `Bearer ${token}` } }
  )
  // Pipe SSE stream from Micronaut back to browser
  return new Response(upstream.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
  })
}
```

```typescript
// hooks/useImportJobStatus.ts
export function useImportJobStatus(
  jobId: string | null,
  onComplete: () => void,
  onReviewRequired: () => void,
) {
  useEffect(() => {
    if (!jobId) return
    const token = getAuthToken()
    // EventSource doesn't support custom headers — use fetch + ReadableStream instead
    const controller = new AbortController()
    fetch(`/api/imports/${jobId}/stream`, {
      headers: { 'x-auth-token': token ?? '' },
      signal: controller.signal,
    }).then(async (res) => {
      const reader = res.body!.getReader()
      const decoder = new TextDecoder()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        const text = decoder.decode(value)
        // Parse SSE "data: {...}" lines
        for (const line of text.split('\n')) {
          if (!line.startsWith('data:')) continue
          const payload = JSON.parse(line.slice(5).trim()) as ImportJobStatusPayload
          if (payload.status === 'COMPLETED') { onComplete(); break }
          if (payload.status === 'REVIEW_REQUIRED') { onReviewRequired(); break }
          if (payload.status === 'FAILED') break
        }
      }
    }).catch(() => {
      // Graceful degradation: fall back to one-time query on connection error
    })
    return () => controller.abort()
  }, [jobId])
}
```

### Graceful Degradation

If the SSE connection fails (network error, browser compatibility, load balancer timeout), the hook's `catch` block triggers a **single fallback query** `transactionImport(id)` to get the current status. This avoids an infinite polling loop while still recovering from transient failures.

| Strategy | Role | When |
|---|---|---|
| **SSE via Next.js proxy** | Primary — real-time status push | Always |
| **Single fallback query** | Degradation — on SSE connection error | Connection failure only |
| GraphQL Subscriptions | Not used | Not recommended for federated supergraph |

---

## Permissions

No new permissions required. Import operations reuse:

| Action | Permission |
|---|---|
| Upload file / scan NFC-e | `banking:transaction:create` |
| Resolve duplicate | `banking:transaction:create` |
| View import jobs | `banking:transaction:view` |

---

## Error Handling

| Scenario | Behavior |
|---|---|
| Malformed OFX / CSV | `status=FAILED`; `error_details` contains row numbers and parse errors |
| SEFAZ timeout on NFC-e | Return error; user can retry |
| NFC-e chNFe already imported | Silent skip (Level 1); inform user ("Nota já importada") |
| OFX `<FITID>` collision (same value, different transaction) | Fingerprint fallback; if still collision → flag for review |
| File too large (> 10 MB) | Reject before parsing |
| Import for wrong account (tenant mismatch) | 403 — financial account does not belong to authenticated account |
| SEFAZ returns invalid/cancelled NFC-e | Return error; do not create transaction |

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Sync or async import? | **Async** for file uploads; **sync** for NFC-e | Files can have thousands of rows; NFC-e is a single SEFAZ HTTP call |
| What is the cross-source dedup unit? | `(financial_account_id, date, amount)` | Only safe heuristic without a global transaction registry |
| Auto-merge or user review for cross-source? | **User review** — never auto-discard | False positives (same amount, same day, different purchase) are real and would cause data loss |
| Which source wins in enrichment merge? | NFC-e > OFX > CSV for merchant data; OFX > NFC-e for bank reference fields | NFC-e has itemized + CNPJ; OFX has bank's own FITID as permanent reference |
| File hash for import idempotency? | SHA-256 of raw bytes | Stable, fast, collision-resistant |
| Re-import after FAILED status? | **Allowed** — new job created | Failed jobs may be due to transient errors; must be retryable |
| Store raw uploaded file? | **No** — only the hash and parsed results | Avoids PII storage of bank statements; hash is sufficient for idempotency |
| Async queue mechanism? | **Kafka** — already configured in the platform | Enables reprocessing via offset replay, dead-letter queues, fan-out to future consumers (notifications, ML categorization). PostgreSQL polling discarded. |
| Browser notification strategy? | **SSE from day 1** via Next.js proxy | `EventSource` cannot send `Authorization` headers (token in localStorage, not cookies). Next.js Route Handler proxies the connection, injecting the token. Kotlin `Sinks.Many` bridges Kafka consumer → SSE endpoint. GraphQL Subscriptions not recommended in federated supergraph. |
| NFC-e item data storage? | **Notes field (Phase 2.5)**, dedicated `transaction_line_items` table in Phase 3 | Items enable AI categorization; dedicate table avoids premature schema complexity |
