---
title: Local-First E2E Implementation Backlog (Stories & Tasks)
type: backlog
category: testing
status: draft
version: 1.0.0
tags: [testing, e2e, playwright, nx, local, backlog, stories, tasks]
ai_optimized: true
search_keywords: [local e2e backlog, playwright story tasks, nx e2e local plan, oauth email reset e2e]
related:
  - ./e2e-sandbox-blueprint.md
  - ../07-frontend/patterns/e2e-testing-pattern.md
  - ../11-infrastructure/local-staging-runbook.md
last_updated: 2026-02-21
---

# Local-First E2E Implementation Backlog (Stories & Tasks)

> **Purpose**: Provide a concrete execution plan (stories and tasks) to operationalize the E2E strategy with full local execution first, without CI/CD gating and without production execution for now.

## 1) Scope, Constraints, and Working Assumptions

### In Scope (Now)

- Full local E2E execution using sandboxed infrastructure.
- Playwright coverage for critical user journeys.
- Third-party dependent journeys using local/sandbox-friendly substitutes (email and OAuth).
- Visual regression checks for high-risk screens.
- Nx-integrated commands/targets for repeatable local developer workflows.

### Out of Scope (Now)

- Mandatory CI/CD E2E gates.
- Production smoke or full production E2E.
- K8s parity environment validation as a required gate.

### Current Baseline (Observed in Repo)

- Playwright exists in `web/tests/e2e` and config in `web/playwright.config.ts`.
- E2E currently starts web server with `pnpm run dev` through Playwright `webServer`.
- Local/staging compose stacks exist (`infra/docker/docker-compose.local.yml`, `infra/docker/docker-compose.staging.yml`).
- `web/project.json` has no explicit E2E targets yet.
- E2E helpers already exist, but cleanup routines are placeholders (`web/tests/e2e/helpers/test-data.ts`).

## 2) Delivery Rules

- Status values for each task: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`.
- Implement stories in sequence unless explicitly marked parallelizable.
- Each story must produce:
  - code changes,
  - an update in docs/runbook,
  - execution evidence (commands run + results).
- Prefer deterministic tests over broad but flaky coverage.

## 3) Story Map (Execution Order)

1. `LE2E-001` Local environment contract hardening.
2. `LE2E-002` Deterministic data lifecycle and cleanup.
3. `LE2E-003` Nx local target integration.
4. `LE2E-004` Email verification and reset flow automation.
5. `LE2E-005` OAuth local-provider flow automation.
6. `LE2E-006` Visual regression foundation.
7. `LE2E-007` Artifact capture and debugging workflow.
8. `LE2E-008` Final runbook and operational handoff.

---

## Story `LE2E-001` — Harden the Local E2E Sandbox Contract

**User Story**  
As a developer, I want one reproducible local sandbox contract so that any machine can run E2E the same way with minimal setup variance.

**Implementation Tasks**

- [ ] `LE2E-001-T01` Define the minimum service matrix required for E2E local runs (web, router, security, financialdata, assets, db, pgbouncer, vault, optional kafka/minio).
- [ ] `LE2E-001-T02` Add a dedicated compose overlay for E2E (`infra/docker/docker-compose.e2e.local.yml`) with only required services and test-friendly overrides.
- [ ] `LE2E-001-T03` Add email-capture service (Mailpit or MailHog) to the E2E overlay and expose API/UI ports.
- [ ] `LE2E-001-T04` Add deterministic runtime defaults (`TZ=UTC`, locale, fixed feature-flag state where possible).
- [ ] `LE2E-001-T05` Add environment template for E2E (`infra/docker/.env.e2e.example`) with explicit required variables.
- [ ] `LE2E-001-T06` Add `scripts/testing/e2e-local-up.sh` and `scripts/testing/e2e-local-down.sh`.
- [ ] `LE2E-001-T07` Ensure health/wait strategy exists before test start (compose health checks + wait command).

**Acceptance Criteria**

- [ ] A clean machine can start the E2E stack using a single command path.
- [ ] All required services become healthy before tests execute.
- [ ] Email capture service is reachable from tests and browser.
- [ ] E2E stack can be destroyed cleanly without orphaned containers.

**Validation Commands (Local)**

- `docker compose -f infra/docker/docker-compose.local.yml -f infra/docker/docker-compose.e2e.local.yml --env-file infra/docker/.env.e2e up -d`
- `docker compose -f infra/docker/docker-compose.local.yml -f infra/docker/docker-compose.e2e.local.yml ps`
- `docker compose -f infra/docker/docker-compose.local.yml -f infra/docker/docker-compose.e2e.local.yml down --remove-orphans`

**Dependencies**

- None.

---

## Story `LE2E-002` — Implement Deterministic Test Data Lifecycle

**User Story**  
As a developer, I want predictable seed/cleanup behavior so tests are isolated, repeatable, and do not accumulate dirty data.

**Implementation Tasks**

- [ ] `LE2E-002-T01` Define data ownership model for E2E runs (`run_id` + deterministic prefixes).
- [ ] `LE2E-002-T02` Implement seed entrypoint (`scripts/testing/e2e-seed.*`) to create baseline users/roles/groups required by existing tests.
- [ ] `LE2E-002-T03` Implement cleanup entrypoint (`scripts/testing/e2e-clean.*`) to remove only test-owned records.
- [ ] `LE2E-002-T04` Replace placeholder cleanup in `web/tests/e2e/helpers/test-data.ts` with a real call path (GraphQL or DB script contract).
- [ ] `LE2E-002-T05` Update Playwright `global-setup.ts` to trigger seed and validate readiness.
- [ ] `LE2E-002-T06` Update Playwright `global-teardown.ts` to trigger cleanup and artifact summary.
- [ ] `LE2E-002-T07` Ensure parallel-safe unique identifiers are used for dynamic entities.
- [ ] `LE2E-002-T08` Add failure-safe cleanup mode (`cleanup-on-failure`) callable manually.

**Acceptance Criteria**

- [ ] Two consecutive runs execute without manual DB reset.
- [ ] Test-created records are isolated and cleaned.
- [ ] Setup and teardown are traceable in logs with run identifier.
- [ ] No production or shared DB endpoint is accepted by seed/cleanup scripts.

**Dependencies**

- `LE2E-001`.

---

## Story `LE2E-003` — Integrate Local E2E Workflows with Nx

**User Story**  
As a developer, I want standard Nx targets for local E2E flows so the team uses the same commands and execution semantics.

**Implementation Tasks**

- [ ] `LE2E-003-T01` Add explicit E2E targets to `web/project.json` using `nx:run-commands`.
- [ ] `LE2E-003-T02` Define at minimum:
  - `e2e-local` (full local suite),
  - `e2e-local-smoke` (fast local smoke),
  - `e2e-local-visual` (visual regression subset),
  - `e2e-local-auth` (auth-focused flows).
- [ ] `LE2E-003-T03` Add command wrappers in root `package.json` for ergonomics (optional but recommended).
- [ ] `LE2E-003-T04` Mark E2E targets as non-cacheable or carefully scoped cache behavior.
- [ ] `LE2E-003-T05` Standardize environment injection (`PLAYWRIGHT_TEST_ENV=local`, env file path).
- [ ] `LE2E-003-T06` Add a single orchestrator command (`scripts/testing/e2e-local-run.sh`) to execute up -> seed -> tests -> down.
- [ ] `LE2E-003-T07` Add clear failure exit codes and step-level logs.

**Acceptance Criteria**

- [ ] Developers run E2E with `nx run neotool-web:e2e-local`.
- [ ] Smoke and full suites are independently executable.
- [ ] Command output makes it clear which phase failed (up, seed, tests, cleanup, down).

**Dependencies**

- `LE2E-001`, `LE2E-002`.

---

## Story `LE2E-004` — Cover Email Validation and Password Reset End-to-End

**User Story**  
As a developer, I want fully automated email-based auth journeys so password reset and email-link flows are validated without external email providers.

**Implementation Tasks**

- [ ] `LE2E-004-T01` Add/extend Page Objects for signup, verify-email-link, forgot-password, and reset-password screens.
- [ ] `LE2E-004-T02` Implement a mail client helper (`web/tests/e2e/helpers/mail.ts`) to query Mailpit/MailHog API.
- [ ] `LE2E-004-T03` Implement token extraction utilities from captured email content.
- [ ] `LE2E-004-T04` Add E2E scenario: request reset -> receive email -> open reset link -> set new password -> sign in.
- [ ] `LE2E-004-T05` Add E2E scenario: signup -> verification email -> verify link -> post-verification behavior.
- [ ] `LE2E-004-T06` Add negative-path tests (expired token, invalid token, reused token).
- [ ] `LE2E-004-T07` Tag scenarios (`@auth`, `@email`, `@smoke` for minimal subset).
- [ ] `LE2E-004-T08` Ensure assertions avoid brittle locale-coupled text unless i18n behavior is explicitly under test.

**Acceptance Criteria**

- [ ] Password reset flow is fully automated end-to-end with local email capture.
- [ ] Email verification flow is fully automated end-to-end with local email capture.
- [ ] At least one happy path and one invalid-token path exist for each flow.

**Dependencies**

- `LE2E-001`, `LE2E-002`, `LE2E-003`.

---

## Story `LE2E-005` — Cover OAuth Flow with a Local Identity Provider

**User Story**  
As a developer, I want OAuth E2E coverage without external provider dependencies so auth federation behavior is tested locally and deterministically.

**Implementation Tasks**

- [ ] `LE2E-005-T01` Choose a local OIDC provider for E2E (Keycloak recommended for full browser realism).
- [ ] `LE2E-005-T02` Add provider service to E2E compose overlay with deterministic realm/client/user bootstrap.
- [ ] `LE2E-005-T03` Configure security service local env for OIDC issuer/client used by E2E.
- [ ] `LE2E-005-T04` Add Playwright helper for OAuth login page transitions.
- [ ] `LE2E-005-T05` Implement E2E scenario: start OAuth login -> provider authentication -> callback -> authenticated app state.
- [ ] `LE2E-005-T06` Implement negative scenario: provider denies auth or invalid callback state.
- [ ] `LE2E-005-T07` Document OAuth test account reset strategy.

**Acceptance Criteria**

- [ ] OAuth login flow runs locally without internet dependency.
- [ ] Auth callback and session establishment are validated.
- [ ] Negative-path OAuth behavior is covered.

**Dependencies**

- `LE2E-001`, `LE2E-003`.

---

## Story `LE2E-006` — Establish Visual Regression for High-Risk Screens

**User Story**  
As a developer, I want automated UI change detection so layout/label regressions are flagged early in local validation.

**Implementation Tasks**

- [ ] `LE2E-006-T01` Configure Playwright snapshot strategy (folder structure, naming, browser policy).
- [ ] `LE2E-006-T02` Stabilize rendering conditions (viewport, timezone, locale, animation disabling, seeded test data).
- [ ] `LE2E-006-T03` Create visual tests for high-risk auth screens (signin, signup, forgot-password, reset-password, verify-email-link).
- [ ] `LE2E-006-T04` Add masking/ignore strategy for volatile UI regions (timestamps, dynamic IDs, rotating content).
- [ ] `LE2E-006-T05` Add a controlled baseline update command (`--update-snapshots`) with explicit developer intent.
- [ ] `LE2E-006-T06` Document visual diff review protocol (what is acceptable vs regression).

**Acceptance Criteria**

- [ ] Visual tests run locally and produce deterministic results.
- [ ] Baseline updates require explicit action.
- [ ] At least five critical screens have baseline coverage.

**Dependencies**

- `LE2E-003`, `LE2E-004`.

---

## Story `LE2E-007` — Improve Failure Triage with Artifacts and Logs

**User Story**  
As a developer, I want actionable failure artifacts so I can debug failing local E2E runs quickly.

**Implementation Tasks**

- [ ] `LE2E-007-T01` Ensure Playwright captures trace, screenshot, and video according to local policy.
- [ ] `LE2E-007-T02` Standardize artifact output location by run (`web/playwright-report/<run_id>/...` or equivalent).
- [ ] `LE2E-007-T03` Persist sandbox logs snapshot on failures (`docker compose logs` export).
- [ ] `LE2E-007-T04` Add a quick triage command/script to open or print artifact paths.
- [ ] `LE2E-007-T05` Add failure taxonomy in docs (infra boot failure, seed failure, auth failure, visual diff failure).

**Acceptance Criteria**

- [ ] Every failed run points to reproducible artifacts.
- [ ] Developer can inspect browser trace and relevant service logs quickly.
- [ ] Triage flow is documented and executable without manual guessing.

**Dependencies**

- `LE2E-001`, `LE2E-003`.

---

## Story `LE2E-008` — Publish Operational Runbook and Handoff Checklist

**User Story**  
As a team member or LLM agent, I want a complete operational runbook so I can execute and maintain the local E2E system without tribal knowledge.

**Implementation Tasks**

- [ ] `LE2E-008-T01` Create `docs/testing/e2e-local-runbook.md` with end-to-end setup and commands.
- [ ] `LE2E-008-T02` Create `docs/testing/e2e-local-troubleshooting.md` for common failures and fixes.
- [ ] `LE2E-008-T03` Document story-to-file mapping for maintainers (where each concern lives).
- [ ] `LE2E-008-T04` Add a “new test authoring checklist” (selectors, tags, cleanup rules, visual rules).
- [ ] `LE2E-008-T05` Add a “release readiness local checklist” for manual pre-merge validation.
- [ ] `LE2E-008-T06` Link this backlog from `docs/testing/e2e-sandbox-blueprint.md`.

**Acceptance Criteria**

- [ ] A new developer can run full local E2E by following docs only.
- [ ] A second developer (or LLM) can add one new E2E test without hidden assumptions.
- [ ] Troubleshooting guide covers at least top 10 practical failure modes.

**Dependencies**

- `LE2E-001` through `LE2E-007`.

---

## 4) Deferred Backlog (Explicitly Not Implemented Now)

These stories are intentionally deferred and should remain `TODO` until cost/priority changes:

- `LE2E-D01` Add PR-required CI E2E smoke gates.
- `LE2E-D02` Add nightly CI full E2E and third-party sandbox canaries.
- `LE2E-D03` Add post-deploy production smoke tests.
- `LE2E-D04` Add K8s parity checks (kind/k3d or ephemeral namespace).

## 5) Initiative-Level Definition of Done

This local-first implementation is complete when:

1. Full E2E flow runs locally from one documented command path.
2. Email-reset/verification and OAuth flows are locally automated.
3. Visual regression exists for critical auth screens.
4. Data seed and cleanup are deterministic and safe.
5. Nx targets are the canonical execution entry points.
6. Runbook/troubleshooting docs allow independent operation by developers and LLMs.
