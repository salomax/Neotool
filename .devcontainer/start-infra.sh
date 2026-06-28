#!/usr/bin/env bash
# =============================================================================
# start-infra.sh — Sobe o stack de infraestrutura dentro do DevPod
# =============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$WORKSPACE_ROOT/infra/docker/docker-compose.local.yml"
ENV_FILE="$WORKSPACE_ROOT/infra/docker/.env.local"

# Lê apenas as vars necessárias do .env.local (evita executar linhas YAML-style com ":")
POSTGRES_USER=$(grep -E "^POSTGRES_USER=" "$ENV_FILE" | cut -d= -f2)
POSTGRES_PASSWORD=$(grep -E "^POSTGRES_PASSWORD=" "$ENV_FILE" | cut -d= -f2)
POSTGRES_DB=$(grep -E "^POSTGRES_DB=" "$ENV_FILE" | cut -d= -f2)

echo "▶ [infra] Aguardando Docker daemon..."
timeout 30 bash -c 'until docker info >/dev/null 2>&1; do sleep 1; done'
echo "✓ [infra] Docker pronto"

echo "▶ [infra] Subindo stack base (banco + kafka + storage + secrets)..."
docker compose \
  -f "$COMPOSE_FILE" \
  --env-file "$ENV_FILE" \
  up -d --wait --quiet-pull \
  postgres pgbouncer kafka minio vault router

echo "▶ [infra] Inicializando bancos auxiliares (unleash_db, umami_db)..."
docker compose \
  -f "$COMPOSE_FILE" \
  --env-file "$ENV_FILE" \
  run --rm \
  -e POSTGRES_USER="$POSTGRES_USER" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -e POSTGRES_DB="$POSTGRES_DB" \
  postgres-init \
  && echo "✓ Bancos auxiliares inicializados" \
  || echo "⚠ postgres-init falhou (bancos podem já existir — ignorando)"

# =============================================================================
# Vault: restaurar segredos (JWT keys e outros)
# =============================================================================
# O Vault sobe vazio a cada criação de workspace.  Para evitar atrito manual,
# restauramos automaticamente a partir de:
#
#   1. Arquivo de backup montado do host: ~/.vault-keys/backup.json
#      (Montado via devcontainer.json como bind-mount read-only)
#
#   2. Variável de ambiente VAULT_JWT_BACKUP_B64 (base64 do backup.json)
#      Útil para workspaces em cloud onde o arquivo local não existe.
#      Exportar antes de `devpod up`: export VAULT_JWT_BACKUP_B64=$(base64 -i ~/.vault-keys/backup.json)
#
# Formato do backup.json (produzido pelo neotool vault backup):
#   { "kid-1": { "privateKey": "...", "publicKey": "..." } }
# =============================================================================

VAULT_TOKEN="${VAULT_TOKEN:-myroot}"
VAULT_BACKUP_FILE="${HOME}/.vault-keys/backup.json"
VAULT_BACKUP_TMP=""

vault_restore() {
    local backup_file="$1"
    echo "▶ [vault] Restaurando segredos JWT (secret/jwt/keys)..."
    VAULT_TOKEN="$VAULT_TOKEN" neotool vault restore \
        --secret-path secret/jwt/keys \
        --input "$backup_file" \
        && echo "✓ [vault] JWT keys restauradas" \
        || echo "⚠ [vault] Falha ao restaurar JWT keys — verifique manualmente"
}

if [ -f "$VAULT_BACKUP_FILE" ]; then
    # Caminho 1: arquivo montado do host Mac (bind-mount é root:root — garante leitura pelo dev user)
    sudo chmod 644 "$VAULT_BACKUP_FILE" 2>/dev/null || true
    vault_restore "$VAULT_BACKUP_FILE"

elif [ -n "${VAULT_JWT_BACKUP_B64:-}" ]; then
    # Caminho 2: env var (cloud / CI)
    VAULT_BACKUP_TMP="$(mktemp /tmp/vault-backup-XXXXXX.json)"
    echo "$VAULT_JWT_BACKUP_B64" | base64 -d > "$VAULT_BACKUP_TMP"
    vault_restore "$VAULT_BACKUP_TMP"
    rm -f "$VAULT_BACKUP_TMP"

else
    echo "⚠ [vault] Nenhum backup encontrado."
    echo "   Mac:   coloque o backup em ~/.vault-keys/backup.json"
    echo "   Cloud: export VAULT_JWT_BACKUP_B64=\$(base64 -i ~/.vault-keys/backup.json)"
    echo "   Manual: VAULT_TOKEN=myroot neotool vault restore --secret-path secret/jwt/keys --input <arquivo>"
fi

echo ""
echo "✓ Infra stack rodando:"
docker ps --format "table {{.Names}}\t{{.Status}}" \
  | grep -E "NAME|neotool-(postgres|pgbouncer|kafka|minio|vault)" || true

echo ""
echo "Próximos passos (manualmente conforme necessidade):"
echo "  Observability:   docker compose -f infra/docker/docker-compose.local.yml --profile observability up -d"
echo "  Serviços Kotlin: .devcontainer/start-dev.sh kotlin"
