---
id: feature.security.authentication.sign-up
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityAuthResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/EmailVerificationService.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_2__add_password_to_users.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_21__add_email_verification.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthenticationServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AuthenticationServiceIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/GraphQLAuthenticationIntegrationTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Sign Up.bru
  frontend:
    - web/src/shared/providers/AuthProvider.tsx
    - web/src/shared/components/auth/SignUpForm.tsx
    - web/src/app/(authentication)/signup/page.tsx
  mobile: []
  workers: []
legacy_refs:
  - removed historical docs
related_specs:
  - feature.security.authentication.email-verification
  - feature.security.session-tokens.access-and-refresh-tokens
  - feature.security.authentication.sign-in
schema: spec-metadata-v1
---

# Sign Up (email/password)

## Behavior

- `signUp(input: SignUpInput!)` registers via `AuthenticationService.registerUser(name, email, password, locale)`.
- Rejects duplicate email with `USER_EMAIL_ALREADY_EXISTS` (`ConflictException`).
- Password must pass strength rules (`AuthenticationService.validatePasswordStrength`): min length 8, uppercase, lowercase, digit, special char.
- Password hashed with Argon2id; user created with `emailVerified = false`.
- Creates enabled user principal; enrolls in default group `"Personal Users"` when present.
- If no pending invitations for the email: creates personal tenant account + membership; if pending invites exist: binds them and skips personal account (`AuthenticationService.registerUser`).
- When `app.verification.enabled` is true (default): initiates email verification; `requiresVerification=true` on success path. Verification send failure still leaves user created (`requiresVerification` may stay false if initiate throws).
- Resolver always issues access JWT **and** refresh token after signup (`SecurityAuthResolver.signUp`) — does not consult `keepMeConnected` / `rememberMe`.

## Invariants

- Email uniqueness enforced before insert.
- Weak passwords never persist.
- New password users start unverified when verification is enabled.
- Signup returns tokens so the client can be authenticated immediately (verification is soft/UX gated).

## Permissions

Backend enforcement:

- Public mutation; no prior auth.
- Default group membership grants inherited RBAC permissions for the new access token.

UX hints:

- Web stores tokens; if `requiresVerification` or `emailVerified === false`, redirects to `/verify-email`.

## Data And Contracts

- GraphQL: `SignUpInput { name, email, password, locale }`, `SignUpPayload { token, refreshToken, user, requiresVerification }`.
- Config: `app.verification.enabled` (default true).

## Impact Surface

- Backend: `AuthenticationService.registerUser`, `EmailVerificationService.initiateVerification`, account membership helpers, default group join
- GraphQL: `signUp`
- Database: `security.users`, principals, memberships/accounts, email_verifications, group_memberships
- Tests: unit + integration auth; Bruno Sign Up
- Frontend: signup form/page, `AuthProvider.signUp`
- Mobile / Workers: none

## Decisions

- Invite-before-signup skips personal account to avoid v1 1:1 membership constraint conflicts.
- Soft-fail verification email so registration is not rolled back on mail errors.

## Known Gaps

- No dedicated E2E asserting invite-before-signup vs personal account creation paths in web.
- Signup always creates refresh token (unlike sign-in remember-me gating).

## Legacy Notes

- Flyway comments reference obsolete email-verification design under `removed historical docs` (not authoritative).
