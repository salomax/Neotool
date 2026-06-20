FROM golang:1.25-alpine AS builder

WORKDIR /app

RUN apk add --no-cache ca-certificates

# Copy shared local modules required by go.mod replace directives.
COPY shared/health ./shared/health
COPY shared/workerpool ./shared/workerpool

# Copy dependency manifests first to maximize cache reuse for go mod download.
COPY financial_data/indicators_job/go.mod financial_data/indicators_job/go.sum ./financial_data/indicators_job/

WORKDIR /app/financial_data/indicators_job
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

# Copy service source only after dependency resolution.
COPY financial_data/indicators_job ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOMAXPROCS=2 go build -p=1 -ldflags="-s -w" -o /bin/cronjob ./cmd/cronjob

FROM alpine:3.19

RUN addgroup -g 1001 cronjob && \
    adduser -D -u 1001 -G cronjob cronjob

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /bin/cronjob /app/cronjob

RUN chown -R cronjob:cronjob /app

USER cronjob

ENTRYPOINT ["/app/cronjob"]
