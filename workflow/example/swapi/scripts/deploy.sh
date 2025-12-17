#!/bin/bash
# Deployment script for SWAPI People ETL flow

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLOW_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$FLOW_DIR"

# Check if Prefect is configured
if ! command -v prefect &> /dev/null; then
    echo "Error: Prefect CLI not found. Please install Prefect first."
    exit 1
fi

# Load environment variables from .env.local or .env
if [ -f "$FLOW_DIR/.env.local" ]; then
    echo "Loading environment variables from .env.local"
    set -a
    source "$FLOW_DIR/.env.local"
    set +a
elif [ -f "$FLOW_DIR/.env" ]; then
    echo "Loading environment variables from .env"
    set -a
    source "$FLOW_DIR/.env"
    set +a
fi

# Set Prefect API URL if not set
export PREFECT_API_URL="${PREFECT_API_URL:-http://localhost:4200/api}"

echo "Deploying SWAPI People ETL flow..."
echo "Prefect API URL: $PREFECT_API_URL"

# Deploy using prefect.yaml configuration
prefect deploy --name swapi-people-etl-deployment

echo "Deployment complete!"
echo ""
echo "To run manually:"
echo "  prefect deployment run swapi-people-etl/swapi-people-etl-deployment"
echo ""
echo "To view in UI:"
echo "  http://localhost:4201"
