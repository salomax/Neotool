FROM golang:1.25-alpine AS builder

RUN apk add --no-cache ca-certificates

WORKDIR /app/financial_data/ratings_job

# Copy dependency manifests first to maximize cache reuse for go mod download.
COPY financial_data/ratings_job/go.mod financial_data/ratings_job/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# Copy service source only after dependency resolution.
COPY financial_data/ratings_job ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /bin/cronjob ./cmd/cronjob

FROM gcr.io/distroless/static-debian12:nonroot

WORKDIR /

COPY --from=builder /bin/cronjob /cronjob

USER nonroot:nonroot

ENTRYPOINT ["/cronjob"]
