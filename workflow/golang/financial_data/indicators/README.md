# Indicators Job (financial_data)

This job ingests Brazilian economic indicators into the `financial_data` schema. It fetches data from official sources, normalizes values, and upserts into `indicator_value`, tracking each run in `indicator_load_control`. It is designed for both **developers** (implementation and ops) and **analysts** (what data is loaded and how to validate it).

## What this job does (high level)

- **Collects** series from official sources (IBGE/SIDRA, BCB/SGS, MDIC/ComexStat).
- **Normalizes** raw values to a consistent schema and time reference (monthly, daily, annual).
- **Stores** values in `indicator_value` and run metadata in `indicator_load_control`.
- **Supports** incremental runs based on the last loaded date to reduce API usage.
- **Allows** filtering by indicator and variation for focused runs.

## Supported indicators (current)

These indicators are seeded in the database and mapped to source requests in code:

- **Inflation**: IPCA, IPCA-15, INPC (monthly change, YTD, rolling 12m), IGP-M, IGP-DI, IGP-10
- **Interest rates**: Selic target (Copom), Selic effective (daily), CDI, DI Over
- **Macro**: GDP (annual), unemployment rate (monthly), average income (habitual/effective, monthly)
- **External sector**: Trade balance (monthly, computed as export − import), current account (monthly), international reserves (monthly)
- **Fiscal**: Public debt / GDP (monthly), primary result (monthly)

## Indicators, variations, and data types

| Indicator | Variation | Frequency | Data type | Source |
| --- | --- | --- | --- | --- |
| IPCA | MONTHLY_CHANGE | Monthly | Percentage (%) | IBGE/SIDRA |
| IPCA | YTD | Monthly | Percentage (%) | IBGE/SIDRA |
| IPCA | ROLLING_12M | Monthly | Percentage (%) | IBGE/SIDRA |
| IPCA_15 | MONTHLY_CHANGE | Monthly | Percentage (%) | IBGE/SIDRA |
| IPCA_15 | YTD | Monthly | Percentage (%) | IBGE/SIDRA |
| IPCA_15 | ROLLING_12M | Monthly | Percentage (%) | IBGE/SIDRA |
| INPC | MONTHLY_CHANGE | Monthly | Percentage (%) | IBGE/SIDRA |
| INPC | YTD | Monthly | Percentage (%) | IBGE/SIDRA |
| INPC | ROLLING_12M | Monthly | Percentage (%) | IBGE/SIDRA |
| IGP_M | MONTHLY_CHANGE | Monthly | Percentage (%) | Ipeadata (IGP12_IGPMG12) |
| IGP_M | YTD | Monthly | Percentage (%) | Ipeadata (IGP12_IGPM12, computed) |
| IGP_M | ROLLING_12M | Monthly | Percentage (%) | Ipeadata (IGP12_IGPM12, computed) |
| IGP_DI | ROLLING_12M | Monthly | Percentage (%) | Ipeadata (IGP12_IGPDI12) |
| IGP_10 | VALUE | Monthly | Index | Ipeadata (FGV) |
| SELIC | EFFECTIVE_RATE | Daily | Percentage (% annualized) | BCB/SGS |
| SELIC | TARGET_RATE | Decision-based | Percentage (% annualized) | BCB/SGS |
| CDI | RATE | Daily | Percentage (%) | BCB/SGS |
| DI_OVER | RATE | Daily | Percentage (%) | BCB/SGS |
| GDP | VALUE | Annual | BRL (millions) | IBGE/SIDRA |
| UNEMPLOYMENT | RATE | Monthly | Percentage (%) | IBGE/SIDRA |
| AVERAGE_INCOME | HABITUAL | Monthly | BRL | IBGE/SIDRA |
| AVERAGE_INCOME | EFFECTIVE | Monthly | BRL | IBGE/SIDRA |
| TRADE_BALANCE | VALUE | Monthly | USD (millions) | MDIC/ComexStat |
| CURRENT_ACCOUNT | VALUE | Monthly | USD (millions) | BCB/SGS |
| INTERNATIONAL_RESERVES | VALUE | Monthly | USD (millions) | BCB/SGS |
| PUBLIC_DEBT_GDP | RATE | Monthly | Percentage (%) | BCB/SGS |
| PRIMARY_RESULT | VALUE | Monthly | BRL (millions) | BCB/SGS |

## Data flow (step-by-step)

1. **Load variations** from DB (`indicator_variation` + `indicator`).
2. **Resolve source request** (SIDRA/SGS/ComexStat) based on indicator + variation.
3. **Determine period**:
   - If there is no previous successful run → backfill window.
   - If there is a recent run → incremental window.
4. **Fetch data** from the source.
5. **Normalize** values and build `ValueRecord`.
6. **Upsert** into `indicator_value`.
7. **Write control record** in `indicator_load_control` with last_reference_date and metadata.

## Incremental vs backfill

The job uses two window types:

- **Backfill**: default for first run or when last data is too old.
- **Incremental**: used when last_reference_date is recent enough.

Window sizes (defaults) are configurable:

- `INDICATORS_BACKFILL_MONTHS` (default 120)
- `INDICATORS_BACKFILL_YEARS` (default 10)
- `INDICATORS_INCREMENTAL_MONTHS` (default 6)
- `INDICATORS_INCREMENTAL_YEARS` (default 2)
- `INDICATORS_INCREMENTAL_DAYS` (default 30)

Each variation can override incremental windows in `indicator_variation` (e.g., monthly indicators can be set to fetch only last 6 months on incremental runs).

## Data sources

### SIDRA (IBGE)
Used for inflation, unemployment, and income series.

### SGS (BCB)
Used for interest rates, fiscal, and external sector series.

### ComexStat (MDIC)
Used for **trade balance**, computed as:

```
balance = export (FOB) - import (FOB)
```

ComexStat has strict rate limits and can return 429 (Too Many Requests). Throttling and retry are built-in and configurable.

### Ipeadata (IPEA, FGV indices)

Used for FGV inflation indices (IGP-M, IGP-DI, IGP-10). Ipeadata provides public OData endpoints per series code.  
IGP-M uses two series:

- `IGP12_IGPMG12` → monthly variation (%)
- `IGP12_IGPM12` → index (points) used to compute YTD and rolling 12m

Computed formulas:

- **YTD** = `(index_t / index_dec_prev_year - 1) * 100`
- **ROLLING_12M** = `(index_t / index_t_minus_12m - 1) * 100`

**TODO**: confirm series code for IGP-10.

## Rate limiting and reliability

- **SGS** throttling via `SGS_MIN_INTERVAL_MS`.
- **ComexStat** throttling + retry (`COMEXSTAT_MIN_INTERVAL_MS`, `COMEXSTAT_MAX_RETRIES`, `COMEXSTAT_RETRY_WAIT_SECONDS`).
- ComexStat uses browser-like headers to reduce Cloudflare blocking.

## Configuration

All configuration is via environment variables.

### Database

- `POSTGRES_HOST` (default `localhost`)
- `POSTGRES_PORT` (default `5432`)
- `POSTGRES_DB` (default `neotool_db`)
- `POSTGRES_USER` (default `neotool`)
- `POSTGRES_PASSWORD` (**required**)
- `POSTGRES_POOL_MIN` (default `2`)
- `POSTGRES_POOL_MAX` (default `10`)
- `POSTGRES_SSL_MODE` (default `prefer`)

### Job behavior

- `INDICATORS_MAX_WORKERS` (default `5`)
- `INDICATORS_QUEUE_BUFFER_MULTIPLIER` (default `2`)
- `INDICATORS_BACKFILL_MONTHS` (default `120`)
- `INDICATORS_BACKFILL_YEARS` (default `10`)
- `INDICATORS_INCREMENTAL_MONTHS` (default `6`)
- `INDICATORS_INCREMENTAL_YEARS` (default `2`)
- `INDICATORS_INCREMENTAL_DAYS` (default `30`)

### Filters (for selective runs)

- `INDICATOR_FILTER` (e.g. `IPCA`, `TRADE_BALANCE`)
- `VARIATION_FILTER` (e.g. `YTD`, `VALUE`, `MONTHLY_CHANGE`)

### Source timeouts

- `SIDRA_TIMEOUT_SECONDS` (default `60`)
- `SGS_TIMEOUT_SECONDS` (default `60`)
- `COMEXSTAT_TIMEOUT_SECONDS` (default `60`)
- `IPEADATA_TIMEOUT_SECONDS` (default `60`)

### Ipeadata base URL

- `IPEADATA_BASE_URL` (default `http://www.ipeadata.gov.br/api/odata4`)

### SGS throttling

- `SGS_MIN_INTERVAL_MS` (default `0`)

### ComexStat headers and throttling

- `COMEXSTAT_USER_AGENT`
- `COMEXSTAT_REFERER`
- `COMEXSTAT_ORIGIN`
- `COMEXSTAT_ACCEPT`
- `COMEXSTAT_MIN_INTERVAL_MS` (default `1000`)
- `COMEXSTAT_MAX_RETRIES` (default `2`)
- `COMEXSTAT_RETRY_WAIT_SECONDS` (default `10`)

## How to run

### Build

```
make build-cronjob
```

### Run locally

```
make run-cronjob
```

### Run a specific indicator

```
INDICATOR_FILTER=IPCA VARIATION_FILTER=YTD make run-cronjob
```

### Run ComexStat safely (recommended settings)

```
INDICATORS_MAX_WORKERS=1 \
COMEXSTAT_MIN_INTERVAL_MS=5000 \
COMEXSTAT_MAX_RETRIES=3 \
COMEXSTAT_RETRY_WAIT_SECONDS=12 \
INDICATOR_FILTER=TRADE_BALANCE VARIATION_FILTER=VALUE \
make run-cronjob
```

## How to test

```
make test
```

You can also run:

```
make vet
make fmt
```

## What to validate (for analysts)

After a run:

- Check that **last_reference_date** is recent for each variation.
- Validate that **counts** look consistent with the expected periodicity.
- Spot-check values vs official sources.

Useful SQL:

```
-- last run per variation
SELECT
  lc.indicator_variation_id,
  v.label AS variation_label,
  i.label AS indicator_label,
  lc.status,
  lc.run_type,
  lc.last_reference_date,
  lc.updated_at
FROM indicator_load_control lc
JOIN indicator_variation v ON v.id = lc.indicator_variation_id
JOIN indicator i ON i.id = v.indicator_id
WHERE lc.id IN (
  SELECT MAX(id)
  FROM indicator_load_control
  GROUP BY indicator_variation_id
)
ORDER BY i.label, v.label;
```

```
-- value coverage
SELECT
  i.label AS indicator_label,
  v.label AS variation_label,
  COUNT(*) AS total_values,
  MAX(iv.reference_date) AS last_reference_date
FROM indicator_value iv
JOIN indicator_variation v ON v.id = iv.indicator_variation_id
JOIN indicator i ON i.id = v.indicator_id
GROUP BY i.label, v.label
ORDER BY i.label, v.label;
```

## Troubleshooting

- **429 from ComexStat**: increase `COMEXSTAT_MIN_INTERVAL_MS`, reduce workers (`INDICATORS_MAX_WORKERS=1`).
- **403 from ComexStat**: headers may be blocked; keep default headers or retry later.
- **SGS metadata failures**: check series ID and SGS availability.

## CI/CD and GitOps (Flux)

### Build & push image (CI)

The indicators image is built and pushed to GHCR on **release tags**:

- Workflow: `.github/workflows/indicators.yml`
- Trigger: tag `vX.Y.Z` (or GitHub Release)
- Image: `ghcr.io/invistus/neotool-financialdata-indicators:vX.Y.Z`

### Deploy (GitOps via Flux)

Flux watches the image repository and updates the CronJob manifest using image automation:

- ImageRepository: `flux-system/neotool-financialdata-indicators`
- ImagePolicy: semver `vX.Y.Z`
- ImageUpdateAutomation: updates `infra/kubernetes/flux/apps/services/financial-data-indicators/cronjob.yaml`

### CronJob manifest

Location (GitOps):  
`invistus-flux/infra/kubernetes/flux/apps/services/financial-data-indicators/cronjob.yaml`

If you need to change schedule, env, or resource limits, update that file and commit.

## Repo structure

```
cmd/cronjob/                # entrypoint
internal/application/       # config + runner
internal/processor/         # worker pool + task dispatch
internal/repository/        # DB access
internal/domain/            # domain structs
internal/source/            # source registry + clients
```
