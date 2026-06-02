# Asset Brand Job

Go worker that runs three one-off brand hydration flows:

- investment asset brand matching into `financial_data.asset_brand` and `financial_data.investment_asset_brand_match` using Brandfetch by ISIN
- banking issuer logo hydration into `banking.issuers.logo_key`
- banking merchant logo hydration into `banking.merchant_logo_cache.asset_id`

This job is intentionally separate from the `financialdata` service startup path. It is meant to run on demand or as a controlled batch so service replicas do not compete over the same external API calls.

---

## What it does

```text
Asset brand hydrator
  ├── marks import_job_control(job_name = 'asset_brand_brandfetch') as RUNNING
  ├── loads pending B3 assets with ISIN and no existing brand match
  ├── for each asset
  │     ├── uses asset.isin_code as the provider query
  │     ├── calls Brandfetch CDN in order: default, logo, symbol
  │     ├── keeps the first image that returns a non-empty body
  │     ├── marks the match AUTO_APPROVED for successful ISIN lookups
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

Merchant logo hydrator
  ├── marks import_job_control(job_name = 'banking_merchant_logo_dev') as RUNNING
  ├── loads distinct banking.merchants.domain with no row in banking.merchant_logo_cache
  ├── fetches PNG bytes from img.logo.dev/{domain}
  ├── uploads them through assets-service create/upload/confirm
  ├── upserts banking.merchant_logo_cache(domain, asset_id, fetched_at)
  └── marks the job SUCCESS or FAILED
```

### Assets processed

Only B3 rows from `financial_data.investment_asset` where:

- `source = 'B3'`
- `ticker IS NOT NULL`
- `asset_type IN ('EQUITY', 'BDR', 'FII')`
- `isin_code IS NOT NULL AND btrim(isin_code) <> ''`
- there is no row yet in `financial_data.investment_asset_brand_match`

### Matching policy

- Lookup uses `asset.isin_code`.
- Attempt order is: default image, `logo`, then `symbol`.
- Successful ISIN matches are stored as `AUTO_APPROVED`.
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
| `BRANDFETCH_BASE_URL` | `https://cdn.brandfetch.io` | Base URL for ISIN logo lookup |
| `BRANDFETCH_CLIENT_ID` | *(required for asset-brand hydrator)* | Brandfetch client id |
| `BRANDFETCH_TIMEOUT_SECONDS` | `15` | HTTP timeout per Brandfetch request |
| `LOGO_DEV_IMAGE_BASE_URL` | `https://img.logo.dev` | Base URL for direct issuer logo fetches |
| `LOGO_DEV_API_KEY` | *(required for issuer/merchant hydrators)* | API key for `logo.dev` image fetches |
| `LOGO_DEV_TIMEOUT_SECONDS` | `15` | HTTP timeout per logo.dev request |
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
| `RUN_MERCHANT_LOGO_HYDRATOR` | `true` | Enables merchant logo cache hydration flow |
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

- exact-ISIN confidence resolution
- end-to-end processor flow with fake repositories and fake Brandfetch client
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
WHERE job_name IN ('asset_brand_brandfetch', 'banking_issuer_logo_dev', 'banking_merchant_logo_dev');

-- Issuers with hydrated logos
SELECT id, name, domain, logo_key
FROM banking.issuers
WHERE logo_key IS NOT NULL
ORDER BY updated_at DESC
LIMIT 20;

-- Merchant logo cache rows
SELECT domain, asset_id, fetched_at
FROM banking.merchant_logo_cache
ORDER BY fetched_at DESC
LIMIT 20;
```

---

## Schema dependency

Requires these migrations to have been applied:

- `V1_14__create_investment_asset_tables.sql`
- `V1_16__create_asset_brand_tables.sql`
- banking issuer migrations in `service/kotlin/banking/src/main/resources/db/migration`
