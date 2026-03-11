# Banking Import Architecture - Implementation Backlog

This backlog breaks the target architecture into executable activities that can be delivered item by item.
Language and labels are intentionally implementation-focused for engineering execution.

## How To Use This Backlog

- Work in order by milestone (`M0` -> `M8`).
- Do not start a task with unresolved dependencies.
- Open a PR per task or small task bundle.
- Keep feature flags enabled for new runtime paths until production validation is complete.

## Status Legend

- `TODO`: not started
- `IN_PROGRESS`: currently being implemented
- `DONE`: merged and validated
- `BLOCKED`: waiting on dependency/decision

---

## M0 - Design Freeze And Contracts

### BKL-001 - Freeze canonical event contracts (IN_PROGRESS)
Service: `banking`, `assistant`, `pdf-extractor`

Scope:
- Define JSON schemas for:
- `pdf-extractor.extracted` (already exists, confirm stable fields)
- `assistant.banking-reader.requested` (new)
- `assistant.banking-pdf-reader.done` (confirm/finalize)
- `assistant.banking-pdf-reader.failed` (new)
- Include version field (`schema_version`) and `callback_context`.

Deliverables:
- Contract document under `docs/`.
- Example payloads per topic.
- Draft delivered at `docs/06-contracts/kafka/banking-pdf-import/`:
  - `pdf-extractor.extracted` v1 schema + example
  - `assistant.banking-reader.requested` v1 schema + example
  - `assistant.banking-pdf-reader.done` v1 schema + example
  - `assistant.banking-pdf-reader.failed` v1 schema + example

Acceptance criteria:
- All involved teams approve schemas.
- No required field ambiguity remains.

### BKL-002 - Define reader output canonical contract (IN_PROGRESS)
Service: `banking`

Scope:
- Standardize output model for deterministic and LLM paths:
- `schema_id`, `schema_version`, `processor_type`, `reader_id`, `confidence`, `warnings`, `normalized_records`, `raw_metadata`.

Deliverables:
- Kotlin data model for canonical parse output.
- Mapping rules from current parser outputs to canonical model.
- Draft delivered:
  - `src/main/kotlin/.../domain/import/CanonicalReaderOutput.kt`
  - `src/main/kotlin/.../domain/import/CanonicalReaderOutputMapper.kt`
  - `README.md` section: "Mapping Rules (Current Parsers -> Canonical Contract)"
  - Unit tests: `CanonicalReaderOutputMapperTest`

Acceptance criteria:
- OFX/CSV existing flows can map into canonical output without data loss.

---

## M1 - Banking Routing Core Refactor

### BKL-003 - Introduce extraction routing layer for PDF (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-001`, `BKL-002`

Scope:
- Add `ExtractionRoutingService` called by PDF status consumer on `pdf-extractor.extracted`.
- Replace direct transition `EXTRACTED -> LLM_PROCESSING` with:
- deterministic route when known fingerprint exists
- LLM route when unknown fingerprint

Deliverables:
- New service class and unit tests.
- Refactored `PdfImportStatusConsumer`.
- Draft delivered:
  - `src/main/kotlin/.../domain/pdfimport/ExtractionRoutingService.kt`
  - `src/main/kotlin/.../infra/kafka/PdfImportStatusConsumer.kt` (uses routing service)
  - `src/main/kotlin/.../infra/kafka/AssistantBankingReaderRequestedProducer.kt`
  - Unit tests: `ExtractionRoutingServiceTest`

Acceptance criteria:
- Known fingerprint path does not publish LLM request.
- Unknown fingerprint path publishes LLM request and sets `LLM_PROCESSING`.

### BKL-004 - Add fingerprint strategy interface for PDF and Excel (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-003`

Scope:
- Add `FingerprintClassifier` abstraction.
- Implement `PdfFingerprintClassifier` (initial signatures).
- Add placeholder `ExcelFingerprintClassifier`.

Deliverables:
- Interface + implementations + tests.
- Registry/DI wiring.
- Draft delivered:
  - `src/main/kotlin/.../domain/import/fingerprint/FingerprintClassifier.kt`
  - `src/main/kotlin/.../domain/import/fingerprint/PdfFingerprintClassifier.kt`
  - `src/main/kotlin/.../domain/import/fingerprint/ExcelFingerprintClassifier.kt`
  - `src/main/kotlin/.../domain/import/fingerprint/FingerprintClassifierRegistry.kt`
  - Routing integration: `src/main/kotlin/.../domain/pdfimport/ExtractionRoutingService.kt`
  - Unit tests: `PdfFingerprintClassifierTest`, `ExcelFingerprintClassifierTest`, `FingerprintClassifierRegistryTest`

Acceptance criteria:
- Classifier returns `known_profile` or `unknown_profile` with deterministic reason.

### BKL-005 - Create producer for assistant request topic (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-001`, `BKL-003`

Scope:
- Add Kafka producer for `assistant.banking-reader.requested`.
- Publish structured payload for unknown PDF/Excel fingerprints.

Deliverables:
- Producer class and config keys in `application.yml`.
- Producer tests with payload assertions.
- Draft delivered:
  - `src/main/kotlin/.../infra/kafka/AssistantBankingReaderRequestedProducer.kt`
  - `src/main/resources/application.yml` (`kafka.topics.pdf.banking-reader-requested`)
  - `src/test/resources/application-test.yml` (`kafka.topics.pdf.banking-reader-requested`)
  - `src/main/kotlin/.../infra/kafka/KafkaTopicInitializer.kt` topic creation
  - Unit tests: `AssistantBankingReaderRequestedProducerTest`, `ExtractionRoutingServiceTest`

Acceptance criteria:
- Message includes `job_id`, `document_type`, extraction content, metadata, and `callback_context`.

---

## M2 - Deterministic PDF Path (Known Fingerprints)

### BKL-006 - Implement deterministic PDF parser interface (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-004`

Scope:
- Add `DeterministicPdfReader` interface and router.
- Add first concrete parser for a known structured profile (for example B3 statement profile).

Deliverables:
- Parser implementation with fixture-driven tests.
- Reader registry and routing tests.
- Draft delivered:
  - `src/main/kotlin/.../domain/pdfimport/reader/DeterministicPdfReader.kt`
  - `src/main/kotlin/.../domain/pdfimport/reader/DeterministicPdfReaderRegistry.kt`
  - `src/main/kotlin/.../domain/pdfimport/reader/DeterministicPdfReaderRouter.kt`
  - `src/main/kotlin/.../domain/pdfimport/reader/NubankCreditCardBillPdfReader.kt`
  - Routing integration: `src/main/kotlin/.../domain/pdfimport/ExtractionRoutingService.kt`
  - Fixtures: `src/test/resources/fixtures/pdf/nubank_known_profile_extracted.txt`
  - Unit tests:
    - `NubankCreditCardBillPdfReaderTest`
    - `DeterministicPdfReaderRegistryTest`
    - `DeterministicPdfReaderRouterTest`
    - `ExtractionRoutingServiceTest` (known profile invokes deterministic router)

Acceptance criteria:
- Fixture parsing is deterministic and reproducible.
- Output is canonical parse contract (`processor_type=DETERMINISTIC`).

### BKL-007 - Persist deterministic parse output into review payload (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-006`

Scope:
- For deterministic PDF path, save canonical output as `review_payload` and set `AWAITING_REVIEW`.
- Preserve current card-match enrichment behavior.

Deliverables:
- Service update + integration tests.
- Draft delivered:
  - `src/main/kotlin/.../domain/pdfimport/PdfImportService.kt`
    - `markAwaitingReviewFromCanonical(...)`
    - canonical payload enrichment with card-match metadata
    - canonical->`PdfBillExtractionResult` projection for review/confirm compatibility
  - `src/main/kotlin/.../domain/pdfimport/ExtractionRoutingService.kt`
    - known deterministic route now sets `AWAITING_REVIEW` via canonical payload
  - `src/main/kotlin/.../graphql/resolver/PdfImportResolver.kt`
    - canonical review payload fallback projection for GraphQL DTO
  - Integration test: `PdfImportWorkflowIT` (known profile -> `AWAITING_REVIEW`)
  - Unit tests:
    - `PdfImportServiceTest` (canonical review payload persistence + card-match enrichment)
    - `ExtractionRoutingServiceTest` (known profile triggers canonical awaiting-review path)
    - `PdfImportResolverTest` (canonical fallback projection)

Acceptance criteria:
- Known PDF does not require assistant call.
- UI review data remains consumable.

---

## M3 - Assistant As AI Agent (Kafka Worker Mode)

### BKL-008 - Add Kafka dependencies and topic config in assistant (IN_PROGRESS)
Service: `assistant`
Depends on: `BKL-001`

Scope:
- Add Micronaut Kafka dependency and config.
- Add consumer group/topic definitions for reader requested and done/failed outputs.

Deliverables:
- `build.gradle.kts` and `application.yml` updates.
- Boot test with Kafka enabled profile.
- Draft delivered:
  - `service/kotlin/assistant/build.gradle.kts`
    - adds `io.micronaut.kafka:micronaut-kafka`
  - `service/kotlin/assistant/src/main/resources/application.yml`
    - adds `kafka` topic/group configuration for:
      - `assistant.banking-reader.requested`
      - `assistant.banking-pdf-reader.done`
      - `assistant.banking-pdf-reader.failed`
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderRequestedBootstrapConsumer.kt`
    - gated bootstrap subscription bean for Kafka-enabled profile validation
  - `service/kotlin/assistant/src/test/kotlin/.../AssistantKafkaBootstrapIT.kt`
    - boots assistant with Kafka enabled and verifies topic subscription via probe message

Acceptance criteria:
- Assistant starts and subscribes to topic when enabled.

### BKL-009 - Implement assistant Kafka consumer for banking reader requests (IN_PROGRESS)
Service: `assistant`
Depends on: `BKL-008`

Scope:
- Consume `assistant.banking-reader.requested`.
- Validate payload and route by `document_type`.

Deliverables:
- Consumer class + deserialization model + tests.
- Draft delivered:
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/AssistantBankingReaderRequestedConsumer.kt`
    - consumes `assistant.banking-reader.requested` as JSON string
    - validates payload shape/content
    - routes by `document_type`
    - publishes `assistant.banking-pdf-reader.failed` on invalid/unsupported payload
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderKafkaModels.kt`
    - canonical request/failure event models for consume/publish flow
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/AssistantBankingPdfReaderFailedProducer.kt`
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderRequestedRoutingService.kt`
    - route targets for `CREDIT_CARD_BILL`, `BANK_STATEMENT`, `INVESTMENT_IR_REPORT`
  - `service/kotlin/assistant/src/test/kotlin/.../unit/kafka/AssistantBankingReaderRequestedConsumerTest.kt`
    - verifies routing and invalid payload -> failed event behavior

Acceptance criteria:
- Invalid payloads are routed to failed output with explicit error reason.

### BKL-010 - Implement prompt selection and completion execution for import use case (IN_PROGRESS)
Service: `assistant`
Depends on: `BKL-009`

Scope:
- Map `document_type + account_type + file_type` to prompt/template.
- Call `CompletionService` in strict JSON mode.
- Enforce output JSON schema validation.

Deliverables:
- Prompt resolver component.
- Validation layer for output schema.
- Unit tests for valid/invalid model responses.
- Draft delivered:
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderPromptResolver.kt`
    - tuple routing for `document_type + account_type + file_type`
    - prompt-id override config with fallback system templates
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/LlmBankingReaderRequestedRoutingService.kt`
    - calls `CompletionService` in JSON mode with `JSON_MODE` capability
    - maps provider errors to canonical assistant failure codes
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderOutputValidator.kt`
    - validates LLM JSON output against done-contract core structure
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderProcessingException.kt`
    - typed error propagation (`code`, `retryable`, `stage`)
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/AssistantBankingReaderRequestedConsumer.kt`
    - maps typed processing failures into actionable failed events
  - Unit tests:
    - `.../unit/kafka/LlmBankingReaderRequestedRoutingServiceTest.kt`
    - `.../unit/kafka/AssistantBankingReaderRequestedConsumerTest.kt`

Acceptance criteria:
- Non-JSON or schema-invalid output never emits `.done`.
- `.failed` includes actionable error code/message.

### BKL-011 - Publish done/failed events for banking (IN_PROGRESS)
Service: `assistant`
Depends on: `BKL-010`

Scope:
- Publish:
- `assistant.banking-pdf-reader.done`
- `assistant.banking-pdf-reader.failed`

Deliverables:
- Kafka producers + tests.
- Draft delivered:
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/AssistantBankingPdfReaderDoneProducer.kt`
    - producer for `assistant.banking-pdf-reader.done`
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/AssistantBankingPdfReaderFailedProducer.kt`
    - producer for `assistant.banking-pdf-reader.failed`
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/BankingReaderKafkaModels.kt`
    - canonical done/failed payload models
  - `service/kotlin/assistant/src/main/kotlin/.../infra/kafka/LlmBankingReaderRequestedRoutingService.kt`
    - emits `.done` on validated success with parse metadata
  - Unit tests:
    - `.../unit/kafka/LlmBankingReaderRequestedRoutingServiceTest.kt`
    - `.../unit/kafka/AssistantBankingReaderRequestedConsumerTest.kt`

Acceptance criteria:
- Event includes `job_id`, `callback_context`, parse output/error metadata.

### BKL-012 - Add banking consumer for assistant failed topic (IN_PROGRESS)
Service: `banking`
Depends on: `BKL-011`

Scope:
- Consume failed events and mark import job `FAILED`.
- Persist structured failure reason in `errorMessage/errorDetails`.

Deliverables:
- Consumer method + tests.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/PdfImportStatusConsumer.kt`
    - consumes `assistant.banking-pdf-reader.failed`
    - marks PDF import job `FAILED` with summary + structured details
  - `service/kotlin/banking/src/main/kotlin/.../domain/pdfimport/PdfImportService.kt`
    - `markFailed(..., errorDetails)` support
  - `service/kotlin/banking/src/main/kotlin/.../domain/pdfimport/PdfImportJob.kt`
    - adds `errorDetails` JSON field
  - `service/kotlin/banking/src/main/kotlin/.../model/pdfimportjob/PdfImportJobEntity.kt`
    - persists `error_details` JSONB
  - `service/kotlin/banking/src/main/resources/db/migration/V0_19__add_pdf_import_error_details.sql`
  - Tests:
    - `.../domain/pdfimport/PdfImportServiceTest.kt`
    - `.../integration/PdfImportWorkflowIT.kt`

Acceptance criteria:
- Failed LLM parse cannot remain stuck in `LLM_PROCESSING`.

---

## M4 - Excel Unknown -> LLM Fallback (No Generic Reader)

### BKL-013 - Add Excel extractor component (DONE)
Service: `banking`
Depends on: `BKL-004`

Scope:
- Add Apache POI based extraction:
- workbook metadata
- sheet names
- tabular/text snapshots per sheet
- output size guardrails

Deliverables:
- `ExcelExtractionService` + fixtures + tests.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/ExcelExtractionService.kt`
    - Apache POI-based extraction for `.xlsx` and `.xls`
    - workbook metadata + sheet names + per-sheet row snapshots
    - deterministic markdown-like text snapshot generation
    - guardrails for sheets/rows/columns/cell length/output length
  - `service/kotlin/banking/src/test/kotlin/.../domain/import/excel/ExcelExtractionServiceTest.kt`
    - `.xlsx` extraction deterministic behavior
    - `.xls` extraction support
    - guardrail enforcement coverage
  - `service/kotlin/banking/build.gradle.kts`
    - adds `org.apache.poi:poi-ooxml`

Acceptance criteria:
- Handles `.xlsx` and `.xls`.
- Extraction produces deterministic text/structure payload.

### BKL-014 - Add known Excel deterministic readers (DONE)
Service: `banking`
Depends on: `BKL-013`

Scope:
- For known Excel fingerprints, route to specific deterministic readers.

Deliverables:
- Reader interface and first concrete reader.
- Router tests.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/reader/DeterministicExcelReader.kt`
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/reader/DeterministicExcelReaderRegistry.kt`
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/reader/DeterministicExcelReaderRouter.kt`
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/reader/TabularBankStatementExcelReader.kt`
    - first deterministic reader for profile `excel-bank-statement-tabular-v1`
    - parses tabular sheet snapshots into canonical `TRANSACTION` records
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/excel/reader/ExcelDeterministicRoutingService.kt`
    - routes known Excel fingerprints to deterministic readers
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/fingerprint/ExcelFingerprintClassifier.kt`
    - known fingerprint detection for bank-statement tabular layouts
  - Tests:
    - `.../domain/import/excel/reader/DeterministicExcelReaderRegistryTest.kt`
    - `.../domain/import/excel/reader/DeterministicExcelReaderRouterTest.kt`
    - `.../domain/import/excel/reader/TabularBankStatementExcelReaderTest.kt`
    - `.../domain/import/excel/reader/ExcelDeterministicRoutingServiceTest.kt`
    - updated: `.../domain/import/fingerprint/ExcelFingerprintClassifierTest.kt`
    - updated: `.../domain/import/fingerprint/FingerprintClassifierRegistryTest.kt`

Acceptance criteria:
- Known fingerprint never goes to assistant path.

### BKL-015 - Route unknown Excel directly to assistant request topic (DONE)
Service: `banking`
Depends on: `BKL-013`, `BKL-005`

Scope:
- Build text/structure payload from Excel extraction.
- Publish assistant request.

Deliverables:
- Routing integration in import parser pipeline.
- Integration tests.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/parser/ImportParserStrategy.kt`
    - `ImportParseOutcome` now supports `assistantRequest` payload
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/parser/ExcelImportParserStrategy.kt`
    - extracts workbook via `ExcelExtractionService`
    - routes known fingerprints via deterministic Excel reader path
    - emits assistant request payload for unknown Excel layouts
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt`
    - wires Excel parser strategy into import router
    - publishes `assistant.banking-reader.requested` for unknown Excel
    - keeps import job in async processing path after request publish
  - Tests:
    - `.../infra/kafka/parser/ExcelImportParserStrategyTest.kt`
    - updated `.../infra/kafka/parser/ImportParserRouterTest.kt`
    - updated `.../infra/kafka/ImportFileConsumerIT.kt`
      - verifies unknown Excel publishes assistant request and transitions to async processing state

Acceptance criteria:
- Unknown Excel does not use generic deterministic scoring.
- Job transitions to async processing path correctly.

---

## M5 - OFX Intent vs Destination Compatibility

### BKL-016 - Extend OFX parser to classify statement intent (DONE)
Service: `banking`

Scope:
- Detect OFX intent:
- `BANK_STATEMENT`
- `CREDIT_CARD_STATEMENT`
- Parse relevant nodes (`BANKMSGSRSV1`, `CREDITCARDMSGSRSV1`, etc).

Deliverables:
- Parser extension and tests for both intents.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/parser/OFXParser.kt`
    - adds `parseWithIntent(...)` with `OfxParseResult`
    - adds statement-intent classification from OFX message-set nodes (`BANKMSGSRSV1`, `CREDITCARDMSGSRSV1`)
    - keeps `parse(...)` backward-compatible (rows-only adapter)
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/parser/OfxImportParserStrategy.kt`
    - enriches canonical metadata with `ofx_message_set_intent` and `ofx_document_format`
  - Fixtures:
    - `service/kotlin/banking/src/test/resources/fixtures/ofx/ofx_creditcard_sgml_sample.ofx`
    - `service/kotlin/banking/src/test/resources/fixtures/ofx/ofx_creditcard_xml_sample.ofx`
  - Tests:
    - `service/kotlin/banking/src/test/kotlin/.../domain/import/parser/OFXParserTest.kt`
      - validates SGML/XML intent classification for both bank and credit-card statements
    - `service/kotlin/banking/src/test/kotlin/.../infra/kafka/parser/ImportParserRouterTest.kt`
      - validates OFX intent/document-format metadata propagation

Acceptance criteria:
- Intent classification works on SGML and XML OFX fixtures.

### BKL-017 - Enforce destination compatibility rule in import pipeline (DONE)
Service: `banking`
Depends on: `BKL-016`

Scope:
- Before processing rows, validate destination financial account subtype against OFX intent.
- Reject mismatches with business-rule error.

Deliverables:
- Validation service + error mapping.
- Integration tests for all 4 matrix scenarios.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/ofx/OfxDestinationCompatibilityService.kt`
    - validates OFX intent vs destination account subtype before row processing
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt`
    - applies OFX compatibility check for OFX parse outcomes
    - enriches failed `error_details` with domain `errorCode`/parameters
  - `service/kotlin/banking/src/main/kotlin/.../error/BankingErrorCode.kt`
    - adds `IMPORT_OFX_DESTINATION_MISMATCH`
  - Tests:
    - `service/kotlin/banking/src/test/kotlin/.../domain/import/ofx/OfxDestinationCompatibilityServiceTest.kt`
      - unit matrix + unknown intent behavior
    - `service/kotlin/banking/src/test/kotlin/.../infra/kafka/ImportFileConsumerIT.kt`
      - integration matrix:
        - BANK intent -> BANK destination (accept)
        - BANK intent -> CREDIT_CARD destination (reject)
        - CREDIT_CARD intent -> CREDIT_CARD destination (accept)
        - CREDIT_CARD intent -> BANK destination (reject)

Acceptance criteria:
- Mismatch case inserts zero transactions and marks job rejected/failed with explicit reason.

---

## M6 - Validation Gate And Review Behavior

### BKL-018 - Implement centralized confidence and validation gate (DONE)
Service: `banking`
Depends on: `BKL-002`, `BKL-006`, `BKL-010`

Scope:
- Single gate to decide:
- auto-commit eligible
- awaiting review
- failed
- Apply deterministic post-validation rules for LLM outputs.

Deliverables:
- `ImportValidationGate` service + tests.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportValidationGate.kt`
    - centralized pre-commit gating policy for reader quality
    - centralized terminal status decision from import processing counters
    - LLM guardrails:
      - invalid rows -> `FAILED`
      - low confidence -> `REVIEW_REQUIRED`
      - warnings present -> `REVIEW_REQUIRED`
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportReaderStatusConsumer.kt`
    - uses gate for LLM post-validation before processing
    - blocks auto-commit when gate returns `REVIEW_REQUIRED`/`FAILED`
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt`
    - uses centralized gate for terminal status decision
  - Tests:
    - `service/kotlin/banking/src/test/kotlin/.../domain/import/ImportValidationGateTest.kt`
    - `service/kotlin/banking/src/test/kotlin/.../infra/kafka/ImportReaderStatusConsumerTest.kt`
      - low-confidence and invalid-row LLM scenarios

Acceptance criteria:
- Invalid required fields/date/amount never auto-commit.
- Low-confidence LLM output is routed to review/failure per policy.

### BKL-019 - Harmonize review payload structure across deterministic and LLM paths (DONE)
Service: `banking`
Depends on: `BKL-018`

Scope:
- Ensure UI receives consistent `review_payload` shape independent of parser type.

Deliverables:
- Payload mapper + compatibility tests with current GraphQL DTOs.
- Draft delivered:
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportReviewPayloadMapper.kt`
    - canonical review payload envelope:
      - `schema_id`
      - `schema_version`
      - `duplicate_candidates`
      - `gate`
    - backward compatibility for legacy array payloads
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt`
    - duplicate review payload now serialized through canonical mapper
  - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportReaderStatusConsumer.kt`
    - gate review payload now serialized through canonical mapper
    - duplicate review payload serialization aligned to canonical mapper
  - `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobService.kt`
    - `resolveImportDuplicate` now reads canonical envelope and legacy array payloads
  - `service/kotlin/banking/src/main/kotlin/.../graphql/resolver/ImportResolver.kt`
    - GraphQL duplicate candidate projection now reads canonical envelope and legacy payloads
  - Tests:
    - `service/kotlin/banking/src/test/kotlin/.../domain/import/ImportReviewPayloadMapperTest.kt`
    - `service/kotlin/banking/src/test/kotlin/.../graphql/resolver/ImportResolverTest.kt`
    - `service/kotlin/banking/src/test/kotlin/.../domain/import/ImportJobServiceTest.kt`
    - `service/kotlin/banking/src/test/kotlin/.../infra/kafka/ImportReaderStatusConsumerTest.kt`

Acceptance criteria:
- Frontend does not need parser-specific conditional branches.

---

## M7 - Observability, Reliability, Operations

### BKL-020 - Add metrics for routing and quality (TODO)
Service: `banking`, `assistant`

Scope:
- Counters and timers:
- deterministic hit rate by fingerprint
- unknown fallback rate
- assistant success/failure rate
- review-required rate
- parse latency by reader

Deliverables:
- Micrometer metrics + dashboard query notes.

Acceptance criteria:
- Metrics visible in Prometheus and queryable by topic/document type.

### BKL-021 - Add idempotency guards for assistant request publishing (DONE)
Service: `banking`
Depends on: `BKL-005`

Scope:
- Prevent duplicate assistant requests for same job stage.
- Safe retry behavior on transient failures.

Deliverables:
- Idempotency state marker and tests.
- Draft delivered:
  - DB migration:
    - `service/kotlin/banking/src/main/resources/db/migration/V0_20__add_import_assistant_request_published_at.sql`
      - adds `transaction_imports.assistant_request_published_at`
  - Domain/model/repository:
    - `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJob.kt`
    - `service/kotlin/banking/src/main/kotlin/.../model/importjob/TransactionImportEntity.kt`
    - `service/kotlin/banking/src/main/kotlin/.../model/importjob/TransactionImportMapper.kt`
    - `service/kotlin/banking/src/main/kotlin/.../domain/import/ImportJobRepository.kt`
    - `service/kotlin/banking/src/main/kotlin/.../repo/TransactionImportEntityRepository.kt`
    - `service/kotlin/banking/src/main/kotlin/.../repo/DefaultImportJobRepository.kt`
      - adds one-shot `markAssistantRequestPublished(jobId)` marker API
  - Runtime guard:
    - `service/kotlin/banking/src/main/kotlin/.../infra/kafka/ImportFileConsumer.kt`
      - skips replay when marker already set and job is `PROCESSING`
      - marks assistant publish once after successful send
      - classifies assistant publish failures as retryable (offset uncommitted)
  - Tests:
    - `service/kotlin/banking/src/test/kotlin/.../infra/kafka/ImportFileConsumerIT.kt`
      - replay of same unknown-excel event does not publish duplicate assistant request
    - `service/kotlin/banking/src/test/kotlin/.../integration/TransactionImportRepositoryIT.kt`
      - marker set-once behavior

Acceptance criteria:
- Reprocessing same extracted event does not create duplicate assistant work.

### BKL-022 - Add dead-letter and retry policy documentation and runbook (DONE)
Service: `banking`, `assistant`, `pdf-extractor`

Scope:
- Define retry counts, backoff, DLQ handling, replay procedure.

Deliverables:
- Operational runbook markdown under docs.
- Draft delivered:
  - `docs/08-workflows/banking-pdf-import-retry-dlq-runbook.md`
    - retry counts and backoff matrix per service
    - DLQ handling and ownership (`pdf-extractor.dlq`, `assistant...failed`, `banking...DLT`)
    - replay procedure keyed by `job_id` with SQL verification and controlled marker reset
  - Link added:
    - `docs/06-contracts/kafka/banking-pdf-import/README.md`

Acceptance criteria:
- On-call can recover a failed import without code changes.

---

## M8 - End-To-End Tests And Rollout

### BKL-023 - Add cross-service e2e test matrix for new routes (TODO)
Service: `banking`, `assistant`, `pdf-extractor`

Scope:
- Scenarios:
- known PDF fingerprint -> deterministic reader
- unknown PDF -> assistant done
- unknown PDF -> assistant failed
- known Excel fingerprint -> deterministic reader
- unknown Excel -> assistant done
- OFX intent mismatch cases

Deliverables:
- Integration/e2e suites and fixture set.

Acceptance criteria:
- All scenarios are reproducible in CI with stable assertions.

### BKL-024 - Introduce feature flags and staged rollout plan (TODO)
Service: `banking`, `assistant`

Scope:
- Feature flags:
- `import.routing.v2.enabled`
- `import.assistant.kafka.enabled`
- `import.excel.llm_fallback.enabled`
- staged enablement by environment.

Deliverables:
- Config flags + rollout checklist.

Acceptance criteria:
- Can disable new paths instantly without rollback.

### BKL-025 - Production readiness review and go-live checklist (TODO)
Service: all
Depends on: `BKL-023`, `BKL-024`

Scope:
- Security review, cost guardrails, SLO/SLA, alerting thresholds, runbook verification.

Deliverables:
- Signed release checklist.

Acceptance criteria:
- Stakeholders approve go-live with no blocking risks.

---

## Suggested Execution Order (Minimal Critical Path)

1. `BKL-001` `BKL-002`
2. `BKL-003` `BKL-004` `BKL-005`
3. `BKL-008` `BKL-009` `BKL-010` `BKL-011` `BKL-012`
4. `BKL-006` `BKL-007`
5. `BKL-013` `BKL-014` `BKL-015`
6. `BKL-016` `BKL-017`
7. `BKL-018` `BKL-019`
8. `BKL-020` `BKL-021` `BKL-022`
9. `BKL-023` `BKL-024` `BKL-025`
