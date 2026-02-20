#!/bin/bash
set -e

# =============================================================================
# Database Initialization Script
# Runs on EVERY container start (not just first boot).
# Idempotent: safe to run multiple times.
# =============================================================================

# List of databases to ensure exist (add new ones here)
DATABASES=(
    "unleash_db"
    # Add more databases as needed:
    # "another_db"
)

# Export password for psql (uses PGPASSWORD env var)
export PGPASSWORD="$POSTGRES_PASSWORD"

echo "Ensuring required databases exist..."

for db in "${DATABASES[@]}"; do
    # Check if database exists, create if not
    psql -v ON_ERROR_STOP=1 \
         --host "$PGHOST" \
         --port "$PGPORT" \
         --username "$POSTGRES_USER" \
         --dbname "$POSTGRES_DB" <<-EOSQL
        SELECT 'CREATE DATABASE $db OWNER $POSTGRES_USER'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
    echo "  ✓ Database '$db' ensured"
done

echo "Database initialization completed."
