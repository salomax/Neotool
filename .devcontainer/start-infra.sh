#!/usr/bin/env bash
# =============================================================================
# start-infra.sh — Sobe o stack de infraestrutura dentro do DevPod
# =============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENTS_BIN="$WORKSPACE_ROOT/.devcontainer/agents"
DEVCONTAINER_BIN="$WORKSPACE_ROOT/.devcontainer"
export PATH="$AGENTS_BIN:$DEVCONTAINER_BIN:$PATH"
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
  postgres pgbouncer kafka minio vault

# Router precisa do profile gateway e NÃO deve bloquear com --wait
# pois ele fica em erro até os serviços Kotlin estarem no ar (comportamento esperado)
echo "▶ [infra] Subindo API gateway (router)..."
docker compose \
  -f "$COMPOSE_FILE" \
  --env-file "$ENV_FILE" \
  --profile gateway \
  up -d --quiet-pull \
  router \
  && echo "✓ [infra] Router iniciado (conectará aos serviços quando estiverem no ar)" \
  || echo "⚠ [infra] Router falhou ao iniciar — verifique: docker logs neotool-graphql-router"

echo "▶ [infra] Criando buckets MinIO..."
docker exec neotool-minio mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1 || true
docker exec neotool-minio mc mb --ignore-existing local/neotool-assets-public  && echo "  ✓ neotool-assets-public" || true
docker exec neotool-minio mc mb --ignore-existing local/neotool-assets-private && echo "  ✓ neotool-assets-private" || true
docker exec neotool-minio mc anonymous set download local/neotool-assets-public >/dev/null 2>&1 || true

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
    local neotool="$WORKSPACE_ROOT/scripts/cli/cli"
    if [[ ! -f "$neotool" ]]; then
        echo "⚠ [vault] CLI não encontrado — pulando restore"
        return 1
    fi
    VAULT_TOKEN="$VAULT_TOKEN" "$neotool" vault restore \
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

# =============================================================================
# API Keys: carregar de ~/.vault-keys/api-keys.env (mesmo bind-mount do Vault)
# =============================================================================
# Formato do arquivo no Mac (~/.vault-keys/api-keys.env):
#   OPENAI_API_KEY=sk-...
#   OPENAI_API_KEY=sk-...          # opcional
#
# Cloud / CI: export API_KEYS_B64=$(base64 -i ~/.vault-keys/api-keys.env)
# =============================================================================

API_KEYS_FILE="${HOME}/.vault-keys/api-keys.env"
API_KEYS_MARKER="# api-keys.env (gerado por start-infra.sh)"

load_api_keys() {
    local src="$1"
    # Evita duplicatas: remove bloco anterior antes de reescrever
    if grep -qF "$API_KEYS_MARKER" "${HOME}/.bashrc" 2>/dev/null; then
        sed -i "/$API_KEYS_MARKER/,/# fim api-keys/d" "${HOME}/.bashrc"
    fi
    {
        echo "$API_KEYS_MARKER"
        grep -E '^[A-Z_]+=.+' "$src" | sed 's/^/export /'
        echo "# fim api-keys"
    } >> "${HOME}/.bashrc"
    echo "✓ [api-keys] Variáveis carregadas em ~/.bashrc"
}

if [ -f "$API_KEYS_FILE" ]; then
    load_api_keys "$API_KEYS_FILE"
    # Também exporta no shell atual (bashrc só vale em sessões interativas)
    set -a; source "$API_KEYS_FILE"; set +a
elif [ -n "${API_KEYS_B64:-}" ]; then
    API_KEYS_TMP="$(mktemp /tmp/api-keys-XXXXXX.env)"
    echo "$API_KEYS_B64" | base64 -d > "$API_KEYS_TMP"
    load_api_keys "$API_KEYS_TMP"
    set -a; source "$API_KEYS_TMP"; set +a
    rm -f "$API_KEYS_TMP"
else
    echo "⚠ [api-keys] Nenhum api-keys.env encontrado."
    echo "   Mac:   crie ~/.vault-keys/api-keys.env com OPENAI_API_KEY e GH_TOKEN"
    echo "   Cloud: export API_KEYS_B64=\$(base64 -i ~/.vault-keys/api-keys.env)"
fi


# =============================================================================
# Git: usar HTTPS com GH_TOKEN em vez de SSH (evita bloqueio de porta 22)
# =============================================================================
if [ -n "${GH_TOKEN:-}" ]; then
    _GH="https://${GH_TOKEN}@github.com/"
    git config --global push.autoSetupRemote true
    # Remove entradas antigas para evitar duplicatas
    git config --global --remove-section "url.${_GH}" 2>/dev/null || true
    # Adiciona as duas regras com --add
    git config --global "url.${_GH}.insteadOf" "git@github.com:"
    git config --global --add "url.${_GH}.insteadOf" "https://github.com/"
    echo "✓ [git] HTTPS com GH_TOKEN configurado (push via HTTPS)"
else
    echo "⚠ [git] GH_TOKEN não definido — push via SSH pode falhar no DevPod"
fi

# =============================================================================
# neotool-dev-agent — Slack Socket Mode bridge para pipeline Slack → Agentes
# =============================================================================
# Controlado por NEOTOOL_AGENT_ENABLED em ~/.vault-keys/api-keys.env
#   NEOTOOL_AGENT_ENABLED=true  → sobe o bridge Socket Mode (padrão)
#   NEOTOOL_AGENT_ENABLED=false → skip (trabalho manual)
#
# Tokens Slack: ~/.vault-keys/devpod-N.env
#   SLACK_APP_TOKEN=xapp-...   (App-Level Token — Socket Mode)
#   SLACK_BOT_TOKEN=xoxb-...   (Bot Token)
#
# Cloudflare Tunnel: NÃO é iniciado automaticamente.
# Use: neotool dev tunnel start <n>  (apenas para acesso web externo)
# =============================================================================

NEOTOOL_LOG="${HOME}/.local/share/neotool"
mkdir -p "$NEOTOOL_LOG"

# Carrega api-keys.env e devpod-N.env antes de subir o bridge
if [ -f "${HOME}/.vault-keys/api-keys.env" ]; then
    set -a; source "${HOME}/.vault-keys/api-keys.env"; set +a
fi
_DEVPOD_N="${NEOTOOL_DEVPOD_N:-1}"
_DEVPOD_ENV="${HOME}/.vault-keys/devpod-${_DEVPOD_N}.env"
if [ -f "$_DEVPOD_ENV" ]; then
    set -a; source "$_DEVPOD_ENV"; set +a
    # Adiciona ao ~/.bashrc para o shell interativo também
    if grep -qF "# devpod-${_DEVPOD_N}.env" "${HOME}/.bashrc" 2>/dev/null; then
        sed -i "/# devpod-${_DEVPOD_N}.env/,/# fim devpod-${_DEVPOD_N}/d" "${HOME}/.bashrc"
    fi
    {
        echo "# devpod-${_DEVPOD_N}.env (gerado por start-infra.sh)"
        grep -E '^[A-Z_]+=.+' "$_DEVPOD_ENV" | sed 's/^/export /'
        echo "# fim devpod-${_DEVPOD_N}"
    } >> "${HOME}/.bashrc"
    echo "✓ [neotool] devpod-${_DEVPOD_N}.env carregado"
else
    echo "⚠ [neotool] devpod-${_DEVPOD_N}.env não encontrado."
    echo "   No Mac: cat > ~/.vault-keys/devpod-${_DEVPOD_N}.env << EOF"
    echo "   SLACK_APP_TOKEN=xapp-1-..."
    echo "   SLACK_BOT_TOKEN=xoxb-..."
    echo "   EOF"
    echo "   Depois: neotool dev agent restart"
fi

if [ "${NEOTOOL_AGENT_ENABLED:-true}" = "true" ]; then
    echo "▶ [neotool-dev-agent] Iniciando bridge na porta 3285..."
    NEOTOOL_AGENT_PORT=3285 \
    nohup python3 /workspaces/invistus/.devcontainer/neotool-dev-agent.py \
        > "$NEOTOOL_LOG/agent.log" 2>&1 &
    sleep 2
    if curl -sf "http://127.0.0.1:3285/health" >/dev/null 2>&1; then
        echo "✓ [neotool-dev-agent] Pronto em http://127.0.0.1:3285"
    else
        echo "⚠ [neotool-dev-agent] Não respondeu — verifique: tail $NEOTOOL_LOG/agent.log"
    fi
    echo "   Tunnel (web):  execute 'neotool dev tunnel start <n>' para acesso web externo"

    # Inicia o watchdog — monitora agent e tunnel, reinicia automaticamente se morrerem
    echo "▶ [watchdog] Iniciando monitoramento..."
    nohup /workspaces/invistus/.devcontainer/neotool-watchdog > /dev/null 2>&1 &
    sleep 1
    if [ -f "$NEOTOOL_LOG/watchdog.pid" ] && kill -0 "$(cat $NEOTOOL_LOG/watchdog.pid)" 2>/dev/null; then
        echo "✓ [watchdog] Ativo (PID $(cat $NEOTOOL_LOG/watchdog.pid)) — logs: $NEOTOOL_LOG/watchdog.log"
    else
        echo "⚠ [watchdog] Nao iniciou — verifique: $NEOTOOL_LOG/watchdog.log"
    fi
else
    echo "⚠ [neotool-dev-agent] Desabilitado (NEOTOOL_AGENT_ENABLED=false)"
fi

echo ""
echo "✓ Infra stack rodando:"
docker ps --format "table {{.Names}}\t{{.Status}}" \
  | grep -E "NAME|neotool-(postgres|pgbouncer|kafka|minio|vault)" || true

echo ""
echo "Próximos passos (manualmente conforme necessidade):"
echo "  Observability:   docker compose -f infra/docker/docker-compose.local.yml --profile observability up -d"
echo "  Serviços Kotlin: .devcontainer/start-dev.sh kotlin"

# =============================================================================
# Agent Skills: nada a fazer aqui
# =============================================================================
# Fonte única em .agents/skills/ (padrão agentskills.io), versionada no repo.
# Codex e Cursor leem .agents/skills/ diretamente (convenção nativa). Claude
# Code só descobre .claude/skills/, que é um symlink versionado no git
# (.claude/skills -> ../.agents/skills) — já materializado pelo checkout,
# sem sync em runtime.
# =============================================================================
