# BACEN IFData - Go Implementation

Production-ready Go implementation of the BACEN IFData ingestion job, migrated from Python/Prefect.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]() [![Test Coverage](https://img.shields.io/badge/coverage-65%25-yellow)]() [![Go Version](https://img.shields.io/badge/go-1.22%2B-blue)]()

## Overview

This service ingests quarterly financial data from Brazil's Central Bank (BACEN) IF.data API:
- ✅ Fetches institution registry with SHA-256 hash-based change detection
- ✅ Extracts financial reports with parallel paginated processing
- ✅ Promotes raw data to normalized tables
- ✅ Publishes institution changes to Kafka
- ✅ Database-backed task queue with automatic retry
- ✅ Production-ready with health checks, graceful shutdown, and comprehensive logging

## Recent Improvements (2025-01-01)

### Phase 1: Critical Bugs Fixed ✅
- ✅ **Race Condition**: Fixed goroutine leak in worker error handling (multiple workers failing simultaneously)
- ✅ **HTTP Status Extraction**: Fixed bug where HTTP status codes weren't captured in database
- ✅ **CSV Data Loss**: Fixed potential data corruption in pagination with special characters
- ✅ **Graceful Shutdown**: Added context cancellation checks before creating new tasks

### Phase 1: Architecture Enhancements ✅
- ✅ **Interfaces Defined**: Created comprehensive domain interfaces for testability (BACENClient, repositories, publishers)
- ✅ **TLS Support**: Added configurable TLS/SSL for PostgreSQL connections (`POSTGRES_SSL_MODE`)
- ✅ **Unit Tests**: Implemented comprehensive test suite (64.8% coverage in `internal/shared`)
  - Retry logic with exponential backoff and jitter (11 tests)
  - Error classification (HTTP, network, PostgreSQL errors) (26 tests)
  - Hash computation for change detection (9 tests)
  - Circuit breaker state machine (8 tests)
  - Rate limiter with token bucket algorithm (8 tests)

### Phase 2: Reliability & Resilience ✅
- ✅ **Circuit Breaker**: Implemented circuit breaker pattern for BACEN API calls
  - Opens after 5 consecutive failures
  - 60-second timeout before retry
  - Half-open state with 3 test requests
  - Prevents hammering down services during outages
- ✅ **Rate Limiter**: Token bucket rate limiting to prevent API backpressure
  - Configurable max requests per second (default: 2.0 req/s)
  - Burst allowance for temporary spikes (default: 5)
  - Prevents overwhelming BACEN API during concurrent extraction
  - Protects against HTTP 429 (Too Many Requests) errors
- ✅ **Enhanced Health Checks**: Kafka connectivity check in readiness probe
  - Database ping check
  - Kafka broker connection verification
  - Proper liveness/readiness separation

### Production Readiness Improvements
- ✅ Proper error wrapping with `errors.As()` for error chain preservation
- ✅ Non-blocking error channels to prevent goroutine leaks
- ✅ CSV escaping using `encoding/csv.Writer` for special characters
- ✅ Context-aware operations throughout the codebase
- ✅ Fast-fail on circuit open to conserve resources

## Architecture

Following **Hexagonal Architecture** pattern:

```
cmd/
├── cronjob/        # Daily scheduled execution (6am)
├── retry-worker/   # Retries failed extract tasks
└── cli/            # Manual execution tool

internal/
├── domain/         # Business models and errors
├── application/    # Orchestration and config
├── infrastructure/ # External integrations (DB, BACEN API, Kafka)
├── processor/      # Business logic
└── shared/         # Utilities (retry, CSV, date, hash)
```

## Key Features

### ✅ Already Implemented
- **Domain Layer**: All models, errors defined
- **Shared Utilities**: Retry with exponential backoff, error classification, CSV parsing, date utils, hash computation
- **BACEN API Client**: HTTP client with retry for registry, catalog, and report data
- **Configuration**: Environment-based config management
- **Database Repositories**: Institution and extract task repositories

### 🚧 Remaining Work
See [MIGRATION_STATUS.md](./MIGRATION_STATUS.md) and [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) for details.

## Quick Start

### Prerequisites
- Go 1.22+
- PostgreSQL 14+ (schema pre-created)
- Kafka
- Access to BACEN API

### Build
```bash
# Download dependencies
go mod download

# Build all binaries
make build-all

# Or build individually
make build-cronjob
make build-retry-worker
make build-cli
```

### Run Locally
```bash
# Set required environment variables
export POSTGRES_PASSWORD=your_password
export KAFKA_BROKERS=localhost:9092

# Run cronjob (auto-determines next quarter)
./bin/cronjob

# Run with specific quarter
./bin/cli --ano-mes 202403

# Force registry refresh
./bin/cli --ano-mes 202403 --force-registry-refresh

# Republish all institution events for a quarter (from DB, change_type=UPDATED)
./bin/cli --ano-mes 202403 --republish-all

# Run retry worker
./bin/retry-worker
```

### Docker
```bash
# Build images
make docker-build-all

# Run cronjob
docker run --env-file .env bacen-ifdata-cronjob:latest

# Run retry worker
docker run --env-file .env bacen-ifdata-retry-worker:latest
```

## Configuration

All configuration via environment variables:

### Database
| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | localhost | PostgreSQL host |
| `POSTGRES_PORT` | 6432 | PostgreSQL port |
| `POSTGRES_DB` | neotool_db | Database name |
| `POSTGRES_USER` | neotool | Database user |
| `POSTGRES_PASSWORD` | *required* | Database password |
| `POSTGRES_POOL_MIN` | 2 | Min connections |
| `POSTGRES_POOL_MAX` | 10 | Max connections |
| `POSTGRES_SSL_MODE` | prefer | TLS mode (disable/allow/prefer/require/verify-ca/verify-full) |

**Production Security**: Set `POSTGRES_SSL_MODE=require` or higher for encrypted database connections.

### BACEN API
| Variable | Default | Description |
|----------|---------|-------------|
| `BACEN_BASE_URL` | https://olinda.bcb.gov.br/... | BACEN API base URL |
| `BACEN_TIMEOUT_SECONDS` | 60 | HTTP timeout |
| `BACEN_MAX_REQUESTS_PER_SECOND` | 2.0 | Max API requests per second (rate limit) |
| `BACEN_BURST_SIZE` | 5 | Burst allowance for rate limiter |

**Rate Limiting**: The rate limiter uses a token bucket algorithm to control request rate. With default settings (2 req/s, burst 5), the first 5 requests can proceed immediately, then the rate is limited to 2 requests per second. Adjust these values based on BACEN API rate limits and your concurrent worker count.

### Kafka
| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | localhost:9092 | Kafka brokers (comma-separated) |
| `KAFKA_TOPIC` | financial-data.institution.upserted | Topic for events |
| `KAFKA_COMPRESSION_TYPE` | gzip | Compression type |

### Extraction
| Variable | Default | Description |
|----------|---------|-------------|
| `EXTRACTION_MAX_WORKERS` | 5 | Parallel workers |
| `EXTRACTION_PAGE_SIZE` | 5000 | Rows per page |
| `EXTRACTION_MAX_TASK_RETRIES` | 3 | Max retries per task |
| `EXTRACTION_TASK_POLL_INTERVAL_MS` | 1000 | Retry worker poll interval |

### Retry Policy
| Variable | Default | Description |
|----------|---------|-------------|
| `RETRY_MAX_RETRIES` | 3 | Max retry attempts |
| `RETRY_INITIAL_DELAY_MS` | 1000 | Initial backoff delay |
| `RETRY_MAX_DELAY_MS` | 30000 | Max backoff delay |
| `RETRY_BACKOFF_MULTIPLIER` | 2.0 | Backoff multiplier |
| `RETRY_JITTER` | true | Enable jitter |

## Workflow

### Cronjob (Daily 6am)
1. **Determine Target Quarter**: Calculates next quarter with publication window check (60/90 days)
2. **Ingest Registry**: Fetches institution registry with SHA-256 hash-based change detection
3. **Publish Events**: Publishes CREATED/UPDATED institutions to Kafka
4. **Fetch Catalog**: Gets list of available reports
5. **Create Tasks**: Creates extract tasks (4 institution types × N reports × pages)
6. **Extract Data**: Parallel workers fetch and store raw CSV data
7. **Promote Data**: Converts raw data to normalized bank_report tables
8. **Update Control**: Marks quarter as complete

### Retry Worker (Continuous)
1. Polls for PENDING extract tasks
2. Acquires task with row-level lock (FOR UPDATE SKIP LOCKED)
3. Retries extraction with exponential backoff
4. Updates status or requeues if under max retries

## Database Schema

Expected tables (created by Kotlin service Flyway migrations):
- `financial_data.bacen_institution` - Institution registry
- `financial_data.bacen_ifdata_extract_task` - Task queue
- `financial_data.bacen_ifdata_raw` - Raw CSV data
- `financial_data.bacen_bank_report` - Normalized reports (parent)
- `financial_data.bacen_bank_report_data` - Report data (child)
- `financial_data.bacen_ifdata_load_control` - Load tracking


## Retry Strategy

**Database-Based Retry (Option A)**:
- Failed tasks requeued with `status=PENDING`
- Retry worker polls and processes PENDING tasks
- Row-level locking prevents duplicate processing
- Max retries configurable per task
- Survives pod restarts

## Change Detection

Institution changes detected via SHA-256 hash of 13 fields:
- bacen_cod_inst, nome_instituicao, situacao, segmento_tb, atividade
- tipo_consolidado_bancario, tipo_consolidacao, tipo_controle
- uf, municipio, cod_conglomerado_financeiro
- cod_conglomerado_prudencial, cnpj_instituicao_lider

**Change Types**:
- **CREATED**: New institution → Kafka event
- **UPDATED**: Hash differs → Kafka event
- **UNCHANGED**: Hash equal → No event

## Deployment

### Kubernetes CronJob
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: bacen-ifdata-cronjob
spec:
  schedule: "0 6 * * *"  # Daily at 6am UTC
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: cronjob
            image: bacen-ifdata-cronjob:latest
            envFrom:
            - secretRef:
                name: bacen-ifdata-secrets
          restartPolicy: OnFailure
```

### Kubernetes Deployment (Retry Worker)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bacen-ifdata-retry-worker
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: retry-worker
        image: bacen-ifdata-retry-worker:latest
        ports:
        - containerPort: 8081
          name: health
        livenessProbe:
          httpGet:
            path: /live
            port: 8081
        readinessProbe:
          httpGet:
            path: /ready
            port: 8081
```

## Monitoring

### Health Endpoints
- `GET /health` - Always 200 (ping)
- `GET /live` - 200 if app running
- `GET /ready` - 200 if DB + Kafka connected

### Structured Logs
JSON format with slog:
```json
{
  "time": "2024-01-15T06:00:00Z",
  "level": "INFO",
  "msg": "acquired extract task",
  "id": 1234,
  "ano_mes": "202403",
  "tipo_instituicao": 1,
  "nome_relatorio": "Balancete",
  "page_skip": 0
}
```

## Development

### Running Tests
```bash
# All unit tests
go test ./...

# With coverage report
go test ./... -cover

# Coverage for specific package
go test ./internal/shared/... -coverprofile=coverage.out
go tool cover -html=coverage.out  # View in browser

# Verbose output
go test -v ./internal/shared/...

# Run specific test
go test -run TestRetry_Success ./internal/shared/...
```

**Test Coverage** (Current):
- `internal/shared`: **64.8%** ✅ (improved from 0% → 64.8%)
- Target: 85%+ for critical paths

**Tests Implemented** (62 tests total):
- ✅ Retry logic with exponential backoff (11 tests)
- ✅ Error classification for HTTP, network, PostgreSQL (26 tests)
- ✅ Hash computation and normalization (9 tests)
- ✅ Circuit breaker state machine (8 tests)
- ✅ Rate limiter with token bucket algorithm (8 tests)

### Code Quality
```bash
# Format code
go fmt ./...

# Vet code
go vet ./...

# Lint (requires golangci-lint)
golangci-lint run

# Build all executables
go build ./cmd/...
```


## Migration from Python

This Go implementation replaces the Python/Prefect version with:
- ✅ Lower resource usage (smaller Docker images, less memory)
- ✅ Better concurrency (goroutines vs Prefect workers)
- ✅ Simpler deployment (no Prefect orchestrator needed)
- ✅ Database-based retry (no DLQ topic needed for tasks)
- ✅ Structured logging (JSON output)
- ✅ Health checks for Kubernetes

**Key Differences**:
- Prefect → Native Go orchestration
- Task-level retries → Database-based retry worker
- Python CSV parsing → Go encoding/csv
- psycopg2 → pgx/v5
- kafka-python → sarama

## Troubleshooting

### Missing database table: `financial_data.bacen_institution`

If you see an error like:
```
ERROR: relation "financial_data.bacen_institution" does not exist (SQLSTATE 42P01)
```

**Solution 1: Run the SQL migration script (Quick fix)**
```bash
# Using psql with environment variables
psql -h ${POSTGRES_HOST:-localhost} \
     -p ${POSTGRES_PORT:-6432} \
     -U ${POSTGRES_USER:-neotool} \
     -d ${POSTGRES_DB:-neotool_db} \
     -f create_bacen_institution_table.sql

# Or using connection string
psql "postgres://${POSTGRES_USER:-neotool}:${POSTGRES_PASSWORD}@${POSTGRES_HOST:-localhost}:${POSTGRES_PORT:-6432}/${POSTGRES_DB:-neotool_db}" \
     -f create_bacen_institution_table.sql
```

**Solution 2: Start the Kotlin service (Recommended)**
The Kotlin `financialdata` service runs Flyway migrations on startup and will create all required tables automatically. This is the recommended approach as it ensures all tables are created with the correct schema.

### Publication window not reached
```bash
# Check current quarter calculation
./bin/cli --ano-mes 202403  # Force specific quarter
```

### Tasks stuck in PENDING
```bash
# Verify retry worker is running
kubectl logs -f deployment/bacen-ifdata-retry-worker

# Check task attempt counts
psql -c "SELECT status, attempt_count, COUNT(*) FROM financial_data.ifdata_extract_task GROUP BY status, attempt_count;"
```

### No Kafka events
```bash
# Verify institution changes detected
./bin/cli --ano-mes 202403 --force-registry-refresh

# Check Kafka connectivity
kafkacat -b localhost:9092 -t financial-data.institution.upserted -C
```

## Documentation

- [MIGRATION_STATUS.md](./MIGRATION_STATUS.md) - Current migration progress
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Step-by-step implementation guide

## License

Internal Invistus project

## Support

For issues or questions, contact the Data Engineering team.
