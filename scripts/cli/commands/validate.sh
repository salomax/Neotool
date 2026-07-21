#!/usr/bin/env bash

set -euo pipefail

# Validate Command - Rewritten
#
# Runs code validation with a declarative task registry and scheduler.
# Supports parallel execution with dependency graph, multiple reporters,
# and sophisticated filtering.

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="$CLI_DIR/lib"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Source runner (scheduler)
# shellcheck source=../lib/runner.sh
source "$LIB_DIR/runner.sh"

# Source renderer
# shellcheck source=../lib/render.sh
source "$LIB_DIR/render.sh"

# Paths
WEB_DIR="$PROJECT_ROOT/web"
MOBILE_DIR="$PROJECT_ROOT/mobile"
BACKEND_DIR="$PROJECT_ROOT/service/kotlin"

# ============================================================================
# TASK REGISTRY
# ============================================================================
# Parallel arrays: TASK_IDS, TASK_TARGETS, TASK_STEPS, TASK_WORKDIRS, TASK_NEEDS, TASK_COMMANDS
declare -a TASK_IDS=()
declare -a TASK_TARGETS=()
declare -a TASK_STEPS=()
declare -a TASK_WORKDIRS=()
declare -a TASK_NEEDS=()
declare -a TASK_COMMANDS=()

# Set when neither trivy nor docker is present, so the summary can say plainly
# that nothing was scanned instead of implying a clean result.
SECURITY_TOOLING_MISSING=false

# Set once the run directory exists; consumed by _validate_cleanup.
VALIDATE_LOG_DIR=""

# Terminate every process this run started, and nothing else.
#
# Reads the .pid files written by run_task plus scheduler.pid. Each task is a
# process-group leader (runner.sh enables job control), so killing the negated
# PID takes down its whole tree. Sends TERM, gives it a moment, then KILL.
VALIDATE_CLEANUP_DONE=false

_validate_cleanup() {
    local rc=$?
    [[ "$VALIDATE_CLEANUP_DONE" == "true" ]] && return $rc
    VALIDATE_CLEANUP_DONE=true
    [[ -z "$VALIDATE_LOG_DIR" ]] && return $rc
    [[ -d "$VALIDATE_LOG_DIR" ]] || return $rc

    local pid_file pid survivors=()

    for pid_file in "$VALIDATE_LOG_DIR"/*.pid; do
        [[ -f "$pid_file" ]] || continue
        pid=$(cat "$pid_file" 2>/dev/null || true)
        # Only numeric, still-alive PIDs. A stale file whose process already
        # exited must never be killed: the number may have been recycled.
        [[ "$pid" =~ ^[0-9]+$ ]] || continue
        kill -0 "$pid" 2>/dev/null || continue

        kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
        survivors+=("$pid")
    done

    if [[ ${#survivors[@]} -gt 0 ]]; then
        # Safe to do real work here: the EXIT trap runs during normal exit
        # processing, not from inside a signal handler.
        printf '\033[?25h' 2>/dev/null || true
        echo ""
        log "⚠️  Interrupted — stopping ${#survivors[@]} running task(s)..." "$YELLOW"
        sleep 0.3
        for pid in "${survivors[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 -- "-$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
            fi
        done
        log "   Stopped. Partial logs: ${VALIDATE_LOG_DIR}" "$GRAY"
    fi

    rm -f "$VALIDATE_LOG_DIR"/*.pid 2>/dev/null || true
    printf '\033[?25h' 2>/dev/null || true
    return $rc
}

# Get available services from settings.gradle.kts
get_available_services() {
    local settings_file="$BACKEND_DIR/settings.gradle.kts"
    if [[ ! -f "$settings_file" ]]; then
        log_error "Error: settings.gradle.kts not found at $settings_file"
        return 1
    fi

    grep -E '^\s*include\(' "$settings_file" | \
        sed -E 's/.*include\(//' | \
        sed -E 's/\).*//' | \
        grep -oE '":[^"]+"' | \
        sed -E 's/^"://' | \
        sed -E 's/"$//' | \
        sort
}

# Add task to registry
add_task() {
    local id="$1"
    local target="$2"
    local step="$3"
    local workdir="$4"
    local needs="$5"
    local cmd="$6"

    TASK_IDS+=("$id")
    TASK_TARGETS+=("$target")
    TASK_STEPS+=("$step")
    TASK_WORKDIRS+=("$workdir")
    TASK_NEEDS+=("$needs")
    TASK_COMMANDS+=("$cmd")
}

# Build complete task registry
build_task_registry() {
    # Web tasks
    add_task "web:typecheck" "web" "typecheck" "$WEB_DIR" "-" "pnpm run typecheck"
    add_task "web:lint" "web" "lint" "$WEB_DIR" "-" "pnpm run lint"
    add_task "web:test" "web" "test" "$WEB_DIR" "web:typecheck" "pnpm run test"
    add_task "web:coverage" "web" "coverage" "$WEB_DIR" "web:test" "pnpm run test:coverage"

    # Mobile tasks
    add_task "mobile:typecheck" "mobile" "typecheck" "$MOBILE_DIR" "-" "pnpm run typecheck"
    add_task "mobile:lint" "mobile" "lint" "$MOBILE_DIR" "-" "pnpm run lint"
    add_task "mobile:test" "mobile" "test" "$MOBILE_DIR" "mobile:typecheck" "pnpm run test"
    add_task "mobile:coverage" "mobile" "coverage" "$MOBILE_DIR" "mobile:test" "pnpm run test:coverage"

    # Service tasks (all services)
    add_task "service:compile" "service" "compile" "$BACKEND_DIR" "-" "./gradlew classes --console=plain"
    add_task "service:lint" "service" "lint" "$BACKEND_DIR" "-" "./gradlew ktlintCheck --console=plain"
    add_task "service:test" "service" "test" "$BACKEND_DIR" "service:compile" "./gradlew test --console=plain"
    add_task "service:coverage" "service" "coverage" "$BACKEND_DIR" "service:test" "./gradlew koverRootReport koverVerify --console=plain"

    # Service tasks (per-service versions, added if service filter is used)
    local available_services
    # sort -u: settings.gradle.kts can list a module more than once, which
    # previously registered its four tasks once per occurrence.
    available_services=$(get_available_services 2>/dev/null | sort -u || echo "")
    for svc in $available_services; do
        add_task "service:$svc:compile" "service:$svc" "compile" "$BACKEND_DIR" "-" "./gradlew :${svc}:classes --console=plain"
        add_task "service:$svc:lint" "service:$svc" "lint" "$BACKEND_DIR" "-" "./gradlew :${svc}:ktlintCheck --console=plain"
        add_task "service:$svc:test" "service:$svc" "test" "$BACKEND_DIR" "service:$svc:compile" "./gradlew :${svc}:test --console=plain"
        add_task "service:$svc:coverage" "service:$svc" "coverage" "$BACKEND_DIR" "service:$svc:test" "./gradlew :${svc}:koverXmlReport :${svc}:koverVerify --console=plain"
    done

    # Default security task, matching .github/workflows/ci.yml's security-scan
    # job exactly (fs / CRITICAL,HIGH / exit-code 1 / table).
    #
    # This must live in the default registry: previously security tasks were
    # only created by parse_security_flags, so `neotool validate` reported a
    # green run having never invoked trivy at all, while CI still gated on it.
    #
    # The `security` subcommand rebuilds this task from its own flags.
    if command -v trivy >/dev/null 2>&1; then
        add_task "security:fs" "security" "scan" "$PROJECT_ROOT" "-" \
            "trivy fs --config trivy.yaml --timeout 15m --format table --severity CRITICAL,HIGH --exit-code 1 ."
    elif command -v docker >/dev/null 2>&1; then
        add_task "security:fs" "security" "scan" "$PROJECT_ROOT" "-" \
            "docker run --rm -v '$PROJECT_ROOT:/src' -w /src aquasec/trivy fs --config /src/trivy.yaml --timeout 15m --format table --severity CRITICAL,HIGH --exit-code 1 /src"
    else
        # Warn loudly rather than failing the whole local run. `validate
        # security` (explicit intent) still hard-fails in parse_security_flags.
        SECURITY_TOOLING_MISSING=true
    fi

    # E2E tasks
    # Note: Preferentially uses prepush.txt manifest if it exists, otherwise falls back to --grep @prepush.
    # The manifest approach is temporary; eventually specs should be tagged with @prepush and this can be removed.
    local e2e_cmd
    if [[ -f "$WEB_DIR/tests/e2e/prepush.txt" ]]; then
        # Use explicit manifest of spec files
        e2e_cmd="pnpm exec playwright test $(cat "$WEB_DIR/tests/e2e/prepush.txt" | tr '\n' ' ') --project=chromium"
    else
        # Fallback to grep for @prepush tag
        e2e_cmd="pnpm exec playwright test --grep @prepush --project=chromium"
    fi
    add_task "e2e" "e2e" "test" "$WEB_DIR" "-" "$e2e_cmd"
}

# Filter task registry by target selection
# Removes tasks not in the selected targets
filter_by_targets() {
    local -a selected_targets=("$@")

    # Default / "all" deliberately EXCLUDE e2e: the Playwright suite is slow
    # and needs a running stack, so it must stay opt-in (`validate e2e`).
    if [[ ${#selected_targets[@]} -eq 0 ]]; then
        selected_targets=("web" "mobile" "service" "security")
    fi

    # Handle "all" target
    local -a expanded_targets=()
    for sel_target in "${selected_targets[@]}"; do
        if [[ "$sel_target" == "all" ]]; then
            expanded_targets=("web" "mobile" "service" "security")
            break
        else
            expanded_targets+=("$sel_target")
        fi
    done
    selected_targets=("${expanded_targets[@]}")

    # Build new arrays with only selected tasks
    local -a new_ids=() new_targets=() new_steps=() new_workdirs=() new_needs=() new_commands=()

    for i in "${!TASK_IDS[@]}"; do
        local task_target="${TASK_TARGETS[$i]}"
        local matched=false

        for sel_target in "${selected_targets[@]}"; do
            # Exact match only. Prefix-matching "service" against
            # "service:banking" made the plain `service` target pull in every
            # per-service task as well, running the whole gradle build once
            # for the aggregate plus once more per service.
            if [[ "$task_target" == "$sel_target" ]]; then
                matched=true
                break
            fi
        done

        if [[ "$matched" == "true" ]]; then
            new_ids+=("${TASK_IDS[$i]}")
            new_targets+=("${TASK_TARGETS[$i]}")
            new_steps+=("${TASK_STEPS[$i]}")
            new_workdirs+=("${TASK_WORKDIRS[$i]}")
            new_needs+=("${TASK_NEEDS[$i]}")
            new_commands+=("${TASK_COMMANDS[$i]}")
        fi
    done

    TASK_IDS=("${new_ids[@]}")
    TASK_TARGETS=("${new_targets[@]}")
    TASK_STEPS=("${new_steps[@]}")
    TASK_WORKDIRS=("${new_workdirs[@]}")
    TASK_NEEDS=("${new_needs[@]}")
    TASK_COMMANDS=("${new_commands[@]}")
}

# Filter task registry by step (--only or --skip)
# Steps vocabulary: typecheck, lint, test, coverage
filter_by_steps() {
    local filter_type="$1"  # "only" or "skip"
    local -a steps=()

    # Parse comma-separated step list
    IFS=',' read -ra steps <<< "$2"

    local -a new_ids=() new_targets=() new_steps=() new_workdirs=() new_needs=() new_commands=()

    for i in "${!TASK_IDS[@]}"; do
        local task_step="${TASK_STEPS[$i]}"
        local matched=false

        for step in "${steps[@]}"; do
            step="${step# }"  # trim leading space
            if [[ "$task_step" == "$step" ]]; then
                matched=true
                break
            fi
        done

        # Decide to include: include if matched AND filter is "only", OR NOT matched AND filter is "skip"
        local should_include=false
        if [[ "$filter_type" == "only" && "$matched" == "true" ]]; then
            should_include=true
        elif [[ "$filter_type" == "skip" && "$matched" != "true" ]]; then
            should_include=true
        fi

        if [[ "$should_include" == "true" ]]; then
            new_ids+=("${TASK_IDS[$i]}")
            new_targets+=("${TASK_TARGETS[$i]}")
            new_steps+=("${TASK_STEPS[$i]}")
            new_workdirs+=("${TASK_WORKDIRS[$i]}")
            new_needs+=("${TASK_NEEDS[$i]}")
            new_commands+=("${TASK_COMMANDS[$i]}")
        fi
    done

    TASK_IDS=("${new_ids[@]}")
    TASK_TARGETS=("${new_targets[@]}")
    TASK_STEPS=("${new_steps[@]}")
    TASK_WORKDIRS=("${new_workdirs[@]}")
    TASK_NEEDS=("${new_needs[@]}")
    TASK_COMMANDS=("${new_commands[@]}")

    # Prune tasks that depend on pruned tasks (transitively)
    prune_orphaned_tasks
}

# Prune tasks whose dependencies are not in the registry
prune_orphaned_tasks() {
    local changed=true
    while [[ "$changed" == "true" ]]; do
        changed=false
        local -a new_ids=() new_targets=() new_steps=() new_workdirs=() new_needs=() new_commands=()

        for i in "${!TASK_IDS[@]}"; do
            local task_id="${TASK_IDS[$i]}"
            local needs="${TASK_NEEDS[$i]}"
            local should_keep=true

            # Check if all dependencies are present
            if [[ "$needs" != "-" && -n "$needs" ]]; then
                for dep in $needs; do
                    # Check if dep is in the remaining tasks
                    local dep_found=false
                    for j in "${!TASK_IDS[@]}"; do
                        if [[ "${TASK_IDS[$j]}" == "$dep" ]]; then
                            dep_found=true
                            break
                        fi
                    done

                    if [[ "$dep_found" != "true" ]]; then
                        should_keep=false
                        changed=true
                        break
                    fi
                done
            fi

            if [[ "$should_keep" == "true" ]]; then
                new_ids+=("${TASK_IDS[$i]}")
                new_targets+=("${TASK_TARGETS[$i]}")
                new_steps+=("${TASK_STEPS[$i]}")
                new_workdirs+=("${TASK_WORKDIRS[$i]}")
                new_needs+=("${TASK_NEEDS[$i]}")
                new_commands+=("${TASK_COMMANDS[$i]}")
            fi
        done

        TASK_IDS=("${new_ids[@]}")
        TASK_TARGETS=("${new_targets[@]}")
        TASK_STEPS=("${new_steps[@]}")
        TASK_WORKDIRS=("${new_workdirs[@]}")
        TASK_NEEDS=("${new_needs[@]}")
        TASK_COMMANDS=("${new_commands[@]}")
    done
}

# Filter by changed files (--changed)
filter_by_changed() {
    # Get changed files
    local changed_files
    changed_files=$(git diff --name-only origin/main...HEAD 2>/dev/null || true)
    changed_files="$changed_files"$(git diff --name-only HEAD 2>/dev/null || true)

    if [[ -z "$changed_files" ]]; then
        log "Warning: Could not determine changed files, running all targets" "$YELLOW"
        return 0
    fi

    # Map files to targets
    local -a touched_targets=()
    local has_other_changes=false

    while IFS= read -r file; do
        if [[ "$file" == web/* ]]; then
            touched_targets+=("web")
        elif [[ "$file" == mobile/* ]]; then
            touched_targets+=("mobile")
        elif [[ "$file" == service/kotlin/* ]]; then
            touched_targets+=("service")
        else
            # Conservative: run everything for infra/root changes
            has_other_changes=true
        fi
    done <<< "$changed_files"

    # Deduplicate
    local -a unique_targets=()
    for target in "${touched_targets[@]}"; do
        local found=false
        for unique in "${unique_targets[@]}"; do
            if [[ "$unique" == "$target" ]]; then
                found=true
                break
            fi
        done
        if [[ "$found" != "true" ]]; then
            unique_targets+=("$target")
        fi
    done

    # If there are other changes, run all; otherwise filter to touched
    if [[ "$has_other_changes" != "true" && ${#unique_targets[@]} -gt 0 ]]; then
        filter_by_targets "${unique_targets[@]}"
        # Always include security
        unique_targets+=("security")
    fi
}

# Print task registry in a readable format
print_task_list() {
    echo ""
    log "Task Graph:" "$BRIGHT"
    echo ""

    for i in "${!TASK_IDS[@]}"; do
        local id="${TASK_IDS[$i]}"
        local target="${TASK_TARGETS[$i]}"
        local step="${TASK_STEPS[$i]}"
        local needs="${TASK_NEEDS[$i]}"
        local cmd="${TASK_COMMANDS[$i]}"

        printf "  %-25s | target=%-12s | needs=%s\n" "$id" "$target" "$needs"
        printf "    $ %s\n" "$cmd"
        echo ""
    done
}

# ============================================================================
# CLI PARSING
# ============================================================================

show_help() {
    cat << EOF
$(log "neotool validate" "$BRIGHT")

Run code validations for web, mobile, services, security, and E2E.

$(log "Usage:" "$BRIGHT")
  neotool validate [target...] [options]
  neotool validate security [options]

$(log "Targets (default: all):" "$BRIGHT")
  web                 Web frontend validations
  mobile              Mobile app validations
  service             All backend services
  service:NAME        Specific backend service
  security            Security scan (Trivy)
  e2e                 E2E tests (Playwright)
  all                 All targets (same as default)

$(log "Options:" "$BRIGHT")
  -j, --jobs N        Max concurrent tasks (default: $(( $(nproc || echo 4) / 2 )))
      --only STEPS    Run only these steps: typecheck,lint,test,coverage (comma-separated)
      --skip STEPS    Skip these steps (comma-separated)
      --changed       Only validate changed targets vs origin/main
      --fail-fast     Cancel remaining work on first failure
      --reporter R    live|plain|json|github (default: auto based on TTY)
      --log-dir DIR   Output directory (default: .neotool/validate/<run-id>/)
  -l, --list          Print resolved task graph and exit
  -h, --help          Show this help message

$(log "Security Subcommand:" "$BRIGHT")
  neotool validate security [--scan fs,config,secret]
                             [--severity LIST]
                             [--ignore-unfixed]
                             [--exit-code N]
                             [--format table|json|sarif]

$(log "Deprecated flags (will be removed):" "$BRIGHT")
  --web              -> use: neotool validate web
  --mobile           -> use: neotool validate mobile
  --service [NAME]   -> use: neotool validate service or service:NAME
  --skip-coverage    -> use: neotool validate --skip coverage
  --parallel         -> now the default (tune with -j N)
  --no-parallel      -> use: neotool validate -j 1

$(log "Examples:" "$BRIGHT")
  neotool validate                    # Run all validations
  neotool validate web                # Web only
  neotool validate web mobile         # Web and mobile
  neotool validate service:banking    # Specific service
  neotool validate --only lint        # Only lint step
  neotool validate --skip coverage    # Skip coverage
  neotool validate --changed -j 4     # Changed targets, 4 jobs
  neotool validate --list             # Print task graph
  neotool validate security           # Security scan only
EOF
}

show_security_help() {
    cat << EOF
$(log "neotool validate security" "$BRIGHT")

Run security scans using Trivy.

$(log "Usage:" "$BRIGHT")
  neotool validate security [options]

$(log "Options:" "$BRIGHT")
  --scan TYPE         Scan types: fs, config, secret (comma-separated, default: fs)
  --severity LIST     CRITICAL,HIGH,MEDIUM,LOW (default: CRITICAL,HIGH)
  --ignore-unfixed    Ignore unfixed vulnerabilities
  --exit-code N       Exit code on findings (default: 1)
  --format FORMAT     Output format: table|json|sarif (default: table)
  --allow-missing-tools  Warn instead of fail if tools are missing
  -h, --help          Show this help

$(log "Examples:" "$BRIGHT")
  neotool validate security
  neotool validate security --severity CRITICAL
  neotool validate security --scan fs,config
  neotool validate security --format json --exit-code 0
EOF
}

# Parse security-specific flags and build security tasks
parse_security_flags() {
    local -a scan_types=("fs")
    local severity="CRITICAL,HIGH"
    local ignore_unfixed=false
    local exit_code=1
    local format="table"
    local allow_missing_tools=false

    # Parse security-specific arguments (remaining args after 'security')
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --scan)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --scan requires an argument"
                    exit 1
                fi
                IFS=',' read -ra scan_types <<< "$2"
                shift 2
                ;;
            --severity)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --severity requires an argument"
                    exit 1
                fi
                severity="$2"
                shift 2
                ;;
            --ignore-unfixed)
                ignore_unfixed=true
                shift
                ;;
            --exit-code)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --exit-code requires an argument"
                    exit 1
                fi
                exit_code="$2"
                shift 2
                ;;
            --format)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --format requires an argument"
                    exit 1
                fi
                format="$2"
                shift 2
                ;;
            --allow-missing-tools)
                allow_missing_tools=true
                shift
                ;;
            -h|--help)
                show_security_help
                exit 0
                ;;
            *)
                log_error "Error: Unknown security option: $1"
                exit 1
                ;;
        esac
    done

    # Check if trivy is available, fall back to docker
    local trivy_cmd
    if command -v trivy >/dev/null 2>&1; then
        trivy_cmd="trivy"
    elif command -v docker >/dev/null 2>&1; then
        trivy_cmd="docker run --rm -v '$PROJECT_ROOT:/src' aquasec/trivy"
    else
        # Neither tool exists. Falling back to a docker command here would just
        # fail at exec time with "docker: command not found" and be reported as
        # a scan failure, which is misleading.
        if [[ "$allow_missing_tools" == "true" ]]; then
            log "⚠️  Warning: neither trivy nor docker found — SKIPPING security scan" "$YELLOW"
            log "   Nothing was scanned. Do not read this run as a clean bill of health." "$YELLOW"
            return 0
        fi
        log_error "Error: trivy not found and docker is not available"
        log_error "Install trivy: https://github.com/aquasecurity/trivy"
        log_error "Or install docker for container-based scanning"
        log_error "(pass --allow-missing-tools to downgrade this to a warning)"
        exit 1
    fi

    # Build tasks for each scan type
    for scan_type in "${scan_types[@]}"; do
        scan_type="${scan_type# }"  # trim leading space
        local task_id="security:$scan_type"
        local cmd_args="$scan_type --format $format --severity $severity --exit-code $exit_code --timeout 15m"

        if [[ "$ignore_unfixed" == "true" ]]; then
            cmd_args="$cmd_args --ignore-unfixed"
        fi

        # Honour .trivyignore. Note this is --ignorefile (a list of CVE IDs to
        # suppress), NOT --skip-files (paths to exclude from scanning) —
        # they're different flags with very different consequences.
        if [[ -f "$PROJECT_ROOT/.trivyignore" ]]; then
            cmd_args="$cmd_args --ignorefile '$PROJECT_ROOT/.trivyignore'"
        fi

        # Add to task registry
        if [[ "$trivy_cmd" == "trivy" ]]; then
            local config_arg=""
            [[ -f "$PROJECT_ROOT/trivy.yaml" ]] && config_arg="--config trivy.yaml"
            add_task "$task_id" "security" "scan" "$PROJECT_ROOT" "-" "trivy $config_arg $cmd_args ."
        else
            # For docker-based trivy, rewrite paths and pin workdir so trivy.yaml resolves.
            cmd_args="${cmd_args//\$PROJECT_ROOT/\/src}"
            local config_arg=""
            [[ -f "$PROJECT_ROOT/trivy.yaml" ]] && config_arg="--config /src/trivy.yaml"
            add_task "$task_id" "security" "scan" "$PROJECT_ROOT" "-" \
                "docker run --rm -v '$PROJECT_ROOT:/src' -w /src aquasec/trivy $config_arg $cmd_args /src"
        fi
    done
}

main() {
    local -a targets=()
    local max_jobs=0
    local only_steps=""
    local skip_steps=""
    local use_changed=false
    local fail_fast=false
    local reporter="auto"
    local log_dir=""
    local list_only=false
    local security_mode=false

    # Default max jobs = min(8, max(2, cores/2))
    local cores
    cores=$(nproc 2>/dev/null || echo 4)
    max_jobs=$(( cores / 2 ))
    [[ $max_jobs -lt 2 ]] && max_jobs=2
    [[ $max_jobs -gt 8 ]] && max_jobs=8

    # Check if first argument is 'security' (before parsing other args)
    if [[ $# -gt 0 && "$1" == "security" ]]; then
        security_mode=true
        shift
        # Parse security-specific flags and build only security tasks
        # NOTE: We do NOT call build_task_registry here, only parse_security_flags
        parse_security_flags "$@"
        # Set targets to security so it won't be filtered out
        targets=("security")
    else
        # Parse arguments (bash 3.2 compatible, using shift)
        while [[ $# -gt 0 ]]; do
            local arg="$1"

            case "$arg" in
                # Targets (including service:NAME format)
                web|mobile|security|e2e|all)
                    targets+=("$arg")
                    shift
                    ;;
                service:*)
                    # service:NAME format (e.g., service:banking)
                    targets+=("$arg")
                    shift
                    ;;
                service)
                    # Check if next arg is a service name (not a flag)
                    if [[ $# -gt 1 && ! "$2" =~ ^- ]]; then
                        targets+=("service:$2")
                        shift 2
                    else
                        targets+=("service")
                        shift
                    fi
                    ;;

            # Options
            -j|--jobs)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: $arg requires an argument"
                    exit 1
                fi
                max_jobs="$2"
                shift 2
                ;;
            --only)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: $arg requires an argument"
                    exit 1
                fi
                only_steps="$2"
                shift 2
                ;;
            --skip)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: $arg requires an argument"
                    exit 1
                fi
                skip_steps="$2"
                shift 2
                ;;
            --changed)
                use_changed=true
                shift
                ;;
            --fail-fast)
                fail_fast=true
                shift
                ;;
            --reporter)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: $arg requires an argument"
                    exit 1
                fi
                reporter="$2"
                shift 2
                ;;
            --log-dir)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: $arg requires an argument"
                    exit 1
                fi
                log_dir="$2"
                shift 2
                ;;
            -l|--list)
                list_only=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;

            # Deprecated flags with helpful messages
            --web)
                log_error "Error: --web is removed; use: neotool validate web"
                exit 2
                ;;
            --mobile)
                log_error "Error: --mobile is removed; use: neotool validate mobile"
                exit 2
                ;;
            --service)
                if [[ $# -gt 1 && ! "$2" =~ ^- ]]; then
                    log_error "Error: --service is removed; use: neotool validate service:$2"
                else
                    log_error "Error: --service is removed; use: neotool validate service"
                fi
                exit 2
                ;;
            --skip-coverage)
                log_error "Error: --skip-coverage is removed; use: neotool validate --skip coverage"
                exit 2
                ;;
            --parallel)
                log_error "Error: --parallel is removed; parallel execution is now the default (use -j N to tune)"
                exit 2
                ;;
            --no-parallel)
                log_error "Error: --no-parallel is removed; use: neotool validate -j 1"
                exit 2
                ;;

            *)
                log_error "Error: Unknown option: $arg"
                echo ""
                show_help
                exit 1
                ;;
        esac
    done
    fi  # End of if/else for security_mode

    # Build task registry (if not already built in security mode)
    if [[ "$security_mode" != "true" ]]; then
        build_task_registry
    fi

    # Apply filters in order: target -> --changed -> --only -> --skip
    #
    # Always filter, even with no targets given. Skipping this call on the
    # default path meant a bare `neotool validate` ran the ENTIRE registry:
    # the e2e Playwright suite plus every per-service gradle task on top of the
    # aggregate ones. filter_by_targets supplies the default set itself.
    filter_by_targets ${targets[@]+"${targets[@]}"}

    if [[ "$use_changed" == "true" ]]; then
        filter_by_changed
    fi

    if [[ -n "$only_steps" ]]; then
        filter_by_steps "only" "$only_steps"
    fi

    if [[ -n "$skip_steps" ]]; then
        filter_by_steps "skip" "$skip_steps"
    fi

    # Create log directory
    if [[ -z "$log_dir" ]]; then
        local run_id
        run_id=$(date +%Y%m%d-%H%M%S)
        log_dir="$PROJECT_ROOT/.neotool/validate/$run_id"
    fi

    # Handle --list before creating anything on disk: it runs nothing, so it
    # should not litter .neotool/validate/ with empty run directories.
    if [[ "$list_only" == "true" ]]; then
        print_task_list
        exit 0
    fi

    mkdir -p "$log_dir"

    # Reap this run's processes on ANY exit, including an unexpected one.
    #
    # The scheduler is backgrounded, so if the foreground renderer dies the
    # script can exit while tasks keep running detached — that is how a crashed
    # repaint left orphaned tsc/eslint/gradle processes behind.
    #
    # Cleanup is driven by PID files this run wrote, never by process name:
    # `pgrep -f gradle` would match daemons from unrelated sessions.
    VALIDATE_LOG_DIR="$log_dir"
    trap _validate_cleanup EXIT
    # Override runner.sh's INT/TERM handler, which does `kill -9 -$$`. That
    # SIGKILLs this shell before the EXIT trap can run, AND job control puts
    # each task in its own process group, so -$$ never reached them anyway:
    # Ctrl-C left the whole task tree orphaned. Reap explicitly, then exit.
    # Keep these handlers TRIVIAL — just exit. `exit` runs the EXIT trap, so
    # _validate_cleanup still does the reaping.
    #
    # Doing the cleanup inline here instead was a mistake: the renderer sits
    # inside command substitutions almost continuously, and a SIGINT landing
    # mid-`$(` leaves bash's parser in a broken state. It then choked trying to
    # parse a multi-statement handler:
    #   render.sh: interrupt trap: line 2: unexpected EOF while looking for matching `)'
    # A bare `exit N` gives the parser nothing to trip over.
    trap 'exit 130' INT
    trap 'exit 143' TERM
    log "Starting validation (${#TASK_IDS[@]} tasks, $max_jobs concurrent)" "$BRIGHT"
    echo ""

    RUNNER_LOG_DIR="$log_dir"
    RUNNER_JOBS_MAX="$max_jobs"
    RUNNER_FAIL_FAST="$fail_fast"

    # Capture start time and build task ID list for renderer
    if command -v gdate >/dev/null 2>&1; then
        RUN_START=$(gdate +%s)
    else
        RUN_START=$(date +%s)
    fi
    RENDER_TASK_IDS="${TASK_IDS[*]}"
    export RUN_START RENDER_TASK_IDS RENDER_LOG_DIR="$log_dir"

    # Run scheduler in background
    run_scheduler &
    local scheduler_pid=$!
    # Recorded so _validate_cleanup reaps the scheduler itself, not just the
    # tasks — otherwise it would survive and keep launching queued work.
    echo "$scheduler_pid" > "$log_dir/scheduler.pid"

    # Resolve the reporter. "auto" picks live on a TTY, plain otherwise; an
    # explicit --reporter always wins.
    local active_reporter="$reporter"
    if [[ "$active_reporter" == "auto" ]]; then
        if [[ -t 1 ]]; then
            active_reporter="live"
        else
            active_reporter="plain"
        fi
    fi

    RENDER_REPORTER="$active_reporter"
    case "$active_reporter" in
        live)   render_live ;;
        plain)  render_plain ;;
        json)   render_json ;;
        github) render_github ;;
        *)
            log_error "Unknown reporter: $active_reporter"
            exit 2
            ;;
    esac

    # Wait for scheduler to complete
    local scheduler_exit=0
    wait $scheduler_pid 2>/dev/null || scheduler_exit=$?

    if [[ $scheduler_exit -ne 0 ]]; then
        # json/github reporters already encode failures in their own format;
        # the human-readable report would corrupt machine-parsed output.
        if [[ "$active_reporter" == "live" || "$active_reporter" == "plain" ]]; then
            render_failure_report "$log_dir" "$PROJECT_ROOT"
        fi
        log_error "❌ Validation failed"
        exit 1
    fi

    if [[ ${#TASK_IDS[@]} -eq 0 ]]; then
        # Never render a green check for a run that executed nothing — that is
        # indistinguishable from a real pass at a glance.
        log "⚠️  Nothing ran: no tasks matched this selection." "$YELLOW"
        exit 0
    fi

    log "✅ All validations passed! (${#TASK_IDS[@]} checks)" "$GREEN"
    if [[ "$SECURITY_TOOLING_MISSING" == "true" ]]; then
        log "⚠️  Security scan was SKIPPED — neither trivy nor docker found." "$YELLOW"
        log "   CI still gates on it; this run proves nothing about vulnerabilities." "$YELLOW"
    fi
    exit 0
}

# Main entry point
main "$@"
