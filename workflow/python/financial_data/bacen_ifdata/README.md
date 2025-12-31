# BACEN IFData Ingestion

Batch-oriented, pull-based ingestion pipeline that ingests regulatory financial data published by the Brazilian Central Bank (BACEN) via IFData OData APIs. It stages raw data safely in PostgreSQL and enables downstream processing and synchronization through Kafka events.

This feature acts as the entry point for all BACEN IFData datasets inside Invistus, designed with strong idempotency guarantees, explicit execution control, full auditability, and safe reprocessing capabilities.

## Setup

1. **Install Python 3.14+** (use pyenv or your preferred version manager)

2. **Create virtual environment:**
   ```bash
   python3.14 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install runtime dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Install development dependencies (for testing and development):**
   ```bash
   pip install -r requirements-dev.txt
   ```

5. **Set environment variables:**
   Create a `.env` or `.env.local` file:
   ```bash
   # Database
   DATABASE_URL=postgresql://user:password@localhost:5432/neotool
   
   # Kafka
   KAFKA_BROKERS=localhost:9092
   
   # BACEN API
   BACEN_BASE_URL=https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/
   
   # Prefect
   PREFECT_API_URL=http://localhost:4200/api
   ```

6. **Start Prefect server** (if not already running):
   
   **Note**: This project uses Prefect 3.x. The server image is `prefecthq/prefect:3-latest`.
   
   ```bash
   docker-compose -f ../../../infra/docker/docker-compose.local.yml --profile prefect up -d
   ```
   
   Verify Prefect is running:
   ```bash
   curl http://localhost:4200/api/health
   prefect version  # Should show 3.x
   ```

7. **Create work pool and work queue** (if not already created):
   
   Prefect 3.x uses **work pools** and **work queues** to manage flow execution:
   - **Work Pool**: Defines the execution environment and infrastructure type (e.g., `process` for local execution, `docker` for containerized runs). It specifies where and how your flows will run.
   - **Work Queue**: A queue within a work pool that routes flow runs to specific workers. This allows you to organize and prioritize different types of work (e.g., ETL jobs, scheduled tasks, ad-hoc runs).
   
   The deployment configuration in `prefect.yaml` references these resources, so they must exist before deploying. The `default` work pool with a `process` type runs flows in local Python processes, which is ideal for development and testing.
   
   First, load your environment variables:
   ```bash
   set -a
   source .env.local  # or source .env if you're using .env
   set +a
   ```
   
   Then create the work pool and queue:
   ```bash
   prefect work-pool create default --type process
   prefect work-queue create bacen-ifdata --pool default
   ```
   
   **Alternative:** You can also export the API URL directly:
   ```bash
   export PREFECT_API_URL=http://localhost:4200/api
   prefect work-pool create default --type process
   prefect work-queue create bacen-ifdata --pool default
   ```

8. **Start Kafka** (if not already running):
   ```bash
   docker-compose -f ../../../infra/docker/docker-compose.yml --profile kafka up -d
   ```

9. **Start PostgreSQL** (if not already running):
   ```bash
   docker-compose -f ../../../infra/docker/docker-compose.yml --profile postgres up -d
   ```

10. **Run database migrations:**
    
    **Important**: Pyway migrations must be run from the `workflow/python/financial_data/` directory (where `pyway.conf` is located), not from the `bacen_ifdata/` subdirectory.
    
    **Run Migrations**:
    
    ```bash
    # Navigate to the financial_data directory (where pyway.conf is located)
    cd ../..  # From bacen_ifdata/, go to workflow/python/financial_data/
    
    # Run migrations using pyway with explicit config file
    # Use the venv from bacen_ifdata/ since that's where pyway is installed
    ../bacen_ifdata/venv/bin/pyway -c pyway.conf migrate
    
    # Or if pyway is in your PATH:
    pyway -c pyway.conf migrate
    
    # Check migration status
    ../bacen_ifdata/venv/bin/pyway -c pyway.conf info
    ```
    
    **Note**: The `-c pyway.conf` flag is required because pyway needs to be run from the directory containing the config file. The config file specifies the migration directory and database connection details. The pyway history table is stored in the `public` schema, so no schema bootstrap is required.
    
    **Troubleshooting**:
    - If you get "Missing configuration options" error, make sure you're running pyway from `workflow/python/financial_data/` directory with `-c pyway.conf`
    - Migrations are idempotent (use `IF NOT EXISTS`), so it's safe to run them multiple times

## Running the Flow

### Standalone Scripts (Without Prefect)

You can run individual tasks directly without Prefect orchestration using standalone scripts. This is useful for:
- Testing specific tasks with different parameters
- Debugging task behavior
- Manual data ingestion for specific quarters
- Development and experimentation

**Registry Ingestion:**
```bash
# Run registry ingestion for a specific AnoMes (YYYYMM format)
python scripts/run_registry_ingestion.py 202403

# With verbose output
python scripts/run_registry_ingestion.py 202403 --verbose
```

**Report Catalog Fetch:**
```bash
# Fetch report catalog from BACEN API
python scripts/run_report_catalog_fetch.py

# With verbose output (shows all reports)
python scripts/run_report_catalog_fetch.py --verbose
```

The standalone scripts use the same environment variables as the Prefect flow and wrap task calls in a minimal Prefect flow context to provide logger support.

**Note**: Standalone scripts run tasks without Prefect's retry mechanisms, timeout handling, and observability features. For production use, prefer running through Prefect.

### Manual Run (Prefect Flow)
```bash
# Once the flow is implemented
python -m flows.bacen_ifdata_ingestion
```

### Deploy to Prefect
```bash
# Once deployment script is created
./scripts/deploy.sh
```

### Run via Prefect CLI
```bash
prefect deployment run bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment
```

## Project Structure

```
bacen_ifdata/
├── flows/              # Prefect flow definitions
├── tasks/              # Prefect task modules
├── schemas/            # Kafka message schemas
├── scripts/            # Utility scripts
├── tests/              # Unit tests
├── prefect.yaml        # Prefect 3.x deployment configuration
├── requirements.txt    # Runtime Python dependencies
└── requirements-dev.txt # Development Python dependencies
```

## Testing

Run tests:
```bash
pytest tests/
```

Run tests with coverage:
```bash
pytest tests/ --cov=. --cov-report=html
```

### Coverage Requirements

This project follows testing standards that require:
- **Unit tests**: 90%+ line coverage and 85%+ branch coverage
- **Integration tests**: 80%+ line coverage and 75%+ branch coverage

**Check Coverage**:
```bash
# Generate HTML coverage report
pytest tests/ --cov=. --cov-report=html

# View coverage report
open htmlcov/index.html

# Check coverage summary in terminal
pytest tests/ --cov=. --cov-report=term-missing
```

**Coverage Configuration**:
Coverage is configured via pytest-cov. To add coverage thresholds, update `pyproject.toml` or add a `.coveragerc` file.

## Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `DATABASE_URL` | PostgreSQL connection string | Yes | - |
| `KAFKA_BROKERS` | Kafka broker addresses (comma-separated) | Yes | `localhost:9092` |
| `BACEN_BASE_URL` | BACEN IFData API base URL | No | `https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/` |
| `PREFECT_API_URL` | Prefect API URL | No | `http://localhost:4200/api` |

## Monitoring

- **Prefect UI**: http://localhost:4201 (Prefect 3.x includes UI in server)
- **Prefect API**: http://localhost:4200/api
- **Prefect Version**: 3.6.5+ (check with `prefect version`)

## Observability

The BACEN IFData ingestion flow provides comprehensive observability through logs, metrics, and artifacts.

### Logs

All tasks emit structured logs with the following information:
- **Batch ID**: Unique identifier for each flow run
- **Task Name**: Name of the executing task
- **Records Processed**: Number of records processed (where applicable)
- **Execution Context**: Additional context like `ano_mes`, `nome_relatorio`, etc.

**Viewing Logs**:
1. **Prefect UI**: Navigate to the flow run in Prefect UI and click on individual tasks to view their logs
2. **Prefect CLI**: Use `prefect flow-run logs <flow-run-id>` to view logs for a specific run
3. **Task Logs**: Each task logs at INFO level for normal operations and ERROR level for failures

**Example Log Queries**:
```bash
# View logs for a specific flow run
prefect flow-run logs <flow-run-id>

# View logs for a specific task
prefect task-run logs <task-run-id>

# Filter logs by level
prefect flow-run logs <flow-run-id> --level INFO
```

### Metrics

The flow tracks the following metrics (available via Prefect UI and API):

**Extraction Metrics**:
- `tasks_executed`: Total number of extraction tasks executed
- `tasks_success`: Number of successful extraction tasks
- `tasks_empty`: Number of tasks that returned empty results (end of pagination)
- `tasks_error`: Number of tasks that failed
- `rows_extracted`: Total number of CSV rows extracted and stored in `ifdata_raw`

**Promotion Metrics** (per report):
- `records_promoted`: Total number of records promoted from raw to hot table
- `records_updated`: Number of records updated in `bank_report` (UPSERT)
- `records_inserted`: Number of new records inserted into `bank_report`

**Kafka Metrics**:
- `total`: Total number of institution events to publish
- `published`: Number of events successfully published to Kafka
- `failed`: Number of events that failed to publish
- `dlq_published`: Number of failed events published to DLQ topic

**Viewing Metrics**:
1. **Prefect UI**: Navigate to the flow run and view task statistics in the UI
2. **Prefect API**: Query metrics via Prefect API endpoints
3. **Flow Run Results**: Metrics are included in the flow run result dictionary

### Artifacts

Each flow run creates a **Markdown Artifact** with a comprehensive summary of the execution:

**Artifact Content**:
- Batch ID and execution timestamp
- Target quarter (`ano_mes`) processed
- Registry ingestion status and changed institutions count
- Extraction phase statistics (tasks executed, rows extracted, etc.)
- Promotion phase statistics (per report)
- Kafka publishing statistics
- Error information (if any)
- Overall flow status

**Viewing Artifacts**:
1. **Prefect UI**: 
   - Navigate to the flow run
   - Click on the "Artifacts" tab
   - Click on the artifact named `bacen-ifdata-ingestion-summary-{batch-id}`
   - View the markdown summary in the UI

2. **Prefect CLI**:
   ```bash
   # List artifacts for a flow run
   prefect artifact ls --flow-run-id <flow-run-id>
   
   # View artifact content
   prefect artifact read <artifact-id>
   ```

3. **Prefect API**:
   ```python
   from prefect import get_client
   
   async with get_client() as client:
       artifacts = await client.read_artifacts(flow_run_id="<flow-run-id>")
       for artifact in artifacts:
           print(artifact.key, artifact.data)
   ```

**Artifact Key Format**: `bacen-ifdata-ingestion-summary-{batch-id}`

### Flow Run Status

The flow returns a comprehensive status dictionary with all execution statistics:

```python
{
    "batch_id": "uuid",
    "target_ano_mes": "202403",
    "status": "success" | "failed" | "skipped",
    "registry_ingested": True | False,
    "changed_institutions": [...],
    "reports_processed": [...],
    "extraction_stats": {...},
    "promotion_stats": [...],
    "kafka_stats": {...},
    "errors": [...]
}
```

**Accessing Flow Run Results**:
1. **Prefect UI**: View the flow run result in the UI
2. **Prefect CLI**: Use `prefect flow-run inspect <flow-run-id>` to view results
3. **Python**: Access `flow_run.state.result()` after the flow completes

### Error Handling and Fallback

When tasks fail after exhausting retries, the flow:
1. **Logs Error**: Comprehensive error logging with stack traces
2. **Calls Fallback Handler**: Persists failed payloads to durable storage
3. **Sends Notifications**: (Currently mocked, extensible to Slack/PagerDuty/email)
4. **Creates Artifact**: Summary artifact includes error information

**Fallback Storage**:
- Default: Local disk (configurable via `FALLBACK_STORAGE_TYPE` and `FALLBACK_STORAGE_PATH` env vars)
- Future: S3, GCS, or other cloud storage

**Viewing Fallback Data**:
- Check the fallback storage location (default: `./fallback/` directory)
- Each failed payload is stored as JSON with metadata (error type, timestamp, context)

### Troubleshooting

**Common Issues and Solutions**:

1. **Flow Skipped - No Target Quarter**:
   - **Cause**: Publication window not met for next quarter
   - **Solution**: Wait for publication window or manually specify `target_ano_mes`

2. **Registry Missing After Ingestion**:
   - **Cause**: BACEN API may not have data for the quarter yet
   - **Solution**: Verify data availability on BACEN website, or wait for publication window

3. **Extraction Tasks Not Completing**:
   - **Cause**: Tasks may be stuck in RUNNING state or hitting errors
   - **Solution**: Check task logs for errors, verify BACEN API availability, check database connectivity

4. **Kafka Publishing Failures**:
   - **Cause**: Kafka broker unavailable or network issues
   - **Solution**: Check Kafka connectivity, verify topic exists, check DLQ for failed messages

5. **High Error Rates**:
   - **Cause**: BACEN API rate limiting or data quality issues
   - **Solution**: Check BACEN API status, review error logs, verify retry configuration

**Debugging Tips**:
- Use Prefect UI to visualize flow execution and identify bottlenecks
- Check individual task logs for detailed error messages
- Review artifacts for summary statistics
- Use standalone scripts (in `scripts/`) to test individual tasks
- Enable verbose logging by setting `PREFECT_LOGGING_LEVEL=DEBUG`

## Prefect 3.x Notes

- **Work Pools**: Define execution environment (e.g., `process` for local, `docker`/`kubernetes` for production)
- **Work Queues**: Route flow runs to specific workers within a work pool
- **Deployment**: Uses `prefect.yaml` configuration file (Prefect 3.x standard)
- **Storage**: Local development uses filesystem (answer "n" to remote storage). Production requires remote storage (GitHub, S3, etc.)

## Feature Documentation

For detailed feature specification, business rules, and implementation details, see:
- [Feature Specification](../../../docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md)
- [Feature Memory](../../../docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.memory.yml)
- [Feature Tasks](../../../docs/03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.tasks.yml)

## Key Features

- **Idempotent Execution**: Safe to re-run without duplicate data
- **Change Detection**: Hash-based detection of institution changes
- **Pagination Support**: Handles large datasets with pagination
- **Error Handling**: Comprehensive retry logic and fallback mechanisms
- **Observability**: Structured logging, metrics, and Prefect artifacts
- **Kafka Integration**: Publishes institution events for downstream processing
