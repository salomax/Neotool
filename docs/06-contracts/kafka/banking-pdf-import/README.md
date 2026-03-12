# Banking PDF Import Kafka Contracts

Versioned contracts for the PDF import workflow spanning `pdf-extractor`, `banking`, and `assistant` services.

Operational retry/DLQ/replay guide:
- `docs/08-workflows/banking-pdf-import-retry-dlq-runbook.md`

## Contract status

- `schema_version = 1.0`
- Naming convention: `snake_case`
- `callback_context` is mandatory in all events
- Message key convention: `job_id`

## Topics and schema files

- `pdf-extractor.extracted`
  - schema: `schemas/pdf-extractor.extracted.v1.schema.json`
  - example: `examples/pdf-extractor.extracted.v1.example.json`
- `assistant.banking-reader.requested`
  - schema: `schemas/assistant.banking-reader.requested.v1.schema.json`
  - example: `examples/assistant.banking-reader.requested.v1.example.json`
- `assistant.banking-pdf-reader.done`
  - schema: `schemas/assistant.banking-pdf-reader.done.v1.schema.json`
  - example: `examples/assistant.banking-pdf-reader.done.v1.example.json`
- `assistant.banking-pdf-reader.failed`
  - schema: `schemas/assistant.banking-pdf-reader.failed.v1.schema.json`
  - example: `examples/assistant.banking-pdf-reader.failed.v1.example.json`

## Common field: `callback_context`

All contracts require:

- `callback_context.job_id` (UUID)
- `callback_context.account_id` (UUID)
- `callback_context.user_id` (UUID)

Additional fields are allowed to preserve forward compatibility and pass-through behavior.

## Validation notes

- For `pdf-extractor.extracted`, `extraction_metadata` keeps `additionalProperties: true` to allow extractor metadata evolution.
- For `assistant.banking-pdf-reader.done`, `llm_output` is constrained to the currently agreed core fields and allows additive extension.
- For `assistant.banking-pdf-reader.failed`, `error.code` is controlled by enum to standardize retry and operational handling.
