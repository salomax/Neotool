# CVM Funds Job

Go worker that imports Brazilian investment fund catalog and daily NAV (Net Asset Value) prices from [CVM's open data portal](https://dados.cvm.gov.br) into the `financial_data` schema.

## What it does

The job runs in **two sequential phases**:

### Phase 1 — Fund Catalog (`cad_fi.csv`)

Streams `cad_fi.csv` (~50k rows) from CVM and upserts each fund into `financial_data.investment_asset`.

```
CVM (HTTP stream)
  └── CatalogIngester          — streams CSV row-by-row; never loads the full file
        ├── pre-filter          — skips records whose SHA-256 hash hasn't changed
        └── WorkerPool          — N workers upsert chunks of 500 rows concurrently
              └── CatalogChunkTask → UpsertFundBatch (pgx.Batch)
```

Hash-based idempotency means re-running the catalog job is cheap: only changed records touch the database.

### Phase 2 — Daily NAV (`inf_diario_fi_YYYYMM.zip`)

For each month in the processing window, downloads the monthly ZIP (~15 MB compressed, ~60 MB extracted, ~1.5 M rows) and upserts each row into `financial_data.asset_daily_price`.

```
NAVDispatcher
  ├── loads fund catalog (cnpj → UUID) once, shared read-only across all tasks
  ├── builds month list (backfill or incremental — see below)
  └── WorkerPool          — M workers each own one month concurrently
        └── NAVMonthTask
              ├── downloads ZIP → temp file (archive/zip needs seekable io.ReaderAt)
              ├── streams CSV; accumulates rows into a chunk
              ├── chunk[:0] reuse — backing array never reallocated inside the loop
              └── UpsertNavBatch (pgx.Batch) every 500 rows
```

### Backfill vs incremental

The `import_job_control` table tracks `last_data_date` per job name:

| State | Behavior |
|-------|----------|
| No row for `cvm_nav` | **Backfill**: processes `CVM_NAV_BACKFILL_YEARS × 12` months (default: 5 years = 60 months) |
| Row exists | **Incremental**: processes from `last_data_date`'s month to last month |

---

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | DB host |
| `POSTGRES_PORT` | `5432` | DB port |
| `POSTGRES_DB` | `neotool_db` | Database name |
| `POSTGRES_USER` | `neotool` | DB user |
| `POSTGRES_PASSWORD` | *(required)* | DB password |
| `POSTGRES_POOL_MIN` | `2` | Min pool connections |
| `POSTGRES_POOL_MAX` | `10` | Max pool connections |
| `POSTGRES_SSL_MODE` | `prefer` | TLS mode |
| `CVM_BASE_URL` | `https://dados.cvm.gov.br` | CVM portal base URL |
| `CVM_TIMEOUT_SECONDS` | `120` | HTTP timeout per request |
| `CVM_CATALOG_WORKERS` | `4` | Goroutines upsering catalog chunks |
| `CVM_CATALOG_QUEUE_BUFFER` | `2` | Task queue buffer (× workers) |
| `CVM_CATALOG_CHUNK_SIZE` | `500` | Rows per catalog DB batch |
| `CVM_NAV_WORKERS` | `2` | Goroutines processing month ZIPs concurrently |
| `CVM_NAV_QUEUE_BUFFER` | `2` | Task queue buffer (× workers) |
| `CVM_NAV_CHUNK_SIZE` | `500` | Rows per NAV DB batch |
| `CVM_NAV_BACKFILL_YEARS` | `5` | Years of history on first run |
| `HEALTH_CHECK_ENABLED` | `true` | Expose `/health`, `/live`, `/ready` |
| `HEALTH_CHECK_PORT` | `8080` | Health check port |
| `CVM_LOG_CONFIG` | `false` | Log resolved config values at startup |

---

## Running locally

```bash
# Build
make build

# Run (requires DB)
POSTGRES_PASSWORD=secret make run

# Or with docker
make docker-build
docker run \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_HOST=host.docker.internal \
  cvm-funds-job:latest
```

---

## Testing

### Unit tests

```bash
make test
# or with coverage report:
make test-coverage
open coverage.html
```

The test suite covers:

| Package | What's tested |
|---------|--------------|
| `internal/shared` | `NormalizeCNPJ`, `ColumnIndex`/`GetField`, `ComputeFundHash`, `ComputeNavHash`, `Retry` with exponential backoff and jitter |
| `internal/domain` | `YearMonth.String`, `AddMonths`, `After`, `LastDay` |
| `internal/processor` | `buildMonthList` (first run / incremental / future date guard), `resolveAssetType`, `parseRow` (valid / missing CNPJ / malformed date) |

### Integration test (manual)

Requires a running Postgres with the `financial_data` schema applied (migration `V1_14`).

```bash
# 1. Seed schema
psql $DATABASE_URL -f service/kotlin/financialdata/src/main/resources/db/migration/V1_14__create_investment_asset_tables.sql

# 2. Run catalog phase only (quick smoke test — ~30 s)
CVM_NAV_WORKERS=0 \
CVM_NAV_BACKFILL_YEARS=0 \
POSTGRES_PASSWORD=secret \
./bin/cronjob

# 3. Full run with 1-year backfill
CVM_NAV_BACKFILL_YEARS=1 \
POSTGRES_PASSWORD=secret \
./bin/cronjob
```

Expected output (structured JSON, key fields):

```json
{"level":"INFO","msg":"phase 1: catalog import starting"}
{"level":"INFO","msg":"catalog ingester: existing hashes loaded","count":0}
{"level":"INFO","msg":"catalog ingester: streaming complete","total_rows":52341,"submitted":52341,"skipped_unchanged":0}
{"level":"INFO","msg":"phase 1: catalog import complete","total_rows":52341,"submitted":52341,"skipped_unchanged":0}
{"level":"INFO","msg":"phase 2: NAV import starting"}
{"level":"INFO","msg":"nav dispatcher: fund catalog loaded","funds":52341}
{"level":"INFO","msg":"nav dispatcher: submitting months","run_type":"BACKFILL","months":12,"first":"202402","last":"202501"}
{"level":"INFO","msg":"nav task completed","month":"202402","total_rows":1487203,"inserted":1487203,"unchanged":0}
...
{"level":"INFO","msg":"cvm-funds-job finished successfully"}
```

On a second run all catalog rows are skipped (`skipped_unchanged = 52341`) and NAV rows show `inserted=0, unchanged=N`.

### Verifying results in DB

```sql
-- Fund count by asset type
SELECT asset_type, COUNT(*)
FROM financial_data.investment_asset
WHERE source = 'CVM'
GROUP BY asset_type ORDER BY 2 DESC;

-- Daily prices for a specific fund
SELECT price_date, close_price, volume
FROM financial_data.asset_daily_price dp
JOIN financial_data.investment_asset a ON a.id = dp.asset_id
WHERE a.fund_cnpj = '11328519000100'
ORDER BY price_date DESC
LIMIT 10;

-- Job control state
SELECT job_name, last_data_date, status, last_success_at
FROM financial_data.import_job_control;
```

---

## Project layout

```
cvm_funds_job/
├── cmd/
│   └── cronjob/main.go          # Entrypoint: wires config → pool → phases
├── internal/
│   ├── application/
│   │   └── config.go            # Env-var config with validation
│   ├── domain/
│   │   ├── constants.go         # SourceCVM, JobNameCatalog, JobNameNAV
│   │   ├── models.go            # FundRecord, NavUpsertParam, InvestmentAsset
│   │   └── yearmonth.go         # YearMonth type (YYYYMM, AddMonths, After, LastDay)
│   ├── infrastructure/
│   │   ├── database/
│   │   │   ├── postgres.go          # pgxpool with startup retry
│   │   │   ├── asset_repository.go  # FindAllFundHashes, UpsertFundBatch
│   │   │   ├── nav_repository.go    # UpsertNavBatch (inserted/unchanged counts)
│   │   │   └── job_control_repository.go
│   │   └── http/
│   │       └── cvm_client.go    # StreamFundCatalog, DownloadNavToTempFile (with retry)
│   ├── processor/
│   │   ├── catalog_ingester.go  # Producer: streams CSV, submits CatalogChunkTasks
│   │   ├── catalog_chunk_task.go
│   │   ├── nav_dispatcher.go    # Builds month list, submits NAVMonthTasks
│   │   └── nav_month_task.go    # Downloads ZIP, streams CSV, commits chunks
│   └── shared/
│       ├── cnpj.go              # NormalizeCNPJ
│       ├── csvutil.go           # ColumnIndex, GetField
│       ├── hash.go              # ComputeFundHash, ComputeNavHash (pipe-delimited)
│       └── retry.go             # Retry with exponential backoff + jitter
├── Dockerfile
├── Makefile
└── go.mod
```

---

## Idempotency

Every write is guarded at two levels:

1. **In-memory pre-filter** (catalog only): `existingHashes` map loaded at startup; records with matching hash are skipped before submission to the worker pool.
2. **SQL guard**: `ON CONFLICT ... DO UPDATE ... WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash` — the DB row is only written if something actually changed.

Re-running the job any number of times is safe and cheap.

## Memory design

| Concern | Approach |
|---------|----------|
| Catalog CSV (streaming) | HTTP body piped directly to `csv.Reader`; `ReuseRecord=true` avoids per-row `[]string` allocation |
| NAV ZIP (~15 MB compressed) | Written to `os.CreateTemp` — `archive/zip` needs seekable `io.ReaderAt`, not a stream |
| NAV CSV chunk | `chunk[:0]` after each `pgx.Batch` commit — backing array reused, no re-allocation in hot loop |
| Catalog chunk ownership | After `pool.Submit(task)`, ingester does `chunk = make(...)` — old array exclusively owned by task goroutine; `defer t.records = nil` releases it on task exit |
