#!/usr/bin/env bash
# Wrapper around ./gradlew for local Kotlin service development.
#
# Serializes the compile phase (:module:classes) with flock so multiple
# neotool dev start invocations do not contend on
# service/kotlin/.gradle/8.14/fileHashes/fileHashes.lock.
#
# Usage (from service/kotlin or any cwd):
#   /path/to/scripts/cli/gradlew-dev.sh :banking:run

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KOTLIN_ROOT="$(cd "$SCRIPT_DIR/../../service/kotlin" && pwd)"
LOCK_FILE="/tmp/neotool-gradle.lock"
GRADLE_LOCK_WAIT="${GRADLE_LOCK_WAIT:-300}"
GRADLE_ARGS=(--no-configuration-cache --no-build-cache)

if [[ $# -lt 1 ]]; then
    echo "Usage: gradlew-dev.sh <gradle-task> [gradle-args...]" >&2
    echo "Example: gradlew-dev.sh :banking:run" >&2
    exit 1
fi

gradle_task="$1"
shift

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(ls -d /usr/lib/jvm/temurin-21-jdk-* 2>/dev/null | head -1 || true)"
    if [[ -n "$JAVA_HOME" ]]; then
        export JAVA_HOME
    fi
fi

if [[ "${DEVPOD:-}" == "true" ]]; then
    DEVPOD_GRADLE_BASE="${NEOTOOL_GRADLE_BASE:-${HOME}/.cache/neotool-gradle/${DEVPOD_WORKSPACE_UID:-default}}"
    export NEOTOOL_GRADLE_BUILD_ROOT="${NEOTOOL_GRADLE_BUILD_ROOT:-${DEVPOD_GRADLE_BASE}/builds}"
    PROJECT_CACHE_DIR="${NEOTOOL_GRADLE_PROJECT_CACHE_DIR:-${DEVPOD_GRADLE_BASE}/project-cache}"
    mkdir -p "$NEOTOOL_GRADLE_BUILD_ROOT" "$PROJECT_CACHE_DIR"
    GRADLE_ARGS+=(--project-cache-dir "$PROJECT_CACHE_DIR")
fi

cd "$KOTLIN_ROOT"

module=""
if [[ "$gradle_task" =~ ^:([^:]+):run$ ]]; then
    module="${BASH_REMATCH[1]}"
fi

if [[ -n "$module" ]]; then
    if ! flock -w "$GRADLE_LOCK_WAIT" "$LOCK_FILE" \
        ./gradlew ":${module}:classes" "${GRADLE_ARGS[@]}"; then
        echo "ERROR: timed out waiting for Gradle lock (${GRADLE_LOCK_WAIT}s)." >&2
        echo "  Try: neotool dev gradle-reset" >&2
        exit 1
    fi
fi

exec ./gradlew "$gradle_task" "${GRADLE_ARGS[@]}" "$@"
