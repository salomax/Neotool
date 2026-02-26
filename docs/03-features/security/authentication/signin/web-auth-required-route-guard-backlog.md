---
title: Web Auth-Required Route Guard Implementation Backlog
type: backlog
category: authentication
status: draft
version: 0.1.0
tags: [authentication, web, signin, backlog, route-guard, redirect]
ai_optimized: true
search_keywords: [protected routes backlog, login required backlog, signin next param backlog, open redirect prevention]
related:
  - 03-features/security/authentication/signin/web-auth-required-route-guard-design.md
  - 03-features/security/authentication/signin/signin.feature
  - 03-features/security/authentication/README.md
  - 08-workflows/feature-development.md
last_updated: 2026-02-26
---

# Web Auth-Required Route Guard Implementation Backlog

> **Purpose**: Break down implementation work for login-required route handling and secure post-login redirect in the web module.

## Overview

This backlog implements the flow:

1. Anonymous user requests protected route.
2. User is redirected to `/signin` with intended target.
3. After authentication, user is returned to intended route if valid.
4. Malicious/invalid redirect targets are rejected safely.

## Milestone Plan

| Milestone | Scope | Exit Condition |
|-----------|-------|----------------|
| M0 | Spec alignment and route policy | Protected-route policy and redirect contract are documented and approved |
| M1 | Redirect utility foundation | Shared redirect parser/validator implemented with unit tests |
| M2 | Sign-in flow integration | Password + OAuth sign-in use validated return target |
| M3 | Route guard enforcement | Protected routes redirect anonymous users with captured target |
| M4 | Hardening and rollout | Observability, E2E tests, rollout guardrails, and rollback plan complete |

---

## M0: Spec Alignment and Route Policy

### Task M0-T1: Confirm protected/public/auth-only route registry

**Target**
- Define canonical route categories for web module auth behavior.

**How to Implement**
1. Create route policy table in code comments/docs from design doc.
2. Confirm initial protected list with product/security owners:
   - `/settings`
   - `/personal-finance`
   - `/personal-finance/finance-transactions`
   - `/profile` (if route exists or when created)
3. Confirm auth-only list:
   - `/signin`, `/signup`, `/forgot-password`, `/reset-password`

**Validation (Done when)**
- [ ] Route policy is documented and approved.
- [ ] No conflicting behavior between header/nav links and policy.
- [ ] Follow-up tickets exist for any deferred routes.

**Depends On**: Design doc approval  
**Estimate**: 0.5 day

---

## M1: Redirect Utility Foundation

### Task M1-T1: Add redirect target parser and sanitizer

**Target**
- Provide one shared utility that validates `next` redirect targets safely.

**How to Implement**
1. Add utility file:
   - `web/src/shared/utils/auth/redirect-target.ts`
2. Implement:
   - `extractNextFromSearchParams(...)`
   - `sanitizeRedirectTarget(...)`
   - `resolvePostLoginRedirect(...)`
3. Enforce constraints:
   - must start with `/`
   - reject `//`
   - reject absolute URLs/schemes
   - reject auth-only routes

**Validation (Done when)**
- [ ] Utility exists and is reused by sign-in flow.
- [ ] All malicious input cases fallback to `/`.
- [ ] No redirect loops to auth-only routes.

**Depends On**: M0-T1  
**Estimate**: 0.5 day

### Task M1-T2: Add unit tests for redirect sanitization

**Target**
- Guarantee open-redirect prevention by test coverage.

**How to Implement**
1. Create tests:
   - `web/src/shared/utils/auth/__tests__/redirect-target.test.ts`
2. Cover cases:
   - `/settings` -> allowed
   - `/personal-finance?tab=a` -> allowed
   - `https://evil.com` -> rejected
   - `//evil.com` -> rejected
   - `javascript:alert(1)` -> rejected
   - `/signin` -> fallback `/`
   - empty/null -> fallback `/`

**Validation (Done when)**
- [ ] Tests pass locally.
- [ ] Abuse cases are explicitly asserted.

**Depends On**: M1-T1  
**Estimate**: 0.5 day

---

## M2: Sign-In Flow Integration

### Task M2-T1: Integrate validated `next` into sign-in page

**Target**
- Sign-in page resolves and uses redirect target instead of always `/`.

**How to Implement**
1. Update:
   - `web/src/app/(authentication)/signin/page.tsx`
2. Read `next` via `useSearchParams` and sanitize using shared utility.
3. If already authenticated, redirect to resolved target (not always `/`).
4. Pass resolved target to post-success callback.

**Validation (Done when)**
- [ ] Visiting `/signin?next=/settings` and signing in sends user to `/settings`.
- [ ] Invalid `next` falls back to `/`.

**Depends On**: M1-T1  
**Estimate**: 0.5 day

### Task M2-T2: Integrate redirect resolution in AuthProvider

**Target**
- Centralize post-auth routing in one provider path used by password and OAuth.

**How to Implement**
1. Update:
   - `web/src/shared/providers/AuthProvider.tsx`
2. Extend `routeAfterAuthentication` to accept optional redirect target.
3. Keep existing email-verification branching behavior.
4. Apply same behavior for:
   - `signIn`
   - `signInWithOAuth`

**Validation (Done when)**
- [ ] Password and OAuth sign-in share same redirect logic.
- [ ] Email verification path still takes priority when required.

**Depends On**: M2-T1  
**Estimate**: 0.5 day

---

## M3: Route Guard Enforcement

### Task M3-T1: Add protected route guard in app shell/layout layer

**Target**
- Redirect anonymous users who open protected routes.

**How to Implement**
1. Create route policy config:
   - `web/src/shared/config/auth-route-policy.ts`
2. Add guard hook/component, for example:
   - `web/src/shared/hooks/auth/useRequireAuth.ts`
3. Apply guard where route-level enforcement is needed (page or shared layout logic).
4. Redirect format:
   - `/signin?next=<encoded current pathname + search>`

**Validation (Done when)**
- [ ] Anonymous access to protected routes redirects to sign-in with `next`.
- [ ] Public routes remain accessible.
- [ ] Authenticated access to protected routes is unaffected.

**Depends On**: M2-T2  
**Estimate**: 1 day

### Task M3-T2: Optional optimistic proxy guard (hardening)

**Target**
- Add early request-time redirect for protected routes using Next.js `proxy.ts`.

**How to Implement**
1. Add:
   - `web/src/proxy.ts`
2. Use lightweight cookie/session hint for optimistic auth presence check.
3. Redirect anonymous requests to:
   - `/signin?next=<requested relative path>`
4. Keep note: proxy does not replace backend authorization.

**Validation (Done when)**
- [ ] Proxy redirects work in dev and production build.
- [ ] No regressions in static/public routes.
- [ ] Behavior documented as optimistic-only.

**Depends On**: M3-T1  
**Estimate**: 1 day

---

## M4: Hardening, Testing, and Rollout

### Task M4-T1: Add E2E scenarios for redirect-back flow

**Target**
- Validate end-to-end behavior and prevent regressions.

**How to Implement**
1. Add e2e spec:
   - `web/tests/e2e/auth-required-redirect.e2e.spec.ts`
2. Scenarios:
   - Anonymous -> `/settings` -> `/signin` -> successful login -> `/settings`
   - Anonymous -> `/personal-finance/finance-transactions` -> login -> same target
   - Malicious `next=https://evil.com` -> login -> `/`
   - Session expires on protected page -> login -> return target

**Validation (Done when)**
- [ ] New E2E suite passes in CI.
- [ ] Flakes are below accepted threshold.

**Depends On**: M3-T1  
**Estimate**: 1 day

### Task M4-T2: Add observability for redirect decisions

**Target**
- Improve operability and abuse detection.

**How to Implement**
1. Emit structured logs/events:
   - `auth_redirect_to_signin`
   - `auth_redirect_after_login_success`
   - `auth_redirect_invalid_target`
2. Include metadata:
   - source path
   - sanitized target
   - rejection reason (if invalid)
3. Ensure PII-safe payload.

**Validation (Done when)**
- [ ] Events visible in monitoring pipeline.
- [ ] Invalid-target attempts are measurable.

**Depends On**: M3-T1  
**Estimate**: 0.5 day

### Task M4-T3: Rollout and rollback checklist

**Target**
- Safely release with controlled blast radius.

**How to Implement**
1. Add feature flags for:
   - guard enablement
   - proxy enablement
2. Rollout in stages:
   - dev -> staging -> production
3. Prepare rollback:
   - disable guard/proxy flags
   - keep existing `/signin` fallback behavior

**Validation (Done when)**
- [ ] Rollout plan is documented in PR/release checklist.
- [ ] Rollback can be executed without code revert.

**Depends On**: M4-T1  
**Estimate**: 0.5 day

---

## Definition of Done (Feature)

- [ ] Design doc approved.
- [ ] Redirect sanitizer implemented and unit-tested.
- [ ] Sign-in flow supports safe `next` redirect.
- [ ] Protected routes enforce login redirect.
- [ ] E2E tests cover happy path and malicious inputs.
- [ ] Observability events are emitted.
- [ ] Rollout and rollback instructions documented.

