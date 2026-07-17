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
GRADLEW_DEV="$CLI_DIR/gradlew-dev.sh"
GRADLE_LOCK_FILE="/tmp/neotool-gradle.lock"

# Ordered list for start all (bash 3 compatible — no associative arrays)
ALL_SERVICES=(security assets assistant financialdata comms banking web)

# All workers (golang + python)
ALL_WORKERS=(asset-brand b3-cotahist b3-corporate-events bacen-ifdata cvm-funds indicators institution-consumer ratings pdf-extractor)

# Infra containers (docker compose services)
ALL_INFRA=(postgres pgbouncer kafka minio vault router)
COMPOSE_FILE="$PROJECT_ROOT/infra/docker/docker-compose.local.yml"
COMPOSE_ENV="$PROJECT_ROOT/infra/docker/.env.local"

# Returns docker container name for an infra service
infra_container() {
    case "$1" in
        postgres)  echo "neotool-postgres" ;;
        pgbouncer) echo "neotool-pgbouncer" ;;
        kafka)     echo "neotool-kafka" ;;
        minio)     echo "neotool-minio" ;;
        vault)     echo "neotool-vault" ;;
        router)    echo "neotool-graphql-router" ;;
        *)         echo "" ;;
    esac
}

# Returns compose profile flag if needed
infra_profile() {
    case "$1" in
        router) echo "--profile gateway" ;;
        *)      echo "" ;;
    esac
}

is_infra() {
    [[ -n "$(infra_container "$1")" ]]
}

infra_running() {
    local container; container=$(infra_container "$1")
    docker ps --format "{{.Names}}" 2>/dev/null | grep -qx "$container"
}

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
        # Use source (not grep|xargs|export) — nested single quotes in the old
        # pattern broke when tmux send-keys ran under zsh.
        echo "set -a && source \"$env_file\" && set +a && "
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
    secret=$(echo "$output" | sed -n 's/^Client Secret: //p' | head -1)
    if [[ -z "$secret" ]]; then
        secret=$(echo "$output" | grep -E "^[A-Z0-9_-]+=.+$" | grep -i "secret\|CLIENT_SECRET" | head -1 | cut -d= -f2-)
    fi

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
    local clean="${1:-false}"
    log "▶ Starting web..." "$BLUE"
    if [[ "$clean" == "true" ]]; then
        log "  ↺ Clearing .next cache..." "$YELLOW"
        rm -rf "$WEB_ROOT/.next/"* 2>/dev/null || true
        log "  ✓ Cache cleared" "$GREEN"
    fi
    local env_cmd
    env_cmd=$(load_env_cmd "$WEB_ROOT/.env.local")
    new_window "web" "bash -c '${env_cmd}cd $WEB_ROOT && pnpm install --silent && pnpm run dev'"
    log "  ✓ web → tmux window 'web' (port 3000)" "$GREEN"
}

gradle_reset() {
    log "▶ Resetting Gradle locks and daemons..." "$BLUE"

    # Running :module:run services keep class files open under build/ and break `./gradlew clean`.
    for svc in security assets assistant financialdata comms banking; do
        if window_exists "$svc"; then
            log "  Stopping $svc (tmux)..." "$GRAY"
            kill_window "$svc"
        fi
    done

    if [[ -x "$KOTLIN_ROOT/gradlew" ]]; then
        (cd "$KOTLIN_ROOT" && ./gradlew --stop) 2>/dev/null || true
    fi
    pkill -f "GradleDaemon" 2>/dev/null || true
    pkill -f "org.gradle.launcher.daemon" 2>/dev/null || true
    pkill -f "$KOTLIN_ROOT/gradlew" 2>/dev/null || true

    rm -f "$GRADLE_LOCK_FILE" "$KOTLIN_ROOT/.gradle/8.14/fileHashes/fileHashes.lock"
    rm -rf "$KOTLIN_ROOT/.gradle/8.14/executionHistory" 2>/dev/null || true
    rm -rf "$KOTLIN_ROOT/.gradle/buildOutputCleanup" 2>/dev/null || true

    # Stale build-cache entries reference files under */build/; deleting build/ alone breaks kspKotlin FROM-CACHE.
    local gradle_user_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    rm -rf "$gradle_user_home/caches/build-cache-1" 2>/dev/null || true
    rm -rf "$KOTLIN_ROOT/.gradle" 2>/dev/null || true

    if [[ "${DEVPOD:-}" == "true" ]]; then
        local devpod_gradle_base="${NEOTOOL_GRADLE_BASE:-${HOME}/.cache/neotool-gradle/${DEVPOD_WORKSPACE_UID:-default}}"
        rm -rf "$devpod_gradle_base/project-cache" 2>/dev/null || true
        rm -rf "$devpod_gradle_base/builds" 2>/dev/null || true
    fi

    if [[ -d "$KOTLIN_ROOT" ]]; then
        log "  Removing Kotlin build directories..." "$GRAY"
        for build_dir in "$KOTLIN_ROOT"/*/build "$KOTLIN_ROOT"/build; do
            [[ -d "$build_dir" ]] || continue
            if command -v fuser >/dev/null 2>&1; then
                fuser -km "$build_dir" 2>/dev/null || true
                sleep 1
            fi
            rm -rf "$build_dir" 2>/dev/null || true
        done
    fi

    log "✓ Gradle reset complete" "$GREEN"
    log "  You can now run: neotool dev start <service>" "$GRAY"
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
    # gradlew-dev serializes :classes with flock before :run to avoid fileHashes lock contention
    new_window "$name" "bash -c '${java_home_cmd}${env_cmd}\"$GRADLEW_DEV\" :${name}:run'"
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

port_listening() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -tln 2>/dev/null | grep -qE ":${port}([[:space:]]|$)" && return 0
    fi
    if (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
        return 0
    fi
    if command -v nc >/dev/null 2>&1; then
        nc -z 127.0.0.1 "$port" 2>/dev/null && return 0
    fi
    if command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:"$port" -sTCP:LISTEN -P -n >/dev/null 2>&1 && return 0
    fi
    return 1
}

service_health_url() {
    local name="$1" port="$2"
    case "$name" in
        web) echo "http://127.0.0.1:${port}" ;;
        *)   echo "http://127.0.0.1:${port}/health" ;;
    esac
}

check_service_status() {
    local name="$1" port="$2"
    local url; url=$(service_health_url "$name" "$port")

    if curl -sf --max-time 2 "$url" >/dev/null 2>&1; then
        echo "up"
    elif port_listening "$port"; then
        echo "starting"
    else
        echo "down"
    fi
}

infra_host_ports() {
    local container="$1"
    local ports
    ports=$(docker port "$container" 2>/dev/null | awk -F: '{print $NF}' | sort -nu | paste -sd, -)
    [[ -n "$ports" ]] && echo "$ports" || echo "—"
}

status_color() {
    case "$1" in
        up|✓*)       echo "$GREEN" ;;
        starting|⚠*) echo "$YELLOW" ;;
        *)           echo "$RED" ;;
    esac
}

print_status_section() {
    log "$1" "$BRIGHT"
    echo ""
}

# Returns all descendant PIDs of a given PID (BFS, max depth 8)
all_descendants() {
    local result="" queue="$1"
    local depth=0
    while [[ -n "${queue// /}" && $depth -lt 8 ]]; do
        local next=""
        for pid in $queue; do
            local children
            children=$(ps --ppid "$pid" -o pid= --no-headers 2>/dev/null | tr '\n' ' ') || true
            next="$next $children"
            result="$result $children"
        done
        queue="$next"
        depth=$((depth + 1))
    done
    echo "$result"
}

# Returns "CPU% MEM" for all processes in a tmux window (sums tree)
window_stats() {
    local window="$1"
    local shell_pid
    shell_pid=$(tmux list-panes -t "${TMUX_SESSION}:${window}" -F "#{pane_pid}" 2>/dev/null | head -1) || true
    if [[ -z "$shell_pid" ]]; then echo "— —"; return 0; fi

    local all_pids; all_pids=$(all_descendants "$shell_pid")
    if [[ -z "${all_pids// /}" ]]; then echo "— —"; return 0; fi

    local ps_args=""
    for pid in $all_pids; do
        [[ -n "$pid" ]] && ps_args="$ps_args -p $pid"
    done
    [[ -z "$ps_args" ]] && echo "— —" && return 0

    # shellcheck disable=SC2086
    ps $ps_args -o %cpu=,rss= --no-headers 2>/dev/null \
        | awk '{cpu+=$1; rss+=$2}
               END {if(NR==0) print "— —";
                    else printf "%.0f%% %dMB", cpu, rss/1024}' || echo "— —"
}

show_infra_table() {
    print_status_section "Infrastructure"
    # Collect docker stats once for all containers
    local docker_stats
    docker_stats=$(docker stats --no-stream \
        --format "{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}" 2>/dev/null)

    printf "%-14s %-8s %-10s %-10s %s\n" "SERVICE" "CPU" "MEM" "PORTS" "STATUS"
    printf "%-14s %-8s %-10s %-10s %s\n" "-------" "---" "---" "-----" "------"
    for name in "${ALL_INFRA[@]}"; do
        local container; container=$(infra_container "$name")
        local ports status color cpu mem
        if infra_running "$name"; then
            ports=$(infra_host_ports "$container")
            status="✓ up"; color="$GREEN"
            local stat_line; stat_line=$(echo "$docker_stats" | grep "^${container}|")
            cpu=$(echo "$stat_line" | cut -d'|' -f2)
            mem=$(echo "$stat_line" | cut -d'|' -f3 | cut -d'/' -f1 | tr -d ' ')
        else
            ports="—"; cpu="—"; mem="—"
            status="✗ down"; color="$RED"
        fi
        printf "%-14s %-8s %-10s %-10s " "$name" "${cpu:-—}" "${mem:-—}" "$ports"
        echo -e "${color}${status}${RESET}"
    done
}

show_services_table() {
    echo ""
    print_status_section "Development services"
    printf "%-16s %-6s %-8s %-10s %-10s %s\n" "SERVICE" "PORT" "CPU" "MEM" "STATUS" "TMUX"
    printf "%-16s %-6s %-8s %-10s %-10s %s\n" "-------" "----" "---" "---" "------" "----"

    for name in "${ALL_SERVICES[@]}"; do
        local port; port=$(service_port "$name")
        local svc_status; svc_status=$(check_service_status "$name" "$port")
        local window_status="—" cpu="—" mem="—"
        if tmux_session_exists && window_exists "$name"; then
            window_status="✓ active"
            local stats; stats=$(window_stats "$name")
            cpu=$(echo "$stats" | awk '{print $1}')
            mem=$(echo "$stats" | awk '{print $2}')
        fi

        local svc_color; svc_color=$(status_color "$svc_status")
        printf "%-16s %-6s %-8s %-10s " "$name" "$port" "$cpu" "$mem"
        echo -e "${svc_color}${svc_status}${RESET}   $window_status"
    done
}

show_workers_table() {
    echo ""
    print_status_section "Workers"
    printf "%-22s %-10s %s\n" "WORKER" "STATUS" "TMUX"
    printf "%-22s %-10s %s\n" "------" "------" "----"
    for name in "${ALL_WORKERS[@]}"; do
        local worker_status="down" worker_color="$RED" window_status="—"
        if tmux_session_exists && window_exists "$name"; then
            worker_status="running"
            worker_color="$GREEN"
            window_status="✓ active"
        fi
        printf "%-22s " "$name"
        echo -e "${worker_color}${worker_status}${RESET}   $window_status"
    done
}

show_vault_jwt_status() {
    local vault_url="http://localhost:8200"
    local vault_token="${VAULT_TOKEN:-myroot}"
    echo ""
    if infra_running vault; then
        local key_count
        key_count=$(curl -sf \
            -H "X-Vault-Token: $vault_token" \
            "${vault_url}/v1/secret/metadata/jwt/keys?list=true" \
            2>/dev/null | jq -r '.data.keys | length' 2>/dev/null || echo "0")
        if [[ "$key_count" -gt 0 ]] 2>/dev/null; then
            echo -e "${GREEN}✓ Vault JWT keys loaded (${key_count} key(s))${RESET}"
        else
            echo -e "${RED}✗ Vault JWT keys missing${RESET}"
            echo -e "${YELLOW}  Fix: neotool vault restore --secret-path secret/jwt/keys --input ~/.vault-keys/backup.json${RESET}"
            echo -e "${YELLOW}  Then: neotool dev restart security${RESET}"
        fi
    else
        echo -e "${YELLOW}⚠ Vault not running — cannot check JWT keys${RESET}"
    fi
}

show_status() {
    log "\nDevelopment environment status:" "$BRIGHT"
    show_infra_table
    show_services_table
    show_workers_table
    show_vault_jwt_status
    echo ""

    if tmux_session_exists; then
        log "tmux session '$TMUX_SESSION' active. To enter: neotool dev attach" "$GRAY"
    else
        log "No active tmux session." "$GRAY"
    fi
}

# =============================================================================
# Infra management
# =============================================================================

dev_infra() {
    local action="${1:-}"
    local target="${2:-}"

    if [[ -z "$action" ]] || [[ "$action" == "--help" ]]; then
        echo "Usage: neotool dev infra <start|stop|restart|status> [service]"
        echo "Services: ${ALL_INFRA[*]}"
        return 0
    fi

    if [[ "$action" == "status" ]]; then
        show_infra_table
        return 0
    fi

    local targets=()
    if [[ -n "$target" ]]; then
        if ! is_infra "$target"; then
            log_error "Unknown infra service: $target. Available: ${ALL_INFRA[*]}"
            exit 1
        fi
        targets=("$target")
    else
        targets=("${ALL_INFRA[@]}")
    fi

    for svc in "${targets[@]}"; do
        local profile; profile=$(infra_profile "$svc")
        case "$action" in
            start)
                log "▶ Starting infra: $svc..." "$BLUE"
                # shellcheck disable=SC2086
                docker compose -f "$COMPOSE_FILE" --env-file "$COMPOSE_ENV"                     $profile up -d "$svc"                     && log "  ✓ $svc up" "$GREEN"                     || log_error "  ✗ failed to start $svc"
                ;;
            stop)
                log "▶ Stopping infra: $svc..." "$BLUE"
                # shellcheck disable=SC2086
                docker compose -f "$COMPOSE_FILE" --env-file "$COMPOSE_ENV"                     $profile stop "$svc"                     && log "  ✓ $svc stopped" "$GREEN"                     || log_error "  ✗ failed to stop $svc"
                ;;
            restart)
                log "▶ Restarting infra: $svc..." "$BLUE"
                # shellcheck disable=SC2086
                docker compose -f "$COMPOSE_FILE" --env-file "$COMPOSE_ENV"                     $profile restart "$svc"                     && log "  ✓ $svc restarted" "$GREEN"                     || log_error "  ✗ failed to restart $svc"
                ;;
            *)
                log_error "Unknown action: $action. Use start|stop|restart|status"
                exit 1
                ;;
        esac
    done
}

# =============================================================================
# Agent — neotool-dev-agent process management
# =============================================================================

AGENT_SCRIPT="/workspaces/invistus/.devcontainer/neotool-dev-agent.py"
AGENT_LOG="/tmp/neotool-agent.log"
AGENT_PID_FILE="/tmp/neotool-agent.pid"
AGENT_API_KEYS="${HOME}/.vault-keys/api-keys.env"
AGENT_DEVPOD_N="${NEOTOOL_DEVPOD_N:-1}"
AGENT_DEVPOD_ENV="${HOME}/.vault-keys/devpod-${AGENT_DEVPOD_N}.env"
AGENT_PORT="${NEOTOOL_AGENT_PORT:-3285}"

WATCHDOG_SCRIPT="/workspaces/invistus/.devcontainer/neotool-watchdog"
WATCHDOG_PID_FILE="${HOME}/.local/share/neotool/watchdog.pid"
WATCHDOG_LOG="${HOME}/.local/share/neotool/watchdog.log"

agent_running() {
    local pid
    pid=$(cat "$AGENT_PID_FILE" 2>/dev/null || true)
    [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

watchdog_running() {
    local pid
    pid=$(cat "$WATCHDOG_PID_FILE" 2>/dev/null || true)
    [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

watchdog_start() {
    if watchdog_running; then
        return 0
    fi
    mkdir -p "$(dirname "$WATCHDOG_PID_FILE")"
    nohup "$WATCHDOG_SCRIPT" > /dev/null 2>&1 &
    # O watchdog escreve o próprio PID em WATCHDOG_PID_FILE
    sleep 1
    if watchdog_running; then
        log "  ✓ watchdog iniciado (PID $(cat $WATCHDOG_PID_FILE))" "$GRAY"
    else
        log "  ⚠ watchdog não iniciou — verifique: $WATCHDOG_LOG" "$YELLOW"
    fi
}

watchdog_stop() {
    if watchdog_running; then
        kill "$(cat "$WATCHDOG_PID_FILE")" 2>/dev/null || true
        rm -f "$WATCHDOG_PID_FILE"
        log "✓ watchdog parado" "$GREEN"
    fi
}

agent_stop() {
    watchdog_stop
    pkill -f "neotool-dev-agent.py" 2>/dev/null || true
    rm -f "$AGENT_PID_FILE"
    log "✓ neotool-dev-agent stopped" "$GREEN"
}

agent_start() {
    if agent_running; then
        log "⚠ neotool-dev-agent already running (PID $(cat $AGENT_PID_FILE))" "$YELLOW"
        return 0
    fi

    # Load API keys and per-DevPod Slack tokens
    # set -a auto-exports todas as vars para que o processo filho (python3) herde
    if [[ -f "$AGENT_API_KEYS" ]]; then
        # shellcheck disable=SC1090
        set -a; source "$AGENT_API_KEYS"; set +a
        log "  ✓ api-keys loaded" "$GRAY"
    else
        log "  ⚠ $AGENT_API_KEYS not found — skipping" "$YELLOW"
    fi
    if [[ -f "$AGENT_DEVPOD_ENV" ]]; then
        # shellcheck disable=SC1090
        set -a; source "$AGENT_DEVPOD_ENV"; set +a
        log "  ✓ devpod-${AGENT_DEVPOD_N}.env loaded" "$GRAY"
    else
        log "  ⚠ $AGENT_DEVPOD_ENV not found — Slack tokens ausentes" "$YELLOW"
    fi

    # Start in background
    python3 "$AGENT_SCRIPT" >> "$AGENT_LOG" 2>&1 &
    local pid=$!
    echo "$pid" > "$AGENT_PID_FILE"
    log "  ✓ started (PID $pid) → $AGENT_LOG" "$GRAY"

    # Wait for health
    local waited=0
    while [[ $waited -lt 10 ]]; do
        if curl -sf "http://127.0.0.1:${AGENT_PORT}/health" >/dev/null 2>&1; then
            log "✓ neotool-dev-agent healthy on :${AGENT_PORT}" "$GREEN"
            watchdog_start
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    log "⚠ agent started but health check timed out — check: neotool dev agent logs" "$YELLOW"
    watchdog_start
}

dev_agent() {
    local action="${1:-}"

    if [[ "$action" == "-m" ]] || [[ "$action" == "--message" ]]; then
        dev_agent_run "${@:2}"
        return
    fi

    if [[ -z "$action" ]] || [[ "$action" == "--help" ]]; then
        cat << EOF
Usage: neotool dev agent <start|stop|restart|logs|attach|status|thread|-m>

  start    Start neotool-dev-agent in background (loads api-keys automatically)
  stop     Stop neotool-dev-agent
  restart  Stop then start
  logs     Tail agent log (Ctrl-C to exit)
  attach   Run agent in foreground (logs to stdout)
  status   Show running status and health
  thread   Inspect per-task JSONL audit trail (logs|tail|cost|list)
  -m       Run a task from the CLI: -m "<message>" [--issue N] [--agent claude|codex|cursor] [--auto]
EOF
        return 0
    fi

    case "$action" in
        start)
            log "▶ Starting neotool-dev-agent..." "$BLUE"
            agent_start
            ;;
        stop)
            log "▶ Stopping neotool-dev-agent..." "$BLUE"
            agent_stop
            ;;
        restart)
            log "▶ Restarting neotool-dev-agent..." "$BLUE"
            agent_stop
            sleep 1
            agent_start
            ;;
        logs)
            if [[ ! -f "$AGENT_LOG" ]]; then
                log "No log file yet: $AGENT_LOG" "$YELLOW"
                return 0
            fi
            tail -f "$AGENT_LOG"
            ;;
        attach)
            log "▶ Attaching neotool-dev-agent (foreground — Ctrl-C to stop)..." "$BLUE"
            pkill -f "neotool-dev-agent.py" 2>/dev/null || true
            rm -f "$AGENT_PID_FILE"
            if [[ -f "$AGENT_API_KEYS" ]]; then
                # shellcheck disable=SC1090
                set -a; source "$AGENT_API_KEYS"; set +a
            fi
            if [[ -f "$AGENT_DEVPOD_ENV" ]]; then
                # shellcheck disable=SC1090
                source "$AGENT_DEVPOD_ENV"
            fi
            python3 "$AGENT_SCRIPT"
            ;;
        status)
            if agent_running; then
                local pid; pid=$(cat "$AGENT_PID_FILE")
                log "✓ neotool-dev-agent running (PID $pid)" "$GREEN"
                if curl -sf "http://127.0.0.1:${AGENT_PORT}/health" >/dev/null 2>&1; then
                    log "  health: ok on :${AGENT_PORT}" "$GREEN"
                else
                    log "  health: not responding on :${AGENT_PORT}" "$RED"
                fi
            else
                log "✗ neotool-dev-agent not running" "$RED"
                log "  Start with: neotool dev agent start" "$GRAY"
            fi
            if watchdog_running; then
                log "✓ watchdog running (PID $(cat $WATCHDOG_PID_FILE))" "$GREEN"
            else
                log "○ watchdog not running" "$YELLOW"
            fi
            ;;
        thread)
            dev_agent_thread "${@:2}"
            ;;
        *)
            log_error "Unknown action: $action. Use start|stop|restart|logs|attach|status|thread"
            exit 1
            ;;
    esac
}

dev_agent_run() {
    if [[ $# -lt 1 ]]; then
        log_error 'Usage: neotool dev agent -m "<message>" [--issue N] [--agent claude|codex|cursor] [--auto]'
        exit 1
    fi

    local CLI_ADAPTER="$PROJECT_ROOT/.devcontainer/agents/cli_adapter.py"
    if [[ ! -f "$CLI_ADAPTER" ]]; then
        log_error "CLI adapter not found: $CLI_ADAPTER"
        exit 1
    fi

    if [[ -f "$AGENT_API_KEYS" ]]; then
        # shellcheck disable=SC1090
        set -a; source "$AGENT_API_KEYS"; set +a
    fi

    PYTHONPATH="$PROJECT_ROOT/.devcontainer" python3 "$CLI_ADAPTER" -m "$@"
}

dev_agent_thread() {
    local subcmd="${1:-}"
    local THREAD_LOG_CLI="$PROJECT_ROOT/.devcontainer/thread_log_cli.py"

    if [[ -z "$subcmd" ]] || [[ "$subcmd" == "--help" ]]; then
        cat << EOF
Usage: neotool dev agent thread <logs|tail|cost|list> [task_id]

  logs <task_id>       Show all JSONL entries for a task
  tail <task_id>       Print new log lines (-f to follow)
  cost <task_id>       Token usage breakdown by stage
  list                 List known task IDs (newest first)
EOF
        return 0
    fi

    if [[ ! -f "$THREAD_LOG_CLI" ]]; then
        log_error "Thread log CLI not found: $THREAD_LOG_CLI"
        exit 1
    fi

    case "$subcmd" in
        logs)
            local task_id="${2:-}"
            if [[ -z "$task_id" ]]; then
                log_error "Usage: neotool dev agent thread logs <task_id>"
                exit 1
            fi
            PYTHONPATH="$PROJECT_ROOT/.devcontainer" python3 "$THREAD_LOG_CLI" logs "$task_id"
            ;;
        tail)
            local task_id="${2:-}"
            if [[ -z "$task_id" ]]; then
                log_error "Usage: neotool dev agent thread tail <task_id> [-f]"
                exit 1
            fi
            shift 2
            PYTHONPATH="$PROJECT_ROOT/.devcontainer" python3 "$THREAD_LOG_CLI" tail "$task_id" "$@"
            ;;
        cost)
            local task_id="${2:-}"
            if [[ -z "$task_id" ]]; then
                log_error "Usage: neotool dev agent thread cost <task_id>"
                exit 1
            fi
            PYTHONPATH="$PROJECT_ROOT/.devcontainer" python3 "$THREAD_LOG_CLI" cost "$task_id"
            ;;
        list)
            PYTHONPATH="$PROJECT_ROOT/.devcontainer" python3 "$THREAD_LOG_CLI" list
            ;;
        *)
            log_error "Unknown thread subcommand: $subcmd. Use logs|tail|cost|list"
            exit 1
            ;;
    esac
}

# =============================================================================
# Help
# =============================================================================

show_help() {
    cat << EOF
$(log "Dev Service Manager" "$BRIGHT")

  $(log "up" "$GREEN") [--all]
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

  $(log "agent" "$GREEN") <start|stop|restart|logs|attach|status|thread|-m>
    Manage the neotool-dev-agent (Slack → AI pipeline bridge).
    start: loads api-keys and starts the bridge; stop/restart/logs/attach/status as expected.
    thread: inspect per-task JSONL audit trail (logs|tail|cost|list).
    -m: run a task from the CLI (prints task_id for thread log commands).

  $(log "tunnel" "$GREEN") <start <n>|stop|status|logs>
    Manage the Cloudflare Tunnel for DevPod N.
    Example: neotool dev tunnel start 1

  $(log "forward" "$GREEN") [stop|status|add] [PORT|LOCAL:REMOTE ...]
    Port-forward DevPod ports to the host via a Docker socat proxy.
    No args: default service/infra ports.
    With ports: default ports + those (PORT or LOCAL:REMOTE).
    add: alias for adding ports to the default set.
    Examples:
      neotool dev forward
      neotool dev forward 9323
      neotool dev forward 15432:5432
      neotool dev forward status
      neotool dev forward stop

  $(log "gradle-reset" "$GREEN")
    Stop Kotlin dev services, Gradle daemons, build/ dirs, and stale build cache
    (use if kspKotlin fails with "Failed to load cache entry" or clean cannot delete build).

Available services:
  web, security, assets, assistant, financialdata, comms, banking

Available workers:
  asset-brand, b3-cotahist, b3-corporate-events, bacen-ifdata,
  cvm-funds, indicators, institution-consumer, ratings, pdf-extractor

Infra subcommand:
  neotool dev infra <start|stop|restart|status> [service]
  Services: postgres, pgbouncer, kafka, minio, vault, router

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
  neotool dev gradle-reset
EOF
}

# =============================================================================
# Forward — runs locally on the host (devpod CLI not available inside container)
# =============================================================================

FORWARD_PROXY_NAME="devpod-port-proxy"
FORWARD_DEFAULT_PORTS=(3000 4000 8080 8082 8083 8084 8091 8092 5432 6432 9092 9094 9000 9001 8200)
FORWARD_EXTRA_PORTS_FILE="${TMPDIR:-/tmp}/devpod-forward-extra-ports"

# Parse PORT or LOCAL:REMOTE into "local remote". Returns non-zero on invalid input.
forward_parse_spec() {
    local spec="$1"
    local local_port remote_port
    if [[ "$spec" =~ ^([0-9]+):([0-9]+)$ ]]; then
        local_port="${BASH_REMATCH[1]}"
        remote_port="${BASH_REMATCH[2]}"
    elif [[ "$spec" =~ ^[0-9]+$ ]]; then
        local_port="$spec"
        remote_port="$spec"
    else
        echo "ERROR: Invalid port spec '$spec'. Use PORT or LOCAL:REMOTE" >&2
        return 1
    fi
    if (( local_port < 1 || local_port > 65535 || remote_port < 1 || remote_port > 65535 )); then
        echo "ERROR: Port out of range in '$spec' (1-65535)" >&2
        return 1
    fi
    echo "${local_port} ${remote_port}"
}

forward_load_extra_specs() {
    if [[ -f "$FORWARD_EXTRA_PORTS_FILE" ]]; then
        # shellcheck disable=SC2002
        cat "$FORWARD_EXTRA_PORTS_FILE" 2>/dev/null | tr ' ' '\n' | sed '/^$/d'
    fi
}

forward_save_extra_specs() {
    mkdir -p "$(dirname "$FORWARD_EXTRA_PORTS_FILE")"
    printf '%s\n' "$@" > "$FORWARD_EXTRA_PORTS_FILE"
}

forward_merge_specs() {
    # stdin: one "local remote" per line → stdout unique by local port (last wins)
    awk 'NF == 2 { map[$1] = $2 } END { for (l in map) print l, map[l] }' | sort -n
}

forward_devpod_container() {
    docker ps --filter "label=devpod.user" --format "{{.Names}}" 2>/dev/null | head -1
}

forward_devpod_ip() {
    local container="$1"
    local ip
    ip=$(docker inspect "$container" --format "{{.NetworkSettings.IPAddress}}" 2>/dev/null)
    if [[ -z "$ip" ]]; then
        ip=$(docker inspect "$container" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>/dev/null | awk '{print $1}')
    fi
    echo "$ip"
}

dev_forward_status() {
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$FORWARD_PROXY_NAME"; then
        echo "○ Port forwarding not active"
        echo "  Start with: neotool dev forward"
        return 0
    fi
    local ports
    ports=$(docker port "$FORWARD_PROXY_NAME" 2>/dev/null \
        | awk -F'[/ ]' '{print $1}' | sort -n | uniq | tr '\n' ' ')
    echo "✓ Port forwarding active (container: $FORWARD_PROXY_NAME)"
    echo "  Published: ${ports:-?(none)}"
    if [[ -f "$FORWARD_EXTRA_PORTS_FILE" ]] && [[ -s "$FORWARD_EXTRA_PORTS_FILE" ]]; then
        echo "  Extra: $(tr '\n' ' ' < "$FORWARD_EXTRA_PORTS_FILE")"
    fi
    echo "  Stop with: neotool dev forward stop"
}

dev_forward_stop() {
    if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "$FORWARD_PROXY_NAME"; then
        docker rm -f "$FORWARD_PROXY_NAME" >/dev/null 2>&1
        echo "✓ Port forwarding stopped"
    else
        echo "No active port forwarding found."
    fi
}

# Start (or recreate) the socat proxy with default ports + optional extra specs.
# Extra specs are PORT or LOCAL:REMOTE strings.
dev_forward_start() {
    local -a extra_specs=()
    local -a all_specs=()
    local spec parsed local_port remote_port
    local container container_ip
    local publish_flags=()
    local socat_script mappings display_ports

    if [[ $# -gt 0 ]]; then
        extra_specs=("$@")
    fi

    container=$(forward_devpod_container)
    if [[ -z "$container" ]]; then
        echo "ERROR: No DevPod container found. Run: neotool dev up"
        return 1
    fi
    container_ip=$(forward_devpod_ip "$container")
    if [[ -z "$container_ip" ]]; then
        echo "ERROR: Could not resolve IP for DevPod container '$container'"
        return 1
    fi
    echo "  Container: $container ($container_ip)"

    # Defaults as LOCAL:REMOTE (same port)
    for local_port in "${FORWARD_DEFAULT_PORTS[@]}"; do
        all_specs+=("${local_port} ${local_port}")
    done

    # Explicit extras from caller
    if ((${#extra_specs[@]} > 0)); then
        for spec in "${extra_specs[@]}"; do
            parsed=$(forward_parse_spec "$spec") || return 1
            all_specs+=("$parsed")
        done
        forward_save_extra_specs "${extra_specs[@]}"
    elif [[ -f "$FORWARD_EXTRA_PORTS_FILE" ]]; then
        # Reload previously saved extras on bare `forward`
        while IFS= read -r spec; do
            [[ -z "$spec" ]] && continue
            parsed=$(forward_parse_spec "$spec") || continue
            all_specs+=("$parsed")
            extra_specs+=("$spec")
        done < <(forward_load_extra_specs)
    fi

    if ((${#all_specs[@]} == 0)); then
        echo "ERROR: No ports to forward"
        return 1
    fi

    mappings=$(printf '%s\n' "${all_specs[@]}" | forward_merge_specs)
    if [[ -z "$mappings" ]]; then
        echo "ERROR: No ports to forward"
        return 1
    fi

    display_ports=""
    while read -r local_port remote_port; do
        [[ -z "$local_port" ]] && continue
        publish_flags+=(-p "${local_port}:${local_port}")
        if [[ "$local_port" == "$remote_port" ]]; then
            display_ports+="${local_port} "
        else
            display_ports+="${local_port}->${remote_port} "
        fi
    done <<< "$mappings"

    # Build entrypoint script that runs all socats in parallel
    socat_script="#!/bin/sh"$'\n'
    while read -r local_port remote_port; do
        [[ -z "$local_port" ]] && continue
        socat_script+="socat TCP-LISTEN:${local_port},fork,reuseaddr TCP:${container_ip}:${remote_port} &"$'\n'
    done <<< "$mappings"
    socat_script+="wait"

    docker rm -f "$FORWARD_PROXY_NAME" >/dev/null 2>&1 || true

    # alpine/socat publishes ports via Docker = Colima/Docker Desktop exposes to Mac
    if ! docker run -d \
        --name "$FORWARD_PROXY_NAME" \
        "${publish_flags[@]}" \
        --entrypoint sh \
        alpine/socat \
        -c "$socat_script" >/dev/null; then
        echo "⚠ Proxy container failed to start."
        echo "  Check: docker logs $FORWARD_PROXY_NAME"
        return 1
    fi

    sleep 2
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$FORWARD_PROXY_NAME"; then
        echo "✓ Port forwarding active via proxy container"
        echo "  Ports: ${display_ports}"
        if ((${#extra_specs[@]} > 0)); then
            echo "  Extra: ${extra_specs[*]}"
        fi
        echo "  Stop with: neotool dev forward stop"
    else
        echo "⚠ Proxy container exited immediately."
        echo "  Check: docker logs $FORWARD_PROXY_NAME"
        return 1
    fi
}

dev_forward() {
    local action="${1:-}"

    case "$action" in
        stop)
            dev_forward_stop
            ;;
        status)
            dev_forward_status
            ;;
        ""|start)
            # Recreate with defaults + any saved extras
            dev_forward_start
            ;;
        add)
            shift
            if [[ $# -eq 0 ]]; then
                echo "Usage: neotool dev forward add <PORT|LOCAL:REMOTE> [...]"
                return 1
            fi
            local -a merged=()
            local -a unique=()
            local spec s u seen
            while IFS= read -r spec; do
                [[ -n "$spec" ]] && merged+=("$spec")
            done < <(forward_load_extra_specs)
            merged+=("$@")
            for s in "${merged[@]}"; do
                seen=false
                if ((${#unique[@]} > 0)); then
                    for u in "${unique[@]}"; do
                        [[ "$u" == "$s" ]] && seen=true && break
                    done
                fi
                [[ "$seen" == false ]] && unique+=("$s")
            done
            dev_forward_start "${unique[@]}"
            ;;
        help|-h|--help)
            cat << EOF
Usage: neotool dev forward [stop|status|add] [PORT|LOCAL:REMOTE ...]

  neotool dev forward              Forward default ports (+ saved extras)
  neotool dev forward 9323         Default ports + 9323
  neotool dev forward 15432:5432   Map host 15432 → DevPod 5432
  neotool dev forward add 9323     Add port(s) to saved extras and recreate
  neotool dev forward status       Show active proxy ports
  neotool dev forward stop         Stop the proxy container
EOF
            ;;
        *)
            # Treat remaining args as port specs (replace saved extras with this set)
            dev_forward_start "$@"
            ;;
    esac
}

# =============================================================================
# Init — runs locally on the host (not inside container)
# =============================================================================

dev_init() {
    local recreate=false
    local start_all=false
    for arg in "$@"; do
        case "$arg" in
            --recreate) recreate=true ;;
            --all)      start_all=true ;;
        esac
    done

    # 1. Check devpod is installed
    if ! command -v devpod >/dev/null 2>&1; then
        echo "ERROR: devpod not found. Install at https://devpod.sh"
        exit 1
    fi

    # Docker bind mounts require the host path to exist. Create placeholders for
    # optional dirs (Cloudflare tunnel tokens, Vault keys, agent CLIs) so
    # `devpod up` works even when those tools are not configured on this machine.
    # Tunnel stays opt-in: start only via `neotool dev tunnel start <n>`.
    mkdir -p \
        "${HOME}/.cloudflare" \
        "${HOME}/.vault-keys" \
        "${HOME}/.claude" \
        "${HOME}/.codex"

    # 2. Start / recreate the DevPod workspace
    echo "▶ [1/3] Starting DevPod workspace..."
    local devpod_flags="--provider docker --ide none"
    [[ "$recreate" == "true" ]] && devpod_flags="$devpod_flags --recreate"
    # shellcheck disable=SC2086
    devpod up "$PROJECT_ROOT" $devpod_flags
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
    if [[ "$start_all" == "true" ]]; then
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
# Sync — update GraphQL contracts end-to-end
# =============================================================================

dev_sync() {
    local NEOTOOL="$CLI_DIR/cli"

    # Detect context: inside DevPod (tmux present) vs Mac
    if command -v tmux >/dev/null 2>&1; then
        # ── Inside DevPod ──────────────────────────────────────────────────────
        # rover not installed in DevPod → use --docker (DinD); non-fatal if it fails
        log "\n▶ [1/5] Regenerating GraphQL supergraph..." "$BLUE"
        if "$NEOTOOL" graphql all --all --docker; then
            log "✓ Supergraph updated" "$GREEN"
        else
            log "⚠ Supergraph generation failed (see above) — continuing with remaining steps" "$YELLOW"
        fi

        echo ""
        log "▶ [2/5] Restarting Apollo Router..." "$BLUE"
        dev_infra restart router
        log "✓ Router restarted" "$GREEN"

        echo ""
        log "▶ [3/5] Stopping web service..." "$BLUE"
        stop_service web

        echo ""
        log "▶ [4/5] Running GraphQL codegen..." "$BLUE"
        (cd "$PROJECT_ROOT/web" && pnpm run codegen)
        log "✓ Codegen complete" "$GREEN"

        echo ""
        log "▶ [5/5] Starting web service..." "$BLUE"
        start_web
        log "\n✓ Contracts updated and web service restarted." "$GREEN"
    else
        # ── On Mac ─────────────────────────────────────────────────────────────
        # Steps 2-5 are exec'd into the DevPod
        local container
        container=$(docker ps --filter "label=devpod.user" --format "{{.Names}}" 2>/dev/null | head -1)
        if [[ -z "$container" ]]; then
            echo "ERROR: No DevPod container found. Run: neotool dev up"
            return 1
        fi

        log "\n▶ [1/5] Regenerating GraphQL supergraph..." "$BLUE"
        if "$NEOTOOL" graphql all --all; then
            log "✓ Supergraph updated" "$GREEN"
        else
            log "⚠ Supergraph generation failed (see above) — continuing with remaining steps" "$YELLOW"
        fi

        echo ""
        log "▶ [2/5] Restarting Apollo Router..." "$BLUE"
        docker exec -i "$container" bash -c "cd /workspaces/invistus && ./scripts/cli/cli dev infra restart router"
        log "✓ Router restarted" "$GREEN"

        echo ""
        log "▶ [3/5] Stopping web service..." "$BLUE"
        docker exec -i "$container" bash -c "cd /workspaces/invistus && ./scripts/cli/cli dev stop web"

        echo ""
        log "▶ [4/5] Running GraphQL codegen..." "$BLUE"
        docker exec -i "$container" bash -c "cd /workspaces/invistus/web && pnpm run codegen"
        log "✓ Codegen complete" "$GREEN"

        echo ""
        log "▶ [5/5] Starting web service..." "$BLUE"
        docker exec -i "$container" bash -c "cd /workspaces/invistus && ./scripts/cli/cli dev start web"
        log "\n✓ Contracts updated and web service restarted." "$GREEN"
    fi
}

# =============================================================================
# Interactive service selector (gum)
# =============================================================================

dev_start_interactive() {
    # Require gum — interactive selector depends on it
    if ! command -v gum &>/dev/null; then
        log "✗ 'gum' not found. Rebuild the DevPod container to install it:" "$RED"
        log "  ./neotool dev up --recreate" "$YELLOW"
        log "" ""
        log "  Or start a specific service directly:" "$GRAY"
        log "  ./neotool dev start <service>" "$GRAY"
        return 1
    fi

    # ── Header ────────────────────────────────────────────────────────────────
    echo ""
    gum style \
        --foreground="212" --border-foreground="99" --border="rounded" \
        --align="center" --width=52 --padding="1 4" \
        "Invistus Dev" "Choose what to start"
    echo ""

    # ── Services selector ─────────────────────────────────────────────────────
    local selected_svcs
    selected_svcs=$(
        printf '%s\n' "${ALL_SERVICES[@]}" | gum choose \
            --no-limit \
            --height=10 \
            --header="  Services  ·  space toggle  ·  enter confirm" \
            --cursor="▸ " \
            --selected-prefix="◉ " \
            --unselected-prefix="○ " \
            --cursor.foreground="212" \
            --selected.foreground="212" \
            --header.foreground="240"
    ) || true

    echo ""

    # ── Workers selector ──────────────────────────────────────────────────────
    local selected_wkrs
    selected_wkrs=$(
        printf '%s\n' "${ALL_WORKERS[@]}" | gum choose \
            --no-limit \
            --height=12 \
            --header="  Workers   ·  space toggle  ·  enter confirm" \
            --cursor="▸ " \
            --selected-prefix="◉ " \
            --unselected-prefix="○ " \
            --cursor.foreground="99" \
            --selected.foreground="99" \
            --header.foreground="240"
    ) || true

    echo ""

    if [[ -z "$selected_svcs" ]] && [[ -z "$selected_wkrs" ]]; then
        log "Nothing selected." "$YELLOW"
        return 0
    fi

    # ── Start security first if selected (other services need JWKS) ───────────
    if echo "$selected_svcs" | grep -q "^security$"; then
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
        [[ $waited -ge 120 ]] && log "⚠ Security not ready after 120s — continuing anyway" "$YELLOW"
    fi

    # ── Start remaining services ──────────────────────────────────────────────
    while IFS= read -r svc; do
        [[ -z "$svc" || "$svc" == "security" ]] && continue
        start_service "$svc"
        sleep 1
    done <<< "$selected_svcs"

    # ── Start workers ─────────────────────────────────────────────────────────
    while IFS= read -r wrk; do
        [[ -z "$wrk" ]] && continue
        start_worker "$wrk"
        sleep 1
    done <<< "$selected_wkrs"

    log "\n✓ Done. Use 'neotool dev attach' to open tmux." "$GREEN"
}

# =============================================================================
# Main
# =============================================================================

main() {
    if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
        exit 0
    fi

    # up, forward, and sync always run locally (outside container — devpod CLI / rover not inside)
    if [[ "${1:-}" == "up" ]]; then
        dev_init "${2:-}"
        exit $?
    fi
    if [[ "${1:-}" == "forward" ]]; then
        shift
        dev_forward "$@"
        exit $?
    fi
    if [[ "${1:-}" == "sync" ]]; then
        dev_sync
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
        # attach + start (with or without service) need -it (tmux attach / gum TUI)
        if [[ "${1:-}" == "attach" ]] || [[ "${1:-}" == "start" ]]; then
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
            local clean_flag=false
            [[ "${2:-}" == "--clean" || "${target}" == "--clean" ]] && clean_flag=true
            [[ "${target}" == "--clean" ]] && target=""
            if [[ -n "$target" ]]; then
                [[ "$target" == "web" ]] && start_web "$clean_flag" || start_service "$target"
                # Auto-attach to the service window so logs are visible immediately
                if tmux_session_exists && [[ -t 1 ]]; then
                    tmux select-window -t "${TMUX_SESSION}:${target}" 2>/dev/null || true
                    tmux attach-session -t "$TMUX_SESSION"
                else
                    log "  → neotool dev attach $target" "$GRAY"
                fi
            else
                ensure_session
                dev_start_interactive
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
        up)
            dev_init "${target:-}"
            ;;
        sync)
            dev_sync
            ;;
        seed)
            dev_seed "${@}"
            ;;
        infra)
            dev_infra "${@}"
            ;;
        restart)
            local clean_flag=false
            [[ "${2:-}" == "--clean" ]] && clean_flag=true
            if [[ -n "$target" ]]; then
                stop_service "$target"
                sleep 1
                [[ "$target" == "web" ]] && start_web "$clean_flag" || start_service "$target"
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
        agent)
            dev_agent "${@}"
            ;;
        watchdog)
            case "${1:-status}" in
                start)
                    log "▶ Iniciando watchdog..." "$BLUE"
                    watchdog_start
                    ;;
                stop)
                    log "▶ Parando watchdog..." "$BLUE"
                    watchdog_stop
                    ;;
                status)
                    if watchdog_running; then
                        log "✓ watchdog running (PID $(cat $WATCHDOG_PID_FILE))" "$GREEN"
                    else
                        log "○ watchdog not running" "$YELLOW"
                    fi
                    ;;
                logs)
                    tail -f "$WATCHDOG_LOG"
                    ;;
                *)
                    echo "Usage: neotool dev watchdog <start|stop|status|logs>"
                    ;;
            esac
            ;;
        tunnel)
            exec "/workspaces/invistus/.devcontainer/neotool-tunnel" "${@}"
            ;;
        gradle-reset)
            gradle_reset
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
