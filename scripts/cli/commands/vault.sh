#!/usr/bin/env bash

set -euo pipefail

# Vault Command
# 
# Manages HashiCorp Vault secrets for JWT keys

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_VAULT_ADDRESS="http://localhost:8200"
DEFAULT_VAULT_TOKEN="${VAULT_TOKEN:-myroot}"
DEFAULT_SECRET_PATH="secret/jwt/keys"
DEFAULT_KEY_BITS=4096
VAULT_CONTAINER_NAME="neotool-vault"

VAULT_ADDRESS="${VAULT_ADDRESS:-$DEFAULT_VAULT_ADDRESS}"
VAULT_TOKEN="${VAULT_TOKEN:-$DEFAULT_VAULT_TOKEN}"

# Show help text
show_help() {
    cat << EOF
$(log "Vault Management" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "create-secret" "$GREEN") <key-name>
    Create a new JWT key pair in Vault with the specified key name.
    Generates a 4096-bit RSA key pair and stores it at secret/jwt/keys/<key-name>.
    
    Options:
      --key-bits <bits>     RSA key size in bits (default: 4096)
      --secret-path <path>   Vault secret path prefix (default: secret/jwt/keys)
      --vault-address <url> Vault server address (default: http://localhost:8200)
      --vault-token <token>  Vault authentication token (default: from VAULT_TOKEN env or 'myroot')
      --force                Overwrite existing secret if it exists

Environment variables:
  VAULT_ADDRESS              Vault server address
  VAULT_TOKEN                Vault authentication token

Examples:
  $0 create-secret kid-1
  $0 create-secret kid-2 --key-bits 2048
  $0 create-secret my-key --vault-address http://vault:8200
  $0 create-secret kid-1 --force
EOF
}

# Check if Vault is accessible
check_vault_connection() {
    local vault_addr="$1"
    local vault_token="$2"
    
    if ! command -v vault >/dev/null 2>&1 && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        log_error "Error: Vault CLI not found and Vault container is not running"
        log_error "Options:"
        log_error "  1. Install Vault CLI: https://developer.hashicorp.com/vault/downloads"
        log_error "  2. Start Vault container: docker-compose -f infra/docker/docker-compose.local.yml --profile secrets up -d vault"
        return 1
    fi
    
    # Try to check Vault status
    if command -v vault >/dev/null 2>&1; then
        # Use local Vault CLI
        if ! VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" vault status >/dev/null 2>&1; then
            log_error "Error: Cannot connect to Vault at ${vault_addr}"
            log_error "Make sure Vault is running and accessible"
            return 1
        fi
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Use Docker exec - need to pass token for authentication check
        if ! docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" vault status >/dev/null 2>&1; then
            log_error "Error: Vault container is running but not responding"
            log_error "Check your VAULT_TOKEN environment variable or use --vault-token"
            return 1
        fi
    else
        log_error "Error: Cannot access Vault"
        return 1
    fi
    
    return 0
}

# Execute Vault command (via CLI or Docker)
execute_vault_command() {
    local command="$1"
    local vault_addr="$2"
    local vault_token="$3"
    
    if command -v vault >/dev/null 2>&1; then
        # Use local Vault CLI
        VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" eval "$command"
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Use Docker exec - need to set env vars in container
        docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" sh -c "$command"
    else
        log_error "Error: Cannot execute Vault command"
        return 1
    fi
}

# Store secret in Vault using HTTP API (more reliable for multiline values)
store_secret_via_api() {
    local secret_path="$1"
    local private_key="$2"
    local public_key="$3"
    local vault_addr="$4"
    local vault_token="$5"
    
    # Convert path to API path (secret/jwt/keys/kid-1 -> secret/data/jwt/keys/kid-1 for KV v2)
    local api_path="${secret_path}"
    # If path doesn't start with secret/data, assume it's a KV v2 path and convert
    if [[ ! "$api_path" =~ ^secret/data/ ]]; then
        # Replace first 'secret/' with 'secret/data/'
        if [[ "$api_path" =~ ^secret/ ]]; then
            api_path="${api_path/#secret\//secret\/data\/}"
        else
            # If path doesn't start with secret/, add it
            api_path="secret/data/${api_path}"
        fi
    fi
    
    # Ensure vault_addr doesn't have trailing slash
    local clean_vault_addr="${vault_addr%/}"
    local api_url="${clean_vault_addr}/v1/${api_path}"
    
    # Create JSON payload using jq if available, otherwise use manual escaping
    local temp_json
    temp_json=$(mktemp)
    
    if command -v jq >/dev/null 2>&1; then
        # Use jq for proper JSON encoding
        jq -n \
            --arg private "$private_key" \
            --arg public "$public_key" \
            '{data: {private: $private, public: $public}}' > "$temp_json"
    else
        # Manual JSON creation with basic escaping
        # This is a fallback if jq is not available
        local private_escaped
        private_escaped=$(printf '%s' "$private_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
        local public_escaped
        public_escaped=$(printf '%s' "$public_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
        
        cat > "$temp_json" << EOF
{
  "data": {
    "private": "${private_escaped}",
    "public": "${public_escaped}"
  }
}
EOF
    fi
    
    # Use curl to make API call
    local http_code
    local response
    response=$(curl -s -w "\n%{http_code}" \
        --header "X-Vault-Token: ${vault_token}" \
        --header "Content-Type: application/json" \
        --request POST \
        --data @"${temp_json}" \
        "${api_url}" 2>&1)
    
    http_code=$(echo "$response" | tail -n1)
    local exit_code=$?
    rm -f "$temp_json"
    
    if [[ $exit_code -ne 0 ]] || [[ "$http_code" != "200" ]]; then
        log_error "API call failed with HTTP code: ${http_code}"
        if [[ "$http_code" == "000" ]]; then
            log_error "Could not connect to Vault at ${vault_addr}"
        elif [[ "$http_code" == "404" ]]; then
            log_error "Secret path not found: ${api_path}"
            log_error "Make sure the KV v2 engine is enabled at 'secret' mount"
            log_error "Try: vault secrets enable -version=2 -path=secret kv"
        elif [[ "$http_code" == "403" ]]; then
            log_error "Permission denied. Check your Vault token has write access to ${api_path}"
        else
            # Show response body for debugging
            local response_body
            response_body=$(echo "$response" | head -n -1)
            if [[ -n "$response_body" ]]; then
                log_error "Response: $response_body"
            fi
        fi
        return 1
    fi
    
    return 0
}

# Generate RSA key pair
generate_key_pair() {
    local key_bits="$1"
    local temp_dir
    temp_dir=$(mktemp -d)
    local private_key_file="${temp_dir}/private.pem"
    local public_key_file="${temp_dir}/public.pem"
    
    # Generate private key
    if ! openssl genpkey -algorithm RSA -out "$private_key_file" -pkeyopt "rsa_keygen_bits:${key_bits}" 2>/dev/null; then
        log_error "Error: Failed to generate private key"
        log_error "Make sure OpenSSL is installed"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # Extract public key
    if ! openssl rsa -pubout -in "$private_key_file" -out "$public_key_file" 2>/dev/null; then
        log_error "Error: Failed to extract public key"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # Read keys
    local private_key
    private_key=$(cat "$private_key_file")
    local public_key
    public_key=$(cat "$public_key_file")
    
    # Clean up
    rm -rf "$temp_dir"
    
    # Return keys via global variables (bash limitation)
    GENERATED_PRIVATE_KEY="$private_key"
    GENERATED_PUBLIC_KEY="$public_key"
    
    return 0
}

# Check if secret exists
secret_exists() {
    local secret_path="$1"
    local vault_addr="$2"
    local vault_token="$3"
    
    local output
    output=$(execute_vault_command "vault kv get ${secret_path} 2>&1" "$vault_addr" "$vault_token" || true)
    
    # Check for common "not found" messages
    if echo "$output" | grep -qi "No value found\|secret not found\|no secret exists"; then
        return 1
    fi
    
    # If we got output that doesn't indicate an error, assume it exists
    # Check for common error patterns
    if echo "$output" | grep -qi "error\|failed\|permission denied"; then
        # Might be an error, but if it's not a "not found" error, assume it exists
        # (to be safe, we'll assume it doesn't exist on errors)
        return 1
    fi
    
    # If we have some output, assume secret exists
    if [[ -n "$output" ]]; then
        return 0
    fi
    
    return 1
}

# Create secret in Vault
create_secret() {
    local key_name="$1"
    local key_bits="$2"
    local secret_path_prefix="$3"
    local vault_addr="$4"
    local vault_token="$5"
    local force="$6"
    
    local secret_path="${secret_path_prefix}/${key_name}"
    
    # Validate key name
    if [[ -z "$key_name" ]]; then
        log_error "Error: Key name is required"
        echo ""
        show_help
        exit 1
    fi
    
    # Check if key name is valid (alphanumeric, dash, underscore)
    if [[ ! "$key_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        log_error "Error: Invalid key name '${key_name}'"
        log_error "Key name must contain only alphanumeric characters, dashes, and underscores"
        exit 1
    fi
    
    # Check Vault connection
    log "Checking Vault connection...\n" "$BLUE"
    if ! check_vault_connection "$vault_addr" "$vault_token"; then
        exit 1
    fi
    log "Vault connection successful\n" "$GREEN"
    
    # Check if secret already exists
    if secret_exists "$secret_path" "$vault_addr" "$vault_token"; then
        if [[ "$force" != "true" ]]; then
            log_error "Error: Secret already exists at ${secret_path}"
            log_error "Use --force to overwrite existing secret"
            exit 1
        else
            log "Warning: Secret already exists at ${secret_path}, overwriting...\n" "$YELLOW"
        fi
    fi
    
    # Generate key pair
    log "Generating ${key_bits}-bit RSA key pair...\n" "$BLUE"
    if ! generate_key_pair "$key_bits"; then
        exit 1
    fi
    log "Key pair generated successfully\n" "$GREEN"
    
    # Store in Vault
    log "Storing keys in Vault at ${secret_path}...\n" "$BLUE"
    
    # Determine if we should use CLI or API
    # Prefer CLI when available, use API as fallback
    local use_api=false
    local cli_failed=false
    
    # Check if we have vault CLI available
    if command -v vault >/dev/null 2>&1; then
        # Try CLI first with file input
        local temp_private
        temp_private=$(mktemp)
        local temp_public
        temp_public=$(mktemp)
        
        echo "$GENERATED_PRIVATE_KEY" > "$temp_private"
        echo "$GENERATED_PUBLIC_KEY" > "$temp_public"
        
        # Try vault kv put with file input
        local vault_cmd="vault kv put ${secret_path} private=@${temp_private} public=@${temp_public}"
        local output
        output=$(VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" eval "$vault_cmd" 2>&1)
        local exit_code=$?
        
        rm -f "$temp_private" "$temp_public"
        
        if [[ $exit_code -eq 0 ]]; then
            # CLI succeeded
            use_api=false
        else
            cli_failed=true
            log "Vault CLI approach failed, trying HTTP API...\n" "$YELLOW"
            log "CLI error: $output\n" "$GRAY"
        fi
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Docker mode - try using docker exec with vault CLI first
        log "Using Docker exec with Vault CLI...\n" "$BLUE"
        
        # Create temporary files in container
        local temp_private
        temp_private=$(mktemp)
        local temp_public
        temp_public=$(mktemp)
        
        echo "$GENERATED_PRIVATE_KEY" > "$temp_private"
        echo "$GENERATED_PUBLIC_KEY" > "$temp_public"
        
        # Copy files to container
        docker cp "$temp_private" "${VAULT_CONTAINER_NAME}:/tmp/private.pem" >/dev/null 2>&1
        docker cp "$temp_public" "${VAULT_CONTAINER_NAME}:/tmp/public.pem" >/dev/null 2>&1
        
        # Use vault kv put in container
        local vault_cmd="vault kv put ${secret_path} private=@/tmp/private.pem public=@/tmp/public.pem"
        local output
        output=$(docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" sh -c "$vault_cmd" 2>&1)
        local exit_code=$?
        
        # Clean up container files
        docker exec "$VAULT_CONTAINER_NAME" sh -c "rm -f /tmp/private.pem /tmp/public.pem" >/dev/null 2>&1
        rm -f "$temp_private" "$temp_public"
        
        if [[ $exit_code -eq 0 ]]; then
            # Docker CLI succeeded
            use_api=false
        else
            cli_failed=true
            log "Docker CLI approach failed, trying HTTP API...\n" "$YELLOW"
            log "CLI error: $output\n" "$GRAY"
        fi
    else
        # No CLI available, must use API
        cli_failed=true
    fi
    
    # Store using API if CLI failed or not available
    if [[ "$cli_failed" == "true" ]]; then
        if ! command -v curl >/dev/null 2>&1; then
            log_error "Error: Neither Vault CLI nor curl is available"
            log_error "Please install Vault CLI or curl to use this command"
            exit 1
        fi
        
        if ! store_secret_via_api "$secret_path" "$GENERATED_PRIVATE_KEY" "$GENERATED_PUBLIC_KEY" "$vault_addr" "$vault_token"; then
            log_error "Error: Failed to store keys in Vault"
            exit 1
        fi
    fi
    
    log "Keys stored successfully in Vault!\n" "$GREEN"
    echo ""
    log "Secret Path: ${secret_path}" "$BRIGHT"
    log "Key Name: ${key_name}" "$BRIGHT"
    log "Key Size: ${key_bits} bits" "$BRIGHT"
    echo ""
    log "You can verify the secret with:" "$BLUE"
    log "  vault kv get ${secret_path}" "$GRAY"
    echo ""
    log "To use this key in your application, set:" "$BLUE"
    log "  export JWT_KEY_ID=${key_name}" "$GRAY"
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
        create-secret)
            local key_name=""
            local key_bits="$DEFAULT_KEY_BITS"
            local secret_path="$DEFAULT_SECRET_PATH"
            local vault_addr="$VAULT_ADDRESS"
            local vault_token="$VAULT_TOKEN"
            local force=false
            
            # Parse arguments
            while [[ $# -gt 0 ]]; do
                case "$1" in
                    --key-bits)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --key-bits requires a value"
                            exit 1
                        fi
                        key_bits="$2"
                        shift 2
                        ;;
                    --secret-path)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --secret-path requires a value"
                            exit 1
                        fi
                        secret_path="$2"
                        shift 2
                        ;;
                    --vault-address)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-address requires a value"
                            exit 1
                        fi
                        vault_addr="$2"
                        shift 2
                        ;;
                    --vault-token)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-token requires a value"
                            exit 1
                        fi
                        vault_token="$2"
                        shift 2
                        ;;
                    --force)
                        force=true
                        shift
                        ;;
                    --help|-h)
                        show_help
                        exit 0
                        ;;
                    *)
                        if [[ -z "$key_name" ]]; then
                            key_name="$1"
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
            
            if [[ -z "$key_name" ]]; then
                log_error "Error: Key name is required"
                echo ""
                show_help
                exit 1
            fi
            
            create_secret "$key_name" "$key_bits" "$secret_path" "$vault_addr" "$vault_token" "$force"
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

