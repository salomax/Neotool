#!/bin/bash
set -euo pipefail

echo "Generating supergraph schemas..."

# Calculate paths from script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
CONTRACTS_DIR="$PROJECT_ROOT/contracts/graphql"
SUPERGRAPH_DIR="$CONTRACTS_DIR/supergraph"
ROVER_DOCKER_IMAGE="${ROVER_DOCKER_IMAGE:-ghcr.io/apollographql/rover:0.40.0}"

# Flags
PRODUCTION_ONLY=true
SYNC_FLUX=true
ALL_ENVS=false

for arg in "$@"; do
    case "$arg" in
        --production)
            PRODUCTION_ONLY=true
            ;;
        --all)
            ALL_ENVS=true
            PRODUCTION_ONLY=false
            ;;
        --sync-flux)
            SYNC_FLUX=true
            ;;
        --no-sync-flux)
            SYNC_FLUX=false
            ;;
    esac
done

# Function to generate a supergraph with rover in Docker
generate_with_docker() {
    local config_file="$1"
    local output_file="$2"
    local env_name="$3"
    local error_log
    error_log="$(mktemp)"
    
    echo "  Generating $env_name supergraph..."
    if ! docker run --rm \
        -v "$CONTRACTS_DIR:/workspace" \
        -w /workspace/supergraph \
        -e APOLLO_ELV2_LICENSE=accept \
        "$ROVER_DOCKER_IMAGE" \
        supergraph compose \
        --elv2-license accept \
        --config "./$config_file" \
        --output "./$output_file" 2>"$error_log"; then
        echo "❌ Docker-based rover failed while generating $env_name supergraph"
        echo "   Image: $ROVER_DOCKER_IMAGE"
        if grep -qi "pull access denied\|not found\|manifest unknown" "$error_log"; then
            echo "   The configured Rover image is unavailable."
            echo "   Override it with ROVER_DOCKER_IMAGE=<valid-image> or run locally without --docker."
        fi
        cat "$error_log"
        rm -f "$error_log"
        return 1
    fi

    rm -f "$error_log"
}

# Function to generate a supergraph with local rover
generate_with_local() {
    local config_file="$1"
    local output_file="$2"
    local env_name="$3"
    local error_log
    error_log="$(mktemp)"
    
    echo "  Generating $env_name supergraph..."
    cd "$SUPERGRAPH_DIR"
    if ! APOLLO_ELV2_LICENSE=accept rover supergraph compose \
        --elv2-license accept \
        --config "./$config_file" \
        --output "./$output_file" 2>"$error_log"; then
        echo "❌ Local rover failed while generating $env_name supergraph"
        if grep -qi "ELV2 license must be accepted" "$error_log"; then
            echo "   Rover requires the Apollo ELv2 license to be accepted for composition."
        fi
        if grep -qi "unable to find dependency\|error sending request\|dns error\|failed to lookup address information" "$error_log"; then
            echo "   Rover could not download the composition dependency."
            echo "   Retry with network access, or use --docker with a valid Rover image."
        fi
        cat "$error_log"
        rm -f "$error_log"
        return 1
    fi

    rm -f "$error_log"
}

# Function to run rover in Docker (recommended for CI/CD)
run_rover_docker() {
    echo "Using Docker-based rover..."
    generate_with_docker "supergraph.yaml" "supergraph.graphql" "production"
    if [[ "$PRODUCTION_ONLY" == false ]]; then
        generate_with_docker "supergraph.local.yaml" "supergraph.local.graphql" "development"
        generate_with_docker "supergraph.staging.yaml" "supergraph.staging.graphql" "staging"
    fi
}

# Function to run rover locally (for development)
run_rover_local() {
    echo "Using local rover..."
    
    # Check if rover is installed
    if ! command -v rover &> /dev/null; then
        echo "Error: rover is not installed or not in PATH"
        echo "Please install rover first:"
        echo "  curl -sSL https://rover.apollo.dev/nix/latest | sh"
        echo "  or visit: https://www.apollographql.com/docs/rover/getting-started/"
        echo ""
        echo "Alternatively, use Docker:"
        echo "  ./neotool graphql generate --docker"
        exit 1
    fi

    # Accept ELv2 license
    export APOLLO_ELV2_LICENSE=accept

    # Generate both supergraph schemas
    generate_with_local "supergraph.yaml" "supergraph.graphql" "production"
    if [[ "$PRODUCTION_ONLY" == false ]]; then
        generate_with_local "supergraph.local.yaml" "supergraph.local.graphql" "development"
        generate_with_local "supergraph.staging.yaml" "supergraph.staging.graphql" "staging"
    fi
}

# Check if we should use Docker (CI environment or explicit flag)
# Check all arguments for --docker flag, not just $1
use_docker_flag=false
for arg in "$@"; do
    if [[ "$arg" == "--docker" ]]; then
        use_docker_flag=true
        break
    fi
done

if [[ "${CI:-false}" == "true" ]] || [[ "${USE_DOCKER_ROVER:-false}" == "true" ]] || [[ "$use_docker_flag" == true ]]; then
    run_rover_docker
else
    run_rover_local
fi

sync_flux_outputs() {
    local flux_root="${INVISTUS_FLUX_DIR:-$PROJECT_ROOT/../invistus-flux}"
    local flux_router_dir="$flux_root/infra/kubernetes/flux/apps/router"

    if [[ ! -d "$flux_router_dir" ]]; then
        echo "⚠️  Flux router directory not found: $flux_router_dir"
        echo "   Skipping Flux sync"
        return 0
    fi

    cp "$SUPERGRAPH_DIR/supergraph.graphql" "$flux_router_dir/supergraph.graphql"
    cp "$SUPERGRAPH_DIR/supergraph.yaml" "$flux_router_dir/supergraph.yaml"
    echo "✅ Synced production supergraph files to Flux:"
    echo "   - $flux_router_dir/supergraph.graphql"
    echo "   - $flux_router_dir/supergraph.yaml"
}

echo ""
echo "✅ Supergraph schemas generated successfully!"
if [[ -f "$SUPERGRAPH_DIR/supergraph.graphql" ]]; then
    echo "  📄 Production: $SUPERGRAPH_DIR/supergraph.graphql ($(wc -c < "$SUPERGRAPH_DIR/supergraph.graphql") bytes)"
fi
if [[ "$PRODUCTION_ONLY" == false ]] && [[ -f "$SUPERGRAPH_DIR/supergraph.local.graphql" ]]; then
    echo "  📄 Development: $SUPERGRAPH_DIR/supergraph.local.graphql ($(wc -c < "$SUPERGRAPH_DIR/supergraph.local.graphql") bytes)"
fi
if [[ "$PRODUCTION_ONLY" == false ]] && [[ -f "$SUPERGRAPH_DIR/supergraph.staging.graphql" ]]; then
    echo "  📄 Staging: $SUPERGRAPH_DIR/supergraph.staging.graphql ($(wc -c < "$SUPERGRAPH_DIR/supergraph.staging.graphql") bytes)"
fi

if [[ "$SYNC_FLUX" == true ]]; then
    sync_flux_outputs
fi
