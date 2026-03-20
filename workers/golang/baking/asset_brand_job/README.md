# Asset Brand Job

Go worker that runs two one-off `logo.dev` hydration flows:

- investment asset brand matching into `financial_data.asset_brand` and `financial_data.investment_asset_brand_match`
- banking issuer logo hydration into `banking.issuers.logo_key`

This job is intentionally separate from the `financialdata` service startup path. It is meant to run on demand or as a controlled batch so service replicas do not compete over the same external API calls.

---

## What it does

```text
Asset brand hydrator
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

Issuer logo hydrator
  ├── marks import_job_control(job_name = 'banking_issuer_logo_dev') as RUNNING
  ├── loads banking.issuers where logo_key IS NULL and domain IS NOT NULL
  ├── fetches PNG bytes from img.logo.dev/{domain}
  ├── uploads them through assets-service create/upload/confirm
  ├── writes the returned asset id back to banking.issuers.logo_key
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

### Issuers processed

Issuer hydration processes rows from `banking.issuers` where:

- `logo_key IS NULL`
- `domain IS NOT NULL`

Uploaded assets use namespace `institution-logos` by default and filenames like `issuers/<issuer-id>.png`.

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
| `LOGO_DEV_IMAGE_BASE_URL` | `https://img.logo.dev` | Base URL for direct issuer logo fetches |
| `LOGO_DEV_TOKEN` | *(required)* | Bearer token for `logo.dev` |
| `LOGO_DEV_TIMEOUT_SECONDS` | `15` | HTTP timeout per request |
| `SECURITY_SERVICE_URL` | `http://localhost:8080` | Security service base URL for OAuth client credentials |
| `SECURITY_SERVICE_ID` | *(optional)* | Service client id; enables issuer logo uploads when set with secret |
| `SECURITY_SERVICE_SECRET` | *(optional)* | Service client secret; enables issuer logo uploads when set with id |
| `SECURITY_TIMEOUT_SECONDS` | `10` | Security token request timeout |
| `ASSETS_APOLLO_ROUTER_URL` | `http://localhost:4000/graphql` | Apollo Router / GraphQL endpoint for assets uploads |
| `ASSETS_NAMESPACE` | `institution-logos` | Namespace used for issuer logo assets |
| `ASSETS_GRAPHQL_TIMEOUT_SECONDS` | `30` | GraphQL timeout |
| `ASSETS_UPLOAD_TIMEOUT_SECONDS` | `60` | Presigned upload timeout |
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
- issuer logo hydration flow with fake logo fetch and asset upload dependencies

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
WHERE job_name IN ('asset_brand_logo_dev', 'banking_issuer_logo_dev');

-- Issuers with hydrated logos
SELECT id, name, domain, logo_key
FROM banking.issuers
WHERE logo_key IS NOT NULL
ORDER BY updated_at DESC
LIMIT 20;
```

---

## Schema dependency

Requires these migrations to have been applied:

- `V1_14__create_investment_asset_tables.sql`
- `V1_16__create_asset_brand_tables.sql`
- banking issuer migrations in `service/kotlin/banking/src/main/resources/db/migration`
