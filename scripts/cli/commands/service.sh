#!/usr/bin/env bash

set -euo pipefail

# Service Registration Command
# 
# Manages service registration with Security Service

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_SECURITY_SERVICE_URL="http://localhost:8080"
DEFAULT_ADMIN_TOKEN="${ADMIN_TOKEN:-}"

SECURITY_SERVICE_URL="${SECURITY_SERVICE_URL:-$DEFAULT_SECURITY_SERVICE_URL}"

# Show help text
show_help() {
    cat << EOF
$(log "Service Registration" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "register" "$GREEN") <service-id>
    Register a new service with the Security Service.
    Generates a client secret and creates a service principal with permissions.
    
    Options:
      --service-id <id>          Service identifier (required)
      --permissions <list>       Comma-separated list of permissions (default: empty)
      --security-url <url>       Security Service URL (default: http://localhost:8081)
      --admin-token <token>      Admin token for authentication (default: from ADMIN_TOKEN env)
      --output-env               Output environment variables for .env.local
      
    Note: Client secret is auto-generated server-side for security.

Environment variables:
  SECURITY_SERVICE_URL           Security Service URL
  ADMIN_TOKEN                    Admin authentication token
  SERVICE_X_CLIENT_SECRET        Client secret (if not generating new one)

Examples:
  $0 register service-x
  $0 register service-x --permissions "asset:read,asset:list"
  $0 register service-x --client-secret "my-secret" --output-env
  $0 register service-x --security-url http://security-service:8081
  $0 register service-x --force
EOF
}

# Check if Security Service is accessible
check_security_service() {
    local security_url="$1"
    
    log "Checking Security Service connection...\n" "$BLUE"
    
    if ! curl -f -s "${security_url}/health" > /dev/null 2>&1; then
        log_error "Error: Cannot connect to Security Service at ${security_url}"
        log_error "Make sure Security Service is running:"
        log_error "  cd service/kotlin/security && ./gradlew run"
        return 1
    fi
    
    log "Security Service is ready\n" "$GREEN"
    return 0
}

# Generate a random client secret
generate_client_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 32 | tr -d '\n'
    elif command -v shuf >/dev/null 2>&1; then
        # Fallback: use /dev/urandom
        cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1
    else
        log_error "Error: Neither openssl nor shuf is available"
        log_error "Please install openssl to generate secure secrets"
        return 1
    fi
}

# Register service with Security Service
register_service() {
    local service_id="$1"
    local permissions="$2"
    local security_url="$3"
    local admin_token="$4"
    local output_env="$5"
    
    # Validate service ID
    if [[ -z "$service_id" ]]; then
        log_error "Error: Service ID is required"
        echo ""
        show_help
        exit 1
    fi
    
    # Validate service ID format (alphanumeric, dash, underscore)
    if [[ ! "$service_id" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        log_error "Error: Invalid service ID '${service_id}'"
        log_error "Service ID must contain only alphanumeric characters, dashes, and underscores"
        exit 1
    fi
    
    # Check Security Service connection
    if ! check_security_service "$security_url"; then
        exit 1
    fi
    
    # Client secret is now auto-generated server-side
    # The secret will be returned in the response
    
    # Parse permissions into JSON array
    local permissions_json="[]"
    if [[ -n "$permissions" ]]; then
        # Convert comma-separated list to JSON array
        local IFS=','
        read -ra PERM_ARRAY <<< "$permissions"
        permissions_json="["
        local first=true
        for perm in "${PERM_ARRAY[@]}"; do
            if [[ "$first" == "true" ]]; then
                first=false
            else
                permissions_json+=","
            fi
            permissions_json+="\"${perm// /}\""
        done
        permissions_json+="]"
    fi
    
    # Build JSON payload
    local temp_json
    temp_json=$(mktemp)
    
    if command -v jq >/dev/null 2>&1; then
        # Use jq for proper JSON encoding
        # Note: clientSecret is now auto-generated server-side
        jq -n \
            --arg service_id "$service_id" \
            --argjson permissions "$permissions_json" \
            '{serviceId: $service_id, permissions: $permissions}' > "$temp_json"
    else
        # Manual JSON creation (fallback)
        # Note: clientSecret is now auto-generated server-side
        cat > "$temp_json" << EOF
{
  "serviceId": "${service_id}",
  "permissions": ${permissions_json}
}
EOF
    fi
    
    # Make registration request
    log "Registering service '${service_id}'...\n" "$BLUE"
    
    local http_code
    local response
    local curl_opts=(
        -X POST
        -H "Content-Type: application/json"
        -w "\n%{http_code}"
        -s
        --data @"${temp_json}"
    )
    
    # Add admin token if provided
    if [[ -n "$admin_token" ]]; then
        curl_opts+=(-H "Authorization: Bearer ${admin_token}")
    fi
    
    response=$(curl "${curl_opts[@]}" "${security_url}/api/internal/services/register" 2>&1)
    http_code=$(echo "$response" | tail -n1)
    local response_body
    response_body=$(echo "$response" | sed '$d')
    
    rm -f "$temp_json"
    
    # Handle response
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "201" ]]; then
        log "Service '${service_id}' registered successfully!\n" "$GREEN"
        echo ""
        # Extract client secret from response (auto-generated server-side)
        local response_client_secret=""
        if command -v jq >/dev/null 2>&1; then
            response_client_secret=$(echo "$response_body" | jq -r '.clientSecret // empty')
        else
            # Fallback: try to extract from JSON manually
            response_client_secret=$(echo "$response_body" | grep -o '"clientSecret"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        fi
        
        log "Service ID: ${service_id}" "$BRIGHT"
        if [[ -n "$response_client_secret" ]]; then
            log "Client Secret: ${response_client_secret}" "$BRIGHT"
        fi
        
        if [[ -n "$permissions" ]]; then
            log "Permissions: ${permissions}" "$BRIGHT"
        fi
        
        if [[ "$output_env" == "true" ]] && [[ -n "$response_client_secret" ]]; then
            echo ""
            log "Add to your .env.local file:" "$BLUE"
            echo ""
            echo "# Service X credentials"
            echo "SERVICE_${service_id^^}_SERVICE_ID=${service_id}"
            echo "SERVICE_${service_id^^}_CLIENT_SECRET=${response_client_secret}"
            echo "SECURITY_SERVICE_URL=${security_url}"
            echo ""
        elif [[ -n "$response_client_secret" ]]; then
            echo ""
            log "⚠️  Save this client secret! Add to your .env.local:" "$YELLOW"
            log "  SERVICE_${service_id^^}_CLIENT_SECRET=${response_client_secret}" "$GRAY"
            echo ""
        fi
        
        return 0
    elif [[ "$http_code" == "409" ]]; then
        log "Service '${service_id}' already registered\n" "$YELLOW"
        log "Use rotateServiceCredentials() or updateServicePermissions() to modify existing service" "$GRAY"
        return 1
    elif [[ "$http_code" == "401" ]] || [[ "$http_code" == "403" ]]; then
        log_error "Error: Authentication failed (HTTP ${http_code})"
        log_error "Provide admin token via --admin-token or ADMIN_TOKEN environment variable"
        return 1
    else
        log_error "Error: Registration failed (HTTP ${http_code})"
        if [[ -n "$response_body" ]]; then
            log_error "Response: $response_body"
        fi
        return 1
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
        register)
            local service_id=""
            local permissions=""
            local security_url="$SECURITY_SERVICE_URL"
            local admin_token="${ADMIN_TOKEN:-}"
            local output_env=false
            
            # Parse arguments
            while [[ $# -gt 0 ]]; do
                case "$1" in
                    --service-id)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --service-id requires a value"
                            exit 1
                        fi
                        service_id="$2"
                        shift 2
                        ;;
                    --permissions)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --permissions requires a value"
                            exit 1
                        fi
                        permissions="$2"
                        shift 2
                        ;;
                    --security-url)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --security-url requires a value"
                            exit 1
                        fi
                        security_url="$2"
                        shift 2
                        ;;
                    --admin-token)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --admin-token requires a value"
                            exit 1
                        fi
                        admin_token="$2"
                        shift 2
                        ;;
                    --output-env)
                        output_env=true
                        shift
                        ;;
                    --help|-h)
                        show_help
                        exit 0
                        ;;
                    *)
                        if [[ -z "$service_id" ]]; then
                            service_id="$1"
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
            
            # If service-id wasn't provided as argument, try positional
            if [[ -z "$service_id" ]] && [[ $# -gt 0 ]]; then
                service_id="$1"
            fi
            
            register_service "$service_id" "$permissions" "$security_url" "$admin_token" "$output_env"
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

