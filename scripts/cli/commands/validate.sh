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
  --frontend-only    Run only frontend validations
  --backend-only     Run only backend validations
  --skip-coverage    Skip coverage checks (faster validation)
  --help             Show this help message

Examples:
  $0                 # Run all validations
  $0 --frontend-only # Run only frontend validations
  $0 --backend-only  # Run only backend validations
  $0 --skip-coverage # Run validations without coverage checks
EOF
}

# Run frontend validations
run_frontend_validations() {
    local skip_coverage="${1:-false}"
    
    log "\n📦 Running Frontend Validations\n" "$BRIGHT"
    
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
    log "  ✓ Typecheck..." "$BLUE"
    if ! pnpm run typecheck; then
        log_error "  ✗ Typecheck failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Typecheck passed" "$GREEN"
    fi
    echo ""
    
    # Lint
    log "  ✓ Lint..." "$BLUE"
    if ! pnpm run lint; then
        log_error "  ✗ Lint failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Lint passed" "$GREEN"
    fi
    echo ""
    
    # Tests
    log "  ✓ Unit Tests..." "$BLUE"
    if ! pnpm run test; then
        log_error "  ✗ Unit tests failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Unit tests passed" "$GREEN"
    fi
    echo ""
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        log "  ✓ Coverage..." "$BLUE"
        if ! pnpm run test:coverage; then
            log_error "  ✗ Coverage check failed"
            VALIDATION_FAILED=true
        else
            log "  ✓ Coverage check passed" "$GREEN"
        fi
        echo ""
    fi
}

# Run backend validations
run_backend_validations() {
    local skip_coverage="${1:-false}"
    
    log "\n☕ Running Backend Validations\n" "$BRIGHT"
    
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
    
    # Lint (ktlint)
    log "  ✓ Lint (ktlint)..." "$BLUE"
    if ! ./gradlew ktlintCheck --no-daemon; then
        log_error "  ✗ Lint failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Lint passed" "$GREEN"
    fi
    echo ""
    
    # Typecheck (classes - compilation catches type errors)
    log "  ✓ Typecheck (compile classes)..." "$BLUE"
    if ! ./gradlew classes --no-daemon; then
        log_error "  ✗ Typecheck failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Typecheck passed" "$GREEN"
    fi
    echo ""
    
    # Unit Tests
    log "  ✓ Unit Tests..." "$BLUE"
    if ! ./gradlew test --no-daemon; then
        log_error "  ✗ Unit tests failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Unit tests passed" "$GREEN"
    fi
    echo ""
    
    # Integration Tests
    log "  ✓ Integration Tests..." "$BLUE"
    if ! ./gradlew testIntegration --no-daemon; then
        log_error "  ✗ Integration tests failed"
        VALIDATION_FAILED=true
    else
        log "  ✓ Integration tests passed" "$GREEN"
    fi
    echo ""
    
    # Coverage (if not skipped)
    if [[ "$skip_coverage" == "false" ]]; then
        log "  ✓ Coverage..." "$BLUE"
        if ! ./gradlew koverRootReport koverVerify --no-daemon; then
            log_error "  ✗ Coverage check failed"
            VALIDATION_FAILED=true
        else
            log "  ✓ Coverage check passed" "$GREEN"
        fi
        echo ""
    fi
}

# Main function
main() {
    local frontend_only=false
    local backend_only=false
    local skip_coverage=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --frontend-only)
                frontend_only=true
                shift
                ;;
            --backend-only)
                backend_only=true
                shift
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
    if [[ "$frontend_only" == true && "$backend_only" == true ]]; then
        log_error "Error: --frontend-only and --backend-only cannot be used together"
        exit 1
    fi
    
    log "\n🔍 Starting Validation Process\n" "$BRIGHT"
    
    # Run validations
    if [[ "$frontend_only" == true ]]; then
        run_frontend_validations "$skip_coverage"
    elif [[ "$backend_only" == true ]]; then
        run_backend_validations "$skip_coverage"
    else
        # Run both frontend and backend
        run_frontend_validations "$skip_coverage"
        run_backend_validations "$skip_coverage"
    fi
    
    # Summary
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

