#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE not found" >&2
  exit 1
fi

# Load .env.local
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

cd "$SCRIPT_DIR"

echo "==> Building b3_cotahist_job..."
go build -o bin/cronjob ./cmd/cronjob

echo "==> Running b3_cotahist_job..."
exec ./bin/cronjob
