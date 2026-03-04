---
title: Phase 2.5 — Transaction Imports Backlog
type: backlog
category: personal-finance
status: planned
version: 1.0.0
tags: [backlog, personal-finance, transactions, import, ofx, nfc-e, csv, kafka, sse, idempotency]
related:
  - 03-features/personal-finance/phase-2-5-transaction-imports.md
last_updated: 2026-02-27
---

# Phase 2.5 — Transaction Imports: Detailed Backlog

> Stories ordered by dependency. Each story is independently deliverable within its sprint group.
> All stories assume the Phase 2 backend (transactions domain, service, GraphQL) is complete and deployed.

---

## Dependency Graph

```
Sprint 1: Foundation & Data Model
  IMPORT-01 (DB migrations)
  IMPORT-02 (Domain model)
  IMPORT-03 (JPA entity + mapper)
  IMPORT-04 (Repository)

Sprint 2: File Parsers (parallel — no inter-dependencies)
  IMPORT-05 (OFX parser)         ← depends on IMPORT-02
  IMPORT-06 (CSV Nubank parser)  ← depends on IMPORT-02
  IMPORT-07 (NFC-e SEFAZ client) ← depends on IMPORT-02
  IMPORT-08 (Source detector)    ← depends on IMPORT-05, IMPORT-06

Sprint 3: Idempotency Engine
  IMPORT-09 (Level 1 — same-source dedup)    ← depends on IMPORT-04
  IMPORT-10 (Level 1b — fingerprint fallback) ← depends on IMPORT-09
  IMPORT-11 (Level 2 — cross-source detection) ← depends on IMPORT-09
  IMPORT-12 (Import Processor — core logic)   ← depends on IMPORT-09, IMPORT-10, IMPORT-11

Sprint 4: Async Pipeline
  IMPORT-13a (assets namespace config) ← no code dependency — assets service change
  IMPORT-13b (file storage port impl)  ← depends on IMPORT-13a
  IMPORT-14 (Kafka topics — infra)     ← no code dependency
  IMPORT-15 (Kafka producer)           ← depends on IMPORT-14
  IMPORT-16 (ImportEventBus)           ← no code dependency
  IMPORT-17 (ImportFileConsumer)       ← depends on IMPORT-12, IMPORT-13b, IMPORT-14, IMPORT-16
  IMPORT-18 (ImportJobService — orchestration) ← depends on IMPORT-04, IMPORT-13b, IMPORT-15

Sprint 5: SSE + GraphQL
  IMPORT-19 (SSE endpoint — Micronaut)  ← depends on IMPORT-16, IMPORT-18
  IMPORT-20 (GraphQL schema)            ← depends on IMPORT-18
  IMPORT-21 (ImportResolver)            ← depends on IMPORT-18, IMPORT-19, IMPORT-20
  IMPORT-22 (BankingWiringFactory)      ← depends on IMPORT-21

Sprint 6: Frontend
  IMPORT-23 (Next.js SSE proxy)         ← depends on IMPORT-19
  IMPORT-24 (useImportJobStatus hook)   ← depends on IMPORT-23
  IMPORT-25 (GraphQL operations codegen) ← depends on IMPORT-20
  IMPORT-26 (Import file upload UI)     ← depends on IMPORT-24, IMPORT-25
  IMPORT-27 (NFC-e QR scanner UI)       ← depends on IMPORT-25
  IMPORT-28 (Duplicate review UI)       ← depends on IMPORT-26
  IMPORT-29 (Bruno API collection)      ← depends on IMPORT-21
```

---

## Sprint 1 — Foundation & Data Model

---

### IMPORT-01 — DB Migrations: alter transactions + create transaction_imports

**Depends on**: _(none — first story)_

**Description**:
Create two Flyway migrations in `service/kotlin/banking/src/main/resources/db/migration/`:

`V0_7__add_import_fields_to_transactions.sql`:
- Add `import_fingerprint TEXT NULL` to `banking.transactions`
- Add `import_job_id UUID NULL` (FK added in V0_8, after table exists)
- Extend the `source` CHECK constraint to include new values:
  `OFX`, `CSV_NUBANK`, `CSV_ITAU`, `CSV_BRADESCO`, `NFC_E`, `RECEIPT_OCR`
  (existing values: `MANUAL`, `OPEN_FINANCE`, `PLUGGY`)
- Add index: `CREATE INDEX idx_transactions_import_fingerprint ON banking.transactions(financial_account_id, import_fingerprint) WHERE import_fingerprint IS NOT NULL`
- Add index: `CREATE INDEX idx_transactions_provider_tx_id ON banking.transactions(financial_account_id, provider_transaction_id, source) WHERE provider_transaction_id IS NOT NULL`

`V0_8__create_transaction_imports.sql`:
- Create `banking.transaction_imports` table (full schema per spec)
- Unique constraint: `UNIQUE(financial_account_id, file_hash)`
- Add FK: `ALTER TABLE banking.transactions ADD CONSTRAINT fk_transactions_import_job FOREIGN KEY (import_job_id) REFERENCES banking.transaction_imports(id)`
- Indexes: `(account_id, created_at DESC)`, `(financial_account_id, status)`

**Acceptance Criteria**:
- Both migrations apply cleanly via Flyway on a fresh PostgreSQL database
- Rollback is documented (Flyway Enterprise) or migration is forward-only with note
- No data loss to existing `banking.transactions` rows (all new columns are nullable)
- The extended `source` CHECK constraint preserves existing MANUAL/OPEN_FINANCE/PLUGGY rows

**Tests**:
- `TransactionMigrationIT` (existing): verify V0_7 and V0_8 apply without error
- `TransactionImportMigrationIT`: insert a row into `banking.transaction_imports`, insert a `banking.transactions` row with `import_job_id` FK, verify FK constraint works
- Negative: attempt insert with invalid `source` value → verify CHECK constraint rejects it

---

### IMPORT-02 — Domain Model: ImportJob, Enums, Commands

**Depends on**: IMPORT-01

**Description**:
Create in `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/`:

- `ImportSource.kt` — enum: `OFX`, `CSV_NUBANK`, `CSV_ITAU`, `CSV_BRADESCO`, `NFC_E`, `RECEIPT_OCR`, `OPEN_FINANCE`, `PLUGGY`
- `ImportJobStatus.kt` — enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `REVIEW_REQUIRED`; add `fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, REVIEW_REQUIRED)`
- `ParsedTransaction.kt` — data class representing a row extracted by any parser; fields: `providerTransactionId: String?`, `creditDebitType`, `amount: BigDecimal`, `date: LocalDate`, `transactionDatetime: Instant?`, `description: String`, `descriptionRaw: String?`, `counterpartyName: String?`, `counterpartyDocument: String?`, `currencyCode: String`
- `ImportDuplicateCandidate.kt` — data class: `incoming: ParsedTransaction`, `existing: Transaction`, `reason: String`
- `ImportJob.kt` — data class: all columns of `banking.transaction_imports` as domain fields
- `ImportJobRepository.kt` — interface:
  ```kotlin
  fun save(job: ImportJob): ImportJob
  fun update(job: ImportJob): ImportJob
  fun findById(id: UUID, accountId: UUID): ImportJob?
  fun findByFileHash(financialAccountId: UUID, fileHash: String): ImportJob?
  fun findByFinancialAccount(financialAccountId: UUID, accountId: UUID): List<ImportJob>
  fun findByAccount(accountId: UUID): List<ImportJob>
  ```
- `CreateImportJobCommand.kt` — data class for creating a new job

**Acceptance Criteria**:
- All enums have the correct values and `isTerminal()` returns true only for COMPLETED, FAILED, REVIEW_REQUIRED
- `ParsedTransaction` has no dependency on JPA or Micronaut
- `ImportJobRepository` is a pure interface (no annotations)

**Tests**:
- `ImportJobStatusTest`: unit test `isTerminal()` for all enum values
- `ParsedTransactionTest`: verify construction and immutability

---

### IMPORT-03 — JPA Entity + Mapper

**Depends on**: IMPORT-02

**Description**:
Create in `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/importjob/`:

- `TransactionImportEntity.kt` — `@MappedEntity("banking.transaction_imports")` with all columns including `errorDetails: String? @Column("error_details")`, `reviewPayload: String? @Column("review_payload")` (JSONB stored as String, serialized with Jackson)
- `TransactionImportMapper.kt` — object with:
  - `toDomain(entity: TransactionImportEntity): ImportJob`
  - `toEntity(job: ImportJob): TransactionImportEntity`
  - `updateEntity(entity: TransactionImportEntity, job: ImportJob): Unit`
  - JSONB fields serialized/deserialized with `ObjectMapper`

**Acceptance Criteria**:
- Round-trip test: domain → entity → domain produces equal object
- Null JSONB fields map correctly (null ↔ null)
- `errorDetails` and `reviewPayload` serialize/deserialize as valid JSON

**Tests**:
- `TransactionImportMapperTest`:
  - `givenFullImportJob_whenRoundTrip_thenEqual()`
  - `givenNullJsonbFields_whenRoundTrip_thenNull()`
  - `givenErrorDetails_whenSerialized_thenValidJson()`

---

### IMPORT-04 — Repository Implementation

**Depends on**: IMPORT-03

**Description**:
Create in `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/`:

- `TransactionImportEntityRepository.kt` — Micronaut Data `@JdbcRepository`:
  ```kotlin
  fun findByIdAndAccountId(id: UUID, accountId: UUID): TransactionImportEntity?
  fun findByFinancialAccountIdAndFileHash(financialAccountId: UUID, fileHash: String): TransactionImportEntity?
  fun findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(financialAccountId: UUID, accountId: UUID): List<TransactionImportEntity>
  fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<TransactionImportEntity>
  @Query("UPDATE banking.transaction_imports SET status = :status, updated_at = NOW() WHERE id = :id")
  fun updateStatus(id: UUID, status: String): Int
  ```

- `DefaultImportJobRepository.kt` — `@Singleton` implementing `ImportJobRepository`; uses `TransactionImportMapper` for all conversions; `@Transactional` on `save` and `update`

**Acceptance Criteria**:
- `findByFinancialAccountIdAndFileHash` returns the existing job when file_hash matches (Level 3 idempotency foundation)
- `save` and `update` use transactions
- All queries scope by `account_id` (tenant isolation)

**Tests**:
- `TransactionImportRepositoryIT` (Testcontainers Postgres):
  - `givenNewJob_whenSave_thenPersisted()`
  - `givenExistingJob_whenFindByFileHash_thenFound()`
  - `givenDifferentAccount_whenFindByFileHash_thenNotFound()` (tenant isolation)
  - `givenExistingJob_whenUpdateStatus_thenReflected()`

---

## Sprint 2 — File Parsers

---

### IMPORT-05 — OFX Parser

**Depends on**: IMPORT-02

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import_/parser/OFXParser.kt`:

- `OFXParser` — parses OFX 1.x (SGML) and OFX 2.x (XML) file bytes
- Detects format via presence of `OFXHEADER:` (SGML) vs `<?OFX` / `<?xml` (XML)
- For SGML: use a regex/custom parser to extract `<STMTTRN>` blocks
- For XML: use a standard XML parser (javax.xml)
- Extracts per-transaction: `FITID` → `providerTransactionId`, `TRNTYPE`, `DTPOSTED` → `date`, `TRNAMT` → `amount` + `creditDebitType`, `NAME` → `description`, `MEMO` → `descriptionRaw`, `CURDEF` → `currencyCode`
- Maps `TRNTYPE` to `TransactionType` (DEBIT → EXPENSE, CREDIT → INCOME, etc.)
- Returns `List<ParsedTransaction>`
- Add library dependency: `com.webcohesion.ofx4j` or implement a lightweight parser (OFX SGML is not valid XML — check if ofx4j handles both)

**Acceptance Criteria**:
- Parses Itaú OFX SGML fixture without error
- Parses Nubank OFX XML fixture without error
- `FITID` is correctly mapped to `providerTransactionId`
- Amount sign correctly maps to `creditDebitType` (positive = CREDIT, negative = DEBIT)
- Date in YYYYMMDD format parses correctly to `LocalDate`
- Malformed file throws `OFXParseException` with descriptive message

**Tests**:
- `OFXParserTest`:
- Fixture files in `src/test/resources/fixtures/ofx/` (anonymized, realistic)

---

### IMPORT-06 — CSV Nubank Parser

**Depends on**: IMPORT-02

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import_/parser/CsvNubankParser.kt`:

- Parses Nubank credit card CSV (UTF-8, comma-delimited)
- Expected header: `date,transaction_type,title,amount,category,charges,Identificador`
- Maps `Identificador` (UUID string) → `providerTransactionId`
- Maps `date` (YYYY-MM-DD) → `date`
- Maps `amount` (positive float) → `amount`; `transaction_type` maps to `creditDebitType` (`purchase`/`debit` → DEBIT, `payment`/`credit` → CREDIT)
- Maps `title` → `description`
- Maps `charges` → `totalInstallments` (nullable int)
- Skips header row; skips rows with blank `Identificador`
- Returns `List<ParsedTransaction>`

**Acceptance Criteria**:
- Parses a 100-row Nubank CSV fixture with correct field mapping
- Rows with missing `Identificador` are skipped (logged as warning)
- Missing optional columns do not cause exception
- File with Windows line endings (CRLF) parses correctly

**Tests**:
- `CsvNubankParserTest`:
- Fixture: `src/test/resources/fixtures/csv/nubank_sample.csv`

---

### IMPORT-07 — NFC-e SEFAZ Client

**Depends on**: IMPORT-02

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/nfce/`:

- `NfceClient.kt` — interface: `fun fetch(qrCodeUrl: String): NfceData`
- `NfceData.kt` — data class: `chNFe`, `dhEmi: Instant`, `vNF: BigDecimal`, `cnpjEmitente: String`, `nomeEmitente: String`, `municipio: String`, `uf: String`, `items: List<NfceItem>`
- `NfceItem.kt` — data class: `xProd: String`, `vProd: BigDecimal`, `qCom: BigDecimal`, `uCom: String`, `ncm: String`
- `DefaultNfceClient.kt` — Micronaut HTTP client; timeout 5s; fetches URL → parses XML response
  - Extracts `chNFe` from URL path (44-digit key, format varies per state — use regex `\d{44}`)
  - OR extracts from XML `<chNFe>` tag if present
  - Handles HTTP 200 with NF-e XML response
  - Throws `NfceUnavailableException` on timeout or non-200 response
  - Throws `NfceInvalidException` if XML is malformed or NF-e is cancelled

**Acceptance Criteria**:
- `chNFe` extraction works for SP (sefaz.fazenda.sp.gov.br), MG, RJ format URLs
- Timeout of 5 seconds is enforced
- Items list is populated correctly (xProd, vProd, qCom)
- Cancelled NF-e (`<cStat>` ≠ 100) throws `NfceInvalidException`

**Tests**:
- `NfceClientTest` (unit — mocked HTTP):
  - `givenValidSefazResponse_whenFetch_thenNfceDataReturned()`
  - `givenTimeoutResponse_whenFetch_thenNfceUnavailableException()`
  - `givenCancelledNfce_whenFetch_thenNfceInvalidException()`
  - `givenMalformedXml_whenFetch_thenNfceInvalidException()`
- Fixture XMLs in `src/test/resources/fixtures/nfce/` (real XML structure, anonymized CNPJ)
- Integration test `@Disabled` (requires live SEFAZ): `NfceClientIntegrationTest`

---

### IMPORT-08 — Import Source Detector

**Depends on**: IMPORT-05, IMPORT-06

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/ImportSourceDetector.kt`:

- `fun detect(fileBytes: ByteArray, fileName: String?): ImportSource`
- Detection logic (order matters):
  1. If `OFXHEADER:` or `<OFX` present in first 256 bytes → `ImportSource.OFX`
  2. If first line matches Nubank CSV header exactly → `ImportSource.CSV_NUBANK`
  3. Otherwise → throw `UnsupportedImportSourceException("Cannot determine import source for file: $fileName")`
- Case-insensitive matching

**Acceptance Criteria**:
- OFX SGML and XML both detected as `OFX`
- Nubank CSV header detected as `CSV_NUBANK`
- Random binary file → `UnsupportedImportSourceException`

**Tests**:
- `ImportSourceDetectorTest`:
  - `givenOfxSgml_whenDetect_thenOFX()`
  - `givenOfxXml_whenDetect_thenOFX()`
  - `givenNubankCsv_whenDetect_thenCsvNubank()`
  - `givenUnknownFile_whenDetect_thenException()`

---

## Sprint 3 — Idempotency Engine

---

### IMPORT-09 — Level 1: Same-Source Deduplication

**Depends on**: IMPORT-04

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/SameSourceDedupService.kt`:

Add to `TransactionEntityRepository`:
```kotlin
fun existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
    financialAccountId: UUID,
    providerTransactionId: String,
    source: String,
): Boolean
```

Implement `SameSourceDedupService`:
- `fun isDuplicate(financialAccountId: UUID, providerTransactionId: String, source: TransactionSource): Boolean`
- Queries the repository; returns `true` if exists

**Acceptance Criteria**:
- Returns `true` when exact match exists in DB
- Returns `false` when no match
- Scoped to `financial_account_id` (no cross-account leak)
- Deleted transactions (`deleted_at IS NOT NULL`) are not considered duplicates

**Tests**:
- `SameSourceDedupServiceTest` (unit — mock repo):
- `SameSourceDedupServiceIT` (Testcontainers): verifies the actual SQL query behavior

---

### IMPORT-10 — Level 1b: Fingerprint Fallback

**Depends on**: IMPORT-09

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/ImportFingerprintService.kt`:

- `fun compute(financialAccountId: UUID, date: LocalDate, amountCents: Long, normalizedDescription: String): String`
  - Concatenates: `"$financialAccountId|$date|$amountCents|${normalizedDescription.trim().lowercase()}"`
  - Returns SHA-256 hex digest (64 chars)
- `fun isDuplicate(financialAccountId: UUID, fingerprint: String): Boolean`
  - Queries `import_fingerprint` column: `WHERE financial_account_id = :faId AND import_fingerprint = :fp AND deleted_at IS NULL`

Add to `TransactionEntityRepository`:
```kotlin
fun existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
    financialAccountId: UUID,
    importFingerprint: String,
): Boolean
```

**Acceptance Criteria**:
- Same inputs always produce the same fingerprint (deterministic)
- Different amounts produce different fingerprints
- Fingerprint is exactly 64 hex characters
- Description normalization: trim + lowercase applied before hashing

**Tests**:
- `ImportFingerprintServiceTest`:

---

### IMPORT-11 — Level 2: Cross-Source Duplicate Candidate Detection

**Depends on**: IMPORT-09

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/dedup/CrossSourceDedupService.kt`:

Add to `TransactionEntityRepository`:
```kotlin
@Query("""
    SELECT * FROM banking.transactions
    WHERE financial_account_id = :financialAccountId
      AND date = :date
      AND amount = :amount
      AND source != :incomingSource
      AND deleted_at IS NULL
""")
fun findCrossSourceCandidates(
    financialAccountId: UUID,
    date: LocalDate,
    amount: BigDecimal,
    incomingSource: String,
): List<TransactionEntity>
```

Implement `CrossSourceDedupService`:
- `fun findCandidates(financialAccountId: UUID, parsed: ParsedTransaction, incomingSource: TransactionSource): List<Transaction>`

**Acceptance Criteria**:
- Returns matches only when `(date, amount, financial_account_id)` match AND source differs
- Does NOT return matches from the same source (those are Level 1)
- Does NOT return soft-deleted transactions
- Empty list when no cross-source conflict

**Tests**:
- `CrossSourceDedupServiceTest` (unit — mock repo):
- `CrossSourceDedupServiceIT` (Testcontainers): verifies SQL query correctness

---

### IMPORT-12 — Import Processor: Core Logic

**Depends on**: IMPORT-09, IMPORT-10, IMPORT-11

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/ImportProcessor.kt`:

`@Singleton` that processes a list of `ParsedTransaction` items for a given `ImportJob`.

```kotlin
data class ImportProcessingResult(
    val imported: List<Transaction>,
    val skipped: Int,           // Level 1 duplicates
    val duplicateCandidates: List<ImportDuplicateCandidate>,  // Level 2 flagged
    val errors: List<ImportRowError>,  // parse/validation failures
)

data class ImportRowError(
    val rowIndex: Int,
    val reason: String,
)
```

Processing logic per `ParsedTransaction`:
1. If `providerTransactionId != null`: run Level 1 check → skip if duplicate
2. Always compute fingerprint; run Level 1b check → skip if fingerprint duplicate
3. Run Level 2 cross-source check → if candidates found, add to `duplicateCandidates`, do NOT insert
4. If no conflict: create `Transaction` via `transactionRepository.save(...)` with `source = importSource`, `importJobId = job.id`, `importFingerprint = computed`
5. Catch per-row exceptions → add to `errors`, continue

The processor does NOT manage job status updates or Kafka events — that is the responsibility of `ImportJobService`.

**Acceptance Criteria**:
- A 100-row OFX with 10 Level-1 duplicates, 3 Level-2 candidates, and 2 malformed rows produces: imported=85, skipped=10, duplicateCandidates=3.size, errors=2
- Level-2 candidates are NOT inserted into the transactions table
- Each successfully imported transaction has `import_job_id` and `import_fingerprint` set
- An exception in one row does not abort processing of subsequent rows

**Tests**:
- `ImportProcessorTest` (unit — all repos mocked):
  - `givenNoDuplicates_whenProcess_thenAllImported()`
  - `givenLevel1Duplicate_whenProcess_thenSkipped()`
  - `givenFingerprintDuplicate_whenProcess_thenSkipped()`
  - `givenLevel2Candidate_whenProcess_thenHeld_notInserted()`
  - `givenMalformedRow_whenProcess_thenErrorRecorded_processingContinues()`
  - `givenMixedScenario_whenProcess_thenCountsCorrect()`
- `ImportProcessorIT` (Testcontainers): end-to-end with real DB

---

## Sprint 4 — Async Pipeline

---

### IMPORT-13a — Assets Service: Add `imports` Namespace

**Service**: `service/kotlin/assets/`

**Depends on**: _(none — assets service change)_

**Description**:
The banking service delegates file storage to the existing `assets` service rather than connecting to MinIO/S3 directly. This story prepares the assets service to accept import files.

**Changes to `service/kotlin/assets/src/main/resources/asset-config.yml`**:
Add a new namespace `imports` configuration:
```yaml
imports:
  visibility: PRIVATE
  max-size-bytes: 10485760  # 10 MB
  allowed-mime-types:
    - application/x-ofx
    - text/x-ofx
    - text/csv
    - application/csv
    - application/octet-stream  # fallback for browsers that don't detect OFX MIME
  storage-key-template: "imports/{ownerId}/{assetId}"
  upload-ttl-seconds: 900    # 15 min presigned PUT URL
  download-ttl-seconds: 3600 # 1 hour presigned GET URL — sufficient for consumer processing
```

The `ownerId` for service-to-service uploads will be the `accountId` (tenant), not a userId — document this convention.

**No new code** is required in the assets service itself. The existing `createAssetUpload` → `confirmAssetUpload` → `deleteAsset` flow handles everything.

**Acceptance Criteria**:
- `createAssetUpload` with namespace `imports` and a valid OFX/CSV MIME type returns a presigned URL
- File size > 10 MB is rejected with a validation error
- MIME types outside the allowed list are rejected
- `storage-key-template` produces keys under `imports/{ownerId}/` prefix

**Tests**:
- In assets service test suite: `AssetServiceImportsNamespaceTest`:
  - `givenImportsNamespace_whenCreateUpload_thenPresignedUrlReturned()`
  - `givenOversizedFile_whenCreateUpload_thenValidationError()`
  - `givenDisallowedMimeType_whenCreateUpload_thenValidationError()`

---

### IMPORT-13b — Banking Service: `ImportFileStoragePort` via Assets Service

**Depends on**: IMPORT-13a

**Description**:
Create the file storage abstraction in the banking service that delegates to the assets service GraphQL API. The banking service does NOT use MinIO SDK directly — it goes through the assets service.

**Port interface** — create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/port/ImportFileStoragePort.kt`:

```kotlin
/**
 * Port for temporary import file storage.
 * Files are stored for the duration of async processing (max 24h via download URL TTL).
 * Implementation delegates to the assets service.
 */
interface ImportFileStoragePort {
    /**
     * Stores the file and returns an [assetId] that can be used to retrieve it later.
     * @param accountId tenant ID used as ownerId in the assets service
     * @param bytes raw file bytes
     * @param fileName original filename (for display only)
     * @param mimeType file MIME type
     * @return assetId (UUID) — store in ImportJob for the consumer to use
     */
    fun store(accountId: UUID, bytes: ByteArray, fileName: String, mimeType: String): UUID

    /**
     * Retrieves the file bytes using a presigned download URL from the assets service.
     */
    fun read(assetId: UUID): ByteArray

    /**
     * Deletes the asset from the assets service (best-effort; idempotent).
     */
    fun delete(assetId: UUID)
}
```

**Assets service GraphQL client** — create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/infra/assets/AssetsServiceClient.kt`:

A Micronaut `@Client` HTTP client that calls the assets service GraphQL endpoint (`http://assets-service/graphql`). Uses standard HTTP POST with `Content-Type: application/json`.

Operations needed:
- `createAssetUpload(namespace, ownerId, mimeType, originalFilename, sizeBytes)` → returns `{ id, uploadUrl }`
- `confirmAssetUpload(assetId)` → returns `{ id, status }`
- `asset(id)` → returns `{ id, downloadUrl(ttl: 3600) }` (presigned download URL)
- `deleteAsset(assetId)` → returns `Boolean`

**Implementation** — create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/infra/assets/AssetsServiceFileStorage.kt`:

```kotlin
@Singleton
class AssetsServiceFileStorage(
    private val assetsClient: AssetsServiceClient,
    private val httpClient: HttpClient,  // Micronaut HTTP client for presigned URL PUT
) : ImportFileStoragePort {

    override fun store(accountId: UUID, bytes: ByteArray, fileName: String, mimeType: String): UUID {
        // Step 1: Request presigned upload URL from assets service
        val upload = assetsClient.createAssetUpload(
            namespace = "transaction-imports",
            ownerId = accountId.toString(),
            mimeType = mimeType,
            originalFilename = fileName,
            sizeBytes = bytes.size.toLong(),
        )
        // Step 2: PUT file bytes directly to presigned S3/R2 URL
        httpClient.put(upload.uploadUrl, bytes, contentType = mimeType)
        // Step 3: Confirm upload with assets service
        assetsClient.confirmAssetUpload(upload.id)
        return upload.id
    }

    override fun read(assetId: UUID): ByteArray {
        val asset = assetsClient.getAsset(assetId)
        val downloadUrl = requireNotNull(asset.downloadUrl) { "Asset $assetId has no download URL" }
        return httpClient.get(downloadUrl)
    }

    override fun delete(assetId: UUID) {
        runCatching { assetsClient.deleteAsset(assetId) }
            .onFailure { log.warn("Failed to delete import asset $assetId", it) }
    }
}
```

**Configuration** — add to `application.yml`:
```yaml
banking:
  assets-service:
    url: ${ASSETS_SERVICE_URL}
    graphql-path: /graphql
```

**Kafka payload update**: The `ImportFileRequestedEvent` now carries `assetId: UUID` instead of `fileKey: String`.

**Acceptance Criteria**:
- `store` → assets service creates asset + file is retrievable via presigned URL
- `read` → fetches actual file bytes from presigned download URL
- `delete` on non-existent asset does not throw (logged as warning)
- `store` failure (e.g. assets service down) propagates exception to caller
- No MinIO SDK dependency in `build.gradle.kts` (banking service)
- `ASSETS_SERVICE_URL` env var is documented and added to deployment config

**Tests**:
- `AssetsServiceFileStorageTest` (unit — mock `AssetsServiceClient` + mock HTTP client):
- `AssetsServiceFileStorageIT` (Testcontainers — requires assets service running or WireMock):


**Infrastructure checklist update**:
- ~~MinIO bucket `invistus-imports`~~ — not needed (assets service manages its existing bucket)
- ~~MinIO lifecycle rule~~ — not needed
- ~~MinIO Testcontainer in CI~~ — not needed
- ✅ `ASSETS_SERVICE_URL` env var in banking service deployment
- ✅ WireMock dependency for integration tests: `testImplementation("org.wiremock:wiremock-standalone:x.x.x")`

---

### IMPORT-14 — Kafka Topics: Infrastructure

**Depends on**: _(none — infrastructure configuration)_

**Description**:
  Configure the following Kafka topics for the banking service:

  Topics to create/configure:
  - `banking.import.file-requested` — partitions: 6, retention: 7 days, cleanup-policy: delete
  - `banking.import.file-requested.DLT` — partitions: 1, retention: 30 days (dead-letter)
  - `banking.import.job-completed` — partitions: 6, retention: 24 hours

  In `service/kotlin/banking/src/main/resources/application.yml`:
  ```yaml
  kafka:
    topics:
      import-file-requested: banking.import.file-requested
      import-job-completed: banking.import.job-completed
    consumers:
      import-file:
        group-id: banking-import-consumer
        max-poll-records: 1  # process one import at a time per partition
        enable-auto-commit: false
        auto-offset-reset: earliest
  ```

  In `service/kotlin/banking/src/test/resources/application-test.yml`:
  - Use embedded Kafka (already configured) or Testcontainers Kafka
  - Map topics to test topic names

  **Acceptance Criteria**:
  - Topics exist in the Kafka cluster after service startup (auto-creation enabled in dev/staging)
  - Consumer group `banking-import-consumer` is registered
  - DLT topic receives messages after max retry exhaustion
  - `max-poll-records: 1` ensures one file processed at a time per consumer instance

  **Tests**:
  - `KafkaTopicConfigIT`: verify all 3 topics exist and are reachable using `AdminClient`

---

  ### IMPORT-15 — Kafka Producer: ImportFileRequested Event


  **Description**:
  Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/infra/kafka/ImportFileProducer.kt`:

  ```kotlin
  data class ImportFileRequestedEvent(
      val jobId: UUID,
      val assetId: UUID,          // assets service assetId (replaces raw fileKey)
      val source: ImportSource,
      val financialAccountId: UUID,
      val accountId: UUID,
  )

  @Singleton
  class ImportFileProducer(
      @KafkaClient producer: KafkaProducer<String, ImportFileRequestedEvent>,
      @Value("\${kafka.topics.import-file-requested}") private val topic: String,
  ) {
      fun publish(event: ImportFileRequestedEvent) {
          producer.send(ProducerRecord(topic, event.jobId.toString(), event))
      }
  }
  ```

  Event is keyed by `jobId` (ensures ordering per job within a partition).

  **Acceptance Criteria**:
  - Message is published to correct topic
  - Message key = jobId string
  - JSON serialization includes all fields
  - On broker unavailability: propagates exception (no silent swallow)

  **Tests**:
  - `ImportFileProducerTest` (unit — mock producer): verify correct topic and key
  - `ImportFileProducerIT` (embedded Kafka): publish → consume → verify payload

---

### IMPORT-16 — ImportEventBus: In-Process Event Bridge


**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import_/ImportEventBus.kt`:

```kotlin
data class ImportJobStatusPayload(
    val jobId: UUID,
    val accountId: UUID,
    val status: ImportJobStatus,
    val importedCount: Int?,
    val skippedCount: Int?,
    val duplicateReviewCount: Int?,
    val errorCount: Int?,
)

@Singleton
class ImportEventBus {
    private val sink: Sinks.Many<ImportJobStatusPayload> =
        Sinks.many().multicast().onBackpressureBuffer(256, false)

    fun emit(payload: ImportJobStatusPayload) {
        sink.tryEmitNext(payload)
    }

    fun asFlux(): Flux<ImportJobStatusPayload> = sink.asFlux()
}
```

**Key considerations**:
- `onBackpressureBuffer(256, false)` — buffer up to 256 events; `false` = do not auto-cancel if no subscribers
- Multiple SSE subscribers to the same `Flux` receive all events (multicast)
- `tryEmitNext` is non-blocking; failure is logged but does not throw (no connected clients = emit is benign)

**Acceptance Criteria**:
- Multiple concurrent subscribers each receive emitted events
- Emitting when no subscribers is a no-op (no exception)
- Events are delivered in emission order

**Tests**:
- `ImportEventBusTest`:
  - `givenSubscriber_whenEmit_thenReceived()`
  - `givenMultipleSubscribers_whenEmit_thenAllReceive()`
  - `givenNoSubscribers_whenEmit_thenNoException()`
  - `givenTwoJobs_whenEmitForJobA_thenSubscriberForJobBDoesNotReceive()` (filter test — done at SSE level, but test bus isolation)

---

### IMPORT-17 — ImportFileConsumer: Kafka Consumer


**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/infra/kafka/ImportFileConsumer.kt`:

```kotlin
@KafkaListener(groupId = "banking-import-consumer", offsetReset = OffsetReset.EARLIEST)
@Singleton
class ImportFileConsumer(
    private val fileStorage: ImportFileStoragePort,   // delegates to assets service — IMPORT-13b
    private val sourceDetector: ImportSourceDetector,
    private val ofxParser: OFXParser,
    private val csvNubankParser: CsvNubankParser,
    private val importProcessor: ImportProcessor,
    private val importJobRepository: ImportJobRepository,
    private val importEventBus: ImportEventBus,
    private val producer: KafkaProducer<String, ImportJobCompletedEvent>,
)
```

Processing flow on message receipt (`ImportFileRequestedEvent`):
1. Read event — payload contains `jobId`, `assetId` (assets service UUID), `source`, `financialAccountId`, `accountId`
2. Load `ImportJob` from DB; if already `COMPLETED` → ack and return (idempotent — crash recovery)
3. Update job status → `PROCESSING`; emit `PROCESSING` event to `ImportEventBus`
4. Read file bytes via `fileStorage.read(assetId)` → calls assets service to get presigned download URL, then fetches bytes
5. Detect source if not already known; select appropriate parser; parse into `List<ParsedTransaction>`
6. Call `importProcessor.process(parsedTransactions, job, source)`
7. Update job with counts (`importedCount`, `skippedCount`, `duplicateReviewCount`, `errorCount`)
8. If `duplicateReviewCount > 0`: serialize candidates to `reviewPayload` (JSONB); status → `REVIEW_REQUIRED`
9. If no candidates: status → `COMPLETED`
10. Emit terminal status event to `ImportEventBus`
11. Publish `ImportJobCompletedEvent` to `banking.import.job-completed`
12. Delete asset via `fileStorage.delete(assetId)` — calls `deleteAsset` on assets service (best-effort; assets service TTL handles cleanup if this call fails)
13. Commit Kafka offset

On exception during steps 4–11:
- Update job status → `FAILED`, `errorDetails` = exception message + stack summary
- Emit `FAILED` event to `ImportEventBus`
- Commit offset (do NOT requeue infinitely — prevents poison pill loops)
- Log with full stack trace for DLT analysis
- Best-effort: attempt `fileStorage.delete(assetId)` even on failure (no throw if it fails)

**Acceptance Criteria**:
- Consumer processes one message at a time per partition (`max-poll-records: 1`)
- On `COMPLETED` job re-delivery (crash recovery): skips reprocessing without re-reading the asset
- File asset is deleted from assets service after successful processing
- Exception in processing → job marked `FAILED`, offset committed, no infinite retry loop
- `ImportEventBus.emit()` is called for every status transition (`PROCESSING` → `COMPLETED`/`FAILED`/`REVIEW_REQUIRED`)
- Assets service call failure in step 4 → job marked `FAILED` (asset unavailable = unrecoverable)
- Assets service delete failure in step 12 → logged as warning, NOT a failure (TTL handles eventual cleanup)

**Tests**:
- `ImportFileConsumerIT` (Testcontainers Postgres + Kafka + Mock Assets service):
  - `givenValidOFXFile_whenConsumed_thenJobCompleted_transactionsImported()`
  - `givenAlreadyCompletedJob_whenConsumed_thenSkipped()` (idempotency — no re-read of asset)
  - `givenMalformedFile_whenConsumed_thenJobFailed_offsetCommitted()`
  - `givenFileWithLevel2Duplicates_whenConsumed_thenJobReviewRequired_candidatesSaved()`
  - `givenAssetsServiceReadFailure_whenConsumed_thenJobFailed()` (replaces `givenMinioReadFailure`)
  - `givenLevel1DuplicatesOnly_whenConsumed_thenJobCompleted_skippedCountCorrect()`
  - `givenAssetsServiceDeleteFailure_whenConsumed_thenJobStillCompleted()` (delete is best-effort)

---

### IMPORT-18 — ImportJobService: Orchestration


**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/import/ImportJobService.kt`:

This service handles the **synchronous** part of the import flow (API side) and also the NFC-e sync path:

```kotlin
@Singleton
open class ImportJobService(
    private val importJobRepository: ImportJobRepository,
    private val fileStorage: ImportFileStoragePort,
    private val importFileProducer: ImportFileProducer,
    private val sourceDetector: ImportSourceDetector,
    private val nfceClient: NfceClient,
    private val importProcessor: ImportProcessor,
    private val financialAccountRepository: FinancialAccountRepository,
)
```

Methods:
- `fun initiateFileImport(financialAccountId, accountId, userId, fileBytes, fileName, source?): ImportJob`
  1. Validate financial account ownership
  2. Hash file bytes (SHA-256) → check Level 3 idempotency (findByFileHash)
  3. If COMPLETED job exists → return it immediately
  4. Detect source if not provided
  5. Store file to MinIO (`transaction-imports/{accountId}/{jobId}/{sanitizedFileName}`)
  6. Create `ImportJob` record (status=PENDING)
  7. Publish `ImportFileRequestedEvent` to Kafka
  8. Return job

- `fun importNfce(financialAccountId, accountId, userId, qrCodeUrl): ImportJob`
  1. Validate financial account ownership
  2. Fetch NFC-e data from SEFAZ (sync, timeout 5s)
  3. Check Level 1 dedup on `chNFe`
  4. If duplicate → return existing job record (or create a "skipped" job for UX continuity)
  5. Create `Transaction` directly (no Kafka)
  6. Create completed `ImportJob` record (status=COMPLETED, imported=1)
  7. Return job

- `fun getJob(id, accountId): ImportJob`
- `fun listJobs(financialAccountId?, accountId): List<ImportJob>`
- `fun resolveImportDuplicate(jobId, incomingId, resolution, accountId): ImportJob`
  - Loads job; validates `REVIEW_REQUIRED` status
  - Applies resolution (KEEP_BOTH / USE_INCOMING / KEEP_EXISTING) to candidate
  - If all candidates resolved → update job status to COMPLETED

**Acceptance Criteria**:
- `initiateFileImport` with same file twice returns the same `ImportJob` (Level 3 idempotency)
- NFC-e import is synchronous and completes in the same request
- `resolveImportDuplicate` with `USE_INCOMING` soft-deletes the existing transaction and commits the incoming one
- `resolveImportDuplicate` updates job to COMPLETED only when all candidates are resolved

**Tests**:
- `ImportJobServiceTest` (unit — all repos/ports mocked):
  - `givenNewFile_whenInitiate_thenJobPendingAndKafkaPublished()`
  - `givenSameFileTwice_whenInitiate_thenExistingJobReturned()` (Level 3 idempotency)
  - `givenValidNfceUrl_whenImportNfce_thenTransactionCreated_jobCompleted()`
  - `givenExistingChNFe_whenImportNfce_thenSkipped()`
  - `givenKeepBothResolution_whenResolve_thenBothTransactionsExist()`
  - `givenUseIncomingResolution_whenResolve_thenExistingDeleted_incomingCommitted()`

---

## Sprint 5 — SSE Endpoint + GraphQL

---

### IMPORT-19 — SSE Endpoint: Micronaut Controller


**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/rest/ImportSseController.kt`:

```kotlin
@Controller("/imports")
class ImportSseController(
    private val importEventBus: ImportEventBus,
    private val importJobService: ImportJobService,
) {
    @Get("/{jobId}/events", produces = [MediaType.TEXT_EVENT_STREAM])
    fun importJobEvents(
        @PathVariable jobId: UUID,
        principal: RequestPrincipal,
    ): Flux<ServerSentEvent<ImportJobStatusPayload>> {
        val accountId = requireNotNull(principal.currentAccountId)
        val current = importJobService.getJob(jobId, accountId)  // throws if not found
        // Connection-at-completion guard
        if (current.status.isTerminal()) {
            return Flux.just(ServerSentEvent.of(ImportJobStatusPayload.from(current)))
        }
        return importEventBus.asFlux()
            .filter { it.jobId == jobId && it.accountId == accountId }
            .map { ServerSentEvent.of(it) }
            .takeUntil { it.status.isTerminal() }
            .timeout(Duration.ofMinutes(10))
    }
}
```

Security:
- Endpoint requires authentication (same mechanism as GraphQL endpoints)
- `accountId` filter prevents cross-tenant event leakage

**Acceptance Criteria**:
- Returns `text/event-stream` content type
- Client connecting after job completion receives one event and stream closes
- Client connecting before completion receives events as they arrive
- Cross-tenant: principal cannot receive events for another account's job
- Non-existent jobId returns 404 (propagated from `getJob`)
- After 10 minutes idle, stream closes with timeout

**Tests**:
- `ImportSseControllerTest` (unit — mock bus + service):
- `ImportSseControllerIT` (Micronaut `@MicronautTest`, TestHttpClient with streaming):


---

### IMPORT-20 — GraphQL Schema: Import Types


**Description**:
Update `contracts/graphql/subgraphs/banking/schema.graphqls` with the import schema (per the spec):

Add enums: `ImportSource`, `ImportStatus`
Add types: `ImportDuplicateCandidate`, `TransactionImport`, `ImportResult`
Add input: `ResolveImportDuplicateInput`
Add queries: `transactionImports(financialAccountId: ID): [TransactionImport!]!`, `transactionImport(id: ID!): TransactionImport`
Add mutations: `importTransactionsFile(...)`, `importNfce(...)`, `resolveImportDuplicate(...)`

After updating the subgraph schema, run the supergraph composition script to update all 3 supergraph files:
- `contracts/graphql/supergraph/supergraph.graphql`
- `contracts/graphql/supergraph/supergraph.local.graphql`
- `contracts/graphql/supergraph/supergraph.staging.graphql`

**Acceptance Criteria**:
- Schema composition succeeds without errors
- All new types are reachable from `Query` and `Mutation`
- `duplicateCandidates` field on `TransactionImport` is nullable (only present when `status = REVIEW_REQUIRED`)

**Tests**:
- `BankingSchemaValidationTest`: load schema and verify all type definitions resolve
- After composition: verify supergraph introspection includes new types

---

### IMPORT-21 — ImportResolver: GraphQL Resolver

**Status**: Done

**Description**:
Create `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/resolver/ImportResolver.kt`:

```kotlin
@Singleton
class ImportResolver(
    private val importJobService: ImportJobService,
) {
    // Queries
    fun transactionImports(financialAccountId: String?, principal: RequestPrincipal): List<TransactionImportDTO>
    fun transactionImport(id: String, principal: RequestPrincipal): TransactionImportDTO?

    // Mutations
    fun importTransactionsFile(input: Map<String, Any?>, principal: RequestPrincipal): ImportResultDTO
    fun importNfce(input: Map<String, Any?>, principal: RequestPrincipal): ImportResultDTO
    fun resolveImportDuplicate(input: Map<String, Any?>, principal: RequestPrincipal): TransactionImportDTO
}
```

Create `TransactionImportDTO` and `ImportResultDTO` in `graphql/dto/ImportDTO.kt`.

`importTransactionsFile` mutation:
- Decodes `fileBase64` → `ByteArray`
- Validates file size ≤ 10 MB (throw `ValidationException` if exceeded)
- Calls `importJobService.initiateFileImport(...)`
- Permission check: `BANKING_TRANSACTION_CREATE`

`importNfce` mutation:
- Validates `qrCodeUrl` is a valid URL
- Calls `importJobService.importNfce(...)`
- Permission check: `BANKING_TRANSACTION_CREATE`

**Acceptance Criteria**:
- File > 10 MB is rejected before calling the service
- Invalid `qrCodeUrl` format → validation error
- `transactionImport(id)` returns null if not found (not an error)
- `importTransactionsFile` returns `status = PENDING` immediately (Kafka async)
- `importNfce` returns `status = COMPLETED` immediately (sync)

**Tests**:
- `ImportResolverTest` (unit — mock service):
  - `givenValidFile_whenImport_thenReturnsPendingJob()`
  - `givenFileTooLarge_whenImport_thenValidationException()`
  - `givenValidQrUrl_whenImportNfce_thenReturnsCompletedJob()`
  - `givenInvalidQrUrl_whenImportNfce_thenValidationException()`
  - `givenJobId_whenQuery_thenDtoReturned()`

---

### IMPORT-22 — BankingWiringFactory: Wire Import Resolver

**Status**: Done

**Depends on**: IMPORT-21

**Description**:
Update `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/BankingWiringFactory.kt`:

Register data fetchers for all new queries and mutations:
- `Query.transactionImports` → `importResolver.transactionImports(...)`
- `Query.transactionImport` → `importResolver.transactionImport(...)`
- `Mutation.importTransactionsFile` → `importResolver.importTransactionsFile(...)`
- `Mutation.importNfce` → `importResolver.importNfce(...)`
- `Mutation.resolveImportDuplicate` → `importResolver.resolveImportDuplicate(...)`

Apply permission checks via the existing permission interceptor pattern:
- `importTransactionsFile`, `importNfce`, `resolveImportDuplicate` → require `BANKING_TRANSACTION_CREATE`
- `transactionImports`, `transactionImport` → require `BANKING_TRANSACTION_VIEW`

**Acceptance Criteria**:
- All 5 new operations are registered and callable via GraphQL
- Unauthorized call returns permission error (not NPE)
- All existing operations continue to work (no regression)

**Tests**:
- `BankingWiringFactoryTest`: verify all expected data fetchers are registered by name
- Full integration test: `ImportGraphQLIT` with actual GraphQL request (Micronaut test server)

---

## Sprint 6 — Frontend

---

### IMPORT-23 — Next.js Route Handler: SSE Proxy

**Status**: Done

**Depends on**: IMPORT-19

**Description**:
Create `web/src/app/api/imports/[jobId]/stream/route.ts`:

```typescript
export async function GET(
  req: Request,
  { params }: { params: { jobId: string } },
) {
  const token = req.headers.get('x-auth-token')
  if (!token) return new Response('Unauthorized', { status: 401 })

  const upstream = await fetch(
    `${process.env.BANKING_SERVICE_INTERNAL_URL}/imports/${params.jobId}/events`,
    {
      headers: { Authorization: `Bearer ${token}` },
      // @ts-expect-error — Node.js fetch duplex required for streaming
      duplex: 'half',
    },
  )

  if (!upstream.ok) {
    return new Response(upstream.statusText, { status: upstream.status })
  }

  return new Response(upstream.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',  // Disable Nginx buffering for SSE
    },
  })
}
```

Add `BANKING_SERVICE_INTERNAL_URL` to `.env.local.example` and deployment config.

**Acceptance Criteria**:
- Missing token returns 401
- Non-existent jobId: proxies 404 from Micronaut
- Response headers set `no-cache` and `X-Accel-Buffering: no` (critical for SSE through reverse proxies)
- Stream stays open while Micronaut stream is open

**Tests**:
- `stream.route.test.ts` (Jest + fetch mock):
  - `givenMissingToken_whenGet_then401()`
  - `givenValidToken_whenGet_thenSseHeadersSet()`
  - `givenMicronautReturns404_whenGet_then404Proxied()`

---

### IMPORT-24 — `useImportJobStatus` Hook

**Status**: Done

**Depends on**: IMPORT-23

**Description**:
Create `web/src/lib/hooks/useImportJobStatus.ts`:

```typescript
export function useImportJobStatus(
  jobId: string | null,
  callbacks: {
    onCompleted: () => void
    onReviewRequired: () => void
    onFailed: (errorDetails?: unknown) => void
    onStatusChange?: (status: ImportStatus) => void
  },
)
```

Implementation:
- Uses `fetch` + `ReadableStream` (NOT `EventSource`) to support custom headers
- Sends `x-auth-token` header from `getAuthToken()`
- Parses SSE `data:` lines using a buffer (handles partial reads / multi-line events)
- Calls appropriate callback on terminal status
- `AbortController` for cleanup on unmount or `jobId` change
- Graceful degradation on connection failure: calls `apolloClient.query(IMPORT_JOB_QUERY, { id: jobId })` once and dispatches result via callbacks

**Acceptance Criteria**:
- Cleanup (abort) is called on unmount
- Partial SSE chunks buffered correctly (data split across multiple reads)
- `onStatusChange` called on each received event (for progress indicator)
- Graceful degradation called only on connection error (not on terminal events)
- Token refreshed if auth token changes during long-lived stream (use `getAuthToken()` lazily)

**Tests**:
- `useImportJobStatus.test.ts` (Jest + `@testing-library/react-hooks`):
  - `givenStreamCompletedEvent_whenHook_thenOnCompletedCalled()`
  - `givenStreamReviewRequired_whenHook_thenOnReviewRequiredCalled()`
  - `givenConnectionError_whenHook_thenFallbackQueryTriggered()`
  - `givenUnmount_whenHook_thenAbortCalled()`
  - `givenPartialChunks_whenHook_thenEventsBufferedCorrectly()`

---

### IMPORT-25 — GraphQL Operations Codegen

**Depends on**: IMPORT-20

**Description**:
Add GraphQL operations to `web/src/lib/graphql/operations/banking/`:

`import-mutations.ts`:
```graphql
mutation ImportTransactionsFile($input: ImportTransactionsFileInput!) {
  importTransactionsFile(
    financialAccountId: $input.financialAccountId
    source: $input.source
    fileBase64: $input.fileBase64
    fileName: $input.fileName
  ) {
    importJob { id status source fileName totalRows createdAt }
  }
}

mutation ImportNfce($financialAccountId: ID!, $qrCodeUrl: String!) {
  importNfce(financialAccountId: $financialAccountId, qrCodeUrl: $qrCodeUrl) {
    importJob { id status importedCount skippedCount completedAt }
  }
}

mutation ResolveImportDuplicate($input: ResolveImportDuplicateInput!) {
  resolveImportDuplicate(input: $input) {
    id status duplicateReviewCount
    duplicateCandidates { incoming { id description amount date } existing { id description amount date source } reason }
  }
}
```

`import-queries.ts`:
```graphql
query GetImportJob($id: ID!) {
  transactionImport(id: $id) {
    id status source fileName totalRows importedCount skippedCount
    duplicateReviewCount errorCount errorDetails completedAt
    duplicateCandidates { incoming { ... } existing { ... } reason }
  }
}

query ListImportJobs($financialAccountId: ID) {
  transactionImports(financialAccountId: $financialAccountId) {
    id status source fileName importedCount createdAt completedAt
  }
}
```

Run codegen to generate `import-mutations.generated.ts` and `import-queries.generated.ts`.

**Acceptance Criteria**:
- Codegen runs without errors after schema update
- All generated types are correctly typed (no `any`)
- `useImportTransactionsFileMutation`, `useImportNfceMutation`, `useGetImportJobQuery`, `useListImportJobsQuery`, `useResolveImportDuplicateMutation` hooks are generated

---

### IMPORT-26 — Import File Upload UI

**Depends on**: IMPORT-24, IMPORT-25

**Status**: Done

**Description**:
Create `web/src/app/personal-finance/finance-transactions/components/ImportTransactionsDrawer.tsx`:

Drawer with:
1. **Source selection step**: OFX / CSV Nubank
2. **File upload step**: `<input type="file">` with drag-and-drop; accepted types: `.ofx`, `.csv`; max 10 MB validated client-side
3. **Processing step**: progress indicator using `useImportJobStatus` hook; shows `status` and `importedCount` as events arrive
4. **Result step**: summary card — "X transações importadas, Y ignoradas (duplicatas)"
5. **Error step**: if `status = FAILED`, show error message with retry button

Open via "Importar extrato" button in the transactions page header.

On `onCompleted`: close drawer + call `refetch()` on transactions list.

i18n: add keys to `en.json` and `pt.json` for all new strings.

**Acceptance Criteria**:
- File > 10 MB shows client-side error before submission
- Unsupported file type rejected with helpful message
- Progress indicator updates in real-time as SSE events arrive
- Drawer close aborts SSE stream (via AbortController returned by hook)
- On completion: transactions list auto-refreshes

**Tests**:
- `ImportTransactionsDrawer.test.tsx`:
  - `givenValidOFXFile_whenSubmit_thenProgressShown()`
  - `givenFileTooLarge_whenSelect_thenErrorShown()`
  - `givenImportCompleted_whenSseEvent_thenSuccessStateAndRefetch()`
  - `givenImportFailed_whenSseEvent_thenErrorStateShown()`

---

### IMPORT-27 — NFC-e QR Scanner UI

**Depends on**: IMPORT-25

**Description**:
Create `web/src/app/personal-finance/finance-transactions/components/ScanNfceButton.tsx`:

- Button "Escanear NFC-e" (mobile-first — uses camera)
- On click: open camera via `@zxing/library` or `react-qr-scanner` to scan QR code
- On QR detected: call `importNfce` mutation with `qrCodeUrl`
- Show success toast with transaction preview (description, amount)
- Show error toast if SEFAZ unavailable or NFC-e invalid

Note: Only meaningful on mobile; on desktop, show a text field to paste the NFC-e URL directly (useful for testing).

**Acceptance Criteria**:
- On mobile: camera opens and scans QR code
- On desktop: URL input field as fallback
- Duplicate NFC-e scan shows "Nota já importada" message (not an error)
- SEFAZ timeout shows "SEFAZ indisponível, tente novamente"

**Tests**:
- `ScanNfceButton.test.tsx`:
  - `givenSuccessfulScan_whenImport_thenSuccessToast()`
  - `givenDuplicateScan_whenImport_thenAlreadyImportedMessage()`
  - `givenSefazTimeout_whenImport_thenErrorToast()`

---

### IMPORT-28 — Duplicate Review UI

**Depends on**: IMPORT-26

**Status**: Done

**Description**:
Create `web/src/app/personal-finance/finance-transactions/components/ImportDuplicateReview.tsx`:

Shown when import job reaches `REVIEW_REQUIRED` status (either via SSE event or on page re-open).

UI:
- Header: "X transações precisam de revisão"
- For each `ImportDuplicateCandidate`:
  - Side-by-side card: "Nova (importada)" vs "Existente"
  - Both show: date, description, amount, source badge
  - Action buttons: "Manter ambas" / "Usar nova" / "Manter existente"
  - Resolved candidates show a checkmark and are collapsed
- "Concluir revisão" button (enabled only when all candidates resolved) → calls `resolveImportDuplicate` for each unresolved → triggers `refetchTransactions`

**Acceptance Criteria**:
- "Concluir revisão" disabled until all candidates have a resolution selected
- `USE_INCOMING` resolution: visual indicator that existing will be replaced
- After all resolved: transactions list refreshes
- Navigating away and back: job state persists (list import jobs on page load)

**Tests**:
- `ImportDuplicateReview.test.tsx`:
  - `givenCandidates_whenRendered_thenAllShown()`
  - `givenNotAllResolved_whenConcluir_thenButtonDisabled()`
  - `givenAllResolved_whenConcluir_thenMutationCalledForEach()`

---

### IMPORT-29 — Bruno API Collection: Import Requests

**Depends on**: IMPORT-21

**Status**: Done

**Description**:
Create Bruno request files in `contracts/api-collections/bruno/collections/supergraph/Banking/`:

- `Import OFX File.bru` — `importTransactionsFile` mutation with a small base64-encoded OFX fixture in the body
- `Import NFC-e QR.bru` — `importNfce` mutation with a test QR code URL (pointing to a fixture or known public NFC-e)
- `Get Import Job.bru` — `transactionImport(id)` query; `id` referenced via Bruno variable
- `List Import Jobs.bru` — `transactionImports(financialAccountId)` query
- `Resolve Import Duplicate.bru` — `resolveImportDuplicate` mutation with `KEEP_BOTH` resolution

Each `.bru` file includes:
- Pre-request script to set `Authorization` header from env variable
- Post-response script to save `importJob.id` to Bruno env for subsequent requests

**Acceptance Criteria**:
- All 5 requests execute successfully against local environment
- `Get Import Job` uses the `id` captured from `Import OFX File` response
- Collection includes `README` comment explaining the happy-path execution order

---

## Cross-Cutting: Infrastructure Checklist

The following items must be confirmed/created before Sprint 4 stories can be deployed:

| Item | Owner | Notes |
|---|---|---|
| Kafka topics created | Infra | `banking.import.file-requested`, `.DLT`, `banking.import.job-completed` |
| Kafka consumer group registered | Infra | `banking-import-consumer` |
| `imports` namespace added to assets service `asset-config.yml` | Dev | IMPORT-13a — no new bucket or lifecycle rule needed |
| `ASSETS_SERVICE_URL` env var in banking service deployment | Dev | Internal URL pointing to assets service |
| WireMock dependency for banking integration tests | Dev | `testImplementation("org.wiremock:wiremock-standalone:x.x.x")` |
| `BANKING_SERVICE_INTERNAL_URL` env var in Next.js | Dev | Pointing to banking service (internal network) |
| `X-Accel-Buffering: no` verified on Nginx/load balancer | Infra | Required for SSE to work through reverse proxy |
| Testcontainers Kafka available in CI | Infra | Verify existing CI setup already has Kafka TC |

> **Not needed** (removed): MinIO bucket, MinIO lifecycle rule, MinIO SDK, MinIO Testcontainer — all handled by the existing assets service.

---

## Test Coverage Summary by Sprint

| Sprint | Unit | Integration | E2E |
|---|---|---|---|
| 1 — Foundation | Mapper round-trips, enum tests | Migration IT, Repository IT | — |
| 2 — Parsers | Parser unit tests with fixtures | NFC-e client IT (optional, `@Disabled`) | — |
| 3 — Idempotency | Dedup service tests, processor tests | Dedup IT with Testcontainers | — |
| 4 — Async Pipeline | EventBus, producer unit | Consumer IT (Kafka + Postgres + WireMock assets) | — |
| 5 — SSE + GraphQL | Resolver tests, SSE controller unit | Full GraphQL + SSE integration IT | — |
| 6 — Frontend | Component tests, hook tests | Route Handler test | Happy-path E2E (Playwright) |
