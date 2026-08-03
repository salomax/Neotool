---
id: feature.security.authentication.email-verification
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/EmailVerificationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/EmailVerificationResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_21__add_email_verification.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthenticationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/GraphQLAuthenticationIntegrationTest.kt
  frontend:
    - web/src/app/(authentication)/verify-email/page.tsx
    - web/src/app/(authentication)/verify-email-link/page.tsx
    - web/src/shared/components/auth/VerificationPrompt.tsx
    - web/src/shared/providers/AuthProvider.tsx
  mobile: []
  workers: []
legacy_refs:
  - removed historical docs
related_specs:
  - feature.security.authentication.sign-up
  - feature.security.authentication.oauth-identity-providers
  - feature.security.authentication.sign-in
schema: spec-metadata-v1
---

# Email Verification

## Behavior

- Magic-link verification after email/password signup when `app.verification.enabled=true`.
- `EmailVerificationService.initiateVerification`: generates UUID token, stores **SHA-256 hash**, default 8h expiry (`app.verification.code-expiration-hours`), emails link; `maxAttempts` configurable (default 5).
- `verifyEmailWithToken(token)`: validates hash, expiry, already-verified paths; on success sets `user.emailVerified=true` and `emailVerifiedAt`.
- `resendVerificationEmail`: authenticated user; rate limit 3 / hour (`app.verification.resend-rate-limit-per-hour`); invalidates active record then re-initiates.
- `myVerificationStatus`: returns verified flags + active send/expiry timestamps; `canResendCode` currently always `true` in DTO (rate limit enforced on resend mutation, not status query) — `EmailVerificationResolver.myVerificationStatus`.
- Backend **does not** block sign-in/API access solely for unverified email; gating is primarily web UX.

## Invariants

- Only hashed tokens are stored.
- Expired / invalid links fail with typed security error codes.
- Resend is rate-limited per user.

## Permissions

Backend enforcement:

- `verifyEmailWithToken` is public (token is the secret).
- `resendVerificationEmail` / `myVerificationStatus` require authenticated user (wiring via principal).

UX hints:

- After signup/sign-in, web redirects unverified users to `/verify-email`.
- Verify-email-link page consumes token from URL.

## Data And Contracts

- GraphQL: `verifyEmailWithToken`, `resendVerificationEmail`, `myVerificationStatus`, related payload types.
- DB: `email_verifications` (+ user `emailVerified` columns from verification migrations).

## Impact Surface

- Backend: `EmailVerificationService`, `EmailVerificationResolver`, signup hook in `AuthenticationService`
- GraphQL: verification operations
- Database: email_verifications, users verification columns
- Tests: covered indirectly via AuthenticationService / GraphQL auth; **no dedicated EmailVerificationService unit/integration suite found**
- Frontend: verify-email pages + AuthProvider redirect
- Mobile / Workers: none

## Decisions

- Magic link only (no OTP code UI path in current service).
- Soft-fail initiate during signup so user creation is not aborted.

## Known Gaps

- Dedicated service-level tests for verify/resend/expiry are thin/missing.
- OAuth IdP extracts Google `email_verified` but does not set `User.emailVerified` — OAuth users may be treated as unverified by web.
- Status query `canResendCode` does not reflect rate-limit state.
- Bruno collection lacks verification operations.
- Duplicate historical migrations (V0_17/18/21) — operational cleanup not in scope here.

## Legacy Notes

- Migration comments cite obsolete design doc under `removed historical docs`.
