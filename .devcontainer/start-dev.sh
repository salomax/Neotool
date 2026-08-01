#!/usr/bin/env bash
# =============================================================================
# start-dev.sh — Sobe os serviços de desenvolvimento em janelas tmux separadas
#
# Uso:
#   .devcontainer/start-dev.sh          # sobe tudo
#   .devcontainer/start-dev.sh web      # só o frontend
#   .devcontainer/start-dev.sh kotlin   # só os serviços Kotlin
#
# Ver logs:  tmux attach -t dev
# Navegar:   Ctrl+B W
# Sair:      Ctrl+B D
# Parar:     tmux kill-session -t dev
# =============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SESSION="dev"
MODE="${1:-all}"

if ! command -v tmux &>/dev/null; then
    echo "✗ tmux não encontrado. Instale com: sudo apt-get install -y tmux"
    exit 1
fi

# Cria sessão com janela inicial chamada "shell" (janela de controle)
if ! tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux new-session -d -s "$SESSION" -n "shell"
    echo "✓ Sessão tmux '$SESSION' criada"
else
    echo "✓ Sessão tmux '$SESSION' já existe"
fi

new_window() {
    local name="$1"
    local cmd="$2"
    # Mata janela existente com mesmo nome para evitar duplicatas
    tmux kill-window -t "$SESSION:$name" 2>/dev/null || true
    tmux new-window -t "$SESSION" -n "$name"
    tmux send-keys -t "$SESSION:$name" "$cmd" Enter
    echo "  ✓ $name"
}

start_web() {
    echo "▶ Web (Next.js :3000)"
    new_window "web" "cd $WORKSPACE_ROOT/web && pnpm install --silent && pnpm run dev"
}

start_kotlin_service() {
    local name="$1"
    local port="$2"
    local env_file="$WORKSPACE_ROOT/service/kotlin/$name/.env.local"
    local load_env=""
    if [ -f "$env_file" ]; then
        load_env="set -a && source $env_file && set +a && "
    fi
    echo "▶ $name (:$port)"
    new_window "$name" "bash -c '${load_env}export MICRONAUT_SERVER_HOST=\"\${MICRONAUT_SERVER_HOST:-0.0.0.0}\" && \"$WORKSPACE_ROOT/scripts/cli/gradlew-dev.sh\" :${name}:run'"
}

case "$MODE" in
    web)
        start_web
        ;;
    kotlin)
        start_kotlin_service security 8080
        start_kotlin_service assets 8083
        start_kotlin_service assistant 8082
        start_kotlin_service financialdata 8091
        ;;
    all|*)
        start_web
        start_kotlin_service security 8080
        start_kotlin_service assets 8083
        start_kotlin_service assistant 8082
        start_kotlin_service financialdata 8091
        ;;
esac

echo ""
echo "  tmux attach -t $SESSION    → ver todos os logs"
echo "  Ctrl+B W                   → navegar entre janelas"
echo "  Ctrl+B D                   → sair sem matar os processos"
