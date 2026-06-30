#!/usr/bin/env bash

set -euo pipefail

# Dev Command
#
# Manages development services via tmux sessions.
# Each service runs in its own tmux window inside the "dev" session.

COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$CLI_DIR/../.." && pwd)"

source "$CLI_DIR/utils.sh"

# =============================================================================
# Config
# =============================================================================

TMUX_SESSION="dev"
KOTLIN_ROOT="$PROJECT_ROOT/service/kotlin"
WEB_ROOT="$PROJECT_ROOT/web"

# Ordered list for start all (bash 3 compatible — no associative arrays)
ALL_SERVICES=(security assets assistant financialdata comms banking web)

# All workers (golang + python)
ALL_WORKERS=(asset-brand b3-cotahist b3-corporate-events bacen-ifdata cvm-funds indicators institution-consumer ratings pdf-extractor)

# Returns filesystem path (relative to PROJECT_ROOT) for a worker
worker_path() {
    case "$1" in
        asset-brand)          echo "workers/golang/common/asset_brand_job" ;;
        b3-cotahist)          echo "workers/golang/financial_data/b3_cotahist_job" ;;
        b3-corporate-events)  echo "workers/golang/financial_data/b3_corporate_events_job" ;;
        bacen-ifdata)         echo "workers/golang/financial_data/bacen_ifdata_job" ;;
        cvm-funds)            echo "workers/golang/financial_data/cvm_funds_job" ;;
        indicators)           echo "workers/golang/financial_data/indicators_job" ;;
        institution-consumer) echo "workers/golang/financial_data/institution_enhancement_consumer" ;;
        ratings)              echo "workers/golang/financial_data/ratings_job" ;;
        pdf-extractor)        echo "workers/python/pdf-extractor" ;;
        *)                    echo "" ;;
    esac
}

# Returns port for a given service name
service_port() {
    case "$1" in
        web)          echo 3000 ;;
        security)     echo 8080 ;;
        assets)       echo 8082 ;;
        assistant)    echo 8083 ;;
        financialdata) echo 8091 ;;
        comms)        echo 8084 ;;
        banking)      echo 8092 ;;
        *)            echo "" ;;
    esac
}

# =============================================================================
# tmux helpers
# =============================================================================

tmux_session_exists() {
    tmux has-session -t "$TMUX_SESSION" 2>/dev/null
}

ensure_session() {
    if ! tmux_session_exists; then
        tmux new-session -d -s "$TMUX_SESSION" -n "shell"
        log "✓ tmux session '$TMUX_SESSION' created" "$GREEN"
    fi
}

window_exists() {
    local name="$1"
    tmux list-windows -t "$TMUX_SESSION" -F "#{window_name}" 2>/dev/null | grep -qx "$name"
}

kill_window() {
    local name="$1"
    if window_exists "$name"; then
        tmux kill-window -t "${TMUX_SESSION}:${name}" 2>/dev/null || true
    fi
}

new_window() {
    local name="$1"
    local cmd="$2"
    kill_window "$name"
    tmux new-window -t "$TMUX_SESSION" -n "$name" -d
    tmux send-keys -t "${TMUX_SESSION}:${name}" "$cmd" Enter
}

# =============================================================================
# Service commands
# =============================================================================

load_env_cmd() {
    local env_file="$1"
    if [[ -f "$env_file" ]]; then
        echo "export \$(grep -E '^[A-Z_][A-Z0-9_]*=' \"$env_file\" | xargs) 2>/dev/null || true && "
    fi
}


# =============================================================================
# Seed — Service principal registration
# =============================================================================

SEED_MANIFEST="$PROJECT_ROOT/scripts/seed/services.json"
SEED_DEFAULT_SECURITY_URL="http://localhost:8080"

seed_wait_for_security() {
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
    echo "✗ Security service not available after ${max}s."
    echo "  Start with: neotool dev start security"
    exit 1
}

seed_upsert_env_var() {
    local file="$1" key="$2" value="$3"
    if [[ ! -f "$file" ]]; then
        log_error "  .env.local not found: $file"
        return 1
    fi
    if grep -q "^${key}=" "$file"; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$file"
    else
        echo "${key}=${value}" >> "$file"
    fi
}

seed_register_one() {
    local service_id="$1" env_file="$2" env_var="$3" permissions="$4"
    local security_url="$5" force="$6" dry_run="$7"
    local abs_env_file="$PROJECT_ROOT/$env_file"
    local perms_flag="" force_flag=""
    [[ -n "$permissions" ]] && perms_flag="--permissions $permissions"
    [[ "$force" == "true" ]] && force_flag="--force"

    log "
▶ $service_id" "$BLUE"
    log "  permissions: ${permissions:-none}" "$GRAY"
    log "  env_file:    $env_file → \$$env_var" "$GRAY"

    if [[ "$dry_run" == "true" ]]; then
        log "  [dry-run] would run: neotool service register $service_id $perms_flag $force_flag --output-env" "$YELLOW"
        return 0
    fi

    local output
    # shellcheck disable=SC2086
    output=$(
        "$CLI_DIR/cli" service register "$service_id"             ${perms_flag} ${force_flag}             --output-env --security-url "$security_url" 2>&1
    ) || {
        if echo "$output" | grep -qi "already registered\|already exists"; then
            log "  ↩ already registered (use --force to reset secret)" "$YELLOW"
            return 0
        fi
        log_error "  ✗ registration failed:"
        echo "$output" | sed 's/^/    /'
        return 1
    }

    local secret
    secret=$(echo "$output" | grep -E "^[A-Z_]+=.+" | grep -i "secret\|CLIENT_SECRET" | head -1 | cut -d= -f2-)

    if [[ -z "$secret" ]]; then
        log "  ↩ registered (secret not returned — already existed)" "$YELLOW"
        return 0
    fi

    seed_upsert_env_var "$abs_env_file" "$env_var" "$secret"
    log "  ✓ registered → secret written to $env_file" "$GREEN"
}

dev_seed() {
    local security_url="$SEED_DEFAULT_SECURITY_URL"
    local force="false" dry_run="false"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --security-url) security_url="$2"; shift 2 ;;
            --force)        force="true"; shift ;;
            --dry-run)      dry_run="true"; shift ;;
            *) log_error "Unknown option: $1"; exit 1 ;;
        esac
    done

    if [[ ! -f "$SEED_MANIFEST" ]]; then
        log_error "Manifest not found: $SEED_MANIFEST"
        exit 1
    fi
    if ! command -v jq >/dev/null 2>&1; then
        log_error "jq is required: sudo apt-get install -y jq"
        exit 1
    fi

    [[ "$dry_run" == "true" ]] && log "DRY RUN — no changes will be made
" "$YELLOW"

    seed_wait_for_security "$security_url"

    local count ok=0 failed=0
    count=$(jq 'length' "$SEED_MANIFEST")
    log "
▶ Seeding $count service principals..." "$BRIGHT"

    for i in $(seq 0 $((count - 1))); do
        local service_id env_file env_var permissions
        service_id=$(jq -r ".[$i].serviceId"              "$SEED_MANIFEST")
        env_file=$(jq -r   ".[$i].envFile"                "$SEED_MANIFEST")
        env_var=$(jq -r    ".[$i].envVar"                 "$SEED_MANIFEST")
        permissions=$(jq -r --arg sep "," ".[$i].permissions | join(\$sep)" "$SEED_MANIFEST")

        if seed_register_one "$service_id" "$env_file" "$env_var" "$permissions"                              "$security_url" "$force" "$dry_run"; then
            ok=$((ok + 1))
        else
            failed=$((failed + 1))
        fi
    done

    echo ""
    log "✓ Done: $ok registered, $failed failed" "$GREEN"
    if [[ $ok -gt 0 ]] && [[ "$dry_run" == "false" ]]; then
        echo ""
        log "Restart services to pick up new secrets:" "$YELLOW"
        log "  neotool dev restart" "$GRAY"
    fi
}

start_web() {
    log "▶ Starting web..." "$BLUE"
    local env_cmd
    env_cmd=$(load_env_cmd "$WEB_ROOT/.env.local")
    new_window "web" "bash -c '${env_cmd}cd $WEB_ROOT && pnpm install --silent && pnpm run dev'"
    log "  ✓ web → tmux window 'web' (port 3000)" "$GREEN"
}

start_kotlin() {
    local name="$1"
    local port; port=$(service_port "$name")
    log "▶ Starting $name..." "$BLUE"
    local env_file="$KOTLIN_ROOT/$name/.env.local"
    local env_cmd
    env_cmd=$(load_env_cmd "$env_file")
    # JAVA_HOME: detecta arch automaticamente
    local java_home_cmd="export JAVA_HOME=\$(ls -d /usr/lib/jvm/temurin-21-jdk-* 2>/dev/null | head -1) && "
    new_window "$name" "bash -c '${java_home_cmd}${env_cmd}cd $KOTLIN_ROOT && ./gradlew :${name}:run --no-configuration-cache'"
    log "  ✓ $name → tmux window '$name' (port $port)" "$GREEN"
}

start_worker() {
    local name="$1"
    local path; path=$(worker_path "$name")
    if [[ -z "$path" ]]; then
        log_error "Unknown worker: $name"
        exit 1
    fi
    log "▶ Starting worker $name..." "$BLUE"
    new_window "$name" "bash -c 'cd $PROJECT_ROOT/$path && ./run_local.sh'"
    log "  ✓ $name → tmux window '$name'" "$GREEN"
}

is_worker() {
    [[ -n "$(worker_path "$1")" ]]
}

start_service() {
    local name="$1"
    ensure_session
    if is_worker "$name"; then
        start_worker "$name"
        return
    fi
    case "$name" in
        web)              start_web ;;
        security|assets|assistant|financialdata|comms|banking) start_kotlin "$name" ;;
        *) log_error "Unknown service: $name"; exit 1 ;;
    esac
}

stop_service() {
    local name="$1"
    if window_exists "$name"; then
        kill_window "$name"
        log "✓ $name stopped" "$GREEN"
    else
        log "⚠ $name was not running" "$YELLOW"
    fi
}

# =============================================================================
# Status
# =============================================================================

check_port() {
    local port="$1"
    ss -tlnp 2>/dev/null | grep -qE ":${port}[[:space:]]" && echo "up" || echo "down"
}

show_status() {
    log "\nDevelopment services status:" "$BRIGHT"
    echo ""
    printf "%-16s %-6s %-8s %s\n" "SERVICE" "PORT" "STATUS" "TMUX"
    printf "%-16s %-6s %-8s %s\n" "-------" "----" "------" "----"

    for name in "${ALL_SERVICES[@]}"; do
        local port; port=$(service_port "$name")
        local port_status
        port_status=$(check_port "$port")
        local window_status="—"
        if tmux_session_exists && window_exists "$name"; then
            window_status="✓ active"
        fi

        local port_color="$RED"
        [[ "$port_status" == "up" ]] && port_color="$GREEN"

        printf "%-16s %-6s " "$name" "$port"
        echo -e "${port_color}${port_status}${RESET}     $window_status"
    done

    echo ""
    printf "%-22s %-8s %s\n" "WORKER" "STATUS" "TMUX"
    printf "%-22s %-8s %s\n" "------" "------" "----"
    for name in "${ALL_WORKERS[@]}"; do
        local window_status="—"
        if tmux_session_exists && window_exists "$name"; then
            window_status="✓ active"
        fi
        printf "%-22s %-8s %s\n" "$name" "—" "$window_status"
    done
    echo ""

    if tmux_session_exists; then
        log "tmux session '$TMUX_SESSION' active. To enter: neotool dev attach" "$GRAY"
    else
        log "No active tmux session." "$GRAY"
    fi
}

# =============================================================================
# Help
# =============================================================================

show_help() {
    cat << EOF
$(log "Dev Service Manager" "$BRIGHT")

  $(log "init" "$GREEN") [--all]
    One-command setup: starts DevPod, waits for infra, activates port forwarding.
    --all also starts all dev services (web + kotlin).
    Run this once to get everything up from scratch.

Usage: neotool dev <subcommand> [service]

Subcommands:
  $(log "start" "$GREEN") [service]
    Start a service in a dedicated tmux window.
    Without argument, starts all services.

  $(log "stop" "$GREEN") [service]
    Stop a service (closes the tmux window).
    Without argument, stops all services.

  $(log "status" "$GREEN")
    Show status of all services (port + tmux window).

  $(log "attach" "$GREEN") [service]
    Open the tmux terminal.
    With a service name, goes directly to that window.

  $(log "logs" "$GREEN") <service>
    Show the last lines of output for a service (without attaching).

Available services:
  web, security, assets, assistant, financialdata, comms, banking

Available workers:
  asset-brand, b3-cotahist, b3-corporate-events, bacen-ifdata,
  cvm-funds, indicators, institution-consumer, ratings, pdf-extractor

Examples:
  neotool dev start
  neotool dev start security
  neotool dev stop web
  neotool dev status
  neotool dev attach
  neotool dev attach web
  neotool dev logs security
  neotool dev restart web
  neotool dev restart
EOF
}

# =============================================================================
# Forward — runs locally on the host (devpod CLI not available inside container)
# =============================================================================

dev_forward() {
    local action="${1:-}"
    local proxy_name="devpod-port-proxy"

    if [[ "$action" == "stop" ]]; then
        if docker ps -q --filter "name=${proxy_name}" 2>/dev/null | grep -q .; then
            docker rm -f "$proxy_name" >/dev/null 2>&1
            echo "✓ Port forwarding stopped"
        else
            echo "No active port forwarding found."
        fi
        return 0
    fi

    # Remove existing proxy if running
    docker rm -f "$proxy_name" >/dev/null 2>&1 || true

    # Get DevPod container IP
    local container
    container=$(docker ps --filter "label=devpod.user" --format "{{.Names}}" 2>/dev/null | head -1)
    if [[ -z "$container" ]]; then
        echo "ERROR: No DevPod container found. Run: neotool dev init"
        return 1
    fi
    local container_ip
    container_ip=$(docker inspect "$container" --format "{{.NetworkSettings.IPAddress}}" 2>/dev/null)
    echo "  Container: $container ($container_ip)"

    # Build port publish flags and socat commands
    local ports=(3000 4000 8080 8082 8083 8084 8091 8092 5432 6432 9092 9094 9000 9001 8200)
    local publish_flags=""
    local socat_cmds=""
    for p in "${ports[@]}"; do
        publish_flags="$publish_flags -p ${p}:${p}"
    done

    # Build entrypoint script that runs all socats in parallel
    local socat_script="#!/bin/sh"$'
'
    for p in "${ports[@]}"; do
        socat_script="${socat_script}socat TCP-LISTEN:${p},fork,reuseaddr TCP:${container_ip}:${p} &"$'
'
    done
    socat_script="${socat_script}wait"

    # Run proxy container (alpine/socat publishes ports via Docker = Colima exposes to Mac)
    # shellcheck disable=SC2086
    docker run -d         --name "$proxy_name"         $publish_flags         --entrypoint sh         alpine/socat         -c "$socat_script" >/dev/null 2>&1

    sleep 2
    if docker ps -q --filter "name=${proxy_name}" 2>/dev/null | grep -q .; then
        echo "✓ Port forwarding active via proxy container"
        echo "  Ports: ${ports[*]}"
        echo "  Stop with: neotool dev forward stop"
    else
        echo "⚠ Proxy container failed to start."
        echo "  Check: docker logs $proxy_name"
    fi
}

# =============================================================================
# Init — runs locally on the host (not inside container)
# =============================================================================

dev_init() {
    local flag="${1:-}"

    # 1. Check devpod is installed
    if ! command -v devpod >/dev/null 2>&1; then
        echo "ERROR: devpod not found. Install at https://devpod.sh"
        exit 1
    fi

    # 2. Start / recreate the DevPod workspace
    echo "▶ [1/3] Starting DevPod workspace..."
    devpod up invistus --provider docker --ide none
    echo "✓ DevPod ready"

    # 3. Wait for infra (start-infra.sh runs via postStartCommand, give it time)
    echo ""
    echo "▶ [2/3] Waiting for infrastructure to be ready..."
    local max_wait=180
    local waited=0
    local container
    container=$(docker ps --filter "label=devpod.user" --format "{{.Names}}" 2>/dev/null | head -1)

    # Router não entra na lista: sobe junto mas fica em erro até os serviços Kotlin estarem no ar
    declare -a INFRA_SERVICES=(neotool-postgres neotool-pgbouncer neotool-minio neotool-vault)

    while [[ $waited -lt $max_wait ]]; do
        local running
        running=$(docker exec "$container" docker ps --format "{{.Names}}" 2>/dev/null)
        local all_up=true
        local status_line=""
        for svc in "${INFRA_SERVICES[@]}"; do
            local short="${svc#neotool-}"
            if echo "$running" | grep -q "^${svc}$"; then
                status_line="${status_line} ✓${short}"
            else
                status_line="${status_line} ✗${short}"
                all_up=false
            fi
        done
        echo "  [${waited}s]${status_line}"
        if [[ "$all_up" == "true" ]]; then
            echo "✓ All infrastructure services are up"
            break
        fi
        sleep 5
        waited=$((waited + 5))
    done
    if [[ $waited -ge $max_wait ]]; then
        echo "⚠ Timed out waiting for infra — check: docker exec $container docker ps"
    fi

    # 4. Activate port forwarding (uses socat proxy — devpod ssh -N does not work reliably)
    echo ""
    echo "▶ [3/3] Activating port forwarding..."
    dev_forward

    # 5. Optionally start all services
    if [[ "$flag" == "--all" ]]; then
        echo ""
        echo "▶ Starting all dev services..."
        sleep 2  # give forward a moment to settle
        "$0" dev start
    fi

    echo ""
    echo "✓ Environment ready!"
    echo "  Web:      http://localhost:3000"
    echo "  Vault:    http://localhost:8200"
    echo "  MinIO:    http://localhost:9001"
    echo "  Postgres: localhost:6432"
    echo ""
    echo "  Start services: ./neotool dev start [service]"
    echo "  Stop forward:   ./neotool dev forward stop"
    echo "  Status:         ./neotool dev status"
}

# =============================================================================
# Main
# =============================================================================

main() {
    if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
        exit 0
    fi

    # init and forward always run locally (outside container — devpod CLI not inside)
    if [[ "${1:-}" == "init" ]]; then
        dev_init "${2:-}"
        exit $?
    fi
    if [[ "${1:-}" == "forward" ]]; then
        dev_forward "${2:-}"
        exit $?
    fi

    # If tmux is not available, we're outside the DevPod — re-run inside the container
    if ! command -v tmux >/dev/null 2>&1; then
        local container
        # Allow override via env var
        if [[ -n "${DEVPOD_CONTAINER:-}" ]]; then
            container="$DEVPOD_CONTAINER"
        else
            # DevPod containers always have the label devpod.user
            container=$(docker ps --filter "label=devpod.user" --format "{{.Names}}" 2>/dev/null | head -1)
        fi
        if [[ -z "$container" ]]; then
            echo "ERROR: No running Invistus DevPod container found."
            echo "  Set manually: DEVPOD_CONTAINER=<name> ./neotool dev $*"
            echo "  Running containers: $(docker ps --format '{{.Names}}' | tr '
' ' ')"
            exit 1
        fi
        echo "↪ Routing to DevPod container: $container"
        # attach and start <service> need -it (end with tmux attach)
        if [[ "${1:-}" == "attach" ]] || { [[ "${1:-}" == "start" ]] && [[ -n "${2:-}" ]]; }; then
            docker exec -it "$container" bash -c "cd /workspaces/invistus && ./scripts/cli/cli dev $(printf '%q ' "$@")"
        else
            docker exec -i "$container" bash -c "cd /workspaces/invistus && ./scripts/cli/cli dev $(printf '%q ' "$@")"
        fi
        exit $?
    fi

    local subcommand="$1"
    shift

    local target="${1:-}"

    case "$subcommand" in
        start)
            if [[ -n "$target" ]]; then
                start_service "$target"
                # Auto-attach to the service window so logs are visible immediately
                if tmux_session_exists && [[ -t 1 ]]; then
                    tmux select-window -t "${TMUX_SESSION}:${target}" 2>/dev/null || true
                    tmux attach-session -t "$TMUX_SESSION"
                else
                    log "  → neotool dev attach $target" "$GRAY"
                fi
            else
                ensure_session
                # Start security first and wait — other services need JWKS to validate JWTs
                start_service "security"
                log "⏳ Waiting for security service (JWKS)..." "$YELLOW"
                local waited=0
                while [[ $waited -lt 120 ]]; do
                    if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
                        log "✓ Security ready" "$GREEN"
                        break
                    fi
                    sleep 5
                    waited=$((waited + 5))
                done
                if [[ $waited -ge 120 ]]; then
                    log "⚠ Security not ready after 120s — starting others anyway" "$YELLOW"
                fi
                # Now start the rest of the services
                for svc in "${ALL_SERVICES[@]}"; do
                    [[ "$svc" == "security" ]] && continue
                    start_service "$svc"
                    sleep 1
                done
                # Start workers
                for w in "${ALL_WORKERS[@]}"; do
                    start_worker "$w"
                    sleep 1
                done
                log "\n✓ All services and workers started." "$GREEN"
                log "  neotool dev attach    — to open tmux" "$GRAY"
                log "  neotool dev status    — to see status" "$GRAY"
            fi
            ;;
        stop)
            if ! tmux_session_exists; then
                log "No active tmux session." "$YELLOW"
                exit 0
            fi
            if [[ -n "$target" ]]; then
                stop_service "$target"
            else
                for svc in "${ALL_SERVICES[@]}"; do
                    stop_service "$svc"
                done
                log "✓ All services stopped." "$GREEN"
            fi
            ;;
        status)
            show_status
            ;;
        attach)
            if ! tmux_session_exists; then
                log_error "Nenhuma sessão tmux ativa. Rode: neotool dev start"
                exit 1
            fi
            if [[ -n "$target" ]]; then
                if ! window_exists "$target"; then
                    log_error "Service '$target' is not running. Run: neotool dev start $target"
                    exit 1
                fi
                tmux select-window -t "${TMUX_SESSION}:${target}"
            fi
            tmux attach-session -t "$TMUX_SESSION"
            ;;
        logs)
            if [[ -z "$target" ]]; then
                log_error "Specify a service: neotool dev logs <service>"
                exit 1
            fi
            if ! tmux_session_exists || ! window_exists "$target"; then
                log_error "Service '$target' is not running."
                exit 1
            fi
            tmux capture-pane -t "${TMUX_SESSION}:${target}" -p -S -100
            ;;
        init)
            dev_init "${target:-}"
            ;;
        seed)
            dev_seed "${@}"
            ;;
        restart)
            if [[ -n "$target" ]]; then
                stop_service "$target"
                sleep 1
                start_service "$target"
            else
                for svc in "${ALL_SERVICES[@]}"; do
                    stop_service "$svc"
                done
                sleep 1
                for svc in "${ALL_SERVICES[@]}"; do
                    start_service "$svc"
                    sleep 1
                done
                log "✓ All services restarted." "$GREEN"
            fi
            ;;
        forward)
            local pidfile="/tmp/devpod-forward.pid"
            if [[ "${target}" == "stop" ]]; then
                if [[ -f "$pidfile" ]]; then
                    kill "$(cat $pidfile)" 2>/dev/null && echo "✓ Port forwarding stopped" || echo "Already stopped"
                    rm -f "$pidfile"
                else
                    echo "No active port forwarding session found."
                fi
            else
                if [[ -f "$pidfile" ]] && kill -0 "$(cat $pidfile)" 2>/dev/null; then
                    echo "Port forwarding already active (PID $(cat $pidfile))"
                else
                    devpod ssh invistus -- -N &
                    echo $! > "$pidfile"
                    echo "✓ Port forwarding active in background (PID $(cat $pidfile))"
                    echo "  Stop with: neotool dev forward stop"
                fi
            fi
            ;;
        *)
            log_error "Unknown subcommand: $subcommand"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"
