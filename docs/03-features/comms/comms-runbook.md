# Comms Service

## Overview
- Central service for synchronous and asynchronous communications.
- Current implementation focuses on **email** (Kafka-backed orchestration + DLQ) and GraphQL API.
- Other channels and template engine are planned but not implemented yet.

## How to Run (Local)

1) Start Kafka:
```bash
docker compose -f infra/docker/docker-compose.local.yml up -d kafka
```

2) Configure environment:
```bash
export KAFKA_BROKERS=localhost:9092
export COMMS_EMAIL_PROVIDER=mock   # or micronaut
export EMAIL_FROM=invitus@invistus.com.br
export EMAIL_FROM_NAME=Invistus
```

3) Run service:
```bash
./gradlew :comms:run
```

Service URL: `http://localhost:8084`  
GraphQL: `http://localhost:8084/graphql`

## Email Configuration

### Provider
`COMMS_EMAIL_PROVIDER`:
- `mock` (default) → logs email to console.
- `micronaut` → sends via SMTP (JavaMail).

### SMTP (Micronaut provider)
Set the following environment variables:
```bash
export COMMS_SMTP_HOST=smtp.hostinger.com
export COMMS_SMTP_PORT=587
export COMMS_SMTP_USER=...
export COMMS_SMTP_PASSWORD=...
export COMMS_SMTP_AUTH_ENABLED=true
export COMMS_SMTP_STARTTLS_ENABLED=true
```

### From Address
The sender is configured via:
```bash
export EMAIL_FROM=noreply@invistus.com.br
export EMAIL_FROM_NAME=Invistus
```

**Important:** SMTP providers only accept `EMAIL_FROM` addresses that are valid on your domain.  
If you want `noreply@...`, create an alias for it in your email provider (it can be a non-delivery/blackhole alias).

## Sending an Email (GraphQL)

Mutation:
```graphql
mutation RequestEmailSend($input: EmailSendRequestInput!) {
  requestEmailSend(input: $input) {
    requestId
    status
  }
}
```

Variables:
```json
{
  "input": {
    "to": "user@example.com",
    "content": {
      "kind": "RAW",
      "format": "TEXT",
      "subject": "Hello",
      "body": "World",
      "variables": {}
    }
  }
}
```

## Notes
- JPA/Flyway are disabled for now in `comms` until persistence is needed.
- Template engine and HTML rendering will be added in Phase 3.
- DLQ handling is active; reprocessing is manual for now.
