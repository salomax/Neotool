---
title: E2E Sandbox & Playwright Blueprint
type: blueprint
category: testing
status: draft
version: 1.0.0
tags: [testing, e2e, playwright, nx, ci-cd, sandbox]
ai_optimized: true
search_keywords: [e2e, playwright, nx, sandbox, ci, cd, visual regression, oauth, email reset]
related:
  - ./e2e-local-implementation-backlog.md
  - ../07-frontend/patterns/e2e-testing-pattern.md
  - ../08-workflows/testing-workflow.md
  - ../11-infrastructure/local-staging-runbook.md
last_updated: 2026-02-21
---

# E2E Sandbox & Playwright Blueprint

> **Purpose**: Define a pragmatic, low-cost E2E strategy for NeoTool that starts locally, integrates with Nx, supports third-party flows, and scales into CI/CD safely.

## 1) Scope and Requirements

This blueprint addresses the current requirements:

1. Provide a sandbox environment for E2E and smoke tests, runnable locally and optionally in CI/CD.
2. Support third-party dependent flows (email validation, password reset, OAuth, and other providers).
3. Detect UI changes (layout shifts, label changes, visual regressions).
4. Integrate cleanly with Nx targets and affected workflows.

## 2) Context and Constraints

- Production runs on Kubernetes.
- A permanent staging environment is currently not feasible due to cost.
- A local Docker Compose staging-like stack already exists.
- Goal: maximize confidence now without introducing high recurring infra spend.

## 3) Decision Summary

### 3.1 Core Strategy

- Use `docker-compose` as the primary E2E sandbox now.
- Treat it as **high-value but not full K8s parity**.
- Compensate infrastructure gap with:
  - post-deploy production smoke tests (read-only, safe),
  - infrastructure validation checks (Helm/manifests/policies),
  - optional periodic mini-K8s validation (kind/k3d) when affordable.

### 3.2 Production Testing Policy

- Do **not** run full destructive E2E directly in production.
- Allow only controlled production smoke tests with:
  - dedicated test accounts/tenant,
  - idempotent and low-impact flows,
  - no destructive side effects (real billing, mass writes, irreversible actions).

## 4) Environment Model

### 4.1 Local Developer Sandbox (Primary, Now)

- Stack: web + backend + database + required dependencies via Docker Compose.
- Standard flow:
  1. start stack,
  2. run migrations,
  3. seed minimal test data,
  4. execute Playwright suites,
  5. teardown.
- Main objective: fast developer feedback and deterministic regression coverage.

### 4.2 CI Pull Request (Low-Cost Gate)

- Keep mandatory gates lightweight:
  - lint,
  - unit tests,
  - integration tests.
- Optional low-cost E2E entry:
  - smoke-only Playwright subset,
  - manual trigger when needed,
  - or `affected` smoke targets only.

### 4.3 Nightly / Manual Extended Validation

- Run broader E2E suite in sandbox mode (compose-based or equivalent).
- Include selected third-party sandbox canaries.
- Non-blocking for merge; blocking for release promotion if desired.

### 4.4 Post-Deploy Production Smoke

- Minimal, read-only or low-risk smoke checks against real production path.
- Detects deployment or infra-specific regressions not visible in compose.

## 5) Third-Party Flow Strategy

Use a two-layer model:

1. **Controlled local/provider-fake tests** (required in PR/local):
   - Email/reset: MailHog or Mailpit.
   - OAuth: local OIDC provider (e.g., Keycloak/Dex/test IdP).
   - External APIs: test doubles or contract-safe mocks.

2. **Real sandbox canaries** (nightly/manual):
   - Run a small curated set against provider sandbox tenants.
   - Validate critical assumptions that mocks cannot guarantee.

## 6) UI Change Detection Strategy

- Enable visual regression with Playwright snapshots (page or component scope).
- Complement visual checks with semantic assertions:
  - role and accessible-name checks,
  - explicit text/label assertions for important UX copy.
- Stabilize rendering to reduce false positives:
  - fixed viewport, theme, locale, fonts, and deterministic time/data.
- Baseline governance:
  - snapshot updates only via explicit review and approval.

## 7) Test Data Management and Cleanup

In ephemeral or compose-based runs, direct DB access is acceptable and expected.

### 7.1 Data Lifecycle

- `migrate -> seed minimal -> run -> cleanup/teardown`.
- Prefer isolated data scopes per run (`tenant_id` / `run_id` prefix).
- Keep seed data minimal and scenario-oriented.

### 7.2 Safety Rules

- Never share mutable test DB across concurrent CI jobs.
- Never run cleanup scripts against production DB.
- Never require manual SQL intervention for regular E2E execution.

## 8) Nx Integration Blueprint

Define a dedicated E2E project with environment-specific targets.

### 8.1 Suggested Target Model

- `e2e-local` — full compose-backed local suite.
- `e2e-smoke` — quick smoke subset.
- `e2e-sandbox` — broader suite with controlled dependencies.
- `e2e-prod-smoke` — safe post-deploy production checks.
- `e2e-visual-smoke` — targeted visual regression checks.

### 8.2 Execution Patterns

- PR optimization: `nx affected -t test,e2e-smoke`.
- Local deep validation: `nx run <e2e-project>:e2e-local`.
- Nightly/manual: `nx run <e2e-project>:e2e-sandbox`.

### 8.3 Tag-Based Test Selection

Adopt tags such as:

- `@smoke`
- `@full`
- `@third-party`
- `@visual`
- `@prod-safe`

Each Nx target maps to one or more tags to keep execution predictable.

## 9) CI/CD Policy Blueprint

### 9.1 Pull Request

- Required: lint + unit + integration (+ optional `affected` smoke E2E).
- Artifact retention on failure: Playwright trace, screenshot, video, logs.

### 9.2 Main Branch / Deploy

- Deploy through existing pipeline.
- Execute post-deploy production smoke (safe subset).

### 9.3 Nightly

- Execute extended E2E including selected third-party sandbox canaries.
- Track flaky tests separately from deterministic regressions.

## 10) Flakiness and Reliability Controls

- Limited retries only for known transient failures.
- Quarantine policy for unstable tests (non-gating until fixed).
- Stable selectors (`data-testid`, robust roles) over brittle CSS selectors.
- Avoid locale-dependent assertions unless i18n behavior is under test.

## 11) Security and Secrets

- Environment-specific secrets in CI secret manager only.
- Dedicated test accounts and provider sandbox tenants.
- No hardcoded tokens, credentials, or callback secrets in repository.

## 12) Rollout Plan

### Phase 0 (Now)

- Use local compose sandbox as primary E2E environment.
- Establish Nx targets and tagging model.
- Add smoke + visual smoke suites.

### Phase 1 (Low-Cost CI)

- Add PR `affected` smoke E2E where affordable.
- Add nightly/manual extended sandbox runs.
- Add provider sandbox canaries for critical flows.

### Phase 2 (Maturity)

- Add periodic K8s-parity validation (kind/k3d or ephemeral namespace).
- Expand production smoke and release-quality gates.
- Tighten SLOs for E2E runtime and flake rate.

## 13) Definition of Done for This Blueprint

This strategy is considered implemented when:

1. Nx targets exist and are documented.
2. Local compose E2E is reproducible with one command path.
3. Third-party critical flows are covered by fake + sandbox canary layers.
4. Visual regression checks are enabled with explicit baseline governance.
5. CI/CD stages and gating rules are explicitly configured and versioned.
