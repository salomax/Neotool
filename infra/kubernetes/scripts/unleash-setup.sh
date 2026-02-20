#!/bin/bash
set -e

# =============================================================================
# Unleash Setup - Configure Unleash secrets in Vault
# =============================================================================
#
# This script adds the Unleash API tokens to Vault.
# Run this AFTER vault-setup.sh and postgres-setup.sh
#
# Prerequisites:
#   - Vault initialized and unsealed
#   - Unleash database credentials already in Vault (from postgres-setup.sh)
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

# =============================================================================
# Pre-flight checks
# =============================================================================
print_header "Unleash Setup"

print_step "Checking prerequisites..."

# Check for required tools
command -v kubectl &> /dev/null || { print_error "kubectl not found"; exit 1; }
command -v jq &> /dev/null || { print_error "jq not found"; exit 1; }
command -v openssl &> /dev/null || { print_error "openssl not found"; exit 1; }
print_success "Required tools available"

# Check Vault credentials file
if [ ! -f "$VAULT_CREDS_FILE" ]; then
    print_error "Vault credentials file not found: $VAULT_CREDS_FILE"
    echo ""
    echo "Run vault-setup.sh first to initialize Vault."
    exit 1
fi
source "$VAULT_CREDS_FILE"
print_success "Vault credentials loaded"

# Find Vault pod
VAULT_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -z "$VAULT_POD" ]; then
    print_error "Vault pod not found in namespace '$NAMESPACE'"
    exit 1
fi
print_success "Vault pod found: $VAULT_POD"

# Check Vault is unsealed
VAULT_STATUS=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status -format=json 2>/dev/null || echo '{"sealed":true}')
SEALED=$(echo "$VAULT_STATUS" | jq -r '.sealed')
if [ "$SEALED" = "true" ]; then
    print_error "Vault is sealed. Run vault-setup.sh to unseal."
    exit 1
fi
print_success "Vault is unsealed"

# =============================================================================
# Check existing Unleash secrets
# =============================================================================
print_header "Checking Existing Secrets"

print_step "Reading current Unleash secrets from Vault..."

EXISTING_SECRET=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
    VAULT_TOKEN='$ROOT_TOKEN'
    export VAULT_TOKEN
    vault kv get -format=json secret/unleash 2>/dev/null || echo '{}'
" 2>/dev/null)

if [ "$EXISTING_SECRET" = "{}" ]; then
    print_error "Unleash base credentials not found in Vault"
    echo ""
    echo "Run postgres-setup.sh first to create Unleash database credentials."
    exit 1
fi

# Check if tokens already exist
EXISTING_SERVER_TOKEN=$(echo "$EXISTING_SECRET" | jq -r '.data.data["server-token"] // empty')
EXISTING_PROXY_TOKEN=$(echo "$EXISTING_SECRET" | jq -r '.data.data["proxy-token"] // empty')

if [ -n "$EXISTING_SERVER_TOKEN" ] && [ -n "$EXISTING_PROXY_TOKEN" ]; then
    print_success "Unleash tokens already configured"
    echo ""
    print_info "server-token: ${EXISTING_SERVER_TOKEN:0:30}..."
    print_info "proxy-token: ${EXISTING_PROXY_TOKEN:0:30}..."
    echo ""
    read -p "Do you want to regenerate tokens? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Keeping existing tokens."
        exit 0
    fi
fi

# =============================================================================
# Generate and store tokens
# =============================================================================
print_header "Generating Unleash API Tokens"

# Extract existing values
UNLEASH_USER=$(echo "$EXISTING_SECRET" | jq -r '.data.data.username')
UNLEASH_PASS=$(echo "$EXISTING_SECRET" | jq -r '.data.data.password')
UNLEASH_DB=$(echo "$EXISTING_SECRET" | jq -r '.data.data.database')

print_step "Generating secure random tokens..."

# Generate tokens in Unleash format
# Format: <scope>:<environment>.<random>
SERVER_TOKEN="*:production.$(openssl rand -hex 32)"
PROXY_TOKEN="default:production.$(openssl rand -hex 32)"

print_success "Tokens generated"
print_info "server-token: ${SERVER_TOKEN:0:30}..."
print_info "proxy-token: ${PROXY_TOKEN:0:30}..."

print_step "Storing tokens in Vault..."

kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
    VAULT_TOKEN='$ROOT_TOKEN'
    export VAULT_TOKEN
    vault kv put secret/unleash \
        username='$UNLEASH_USER' \
        password='$UNLEASH_PASS' \
        database='$UNLEASH_DB' \
        server-token='$SERVER_TOKEN' \
        proxy-token='$PROXY_TOKEN'
" > /dev/null 2>&1

print_success "Tokens stored in Vault"

# =============================================================================
# Trigger ExternalSecrets refresh
# =============================================================================
print_header "Refreshing ExternalSecrets"

print_step "Triggering ExternalSecrets to sync..."

kubectl annotate externalsecret unleash-proxy-token -n "$NAMESPACE" \
    force-sync="$(date +%s)" --overwrite 2>/dev/null || true
kubectl annotate externalsecret unleash-server-token -n "$NAMESPACE" \
    force-sync="$(date +%s)" --overwrite 2>/dev/null || true

sleep 5

# Check sync status
PROXY_STATUS=$(kubectl get externalsecret unleash-proxy-token -n "$NAMESPACE" -o jsonpath='{.status.conditions[0].reason}' 2>/dev/null || echo "Unknown")
SERVER_STATUS=$(kubectl get externalsecret unleash-server-token -n "$NAMESPACE" -o jsonpath='{.status.conditions[0].reason}' 2>/dev/null || echo "Unknown")

if [ "$PROXY_STATUS" = "SecretSynced" ] && [ "$SERVER_STATUS" = "SecretSynced" ]; then
    print_success "ExternalSecrets synced successfully"
else
    print_info "unleash-proxy-token: $PROXY_STATUS"
    print_info "unleash-server-token: $SERVER_STATUS"
    echo ""
    echo "If not synced, wait a moment and check with:"
    echo "  kubectl get externalsecrets -n $NAMESPACE"
fi

# =============================================================================
# Summary
# =============================================================================
print_header "Setup Complete!"

echo "Unleash API tokens have been configured in Vault."
echo ""
echo "Tokens stored at: secret/unleash"
echo "  - server-token: For server-to-server API access"
echo "  - proxy-token: For Unleash Edge/Proxy client access"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo ""
echo "  1. Restart Unleash pods to pick up new tokens (if already running):"
echo "     kubectl rollout restart deployment unleash-server -n $NAMESPACE"
echo ""
echo "  2. Verify Unleash is running:"
echo "     kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=unleash"
echo ""
echo "  3. Access Unleash UI (port-forward):"
echo "     kubectl port-forward -n $NAMESPACE svc/unleash-server 4242:4242"
echo "     # Then open: http://localhost:4242"
echo ""
