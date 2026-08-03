---
id: feature.security.oauth2-authorization-server.oauth2-rate-limits
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenRateLimitService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenRateLimitConfig.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenRateLimitCounterRepository.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/oauth2/OAuth2TokenExchangeRateLimiter.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2TokenController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/observability/SecurityMetrics.kt
    - service/kotlin/security/src/main/resources/application.yml
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_39__create_oauth2_token_rate_limit_counters.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/oauth2/OAuth2TokenRateLimitServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/OAuth2TokenRateLimitIntegrationTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.oauth2-authorization-server.token-grants-refresh-exchange
  - feature.security.oauth2-authorization-server.dynamic-client-registration
schema: spec-metadata-v1
---

# OAuth2 AS — Rate Limits

## Behavior

### Token endpoint (`POST /oauth2/token`)

- Before grant handling, `OAuth2TokenService` resolves a client id (from `client_id` or OAuth refresh token metadata) and calls `OAuth2TokenRateLimitService.check`.
- Default config (`OAuth2TokenRateLimitConfig` / `application.yml`): **limit 120** per **windowMs 3_600_000** (1 hour), overridable via env.
- Counters persisted in DB (V0_39); aligned fixed windows; on exceed → `OAuth2RateLimitException` → HTTP **429** with `Retry-After` and OAuth error body.
- Metric: `SecurityMetrics.incrementOAuth2TokenRateLimitExceeded`.
- Applies to authorization_code, refresh_token, and token-exchange when client id can be resolved.

### Token exchange additional limiter

- `InMemoryOAuth2TokenExchangeRateLimiter` (config `oauth.authorization-server.token-exchange.rate-limit-per-minute`, default 60) checked inside `OAuth2TokenExchangeService` per service client id — separate from DB token-endpoint limiter.

### Not rate-limited here

- `POST /oauth2/register` (DCR) has no equivalent limiter in this code path.
- App GraphQL auth / password-reset rate limits are unrelated.

## Invariants

- Empty client id fails rate-limit check with invalid request.
- Denied decisions still return remaining/reset metadata internally; HTTP exposes Retry-After.

## Permissions

Backend enforcement: rate limit is availability/abuse control, not RBAC.

UX hints: none.

## Data And Contracts

- HTTP 429 on `/oauth2/token`.
- DB rate-limit counters table.
- No GraphQL.

## Impact Surface

- Backend: rate limit service/repo/config, token controller, exchange limiter, metrics
- Tests: unit + integration rate-limit tests
- Bruno: none

## Decisions

- Persist token-endpoint counters for multi-instance correctness; keep exchange limiter in-memory (per-node).

## Known Gaps

- DCR open registration without rate limit.
- In-memory exchange limiter does not coordinate across replicas.
- No Bruno exercise of 429 path.

## Legacy Notes

- None used as authority.
