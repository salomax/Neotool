# Multi-stage Dockerfile for institution enhancement consumer

# Stage 1: Build
FROM golang:1.23-alpine AS builder

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache git ca-certificates tzdata

# Copy entire golang workspace to include shared modules
COPY . .

# Set working directory to the service
WORKDIR /app/financial_data/institution_enhancement_consumer

RUN go mod download

# Build consumer binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /bin/consumer \
    ./cmd/consumer

# Build standalone binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /bin/standalone \
    ./cmd/standalone

# Build DLQ consumer binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /bin/dlq-consumer \
    ./cmd/dlq-consumer

# Build DLQ inspector binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-s -w" \
    -o /bin/dlq-inspector \
    ./cmd/dlq-inspector

# Stage 2: Consumer Runtime
FROM alpine:3.19 AS consumer

RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

RUN apk add --no-cache ca-certificates tzdata wget

COPY --from=builder --chown=consumer:consumer /bin/consumer /app/consumer
COPY --from=builder --chown=consumer:consumer /app/financial_data/institution_enhancement_consumer/internal/data/websites.csv /app/internal/data/websites.csv

USER consumer
WORKDIR /app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/consumer"]

# Stage 3: Standalone Runtime
FROM alpine:3.19 AS standalone

RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder --chown=consumer:consumer /bin/standalone /app/standalone

USER consumer
WORKDIR /app

ENTRYPOINT ["/app/standalone"]

# Stage 4: DLQ Consumer Runtime
FROM alpine:3.19 AS dlq-consumer

RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

RUN apk add --no-cache ca-certificates tzdata wget

COPY --from=builder --chown=consumer:consumer /bin/dlq-consumer /app/dlq-consumer

USER consumer
WORKDIR /app
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/health || exit 1

ENTRYPOINT ["/app/dlq-consumer"]

# Stage 5: DLQ Inspector Runtime
FROM alpine:3.19 AS dlq-inspector

RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder --chown=consumer:consumer /bin/dlq-inspector /app/dlq-inspector

USER consumer
WORKDIR /app

ENTRYPOINT ["/app/dlq-inspector"]
