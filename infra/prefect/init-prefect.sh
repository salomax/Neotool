#!/bin/bash
set -e

PREFECT_API_URL=${PREFECT_API_URL:-http://prefect-server:4200/api}
MAX_RETRIES=30
RETRY_DELAY=2

echo "🚀 Prefect Infrastructure Initialization"
echo "========================================"

# Wait for Prefect API
echo "Waiting for Prefect server to be ready..."
for i in $(seq 1 $MAX_RETRIES); do
  if curl -f -s "${PREFECT_API_URL}/health" > /dev/null 2>&1; then
    echo "✓ Prefect server is ready!"
    break
  fi
  if [ $i -eq $MAX_RETRIES ]; then
    echo "✗ Prefect server did not become ready after $MAX_RETRIES attempts"
    exit 1
  fi
  echo "  Attempt $i/$MAX_RETRIES: Waiting..."
  sleep $RETRY_DELAY
done

export PREFECT_API_URL

# Create work pool (idempotent)
echo ""
echo "Creating work pool 'default'..."
prefect work-pool create default --type process 2>&1 | grep -v "already exists" || true

# Create work queue (idempotent)
echo "Creating work queue 'bacen-ifdata'..."
prefect work-queue create bacen-ifdata --pool default 2>&1 | grep -v "already exists" || true

# Deploy flows (if workflow directory is mounted)
if [ -d "/workflow" ]; then
  echo ""
  echo "Deploying Prefect flows..."
  
  # Deploy bacen_ifdata flow
  if [ -d "/workflow/financial_data/bacen_ifdata" ]; then
    echo "  Deploying bacen-ifdata-ingestion..."
    cd /workflow/financial_data/bacen_ifdata
    prefect deploy 2>&1 | grep -v "already exists" || true
  fi
  
  # Add other flows here as needed
  # if [ -d "/workflow/other/flow" ]; then
  #   cd /workflow/other/flow
  #   prefect deploy || true
  # fi
fi

echo ""
echo "✓ Prefect infrastructure initialization complete!"

