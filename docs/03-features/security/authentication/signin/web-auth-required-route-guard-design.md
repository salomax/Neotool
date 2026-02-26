---
title: Web Auth-Required Route Guard - Design and Industry Research
type: feature
category: authentication
status: draft
version: 0.1.0
tags: [authentication, web, signin, route-guard, redirect, nextjs, security]
ai_optimized: true
search_keywords: [protected route, login required, redirect after login, next parameter, open redirect, nextjs proxy]
related:
  - 03-features/security/authentication/signin/signin.feature
  - 03-features/security/authentication/README.md
  - 03-features/security/resource-ownership/account-resource-ownership-requirements.md
last_updated: 2026-02-26
---

# Web Auth-Required Route Guard - Design and Industry Research

> **Purpose**: Define a secure and user-friendly flow for web routes that require authentication, including redirecting users to sign-in and returning them to the original destination after successful login.

## Research Snapshot

- **Research date**: February 26, 2026
- **Scope**: Next.js 16 guidance, OAuth security guidance, and common redirect patterns used by auth platforms in production systems.

## Problem Statement

In the web module, unauthenticated users can attempt to access pages that should only be available after login. The product requirement is:

1. Detect login-required page access.
2. Redirect unauthenticated users to sign-in.
3. After successful sign-in, route them back to the page they originally requested.

## Current State in Invistus Web

Observed current behavior in `web`:

- The sign-in success flow redirects to `/` by default.
- Forced auth failures also redirect to `/signin`, without preserving the original target.
- There is no centralized protected-route guard yet (no `proxy.ts` guard for route-level auth checks).
- Auth state is client-side (`localStorage` / `sessionStorage` token), which limits server-side route checks.

Implementation references:

- `web/src/shared/providers/AuthProvider.tsx`
- `web/src/app/(authentication)/signin/page.tsx`
- `web/src/lib/graphql/client.ts`
- `web/src/shared/utils/auth.ts`

## 2026 Industry Findings

### Finding 1: Redirect-back flow is the default UX standard

Modern auth systems preserve user intent by carrying a return target (for example `next` or `redirect_url`) and using it after successful authentication.

Implication for Invistus:

- Implement `signin?next=...` (or equivalent) with strict validation.
- Fallback to `/` only when no valid target exists.

### Finding 2: Route interception is useful, but only for optimistic checks

Next.js 16 recommends `proxy.ts` for centralized request interception and redirects. It also warns that proxy checks should be lightweight and not replace data-layer authorization.

Implication for Invistus:

- Use `proxy.ts` for fast redirect decisions.
- Keep real authorization checks in backend/data-access layers.

### Finding 3: Redirect targets must be validated to prevent open redirects

OWASP and OAuth BCP recommend never trusting user-provided redirect destinations. Open redirectors are a known phishing and token-leak vector.

Implication for Invistus:

- Accept only internal relative paths.
- Reject absolute URLs and protocol-relative URLs.
- Apply allowlist/prefix checks for protected targets when possible.

### Finding 4: OAuth flows should preserve state safely

OAuth/OIDC guidance recommends using `state` (nonce + state mapping) for CSRF protection and restoring user intent.

Implication for Invistus:

- For OAuth sign-in, preserve return target in a state-bound payload (or server/client storage keyed by nonce), not only raw query params.

## Recommended Solution for Invistus

### 1. Route Policy Model

Define route categories:

- `PUBLIC`: no login required.
- `AUTH_REQUIRED`: user must be logged in.
- `AUTH_ONLY`: pages intended only for signed-out users (for example `/signin`, `/signup`).

Initial recommendation:

- `AUTH_REQUIRED`: `/settings`, `/personal-finance`, `/personal-finance/finance-transactions`, `/profile` (when present).
- `AUTH_ONLY`: `/signin`, `/signup`, `/forgot-password`, `/reset-password`.
- `PUBLIC`: landing page and public data pages unless business explicitly changes them.

### 2. Redirect Contract

When anonymous user requests protected page:

1. Capture full relative destination path (`pathname + search`).
2. Redirect to `/signin?next=<urlEncodedRelativePath>`.

After successful sign-in:

1. Read `next`.
2. Validate and normalize.
3. Route to validated target.
4. Fallback to `/` if invalid/missing.

### 3. Redirect Target Validation Rules

Accept only values that satisfy all conditions:

- Starts with `/`.
- Does not start with `//`.
- Does not contain scheme (`http://`, `https://`, `javascript:` and similar).
- Does not point to `AUTH_ONLY` pages.
- Optional: matches known application route prefixes.

If validation fails, use `/`.

### 4. Guard Placement Strategy

Two-phase approach aligned to current architecture:

### Phase A (immediate, low risk)

- Implement client-side guard/redirect contract:
  - Route classification utility.
  - Sign-in page reads/uses `next`.
  - AuthProvider sign-in uses validated `next`.

### Phase B (hardening)

- Introduce `web/src/proxy.ts` for optimistic redirects.
- Use cookie-based session hint for server-visible auth presence.
- Keep backend as source of truth for secure authz checks.

Note: since current auth token lives in browser storage, `proxy.ts` cannot validate it directly without a cookie/session migration.

## Security Requirements

1. Prevent open redirect vulnerabilities (CWE-601).
2. Prevent redirect loops:
   - `/signin -> /signin`
   - `AUTH_ONLY -> AUTH_ONLY` after login.
3. Keep authorization enforcement at API/data layer.
4. Preserve OAuth state/nonce semantics when round-tripping return target.
5. Log suspicious redirect attempts (`invalid_next_param`) for monitoring.

## User Experience Requirements

1. Preserve user intent for direct URL entry and deep links.
2. Preserve query parameters for legitimate destinations.
3. Apply same post-login behavior for password and OAuth login.
4. If session expires while user is on protected page, send back to same page after re-login.

## Non-Goals

- Replacing backend authorization with frontend guards.
- Designing full role/permission matrix (already covered by security specs).
- Rewriting existing JWT/token architecture in this feature alone.

## Acceptance Criteria (Feature-Level)

- Unauthenticated user accessing an `AUTH_REQUIRED` page is redirected to sign-in.
- After successful sign-in, user lands on originally requested page.
- Invalid/malicious `next` values are ignored safely with fallback `/`.
- OAuth and password sign-in flows behave consistently for redirect-back.
- E2E coverage exists for happy path and abuse cases.

## Sources

Research used on February 26, 2026:

1. Next.js Authentication Guide (App Router): https://nextjs.org/docs/app/guides/authentication
2. Next.js Proxy Getting Started: https://nextjs.org/docs/app/getting-started/proxy
3. Next.js Proxy File Convention (`proxy.ts`): https://nextjs.org/docs/app/api-reference/file-conventions/proxy
4. RFC 9700 (OAuth 2.0 Security BCP): https://www.rfc-editor.org/rfc/rfc9700
5. OWASP Unvalidated Redirects Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html
6. Auth0 State Parameter Guidance: https://auth0.com/docs/secure/attack-protection/state-parameters
7. Auth0 Redirect Users After Login: https://auth0.com/docs/authenticate/login/redirect-users-after-login
8. Clerk Redirect URL Pattern: https://clerk.com/docs/guides/development/customize-redirect-urls
9. Supabase Redirect URL Allowlist Guidance: https://supabase.com/docs/guides/auth/redirect-urls
