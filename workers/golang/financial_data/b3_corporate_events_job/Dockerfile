# Multi-stage Dockerfile for b3-corporate-events-job

# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM golang:1.25-alpine AS builder

RUN apk add --no-cache git ca-certificates tzdata

WORKDIR /build/financial_data/b3_corporate_events_job

COPY shared/workerpool /build/shared/workerpool
COPY shared/health     /build/shared/health

COPY financial_data/b3_corporate_events_job/go.mod financial_data/b3_corporate_events_job/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY financial_data/b3_corporate_events_job ./

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /build/bin/cronjob \
    ./cmd/cronjob

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM alpine:3.19

RUN addgroup -g 1000 cronjob && \
    adduser -D -u 1000 -G cronjob cronjob

RUN apk add --no-cache ca-certificates tzdata wget

WORKDIR /app

COPY --from=builder /build/bin/cronjob /app/cronjob

RUN chown -R cronjob:cronjob /app

USER cronjob

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/cronjob"]
