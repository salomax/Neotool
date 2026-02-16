# Institution Enhancement Consumer (Go)

## What is it?
Institution Enhancement Consumer (IEC) is a Go service that **consumes Kafka events about financial institutions** and **enriches them** with company data, website and social links, and optional logo assets before writing the result to PostgreSQL.

"Consumer" here means **Kafka consumer group**: it reads messages from a topic, processes them, and stores the enhanced record. "Enhancement" refers to the enrichment steps described below.

## What does the consumer do?
End-to-end flow for each institution message:

1. **Read event** from `KAFKA_TOPIC` (default: `financial-data.institution.upserted`).
2. **Determine parent CNPJ**:
   - If `bacen_cod_inst` does **not** start with `C`, build from `bacen_cod_inst` (ignore `cnpj_instituicao_lider`).
   - If `bacen_cod_inst` starts with `C`, build from `cnpj_instituicao_lider` (required).
3. **Check enhancement cache**: If a `financial_institution` row exists for the parent CNPJ with `updated_at` within the last 24 hours, skip steps 4–9 and only create/update the bacen_institution ↔ financial_institution link. The link step runs in both the cache-hit path and the full-enhancement path below.
4. **Fetch CNPJ company data** via the CNPJA API (rate-limited globally).
5. **Discover website** using a priority chain:
   - Special institution keywords (fixed URLs, no validation)
   - Optional CSV mapping (`WEBSITES_CSV_PATH`)
   - CNPJA fields (`website`, `site`, `url`, `homepage`, `web`)
   - Email domain fallback
   - Optional Serper search (if enabled)
6. **Validate website** (HTTP reachability, 2 attempts with retry/backoff). Accepts 2xx, 3xx, and 403.
7. **Scrape social links** from the validated website (Facebook, Instagram, LinkedIn, Twitter/X, YouTube, WhatsApp).
8. **Optional logo processing**:
   - If `LOGO_DEV_TOKEN`, `SECURITY_SERVICE_ID`, and `SECURITY_SERVICE_SECRET` are set, download a logo from logo.dev and upload it to the Assets service.
   - `SECURITY_SERVICE_ID` and `SECURITY_SERVICE_SECRET` are **service-to-service OAuth2 client credentials** used to obtain a short-lived access token from the internal Security service. That token is required to authorize GraphQL requests and presigned upload flows in the Assets service. Without these credentials, the consumer cannot authenticate, so logo upload is disabled (download-only is not attempted).
9. **Upsert** the enriched record into `financial_data.financial_institution`, then **link** all bacen_institution records that share the same parent_cnpj to this financial_institution (via `financial_institution_bacen_institution`).
10. **Retry or DLQ**:
   - Retries are handled with exponential backoff.
   - After max retries, messages are published to `KAFKA_DLQ_TOPIC`.

## Entry points
This project ships multiple binaries. The main consumer is `cmd/consumer`.

- `cmd/consumer` - Kafka consumer (main service)
- `cmd/standalone` - Process a single CNPJ from CLI
- `cmd/dlq-consumer` - Reprocess messages from DLQ
- `cmd/retry-worker` - Database-driven retry worker
- `cmd/dlq-inspector` - Print DLQ stats
- `cmd/logo-updater` - Backfill logos for records that have a website but no logo

## Repository layout

```
workflow/golang/financial_data/institution_enhancement_consumer/
├── cmd/                 # Entry points
├── internal/            # Domain + application + infrastructure
├── deployments/         # Docker/K8s
├── docs/                # Extra docs / runbooks
├── test/                # Integration tests
└── Makefile             # Build/test helpers
```

## Prerequisites

- Go 1.22+
- PostgreSQL 13+ (table `financial_data.parent_institution` must exist)
- Kafka 3.0+
- CNPJA API access (https://open.cnpja.com)
- Optional: Serper API key, logo.dev token, Security + Assets services

## Setup

1. Enter the project directory:
   ```bash
   cd workflow/golang/financial_data/institution_enhancement_consumer
   ```
2. Download dependencies:
   ```bash
   make deps
   # or: go mod download
   ```
3. Configure environment variables (see next section).
4. Build the binaries:
   ```bash
   make build
   # or build only the consumer:
   make build-consumer
   ```

## Building on Linux and macOS

The Makefile targets default to `GOOS=linux` and `GOARCH=amd64` to produce container-friendly binaries. For native builds, override `GOOS/GOARCH` or call `go build` directly.

### Linux (native or cross-compiled)

```bash
# linux/amd64 (default)
make build-consumer

# linux/arm64
GOOS=linux GOARCH=arm64 go build -o bin/consumer ./cmd/consumer
```

### macOS (native)

```bash
# Apple Silicon
GOOS=darwin GOARCH=arm64 go build -o bin/consumer ./cmd/consumer

# Intel
GOOS=darwin GOARCH=amd64 go build -o bin/consumer ./cmd/consumer
```

## Configuration (environment variables)
All config is driven by env vars. Defaults are applied when shown.

### PostgreSQL
| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `6432` | PostgreSQL port |
| `POSTGRES_DB` | `neotool_db` | Database name |
| `POSTGRES_USER` | `neotool` | Database user |
| `POSTGRES_PASSWORD` | **required** | Database password |
| `POSTGRES_POOL_MIN` | `2` | Min pool connections |
| `POSTGRES_POOL_MAX` | `10` | Max pool connections |

### Kafka
| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `localhost:9092` | Comma-separated broker list |
| `KAFKA_TOPIC` | `financial-data.institution.upserted` | Input topic |
| `KAFKA_CONSUMER_GROUP` | `institution-enhancement-consumer-go` | Consumer group |
| `KAFKA_DLQ_TOPIC` | `financial-data.institution.upserted.enhancement.dlq` | DLQ topic |

### CNPJA API
| Variable | Default | Description |
|----------|---------|-------------|
| `CNPJA_BASE_URL` | `https://open.cnpja.com` | Base URL |
| `CNPJA_TIMEOUT_SECONDS` | `30` | Request timeout |
| `CNPJA_RATE_LIMIT_SECONDS` | `15` | Min delay between requests |

### Website discovery
| Variable | Default | Description |
|----------|---------|-------------|
| `WEBSITES_CSV_PATH` | `internal/data/websites.csv` | CSV with CNPJ-to-website mappings |
| `WEBSITES_CSV_ENABLED` | `true` | Enable CSV lookup |
| `SERPER_API_KEY` | *(optional)* | Serper API key |
| `SERPER_ENABLED` | `false` | Enable Serper searches |
| `SERPER_TIMEOUT_SECONDS` | `10` | Serper request timeout |

CSV format (semicolon separated):

```
"parent_cnpj";"legal_name";"website"
```

Special institution keywords (case-insensitive substring match on institution name). If matched, the website is set directly and **skips validation**:

- `sicoob` -> `https://www.sicoob.com.br`
- `cresol` -> `https://cresol.com.br`
- `sicredi` -> `https://sicredi.com.br`
- `unicred` -> `https://www.unicred.com.br/`

### Retry policy
| Variable | Default | Description |
|----------|---------|-------------|
| `RETRY_MAX_RETRIES` | `3` | Max retry attempts |
| `RETRY_INITIAL_DELAY_MS` | `1000` | Initial delay (ms) |
| `RETRY_MAX_DELAY_MS` | `30000` | Max delay (ms) |
| `RETRY_BACKOFF_MULTIPLIER` | `2.0` | Backoff multiplier |
| `RETRY_JITTER` | `true` | Enable jitter |
| `RETRY_DLQ_MAX_RETRIES` | `3` | Max retries in DLQ consumer |

### Worker pool
| Variable | Default | Description |
|----------|---------|-------------|
| `WORKER_POOL_SIZE` | `5` | Concurrent workers |

### Health checks
| Variable | Default | Description |
|----------|---------|-------------|
| `HEALTH_CHECK_ENABLED` | `true` | Enable HTTP health server |
| `HEALTH_CHECK_PORT` | `8080` | Health server port |

Endpoints:
- `/health` basic check
- `/live` consumer loop alive
- `/ready` DB + Kafka reachable

### Logo processing (optional)
| Variable | Default | Description |
|----------|---------|-------------|
| `LOGO_DEV_TOKEN` | *(optional)* | logo.dev API token |
| `LOGO_DEV_TIMEOUT_SECONDS` | `30` | Logo download timeout |
| `SECURITY_HOST` | `localhost:8080` | Security service host |
| `SECURITY_SERVICE_ID` | *(optional)* | OAuth2 client id |
| `SECURITY_SERVICE_SECRET` | *(optional)* | OAuth2 client secret |
| `SECURITY_TIMEOUT_SECONDS` | `10` | OAuth2 request timeout |
| `ASSETS_APOLLO_ROUTER_URL` | `http://router:4000/graphql` | Assets GraphQL endpoint |
| `ASSETS_NAMESPACE` | `institution-logos` | Asset namespace |
| `ASSETS_UPLOAD_TIMEOUT_SECONDS` | `60` | Logo upload timeout |
| `ASSETS_GRAPHQL_TIMEOUT_SECONDS` | `30` | GraphQL timeout |

Logo processing is **enabled only when all three are set**:
`LOGO_DEV_TOKEN`, `SECURITY_SERVICE_ID`, and `SECURITY_SERVICE_SECRET`.

## Run locally

### Kafka consumer (main)

```bash
export POSTGRES_PASSWORD=your_password
export KAFKA_BROKERS=localhost:9092
# optional overrides:
# export KAFKA_TOPIC=financial-data.institution.upserted
# export CNPJA_BASE_URL=https://open.cnpja.com

make build-consumer
./bin/consumer
```

### Standalone single-CNPJ mode

```bash
make build-standalone
./bin/standalone 12345678000190
```

#### Standalone examples and variations

```bash
# Build + run via Makefile
make run-standalone CNPJ=12345678000190

# Run directly from source (go run)
go run ./cmd/standalone 12345678000190

# Disable CSV website lookup
WEBSITES_CSV_ENABLED=false ./bin/standalone 12345678000190

# Enable Serper for website discovery
SERPER_ENABLED=true SERPER_API_KEY=your_key ./bin/standalone 12345678000190

# Enable logo processing (requires Assets + Security services)
LOGO_DEV_TOKEN=token \
SECURITY_SERVICE_ID=client_id \
SECURITY_SERVICE_SECRET=client_secret \
./bin/standalone 12345678000190
```

### DLQ consumer

```bash
make build-dlq-consumer
./bin/dlq-consumer
```

### Retry worker

```bash
make build-retry-worker
./bin/retry-worker
```

### Logo backfill

```bash
mkdir -p bin
go build -o bin/logo-updater ./cmd/logo-updater
# logo-updater requires LOGO_DEV_TOKEN, SECURITY_SERVICE_ID, SECURITY_SERVICE_SECRET
./bin/logo-updater
```

### DLQ inspector

```bash
make build-dlq-inspector
./bin/dlq-inspector
```

## Testing

```bash
# Unit tests (race + coverage)
make test

# Integration tests (requires Docker for testcontainers)
make test-integration
```

Outputs:
- Coverage file: `coverage.out`

## How to run locally end-to-end (minimal checklist)

1. Start PostgreSQL and Kafka.
2. Ensure `financial_data.parent_institution` exists in Postgres.
3. Export env vars (at minimum `POSTGRES_PASSWORD` and `KAFKA_BROKERS`).
4. Build and run the consumer.
5. Produce a test message to the input topic and confirm a row upsert in Postgres.

## Troubleshooting

- **No messages consumed**: verify `KAFKA_BROKERS`, `KAFKA_TOPIC`, and consumer group lag.
- **Readiness failing**: check DB connectivity and Kafka auth/ACLs.
- **No website found**: confirm `WEBSITES_CSV_PATH` exists or enable Serper.
- **Logo processing disabled**: ensure `LOGO_DEV_TOKEN`, `SECURITY_SERVICE_ID`, and `SECURITY_SERVICE_SECRET` are set.

## License
Proprietary - Invistus Financial Data Platform
