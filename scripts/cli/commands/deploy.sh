#!/usr/bin/env bash

set -euo pipefail

# Deploy Command
#
# Local production deployment fallback that mirrors CI/CD flow:
# 1) Sync main
# 2) Run validations
# 3) Create local git tag
# 4) Build production images
# 5) Confirm apply
# 6) Push images + tag
# 7) Update invistus-flux (flux-updates) image tags and push

COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

INVISTUS_FLUX_DIR="${INVISTUS_FLUX_DIR:-$PROJECT_ROOT/../invistus-flux}"
IMAGE_PREFIX="ghcr.io/invistus"
MAIN_BRANCH="main"
FLUX_BRANCH="flux-updates"
DEPLOY_REVISION=""

# Production scope mirrors .github/workflows/build-production.yml and excludes jobs/consumers.
IMAGE_SPECS=(
  "security|service/kotlin|service/kotlin/security/Dockerfile|neotool-security"
  "assets|service/kotlin|service/kotlin/assets/Dockerfile|neotool-assets"
  "financialdata|service/kotlin|service/kotlin/financialdata/Dockerfile|neotool-financialdata"
  "web|web|web/Dockerfile|neotool-web"
)

FLUX_IMAGE_FILES=(
  "infra/kubernetes/flux/apps/services/security/deployment.yaml|neotool-security"
  "infra/kubernetes/flux/apps/services/assets/deployment.yaml|neotool-assets"
  "infra/kubernetes/flux/apps/services/financial-data/deployment.yaml|neotool-financialdata"
  "infra/kubernetes/flux/apps/web/nextjs/deployment.yaml|neotool-web"
)

show_help() {
    cat << EOF_HELP
$(log "Deploy (Production Fallback)" "$BRIGHT")

Usage: $0 <namespace> --tag <tag> [options]

Runs a local deploy flow that mirrors production CI/CD:
  1. Checkout/pull main
  2. Run validation (web + service + security in parallel, optional)
  3. Create git tag on main
  4. Build production images (web + services)
  5. Confirm apply
  6. Push images to GHCR
  7. Update invistus-flux on branch flux-updates
  8. Commit and push flux changes

Arguments:
  <namespace>       Deployment namespace (currently only: production)

Required:
  --tag <tag>       Release tag (example: v0.1.49)

Options:
  --skip-validation Skip validation before build/push
  --yes             Skip confirmation prompt and apply immediately
  --help            Show this help message

Environment variables:
  INVISTUS_FLUX_DIR   Path to invistus-flux repo (default: ../invistus-flux)
  GHCR_USERNAME       GHCR username for docker login (optional)
  GHCR_TOKEN          GHCR token/PAT for docker login (optional)
  GITHUB_ACTOR        Alternative username for GHCR login
  GITHUB_TOKEN        Alternative token for GHCR login

Examples:
  $0 production --tag v0.1.49
  $0 production --tag v0.1.49 --skip-validation
  $0 production --tag v0.1.49 --yes
  INVISTUS_FLUX_DIR=../invistus-flux $0 production --tag v0.1.49
EOF_HELP
}

format_command() {
    local cmd_str=""
    printf -v cmd_str '%q ' "$@"
    echo "${cmd_str% }"
}

log_command() {
    log "  \$ $(format_command "$@")" "$CYAN"
}

run_cmd() {
    log_command "$@"
    "$@"
}

require_command() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        log_error "Error: Required command not found: $cmd"
        exit 1
    fi
}

ensure_git_repo() {
    local repo_dir="$1"
    local name="$2"

    if [[ ! -d "$repo_dir/.git" ]]; then
        log_error "Error: $name repository not found at $repo_dir"
        exit 1
    fi
}

ensure_clean_worktree() {
    local repo_dir="$1"
    local name="$2"
    local status_output

    log_command git -C "$repo_dir" status --porcelain
    status_output="$(git -C "$repo_dir" status --porcelain)"
    if [[ -n "$status_output" ]]; then
        log_error "Error: $name has uncommitted changes"
        log_error "Please commit/stash changes before running deploy"
        exit 1
    fi
}

validate_namespace() {
    local namespace="$1"
    if [[ "$namespace" != "production" ]]; then
        log_error "Error: Unsupported namespace '$namespace'"
        log_error "Currently only 'production' is supported by this deploy flow"
        exit 1
    fi
}

validate_tag() {
    local tag="$1"

    if [[ -z "$tag" ]]; then
        log_error "Error: --tag is required"
        exit 1
    fi

    if [[ "$tag" =~ [[:space:]] ]]; then
        log_error "Error: Invalid tag '$tag' (must not contain spaces)"
        exit 1
    fi

    if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9._-]+)?$ ]]; then
        log_error "Error: Invalid tag format '$tag'"
        log_error "Expected format like: v0.1.49"
        exit 1
    fi
}

sync_main_branch() {
    log "\n🔄 Syncing main branch..." "$BLUE"

    run_cmd git -C "$PROJECT_ROOT" fetch origin "$MAIN_BRANCH"
    run_cmd git -C "$PROJECT_ROOT" checkout "$MAIN_BRANCH"
    run_cmd git -C "$PROJECT_ROOT" pull --ff-only origin "$MAIN_BRANCH"

    local current_branch
    log_command git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD
    current_branch="$(git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD)"
    if [[ "$current_branch" != "$MAIN_BRANCH" ]]; then
        log_error "Error: Failed to switch to $MAIN_BRANCH"
        exit 1
    fi

    log "✅ Main branch is up to date" "$GREEN"
}

run_validation() {
    log "\n🔍 Running validations (web + service + security)..." "$BLUE"
    run_cmd "$PROJECT_ROOT/neotool" validate
    log "✅ Validation passed" "$GREEN"
}

ensure_tag_available() {
    local tag="$1"

    log_command git -C "$PROJECT_ROOT" show-ref --verify --quiet "refs/tags/$tag"
    if git -C "$PROJECT_ROOT" show-ref --verify --quiet "refs/tags/$tag"; then
        log_error "Error: Tag already exists locally: $tag"
        exit 1
    fi

    local remote_tag
    log_command git -C "$PROJECT_ROOT" ls-remote --tags origin "refs/tags/$tag"
    remote_tag="$(git -C "$PROJECT_ROOT" ls-remote --tags origin "refs/tags/$tag" 2>/dev/null || true)"
    if [[ -n "$remote_tag" ]]; then
        log_error "Error: Tag already exists on origin: $tag"
        exit 1
    fi
}

create_local_tag() {
    local tag="$1"

    ensure_tag_available "$tag"
    run_cmd git -C "$PROJECT_ROOT" tag -a "$tag" -m "Release $tag"
    log "✅ Created local tag: $tag" "$GREEN"
}

build_single_image() {
    local context="$1"
    local dockerfile="$2"
    local image_name="$3"
    local tag="$4"

    local full_image="$IMAGE_PREFIX/$image_name"

    docker buildx build \
      --platform linux/amd64 \
      --load \
      --file "$PROJECT_ROOT/$dockerfile" \
      --tag "$full_image:$tag" \
      --tag "$full_image:latest" \
      --label "org.opencontainers.image.source=https://github.com/invistus/invistus" \
      --label "org.opencontainers.image.revision=$DEPLOY_REVISION" \
      --label "org.opencontainers.image.version=$tag" \
      "$PROJECT_ROOT/$context"
}

build_images_parallel() {
    local tag="$1"

    log "\n🐳 Building production images (parallel)..." "$BLUE"

    local -a pids=()
    local -a names=()
    local -a logs=()

    local spec
    for spec in "${IMAGE_SPECS[@]}"; do
        IFS='|' read -r name context dockerfile image_name <<< "$spec"
        local full_image="$IMAGE_PREFIX/$image_name"
        local build_cmd
        build_cmd="$(format_command \
            docker buildx build \
            --platform linux/amd64 \
            --load \
            --file "$PROJECT_ROOT/$dockerfile" \
            --tag "$full_image:$tag" \
            --tag "$full_image:latest" \
            --label "org.opencontainers.image.source=https://github.com/invistus/invistus" \
            --label "org.opencontainers.image.revision=$DEPLOY_REVISION" \
            --label "org.opencontainers.image.version=$tag" \
            "$PROJECT_ROOT/$context")"

        local log_file
        log_file="$(mktemp -t neotool-deploy-build-${name}.XXXXXX)"

        (
            build_single_image "$context" "$dockerfile" "$image_name" "$tag"
        ) >"$log_file" 2>&1 &

        pids+=("$!")
        names+=("$name")
        logs+=("$log_file")

        log "  \$ $build_cmd" "$CYAN"
        log "  started build: $name (log: $log_file)" "$GRAY"
    done

    local failed=false
    local i
    for i in "${!pids[@]}"; do
        if wait "${pids[$i]}"; then
            log "✅ built: ${names[$i]}" "$GREEN"
            rm -f "${logs[$i]}" 2>/dev/null || true
        else
            failed=true
            log_error "❌ build failed: ${names[$i]}"
            log "Last output (${names[$i]}):" "$YELLOW"
            tail -n 120 "${logs[$i]}" 2>/dev/null || true
            echo ""
        fi
    done

    if [[ "$failed" == true ]]; then
        log_error "Build phase failed"
        exit 1
    fi

    log "✅ All images built" "$GREEN"
}

docker_login_ghcr_if_possible() {
    local username="${GHCR_USERNAME:-${GITHUB_ACTOR:-}}"
    local token="${GHCR_TOKEN:-${GITHUB_TOKEN:-}}"

    if [[ -n "$username" && -n "$token" ]]; then
        log "\n🔐 Logging in to GHCR as $username..." "$BLUE"
        log "  \$ docker login ghcr.io -u $username --password-stdin" "$CYAN"
        docker login ghcr.io -u "$username" --password-stdin >/dev/null <<< "$token"
        log "✅ GHCR login successful" "$GREEN"
    else
        log "\nℹ️  GHCR credentials not provided in env; using existing docker auth if available" "$YELLOW"
    fi
}

push_single_image() {
    local image_name="$1"
    local tag="$2"

    local full_image="$IMAGE_PREFIX/$image_name"
    docker push "$full_image:$tag"
    docker push "$full_image:latest"
}

push_images_parallel() {
    local tag="$1"

    log "\n📤 Pushing images to GHCR (parallel)..." "$BLUE"

    local -a pids=()
    local -a names=()
    local -a logs=()

    local spec
    for spec in "${IMAGE_SPECS[@]}"; do
        IFS='|' read -r name _context _dockerfile image_name <<< "$spec"
        local full_image="$IMAGE_PREFIX/$image_name"
        local push_cmd_tag
        local push_cmd_latest
        push_cmd_tag="$(format_command docker push "$full_image:$tag")"
        push_cmd_latest="$(format_command docker push "$full_image:latest")"

        local log_file
        log_file="$(mktemp -t neotool-deploy-push-${name}.XXXXXX)"

        (
            push_single_image "$image_name" "$tag"
        ) >"$log_file" 2>&1 &

        pids+=("$!")
        names+=("$name")
        logs+=("$log_file")

        log "  \$ $push_cmd_tag" "$CYAN"
        log "  \$ $push_cmd_latest" "$CYAN"
        log "  started push: $name (log: $log_file)" "$GRAY"
    done

    local failed=false
    local i
    for i in "${!pids[@]}"; do
        if wait "${pids[$i]}"; then
            log "✅ pushed: ${names[$i]}" "$GREEN"
            rm -f "${logs[$i]}" 2>/dev/null || true
        else
            failed=true
            log_error "❌ push failed: ${names[$i]}"
            log "Last output (${names[$i]}):" "$YELLOW"
            tail -n 120 "${logs[$i]}" 2>/dev/null || true
            echo ""
        fi
    done

    if [[ "$failed" == true ]]; then
        log_error "Push phase failed"
        exit 1
    fi

    log "✅ All images pushed" "$GREEN"
}

confirm_apply() {
    local namespace="$1"
    local tag="$2"
    local auto_yes="$3"

    if [[ "$auto_yes" == "true" ]]; then
        return 0
    fi

    echo ""
    read -r -p "Apply $tag to $namespace? [Y/n] " confirmation

    if [[ -z "$confirmation" ]]; then
        return 0
    fi

    if [[ "$confirmation" =~ ^[Yy]$|^[Yy][Ee][Ss]$ ]]; then
        return 0
    fi

    return 1
}

checkout_flux_branch() {
    log "\n🌿 Preparing invistus-flux branch $FLUX_BRANCH..." "$BLUE"

    run_cmd git -C "$INVISTUS_FLUX_DIR" fetch origin "$MAIN_BRANCH" "$FLUX_BRANCH"

    log_command git -C "$INVISTUS_FLUX_DIR" show-ref --verify --quiet "refs/heads/$FLUX_BRANCH"
    if git -C "$INVISTUS_FLUX_DIR" show-ref --verify --quiet "refs/heads/$FLUX_BRANCH"; then
        run_cmd git -C "$INVISTUS_FLUX_DIR" checkout "$FLUX_BRANCH"
    else
        run_cmd git -C "$INVISTUS_FLUX_DIR" checkout -b "$FLUX_BRANCH" "origin/$FLUX_BRANCH"
    fi

    run_cmd git -C "$INVISTUS_FLUX_DIR" pull --ff-only origin "$FLUX_BRANCH"

    log "✅ invistus-flux ready on $FLUX_BRANCH" "$GREEN"
}

update_flux_image_file() {
    local file_path="$1"
    local image_name="$2"
    local tag="$3"

    local full_path="$INVISTUS_FLUX_DIR/$file_path"

    if [[ ! -f "$full_path" ]]; then
        log_error "Error: Flux file not found: $file_path"
        exit 1
    fi

    local tmp_file
    tmp_file="$(mktemp -t neotool-flux-update.XXXXXX)"

    log_command sed -E "s|(image:[[:space:]]+ghcr.io/invistus/${image_name}:)[^[:space:]]+|\\1${tag}|g" "$full_path"
    sed -E "s|(image:[[:space:]]+ghcr.io/invistus/${image_name}:)[^[:space:]]+|\\1${tag}|g" "$full_path" > "$tmp_file"

    log_command cmp -s "$full_path" "$tmp_file"
    if cmp -s "$full_path" "$tmp_file"; then
        run_cmd rm -f "$tmp_file"
        log "  no change needed: $file_path" "$GRAY"
        return 0
    fi

    run_cmd mv "$tmp_file" "$full_path"
    log "  updated: $file_path" "$GREEN"
}

update_flux_artifacts() {
    local namespace="$1"
    local tag="$2"

    log "\n📝 Updating flux artifacts for $namespace ($tag)..." "$BLUE"

    local spec
    for spec in "${FLUX_IMAGE_FILES[@]}"; do
        IFS='|' read -r file_path image_name <<< "$spec"
        update_flux_image_file "$file_path" "$image_name" "$tag"
    done

    if [[ -z "$(git -C "$INVISTUS_FLUX_DIR" status --porcelain)" ]]; then
        log "ℹ️  No flux file changes detected" "$YELLOW"
        return 0
    fi

    local commit_message="chore(flux): apply ${tag} to ${namespace}"
    run_cmd git -C "$INVISTUS_FLUX_DIR" add infra/kubernetes/flux/apps
    run_cmd git -C "$INVISTUS_FLUX_DIR" commit -m "$commit_message"
    run_cmd git -C "$INVISTUS_FLUX_DIR" push origin "$FLUX_BRANCH"

    log "✅ Flux updates committed and pushed" "$GREEN"
}

push_release_tag() {
    local tag="$1"
    log "\n🏷️  Pushing tag $tag..." "$BLUE"
    run_cmd git -C "$PROJECT_ROOT" push origin "refs/tags/$tag"
    log "✅ Tag pushed: $tag" "$GREEN"
}

main() {
    local namespace=""
    local tag=""
    local auto_yes=false
    local skip_validation=false

    if [[ $# -eq 0 ]]; then
        show_help
        exit 0
    fi

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --tag)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --tag requires a value"
                    exit 1
                fi
                tag="$2"
                shift 2
                ;;
            --yes)
                auto_yes=true
                shift
                ;;
            --skip-validation)
                skip_validation=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            --*)
                log_error "Unknown option: $1"
                echo ""
                show_help
                exit 1
                ;;
            *)
                if [[ -z "$namespace" ]]; then
                    namespace="$1"
                    shift
                else
                    log_error "Unexpected argument: $1"
                    echo ""
                    show_help
                    exit 1
                fi
                ;;
        esac
    done

    if [[ -z "$namespace" ]]; then
        log_error "Error: namespace is required"
        echo ""
        show_help
        exit 1
    fi

    validate_namespace "$namespace"
    validate_tag "$tag"

    require_command git
    require_command docker

    ensure_git_repo "$PROJECT_ROOT" "invistus"
    ensure_git_repo "$INVISTUS_FLUX_DIR" "invistus-flux"

    ensure_clean_worktree "$PROJECT_ROOT" "invistus"
    ensure_clean_worktree "$INVISTUS_FLUX_DIR" "invistus-flux"

    sync_main_branch
    log_command git -C "$PROJECT_ROOT" rev-parse HEAD
    DEPLOY_REVISION="$(git -C "$PROJECT_ROOT" rev-parse HEAD)"
    log "Deploy revision: $DEPLOY_REVISION" "$GRAY"

    if [[ "$skip_validation" == true ]]; then
        log "\n⏭️  Skipping validation (--skip-validation)" "$YELLOW"
    else
        run_validation
    fi

    create_local_tag "$tag"
    build_images_parallel "$tag"

    if ! confirm_apply "$namespace" "$tag" "$auto_yes"; then
        log "\nDeployment cancelled before apply." "$YELLOW"
        log "Local artifacts kept:" "$YELLOW"
        log "  - local git tag: $tag" "$YELLOW"
        log "  - built local images: $IMAGE_PREFIX/*:$tag" "$YELLOW"
        exit 0
    fi

    docker_login_ghcr_if_possible
    push_images_parallel "$tag"
    push_release_tag "$tag"

    checkout_flux_branch
    ensure_clean_worktree "$INVISTUS_FLUX_DIR" "invistus-flux"
    update_flux_artifacts "$namespace" "$tag"

    log "\n🎉 Deployment flow completed successfully" "$BRIGHT"
    log "Namespace: $namespace" "$BRIGHT"
    log "Tag:       $tag" "$BRIGHT"
}

main "$@"
