# Institution Enhancement Consumer

Kafka consumer that enriches BACEN institution data with CNPJá API information, including domain extraction and receita data storage.

## Overview

This consumer processes `financial-data.institution.upserted` events to enhance institution records with additional data from CNPJá API:

1. **CNPJ Construction**: Builds matrix CNPJ from `bacen_cod_inst`
2. **CNPJá API Integration**: Queries CNPJá API for company information
3. **Domain Extraction**: Extracts domain from email addresses
4. **Data Storage**: Stores enhancement data in `financial_data.institution_enhancement` table

## Setup

1. **Create virtual environment:**
   ```bash
   python3.14 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

3. **Set environment variables:**
   ```bash
   # Database
   POSTGRES_HOST=localhost
   POSTGRES_PORT=6432
   POSTGRES_DB=neotool_db
   POSTGRES_USER=neotool
   POSTGRES_PASSWORD=neotool
   
   # Kafka
   KAFKA_BROKERS=localhost:9092
   
   # CNPJá API (optional)
   CNPJA_BASE_URL=https://open.cnpja.com/office
   CNPJA_API_TIMEOUT=30
   CNPJA_RATE_LIMIT_SECONDS=15
   
   # Database Connection Pool (optional)
   POSTGRES_POOL_MIN=2
   POSTGRES_POOL_MAX=10
   
   # Health Check Server (optional)
   HEALTH_CHECK_PORT=8080
   HEALTH_CHECK_ENABLED=true
   ```

## Running the Consumer

```bash
python flows/consumer.py
```

The consumer will:
- Connect to Kafka and subscribe to `financial-data.institution.upserted` topic
- Process messages sequentially per partition (enforces rate limiting)
- Store enhancement data in database
- Publish failed messages to DLQ topic: `financial-data.institution.upserted.enhancement.dlq`

## Rate Limiting

The consumer enforces a 15-second minimum between CNPJá API queries to respect the API rate limits (approximately 5 requests per minute). Sequential processing per partition ensures this limit is maintained.

### Current Implementation

The current rate limiting implementation uses global state (`_last_cnpja_query_time`) which works correctly for a single process/instance. This ensures sequential processing and rate limit compliance within a single consumer instance.

### TODO: Distributed Rate Limiting

For multi-process or multi-instance deployments (e.g., multiple Kubernetes pods), the current global state approach will not coordinate rate limiting across instances. To support distributed deployments, implement Redis-based distributed rate limiting:

- Use Redis to store the last query timestamp with a distributed lock
- Coordinate rate limiting across all consumer instances
- Ensure total API calls across all instances respect the rate limit
- Consider using Redis SETNX or Redlock for distributed locking

**Note**: This is a future enhancement. The current implementation is suitable for single-instance deployments.

## Architecture

- **Consumer**: `flows/consumer.py` - Main Kafka consumer loop
- **Processor**: `tasks/processor.py` - Business logic for processing messages
- **CNPJá Client**: `tasks/cnpja_client.py` - HTTP client with rate limiting
- **CNPJ Utils**: `tasks/cnpj_utils.py` - CNPJ construction utilities
- **Message Model**: `tasks/message.py` - Kafka message data classes
- **DLQ Publisher**: `tasks/dlq_publisher.py` - Dead letter queue publisher

## Database Schema

Enhancement data is stored in `financial_data.institution_enhancement` table:
- `parent_cnpj` (TEXT, PK) - Root CNPJ
- `domain` (TEXT, nullable) - Extracted domain
- `website` (TEXT, nullable) - Website URL (TODO)
- `logo_storage_key` (TEXT, nullable) - Logo storage key (TODO)
- `social_network` (JSONB, nullable) - Social network links (TODO)
- `receita_data` (JSONB, nullable) - Full CNPJá API response
- `created_at`, `updated_at` - Timestamps

## Error Handling

- **404 Not Found**: Institution not found in CNPJá API → Store with null domain/receita_data
- **Rate Limit Exceeded (429)**: Retry with exponential backoff (respecting 15s minimum)
- **Timeout**: Retry with exponential backoff (max 3 attempts)
- **Database Connection Errors**: Retry with exponential backoff (max 3 attempts)
- **Permanent Errors**: Send to DLQ after max retries
- **DLQ Publishing Failures**: Message not committed, will be retried by Kafka consumer

## Health Checks

The consumer includes HTTP health check endpoints for Kubernetes:

- **`/health`**: Basic health check (always returns 200 if server is running)
- **`/ready`**: Readiness probe (checks database and Kafka connectivity)
- **`/live`**: Liveness probe (checks if consumer loop is active)

Health check server runs on port 8080 by default (configurable via `HEALTH_CHECK_PORT`).

## Connection Pooling

The consumer uses connection pooling for database connections to improve performance and resource management:

- **Pool Size**: Configurable via `POSTGRES_POOL_MIN` (default: 2) and `POSTGRES_POOL_MAX` (default: 10)
- **Connection Reuse**: Connections are reused from the pool instead of creating new ones for each message
- **Automatic Cleanup**: Pool is closed gracefully on consumer shutdown

## Related Documentation

- [Feature Specification](../../../docs/03-features/financial_data/institution-enhancement/institution-enhancement.md)
- [BACEN IFData Ingestion](../bacen_ifdata/README.md) - Source of institution events

