#!/bin/bash
set -e

# =============================================================================
# Vault Setup - Unified script for Vault initialization and configuration
# =============================================================================

NAMESPACE="production"
VAULT_CREDS_FILE="$HOME/.neotool/vault-credentials.txt"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --namespace=*) NAMESPACE="${1#*=}"; shift ;;
        --namespace) NAMESPACE="$2"; shift 2 ;;
        -h|--help)
            echo "Usage: $0 [--namespace NAMESPACE]"
            echo ""
            echo "Unified Vault setup script - handles init, unseal, and configuration"
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# =============================================================================
# Pre-flight checks
# =============================================================================
print_header "Vault Setup"

print_step "Checking prerequisites..."

command -v kubectl &> /dev/null || { print_error "kubectl not found"; exit 1; }
command -v jq &> /dev/null || { print_error "jq not found"; exit 1; }
print_success "kubectl and jq available"

kubectl get namespace "$NAMESPACE" &> /dev/null || { print_error "Namespace '$NAMESPACE' not found"; exit 1; }
print_success "Namespace '$NAMESPACE' exists"

VAULT_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -z "$VAULT_POD" ]; then
    print_error "Vault pod not found"
    echo ""
    echo "Check the HelmRelease status:"
    echo "  flux get helmrelease vault -n flux-system"
    exit 1
fi
print_success "Pod found: $VAULT_POD"

# =============================================================================
# Check Vault status
# =============================================================================
print_step "Checking Vault status..."

# Try to get status (may fail if not initialized)
VAULT_STATUS=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status -format=json 2>/dev/null || echo '{"initialized":false,"sealed":true}')
INITIALIZED=$(echo "$VAULT_STATUS" | jq -r '.initialized')
SEALED=$(echo "$VAULT_STATUS" | jq -r '.sealed')

echo ""
print_info "Initialized: $INITIALIZED"
print_info "Sealed: $SEALED"
echo ""

# =============================================================================
# Step 1: Initialize (if needed)
# =============================================================================
if [ "$INITIALIZED" != "true" ]; then
    print_header "Step 1: Initialization"

    echo "Vault needs to be initialized. This will generate:"
    echo "  - 5 unseal keys (3 required to unlock)"
    echo "  - 1 root token (admin access)"
    echo ""
    read -p "Continue with initialization? (y/N): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi

    print_step "Initializing Vault..."

    INIT_OUTPUT=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator init \
        -key-shares=5 \
        -key-threshold=3 \
        -format=json)

    # Extract credentials
    UNSEAL_KEY_1=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[0]')
    UNSEAL_KEY_2=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[1]')
    UNSEAL_KEY_3=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[2]')
    UNSEAL_KEY_4=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[3]')
    UNSEAL_KEY_5=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[4]')
    ROOT_TOKEN=$(echo "$INIT_OUTPUT" | jq -r '.root_token')

    # Save credentials
    mkdir -p "$(dirname "$VAULT_CREDS_FILE")"
    cat > "$VAULT_CREDS_FILE" <<EOF
# Vault Credentials - KEEP SECURE!
# Generated: $(date)
# Namespace: $NAMESPACE

ROOT_TOKEN=$ROOT_TOKEN

UNSEAL_KEY_1=$UNSEAL_KEY_1
UNSEAL_KEY_2=$UNSEAL_KEY_2
UNSEAL_KEY_3=$UNSEAL_KEY_3
UNSEAL_KEY_4=$UNSEAL_KEY_4
UNSEAL_KEY_5=$UNSEAL_KEY_5
EOF
    chmod 600 "$VAULT_CREDS_FILE"

    print_success "Vault initialized!"
    print_success "Credentials saved to: $VAULT_CREDS_FILE"
    echo ""
    echo -e "${RED}WARNING: BACKUP THESE CREDENTIALS!${NC}"
    echo -e "${RED}         Without them, you will permanently lose access to Vault.${NC}"
    echo ""
    read -p "Press Enter to continue..."

    SEALED="true"  # After init, vault is sealed
else
    print_success "Vault already initialized"
fi

# =============================================================================
# Step 2: Unseal (if needed)
# =============================================================================
if [ "$SEALED" = "true" ]; then
    print_header "Step 2: Unseal"

    # Load credentials
    if [ ! -f "$VAULT_CREDS_FILE" ]; then
        print_error "Credentials file not found: $VAULT_CREDS_FILE"
        echo ""
        echo "If you have the unseal keys, you can unseal manually:"
        echo "  kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal <KEY>"
        exit 1
    fi

    source "$VAULT_CREDS_FILE"

    print_step "Unsealing Vault with 3 keys..."

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_1" > /dev/null 2>&1
    print_info "Key 1/3 ✓"

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_2" > /dev/null 2>&1
    print_info "Key 2/3 ✓"

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_3" > /dev/null 2>&1
    print_info "Key 3/3 ✓"

    print_success "Vault unsealed!"
else
    print_success "Vault already unsealed"
fi

# =============================================================================
# Step 3: Configure Kubernetes Auth
# =============================================================================
print_header "Step 3: Configuration"

# Load credentials if not loaded
if [ -z "$ROOT_TOKEN" ]; then
    if [ -f "$VAULT_CREDS_FILE" ]; then
        source "$VAULT_CREDS_FILE"
    else
        print_error "Credentials not found"
        exit 1
    fi
fi

print_step "Configuring Kubernetes auth and secrets engine..."

kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
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

# Create policy for apps
vault policy write app-policy - <<POLICY
path \"secret/data/*\" {
  capabilities = [\"read\", \"list\"]
}
path \"secret/metadata/*\" {
  capabilities = [\"read\", \"list\"]
}
POLICY

# Create Kubernetes role
vault write auth/kubernetes/role/app \
    bound_service_account_names=external-secrets-vault-auth \
    bound_service_account_namespaces=$NAMESPACE \
    policies=app-policy \
    ttl=24h
" > /dev/null 2>&1

print_success "Kubernetes auth configured"
print_success "KV secrets engine enabled"
print_success "Policy 'app-policy' created"
print_success "Role 'app' created for External Secrets"

# =============================================================================
# Summary
# =============================================================================
print_header "Setup Complete!"

echo "Vault status:"
kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status 2>/dev/null | head -10

echo ""
echo "Credentials saved at:"
echo "  $VAULT_CREDS_FILE"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo ""
echo "  1. Configure database credentials:"
echo "     ./postgres-setup.sh --namespace $NAMESPACE"
echo ""
echo "  2. Configure other secrets as needed"
echo ""
echo "Useful commands:"
echo ""
echo "  # List stored secrets"
echo "  kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c 'VAULT_TOKEN=\$ROOT_TOKEN vault kv list secret/'"
echo ""
echo "  # Unseal after pod restart"
echo "  $0 --namespace $NAMESPACE"
echo ""
echo "  # Access Vault UI (port-forward)"
echo "  kubectl port-forward -n $NAMESPACE svc/vault 8200:8200"
echo "  # Then open: http://localhost:8200 (use Root Token)"
echo ""
