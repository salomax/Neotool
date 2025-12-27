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
3. **Website Resolution**: TODO - Resolves website URL from domain (if available)
4. **Social Network Discovery**: TODO - Scrapes website to discover social network links
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
│  - Resolve website URL (TODO)       │
│  - Scrape social networks (TODO)     │
│  - Download logo (TODO: asset mgmt)  │
│  - Store receita_data (JSONB)       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  financial_data.institution_enhancement│
│  (one per parent CNPJ, shared by     │
│   institutions with same parent)     │
└─────────────────────────────────────┘
```

## Database Schema

### Table: `financial_data.institution_enhancement`

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
6. **Extract Domain**: Parse domain from first email in CNPJ data provider response
7. **Resolve Website**: TODO - Attempt to resolve website URL from domain (try HTTPS, then HTTP)
8. **Scrape Social Networks**: TODO - If website is available, scrape homepage for social network links
9. **Download Logo**: TODO - Call asset management service to download and store logo
10. **Store Enhancement Data**: Upsert into `institution_enhancement` table with:
   - `parent_cnpj`: Root CNPJ (cnpj_instituicao_lider if available, otherwise constructed CNPJ)
   - `domain`: Extracted domain
   - `website`: TODO - Resolved website URL (currently stored as NULL)
   - `social_network`: TODO - JSON object with social network links (currently stored as NULL)
   - `logo_storage_key`: TODO - Storage key from asset management service (currently stored as NULL)
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

## Website Resolution (TODO)

**Current Status**: Not yet implemented

**Planned Implementation**:
If domain is available, attempt to resolve website URL:
- Try HTTPS first: `https://{domain}`
- Fallback to HTTP: `http://{domain}`
- Follow redirects (up to 10 redirects)
- Store final resolved URL in `website` column
- Used for logo download and social network discovery

**Current Behavior**: `website` column is stored as NULL

## Social Network Discovery (TODO)

**Current Status**: Not yet implemented

**Planned Implementation**:
If website is available, scrape homepage for social network links:
- Search for links matching patterns: linkedin.com, instagram.com, facebook.com, twitter.com, x.com
- Check meta tags for social network URLs
- Store discovered links in `social_network` JSONB column as:
  ```json
  {
    "linkedin": "https://linkedin.com/company/...",
    "instagram": "https://instagram.com/...",
    "facebook": "https://facebook.com/...",
    "twitter": "https://twitter.com/...",
    "x": "https://x.com/..."
  }
  ```
- Only non-null values are stored (missing networks are omitted)

**Current Behavior**: `social_network` column is stored as NULL

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
- `financial_data.institution_enhancement.processed` - Counter
- `financial_data.institution_enhancement.processing.duration` - Timer
- `financial_data.institution_enhancement.dlq.count` - Counter
- `financial_data.institution_enhancement.retry.count` - Counter
- `financial_data.institution_enhancement.error.count` - Counter
- `financial_data.institution_enhancement.cnpj_data_provider.queries` - Counter
- `financial_data.institution_enhancement.cnpj_data_provider.not_found` - Counter
- `financial_data.institution_enhancement.domain.extracted` - Counter
- `financial_data.institution_enhancement.website.resolved` - Counter (TODO)
- `financial_data.institution_enhancement.social_network.discovered` - Counter (TODO)
- `financial_data.institution_enhancement.logo.downloaded` - Counter (TODO)

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
