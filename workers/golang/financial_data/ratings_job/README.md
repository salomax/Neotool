# Financial Institution Ratings Ingestion

Go-based workflow for ingesting credit ratings from external rating agencies (Fitch, S&P Global, Moody's) and storing them in the `financial_data` schema.

## Overview

This service fetches ratings for financial institutions from rating agency APIs and stores them with:
- **Idempotent processing** - Hash-based change detection prevents duplicate records
- **Evidence storage** - Raw API responses stored for audit compliance
- **History tracking** - All rating changes recorded for analysis
- **Rate limiting** - Respects API throttling requirements (15s for Fitch)
- **Strategy-based issuers** - Issuer-specific mapping and fetching behavior is isolated

### Phase 1: Fitch Ratings

The initial implementation supports Fitch Ratings via their GraphQL API. Future phases will add S&P Global and Moody's.

## Architecture

```
cmd/
├── cronjob/         # Scheduled execution (K8s CronJob)
└── standalone/      # Manual execution for testing
internal/
├── domain/          # Models, interfaces, errors
├── application/     # Config, health server
├── infrastructure/
│   ├── database/    # PostgreSQL repositories
│   └── issuer/      # API clients (Fitch, etc.)
├── processor/       # Business logic
└── shared/          # Utilities (hash, retry, rate limiter)
```

### Issuer Strategy Pattern

Issuer-specific behavior lives behind strategies and registrars:

- **IssuerStrategy**: controls mapping discovery/creation and rating fetching
- **RatingRegistrar**: handles evidence storage, change detection, ratings/history persistence

This keeps each issuer flow isolated and makes future issuers (S&P Global, Moody's) plug in cleanly.

## Prerequisites

1. **Go 1.24+**
2. **PostgreSQL** with migrations applied (V1_6 and V1_7)
3. **Issuer configuration** enabled in `rating_issuer`

## Configuration

Environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_HOST` | Database host | `localhost` |
| `POSTGRES_PORT` | Database port | `5432` |
| `POSTGRES_DB` | Database name | `neotool_db` |
| `POSTGRES_USER` | Database user | `neotool` |
| `POSTGRES_PASSWORD` | Database password | **required** |
| `POSTGRES_SSL_MODE` | SSL mode | `prefer` |
| `FITCH_API_URL` | Fitch GraphQL endpoint | `https://api.fitchratings.com/` |
| `FITCH_TIMEOUT_SECONDS` | Request timeout | `60` |
| `FITCH_THROTTLE_SECONDS` | Delay between requests | `15` |
| `RETRY_MAX_RETRIES` | Max retry attempts | `3` |
| `HEALTH_CHECK_ENABLED` | Enable health endpoints | `true` |
| `HEALTH_CHECK_PORT` | Health server port | `8080` |

## Database Setup

### 1. Apply Migrations

Ensure migrations V1_6 (tables) and V1_7 (seed data) are applied:

```sql
-- V1_6 creates:
-- - financial_data.rating_issuer
-- - financial_data.financial_institution_rating_issuer
-- - financial_data.rating_source_evidence
-- - financial_data.financial_institution_rating
-- - financial_data.financial_institution_rating_history

-- V1_7 seeds:
-- - FITCH issuer (enabled)
-- - SP_GLOBAL issuer (disabled)
-- - MOODYS issuer (disabled)
```

### 2. Configure Institution Mappings (Optional for Fitch)

Fitch mappings can be created automatically by the strategy when they do not exist. Manual mapping is optional and useful for fixing or overriding slugs.

To manually create a mapping for a specific institution:

```sql
-- Example: Banco do Brasil
INSERT INTO financial_data.financial_institution_rating_issuer
    (rating_issuer_id, financial_institution_id, config, enabled)
SELECT
    ri.id,
    fi.id,
    '{"slug": "banco-do-brasil-sa-80089721"}'::jsonb,
    true
FROM financial_data.rating_issuer ri
CROSS JOIN financial_data.financial_institution fi
WHERE ri.code = 'FITCH'
  AND fi.parent_cnpj = '00000000000191';
```

### Finding Fitch Slugs (Manual)

1. Go to [fitchratings.com](https://www.fitchratings.com)
2. Search for the institution
3. The slug is in the URL: `https://www.fitchratings.com/entity/banco-do-brasil-sa-80089721`
4. Use `banco-do-brasil-sa-80089721` as the slug

## Ingestion Flow (Abstract)

All issuers follow the same high-level flow:

1. **Identify institutions and mappings** (issuer configuration must be enabled)
2. **Rating fetching**
3. **Rating registration** (hash check, upsert, history)

### Fitch Mapping Flow

The Fitch strategy follows the flow below for each institution:

1. List all financial institutions with a left join to `financial_institution_rating_issuer`
2. If a mapping exists and `enabled = false`, skip it
3. If no mapping exists:
   - Suggest a Fitch slug using the institution `legal_name`
   - Fetch the entity name for the slug
   - If the entity name matches the institution `legal_name` (case-insensitive):
     - Create mapping with `enabled = true` and `config = {"slug": "<slug>"}`
     - Continue to rating fetching
   - If no valid match:
     - Create mapping with `enabled = false` and `config = NULL`
4. If a mapping exists with `enabled = true`:
   - Fetch ratings using `config.slug`
   - Continue to rating registration

## Building

```bash
# Build all binaries
make build

# Build specific binary
make build-cronjob
make build-standalone
```

## Running

### Standalone Mode (Testing)

Use standalone mode to test the ingestion without a scheduler:

```bash
# Dry run - fetch and display ratings without persisting
POSTGRES_PASSWORD=your_password \
./bin/ratings-standalone \
  --issuer=FITCH \
  --dry-run \
  --verbose

# Process a specific institution
POSTGRES_PASSWORD=your_password \
./bin/ratings-standalone \
  --issuer=FITCH \
  --institution=<uuid> \
  --dry-run \
  --verbose

# Actually persist the data
POSTGRES_PASSWORD=your_password \
./bin/ratings-standalone \
  --issuer=FITCH
```

**Flags:**
- `--issuer` (required): Issuer code (e.g., `FITCH`)
- `--institution`: Specific institution UUID (optional)
- `--dry-run`: Fetch but don't persist changes
- `--verbose`: Enable debug logging

### Cronjob Mode (Production)

```bash
# Process all enabled issuers
POSTGRES_PASSWORD=your_password \
./bin/ratings-cronjob

# Process specific issuer only
POSTGRES_PASSWORD=your_password \
./bin/ratings-cronjob --issuer=FITCH
```

## Example Output

```
time=2026-01-22T10:00:00Z level=INFO msg="starting ratings standalone" issuer=FITCH dry_run=true
time=2026-01-22T10:00:00Z level=INFO msg="connecting to database"
time=2026-01-22T10:00:00Z level=INFO msg="database connection established" host=localhost database=neotool_db
time=2026-01-22T10:00:00Z level=INFO msg="processing mappings" count=1 issuer=FITCH
time=2026-01-22T10:00:00Z level=INFO msg="fetching ratings" institution_id=abc-123 slug=banco-do-brasil-sa-80089721
time=2026-01-22T10:00:15Z level=INFO msg="fetched ratings" institution_id=abc-123 rating_count=8
time=2026-01-22T10:00:15Z level=INFO msg=rating name=LONG_TERM_ISSUER_DEFAULT_RATING label=BB date=2025-12-12 hash="a1b2c3d4e5f6..."
time=2026-01-22T10:00:15Z level=INFO msg=rating name=SHORT_TERM_ISSUER_DEFAULT_RATING label=B date=2025-12-12 hash="b2c3d4e5f6a1..."
time=2026-01-22T10:00:15Z level=INFO msg=rating name=NATIONAL_LONG_TERM_RATING label="AAA(bra)" date=2025-12-12 hash="c3d4e5f6a1b2..."
time=2026-01-22T10:00:15Z level=INFO msg="dry run: skipping persistence" institution_id=abc-123
time=2026-01-22T10:00:15Z level=INFO msg="standalone completed" created=0 updated=0 unchanged=0 errors=0 duration=15.2s
```

## Rating Types

Fitch rating types are normalized to UPPER_SNAKE_CASE:

| Fitch Description | Normalized Name |
|-------------------|-----------------|
| Long Term Issuer Default Rating | `LONG_TERM_ISSUER_DEFAULT_RATING` |
| Short Term Issuer Default Rating | `SHORT_TERM_ISSUER_DEFAULT_RATING` |
| National Long Term Rating | `NATIONAL_LONG_TERM_RATING` |
| National Short Term Rating | `NATIONAL_SHORT_TERM_RATING` |
| Viability Rating | `VIABILITY_RATING` |
| Government Support Rating | `GOVERNMENT_SUPPORT_RATING` |

## Change Detection

The service uses content hashing for idempotent processing:

1. **Hash computation**: `SHA256(issuer_id | rating_name | rating_label | rating_date | custom_params)`
2. **Change detection**:
   - **CREATED**: No existing rating found
   - **UPDATED**: Hash differs from existing
   - **UNCHANGED**: Hash matches existing (no action taken)
3. **History**: Records created for CREATED and UPDATED changes only

## Testing

```bash
# Run tests
make test

# Run tests with coverage
make test-coverage
```

## Health Checks

When `HEALTH_CHECK_ENABLED=true`, the service exposes:

- `GET /health` - Simple ping (always 200)
- `GET /live` - Liveness check (always 200)
- `GET /ready` - Readiness check (200 if DB connected)

## Troubleshooting

### "no enabled mappings found for issuer"

No mappings were found for the issuer. For Fitch, this means either:

- The issuer is disabled in `rating_issuer`, or
- All mappings were created with `enabled = false`

Check current mappings:

```sql
SELECT * FROM financial_data.financial_institution_rating_issuer
WHERE enabled = true;
```

### "invalid or missing slug in mapping config"

The mapping config doesn't have a slug. Update or recreate the mapping:

```sql
UPDATE financial_data.financial_institution_rating_issuer
SET config = '{"slug": "your-institution-slug"}'::jsonb
WHERE id = '<mapping-id>';
```

### Rate limiting (429 errors)

The Fitch API has strict rate limits. The service automatically waits 15 seconds between requests. If you're still getting 429s, increase `FITCH_THROTTLE_SECONDS`.

### Entity not found

The slug may be incorrect. Verify it on the Fitch website.

## Related Documentation

- [Feature Spec](../../../spec/features/financial_data/financial-institution-ratings.md)
- [Technical Spec](../../../spec/features/financial_data/financial-institution-ratings-spec.md)
- [Database Migrations](../../../service/kotlin/financialdata/src/main/resources/db/migration/)
