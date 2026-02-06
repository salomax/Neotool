# Multi-stage Dockerfile for bacen-ifdata Go application
# Builds 3 binaries: cronjob, retry-worker, cli

# Stage 1: Builder
FROM golang:1.22-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git ca-certificates tzdata

WORKDIR /build

# Allows building from repo root by overriding APP_DIR
ARG APP_DIR=.

# Copy source code (full build context)
COPY . ./

# Use the app directory inside the build context
WORKDIR /build/${APP_DIR}

# Download dependencies
RUN go mod download

# Build binaries with optimizations
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /build/bin/cronjob \
    ./cmd/cronjob

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /build/bin/retry-worker \
    ./cmd/retry-worker

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /build/bin/cli \
    ./cmd/cli

# Stage 2: Cronjob Runtime
FROM alpine:3.19 AS cronjob

# Create non-root user
RUN addgroup -g 1000 cronjob && \
    adduser -D -u 1000 -G cronjob cronjob

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata wget

WORKDIR /app

# Copy binary from builder
COPY --from=builder /build/bin/cronjob /app/cronjob

# Set ownership
RUN chown -R cronjob:cronjob /app

# Switch to non-root user
USER cronjob

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run cronjob
ENTRYPOINT ["/app/cronjob"]

# Stage 3: Retry Worker Runtime
FROM alpine:3.19 AS retry-worker

# Create non-root user
RUN addgroup -g 1000 worker && \
    adduser -D -u 1000 -G worker worker

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata wget

WORKDIR /app

# Copy binary from builder
COPY --from=builder /build/bin/retry-worker /app/retry-worker

# Set ownership
RUN chown -R worker:worker /app

# Switch to non-root user
USER worker

# Expose health check port (different from cronjob)
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/health || exit 1

# Run retry worker
ENTRYPOINT ["/app/retry-worker"]

# Stage 4: CLI Runtime
FROM alpine:3.19 AS cli

# Create non-root user
RUN addgroup -g 1000 cli && \
    adduser -D -u 1000 -G cli cli

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

# Copy binary from builder
COPY --from=builder /build/bin/cli /app/cli

# Set ownership
RUN chown -R cli:cli /app

# Switch to non-root user
USER cli

# Run CLI
ENTRYPOINT ["/app/cli"]
