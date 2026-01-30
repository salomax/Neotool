FROM golang:1.24-alpine AS builder

WORKDIR /app

RUN apk add --no-cache ca-certificates

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /app/bin/cronjob ./cmd/cronjob

FROM gcr.io/distroless/static-debian12:nonroot

WORKDIR /

COPY --from=builder /app/bin/cronjob /cronjob

USER nonroot:nonroot

ENTRYPOINT ["/cronjob"]
