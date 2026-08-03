---
id: feature.security.authentication.password-reset
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/rate/RateLimitService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityAuthResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/email/CommsEmailService.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_3__add_password_reset.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/PasswordResetServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/RateLimitServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/PasswordResetIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/PasswordResetGraphQLIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/PasswordResetEmailIntegrationTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Request Password Reset.bru
    - contracts/api-collections/bruno/collections/supergraph/Security/Reset Password.bru
  frontend:
    - web/src/app/(authentication)/forgot-password/page.tsx
    - web/src/app/(authentication)/reset-password/page.tsx
    - web/src/shared/components/auth/ForgotPasswordForm.tsx
    - web/src/shared/components/auth/ResetPasswordForm.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authentication.sign-in
  - feature.security.authentication.oauth-identity-providers
schema: spec-metadata-v1
---

# Password Reset

## Behavior

- `requestPasswordReset(email, locale?)`:
  - Always returns success payload with generic message (email existence, validation failure, exceptions, and rate-limit are not revealed to client) — `SecurityAuthResolver.requestPasswordReset`.
  - Service: rate-limit check first (3 attempts / hour / email via `RateLimitService`); still returns true when limited.
  - Missing user or user without `passwordHash` (OAuth-only): no-op success.
  - Otherwise generates UUID reset token, 1h expiry on user row, clears prior used flag, emails link via `EmailService` (comms or mock); email send errors do not fail the mutation.
- `resetPassword(token, newPassword)`:
  - Validates token exists, not expired, not already used.
  - Enforces same password strength rules as signup.
  - Hashes new password; marks token used; clears token fields.
  - Invalid token → `PASSWORD_RESET_TOKEN_INVALID`; weak password → `USER_PASSWORD_TOO_WEAK`.

## Invariants

- Request path must not disclose account existence or rate-limit state.
- Reset tokens expire after 1 hour and are single-use.
- OAuth-only accounts cannot obtain a usable reset (no password hash) but clients still see success on request.

## Permissions

Backend enforcement:

- Both mutations are public.
- No auth principal required.

UX hints:

- Forgot/reset password pages call the GraphQL mutations; success messaging mirrors backend generic copy.

## Data And Contracts

- GraphQL: `RequestPasswordResetInput`, `RequestPasswordResetPayload`, `ResetPasswordInput`, `ResetPasswordPayload`.
- DB: user password reset columns (`V0_3`); password_reset_attempts for rate limit.

## Impact Surface

- Backend: `AuthenticationService.requestPasswordReset` / `resetPassword` / `validateResetToken`, `RateLimitService`, email services
- GraphQL: request/reset mutations
- Database: users reset columns, attempt table
- Tests: unit + GraphQL/integration/email; Bruno; RateLimitServiceTest
- Frontend: forgot-password / reset-password flows
- Mobile / Workers: none

## Decisions

- Security-by-obscurity response on request (always success).
- Token stored on user entity (not hashed) — current implementation.

## Known Gaps

- Reset token stored in plaintext on user row (vs hashed email-verification tokens).
- No dedicated web E2E for password reset happy path (backend coverage is strong).

## Legacy Notes

- None used as authority.
