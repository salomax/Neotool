---
title: Institution Enhancement Consumer
type: feature
category: financial-data
status: current
version: 1.0.0
tags: [feature, financial-data, kafka, consumer, cnpja, enhancement]
ai_optimized: true
search_keywords: [institution, enhancement, cnpja, kafka, consumer, logo, domain, receita]
related:
  - 03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 05-standards/database-standards/schema-standards.md
---

# Institution Enhancement Consumer

> **Purpose**: Kafka consumer that enriches BACEN institution data with CNPJ data provider information, including domain extraction, logo management, and social network discovery.

## Overview

This feature implements a Kafka consumer that processes `financial-data.institution.upserted` events to enhance institution records with additional data from external sources:

1. **CNPJ Data Provider Integration**: Queries CNPJ data provider for company information using constructed CNPJ
2. **Domain Extraction**: Extracts domain from email addresses in CNPJ data provider response
3. **Website Resolution**: Resolves website URL from CNPJ data provider response, special rules, or domain
4. **Social Network Discovery**: Scrapes website to discover social network links (if website is reachable)
5. **Logo Management**: TODO - Downloads and stores institution logos (via asset management service)
6. **Receita Data Storage**: Stores full CNPJ data provider response in JSONB column for future use

## Architecture

```
┌─────────────────────────────────────┐
│  financial-data.institution.upserted│
│           (Kafka Topic)              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  InstitutionEnhancementConsumer       │
│  (Sequential processing, 15s rate    │
│   limit for CNPJ data provider)      │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  InstitutionEnhancementProcessor     │
│  - Construct CNPJ from bacen_cod_inst│
│  - Query CNPJ data provider (with rate limit) │
│  - Extract domain from emails       │
│  - Resolve website URL              │
│  - Scrape social networks           │
│  - Download logo (TODO: asset mgmt)  │
│  - Store receita_data (JSONB)       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  financial_data.parent_institution│
│  (one per parent CNPJ, shared by     │
│   institutions with same parent)     │
└─────────────────────────────────────┘
```

## Database Schema

### Table: `financial_data.parent_institution`

**Relationship**: One enhancement per parent CNPJ (shared by institutions with the same parent)
- Primary Key: `parent_cnpj` (root CNPJ - either `cnpj_instituicao_lider` or constructed from `bacen_cod_inst`)
- If multiple institutions share the same parent (`cnpj_instituicao_lider`), they share the same enhancement
- If an institution has no parent, enhancement is for that institution itself (using constructed CNPJ)

**Columns**:
- `parent_cnpj` (TEXT, PK) - Root CNPJ (cnpj_instituicao_lider if available, otherwise constructed from bacen_cod_inst)
- `domain` (TEXT, nullable) - Extracted domain from CNPJ data provider emails
- `website` (TEXT, nullable) - Website URL if available from CNPJ data provider or website resolution (TODO: not yet implemented)
- `logo_storage_key` (TEXT, nullable) - Storage key for logo image (TODO: asset management service)
- `social_network` (JSONB, nullable) - Social network links discovered from website scraping (JSON object with keys: linkedin, instagram, facebook, twitter, x) (TODO: not yet implemented)
- `receita_data` (JSONB, nullable) - Full CNPJ data provider response (from Receita.gov.br data)
- `created_at` (TIMESTAMPTZ) - Record creation timestamp
- `updated_at` (TIMESTAMPTZ) - Record update timestamp

**Indexes**:
- Primary key on `parent_cnpj` (automatically created)
- No additional indexes - can be added later if query patterns emerge

## Kafka Consumer Configuration

### Topic
- **Input**: `financial-data.institution.upserted`
- **DLQ**: `financial-data.institution.upserted.enhancement.dlq`
- **Consumer Group**: `institution-enhancement-consumer-group`

### Rate Limiting

**CNPJ Data Provider Backpressure**:
- Minimum 15 seconds between CNPJ data provider queries
- Sequential processing per partition to enforce rate limit
- Consumer group configured for sequential processing (single consumer per partition)

**Rationale**: CNPJ data provider has rate limits (approximately 5 requests per minute). Sequential processing ensures we don't exceed these limits.

### Processing Flow

1. **Receive Message**: Consumer receives `financial-data.institution.upserted` event
2. **Extract Identifier**: Get `bacen_cod_inst` from message
3. **Construct CNPJ**: Build matrix CNPJ from `bacen_cod_inst` (8 digits + "0001" + 2 validator digits)
4. **Rate Limit Check**: Wait if needed to maintain 15-second minimum between CNPJ data provider queries
5. **Query CNPJ Data Provider**: Request company information for constructed CNPJ
6. **Extract Domain**: Parse domain from first email in CNPJ data provider response (excluding blacklisted email providers)
7. **Resolve Website**: Attempt to resolve website URL using special rules, API response fields, or domain construction
8. **Validate Website**: Check if resolved website is reachable (returns 2xx or 3xx status)
9. **Scrape Social Networks**: If website is available and reachable, scrape homepage for social network links
10. **Download Logo**: TODO - Call asset management service to download and store logo
11. **Store Enhancement Data**: Upsert into `institution_enhancement` table with:
   - `parent_cnpj`: Root CNPJ (cnpj_instituicao_lider if available, otherwise constructed CNPJ)
   - `email_domain`: Extracted domain (excluding blacklisted providers)
   - `website`: Resolved website URL if available and reachable, otherwise NULL
   - `social_network`: JSON object with social network links if website was scraped, otherwise NULL
   - `logo_storage_key`: TODO - Storage key from asset management service (currently stored as NULL)
   - `legal_name`: Legal name from CNPJá API response
   - `trade_name`: Trade name (alias) from CNPJá API response
   - `receita_data`: Full CNPJ data provider response as JSONB

## CNPJ Construction Logic

The consumer constructs a matrix CNPJ from `bacen_cod_inst`:

1. Extract digits only from `bacen_cod_inst`
2. Pad to 8 digits with leading zeros
3. Append "0001" (matrix branch code) → 12 digits
4. Calculate 2 validator digits using CNPJ validation algorithm
5. Return full 14-digit CNPJ

**Example**:
- Input: `bacen_cod_inst = "60872504"`
- Constructed: `608725040001XX` (where XX are validator digits)

## Domain Extraction

From CNPJ data provider response, extract domain from the first email:
- Path: `emails[0].domain`
- Normalize: Strip whitespace, handle null/empty values

## Website Resolution

**Current Status**: Implemented

Website URL is resolved using the following priority order:

1. **Special Rules** (highest priority): If the trade name (`alias`) or legal name (`company.name`) contains specific keywords, return the corresponding website:
   - **Sicoob**: If name contains "sicoob" → `https://www.sicoob.com.br`
   - **Cresol**: If name contains "cresol" → `https://cresol.com.br`
   - **Sicredi**: If name contains "sicredi" → `https://sicredi.com.br`
   - **Unicred**: If name contains "unicred" → `https://www.unicred.com.br/`
   - All matches are case-insensitive

2. **Direct API Response**: Check common fields in CNPJá API response:
   - `website`, `site`, `url`, `homepage`, `web`
   - Normalize URL to include protocol if missing

3. **Domain Construction** (fallback): If domain is available from emails, construct URL as `https://{domain}`

4. **Validation**: Before saving, validate that the website is reachable (returns 2xx or 3xx status code). If validation fails, website is not saved to database.

**Current Behavior**: `website` column stores the resolved URL if available and reachable, otherwise NULL

## Social Network Discovery

**Current Status**: Implemented

If website is available and reachable, scrape homepage for social network links:
- Search for links matching patterns: linkedin.com, instagram.com, facebook.com, twitter.com, x.com, youtube.com, whatsapp.com
- Check all `<a>` tags with `href` attributes
- Normalize URLs (handle relative URLs, protocol-less URLs, etc.)
- Store discovered links in `social_network` JSONB column as:
  ```json
  {
    "linkedin": "https://linkedin.com/company/...",
    "instagram": "https://instagram.com/...",
    "facebook": "https://facebook.com/...",
    "twitter": "https://twitter.com/...",
    "youtube": "https://youtube.com/...",
    "whatsapp": "https://whatsapp.com/..."
  }
  ```
- Only non-null values are stored (missing networks are omitted)
- Only the first match per platform is stored

**Current Behavior**: `social_network` column stores discovered links if website was successfully scraped, otherwise NULL

## Logo Management (TODO)

**Current Status**: Placeholder for asset management service integration

**Future Implementation**:
- Call asset management service with normalized domain
- Service downloads logo from logo.dev API
- Service returns storage key
- Store key in `logo_storage_key` column

**Logo URL Construction**:
- Use `website` column value if available (resolved from domain)
- Fallback to domain from emails
- Normalize: Remove protocol, path, query parameters
- Format: `https://img.logo.dev/{normalized_domain}?token=...&format=png&retina=true`

## Receita Data Storage

The full CNPJ data provider response is stored in the `receita_data` JSONB column:
- Enables future queries without re-fetching from CNPJ data provider
- Supports additional data extraction as needed
- Preserves complete API response for auditing

## Error Handling

### CNPJ Data Provider Errors
- **404 Not Found**: Institution not found in CNPJ data provider → Store enhancement record with null domain/receita_data
- **Rate Limit Exceeded**: Retry with exponential backoff (respecting 15s minimum)
- **Timeout**: Retry with exponential backoff
- **Permanent Errors**: Send to DLQ after max retries

### Validation Errors
- Invalid `bacen_cod_inst`: Send to DLQ immediately (no retry)
- CNPJ construction failure: Send to DLQ immediately

### Database Errors
- Retry transient errors (connection issues)
- Send to DLQ for permanent errors (constraint violations)

## Metrics (TODO)

**Current Status**: Metrics collection is not yet implemented

**Planned Metrics**:
- `financial_data.parent_institution.processed` - Counter
- `financial_data.parent_institution.processing.duration` - Timer
- `financial_data.parent_institution.dlq.count` - Counter
- `financial_data.parent_institution.retry.count` - Counter
- `financial_data.parent_institution.error.count` - Counter
- `financial_data.parent_institution.cnpj_data_provider.queries` - Counter
- `financial_data.parent_institution.cnpj_data_provider.not_found` - Counter
- `financial_data.parent_institution.domain.extracted` - Counter
- `financial_data.parent_institution.website.resolved` - Counter (TODO)
- `financial_data.parent_institution.social_network.discovered` - Counter (TODO)
- `financial_data.parent_institution.logo.downloaded` - Counter (TODO)

## Connection Pooling

The consumer uses connection pooling for database connections to improve performance and resource management:

- **Pool Size**: Configurable via `POSTGRES_POOL_MIN` (default: 2) and `POSTGRES_POOL_MAX` (default: 10)
- **Connection Reuse**: Connections are reused from the pool instead of creating new ones for each message
- **Automatic Cleanup**: Pool is closed gracefully on consumer shutdown
- **Retry Logic**: Connection pool errors are retried with exponential backoff (max 3 attempts)

## Health Checks

The consumer includes HTTP health check endpoints for Kubernetes:

- **`/health`**: Basic health check (always returns 200 if server is running)
- **`/ready`**: Readiness probe (checks database and Kafka connectivity)
- **`/live`**: Liveness probe (checks if consumer loop is active)

Health check server runs on port 8080 by default (configurable via `HEALTH_CHECK_PORT`). The health check server can be disabled by setting `HEALTH_CHECK_ENABLED=false`.

## Idempotency

Processing is idempotent:
- Upsert operation on `institution_enhancement` table
- Uses `parent_cnpj` as natural key
- Re-processing same message updates existing record

## Testing

### Prerequisites

To test the institution enhancement consumer, you need the following services running:

1. **PostgreSQL** (via pgbouncer) - Database for storing enhancement data
2. **Kafka** - Message broker for consuming institution events
3. **Consumer Service** - The institution enhancement consumer itself

**Quick Start with Docker Compose**:

```bash
# Start required services
cd infra/docker
docker compose -f docker-compose.local.yml --profile database --profile kafka up -d

# Start the consumer
docker compose -f docker-compose.local.yml up institution-enhancement-consumer
```

**Alternative: Local Development**:

```bash
# Start database and Kafka services only
docker compose -f docker-compose.local.yml --profile database --profile kafka up -d

# Run consumer locally (requires Python 3.14 and dependencies)
cd workflow/financial_data/institution_enhancement
python3.14 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Set environment variables (see README.md for full list)
export POSTGRES_HOST=localhost
export POSTGRES_PORT=6432
export POSTGRES_DB=neotool_db
export POSTGRES_USER=neotool
export POSTGRES_PASSWORD=neotool
export KAFKA_BROKERS=localhost:9092

# Run consumer
python flows/consumer.py
```

### Test Message Format

The consumer expects messages on the `financial-data.institution.upserted` topic with the following structure:

```json
{
  "schema_version": "1.0",
  "event_id": "test-event-123",
  "occurred_at": "2024-01-01T00:00:00Z",
  "source": "bacen-ifdata-ingestion",
  "bacen_cod_inst": "60872504",
  "change_type": "CREATED",
  "nome_instituicao": "Banco Teste S.A.",
  "situacao": "Ativa",
  "cnpj_instituicao_lider": null,
  "ano_mes_ref": "202401"
}
```

**Required Fields**:
- `schema_version`: Schema version (e.g., "1.0")
- `event_id`: Unique event identifier
- `occurred_at`: ISO 8601 timestamp
- `source`: Source system identifier
- `bacen_cod_inst`: BACEN institution code (8 digits)
- `change_type`: Either "CREATED" or "UPDATED"

**Optional Fields**:
- `cnpj_instituicao_lider`: Parent CNPJ if institution is part of a group
- `nome_instituicao`: Institution name
- Other institution metadata fields

### Publishing Test Messages

**Using Python (kafka-python)**:

```python
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers='localhost:9092',
    value_serializer=lambda v: json.dumps(v).encode('utf-8'),
    key_serializer=lambda k: str(k).encode('utf-8') if k else None
)

test_message = {
    "schema_version": "1.0",
    "event_id": "test-123",
    "occurred_at": "2024-01-01T00:00:00Z",
    "source": "test",
    "bacen_cod_inst": "60872504",
    "change_type": "CREATED"
}

# Publish to topic
future = producer.send('financial-data.institution.upserted', 
                      key='60872504', 
                      value=test_message)
future.get(timeout=10)  # Wait for confirmation

producer.close()
```

**Using Kafka CLI tools**:

```bash
# Create topic if it doesn't exist
docker exec neotool-kafka kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic financial-data.institution.upserted \
  --partitions 1 \
  --replication-factor 1

# Publish message
echo '{"schema_version":"1.0","event_id":"test-123","occurred_at":"2024-01-01T00:00:00Z","source":"test","bacen_cod_inst":"60872504","change_type":"CREATED"}' | \
docker exec -i neotool-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic financial-data.institution.upserted
```

### Expected Results

#### Successful Processing

When a message is successfully processed, you should see:

1. **Consumer Logs**:
   ```
   INFO - Processing message: bacen_cod_inst=60872504, parent_cnpj=60872504000123, event_id=test-123
   INFO - Successfully stored enhancement data: parent_cnpj=60872504000123, domain=example.com
   ```

2. **Database Record** in `financial_data.parent_institution`:
   ```sql
   SELECT parent_cnpj, domain, receita_data, created_at, updated_at
   FROM financial_data.parent_institution
   WHERE parent_cnpj = '60872504000123';
   ```

   **Expected columns**:
   - `parent_cnpj`: Constructed CNPJ (14 digits) or `cnpj_instituicao_lider` if available
   - `email_domain`: Extracted domain from CNPJá API emails (e.g., "example.com") or NULL if not found or blacklisted
   - `receita_data`: Full CNPJá API response as JSONB (or NULL if 404)
   - `website`: Resolved website URL if available and reachable, otherwise NULL
   - `legal_name`: Legal name from CNPJá API response (company.name) or NULL
   - `trade_name`: Trade name from CNPJá API response (alias) or NULL
   - `logo_storage_key`: NULL (not yet implemented)
   - `social_network`: JSONB object with social network links if website was scraped, otherwise NULL
   - `created_at`, `updated_at`: Timestamps

3. **CNPJ Construction**: The consumer constructs a matrix CNPJ from `bacen_cod_inst`:
   - Input: `bacen_cod_inst = "60872504"`
   - Output: `parent_cnpj = "608725040001XX"` (where XX are validator digits)

#### API Not Found (404)

When CNPJá API returns 404 (institution not found):

1. **Consumer Logs**:
   ```
   INFO - Processing message: bacen_cod_inst=60872504, parent_cnpj=60872504000123
   INFO - Successfully stored enhancement data: parent_cnpj=60872504000123, domain=None
   ```

2. **Database Record**:
   - `domain`: NULL
   - `receita_data`: NULL
   - Record is still created (allows future retries)

#### Rate Limiting

The consumer enforces a 15-second minimum between CNPJá API queries:

- First message: Processed immediately
- Second message (within 15 seconds): Consumer waits to maintain rate limit
- Check logs for: `Waiting X seconds to maintain rate limit`

#### DLQ (Dead Letter Queue)

Failed messages are published to `financial-data.institution.upserted.enhancement.dlq`:

**DLQ Message Structure**:
```json
{
  "original_message": { /* original Kafka message */ },
  "error_type": "ValueError",
  "error_message": "Invalid bacen_cod_inst",
  "failed_at": "2024-01-01T00:00:00Z"
}
```

**Consume from DLQ**:
```python
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    'financial-data.institution.upserted.enhancement.dlq',
    bootstrap_servers='localhost:9092',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

for message in consumer:
    print(f"DLQ Message: {message.value}")
    break
```

### Verification Steps

#### 1. Check Consumer Health

**Health Check Endpoints** (if port 8080 is exposed):

```bash
# Basic health check
curl http://localhost:8080/health

# Readiness probe (checks database and Kafka)
curl http://localhost:8080/ready

# Liveness probe (checks if consumer loop is active)
curl http://localhost:8080/live
```

**Expected Responses**:
- `/health`: `{"status": "healthy", "service": "institution-enhancement-consumer"}`
- `/ready`: `{"status": "ready", "checks": {"database": {...}, "kafka": {...}}}`
- `/live`: `{"status": "alive", "consumer": "running"}`

#### 2. Verify Database Records

```sql
-- Check all enhancement records
SELECT 
    parent_cnpj,
    domain,
    website,
    logo_storage_key,
    social_network,
    receita_data IS NOT NULL as has_receita_data,
    created_at,
    updated_at
FROM financial_data.parent_institution
ORDER BY created_at DESC
LIMIT 10;

-- Check specific institution
SELECT *
FROM financial_data.parent_institution
WHERE parent_cnpj = '60872504000123';

-- Verify receita_data structure
SELECT 
    parent_cnpj,
    domain,
    receita_data->>'cnpj' as cnpj_from_api,
    receita_data->>'name' as name_from_api,
    jsonb_array_length(receita_data->'emails') as email_count
FROM financial_data.parent_institution
WHERE receita_data IS NOT NULL;
```

#### 3. Check Consumer Logs

```bash
# Docker logs
docker logs neotool-institution-enhancement-consumer -f

# Look for:
# - "Processing message" entries
# - "Successfully stored enhancement data" entries
# - Any error messages
# - Rate limiting messages
```

#### 4. Verify Idempotency

Publish the same message twice:

```python
# First publish
producer.send('financial-data.institution.upserted', key='60872504', value=message1)
time.sleep(5)

# Second publish (same bacen_cod_inst)
producer.send('financial-data.institution.upserted', key='60872504', value=message2)
```

**Expected**: Only one database record exists, with `updated_at` timestamp reflecting the second update.

#### 5. Test Error Scenarios

**Invalid Message** (missing required field):
```json
{
  "schema_version": "1.0",
  "event_id": "test-invalid",
  "occurred_at": "2024-01-01T00:00:00Z",
  "source": "test",
  "change_type": "CREATED"
  // Missing bacen_cod_inst
}
```

**Expected**: Message sent to DLQ with validation error.

**Invalid CNPJ Construction**:
```json
{
  "bacen_cod_inst": "INVALID",
  // ... other fields
}
```

**Expected**: Message sent to DLQ with CNPJ construction error.

### Integration Tests

Run the automated integration tests:

```bash
cd workflow/financial_data/institution_enhancement
pytest tests/test_integration.py -v -m integration
```

**Test Coverage**:
- End-to-end message processing with real Kafka and PostgreSQL
- Successful processing with mocked CNPJá API
- 404 handling (institution not found)
- Idempotency (processing same message twice)
- Rate limiting enforcement
- DLQ flow and message structure

**Note**: Integration tests require Docker to be running (uses Testcontainers).

## Key Design Decisions

### 1. Root CNPJ as Primary Key
- Uses `parent_cnpj` as primary key
- Value is either `cnpj_instituicao_lider` (if available) or constructed CNPJ from `bacen_cod_inst`
- Enables efficient lookups and ensures uniqueness

### 2. Per Parent CNPJ Relationship
- Enhancement is per parent CNPJ, not per individual institution
- Multiple institutions can share the same enhancement if they have the same parent
- If institution has no parent, enhancement is for that institution itself

### 3. Rate Limiting Strategy
- CNPJ data provider: 15 seconds minimum between queries (5 requests/minute limit)
- Sequential processing per partition enforced by consumer group configuration
- Global rate limiting state in processor to enforce across all partitions
- **Note**: Current implementation uses global state which works for single-instance deployments. For multi-instance deployments, Redis-based distributed rate limiting would be needed (future enhancement)

### 4. Receita Data Storage
- Full CNPJ data provider response stored as JSONB
- Enables future queries without re-fetching
- Preserves complete API response for auditing

### 5. Logo Management (Future)
- Placeholder for asset management service integration
- `logo_storage_key` column ready for storage key
- Logo download logic documented but not yet implemented

## Spec Compliance

This feature follows the project's specification standards:

- ✅ Follows Kafka Consumer Pattern from `docs/04-patterns/backend-patterns/kafka-consumer-pattern.md`
- ✅ Follows Database Standards from `docs/05-standards/database-standards/schema-standards.md`
- ✅ Follows Spec Context Strategy from `docs/06-workflows/spec-context-strategy.md`
- ✅ Migration follows naming convention: `V{version}__{description}.sql`
- ✅ Table in `financial_data` schema (not public)
- ✅ Idempotent migration (uses `IF NOT EXISTS`)

## Related Documentation

- [BACEN IFData Ingestion](../bacen-ifdata-ingestion/bacen-ifdata-ingestion.md) - Source of institution events
- [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md) - Consumer implementation pattern
- [Database Standards](../../05-standards/database-standards/schema-standards.md) - Schema design rules
