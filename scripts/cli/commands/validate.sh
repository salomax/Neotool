#!/usr/bin/env bash

set -euo pipefail

# Validate Command
# 
# Runs all validations for both frontend and backend:
# - Lint (frontend: eslint, backend: ktlint)
# - Typecheck (frontend: tsc, backend: compile classes)
# - Tests (frontend: vitest, backend: test + testIntegration)
# - Coverage (frontend: vitest coverage, backend: kover)

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
declare -a TASK_LATEST_LINES=()
declare -a TASK_START_TIMES=()
declare -a TASK_ELAPSED_TIMES=()
declare -a TASK_COMMANDS=()
CURRENT_TASK_INDEX=-1
UI_INITIALIZED=false
INITIAL_CURSOR_LINE=0

# Show help text
show_help() {
    cat << EOF
$(log "Validation Command" "$BRIGHT")

Usage: $0 [options]

Runs all validations for both frontend and backend:
  - Lint (frontend: eslint, backend: ktlint)
  - Typecheck (frontend: tsc, backend: compile classes)
  - Tests (frontend: vitest, backend: test + testIntegration)
  - Coverage (frontend: vitest coverage, backend: kover)

Options:
  --web              Run only web (frontend) validations
  --service [name]   Run only service (backend) validations
                     If service name is provided, validate only that service
                     Available services: app, assistant, security, common
                     If no name provided, validate all services
  --skip-coverage    Skip coverage checks (faster validation)
  --help             Show this help message

Examples:
  $0                      # Run all validations
  $0 --web                # Run only web validations
  $0 --service             # Run all service validations
  $0 --service security    # Run only security service validations
  $0 --service app         # Run only app service validations
  $0 --skip-coverage       # Run validations without coverage checks
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
    
    # Save initial cursor position
    INITIAL_CURSOR_LINE=$(tput lines 2>/dev/null || echo "0")
    
    # Show header
    log "\n🔍 Starting Validation Process\n" "$BRIGHT"
    echo ""
    
    UI_INITIALIZED=true
    CURRENT_TASK_INDEX=-1
}

# Add a new task to the display
add_new_task() {
    local task_name="$1"
    
    CURRENT_TASK_INDEX=$((CURRENT_TASK_INDEX + 1))
    TASK_NAMES+=("$task_name")
    TASK_STATUSES+=("running")
    TASK_LATEST_LINES+=("")
    TASK_COMMANDS+=("")
    
    # Get start time
    if command -v gdate >/dev/null 2>&1; then
        TASK_START_TIMES+=("$(gdate +%s.%N)")
    else
        TASK_START_TIMES+=("$(date +%s.%N 2>/dev/null || date +%s)")
    fi
    
    TASK_ELAPSED_TIMES+=("")
    
    # Display the new task with running status
    update_task_status "$CURRENT_TASK_INDEX" "running" "" ""
}

# Update task status display
update_task_status() {
    local task_idx="$1"
    local status="$2"
    local latest_line="${3:-}"
    local elapsed_time="${4:-}"
    local command="${5:-}"
    
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
    
    # Store previous status before updating (needed to clear running line)
    local previous_status="${TASK_STATUSES[$task_idx]}"
    TASK_STATUSES[$task_idx]="$status"
    if [[ -n "$latest_line" ]]; then
        TASK_LATEST_LINES[$task_idx]="$latest_line"
    fi
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
    
    # Print task status (use carriage return to update in place for running tasks)
    local task_name="${TASK_NAMES[$task_idx]}"
    
    if [[ "$status" == "running" ]]; then
        # For running tasks, use carriage return to update in place
        # Clear to end of line to remove any previous content
        local term_width=$(tput cols 2>/dev/null || echo "80")
        printf "\r%s %-40s %s" "$emoji" "$task_name" "$status_text"
        # Clear rest of line
        printf "\033[K"
    else
        # For completed tasks, first clear the running line if it was previously running
        if [[ "$previous_status" == "running" ]]; then
            # Clear the running line by moving to start and clearing to end
            printf "\r\033[K"
        fi
        # Then print final status on a new line
        printf "\n%s %-40s %s\n" "$emoji" "$task_name" "$status_text"
        
        # Show the command if task is done
        if [[ -n "${TASK_COMMANDS[$task_idx]}" ]]; then
            local cmd="${TASK_COMMANDS[$task_idx]}"
            log "    $ $cmd" "$CYAN"
        fi
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

# Run command with live output capture
run_with_live_output() {
    local command="$1"
    local task_name="$2"
    local task_idx="$3"
    
    local start_time
    local end_time
    local latest_line=""
    local exit_code=0
    
    # Get start time (seconds since epoch with nanoseconds)
    if command -v gdate >/dev/null 2>&1; then
        start_time=$(gdate +%s.%N)
    else
        start_time=$(date +%s.%N 2>/dev/null || date +%s)
    fi
    
    # Use a temp file to capture output
    local output_file=$(mktemp)
    
    # Execute command in background, redirecting output to file
    eval "$command" > "$output_file" 2>&1 &
    local cmd_pid=$!
    
    # Monitor the output file for new lines using tail -f
    local tail_pid=""
    if command -v tail >/dev/null 2>&1; then
        # Wait a bit for file to be created
        sleep 0.1
        if [[ -f "$output_file" ]]; then
            tail -f "$output_file" 2>/dev/null | while IFS= read -r line || [[ -n "$line" ]]; do
                if [[ -n "$line" ]]; then
                    latest_line="$line"
                    update_task_status "$task_idx" "running" "$latest_line" ""
                fi
            done &
            tail_pid=$!
        fi
    fi
    
    # Wait for command to finish
    wait "$cmd_pid" 2>/dev/null
    exit_code=$?
    
    # Stop tail if it's running
    if [[ -n "$tail_pid" ]]; then
        kill "$tail_pid" 2>/dev/null || true
        # Give it a moment to finish
        sleep 0.1
        kill -9 "$tail_pid" 2>/dev/null || true
    fi
    
    # Get the latest line from output
    if [[ -f "$output_file" ]] && [[ -s "$output_file" ]]; then
        latest_line=$(tail -n 1 "$output_file" 2>/dev/null || echo "")
    fi
    
    # Get end time and calculate elapsed
    if command -v gdate >/dev/null 2>&1; then
        end_time=$(gdate +%s.%N)
    else
        end_time=$(date +%s.%N 2>/dev/null || date +%s)
    fi
    
    # Calculate elapsed time using awk for floating point arithmetic
    local elapsed
    if command -v awk >/dev/null 2>&1; then
        elapsed=$(awk "BEGIN {printf \"%.2f\", $end_time - $start_time}")
    else
        # Fallback: use integer seconds
        local start_int=${start_time%.*}
        local end_int=${end_time%.*}
        elapsed=$((end_int - start_int))
    fi
    
    local formatted_time=$(format_elapsed_time "$elapsed")
    
    # Update final status
    if [[ $exit_code -eq 0 ]]; then
        update_task_status "$task_idx" "success" "" "$formatted_time" "$command"
    else
        update_task_status "$task_idx" "failure" "${latest_line:-Command failed}" "$formatted_time" "$command"
    fi
    
    # Cleanup
    rm -f "$output_file"
    
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
    add_new_task "web typecheck"
    local task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "pnpm run typecheck" "web typecheck" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Lint
    add_new_task "web lint"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "pnpm run lint" "web lint" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Tests
    add_new_task "web test"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "pnpm run test" "web test" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        add_new_task "web coverage"
        task_idx=$CURRENT_TASK_INDEX
        if ! run_with_live_output "pnpm run test:coverage" "web coverage" "$task_idx"; then
            VALIDATION_FAILED=true
        fi
    fi
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
    
    # Lint (ktlint)
    add_new_task "${task_label_prefix}lint"
    local task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "./gradlew ${task_prefix}ktlintCheck --no-daemon" "${task_label_prefix}lint" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Typecheck (classes - compilation catches type errors)
    add_new_task "${task_label_prefix}typecheck"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "./gradlew ${task_prefix}classes --no-daemon" "${task_label_prefix}typecheck" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Unit Tests
    add_new_task "${task_label_prefix}test"
    task_idx=$CURRENT_TASK_INDEX
    if ! run_with_live_output "./gradlew ${task_prefix}test --no-daemon" "${task_label_prefix}test" "$task_idx"; then
        VALIDATION_FAILED=true
    fi
    
    # Integration Tests (skip for common module as it doesn't have testIntegration)
    if [[ "$service_name" == "common" ]]; then
        # Skip testIntegration for common module
        :
    else
        add_new_task "${task_label_prefix}testIntegration"
        task_idx=$CURRENT_TASK_INDEX
        # When validating all services, exclude common module
        local test_integration_cmd="./gradlew ${task_prefix}testIntegration --no-daemon"
        if [[ -z "$service_name" ]]; then
            test_integration_cmd="./gradlew testIntegration -x :common:testIntegration --no-daemon"
        fi
        if ! run_with_live_output "$test_integration_cmd" "${task_label_prefix}testIntegration" "$task_idx"; then
            VALIDATION_FAILED=true
        fi
    fi
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        add_new_task "${task_label_prefix}coverage"
        task_idx=$CURRENT_TASK_INDEX
        # For specific service, use service-specific kover tasks
        # For all services, use root kover tasks
        local coverage_cmd
        if [[ -n "$service_name" ]]; then
            coverage_cmd="./gradlew ${task_prefix}koverXmlReport ${task_prefix}koverVerify --no-daemon"
        else
            coverage_cmd="./gradlew koverRootReport koverVerify --no-daemon"
        fi
        if ! run_with_live_output "$coverage_cmd" "${task_label_prefix}coverage" "$task_idx"; then
            VALIDATION_FAILED=true
        fi
    fi
}

# Main function
main() {
    local web_only=false
    local service_only=false
    local service_name=""
    local skip_coverage=false
    
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
        case "$service_name" in
            app|assistant|security|common)
                # Valid service name
                ;;
            *)
                log_error "Error: Invalid service name: $service_name"
                log_error "Available services: app, assistant, security, common"
                exit 1
                ;;
        esac
    fi
    
    # Initialize UI
    init_validation_ui
    
    # Set up cleanup trap
    trap cleanup_ui EXIT
    
    # Run validations
    if [[ "$web_only" == true ]]; then
        run_frontend_validations "$skip_coverage"
    elif [[ "$service_only" == true ]]; then
        run_backend_validations "$skip_coverage" "$service_name"
    else
        # Run both frontend and backend
        run_frontend_validations "$skip_coverage"
        run_backend_validations "$skip_coverage" "$service_name"
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

