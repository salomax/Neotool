#!/usr/bin/env bash

set -euo pipefail

# Validate Command
# 
# Runs all validations for both frontend and backend:
# Backend validation order:
# - Compile (classes - compiles main sources, runs KSP processing, catches type errors)
# - Tests (test - includes unit and integration)
# - Lint (ktlint)
# - Coverage (kover)
# - Security scan (trivy fs, if available)
# Frontend validation order:
# - Typecheck (tsc)
# - Tests (vitest)
# - Lint (eslint)
# - Coverage (vitest coverage)

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Paths
WEB_DIR="$PROJECT_ROOT/web"
BACKEND_DIR="$PROJECT_ROOT/service/kotlin"

# Track validation results
VALIDATION_FAILED=false

# UI state tracking
declare -a TASK_NAMES=()
declare -a TASK_STATUSES=()
declare -a TASK_START_TIMES=()
declare -a TASK_ELAPSED_TIMES=()
declare -a TASK_COMMANDS=()
declare -a TASK_HEADER_INITIALIZED=()  # Track if header (status + command) has been displayed
CURRENT_TASK_INDEX=-1
UI_INITIALIZED=false

# Get available services from settings.gradle.kts
get_available_services() {
    local settings_file="$BACKEND_DIR/settings.gradle.kts"
    if [[ ! -f "$settings_file" ]]; then
        log_error "Error: settings.gradle.kts not found at $settings_file"
        return 1
    fi
    
    # Extract service names from include() statement
    # Example: include(":common", ":security", ":app", ":assistant", ":financialdata")
    # We'll extract: common, security, app, assistant, financialdata
    grep -E '^\s*include\(' "$settings_file" | \
        sed -E 's/.*include\(//' | \
        sed -E 's/\).*//' | \
        grep -oE '":[^"]+"' | \
        sed -E 's/^"://' | \
        sed -E 's/"$//' | \
        sort
}

# Show help text
show_help() {
    local available_services
    available_services=$(get_available_services 2>/dev/null | tr '\n' ',' | sed 's/,$//' | sed 's/,/, /g')
    
    cat << EOF
$(log "Validation Command" "$BRIGHT")

Usage: $0 [options]

Runs all validations for both frontend and backend:
  - Lint (frontend: eslint, backend: ktlint)
  - Typecheck (frontend: tsc, backend: compile classes)
  - Tests (frontend: vitest, backend: test - includes unit and integration)
  - Coverage (frontend: vitest coverage, backend: kover)
  - Security scan (trivy fs, same as CI, when trivy is installed)

Options:
  --web              Run only web (frontend) validations
  --service [name]   Run only service (backend) validations
                     If service name is provided, validate only that service
                     Available services: ${available_services:-app, assistant, security, common}
                     If no name provided, validate all services
  --skip-coverage    Skip coverage checks (faster validation)
  --no-parallel      Run web/service/security validations sequentially (default is parallel when running full validation)
  --help             Show this help message

Examples:
  $0                      # Run all validations
  $0 --web                # Run only web validations
  $0 --service             # Run all service validations
  $0 --service security    # Run only security service validations
  $0 --service app         # Run only app service validations
  $0 --skip-coverage       # Run validations without coverage checks
  $0 --no-parallel         # Run full validation sequentially
EOF
}

# Format elapsed time for display
format_elapsed_time() {
    local seconds="$1"
    # Normalize decimal separator to dot (handle locale issues)
    seconds="${seconds//,/.}"
    # Convert to integer seconds for comparison
    local int_seconds=${seconds%.*}
    # Ensure int_seconds is numeric (remove any non-numeric characters)
    int_seconds="${int_seconds//[^0-9]/}"
    # Default to 0 if empty
    int_seconds="${int_seconds:-0}"
    local decimal_part="${seconds#*.}"
    decimal_part="${decimal_part:0:1}"  # First decimal digit
    # Ensure decimal_part is numeric
    decimal_part="${decimal_part//[^0-9]/}"
    
    if [[ $int_seconds -lt 1 ]]; then
        printf "0.%ss" "${decimal_part:-0}"
    elif [[ $int_seconds -lt 60 ]]; then
        if [[ -n "$decimal_part" ]] && [[ "$decimal_part" != "0" ]]; then
            printf "%d.%ss" "$int_seconds" "$decimal_part"
        else
            printf "%ds" "$int_seconds"
        fi
    else
        local mins=$(( int_seconds / 60 ))
        local secs=$(( int_seconds % 60 ))
        printf "%dm %ds" "$mins" "$secs"
    fi
}

# Initialize validation UI
init_validation_ui() {
    if [[ "$UI_INITIALIZED" == true ]]; then
        return
    fi
    
    # Only use advanced UI if we're in a TTY
    if [[ ! -t 1 ]]; then
        # Not a TTY, use simple output
        log "\n🔍 Starting Validation Process\n" "$BRIGHT"
        UI_INITIALIZED=true
        CURRENT_TASK_INDEX=-1
        return
    fi
    
    # Show header
    log "\n🔍 Starting Validation Process\n" "$BRIGHT"
    echo ""
    
    UI_INITIALIZED=true
    CURRENT_TASK_INDEX=-1
}

# Add a new task to the display
add_new_task() {
    local task_name="$1"
    local command="${2:-}"
    
    CURRENT_TASK_INDEX=$((CURRENT_TASK_INDEX + 1))
    TASK_NAMES+=("$task_name")
    TASK_STATUSES+=("running")
    TASK_COMMANDS+=("$command")
    TASK_HEADER_INITIALIZED+=(false)  # Initialize header as not displayed yet
    
    # Get start time
    if command -v gdate >/dev/null 2>&1; then
        TASK_START_TIMES+=("$(gdate +%s.%N)")
    else
        TASK_START_TIMES+=("$(date +%s.%N 2>/dev/null || date +%s)")
    fi
    
    TASK_ELAPSED_TIMES+=("")
    
    # Display the new task with running status and command
    update_task_status "$CURRENT_TASK_INDEX" "running" "" "$command"
}

# Update task status display
update_task_status() {
    local task_idx="$1"
    local status="$2"
    local elapsed_time="${3:-}"
    local command="${4:-}"
    
    if [[ $task_idx -lt 0 ]] || [[ $task_idx -ge ${#TASK_NAMES[@]} ]]; then
        return
    fi
    
    # Store command if provided
    if [[ -n "$command" ]]; then
        TASK_COMMANDS[$task_idx]="$command"
    fi
    
    # If not a TTY, use simple output
    if [[ ! -t 1 ]]; then
        local task_name="${TASK_NAMES[$task_idx]}"
        case "$status" in
            running)
                log "  ⏳ $task_name..." "$BLUE"
                ;;
            success)
                log "  ✅ $task_name passed" "$GREEN"
                if [[ -n "${TASK_COMMANDS[$task_idx]}" ]]; then
                    log "    Command: ${TASK_COMMANDS[$task_idx]}" "$CYAN"
                fi
                ;;
            failure)
                log_error "  ❌ $task_name failed"
                if [[ -n "${TASK_COMMANDS[$task_idx]}" ]]; then
                    log "    Command: ${TASK_COMMANDS[$task_idx]}" "$CYAN"
                fi
                ;;
        esac
        return
    fi
    
    local previous_status="${TASK_STATUSES[$task_idx]}"
    
    # Check if this is the initial display (header not shown yet)
    local header_initialized="${TASK_HEADER_INITIALIZED[$task_idx]:-false}"
    
    TASK_STATUSES[$task_idx]="$status"
    if [[ -n "$elapsed_time" ]]; then
        TASK_ELAPSED_TIMES[$task_idx]="$elapsed_time"
    fi
    
    # Determine emoji and status text
    local emoji=""
    local status_text=""
    case "$status" in
        running)
            emoji="⏳"
            status_text="[running]"
            ;;
        success)
            emoji="✅"
            if [[ -n "${TASK_ELAPSED_TIMES[$task_idx]}" ]]; then
                status_text="[${TASK_ELAPSED_TIMES[$task_idx]}]"
            else
                status_text="[done]"
            fi
            ;;
        failure)
            emoji="❌"
            if [[ -n "${TASK_ELAPSED_TIMES[$task_idx]}" ]]; then
                status_text="[${TASK_ELAPSED_TIMES[$task_idx]}]"
            else
                status_text="[failed]"
            fi
            ;;
    esac
    
    local task_name="${TASK_NAMES[$task_idx]}"
    local cmd="${command:-${TASK_COMMANDS[$task_idx]}}"
    if [[ -n "$cmd" ]]; then
        TASK_COMMANDS[$task_idx]="$cmd"
    fi
    
    # If header not initialized, display it now (status + command lines)
    if [[ "$header_initialized" == "false" ]]; then
        # Display status line
        printf "%s %-40s %s" "$emoji" "$task_name" "$status_text"
        if command -v tput >/dev/null 2>&1; then
            tput el 2>/dev/null || printf "\033[K"
        else
            printf "\033[K"
        fi
        printf "\n"
        
        # Display command line
        if [[ -n "$cmd" ]]; then
            printf "${CYAN}    $ %s${RESET}" "$cmd"
            if command -v tput >/dev/null 2>&1; then
                tput el 2>/dev/null || printf "\033[K"
            else
                printf "\033[K"
            fi
            printf "\n"
        fi
        
        TASK_HEADER_INITIALIZED[$task_idx]="true"
    fi
    
    # Handle status change from running to success/failure
    if [[ "$previous_status" == "running" && "$status" != "running" ]]; then
        # Move up to status line (1 line for command if exists, or 0)
        local lines_to_status=1  # Always at least 1 (status line itself)
        if [[ -n "$cmd" ]]; then
            lines_to_status=2  # Status + command
        fi
        
        # Move to status line
        local i=0
        while [[ $i -lt $lines_to_status ]]; do
            if command -v tput >/dev/null 2>&1; then
                tput cuu1 2>/dev/null || printf "\033[1A"
            else
                printf "\033[1A"
            fi
            i=$((i + 1))
        done
        
        # Update status line
        printf "%s %-40s %s" "$emoji" "$task_name" "$status_text"
        if command -v tput >/dev/null 2>&1; then
            tput el 2>/dev/null || printf "\033[K"
        else
            printf "\033[K"
        fi
        printf "\n"
        
        # Move back down to after command line
        if [[ -n "$cmd" ]]; then
            printf "\n"
        fi
        return
    fi
}

# Cleanup UI on exit
cleanup_ui() {
    if [[ "$UI_INITIALIZED" == true ]]; then
        # Move cursor to end
        echo ""
        echo ""
    fi
}

# Run command silently in background
run_command_silently() {
    local command="$1"
    local task_name="$2"
    local task_idx="$3"
    
    local start_time
    local end_time
    local exit_code=0
    
    # Temporarily disable job control monitoring
    local job_control_state="disabled"
    if [[ -o monitor ]] 2>/dev/null; then
        job_control_state="enabled"
        set +m 2>/dev/null || true
    fi
    
    # Get start time
    if command -v gdate >/dev/null 2>&1; then
        start_time=$(gdate +%s.%N)
    else
        start_time=$(date +%s.%N 2>/dev/null || date +%s)
    fi
    
    # Execute command in background, redirecting all output to /dev/null
    eval "$command" > /dev/null 2>&1 &
    local cmd_pid=$!
    
    # Wait for command to finish
    wait "$cmd_pid" 2>/dev/null
    exit_code=$?
    
    # Restore job control state
    if [[ "$job_control_state" == "enabled" ]]; then
        set -m
    fi
    
    # Get end time and calculate elapsed
    if command -v gdate >/dev/null 2>&1; then
        end_time=$(gdate +%s.%N)
    else
        end_time=$(date +%s.%N 2>/dev/null || date +%s)
    fi
    
    # Calculate elapsed time
    local elapsed
    if command -v awk >/dev/null 2>&1; then
        elapsed=$(awk "BEGIN {printf \"%.2f\", $end_time - $start_time}")
    else
        local start_int=${start_time%.*}
        local end_int=${end_time%.*}
        elapsed=$((end_int - start_int))
    fi
    
    local formatted_time=$(format_elapsed_time "$elapsed")
    
    # Update final status
    if [[ $exit_code -eq 0 ]]; then
        update_task_status "$task_idx" "success" "$formatted_time" "$command"
    else
        update_task_status "$task_idx" "failure" "$formatted_time" "$command"
    fi
    
    return $exit_code
}

# Run frontend validations
run_frontend_validations() {
    local skip_coverage="${1:-false}"
    
    if [[ ! -d "$WEB_DIR" ]]; then
        log_error "Error: Frontend directory not found at $WEB_DIR"
        return 1
    fi
    
    cd "$WEB_DIR"
    
    # Check if pnpm is available
    if ! command -v pnpm &> /dev/null; then
        log_error "Error: pnpm is not installed or not in PATH"
        return 1
    fi
    
    # Typecheck
    add_new_task "web typecheck" "pnpm run typecheck"
    local task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "pnpm run typecheck" "web typecheck" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Tests
    add_new_task "web test" "pnpm run test"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "pnpm run test" "web test" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Lint
    add_new_task "web lint" "pnpm run lint"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "pnpm run lint" "web lint" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        add_new_task "web coverage" "pnpm run test:coverage"
        task_idx=$CURRENT_TASK_INDEX
        if ! run_command_silently "pnpm run test:coverage" "web coverage" "$task_idx"; then
            VALIDATION_FAILED=true
        fi
    fi
}

# Run frontend validations without interactive UI (used for parallel mode)
run_frontend_validations_simple() {
    local skip_coverage="${1:-false}"
    local first_failed_step=""
    local first_failed_cmd=""

    if [[ ! -d "$WEB_DIR" ]]; then
        log_error "Error: Frontend directory not found at $WEB_DIR"
        return 1
    fi

    cd "$WEB_DIR"

    if ! command -v pnpm &> /dev/null; then
        log_error "Error: pnpm is not installed or not in PATH"
        return 1
    fi

    local cmd=""
    echo "==> web: typecheck"
    cmd="pnpm run typecheck"
    $cmd || {
        first_failed_step="${first_failed_step:-web: typecheck}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    echo ""
    echo "==> web: test"
    cmd="pnpm run test"
    $cmd || {
        first_failed_step="${first_failed_step:-web: test}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    echo ""
    echo "==> web: lint"
    cmd="pnpm run lint"
    $cmd || {
        first_failed_step="${first_failed_step:-web: lint}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    if [[ "$skip_coverage" == "false" ]]; then
        echo ""
        echo "==> web: coverage"
        cmd="pnpm run test:coverage"
        $cmd || {
            first_failed_step="${first_failed_step:-web: coverage}"
            first_failed_cmd="${first_failed_cmd:-$cmd}"
        }
    fi

    if [[ -n "$first_failed_step" ]]; then
        echo ""
        echo "FAILED STEP: $first_failed_step"
        echo "FAILED COMMAND: $first_failed_cmd"
        return 1
    fi
    return 0
}

# Run backend validations
run_backend_validations() {
    local skip_coverage="${1:-false}"
    local service_name="${2:-}"
    
    if [[ ! -d "$BACKEND_DIR" ]]; then
        log_error "Error: Backend directory not found at $BACKEND_DIR"
        return 1
    fi
    
    cd "$BACKEND_DIR"
    
    # Check if gradlew exists and is executable
    if [[ ! -f "./gradlew" ]]; then
        log_error "Error: gradlew not found at $BACKEND_DIR/gradlew"
        return 1
    fi
    
    if [[ ! -x "./gradlew" ]]; then
        chmod +x ./gradlew
    fi
    
    # Build Gradle task prefix for specific service or all services
    local task_prefix=""
    local task_label_prefix=""
    if [[ -n "$service_name" ]]; then
        task_prefix=":${service_name}:"
        task_label_prefix="backend ${service_name} "
    else
        task_label_prefix="backend "
    fi
    
    # Compilation validation (classes - compiles main sources and catches type errors)
    add_new_task "${task_label_prefix}compile" "./gradlew ${task_prefix}classes --no-daemon"
    local task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "./gradlew ${task_prefix}classes --no-daemon" "${task_label_prefix}compile" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Tests (unit and integration - test task runs both)
    add_new_task "${task_label_prefix}test" "./gradlew ${task_prefix}test --no-daemon"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "./gradlew ${task_prefix}test --no-daemon" "${task_label_prefix}test" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Lint (ktlint)
    add_new_task "${task_label_prefix}lint" "./gradlew ${task_prefix}ktlintCheck --no-daemon"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "./gradlew ${task_prefix}ktlintCheck --no-daemon" "${task_label_prefix}lint" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        # For specific service, use service-specific kover tasks
        # For all services, use root kover tasks
        local coverage_cmd
        if [[ -n "$service_name" ]]; then
            coverage_cmd="./gradlew ${task_prefix}koverXmlReport ${task_prefix}koverVerify --no-daemon"
        else
            coverage_cmd="./gradlew koverRootReport koverVerify --no-daemon"
        fi
        add_new_task "${task_label_prefix}coverage" "$coverage_cmd"
        task_idx=$CURRENT_TASK_INDEX
        if ! run_command_silently "$coverage_cmd" "${task_label_prefix}coverage" "$task_idx"; then
            VALIDATION_FAILED=true
        fi
    fi
}

# Run backend validations without interactive UI (used for parallel mode)
run_backend_validations_simple() {
    local skip_coverage="${1:-false}"
    local service_name="${2:-}"
    local first_failed_step=""
    local first_failed_cmd=""

    if [[ ! -d "$BACKEND_DIR" ]]; then
        log_error "Error: Backend directory not found at $BACKEND_DIR"
        return 1
    fi

    cd "$BACKEND_DIR"

    if [[ ! -f "./gradlew" ]]; then
        log_error "Error: gradlew not found at $BACKEND_DIR/gradlew"
        return 1
    fi

    if [[ ! -x "./gradlew" ]]; then
        chmod +x ./gradlew
    fi

    local task_prefix=""
    if [[ -n "$service_name" ]]; then
        task_prefix=":${service_name}:"
    fi

    echo "==> service: compile (classes)"
    local cmd=""
    cmd="./gradlew ${task_prefix}classes --no-daemon --console=plain"
    eval "$cmd" || {
        first_failed_step="${first_failed_step:-service: compile (classes)}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    echo ""
    echo "==> service: test"
    cmd="./gradlew ${task_prefix}test --no-daemon --console=plain"
    eval "$cmd" || {
        first_failed_step="${first_failed_step:-service: test}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    echo ""
    echo "==> service: lint (ktlintCheck)"
    cmd="./gradlew ${task_prefix}ktlintCheck --no-daemon --console=plain"
    eval "$cmd" || {
        first_failed_step="${first_failed_step:-service: lint (ktlintCheck)}"
        first_failed_cmd="${first_failed_cmd:-$cmd}"
    }

    if [[ "$skip_coverage" == "false" ]]; then
        local coverage_cmd=""
        if [[ -n "$service_name" ]]; then
            coverage_cmd="./gradlew ${task_prefix}koverXmlReport ${task_prefix}koverVerify --no-daemon --console=plain"
        else
            coverage_cmd="./gradlew koverRootReport koverVerify --no-daemon --console=plain"
        fi
        echo ""
        echo "==> service: coverage"
        cmd="$coverage_cmd"
        eval "$cmd" || {
            first_failed_step="${first_failed_step:-service: coverage}"
            first_failed_cmd="${first_failed_cmd:-$cmd}"
        }
    fi

    if [[ -n "$first_failed_step" ]]; then
        echo ""
        echo "FAILED STEP: $first_failed_step"
        echo "FAILED COMMAND: $first_failed_cmd"
        return 1
    fi
    return 0
}

# Run security validation (same baseline as CI)
run_security_validation() {
    cd "$PROJECT_ROOT"

    if ! command -v trivy >/dev/null 2>&1; then
        log "⚠️  trivy command not found in PATH - skipping security scan" "$YELLOW"
        return 0
    fi

    local trivy_cmd="trivy fs --format table --severity CRITICAL,HIGH --exit-code 1 ."
    add_new_task "security scan (trivy)" "$trivy_cmd"
    local task_idx=$CURRENT_TASK_INDEX
    if ! run_command_silently "$trivy_cmd" "security scan (trivy)" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
}

# Run security validation without interactive UI (used for parallel mode)
run_security_validation_simple() {
    cd "$PROJECT_ROOT"

    if ! command -v trivy >/dev/null 2>&1; then
        return 0
    fi

    echo "==> security: trivy fs"
    local cmd="trivy fs --format table --severity CRITICAL,HIGH --exit-code 1 ."
    $cmd || {
        echo ""
        echo "FAILED STEP: security: trivy fs"
        echo "FAILED COMMAND: $cmd"
        return 1
    }
}

# Run full validation (web + backend + security scan) in parallel.
# Keeps sequential (UI) execution available via --no-parallel.
run_full_validations_parallel() {
    local skip_coverage="${1:-false}"

    log "\n🔍 Starting Validation Process (parallel)\n" "$BRIGHT"

    local web_log backend_log security_log
    web_log="$(mktemp -t neotool-validate-web.XXXXXX)"
    backend_log="$(mktemp -t neotool-validate-backend.XXXXXX)"
    security_log="$(mktemp -t neotool-validate-security.XXXXXX)"

    log "Logs:" "$GRAY"
    log "  web:      $web_log" "$GRAY"
    log "  service:  $backend_log" "$GRAY"
    log "  security: $security_log\n" "$GRAY"

    local web_pid backend_pid security_pid
    ( run_frontend_validations_simple "$skip_coverage" ) >"$web_log" 2>&1 & web_pid=$!
    ( run_backend_validations_simple "$skip_coverage" "" ) >"$backend_log" 2>&1 & backend_pid=$!
    ( run_security_validation_simple ) >"$security_log" 2>&1 & security_pid=$!

    local web_rc=0
    local backend_rc=0
    local security_rc=0

    wait "$web_pid" || web_rc=$?
    wait "$backend_pid" || backend_rc=$?
    wait "$security_pid" || security_rc=$?

    if [[ $web_rc -ne 0 ]]; then
        VALIDATION_FAILED=true
        log_error "❌ web validation failed"
        local summary
        summary=$(grep -E '^FAILED (STEP|COMMAND):' "$web_log" 2>/dev/null | tail -n 2 || true)
        if [[ -n "$summary" ]]; then
            log "$summary" "$YELLOW"
            echo ""
        fi
        log "Last output (web):" "$YELLOW"
        tail -n 50 "$web_log" 2>/dev/null || true
        echo ""
    else
        log "✅ web validation passed" "$GREEN"
    fi

    if [[ $backend_rc -ne 0 ]]; then
        VALIDATION_FAILED=true
        log_error "❌ service validation failed"
        local summary
        summary=$(grep -E '^FAILED (STEP|COMMAND):' "$backend_log" 2>/dev/null | tail -n 2 || true)
        if [[ -n "$summary" ]]; then
            log "$summary" "$YELLOW"
            echo ""
        fi
        log "Last output (service):" "$YELLOW"
        tail -n 200 "$backend_log" 2>/dev/null || true
        echo ""
        log "Tip: open full log at $backend_log" "$GRAY"
        echo ""
    else
        log "✅ service validation passed" "$GREEN"
    fi

    if [[ $security_rc -ne 0 ]]; then
        VALIDATION_FAILED=true
        log_error "❌ security scan failed"
        local summary
        summary=$(grep -E '^FAILED (STEP|COMMAND):' "$security_log" 2>/dev/null | tail -n 2 || true)
        if [[ -n "$summary" ]]; then
            log "$summary" "$YELLOW"
            echo ""
        fi
        log "Last output (security):" "$YELLOW"
        tail -n 50 "$security_log" 2>/dev/null || true
        echo ""
    else
        log "✅ security scan passed" "$GREEN"
    fi

    # Only cleanup logs if everything passed.
    # On failures, keep logs so users can inspect full output.
    if [[ "$VALIDATION_FAILED" != true ]]; then
        rm -f "$web_log" "$backend_log" "$security_log" 2>/dev/null || true
    fi
}

# Main function
main() {
    local web_only=false
    local service_only=false
    local service_name=""
    local skip_coverage=false
    local parallel=true
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --web)
                web_only=true
                shift
                ;;
            --service)
                service_only=true
                # Check if next argument is a service name (not another flag)
                if [[ $# -gt 1 && ! "$2" =~ ^-- ]]; then
                    service_name="$2"
                    shift 2
                else
                    shift
                fi
                ;;
            --skip-coverage)
                skip_coverage=true
                shift
                ;;
            --no-parallel)
                parallel=false
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                echo ""
                show_help
                exit 1
                ;;
        esac
    done
    
    # Validate mutually exclusive options
    if [[ "$web_only" == true && "$service_only" == true ]]; then
        log_error "Error: --web and --service cannot be used together"
        exit 1
    fi
    
    # Validate service name if provided
    if [[ -n "$service_name" ]]; then
        local available_services
        available_services=$(get_available_services)
        if [[ $? -ne 0 ]]; then
            log_error "Error: Failed to read available services from settings.gradle.kts"
            exit 1
        fi
        
        # Check if service_name is in the list of available services
        if ! echo "$available_services" | grep -qFx "$service_name"; then
            local services_list
            services_list=$(echo "$available_services" | tr '\n' ',' | sed 's/,$//' | sed 's/,/, /g')
            log_error "Error: Invalid service name: $service_name"
            log_error "Available services: $services_list"
            exit 1
        fi
    fi
    
    # Run validations
    if [[ "$web_only" == true ]]; then
        init_validation_ui
        trap cleanup_ui EXIT
        run_frontend_validations "$skip_coverage"
    elif [[ "$service_only" == true ]]; then
        init_validation_ui
        trap cleanup_ui EXIT
        run_backend_validations "$skip_coverage" "$service_name"
    else
        if [[ "$parallel" == true ]]; then
            run_full_validations_parallel "$skip_coverage"
        else
            # Run full validation set sequentially (frontend, backend, and security scan)
            init_validation_ui
            trap cleanup_ui EXIT
            run_frontend_validations "$skip_coverage"
            run_backend_validations "$skip_coverage" "$service_name"
            run_security_validation
        fi
    fi
    
    # Summary
    echo ""
    echo ""
    if [[ "$VALIDATION_FAILED" == true ]]; then
        log_error "❌ Validation failed - some checks did not pass"
        exit 1
    else
        log "✅ All validations passed!" "$GREEN"
        exit 0
    fi
}

# Run main function
main "$@"
