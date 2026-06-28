#!/usr/bin/env bash
# =============================================================================
# setup-env.sh — Cria arquivos .env.local a partir dos .env.example
# Chamado no postCreateCommand. Idempotente: não sobrescreve se já existir.
# =============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

copy_env() {
    local example="$1"
    local target="${example%.example}.local"
    if [ -f "$target" ]; then
        echo "  ~ ${target#$WORKSPACE_ROOT/} (já existe — mantido)"
    elif [ -f "$example" ]; then
        cp "$example" "$target"
        echo "  ✓ ${target#$WORKSPACE_ROOT/}"
    fi
}

echo "▶ [setup-env] Criando arquivos .env.local..."

# Web
copy_env "$WORKSPACE_ROOT/web/.env.example"

# Kotlin services
copy_env "$WORKSPACE_ROOT/service/kotlin/security/.env.example"
copy_env "$WORKSPACE_ROOT/service/kotlin/assets/.env.example"
copy_env "$WORKSPACE_ROOT/service/kotlin/assistant/.env.example"
copy_env "$WORKSPACE_ROOT/service/kotlin/financialdata/.env.example"

# Workers Golang
copy_env "$WORKSPACE_ROOT/workers/golang/common/asset_brand_job/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/b3_cotahist_job/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/bacen_ifdata_job/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/cvm_funds_job/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/indicators_job/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/institution_enhancement_consumer/.env.example"
copy_env "$WORKSPACE_ROOT/workers/golang/financial_data/ratings_job/.env.example"

# Workers Python
copy_env "$WORKSPACE_ROOT/workers/python/pdf-extractor/.env.example"

echo "✓ Setup de ambiente concluído"
echo "  Edite os FILL_IN nos .env.local antes de subir os serviços."
