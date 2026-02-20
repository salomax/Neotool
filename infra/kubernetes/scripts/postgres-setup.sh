#!/bin/bash
set -e

# =============================================================================
# PostgreSQL Setup - Creates users and stores credentials in Vault
# =============================================================================
#
# This script:
# 1. Connects to Vault and retrieves/generates PostgreSQL credentials
# 2. Creates users in PostgreSQL (if PG is running)
# 3. Stores credentials in Vault for External Secrets to sync
#
# Prerequisites:
# - Vault must be initialized and unsealed (run vault-setup.sh first)
# - kubectl access to the cluster
#
# =============================================================================

NAMESPACE="production"
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "  $1"
}

generate_password() {
    openssl rand -base64 24 | tr -d '/+=' | head -c 32
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace=*) NAMESPACE="${1#*=}"; shift ;;
        --namespace) NAMESPACE="$2"; shift 2 ;;
        -h|--help)
            echo "Usage: $0 [--namespace NAMESPACE]"
            echo ""
            echo "PostgreSQL setup script - creates users and stores credentials in Vault"
            echo ""
            echo "This script will configure:"
            echo "  - Main PostgreSQL admin user"
            echo "  - Application database and user"
            echo "  - Unleash database and user"
            echo "  - PgBouncer userlist"
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# =============================================================================
# Pre-flight checks
# =============================================================================
print_header "PostgreSQL Setup"

print_step "Checking prerequisites..."

command -v kubectl &> /dev/null || { print_error "kubectl not found"; exit 1; }
command -v jq &> /dev/null || { print_error "jq not found"; exit 1; }
print_success "kubectl and jq available"

# Check Vault credentials
if [ ! -f "$VAULT_CREDS_FILE" ]; then
    print_error "Vault credentials not found: $VAULT_CREDS_FILE"
    echo ""
    echo "Run vault-setup.sh first to initialize Vault"
    exit 1
fi
source "$VAULT_CREDS_FILE"
print_success "Vault credentials loaded"

# Find Vault pod
VAULT_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -z "$VAULT_POD" ]; then
    print_error "Vault pod not found"
    exit 1
fi

# Check Vault is unsealed
VAULT_SEALED=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status -format=json 2>/dev/null | jq -r '.sealed')
if [ "$VAULT_SEALED" = "true" ]; then
    print_error "Vault is sealed. Run vault-setup.sh to unseal it first."
    exit 1
fi
print_success "Vault is unsealed and ready"

# =============================================================================
# Helper function to store secrets in Vault
# =============================================================================
store_in_vault() {
    local path=$1
    shift
    local kv_pairs="$@"

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
        export VAULT_TOKEN='$ROOT_TOKEN'
        vault kv put secret/$path $kv_pairs
    " > /dev/null 2>&1
}

get_from_vault() {
    local path=$1
    local key=$2

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
        export VAULT_TOKEN='$ROOT_TOKEN'
        vault kv get -field=$key secret/$path 2>/dev/null
    " 2>/dev/null || echo ""
}

secret_exists_in_vault() {
    local path=$1
    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
        export VAULT_TOKEN='$ROOT_TOKEN'
        vault kv get secret/$path > /dev/null 2>&1 && echo true || echo false
    " 2>/dev/null
}

# =============================================================================
# Step 1: Configure Main PostgreSQL Credentials
# =============================================================================
print_header "Step 1: PostgreSQL Admin Credentials"

POSTGRES_EXISTS=$(secret_exists_in_vault "postgres")

if [ "$POSTGRES_EXISTS" = "true" ]; then
    print_info "Existing credentials found in Vault"

    EXISTING_USER=$(get_from_vault "postgres" "username")
    EXISTING_DB=$(get_from_vault "postgres" "database")

    print_info "  Username: $EXISTING_USER"
    print_info "  Database: $EXISTING_DB"
    echo ""

    read -p "Keep existing credentials? (Y/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Nn]$ ]]; then
        POSTGRES_EXISTS="false"
    fi
fi

if [ "$POSTGRES_EXISTS" != "true" ]; then
    echo "Configure PostgreSQL admin credentials:"
    echo ""

    read -p "  Admin username [postgres]: " PG_ADMIN_USER
    PG_ADMIN_USER=${PG_ADMIN_USER:-postgres}

    read -p "  Database name [neotool]: " PG_DATABASE
    PG_DATABASE=${PG_DATABASE:-neotool}

    echo ""
    read -p "  Generate random password? (Y/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Nn]$ ]]; then
        read -sp "  Enter password: " PG_ADMIN_PASSWORD
        echo ""
    else
        PG_ADMIN_PASSWORD=$(generate_password)
        echo ""
        print_info "Generated password: $PG_ADMIN_PASSWORD"
        echo ""
        echo -e "${RED}  Save this password! It won't be shown again.${NC}"
        read -p "  Press Enter to continue..."
    fi

    print_step "Storing PostgreSQL credentials in Vault..."
    store_in_vault "postgres" \
        "username=$PG_ADMIN_USER" \
        "password=$PG_ADMIN_PASSWORD" \
        "database=$PG_DATABASE"

    print_success "PostgreSQL credentials stored in Vault (secret/postgres)"
else
    PG_ADMIN_USER=$(get_from_vault "postgres" "username")
    PG_ADMIN_PASSWORD=$(get_from_vault "postgres" "password")
    PG_DATABASE=$(get_from_vault "postgres" "database")
    print_success "Using existing PostgreSQL credentials"
fi

# =============================================================================
# Step 2: Configure Unleash Database Credentials
# =============================================================================
print_header "Step 2: Unleash Database Credentials"

UNLEASH_EXISTS=$(secret_exists_in_vault "unleash")

if [ "$UNLEASH_EXISTS" = "true" ]; then
    print_info "Existing Unleash credentials found in Vault"

    EXISTING_UNLEASH_USER=$(get_from_vault "unleash" "username")
    EXISTING_UNLEASH_DB=$(get_from_vault "unleash" "database")

    print_info "  Username: $EXISTING_UNLEASH_USER"
    print_info "  Database: $EXISTING_UNLEASH_DB"
    echo ""

    read -p "Keep existing credentials? (Y/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Nn]$ ]]; then
        UNLEASH_EXISTS="false"
    fi
fi

if [ "$UNLEASH_EXISTS" != "true" ]; then
    echo "Configure Unleash database credentials:"
    echo ""

    read -p "  Unleash username [unleash]: " UNLEASH_USER
    UNLEASH_USER=${UNLEASH_USER:-unleash}

    read -p "  Unleash database [unleash]: " UNLEASH_DB
    UNLEASH_DB=${UNLEASH_DB:-unleash}

    echo ""
    read -p "  Generate random password? (Y/n): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Nn]$ ]]; then
        read -sp "  Enter password: " UNLEASH_PASSWORD
        echo ""
    else
        UNLEASH_PASSWORD=$(generate_password)
        echo ""
        print_info "Generated password: $UNLEASH_PASSWORD"
        echo ""
        echo -e "${RED}  Save this password! It won't be shown again.${NC}"
        read -p "  Press Enter to continue..."
    fi

    print_step "Storing Unleash credentials in Vault..."
    store_in_vault "unleash" \
        "username=$UNLEASH_USER" \
        "password=$UNLEASH_PASSWORD" \
        "database=$UNLEASH_DB"

    print_success "Unleash credentials stored in Vault (secret/unleash)"
else
    UNLEASH_USER=$(get_from_vault "unleash" "username")
    UNLEASH_PASSWORD=$(get_from_vault "unleash" "password")
    UNLEASH_DB=$(get_from_vault "unleash" "database")
    print_success "Using existing Unleash credentials"
fi

# =============================================================================
# Step 3: Store PgBouncer Userlist
# =============================================================================
print_header "Step 3: PgBouncer Userlist"

print_step "Generating PgBouncer userlist..."

# PgBouncer userlist format: "username" "password"
# We store it in Vault and External Secrets will create the K8s Secret
USERLIST_CONTENT="\"$PG_ADMIN_USER\" \"$PG_ADMIN_PASSWORD\"
\"$UNLEASH_USER\" \"$UNLEASH_PASSWORD\""

print_info "Users in userlist:"
print_info "  - $PG_ADMIN_USER"
print_info "  - $UNLEASH_USER"

store_in_vault "pgbouncer" \
    "userlist=$USERLIST_CONTENT"

print_success "PgBouncer userlist stored in Vault (secret/pgbouncer)"

# =============================================================================
# Step 4: Create PostgreSQL users (if PG is running)
# =============================================================================
print_header "Step 4: Create PostgreSQL Users"

PG_POD=$(kubectl get pod -n "$NAMESPACE" -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

if [ -z "$PG_POD" ]; then
    print_info "PostgreSQL pod not found or not running yet"
    print_info "Users will be created when PostgreSQL starts with these credentials"
    echo ""
    print_info "The postgres container uses POSTGRES_USER and POSTGRES_PASSWORD"
    print_info "from the postgres-credentials secret (synced from Vault)"
else
    PG_STATUS=$(kubectl get pod -n "$NAMESPACE" "$PG_POD" -o jsonpath='{.status.phase}' 2>/dev/null)

    if [ "$PG_STATUS" = "Running" ]; then
        print_step "PostgreSQL is running. Creating additional users..."

        # Create Unleash database and user
        kubectl exec -n "$NAMESPACE" "$PG_POD" -- sh -c "
            PGPASSWORD='$PG_ADMIN_PASSWORD' psql -U '$PG_ADMIN_USER' -d postgres <<SQL
-- Create Unleash user if not exists
DO \\\$\\\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$UNLEASH_USER') THEN
        CREATE USER $UNLEASH_USER WITH PASSWORD '$UNLEASH_PASSWORD';
    ELSE
        ALTER USER $UNLEASH_USER WITH PASSWORD '$UNLEASH_PASSWORD';
    END IF;
END
\\\$\\\$;

-- Create Unleash database if not exists
SELECT 'CREATE DATABASE $UNLEASH_DB OWNER $UNLEASH_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$UNLEASH_DB')\\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE $UNLEASH_DB TO $UNLEASH_USER;
SQL
        " 2>/dev/null && print_success "Unleash user and database created" || print_info "Could not create Unleash user (may already exist or PG not ready)"
    else
        print_info "PostgreSQL pod status: $PG_STATUS"
        print_info "Skipping user creation - PostgreSQL not ready"
    fi
fi

# =============================================================================
# Summary
# =============================================================================
print_header "Setup Complete!"

echo "Credentials stored in Vault:"
echo ""
echo "  secret/postgres"
echo "    - username: $PG_ADMIN_USER"
echo "    - password: ********"
echo "    - database: $PG_DATABASE"
echo ""
echo "  secret/unleash"
echo "    - username: $UNLEASH_USER"
echo "    - password: ********"
echo "    - database: $UNLEASH_DB"
echo ""
echo "  secret/pgbouncer"
echo "    - userlist: (contains all users for PgBouncer auth)"
echo ""
echo "External Secrets will sync these to Kubernetes Secrets:"
echo "  - postgres-credentials"
echo "  - pgbouncer-credentials"
echo "  - pgbouncer-userlist"
echo "  - unleash-credentials"
echo ""
echo "Verify sync status:"
echo "  kubectl get externalsecret -n $NAMESPACE"
echo ""
echo "Check secrets:"
echo "  kubectl get secret -n $NAMESPACE | grep -E 'postgres|pgbouncer|unleash'"
echo ""
