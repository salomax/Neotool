# Multi-stage Dockerfile for b3-cotahist-job
# Single binary: cronjob
# Requires headless Chromium (go-rod) to download files from B3's Cloudflare CDN.

# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM golang:1.24-alpine AS builder

RUN apk add --no-cache git ca-certificates tzdata

WORKDIR /build/financial_data/b3_cotahist_job

# Copy shared modules that are referenced via replace directives.
COPY shared/workerpool /build/shared/workerpool
COPY shared/health     /build/shared/health

# Copy dependency manifests first to maximise cache reuse for go mod download.
COPY financial_data/b3_cotahist_job/go.mod financial_data/b3_cotahist_job/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# Copy service source after dependency resolution.
COPY financial_data/b3_cotahist_job ./

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
RUN addgroup -g 1000 cronjob && \
    adduser -D -u 1000 -G cronjob cronjob

# Runtime dependencies: Chromium for B3 CDN downloads, TLS certs, timezone data, wget for health probe.
# --disable-dev-shm-usage is set in code so Chrome writes to /tmp instead of /dev/shm.
# --no-sandbox / --disable-setuid-sandbox are also set in code (container-safe flags).
RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    wget \
    chromium \
    chromium-chromedriver \
    nss \
    freetype \
    harfbuzz

WORKDIR /app

COPY --from=builder /build/bin/cronjob /app/cronjob

RUN chown -R cronjob:cronjob /app

USER cronjob

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/cronjob"]
