# Asset Brand Job

Go worker that hydrates `financial_data.asset_brand` and `financial_data.investment_asset_brand_match` using [`logo.dev` brand search](https://logo.dev/).

This job is intentionally separate from the `financialdata` service startup path. It is meant to run on demand or as a controlled batch so service replicas do not compete over the same external API calls.

---

## What it does

```
Hydrator
  ├── marks import_job_control(job_name = 'asset_brand_logo_dev') as RUNNING
  ├── loads pending B3 assets with no existing brand match
  ├── for each asset
  │     ├── builds a search query from asset.name
  │     ├── calls GET /search on logo.dev
  │     ├── takes the first result only
  │     ├── computes confidence from normalized-name similarity
  │     └── upserts:
  │           asset_brand
  │           investment_asset_brand_match
  └── marks the job SUCCESS or FAILED
```

### Assets processed

Only B3 rows from `financial_data.investment_asset` where:

- `source = 'B3'`
- `ticker IS NOT NULL`
- `asset_type IN ('EQUITY', 'BDR', 'FII')`
- there is no row yet in `financial_data.investment_asset_brand_match`

### Matching policy

- Search uses cleaned `asset.name`, not the ticker.
- Only the first `logo.dev` result is considered in this first pass.
- `AUTO_APPROVED` is used only when confidence is above the configured threshold.
- Lower-confidence matches are stored as `PENDING` for manual review.
- No result means no row is written; the asset remains eligible for a later retry.

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
| `LOGO_DEV_BASE_URL` | `https://api.logo.dev` | Base URL for brand search |
| `LOGO_DEV_TOKEN` | *(required)* | Bearer token for `logo.dev` |
| `LOGO_DEV_TIMEOUT_SECONDS` | `15` | HTTP timeout per request |
| `ASSET_BRAND_BATCH_LIMIT` | `250` | Max assets processed per run |
| `ASSET_BRAND_THROTTLE_MS` | `250` | Delay between requests |
| `ASSET_BRAND_AUTO_APPROVE_THRESHOLD` | `0.93` | Confidence threshold for `AUTO_APPROVED` |
| `HEALTH_CHECK_ENABLED` | `true` | Expose `/health`, `/live`, `/ready` |
| `HEALTH_CHECK_PORT` | `8080` | Health check port |
| `ASSET_BRAND_LOG_CONFIG` | `false` | Log config values at startup |

---

## Running locally

```bash
./run_local.sh
```

The script sources `.env.local`, builds `./bin/cronjob`, and runs the worker.

---

## Testing

```bash
go test ./...
```

The current unit tests cover:

- query cleanup for share-class suffixes
- confidence scoring for strong vs weak matches
- end-to-end processor flow with fake repositories and fake `logo.dev` client

---

## Verifying results in DB

```sql
-- Brands created
SELECT id, normalized_name, display_name, domain, status, confidence
FROM financial_data.asset_brand
ORDER BY updated_at DESC
LIMIT 20;

-- Per-asset matches
SELECT ia.ticker, ia.name, m.status, m.match_confidence, b.display_name, b.domain
FROM financial_data.investment_asset_brand_match m
JOIN financial_data.investment_asset ia ON ia.id = m.investment_asset_id
JOIN financial_data.asset_brand b ON b.id = m.asset_brand_id
ORDER BY m.updated_at DESC
LIMIT 20;

-- Job control state
SELECT job_name, status, last_run_at, last_success_at, error_message
FROM financial_data.import_job_control
WHERE job_name = 'asset_brand_logo_dev';
```

---

## Schema dependency

Requires these migrations to have been applied:

- `V1_14__create_investment_asset_tables.sql`
- `V1_16__create_asset_brand_tables.sql`
