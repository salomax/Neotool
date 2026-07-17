#!/usr/bin/env bash
# Build the invistus-rover image with the supergraph plugin baked in.
# Usage:
#   ./scripts/cli/commands/graphql/build-rover-image.sh
#   DOCKER="docker exec <container> docker" ./scripts/cli/commands/graphql/build-rover-image.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
DEVCONTAINER_DIR="$PROJECT_ROOT/.devcontainer"
# shellcheck source=/dev/null
source "$DEVCONTAINER_DIR/rover-versions.env"

DOCKER_CMD="${DOCKER:-docker}"
IMAGE_TAG="invistus-rover:${ROVER_VERSION}-sg${SUPERGRAPH_PLUGIN_VERSION}"

echo "▶ Building ${IMAGE_TAG} (Rover ${ROVER_VERSION} + supergraph ${SUPERGRAPH_PLUGIN_VERSION})..."
# shellcheck disable=SC2086
$DOCKER_CMD build \
  --build-arg "ROVER_VERSION=${ROVER_VERSION}" \
  --build-arg "SUPERGRAPH_PLUGIN_VERSION=${SUPERGRAPH_PLUGIN_VERSION}" \
  -f "$DEVCONTAINER_DIR/Dockerfile.rover" \
  -t "$IMAGE_TAG" \
  -t "invistus-rover:latest" \
  "$DEVCONTAINER_DIR"

echo "✓ Built ${IMAGE_TAG} (also tagged invistus-rover:latest)"
