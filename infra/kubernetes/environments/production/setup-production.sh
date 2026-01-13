#!/bin/bash
set -e

# Production K3S Setup - Idempotent
# Run this script multiple times safely - it only does what's needed

NAMESPACE="production"
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}ℹ${NC}  $1"
}

log_success() {
    echo -e "${GREEN}✓${NC}  $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC}  $1"
}

log_error() {
    echo -e "${RED}✗${NC}  $1"
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 not found. Please install it first."
        exit 1
    fi
}

echo "================================================"
echo "  Neotool Production Setup (Idempotent)"
echo "================================================"
echo ""

# ============================================
# Prerequisites Check
# ============================================
log_info "Checking prerequisites..."

check_command kubectl
check_command jq
log_success "All prerequisites installed"
echo ""

# ============================================
# Namespace
# ============================================
log_info "Checking namespace '$NAMESPACE'..."

if kubectl get namespace $NAMESPACE &>/dev/null; then
    log_success "Namespace already exists"
else
    log_info "Creating namespace..."
    kubectl create namespace $NAMESPACE
    log_success "Namespace created"
fi
echo ""

# ============================================
# Linkerd Injection
# ============================================
log_info "Checking Linkerd injection..."

if kubectl get namespace $NAMESPACE -o jsonpath='{.metadata.annotations.linkerd\.io/inject}' 2>/dev/null | grep -q "enabled"; then
    log_success "Linkerd injection already enabled"
else
    log_info "Enabling Linkerd injection..."
    kubectl annotate namespace $NAMESPACE linkerd.io/inject=enabled --overwrite
    log_success "Linkerd injection enabled"
fi
echo ""

# ============================================
# Vault
# ============================================
log_info "Checking Vault deployment..."

if kubectl get statefulset vault -n $NAMESPACE &>/dev/null; then
    log_success "Vault already deployed"
else
    log_info "Deploying Vault..."
    kubectl apply -f vault/vault-serviceaccount.yaml
    kubectl apply -f vault/vault-config.yaml
    kubectl apply -f vault/vault-deployment.yaml

    log_info "Waiting for Vault pod to be ready..."
    kubectl wait --for=condition=ready pod -l app=vault -n $NAMESPACE --timeout=300s || true
    log_success "Vault deployed"
fi
echo ""

# Get Vault pod name
VAULT_POD=$(kubectl get pod -n $NAMESPACE -l app=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

if [ -z "$VAULT_POD" ]; then
    log_error "Vault pod not found. Deployment may have failed."
    exit 1
fi

# ============================================
# Vault Initialization
# ============================================
log_info "Checking Vault initialization..."

VAULT_INITIALIZED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.initialized' || echo "false")

if [ "$VAULT_INITIALIZED" = "true" ]; then
    log_success "Vault already initialized"

    # Check if credentials file exists
    if [ -f "$VAULT_CREDS_FILE" ]; then
        log_success "Vault credentials found at: $VAULT_CREDS_FILE"
    else
        log_warning "Vault is initialized but credentials file not found"
        log_warning "If you need to access Vault, retrieve credentials manually"
    fi
else
    log_info "Initializing Vault..."

    # Create credentials directory
    mkdir -p "$(dirname "$VAULT_CREDS_FILE")"

    # Initialize Vault
    INIT_OUTPUT=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator init \
        -key-shares=5 \
        -key-threshold=3 \
        -format=json)

    # Extract keys and token
    UNSEAL_KEY_1=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[0]')
    UNSEAL_KEY_2=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[1]')
    UNSEAL_KEY_3=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[2]')
    UNSEAL_KEY_4=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[3]')
    UNSEAL_KEY_5=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[4]')
    ROOT_TOKEN=$(echo "$INIT_OUTPUT" | jq -r '.root_token')

    # Save credentials
    cat > "$VAULT_CREDS_FILE" <<EOF
# Vault Credentials - KEEP SECURE!
# Generated: $(date)

ROOT_TOKEN=$ROOT_TOKEN

UNSEAL_KEY_1=$UNSEAL_KEY_1
UNSEAL_KEY_2=$UNSEAL_KEY_2
UNSEAL_KEY_3=$UNSEAL_KEY_3
UNSEAL_KEY_4=$UNSEAL_KEY_4
UNSEAL_KEY_5=$UNSEAL_KEY_5
EOF

    chmod 600 "$VAULT_CREDS_FILE"

    log_success "Vault initialized"
    log_warning "Credentials saved to: $VAULT_CREDS_FILE"
    log_warning "BACKUP THESE CREDENTIALS SECURELY!"
fi
echo ""

# ============================================
# Vault Unseal
# ============================================
log_info "Checking Vault seal status..."

VAULT_SEALED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- vault status -format=json 2>/dev/null | jq -r '.sealed')

if [ "$VAULT_SEALED" = "false" ]; then
    log_success "Vault is unsealed"
else
    log_info "Vault is sealed. Unsealing..."

    # Load credentials
    if [ ! -f "$VAULT_CREDS_FILE" ]; then
        log_error "Credentials file not found at: $VAULT_CREDS_FILE"
        log_error "Cannot unseal Vault automatically"
        log_info "Run manually: ./vault/03-unseal-vault.sh"
        exit 1
    fi

    source "$VAULT_CREDS_FILE"

    # Unseal with 3 keys
    kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_1" > /dev/null 2>&1
    kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_2" > /dev/null 2>&1
    kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal "$UNSEAL_KEY_3" > /dev/null 2>&1

    log_success "Vault unsealed"
fi
echo ""

# ============================================
# Vault Kubernetes Auth
# ============================================
log_info "Checking Vault Kubernetes authentication..."

source "$VAULT_CREDS_FILE"

AUTH_ENABLED=$(kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'
vault auth list -format=json 2>/dev/null | jq -r 'has(\"kubernetes/\")' || echo false
")

if [ "$AUTH_ENABLED" = "true" ]; then
    log_success "Kubernetes auth already configured"
else
    log_info "Configuring Kubernetes auth..."

    kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'

# Enable Kubernetes auth
vault auth enable kubernetes 2>/dev/null || true

# Configure Kubernetes auth
vault write auth/kubernetes/config \
    kubernetes_host=\"https://\$KUBERNETES_PORT_443_TCP_ADDR:443\" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

# Enable KV secrets engine
vault secrets enable -path=secret kv-v2 2>/dev/null || true

# Create policy
vault policy write app-policy - <<EOF
path \"secret/data/*\" {
  capabilities = [\"read\", \"list\"]
}
EOF

# Create Kubernetes role
vault write auth/kubernetes/role/app \
    bound_service_account_names=default \
    bound_service_account_namespaces=$NAMESPACE \
    policies=app-policy \
    ttl=24h
" > /dev/null 2>&1

    log_success "Kubernetes auth configured"
fi
echo ""

# ============================================
# PostgreSQL Credentials
# ============================================
log_info "Checking PostgreSQL credentials in Vault..."

PG_CREDS_EXIST=$(kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'
vault kv get secret/postgres > /dev/null 2>&1 && echo true || echo false
")

if [ "$PG_CREDS_EXIST" = "true" ]; then
    log_success "PostgreSQL credentials already exist in Vault"
else
    log_info "PostgreSQL credentials not found in Vault"
    log_info "Generating secure credentials..."

    PG_USER="neotool"
    PG_DB="neotool_production"
    PG_PASSWORD=$(openssl rand -base64 32)

    kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c "
export VAULT_TOKEN='$ROOT_TOKEN'
vault kv put secret/postgres \
    username='$PG_USER' \
    password='$PG_PASSWORD' \
    database='$PG_DB'
" > /dev/null 2>&1

    log_success "PostgreSQL credentials stored in Vault"
    log_info "Username: $PG_USER"
    log_info "Database: $PG_DB"
    log_info "Password: [stored in Vault]"
fi
echo ""

# ============================================
# Summary
# ============================================
echo "================================================"
echo "  Production Setup Complete!"
echo "================================================"
echo ""
log_success "Namespace: $NAMESPACE"
log_success "Linkerd injection: enabled"
log_success "Vault: deployed and configured"
log_success "PostgreSQL credentials: ready"
echo ""
log_info "Vault credentials: $VAULT_CREDS_FILE"
log_warning "BACKUP THIS FILE SECURELY!"
echo ""
echo "Next steps:"
echo "  1. Install External Secrets Operator:"
echo "     helm repo add external-secrets https://charts.external-secrets.io"
echo "     helm install external-secrets external-secrets/external-secrets -n $NAMESPACE"
echo ""
echo "  2. Deploy PostgreSQL:"
echo "     kubectl apply -f postgres/"
echo ""
echo "  3. Deploy applications:"
echo "     kubectl apply -k ."
echo ""
