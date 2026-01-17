#!/usr/bin/env bash

set -euo pipefail

# Flags Command
# 
# Manages Unleash feature flags

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_UNLEASH_URL="http://unleash:4242"
DEFAULT_ENVIRONMENT="development"
UNLEASH_PROJECT="default"

# Show help text
show_help() {
    cat << EOF
$(log "Unleash Feature Flags Management" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "import" "$GREEN") <file>
    Import flags from a YAML file.
    
    Options:
      --url <url>      Unleash server URL (default: http://unleash:4242)
      --token <token>  API token (default: reads from UNLEASH_SERVER_API_TOKEN env var)
      --dry-run        Show what would be created without making changes
      --force          Update existing flags instead of skipping them
      --quiet, -q      Minimal output

  $(log "list" "$GREEN")
    List all feature flags.
    
    Options:
      --url <url>      Unleash server URL (default: http://unleash:4242)
      --token <token>  API token (default: reads from UNLEASH_SERVER_API_TOKEN env var)
      --format <fmt>   Output format: table, json, yaml (default: table)
      --quiet, -q      Minimal output

  $(log "enable" "$GREEN") <flag-name>
    Enable a feature flag in the specified environment.
    
    Options:
      --url <url>      Unleash server URL (default: http://unleash:4242)
      --token <token>  API token (default: reads from UNLEASH_SERVER_API_TOKEN env var)
      --env <env>      Environment to enable in (default: development)
      --yes, -y        Skip confirmation prompt
      --quiet, -q      Minimal output

  $(log "disable" "$GREEN") <flag-name>
    Disable a feature flag in the specified environment.
    
    Options:
      --url <url>      Unleash server URL (default: http://unleash:4242)
      --token <token>   API token (default: reads from UNLEASH_SERVER_API_TOKEN env var)
      --env <env>       Environment to disable in (default: development)
      --yes, -y         Skip confirmation prompt
      --quiet, -q       Minimal output

Environment variables:
  UNLEASH_SERVER_API_TOKEN    Unleash API token (used if --token not provided)

Examples:
  $0 list
  $0 list --url https://unleash.example.com --format json
  $0 import infra/unleash/flags.yaml
  $0 import infra/unleash/flags.yaml --url https://unleash.example.com
  $0 import infra/unleash/flags.yaml --dry-run
  $0 enable security/login/enabled
  $0 enable assistant/enable --url https://unleash.example.com --env production
  $0 disable security/login/enabled --env staging
EOF
}

# Get Unleash URL (with fallback to localhost if unleash:4242 not accessible)
get_unleash_url() {
    local url="${1:-}"
    
    if [[ -n "$url" ]]; then
        echo "$url"
        return
    fi
    
    # Try Docker network URL first, fallback to localhost
    if curl -s --max-time 2 "${DEFAULT_UNLEASH_URL}/health" >/dev/null 2>&1; then
        echo "$DEFAULT_UNLEASH_URL"
    else
        echo "http://localhost:4242"
    fi
}

# Get Unleash token from option or environment variable
get_unleash_token() {
    local token="${1:-}"
    
    if [[ -n "$token" ]]; then
        echo "$token"
        return
    fi
    
    if [[ -n "${UNLEASH_SERVER_API_TOKEN:-}" ]]; then
        echo "$UNLEASH_SERVER_API_TOKEN"
        return
    fi
    
    log_error "Error: API token is required"
    log_error "Provide token via --token option or UNLEASH_SERVER_API_TOKEN environment variable"
    exit 1
}

# Check if Unleash is accessible
check_unleash_connection() {
    local url="$1"
    local token="$2"
    local quiet="${3:-false}"
    
    if [[ "$quiet" != "true" ]]; then
        log "Checking Unleash connection...\n" "$BLUE"
    fi
    
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        --max-time 5 \
        --header "Authorization: ${token}" \
        "${url}/api/admin/health" 2>/dev/null || echo "000")
    
    if [[ "$http_code" == "200" ]]; then
        if [[ "$quiet" != "true" ]]; then
            log "Unleash connection successful\n" "$GREEN"
        fi
        return 0
    elif [[ "$http_code" == "000" ]]; then
        log_error "Error: Cannot connect to Unleash at ${url}"
        log_error "Make sure Unleash is running and accessible"
        return 1
    elif [[ "$http_code" == "401" ]] || [[ "$http_code" == "403" ]]; then
        log_error "Error: Authentication failed (HTTP ${http_code})"
        log_error "Check your API token is correct"
        return 1
    else
        log_error "Error: Unexpected response from Unleash (HTTP ${http_code})"
        return 1
    fi
}

# Prompt for confirmation on production operations
confirm_production() {
    local url="$1"
    local yes_flag="${2:-false}"
    
    if [[ "$yes_flag" == "true" ]]; then
        return 0
    fi
    
    if echo "$url" | grep -qiE "prod|production"; then
        log "⚠️  Warning: This operation targets a PRODUCTION environment\n" "$YELLOW"
        echo -n "Are you sure you want to continue? (yes/no): "
        read -r confirmation
        if [[ ! "$confirmation" =~ ^[Yy][Ee][Ss]$ ]]; then
            log "Operation cancelled\n" "$YELLOW"
            exit 0
        fi
    fi
}

# Make Unleash API call
# Returns: response body on stdout, HTTP code via global variable UNLEASH_HTTP_CODE
unleash_api_call() {
    local method="$1"
    local url="$2"
    local token="$3"
    local endpoint="$4"
    local data="${5:-}"
    local quiet="${6:-false}"
    
    local full_url="${url}${endpoint}"
    local http_code
    local response
    local temp_file
    local curl_stderr
    
    temp_file=$(mktemp)
    
    if [[ -n "$data" ]]; then
        response=$(curl -s -w "\n%{http_code}" \
            --request "$method" \
            --header "Authorization: ${token}" \
            --header "Content-Type: application/json" \
            --data "$data" \
            "${full_url}" 2>"$temp_file" || echo -e "\n000")
    else
        response=$(curl -s -w "\n%{http_code}" \
            --request "$method" \
            --header "Authorization: ${token}" \
            "${full_url}" 2>"$temp_file" || echo -e "\n000")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    local response_body
    response_body=$(echo "$response" | head -n -1)
    curl_stderr=$(cat "$temp_file" 2>/dev/null || true)
    rm -f "$temp_file"
    
    # Set global variable for HTTP code
    UNLEASH_HTTP_CODE="$http_code"
    
    if [[ "$http_code" == "000" ]]; then
        if [[ "$quiet" != "true" ]]; then
            log_error "Error: Could not connect to Unleash at ${url}"
            if [[ -n "$curl_stderr" ]]; then
                log_error "Details: $curl_stderr"
            fi
        fi
        return 1
    fi
    
    # Output response body
    echo "$response_body"
    return 0
}

# Execute list command
execute_list() {
    local url=""
    local token=""
    local format="table"
    local quiet=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --url requires a value"
                    exit 1
                fi
                url="$2"
                shift 2
                ;;
            --token)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --token requires a value"
                    exit 1
                fi
                token="$2"
                shift 2
                ;;
            --format)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --format requires a value"
                    exit 1
                fi
                format="$2"
                if [[ ! "$format" =~ ^(table|json|yaml)$ ]]; then
                    log_error "Error: --format must be one of: table, json, yaml"
                    exit 1
                fi
                shift 2
                ;;
            --quiet|-q)
                quiet=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Error: Unknown option: $1"
                echo ""
                show_help
                exit 1
                ;;
        esac
    done
    
    url=$(get_unleash_url "$url")
    token=$(get_unleash_token "$token")
    
    if ! check_unleash_connection "$url" "$token" "$quiet"; then
        exit 1
    fi
    
    local api_response
    api_response=$(unleash_api_call "GET" "$url" "$token" "/api/admin/projects/${UNLEASH_PROJECT}/features" "" "$quiet")
    local http_code="$UNLEASH_HTTP_CODE"
    
    if [[ "$http_code" != "200" ]]; then
        log_error "Error: Failed to fetch flags (HTTP ${http_code:-unknown})"
        if [[ -n "$api_response" ]]; then
            log_error "Response: $api_response"
        fi
        exit 1
    fi
    
    if [[ "$format" == "json" ]]; then
        if command -v jq >/dev/null 2>&1; then
            echo "$api_response" | jq '.'
        else
            echo "$api_response"
        fi
    elif [[ "$format" == "yaml" ]]; then
        if command -v yq >/dev/null 2>&1; then
            echo "$api_response" | jq '.' | yq -P
        else
            log_error "Error: yq is required for YAML output format"
            log_error "Install yq: https://github.com/mikefarah/yq"
            exit 1
        fi
    else
        # Table format
        if command -v jq >/dev/null 2>&1; then
            local flags_count
            flags_count=$(echo "$api_response" | jq '.features | length' 2>/dev/null || echo "0")
            
            if [[ "$flags_count" == "0" ]]; then
                log "No feature flags found\n" "$YELLOW"
                exit 0
            fi
            
            echo ""
            printf "%-40s %-15s %-10s %s\n" "NAME" "TYPE" "ENABLED" "DESCRIPTION"
            printf "%-40s %-15s %-10s %s\n" "$(printf '%.0s-' {1..40})" "$(printf '%.0s-' {1..15})" "$(printf '%.0s-' {1..10})" "$(printf '%.0s-' {1..50})"
            
            echo "$api_response" | jq -r '.features[] | "\(.name)|\(.type)|\(.environments[0].enabled // false)|\(.description // "")"' 2>/dev/null | while IFS='|' read -r name type enabled desc; do
                local enabled_str
                if [[ "$enabled" == "true" ]]; then
                    enabled_str=$(log "enabled" "$GREEN")
                else
                    enabled_str=$(log "disabled" "$RED")
                fi
                printf "%-40s %-15s %-10s %s\n" "$name" "$type" "$enabled_str" "${desc:0:50}"
            done
            echo ""
        else
            log_error "Error: jq is required for table output format"
            log_error "Install jq: https://stedolan.github.io/jq/"
            exit 1
        fi
    fi
}

# Execute enable command
execute_enable() {
    local flag_name=""
    local url=""
    local token=""
    local environment="$DEFAULT_ENVIRONMENT"
    local yes_flag=false
    local quiet=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --url requires a value"
                    exit 1
                fi
                url="$2"
                shift 2
                ;;
            --token)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --token requires a value"
                    exit 1
                fi
                token="$2"
                shift 2
                ;;
            --env)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --env requires a value"
                    exit 1
                fi
                environment="$2"
                shift 2
                ;;
            --yes|-y)
                yes_flag=true
                shift
                ;;
            --quiet|-q)
                quiet=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                if [[ -z "$flag_name" ]]; then
                    flag_name="$1"
                else
                    log_error "Error: Unknown option: $1"
                    echo ""
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    if [[ -z "$flag_name" ]]; then
        log_error "Error: Flag name is required"
        echo ""
        show_help
        exit 1
    fi
    
    url=$(get_unleash_url "$url")
    token=$(get_unleash_token "$token")
    
    confirm_production "$url" "$yes_flag"
    
    if ! check_unleash_connection "$url" "$token" "$quiet"; then
        exit 1
    fi
    
    local endpoint="/api/admin/projects/${UNLEASH_PROJECT}/features/${flag_name}/environments/${environment}/on"
    local api_response
    local http_code
    
    if [[ "$quiet" != "true" ]]; then
        log "Enabling flag '${flag_name}' in environment '${environment}'...\n" "$BLUE"
    fi
    
    api_response=$(unleash_api_call "POST" "$url" "$token" "$endpoint" "" "$quiet")
    http_code="$UNLEASH_HTTP_CODE"
    
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "204" ]]; then
        if [[ "$quiet" != "true" ]]; then
            log "✅ Flag '${flag_name}' enabled in '${environment}'\n" "$GREEN"
        fi
    elif [[ "$http_code" == "404" ]]; then
        log_error "Error: Flag '${flag_name}' not found"
        exit 1
    else
        log_error "Error: Failed to enable flag (HTTP ${http_code:-unknown})"
        if [[ -n "$api_response" ]]; then
            log_error "Response: $api_response"
        fi
        exit 1
    fi
}

# Execute disable command
execute_disable() {
    local flag_name=""
    local url=""
    local token=""
    local environment="$DEFAULT_ENVIRONMENT"
    local yes_flag=false
    local quiet=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --url requires a value"
                    exit 1
                fi
                url="$2"
                shift 2
                ;;
            --token)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --token requires a value"
                    exit 1
                fi
                token="$2"
                shift 2
                ;;
            --env)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --env requires a value"
                    exit 1
                fi
                environment="$2"
                shift 2
                ;;
            --yes|-y)
                yes_flag=true
                shift
                ;;
            --quiet|-q)
                quiet=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                if [[ -z "$flag_name" ]]; then
                    flag_name="$1"
                else
                    log_error "Error: Unknown option: $1"
                    echo ""
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    if [[ -z "$flag_name" ]]; then
        log_error "Error: Flag name is required"
        echo ""
        show_help
        exit 1
    fi
    
    url=$(get_unleash_url "$url")
    token=$(get_unleash_token "$token")
    
    confirm_production "$url" "$yes_flag"
    
    if ! check_unleash_connection "$url" "$token" "$quiet"; then
        exit 1
    fi
    
    local endpoint="/api/admin/projects/${UNLEASH_PROJECT}/features/${flag_name}/environments/${environment}/off"
    local api_response
    local http_code
    
    if [[ "$quiet" != "true" ]]; then
        log "Disabling flag '${flag_name}' in environment '${environment}'...\n" "$BLUE"
    fi
    
    api_response=$(unleash_api_call "POST" "$url" "$token" "$endpoint" "" "$quiet")
    http_code="$UNLEASH_HTTP_CODE"
    
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "204" ]]; then
        if [[ "$quiet" != "true" ]]; then
            log "✅ Flag '${flag_name}' disabled in '${environment}'\n" "$GREEN"
        fi
    elif [[ "$http_code" == "404" ]]; then
        log_error "Error: Flag '${flag_name}' not found"
        exit 1
    else
        log_error "Error: Failed to disable flag (HTTP ${http_code:-unknown})"
        if [[ -n "$api_response" ]]; then
            log_error "Response: $api_response"
        fi
        exit 1
    fi
}

# Execute import command
execute_import() {
    local file=""
    local url=""
    local token=""
    local dry_run=false
    local force=false
    local quiet=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --url requires a value"
                    exit 1
                fi
                url="$2"
                shift 2
                ;;
            --token)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --token requires a value"
                    exit 1
                fi
                token="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --force)
                force=true
                shift
                ;;
            --quiet|-q)
                quiet=true
                shift
                ;;
            --help|-h)
                show_help
                exit 1
                ;;
            *)
                if [[ -z "$file" ]]; then
                    file="$1"
                else
                    log_error "Error: Unknown option: $1"
                    echo ""
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    if [[ -z "$file" ]]; then
        log_error "Error: YAML file path is required"
        echo ""
        show_help
        exit 1
    fi
    
    # Resolve file path (relative to project root or absolute)
    if [[ ! "$file" =~ ^/ ]]; then
        file="${PROJECT_ROOT}/${file}"
    fi
    
    if [[ ! -f "$file" ]]; then
        log_error "Error: File not found: ${file}"
        exit 1
    fi
    
    # Check for yq
    if ! command -v yq >/dev/null 2>&1; then
        log_error "Error: yq is required for YAML parsing"
        log_error "Install yq: https://github.com/mikefarah/yq"
        exit 1
    fi
    
    # Validate YAML structure
    if ! yq eval '.flags' "$file" >/dev/null 2>&1; then
        log_error "Error: Invalid YAML structure. Expected 'flags' array at root level"
        exit 1
    fi
    
    local flags_count
    flags_count=$(yq eval '.flags | length' "$file" 2>/dev/null || echo "0")
    
    if [[ "$flags_count" == "0" ]]; then
        log_error "Error: No flags found in YAML file"
        exit 1
    fi
    
    url=$(get_unleash_url "$url")
    token=$(get_unleash_token "$token")
    
    if [[ "$dry_run" != "true" ]]; then
        if ! check_unleash_connection "$url" "$token" "$quiet"; then
            exit 1
        fi
    fi
    
    if [[ "$dry_run" == "true" ]]; then
        log "🔍 Dry run mode: No changes will be made\n" "$YELLOW"
    fi
    
    local created=0
    local skipped=0
    local updated=0
    local failed=0
    local failed_flags=()
    
    # Process each flag
    local i=0
    while [[ $i -lt $flags_count ]]; do
        local flag_name
        local flag_description
        local flag_type
        local flag_enabled
        
        flag_name=$(yq eval ".flags[$i].name" "$file" 2>/dev/null || echo "")
        flag_description=$(yq eval ".flags[$i].description // \"\"" "$file" 2>/dev/null || echo "")
        flag_type=$(yq eval ".flags[$i].type // \"release\"" "$file" 2>/dev/null || echo "release")
        flag_enabled=$(yq eval ".flags[$i].enabled // false" "$file" 2>/dev/null || echo "false")
        
        if [[ -z "$flag_name" ]]; then
            log_error "Error: Flag at index $i is missing required 'name' field"
            ((failed++))
            failed_flags+=("index $i (missing name)")
            ((i++))
            continue
        fi
        
        if [[ "$quiet" != "true" ]]; then
            log "Processing: ${flag_name}...\n" "$BLUE"
        fi
        
        if [[ "$dry_run" == "true" ]]; then
            log "  Would create flag: ${flag_name} (type: ${flag_type}, enabled: ${flag_enabled})\n" "$GRAY"
            ((created++))
        else
            # Check if flag exists
            local check_response
            check_response=$(unleash_api_call "GET" "$url" "$token" "/api/admin/projects/${UNLEASH_PROJECT}/features/${flag_name}" "" "$quiet")
            local check_http_code="$UNLEASH_HTTP_CODE"
            
            if [[ "$check_http_code" == "200" ]]; then
                # Flag exists
                if [[ "$force" == "true" ]]; then
                    # Update flag (Unleash doesn't have a direct update endpoint for basic properties,
                    # so we'll skip for now and just enable/disable if needed)
                    if [[ "$quiet" != "true" ]]; then
                        log "  Flag exists, skipping update (update not yet implemented)\n" "$YELLOW"
                    fi
                    ((skipped++))
                else
                    if [[ "$quiet" != "true" ]]; then
                        log "  Flag already exists, skipping (use --force to update)\n" "$YELLOW"
                    fi
                    ((skipped++))
                fi
            else
                # Create new flag
                local create_data
                if command -v jq >/dev/null 2>&1; then
                    create_data=$(jq -n \
                        --arg name "$flag_name" \
                        --arg desc "$flag_description" \
                        --arg type "$flag_type" \
                        '{name: $name, description: $desc, type: $type, impressionData: false}')
                else
                    # Fallback JSON creation
                    create_data="{\"name\":\"${flag_name}\",\"description\":\"${flag_description}\",\"type\":\"${flag_type}\",\"impressionData\":false}"
                fi
                
                local create_response
                create_response=$(unleash_api_call "POST" "$url" "$token" "/api/admin/projects/${UNLEASH_PROJECT}/features" "$create_data" "$quiet")
                local create_http_code="$UNLEASH_HTTP_CODE"
                
                if [[ "$create_http_code" == "200" ]] || [[ "$create_http_code" == "201" ]]; then
                    # Enable/disable in default environment if needed
                    if [[ "$flag_enabled" == "true" ]]; then
                        local enable_endpoint="/api/admin/projects/${UNLEASH_PROJECT}/features/${flag_name}/environments/${DEFAULT_ENVIRONMENT}/on"
                        unleash_api_call "POST" "$url" "$token" "$enable_endpoint" "" "$quiet" >/dev/null || true
                    fi
                    
                    if [[ "$quiet" != "true" ]]; then
                        log "  ✅ Created flag: ${flag_name}\n" "$GREEN"
                    fi
                    ((created++))
                else
                    log_error "  ❌ Failed to create flag: ${flag_name} (HTTP ${create_http_code:-unknown})"
                    if [[ -n "$create_response" ]]; then
                        log_error "  Response: $create_response"
                    fi
                    ((failed++))
                    failed_flags+=("$flag_name")
                fi
            fi
        fi
        
        ((i++))
    done
    
    # Summary
    echo ""
    if [[ "$dry_run" == "true" ]]; then
        log "Dry run complete:\n" "$BRIGHT"
        log "  Would create: ${created} flags\n" "$GRAY"
    else
        log "Import complete:\n" "$BRIGHT"
        log "  Created: ${created} flags\n" "$GREEN"
        if [[ $skipped -gt 0 ]]; then
            log "  Skipped: ${skipped} flags (already exist)\n" "$YELLOW"
        fi
        if [[ $updated -gt 0 ]]; then
            log "  Updated: ${updated} flags\n" "$BLUE"
        fi
        if [[ $failed -gt 0 ]]; then
            log "  Failed: ${failed} flags\n" "$RED"
            for failed_flag in "${failed_flags[@]}"; do
                log "    - ${failed_flag}\n" "$RED"
            done
        fi
    fi
    
    if [[ $failed -gt 0 ]]; then
        exit 1
    fi
}

# Main function
main() {
    # Check for help flag first
    if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
        exit 0
    fi
    
    local subcommand="${1:-}"
    shift 2>/dev/null || true
    
    case "$subcommand" in
        import)
            execute_import "$@"
            ;;
        list)
            execute_list "$@"
            ;;
        enable)
            execute_enable "$@"
            ;;
        disable)
            execute_disable "$@"
            ;;
        *)
            log_error "Unknown subcommand: $subcommand"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function
main "$@"
