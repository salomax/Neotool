#!/usr/bin/env bash
# =============================================================================
# scripts/colima-start.sh
# Starts Colima with the recommended settings for Invistus development and
# configures a swapfile inside the VM (prevents OOM kills during Next.js,
# Kotlin compilation, and Playwright E2E).
#
# Tuned for Apple Silicon Macs with 18GB RAM:
#   - 14GB VM: multiple Kotlin services + Next.js dev + Playwright headroom
#   - 4GB swap: absorbs compilation spikes without silent OOM kills
#   - ~4GB left for macOS
#
# Usage:
#   ./scripts/colima-start.sh [--cpu N] [--memory N] [--disk N] [--swap N]
#
# Environment overrides: CPU, MEMORY, DISK, SWAP_GB
# Defaults: 6 CPUs, 14GB RAM, 100GB disk, 4GB swap, vz + Rosetta (Apple Silicon)
# =============================================================================

set -euo pipefail

CPU=${CPU:-6}
MEMORY=${MEMORY:-14}
DISK=${DISK:-100}
SWAP_GB=${SWAP_GB:-4}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --cpu) CPU="$2"; shift 2 ;;
        --memory) MEMORY="$2"; shift 2 ;;
        --disk) DISK="$2"; shift 2 ;;
        --swap) SWAP_GB="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: $0 [--cpu N] [--memory N] [--disk N] [--swap N]" >&2
            exit 1
            ;;
    esac
done

colima_is_running() {
    colima status -j 2>/dev/null | jq -e '.driver' >/dev/null 2>&1
}

colima_memory_gb() {
    local bytes
    bytes=$(colima status -j 2>/dev/null | jq -r '.memory // 0')
    echo $(( bytes / 1024 / 1024 / 1024 ))
}

# -----------------------------------------------------------------------------
# 1. Start Colima (restart automatically when RAM allocation changed)
# -----------------------------------------------------------------------------
if colima_is_running; then
    current_mem=$(colima_memory_gb)
    if [[ "$current_mem" -ne "$MEMORY" ]]; then
        echo "▶ Colima is running with ${current_mem}GB RAM; resizing to ${MEMORY}GB..."
        colima stop
    else
        echo "✓ Colima already running (${MEMORY}GB RAM, ${CPU} CPUs)"
    fi
fi

if ! colima_is_running; then
    echo "▶ Starting Colima (cpu=${CPU}, mem=${MEMORY}GB, disk=${DISK}GB)..."
    colima start \
        --cpu "$CPU" \
        --memory "$MEMORY" \
        --disk "$DISK" \
        --vm-type vz \
        --vz-rosetta
    echo "✓ Colima started"
fi

# -----------------------------------------------------------------------------
# 2. Configure swap inside the VM (persists until colima stop)
# -----------------------------------------------------------------------------
echo "▶ Configuring ${SWAP_GB}GB swap in Colima VM..."

colima ssh -- bash -s <<EOF
set -e

if swapon --show 2>/dev/null | grep -q swapfile; then
    echo "✓ Swap already active"
    free -h | grep Swap
    exit 0
fi

echo "  Creating ${SWAP_GB}GB swapfile..."
sudo dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB * 1024)) status=none
sudo chmod 600 /swapfile
sudo mkswap /swapfile -q
sudo swapon /swapfile
echo "✓ Swap configured"
free -h | grep Swap
EOF

echo ""
echo "✓ Colima ready for Invistus development (${MEMORY}GB RAM + ${SWAP_GB}GB swap)"
echo "  Next: neotool dev up"
