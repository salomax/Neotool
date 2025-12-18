# SWAPI ETL Job

ETL job that fetches people data from SWAPI (https://swapi.dev/), transforms it, and publishes to Kafka for processing by Kotlin consumers.

## Setup

1. **Install Python 3.11+** (use pyenv or your preferred version manager)

2. **Create virtual environment:**
   ```bash
   python3.11 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

4. **Set environment variables:**
   Create a `.env` or `.env.local` file:
   ```bash
   KAFKA_BROKERS=localhost:9092
   SWAPI_BASE_URL=https://swapi.dev/api
   PREFECT_API_URL=http://localhost:4200/api
   ```

5. **Start Prefect server** (if not already running):
   
   **Note**: This project uses Prefect 3.x. The server image is `prefecthq/prefect:3-latest`.
   
   ```bash
   docker-compose -f ../../../infra/docker/docker-compose.local.yml --profile prefect up -d
   ```
   
   Verify Prefect is running:
   ```bash
   curl http://localhost:4200/api/health
   prefect version  # Should show 3.x
   ```

6. **Create work pool and work queue** (if not already created):
   
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
   prefect work-queue create swapi-etl --pool default
   ```
   
   **Alternative:** You can also export the API URL directly:
   ```bash
   export PREFECT_API_URL=http://localhost:4200/api
   prefect work-pool create default --type process
   prefect work-queue create swapi-etl --pool default
   ```

7. **Start Kafka** (if not already running):
   ```bash
   docker-compose -f ../../../infra/docker/docker-compose.yml --profile kafka up -d
   ```

## Running the Flow

### Manual Run
```bash
python -m flows.swapi_people_etl
```

### Deploy to Prefect
```bash
./scripts/deploy.sh
```

### Run via Prefect CLI
```bash
prefect deployment run swapi-people-etl/swapi-people-etl-deployment
```

## Project Structure

```
swapi/
├── flows/              # Prefect flow definitions
├── deployments/        # Deployment configurations (legacy)
├── schemas/            # Kafka message schemas
├── scripts/            # Utility scripts
├── tests/              # Unit tests
├── prefect.yaml        # Prefect 3.x deployment configuration
└── requirements.txt    # Python dependencies
```

## Testing

Run tests:
```bash
pytest tests/
```

## Monitoring

- **Prefect UI**: http://localhost:4201 (Prefect 3.x includes UI in server)
- **Prefect API**: http://localhost:4200/api
- **Prefect Version**: 3.6.5+ (check with `prefect version`)

## Prefect 3.x Notes

- **Work Pools**: Define execution environment (e.g., `process` for local, `docker`/`kubernetes` for production)
- **Work Queues**: Route flow runs to specific workers within a work pool
- **Deployment**: Uses `prefect.yaml` configuration file (Prefect 3.x standard)
- **Storage**: Local development uses filesystem (answer "n" to remote storage). Production requires remote storage (GitHub, S3, etc.)
