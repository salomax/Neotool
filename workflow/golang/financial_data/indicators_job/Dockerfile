FROM golang:1.24-alpine AS builder

WORKDIR /app

RUN apk add --no-cache ca-certificates

# Copy entire golang workspace to include shared modules
COPY . .

# Set working directory to the service
WORKDIR /app/financial_data/indicators_job

RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /bin/cronjob ./cmd/cronjob

FROM gcr.io/distroless/static-debian12:nonroot

WORKDIR /

COPY --from=builder /bin/cronjob /cronjob

USER nonroot:nonroot

ENTRYPOINT ["/cronjob"]
