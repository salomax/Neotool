# Brazilian Economic Indicators - Async Job Design

**Type**: Workflow Design  
**Module**: financial_data  
**Status**: Draft

## Overview

Single Go-based async job with **goroutine concurrency**, aligned with existing workflows in
`invistus/workflow/golang/financial_data`. The job is **metadata-driven**, idempotent, and
records execution state in `economic_indicator_load_control`.

---

## Execution Model

**One job, multi-threaded**:
- A single cronjob binary runs periodically.
- It dispatches multiple indicator-variation tasks concurrently via worker pool.
- Each task is isolated (economic_indicator_variation + period range).

**Idempotency**:
- Writes are upserts on `(indicator_variation_id, reference_date)`.
- Re-runs are safe and can update revised values.

---

## Control Table

**Table**: `financial_data.economic_indicator_load_control`

Purpose:
- Track what already ran per indicator variation
- Store ranges and cursors for chunked loads
- Support retries and observability

**Key fields**:
- `indicator_variation_id`
- `run_type` (FULL, INCREMENTAL, BACKFILL)
- `status` (PENDING, RUNNING, SUCCESS, FAILED, SKIPPED)
- `scheduled_at`, `started_at`, `finished_at`
- `last_reference_date`, `last_publication_date`
- `cursor` (JSONB with pagination or period range)
- `error_message`, `attempts`

---

## Suggested Packages (Go)

```
workflow/golang/financial_data/indicators/
├── cmd/cronjob/              # entrypoint
├── internal/application/     # job runner + orchestration
├── internal/processor/       # worker pool + fetch/normalize/save
├── internal/source/          # clients: SIDRA, SGS, ComexStat, etc.
├── internal/repository/      # DB access: indicators, values, load_control
└── internal/domain/          # structs + enums
```

Leverage patterns from `bacen_ifdata`:
- ETL runner with health server
- worker pool and error handling
- config via env

---

## Core Flow (High-Level)

1. **Discover tasks**:
   - Read active `economic_indicator_variation` rows
   - Decide run_type and period range (incremental vs backfill)
   - Insert a row into `economic_indicator_load_control` with `PENDING`

2. **Dispatch workers**:
   - Fan-out tasks into a buffered channel
   - N workers (`MAX_WORKERS`) consume tasks

3. **Fetch**:
   - Build source request (SIDRA/SGS/etc.)
   - Apply rate limit per source

4. **Normalize**:
   - Parse reference date (period)
   - Parse value to numeric
   - Set `publication_date` when known

5. **Upsert**:
   - Insert/Update `economic_indicator_value`
   - Store `source_meta` (URL, params, retrieved_at)

6. **Finalize**:
   - Update load_control row to SUCCESS/FAILED

---

## Scheduling Strategy

- **Monthly**: IPCA/IPCA-15/INPC, PNAD unemployment, income
- **Daily**: Selic effective, reserves (if included)
- **Annual**: PIB value
- **Event**: Selic target (Copom)

