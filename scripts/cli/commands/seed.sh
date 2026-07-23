#!/usr/bin/env bash
# =============================================================================
# seed.sh — Registra service principals e escreve os secrets nos .env.local
#
# Uso: neotool seed [--security-url URL] [--force] [--dry-run]
#
# O manifesto de serviços está em scripts/seed/services.json.
# Para adicionar um novo serviço, edite apenas o manifesto.
# =============================================================================

set -euo pipefail

COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$CLI_DIR/../.." && pwd)"

source "$CLI_DIR/utils.sh"

MANIFEST="$PROJECT_ROOT/scripts/seed/services.json"
DEFAULT_SECURITY_URL="http://localhost:8080"

show_help() {
    cat << EOF
$(log "Seed — Service Principal Registration" "$BRIGHT")

Reads scripts/seed/services.json and registers every service principal
in the security service, then writes the generated secret directly into
each service's .env.local.

Usage: neotool seed [options]

Options:
  --security-url URL    Security service URL (default: $DEFAULT_SECURITY_URL)
  --force               Re-register even if service already exists (new secret)
  --dry-run             Show what would be done without making changes

Examples:
  neotool seed
  neotool seed --force
  neotool seed --security-url http://localhost:8080 --dry-run
EOF
}

# =============================================================================
# Wait for security service to be healthy
# =============================================================================

wait_for_security() {
    local url="$1"
    local max=60
    local waited=0
    echo "▶ Waiting for security service at $url..."
    while [[ $waited -lt $max ]]; do
        if curl -sf "${url}/health" >/dev/null 2>&1; then
            echo "✓ Security service is up"
            return 0
        fi
        sleep 3
        waited=$((waited + 3))
        echo "  [${waited}s] still waiting..."
    done
    echo "✗ Security service not available after ${max}s. Is it running?"
    echo "  Start with: neotool dev start security"
    exit 1
}

# =============================================================================
# Write or update a var in a .env.local file
# =============================================================================

upsert_env_var() {
    local file="$1"
    local key="$2"
    local value="$3"

    if [[ ! -f "$file" ]]; then
        log_error "  .env.local not found: $file"
        log_error "  Run: neotool dev setup-env first"
        return 1
    fi

    if grep -q "^${key}=" "$file"; then
        # Update existing
        sed -i "s|^${key}=.*|${key}=${value}|" "$file"
    else
        # Append
        echo "${key}=${value}" >> "$file"
    fi
}

# =============================================================================
# Register one service from the manifest entry (parsed as args)
# =============================================================================

register_one() {
    local service_id="$1"
    local env_file="$2"
    local env_var="$3"
    local permissions="$4"   # comma-separated
    local security_url="$5"
    local force="$6"
    local dry_run="$7"

    local abs_env_file="$PROJECT_ROOT/$env_file"
    local perms_flag=""
    [[ -n "$permissions" ]] && perms_flag="--permissions $permissions"

    local force_flag=""
    [[ "$force" == "true" ]] && force_flag="--force"

    log "\n▶ $service_id" "$BLUE"
    log "  permissions: ${permissions:-none}" "$GRAY"
    log "  env_file:    $env_file → \$$env_var" "$GRAY"

    if [[ "$dry_run" == "true" ]]; then
        log "  [dry-run] would run: neotool service register $service_id $perms_flag $force_flag --output-env" "$YELLOW"
        return 0
    fi

    # Call neotool service register and capture output
    local output
    # shellcheck disable=SC2086
    output=$(
        SECURITY_URL="$security_url" \
        "$CLI_DIR/cli" service register "$service_id" \
            ${perms_flag} \
            ${force_flag} \
            --output-env \
            --security-url "$security_url" \
        2>&1
    ) || {
        # service register exits non-zero on "already exists" without --force
        if echo "$output" | grep -qi "already registered\|already exists"; then
            log "  ↩ already registered (use --force to reset secret)" "$YELLOW"
            return 0
        fi
        log_error "  ✗ registration failed:"
        echo "$output" | sed 's/^/    /'
        return 1
    }

    # Extract the generated secret from service register output.
    # Prefer the explicit "Client Secret:" line; fall back to env-style output.
    local secret
    secret=$(echo "$output" | sed -n 's/^Client Secret: //p' | head -1)
    if [[ -z "$secret" ]]; then
        secret=$(echo "$output" | grep -E "^[A-Z0-9_-]+=.+$" | grep -i "secret\|CLIENT_SECRET" | head -1 | cut -d= -f2-)
    fi

    if [[ -z "$secret" ]]; then
        log "  ↩ registered (secret already existed — not returned)" "$YELLOW"
        return 0
    fi

    upsert_env_var "$abs_env_file" "SECURITY_SERVICE_ID" "$service_id"
    upsert_env_var "$abs_env_file" "$env_var" "$secret"
    log "  ✓ registered → secret written to $env_file" "$GREEN"
}

# =============================================================================
# Main
# =============================================================================

main() {
    local security_url="$DEFAULT_SECURITY_URL"
    local force="false"
    local dry_run="false"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --security-url) security_url="$2"; shift 2 ;;
            --force)        force="true"; shift ;;
            --dry-run)      dry_run="true"; shift ;;
            --help|-h)      show_help; exit 0 ;;
            *) log_error "Unknown option: $1"; show_help; exit 1 ;;
        esac
    done

    if [[ ! -f "$MANIFEST" ]]; then
        log_error "Manifest not found: $MANIFEST"
        exit 1
    fi

    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq is required. Install with: sudo apt-get install -y jq"
        exit 1
    fi

    [[ "$dry_run" == "true" ]] && log "DRY RUN — no changes will be made\n" "$YELLOW"

    wait_for_security "$security_url"

    local count
    count=$(jq 'length' "$MANIFEST")
    log "\n▶ Seeding $count service principals from manifest..." "$BRIGHT"

    local ok=0
    local failed=0

    for i in $(seq 0 $((count - 1))); do
        local service_id env_file env_var permissions
        service_id=$(jq -r ".[$i].serviceId"    "$MANIFEST")
        env_file=$(jq -r   ".[$i].envFile"      "$MANIFEST")
        env_var=$(jq -r    ".[$i].envVar"        "$MANIFEST")
        permissions=$(jq -r ".[$i].permissions | join(\",\")" "$MANIFEST")

        if register_one "$service_id" "$env_file" "$env_var" "$permissions" \
                        "$security_url" "$force" "$dry_run"; then
            ok=$((ok + 1))
        else
            failed=$((failed + 1))
        fi
    done

    echo ""
    log "✓ Done: $ok registered, $failed failed" "$GREEN"

    if [[ $ok -gt 0 ]] && [[ "$dry_run" == "false" ]]; then
        echo ""
        log "Services with updated secrets need to be restarted:" "$YELLOW"
        log "  neotool dev restart" "$GRAY"
    fi
}

main "$@"
