#!/bin/bash
set -e

# =============================================================================
# Vault Setup - Script unificado para inicialização e configuração do Vault
# =============================================================================

NAMESPACE="production"
VAULT_CREDS_FILE="$HOME/.invistus/vault-credentials.txt"

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

print_step "Verificando pré-requisitos..."

command -v kubectl &> /dev/null || { print_error "kubectl não encontrado"; exit 1; }
command -v jq &> /dev/null || { print_error "jq não encontrado"; exit 1; }
print_success "kubectl e jq disponíveis"

kubectl get namespace "$NAMESPACE" &> /dev/null || { print_error "Namespace '$NAMESPACE' não existe"; exit 1; }
print_success "Namespace '$NAMESPACE' existe"

VAULT_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -z "$VAULT_POD" ]; then
    print_error "Pod do Vault não encontrado"
    echo ""
    echo "Verifique o status do HelmRelease:"
    echo "  flux get helmrelease vault -n flux-system"
    exit 1
fi
print_success "Pod encontrado: $VAULT_POD"

# =============================================================================
# Check Vault status
# =============================================================================
print_step "Verificando status do Vault..."

# Try to get status (may fail if not initialized)
VAULT_STATUS=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status -format=json 2>/dev/null || echo '{"initialized":false,"sealed":true}')
INITIALIZED=$(echo "$VAULT_STATUS" | jq -r '.initialized')
SEALED=$(echo "$VAULT_STATUS" | jq -r '.sealed')

echo ""
print_info "Inicializado: $INITIALIZED"
print_info "Selado: $SEALED"
echo ""

# =============================================================================
# Step 1: Initialize (if needed)
# =============================================================================
if [ "$INITIALIZED" != "true" ]; then
    print_header "Passo 1: Inicialização"

    echo "O Vault precisa ser inicializado. Isso irá gerar:"
    echo "  - 5 unseal keys (necessárias 3 para desbloquear)"
    echo "  - 1 root token (acesso administrativo)"
    echo ""
    read -p "Continuar com a inicialização? (s/N): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        echo "Abortado."
        exit 0
    fi

    print_step "Inicializando Vault..."

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
# Vault Credentials - MANTENHA SEGURO!
# Gerado em: $(date)
# Namespace: $NAMESPACE

ROOT_TOKEN=$ROOT_TOKEN

UNSEAL_KEY_1=$UNSEAL_KEY_1
UNSEAL_KEY_2=$UNSEAL_KEY_2
UNSEAL_KEY_3=$UNSEAL_KEY_3
UNSEAL_KEY_4=$UNSEAL_KEY_4
UNSEAL_KEY_5=$UNSEAL_KEY_5
EOF
    chmod 600 "$VAULT_CREDS_FILE"

    print_success "Vault inicializado!"
    print_success "Credenciais salvas em: $VAULT_CREDS_FILE"
    echo ""
    echo -e "${RED}⚠️  FAÇA BACKUP DESSAS CREDENCIAIS!${NC}"
    echo -e "${RED}   Sem elas, você perderá acesso ao Vault permanentemente.${NC}"
    echo ""
    read -p "Pressione Enter para continuar..."

    SEALED="true"  # After init, vault is sealed
else
    print_success "Vault já inicializado"
fi

# =============================================================================
# Step 2: Unseal (if needed)
# =============================================================================
if [ "$SEALED" = "true" ]; then
    print_header "Passo 2: Unseal"

    # Load credentials
    if [ ! -f "$VAULT_CREDS_FILE" ]; then
        print_error "Arquivo de credenciais não encontrado: $VAULT_CREDS_FILE"
        echo ""
        echo "Se você tem as unseal keys, pode fazer unseal manualmente:"
        echo "  kubectl exec -n $NAMESPACE $VAULT_POD -- vault operator unseal <KEY>"
        exit 1
    fi

    source "$VAULT_CREDS_FILE"

    print_step "Desbloqueando Vault com 3 keys..."

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_1" > /dev/null 2>&1
    print_info "Key 1/3 ✓"

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_2" > /dev/null 2>&1
    print_info "Key 2/3 ✓"

    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault operator unseal "$UNSEAL_KEY_3" > /dev/null 2>&1
    print_info "Key 3/3 ✓"

    print_success "Vault desbloqueado!"
else
    print_success "Vault já está desbloqueado"
fi

# =============================================================================
# Step 3: Configure Kubernetes Auth
# =============================================================================
print_header "Passo 3: Configuração"

# Load credentials if not loaded
if [ -z "$ROOT_TOKEN" ]; then
    if [ -f "$VAULT_CREDS_FILE" ]; then
        source "$VAULT_CREDS_FILE"
    else
        print_error "Credenciais não encontradas"
        exit 1
    fi
fi

print_step "Configurando Kubernetes auth e secrets engine..."

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

print_success "Kubernetes auth configurado"
print_success "KV secrets engine habilitado"
print_success "Policy 'app-policy' criada"
print_success "Role 'app' criada para External Secrets"

# =============================================================================
# Step 4: Store Secrets (interactive)
# =============================================================================
print_header "Passo 4: Armazenar Secrets"

store_secret() {
    local secret_path=$1
    local secret_name=$2

    echo ""
    echo -e "${YELLOW}Configurando: $secret_name ($secret_path)${NC}"
    echo ""

    # Check if exists
    EXISTS=$(kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
        export VAULT_TOKEN='$ROOT_TOKEN'
        vault kv get secret/$secret_path > /dev/null 2>&1 && echo true || echo false
    ")

    if [ "$EXISTS" = "true" ]; then
        print_info "Secret já existe em secret/$secret_path"
        read -p "  Sobrescrever? (s/N): " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Ss]$ ]]; then
            return
        fi
    fi

    # Collect key-value pairs
    local kv_pairs=""
    echo "  Digite os pares chave=valor (linha vazia para terminar):"
    echo "  Exemplo: username=admin"
    echo ""

    while true; do
        read -p "  > " pair
        if [ -z "$pair" ]; then
            break
        fi
        if [[ "$pair" == *"="* ]]; then
            kv_pairs="$kv_pairs $pair"
        else
            print_error "  Formato inválido. Use: chave=valor"
        fi
    done

    if [ -z "$kv_pairs" ]; then
        print_info "  Nenhum valor fornecido, pulando..."
        return
    fi

    # Store in Vault
    kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- sh -c "
        export VAULT_TOKEN='$ROOT_TOKEN'
        vault kv put secret/$secret_path $kv_pairs
    " > /dev/null 2>&1

    print_success "Secret armazenado em secret/$secret_path"
}

echo "Agora você pode armazenar secrets no Vault."
echo "Secrets comuns para o ambiente Invistus:"
echo ""
echo "  1) postgres    - Credenciais do PostgreSQL"
echo "  2) redis       - Credenciais do Redis (se houver senha)"
echo "  3) api-keys    - Chaves de API externas"
echo "  4) jwt         - Secret para tokens JWT"
echo "  5) custom      - Outro secret personalizado"
echo "  0) Pular       - Configurar secrets depois"
echo ""

while true; do
    read -p "Escolha uma opção (0-5): " choice

    case $choice in
        0) break ;;
        1) store_secret "postgres" "PostgreSQL" ;;
        2) store_secret "redis" "Redis" ;;
        3) store_secret "api-keys" "API Keys" ;;
        4) store_secret "jwt" "JWT Secret" ;;
        5)
            read -p "Nome do secret path: " custom_path
            read -p "Descrição: " custom_name
            store_secret "$custom_path" "$custom_name"
            ;;
        *) echo "Opção inválida" ;;
    esac

    echo ""
    read -p "Configurar outro secret? (s/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        break
    fi
done

# =============================================================================
# Summary
# =============================================================================
print_header "Setup Completo!"

echo "Status do Vault:"
kubectl exec -n "$NAMESPACE" "$VAULT_POD" -- vault status 2>/dev/null | head -10

echo ""
echo "Credenciais salvas em:"
echo "  $VAULT_CREDS_FILE"
echo ""
echo "Comandos úteis:"
echo ""
echo "  # Ver secrets armazenados"
echo "  kubectl exec -n $NAMESPACE $VAULT_POD -- sh -c 'VAULT_TOKEN=\$ROOT_TOKEN vault kv list secret/'"
echo ""
echo "  # Unseal após restart do pod"
echo "  $0 --namespace $NAMESPACE"
echo ""
echo "  # Acessar UI do Vault (port-forward)"
echo "  kubectl port-forward -n $NAMESPACE svc/vault 8200:8200"
echo "  # Acesse: http://localhost:8200 (use o Root Token para login)"
echo ""
