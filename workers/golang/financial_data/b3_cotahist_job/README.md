# B3 COTAHIST Job

Go worker that imports daily equity, FII, and options prices from [B3's historical data series](http://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/series-historicas/) into the `financial_data` schema.

Data is sourced from the COTAHIST fixed-width flat files published by B3 (D+1 settlement). Annual files (`COTAHIST_A{YYYY}.ZIP`) are used for backfill; the current year's annual file is re-downloaded on each incremental run.

---

## What it does

```
Dispatcher
  ├── reads last_data_date from financial_data.import_job_control
  ├── builds year list (backfill: N past years; incremental: current year only)
  └── WorkerPool (N workers, default 3)
        └── CotahistTask (one per year)
              ├── downloads COTAHIST_A{YYYY}.ZIP → temp file
              ├── Pass 1 — catalog
              │     streams fixed-width records, collects unique assets by BDI code
              │     UpsertBatch → investment_asset (ON CONFLICT ticker)
              │     FindTickerIDs → builds ticker→UUID map in memory
              └── Pass 2 — prices
                    streams records again, looks up UUID from map
                    UpsertBatch → asset_daily_price in chunks of 500 rows
```

### Asset types imported

| BDI code | Asset type | DB `asset_type` |
|----------|-----------|----------------|
| `02` | Equity (lote padrão) | `EQUITY` |
| `12` | Fundo Imobiliário | `FII` |
| `78` | Opção de compra | `OPTION_CALL` |
| `79` | Opção de venda | `OPTION_PUT` |
| other | all other instruments | skipped |

Fractional lots (BDI `96`) and non-spot market types are skipped — they duplicate the same ticker at a different lot size.

### COTAHIST fixed-width fields used

| Field | Position | Description |
|-------|----------|-------------|
| `DATPRE` | 3–10 | Date `YYYYMMDD` |
| `CODBDI` | 11–12 | BDI code |
| `CODNEG` | 13–24 | Ticker (12 chars, space-padded) |
| `TPMERC` | 25–27 | Market type (`010` = spot) |
| `NOMRES` | 28–39 | Short name |
| `ESPECI` | 40–49 | Specification (ON, PN, CI, …) |
| `PREABE` | 57–69 | Open price (÷ 100) |
| `PREMAX` | 70–82 | High price (÷ 100) |
| `PREMED` | 83–95 | Weighted avg price (÷ 100) |
| `PREMIN` | 96–108 | Low price (÷ 100) |
| `TOTNEG` | 135–139 | Number of trades |
| `VOLTOT` | 158–175 | Financial volume in BRL (÷ 100) |
| `PREULT` | 176–188 | Close / last price (÷ 100) |
| `CODISI` | 230–241 | ISIN code |

### Backfill vs incremental

The `import_job_control` table tracks `last_data_date` per job name (`b3_cotahist`):

| State | Behavior |
|-------|----------|
| No row for `b3_cotahist` | **Backfill**: submits annual tasks for `(currentYear − B3_BACKFILL_YEARS)` through `currentYear` |
| `last_data_date` in a prior year | **Resume**: submits annual tasks from that year through `currentYear` |
| `last_data_date` in current year | **Incremental**: submits only the current year's annual file |

`MarkSuccess` is called with yesterday's date only after `pool.Wait()` confirms all tasks succeeded. A partial run (e.g. one year task fails) does not advance `last_data_date` — the failed year is retried next run, and already-inserted rows are skipped cheaply by content-hash comparison.

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
| `B3_BASE_URL` | `http://bvmf.bmfbovespa.com.br/InstDados/SerHist` | Base URL for COTAHIST downloads |
| `B3_TIMEOUT_SECONDS` | `300` | HTTP timeout per file (annual files can be ~100 MB) |
| `B3_WORKERS` | `3` | Concurrent year tasks in the worker pool |
| `B3_QUEUE_BUFFER` | `2` | Task queue buffer multiplier |
| `B3_CHUNK_SIZE` | `500` | Rows per `pgx.Batch` commit |
| `B3_BACKFILL_YEARS` | `5` | Years of history to load on first run |
| `HEALTH_CHECK_ENABLED` | `true` | Expose `/health`, `/live`, `/ready` |
| `HEALTH_CHECK_PORT` | `8080` | Health check port |
| `B3_LOG_CONFIG` | `false` | Log resolved config values at startup |

---

## Running locally

```bash
# requires a running Postgres with the financial_data schema applied
./run_local.sh
```

The script sources `.env.local`, builds `./bin/cronjob`, and runs it. See `.env.local` for defaults — `B3_BACKFILL_YEARS=1` keeps the first local run fast (~1 year of data).

For a full backfill from scratch, edit `.env.local` and set `B3_BACKFILL_YEARS=5` (or more).

To force a re-run from scratch without changing the years:

```sql
DELETE FROM financial_data.import_job_control WHERE job_name = 'b3_cotahist';
```

---

## Verifying results in DB

```sql
-- Asset count by type
SELECT asset_type, COUNT(*)
FROM financial_data.investment_asset
WHERE source = 'B3'
GROUP BY asset_type ORDER BY 2 DESC;

-- Recent prices for a specific ticker
SELECT price_date, open_price, high_price, low_price, close_price, volume, trades
FROM financial_data.asset_daily_price dp
JOIN financial_data.investment_asset a ON a.id = dp.asset_id
WHERE a.ticker = 'PETR4'
ORDER BY price_date DESC
LIMIT 10;

-- Job control state
SELECT job_name, last_data_date, status, last_success_at, error_message
FROM financial_data.import_job_control
WHERE job_name = 'b3_cotahist';

-- Idempotency check: run twice, second run should show 0 inserted
SELECT SUM(CASE WHEN created_at = updated_at THEN 0 ELSE 1 END) AS updated_rows
FROM financial_data.investment_asset
WHERE source = 'B3';
```

Expected log output (structured JSON, key fields):

```json
{"level":"INFO","msg":"b3-cotahist-job starting"}
{"level":"INFO","msg":"configuration loaded","workers":3,"chunk_size":500,"backfill_years":1}
{"level":"INFO","msg":"connected to PostgreSQL","max_conns":10,"min_conns":2}
{"level":"INFO","msg":"dispatcher: submitting year tasks","run_type":"BACKFILL","years":1,"first":2025,"last":2025}
{"level":"INFO","msg":"cotahist task starting","label":"2025"}
{"level":"INFO","msg":"cotahist task: unique assets collected","label":"2025","assets":847}
{"level":"INFO","msg":"cotahist task: ticker map loaded","label":"2025","mapped":847}
{"level":"INFO","msg":"cotahist task completed","label":"2025","total_lines":250000,"price_inserted":210000,"price_unchanged":0,"price_skipped":40000}
{"level":"INFO","msg":"b3-cotahist-job finished successfully"}
```

On a second run all rows show `price_unchanged=N, price_inserted=0`.

---

## Idempotency

Every write is guarded at two levels:

1. **Catalog SQL guard**: `ON CONFLICT (ticker) DO UPDATE ... WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash` — no write if ticker already exists with the same name/type/ISIN.
2. **Price SQL guard**: `ON CONFLICT (asset_id, price_date) DO UPDATE ... WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash` — no write if the OHLCV data hasn't changed.

The content hash covers: `assetType|especi|isinCode|name|ticker` for assets, and `ticker|date|open|high|low|close` for prices.

Re-running the job any number of times is safe and cheap.

---

## Project layout

```
b3_cotahist_job/
├── cmd/cronjob/main.go                  # Entrypoint: config → pool → dispatcher → MarkSuccess
├── internal/
│   ├── application/config.go            # Env-var config with validation
│   ├── domain/
│   │   ├── constants.go                 # BDI codes, asset types, lot sizes
│   │   ├── models.go                    # AssetRecord, PriceUpsertParam
│   │   └── parser.go                    # Fixed-width COTAHIST record parser
│   ├── infrastructure/
│   │   ├── database/
│   │   │   ├── postgres.go              # pgxpool with startup retry
│   │   │   ├── asset_repository.go      # UpsertBatch + FindTickerIDs
│   │   │   ├── price_repository.go      # UpsertBatch (inserted/unchanged counts)
│   │   │   └── job_control_repository.go
│   │   └── http/
│   │       └── b3_client.go             # DownloadAnnual/Daily + OpenTXTFromZip
│   ├── processor/
│   │   ├── dispatcher.go                # Builds year list, submits tasks, stores lastDate
│   │   └── task.go                      # CotahistTask: two-pass catalog + prices
│   └── shared/
│       ├── hash.go                      # ComputeAssetHash, ComputePriceHash
│       └── retry.go                     # Retry with exponential backoff + jitter
├── .env.local                           # Local config (gitignored)
├── run_local.sh                         # Build + run helper
└── go.mod
```

---

## Schema dependency

Requires migration `V1_14__create_investment_asset_tables.sql` to have been applied:

```sql
-- tables used:
financial_data.investment_asset       -- asset catalog (keyed on ticker for B3 assets)
financial_data.asset_daily_price      -- OHLCV prices (keyed on asset_id + price_date)
financial_data.import_job_control     -- job run state (keyed on job_name = 'b3_cotahist')
```
