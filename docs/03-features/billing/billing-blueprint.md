---
title: Billing Module Blueprint
type: feature-spec
category: billing
status: draft
version: 1.0.0
tags: [billing, subscriptions, payments, entitlements]
ai_optimized: true
search_keywords: [billing, subscription, plans, payment-provider, stripe, entitlements]
related:
  - 02-architecture/service-architecture.md
  - 02-architecture/api-architecture.md
  - 01-overview/core-principles.md
  - 03-features/security/README.md
last_updated: 2026-02-04
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
- Entitlement resolution (what an account can use right now)
- Provider integration through an abstraction layer (Stripe first, provider-agnostic core)

The module is the system of record for access decisions and account monetization state, while external providers execute payment collection.

## Goals

- Support recurring plan billing for accounts.
- Keep payment-provider integration abstract and replaceable.
- Enforce product access through entitlements derived from subscription state.
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
- Entitlements and feature limits

`billing` does **not** own:
- Authentication/identity (security module)
- Product feature implementation (app module)
- Raw provider data model internals (stored only as external references + raw events)

## Core Domain Model (v1)

### Main Entities

- `BillingAccount`
  - `id`, `ownerType`, `ownerId`, `currency`, `billingEmail`, `providerCustomerRef`
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
- `EntitlementSnapshot`
  - Resolved feature access and limits at a given time
- `ProviderEventInbox`
  - Raw webhook/event payload with idempotency tracking

### Critical Relationships

- One `BillingAccount` has many `Subscription` records over time.
- One active account should have at most one active base subscription (enforced by unique constraint + status filter).
- One `Subscription` has one or many `SubscriptionItem` rows.
- One `Invoice` belongs to one `Subscription` and billing period.
- One `EntitlementSnapshot` is derived from active subscription + plan policy.

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
- `TRIALING` and `ACTIVE` produce entitlements.
- `PAST_DUE` may keep entitlements during configurable grace period.
- `CANCELED` can either end immediately or at period end (`cancelAtPeriodEnd=true`).
- Upgrades are immediate (optional proration).
- Downgrades apply next cycle by default (safer for support/UX).

## Entitlements Model (important)

Do not gate features directly on "subscription active".
Use explicit entitlements, for example:
- `projects.max = 3`
- `storage.gb = 50`
- `api.rate_limit.rpm = 600`
- `feature.advanced_analytics = true`

Resolution order:
1. Base plan entitlements
2. Add-ons / overrides
3. Temporary grants
4. Suspension rules (`past_due`, fraud hold, manual admin lock)

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
- `myEntitlements`

Mutations:
- `startSubscription(input)`
- `changePlan(input)`
- `cancelSubscription(input: { cancelAtPeriodEnd: Boolean })`
- `resumeSubscription`
- `createBillingPortalSession`
- `previewPlanChange(input)` (proration preview, optional)

Internal/Admin operations:
- `syncSubscriptionFromProvider(subscriptionId)`
- `grantTemporaryEntitlement(...)`
- `revokeTemporaryEntitlement(...)`

## Data and Constraints (database)

Recommended constraints:
- Unique active base subscription per account.
- Strong foreign keys from invoices/payments to subscription/account.
- Unique `(provider, providerEventId)` in inbox.
- Optimistic locking (`version`) on mutable aggregates.
- Audit timestamps on all stateful tables.

Recommended indexing:
- `subscription(account_id, status)`
- `subscription(current_period_end)`
- `invoice(account_id, created_at desc)`
- `provider_event_inbox(status, next_retry_at)`

## Security and Compliance

- Enforce ownership checks: account can only access its own billing data.
- Treat webhook endpoints as sensitive: signature verification required.
- Avoid storing raw card data (delegate PCI scope to provider).
- Protect billing mutations with explicit permissions (e.g., `billing:manage`).
- Emit audit logs for lifecycle changes and manual overrides.

## Observability

Metrics:
- `billing_subscription_state_transitions_total`
- `billing_webhook_events_total{status=...}`
- `billing_webhook_processing_latency_ms`
- `billing_entitlement_resolution_latency_ms`
- `billing_payment_failures_total`

Alerts:
- Webhook failure rate spike
- Inbox backlog growth
- Entitlement resolution errors
- Renewal job failures

## Rollout Plan

### Phase 0 - Foundation
- Define tables/entities and lifecycle enums.
- Build provider abstraction interfaces and mock adapter.
- Implement entitlement resolver and cache strategy.

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
- Add-ons and seat-based pricing.
- Multi-channel billing (`WEB`, `IOS_IAP`, `ANDROID_PLAY`) normalization.

## Open Decisions

- Billing anchor day policy (signup day vs fixed calendar day).
- Grace period duration for `PAST_DUE`.
- Proration policy defaults for upgrades/downgrades.
- Whether v1 includes scheduled plan changes.
- Multi-tenant vs single-account billing ownership model.

## Suggested Next Artifacts

- `docs/03-features/billing/subscription-lifecycle.feature` (Gherkin)
- `docs/03-features/billing/DECISIONS.md` (trade-offs + ADR links)
- `docs/03-features/billing/billing-runbook.md` (ops and incident handling)
