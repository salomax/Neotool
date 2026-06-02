# Multi-stage Dockerfile for asset-brand-job
# Single binary: cronjob

# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM golang:1.24-alpine AS builder

RUN apk add --no-cache git ca-certificates tzdata

WORKDIR /build/common/asset_brand_job

# Copy shared modules that are referenced via replace directives.
COPY shared/auth   /build/shared/auth
COPY shared/health /build/shared/health

# Copy dependency manifests first to maximise cache reuse for go mod download.
COPY common/asset_brand_job/go.mod common/asset_brand_job/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# Copy service source after dependency resolution.
COPY common/asset_brand_job ./

# Build the single cronjob binary with size optimisations.
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /build/bin/cronjob \
    ./cmd/cronjob

# ── Stage 2: Cronjob Runtime ──────────────────────────────────────────────────
FROM alpine:3.19 AS cronjob

# Create non-root user.
RUN addgroup -g 1001 cronjob && \
    adduser -D -u 1001 -G cronjob cronjob

# Runtime dependencies: TLS certs, timezone data, wget for health probe.
RUN apk add --no-cache ca-certificates tzdata wget

WORKDIR /app

COPY --from=builder /build/bin/cronjob /app/cronjob

RUN chown -R cronjob:cronjob /app

USER cronjob

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/cronjob"]
