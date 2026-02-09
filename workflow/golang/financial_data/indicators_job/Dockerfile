FROM golang:1.24-alpine AS builder

WORKDIR /app

RUN apk add --no-cache ca-certificates

# Copy entire golang workspace to include shared modules
COPY . .

# Set working directory to the service
WORKDIR /app/financial_data/indicators_job

RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /bin/cronjob ./cmd/cronjob

FROM alpine:3.19

RUN addgroup -g 1001 cronjob && \
    adduser -D -u 1001 -G cronjob cronjob

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /bin/cronjob /app/cronjob

RUN chown -R cronjob:cronjob /app

USER cronjob

ENTRYPOINT ["/app/cronjob"]
