# Comms Service

Communication service for Neotool platform. The current implementation focuses on asynchronous email delivery with Kafka orchestration, provider abstraction, DLQ handling, and a multi-channel template engine (with email templates in use today).

## Quick Start

### Local Development (Mock Email Provider)

```bash
# From repository root: start Kafka
docker compose -f infra/docker/docker-compose.local.yml up -d kafka

# From service/kotlin
cd service/kotlin
export KAFKA_BROKERS=localhost:9094
export COMMS_EMAIL_PROVIDER=mock
export EMAIL_FROM=noreply@neotool.com
export EMAIL_FROM_NAME=Neotool

# JWT validation (recommended for authenticated GraphQL usage)
export JWT_JWKS_URL=http://localhost:8080/.well-known/jwks.json

./gradlew :comms:run
```

Service endpoints:
- `http://localhost:8084/graphql`
- `http://localhost:8084/graphiql`
- `http://localhost:8084/health`
- `http://localhost:8084/prometheus`

Notes:
- `KAFKA_BROKERS` is required.
- For host-machine Kafka access with `infra/docker/docker-compose.local.yml`, use `localhost:9094`.
- For container-to-container access, use `kafka:9092`.

## Configuration

All configuration is managed via environment variables and `src/main/resources/application.yml`.

### Required

- `KAFKA_BROKERS`: Kafka bootstrap servers.

### Authentication / Authorization

GraphQL operations are authenticated and permission-gated.

- Required permission for email send: `comms:email:send`
- JWT validation options:
  - `JWT_JWKS_URL` (recommended): JWKS endpoint from the security service
  - or `JWT_PUBLIC_KEY` / `JWT_PUBLIC_KEY_PATH`

### Email Provider

- `COMMS_EMAIL_PROVIDER`: `mock` (default) or `micronaut`
- `EMAIL_FROM`: sender email (default: `noreply@neotool.com`)
- `EMAIL_FROM_NAME`: sender display name (default: `neotool`)

### SMTP (when `COMMS_EMAIL_PROVIDER=micronaut`)

- `JAVAMAIL_ENABLED` (default: `true`)
- `COMMS_SMTP_HOST`
- `COMMS_SMTP_PORT` (default: `587`)
- `COMMS_SMTP_USER`
- `COMMS_SMTP_PASSWORD`
- `COMMS_SMTP_AUTH_ENABLED` (default: `true`)
- `COMMS_SMTP_STARTTLS_ENABLED` (default: `true`)

### Template Engine

- `COMMS_DEFAULT_LOCALE` (default: `en`)
- `COMMS_TEMPLATE_HOT_RELOAD` (default: `false`)

### Database / Pool (currently not used by active flows)

- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `HIKARI_MAX_POOL_SIZE`, `HIKARI_MIN_IDLE`, `HIKARI_CONNECTION_TIMEOUT`, `HIKARI_IDLE_TIMEOUT`, `HIKARI_MAX_LIFETIME`, `HIKARI_LEAK_DETECTION`, `HIKARI_VALIDATION_TIMEOUT`

Current defaults:
- `jpa.default.enabled=false`
- `flyway.datasources.default.enabled=false`

## Architecture

High-level email flow:

1. `requestEmailSend` GraphQL mutation receives an email request.
2. If `content.kind=TEMPLATE`, template rendering happens synchronously in `KafkaEmailSendService`.
3. Service publishes `EMAIL_SEND_REQUESTED` to Kafka topic `comms.email.send.v1`.
4. `EmailSendConsumer` processes the event via `EmailSendProcessor`.
5. Processor validates payload and dispatches to configured provider (`mock` / `micronaut`).
6. Failures go through retry policy and may be published to `comms.email.send.dlq`.
7. `EmailDlqConsumer` logs DLQ events for operational visibility.

Core topics:
- Main: `comms.email.send.v1`
- DLQ: `comms.email.send.dlq`

## Project Structure

```text
comms/
├── src/main/kotlin/io/github/salomax/neotool/comms/
│   ├── Application.kt
│   ├── graphql/
│   │   ├── CommsGraphQLFactory.kt
│   │   ├── CommsWiringFactory.kt
│   │   └── resolvers/
│   ├── email/
│   │   ├── dto/
│   │   ├── service/
│   │   ├── consumer/
│   │   ├── processor/
│   │   ├── provider/
│   │   ├── kafka/
│   │   └── metrics/
│   ├── template/
│   │   ├── domain/
│   │   ├── registry/
│   │   ├── renderer/
│   │   ├── substitution/
│   │   └── service/
│   └── domain/rbac/CommsPermissions.kt
├── src/main/resources/
│   ├── application.yml
│   ├── graphql/schema.graphqls
│   └── templates/email/
└── src/test/
```

## GraphQL API

Current schema exposes one mutation:

- `requestEmailSend(input: EmailSendRequestInput!): EmailSendResult!`

`EmailSendResult.status` currently returns:
- `QUEUED`

### RAW Content Example

```graphql
mutation RequestEmailSend($input: EmailSendRequestInput!) {
  requestEmailSend(input: $input) {
    requestId
    status
  }
}
```

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

### TEMPLATE Content Example

```json
{
  "input": {
    "to": "user@example.com",
    "content": {
      "kind": "TEMPLATE",
      "templateKey": "user.welcome",
      "locale": "pt-BR",
      "variables": {
        "userName": "Salomax",
        "verificationUrl": "https://app.neotool.io/verify?token=abc123"
      }
    }
  }
}
```

Auth header example:

```bash
Authorization: Bearer <jwt-with-comms:email:send>
```

## Templates

Templates live under:

- `src/main/resources/templates/{channel}/{template-key}/template.yml`

Email templates currently available:

- `user.welcome`
- `order.confirmation`
- `auth.password-reset`
- `auth.verify-email`

Template capabilities:
- Mustache variable substitution (`{{var}}`)
- Required/optional variable definitions
- Locale fallback chain: exact locale -> language-only -> default locale
- Email HTML processing with CSS inlining

Minimal `template.yml` shape:

```yaml
key: user.welcome
channel: EMAIL
metadata:
  name: "User Welcome Email"
variables:
  - name: userName
    type: STRING
    required: true
locales:
  en:
    subject: "Welcome, {{userName}}"
    bodyPath: "body.en.html"
defaultLocale: en
channelConfig:
  email:
    format: HTML
    inlineCss: true
```

## Testing

```bash
# Unit tests only
./gradlew :comms:testUnit

# Integration tests (Testcontainers + Docker required)
./gradlew :comms:testIntegration

# Full verification
./gradlew :comms:check
```

## Observability

Prometheus metrics include:
- `comms.email.processed`
- `comms.email.processing.duration`
- `comms.email.dlq.count`
- `comms.email.retry.count`
- `comms.email.error.count`
- `comms.email.dlq.publish.failure`

## Troubleshooting

- Startup fails with Kafka config error:
  - Set `KAFKA_BROKERS` (for local docker compose, `localhost:9094`).
- `Email provider not found`:
  - Check `COMMS_EMAIL_PROVIDER` value (`mock` or `micronaut`).
- GraphQL returns authorization errors:
  - Provide `Authorization: Bearer <token>` and include permission `comms:email:send`.
- TEMPLATE request fails due missing fields:
  - Ensure `templateKey`, `locale`, and required variables are provided.
- SMTP send failures:
  - Verify `COMMS_SMTP_*` values and `EMAIL_FROM` is allowed by your SMTP provider.

## References

- [Comms Module Requirements](../../../docs/03-features/comms/comms-module-requirements.md)
- [Comms Runbook](../../../docs/03-features/comms/comms-runbook.md)
- [Template Authoring Guide](../../../docs/03-features/comms/template-authoring-guide.md)
- [Template Engine Requirements](../../../docs/03-features/comms/template-engine-requirements.md)
- [Password Reset Template Implementation](../../../docs/03-features/comms/password-reset-template-implementation.md)
