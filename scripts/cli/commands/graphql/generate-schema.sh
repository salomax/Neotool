#!/bin/bash
set -euo pipefail

echo "Generating supergraph schemas..."

# Calculate paths from script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
CONTRACTS_DIR="$PROJECT_ROOT/contracts/graphql"
SUPERGRAPH_DIR="$CONTRACTS_DIR/supergraph"
# shellcheck source=/dev/null
source "$PROJECT_ROOT/.devcontainer/rover-versions.env"
# Baked image from `neotool dev up` / `--recreate` (plugin already inside — no runtime download).
ROVER_DOCKER_IMAGE="${ROVER_DOCKER_IMAGE:-invistus-rover:${ROVER_VERSION}-sg${SUPERGRAPH_PLUGIN_VERSION}}"

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

ensure_invistus_rover_image() {
    if docker image inspect "$ROVER_DOCKER_IMAGE" >/dev/null 2>&1; then
        return 0
    fi
    echo "❌ Rover image not found: $ROVER_DOCKER_IMAGE"
    echo "   Build it once during sandbox setup (downloads plugin at build time only):"
    echo "     ./neotool dev up --recreate"
    echo "   Or:"
    echo "     $PROJECT_ROOT/scripts/cli/commands/graphql/build-rover-image.sh"
    echo "   If the build fails, verify outbound access to rover.apollo.dev and GitHub releases."
    echo "   Override with ROVER_DOCKER_IMAGE=<image> if you manage your own image."
    return 1
}

# Function to generate a supergraph with rover in Docker
generate_with_docker() {
    local config_file="$1"
    local output_file="$2"
    local env_name="$3"
    local error_log
    local status
    error_log="$(mktemp)"

    ensure_invistus_rover_image || return 1
    
    echo "  Generating $env_name supergraph..."
    echo "  Using baked image $ROVER_DOCKER_IMAGE (--skip-update, no plugin download)..."
    set +e
    docker run --rm \
        -v "$CONTRACTS_DIR:/workspace" \
        -w /workspace/supergraph \
        -e APOLLO_ELV2_LICENSE=accept \
        "$ROVER_DOCKER_IMAGE" \
        supergraph compose \
        --elv2-license accept \
        --skip-update \
        --federation-version "=${SUPERGRAPH_PLUGIN_VERSION}" \
        --config "./$config_file" \
        --output "./$output_file" 2>&1 | tee "$error_log"
    status=${PIPESTATUS[0]}
    set -e
    if [[ "$status" -ne 0 ]]; then
        echo "❌ Docker-based rover failed while generating $env_name supergraph"
        echo "   Image: $ROVER_DOCKER_IMAGE"
        if grep -qi "pull access denied\|not found\|manifest unknown" "$error_log"; then
            echo "   Rebuild with: ./neotool dev up --recreate"
        fi
        if grep -qi "failed to fetch oauth token\|denied: denied\|authentication required" "$error_log"; then
            echo "   Registry authentication failed while trying to resolve the rover image."
            echo "   Rebuild the baked image locally with:"
            echo "     ./scripts/cli/commands/graphql/build-rover-image.sh"
            echo "   If that also fails, check outbound access to rover.apollo.dev and GitHub releases."
        fi
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
    local status
    error_log="$(mktemp)"
    
    echo "  Generating $env_name supergraph..."
    echo "  Using local rover (--skip-update; plugin should already be installed)..."
    cd "$SUPERGRAPH_DIR"
    set +e
    APOLLO_ELV2_LICENSE=accept rover supergraph compose \
        --elv2-license accept \
        --skip-update \
        --federation-version "=${SUPERGRAPH_PLUGIN_VERSION}" \
        --config "./$config_file" \
        --output "./$output_file" 2>&1 | tee "$error_log"
    status=${PIPESTATUS[0]}
    set -e
    if [[ "$status" -ne 0 ]]; then
        echo "❌ Local rover failed while generating $env_name supergraph"
        if grep -qi "ELV2 license must be accepted" "$error_log"; then
            echo "   Rover requires the Apollo ELv2 license to be accepted for composition."
        fi
        if grep -qi "unable to find dependency\|error sending request\|dns error\|failed to lookup address information\|skip-update\|plugin\|Failed to run the composition binary" "$error_log"; then
            echo "   Composition plugin missing or incompatible."
            echo "   Inside DevPod: ./neotool dev up --recreate  (bakes rover + plugin into the image)"
            echo "   On the host:   rover install --plugin supergraph@=${SUPERGRAPH_PLUGIN_VERSION} --elv2-license accept"
        fi
        rm -f "$error_log"
        return 1
    fi

    rm -f "$error_log"
}

# Function to run rover in Docker (recommended for CI/CD)
run_rover_docker() {
    echo "Using Docker-based rover ($ROVER_DOCKER_IMAGE)..."
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
        echo "Inside DevPod: ./neotool dev up --recreate"
        echo "On the host:"
        echo "  curl -sSL https://rover.apollo.dev/nix/v${ROVER_VERSION} | sh"
        echo "  rover install --plugin supergraph@=${SUPERGRAPH_PLUGIN_VERSION} --elv2-license accept"
        echo ""
        echo "Alternatively, use the baked Docker image:"
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
