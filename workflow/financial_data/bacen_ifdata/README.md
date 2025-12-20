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
    
    **Important**: Pyway migrations must be run from the `workflow/financial_data/` directory (where `pyway.conf` is located), not from the `bacen_ifdata/` subdirectory.
    
    **Run Migrations**:
    
    ```bash
    # Navigate to the financial_data directory (where pyway.conf is located)
    cd ../..  # From bacen_ifdata/, go to workflow/financial_data/
    
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
    - If you get "Missing configuration options" error, make sure you're running pyway from `workflow/financial_data/` directory with `-c pyway.conf`
    - Migrations are idempotent (use `IF NOT EXISTS`), so it's safe to run them multiple times

## Running the Flow

### Manual Run
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
