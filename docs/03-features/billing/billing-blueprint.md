---
title: Billing Module Blueprint
type: feature-spec
category: billing
status: draft
version: 1.2.0
tags: [billing, subscriptions, payments, entitlements]
ai_optimized: true
search_keywords: [billing, subscription, plans, payment-provider, stripe, entitlements]
related:
  - 02-architecture/service-architecture.md
  - 02-architecture/api-architecture.md
  - 01-overview/core-principles.md
  - 03-features/security/README.md
  - 03-features/security/resource-ownership/account-resource-ownership-requirements.md
  - 03-features/security/resource-ownership/resource-ownership.md
last_updated: 2026-02-11
---

# Billing Module Blueprint

## Module Name Recommendation

**Recommended module name:** `billing`

Why this is the best fit:
- Aligns with the existing architecture plan for a future **Billing Service**.
- Is vendor-neutral and works whether you use Stripe now or another provider later.
- Covers subscriptions, invoices, payments, taxes, and refunds under one business boundary.

If you want to be extra explicit in product language, use **"Billing & Entitlements"** as the user-facing feature name while keeping the technical module name as `billing`.

## Overview

The `billing` module owns account monetization lifecycle:
- Plans and prices
- Account subscriptions and term periods
- Invoice and payment status synchronization
- Plan-access resolution (what an account can use right now)
- Quota/rate policy resolution (how much/how fast an account can use)
- Provider integration through an abstraction layer (Stripe first, provider-agnostic core)

The module is the system of record for account monetization and plan-access state, while external providers execute payment collection.
Authorization decisions remain in the security module; billing contributes account-level plan and quota inputs.

## Goals

- Support recurring plan billing for accounts.
- Keep payment-provider integration abstract and replaceable.
- Enforce account-level plan ceilings and quotas derived from subscription state.
- Be resilient to webhook retries, duplicates, and out-of-order events.
- Enable future expansion to usage-based billing and multi-channel billing (web + mobile stores).

## Non-Goals (v1)

- Complex tax engine implementation.
- Advanced revenue recognition/accounting reports.
- Multi-provider live active/active failover.
- Full quote-to-cash and enterprise contract automation.

## Bounded Context and Ownership

`billing` owns:
- Plan catalog (`Plan`, `Price`, `BillingInterval`)
- Subscription lifecycle (`trialing`, `active`, `past_due`, `canceled`, etc.)
- Billing periods and renewals
- Invoice/payment synchronization
- Plan action policy and usage limits

`billing` does **not** own:
- Authentication/identity (security module)
- Authorization policy evaluation (RBAC/resource ownership/ABAC in security module)
- Product feature implementation (app module)
- Raw provider data model internals (stored only as external references + raw events)

## Core Domain Model (v1)

### Main Entities

- `BillingAccount`
  - `id`, `accountId`, `currency`, `billingEmail`, `providerCustomerRef`
- `Plan`
  - Product plan definition (`FREE`, `PRO`, `BUSINESS`)
- `Price`
  - Money + interval + trial policy linked to a `Plan`
- `Subscription`
  - Account + chosen price + lifecycle state + period boundaries
- `SubscriptionItem`
  - Supports future multi-line subscriptions and add-ons
- `Invoice`
  - Billing document state (`draft`, `open`, `paid`, `void`, `uncollectible`)
- `PaymentTransaction`
  - Payment attempt/result references
- `PlanAccessSnapshot`
  - Resolved plan action allowlist and limits at a given time
- `ProviderEventInbox`
  - Raw webhook/event payload with idempotency tracking

### Critical Relationships

- One `BillingAccount` maps to exactly one `security.accounts.id` (`accountId`).
- One `BillingAccount` has many `Subscription` records over time.
- One active account should have at most one active base subscription (enforced by unique constraint + status filter).
- One `Subscription` has one or many `SubscriptionItem` rows.
- One `Invoice` belongs to one `Subscription` and billing period.
- One `PlanAccessSnapshot` is derived from active subscription + plan policy.

### Identity and Scope Alignment (Security Integration)

- Billing ownership is always account-scoped (`accountId`), not user-scoped.
- Account membership and account role (`OWNER`/`ADMIN`/`MEMBER`/`VIEWER`) are owned by security module.
- Account selection preference is stored on `account_memberships.is_default`; login/session context sets `current_account`.
- Billing reads/writes must execute within the authenticated `current_account` context and active membership.
- JWT carries account context but not final long-lived authorization. Sensitive requests revalidate active membership/delegation via cache or DB checks.

## Subscription Lifecycle (recommended state machine)

States:
- `INCOMPLETE`
- `TRIALING`
- `ACTIVE`
- `PAST_DUE`
- `PAUSED` (optional, provider-dependent)
- `CANCELED`
- `EXPIRED`

Key rules:
- `TRIALING` and `ACTIVE` produce plan-access state.
- `PAST_DUE` may keep plan-access state during configurable grace period.
- `CANCELED` can either end immediately or at period end (`cancelAtPeriodEnd=true`).
- Upgrades are immediate (optional proration).
- Downgrades apply next cycle by default (safer for support/UX).

## Plan Access and Quota Model (No Overlap)

Do not gate behavior on subscription status alone.

Use a single action catalog shared with security permissions:
- `report:view`
- `report:export`
- `budget:create`

The canonical action and capacity metric catalogs, their governance, and the rule "absent in plan allowlist = denied" are defined in [Action and Capacity Registries](../security/resource-ownership/action-capacity-registries.md).

Then apply account-level commercial policy:
- Plan action allowlist (`plan_allows(action_key)`)
- Plan limits (`quota(metric_key)`), for example:
  - `budgets.max = 20`
  - `storage.gb = 50`
  - `api.rate_limit.rpm = 600`

Important non-overlap rule:
- Do not create a second key for the same action (for example, avoid both `report:export` and `feature.advanced_reports` for one behavior).
- If an action key is absent from the plan allowlist, it is denied by default.

Resolution order:
1. Base plan action/limits
2. Add-ons / overrides
3. Temporary grants/overrides
4. Suspension rules (`past_due`, fraud hold, manual admin lock)

For account-owned features, use this gate order:
1. Account context + active membership/delegation validity (security)
2. Account role gate (security role ceiling for operation category)
3. Plan action gate (billing, account-level ceiling)
4. RBAC action permission (security, user/group-based)
5. Resource row ownership/ACL (security)
6. ABAC conditional checks (security, optional)
7. Capacity gate (billing quotas/rate limits, when applicable)

Reference equation:

`allow = membership_valid AND account_role_allows(operation) AND plan_allows(action) AND rbac_allows(action) AND row_allows AND abac_allows AND capacity_available`

## Provider Abstraction (Stripe-ready, provider-neutral)

Create a provider port like:

- `BillingProviderAdapter`
  - `createCustomer(...)`
  - `startSubscription(...)`
  - `changeSubscription(...)`
  - `cancelSubscription(...)`
  - `createPortalSession(...)`
  - `listInvoices(...)`
  - `verifyWebhook(...)`
  - `mapExternalEvent(...) -> DomainEvent`

Implementation classes:
- `StripeBillingProviderAdapter` (first)
- Future: `PaddleBillingProviderAdapter`, `MockBillingProviderAdapter`

Keep provider identifiers in dedicated external reference fields:
- `providerCustomerRef`
- `providerSubscriptionRef`
- `providerInvoiceRef`
- `providerPriceRef`

## Event-Driven Processing Pattern

Use an inbox processor for webhook reliability:

1. Receive webhook
2. Verify signature
3. Store payload in `ProviderEventInbox` (`eventId`, `receivedAt`, `status=PENDING`)
4. Process asynchronously with idempotency lock
5. Apply domain transitions
6. Mark inbox row `PROCESSED` (or `FAILED` with retry metadata)

This protects against retries, duplicate events, and temporary downstream failures.

## Suggested GraphQL Surface (v1)

Queries:
- `myBillingAccount`
- `myCurrentPlan`
- `mySubscription`
- `myInvoices(first, after)`
- `myPlanAccess` (or compatibility alias `myEntitlements`)

All queries are resolved for `current_account` from auth/session context.

Mutations:
- `startSubscription(input)`
- `changePlan(input)`
- `cancelSubscription(input: { cancelAtPeriodEnd: Boolean })`
- `resumeSubscription`
- `createBillingPortalSession`
- `previewPlanChange(input)` (proration preview, optional)

Internal/Admin operations:
- `syncSubscriptionFromProvider(subscriptionId)`
- `grantTemporaryPlanOverride(...)`
- `revokeTemporaryPlanOverride(...)`

## Data and Constraints (database)

Recommended constraints:
- Unique `billing_account(account_id)`.
- Unique active base subscription per account.
- Strong foreign keys from invoices/payments to subscription/account.
- Unique `(plan_id, action_key)` for plan action allowlist rows.
- Unique `(plan_id, metric_key, period)` for plan quota rows.
- Unique `(account_id, metric_key, period_start, period_end)` for durable usage counters.
- Unique `(provider, providerEventId)` in inbox.
- Optimistic locking (`version`) on mutable aggregates.
- Audit timestamps on all stateful tables.

Recommended indexing:
- `subscription(account_id, status)`
- `subscription(current_period_end)`
- `invoice(account_id, created_at desc)`
- `provider_event_inbox(status, next_retry_at)`

Quota/rate-limit enforcement guidance:
- For periodic quotas (daily/monthly), use durable account usage counters and atomic conditional increments.
- For high-frequency throttles (`rpm`, `rps`), use an edge/distributed rate limiter (token bucket/sliding window), not only relational counters.
- Avoid split "check then increment" write paths. Use single-statement or transactional compare-and-increment semantics.

## Security and Compliance

- Enforce ownership checks: account can only access its own billing data.
- Treat webhook endpoints as sensitive: signature verification required.
- Avoid storing raw card data (delegate PCI scope to provider).
- Protect billing mutations with explicit permissions (e.g., `billing:manage`) and account role ceiling (`OWNER`/`ADMIN`).
- Protect billing reads with active membership and explicit read permission (e.g., `billing:read`).
- Prefer short-lived access tokens carrying `sub`, `current_account`, and `session_version`; validate membership/delegation freshness server-side.
- Emit audit logs for lifecycle changes and manual overrides.

## Observability

Metrics:
- `billing_subscription_state_transitions_total`
- `billing_webhook_events_total{status=...}`
- `billing_webhook_processing_latency_ms`
- `billing_plan_access_resolution_latency_ms`
- `billing_quota_check_latency_ms`
- `billing_quota_denials_total{metric=...}`
- `billing_rate_limit_denials_total{metric=...}`
- `billing_payment_failures_total`

Alerts:
- Webhook failure rate spike
- Inbox backlog growth
- Plan-access resolution errors
- Quota or throttle denial spikes (unexpected baseline change)
- Renewal job failures

## Rollout Plan

### Phase 0 - Foundation
- Define tables/entities and lifecycle enums.
- Build provider abstraction interfaces and mock adapter.
- Implement plan-access resolver, quota policy resolver, and cache strategy.

### Phase 1 - Stripe Integration
- Implement Stripe adapter.
- Build checkout/subscription start flow.
- Build webhook inbox + processor.

### Phase 2 - Self-Serve Billing
- Billing portal session.
- Invoice history.
- Cancel/resume/change plan flows.

### Phase 3 - Hardening
- Grace-period policy.
- Dunning and retry strategy.
- Admin repair operations and replay tooling.

### Phase 4 - Expansion
- Usage-based components.
- Add-ons and seat-based pricing (seat counts derived from ACTIVE account memberships).
- Multi-channel billing (`WEB`, `IOS_IAP`, `ANDROID_PLAY`) normalization.

## Open Decisions

- Billing anchor day policy (signup day vs fixed calendar day).
- Grace period duration for `PAST_DUE`.
- Proration policy defaults for upgrades/downgrades.
- Whether v1 includes scheduled plan changes.
- Seat counting policy details (which membership roles/statuses are billable).
- Action catalog governance (which team owns `action_key` definitions and change process).
- Quota reset policy details (calendar vs rolling windows by metric).

## Suggested Next Artifacts

- `docs/03-features/billing/subscription-lifecycle.feature` (Gherkin)
- `docs/03-features/billing/DECISIONS.md` (trade-offs + ADR links)
- `docs/03-features/billing/billing-runbook.md` (ops and incident handling)
