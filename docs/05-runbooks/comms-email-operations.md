---
title: Comms Email Operations Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/comms/README.md
  - service/kotlin/comms/src/main/resources/application.yml
  - service/kotlin/comms/src/main/kotlin/io/github/salomax/neotool/comms/email/kafka/EmailTopics.kt
  - service/kotlin/comms/src/test/kotlin/io/github/salomax/neotool/comms/graphql/integration/CommsGraphQLIntegrationTest.kt
load_when:
  - bringing up the comms service locally
  - debugging email send configuration or DLQ behavior
supersedes:
  - historical comms runbook
schema: docs-metadata-v1
---

# Comms Email Operations Runbook

Purpose: operate the comms service email path using the current local runtime and
configured providers.

## Symptoms

- Comms service starts but email sends never leave the mock provider.
- SMTP sends fail after switching `COMMS_EMAIL_PROVIDER=micronaut`.
- Messages accumulate on the email DLQ topic.

## Authority

Local service runtime and endpoints come from `service/kotlin/comms/README.md`
and `service/kotlin/comms/src/main/resources/application.yml`. Kafka topic names
come from `EmailTopics`.

## Commands

```bash
# Start Kafka for local comms work
docker compose -f infra/docker/docker-compose.local.yml up -d kafka

# Service env
export KAFKA_BROKERS=localhost:9092
export COMMS_EMAIL_PROVIDER=mock
export EMAIL_FROM=noreply@invistus.com.br
export EMAIL_FROM_NAME=Invistus

# Optional SMTP provider
export COMMS_SMTP_HOST=smtp.hostinger.com
export COMMS_SMTP_PORT=587
export COMMS_SMTP_USER=...
export COMMS_SMTP_PASSWORD=...
export COMMS_SMTP_AUTH_ENABLED=true
export COMMS_SMTP_STARTTLS_ENABLED=true

# Run the service
./gradlew :comms:run
```

Endpoints:

- `http://localhost:8084/graphql`
- `http://localhost:8084/graphiql`
- `http://localhost:8084/health`
- `http://localhost:8084/prometheus`

Kafka topics:

- `comms.email.send.v1`
- `comms.email.send.dlq`

## Rollback

- If SMTP delivery fails, switch back to `COMMS_EMAIL_PROVIDER=mock` to confirm
  the GraphQL/Kafka path still works.
- If sender rejection occurs, restore a domain-valid `EMAIL_FROM` or the
  provider-approved alias.
- Do not keep replaying DLQ traffic until provider configuration is fixed.

## Verification

- Service exposes `/health` on port `8084`.
- GraphQL `requestEmailSend` accepts a valid mutation payload.
- Mock provider logs delivery locally, or SMTP provider accepts a send without
  authentication/from-address errors.
- DLQ volume does not grow after a known-good send.

## Related

- [README.md](README.md)
- [../02-development/repo-commands.md](../02-development/repo-commands.md)
- [../01-architecture/graphql-and-contracts.md](../01-architecture/graphql-and-contracts.md)
