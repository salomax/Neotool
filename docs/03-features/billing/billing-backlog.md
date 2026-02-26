---
title: Billing Module Implementation Backlog
type: backlog
category: billing
status: draft
version: 0.1.0
tags: [billing, plans, quotas, subscriptions, entitlements, plan-gate, capacity-gate]
ai_optimized: true
search_keywords: [billing backlog, plan gate, quota enforcement, plan allowlist, subscription lifecycle, stripe]
related:
  - 03-features/billing/billing-blueprint.md
  - 03-features/security/resource-ownership/account-resource-ownership-requirements.md
  - 03-features/security/resource-ownership/account-resource-ownership-backlog.md
  - 03-features/security/resource-ownership/action-capacity-registries.md
last_updated: 2026-02-25
---

# Billing Module Implementation Backlog

> **Purpose**: Break down the billing module into implementable milestones and detailed tasks, covering plans, quotas, subscriptions, and the integration of the Plan Gate (Layer 3) and Capacity Gate (Layer 7) into the authorization evaluation order.

## Context: Authorization Layers Owned by Billing

The security module defines a 7-layer authorization evaluation order (see [FR-7.0](../security/resource-ownership/account-resource-ownership-requirements.md#fr-70-authorization-evaluation-order-no-overlap)). Billing **owns and feeds** two of those layers:

```
Layer 3 — Plan Gate (commercial ceiling)
  Supplied by: billing.PlanAccessResolver → plan_allows(action_key)
  Denial code: PLAN_NOT_ALLOWED

Layer 7 — Capacity Gate (quota / rate limit)
  Supplied by: billing.QuotaService → capacity_available(metric_key)
  Denial codes: QUOTA_EXCEEDED, RATE_LIMITED
```

All other layers (1, 2, 4, 5, 6) remain owned by the security module.

## Key Domain Relationships

```
User
 ├── AccountMembership → Account ─────────────────────┐
 │        (OWNER/ADMIN/MEMBER/VIEWER)                  │
 │                                                      ▼
 └── GroupMembership → Group → RBAC roles       BillingAccount
       (global, Phase 1)            │                   │
                                    ▼                   ▼
                             RBAC permissions      Subscription
                             (action keys)              │
                                                        ▼
                                                       Plan
                                                   ┌────┴────┐
                                              PlanAction  PlanQuota
                                         (allowlist)   (limits)
                                         plan_allows()  capacity_available()
```

One `BillingAccount` maps 1:1 to a `security.accounts` row (`accountId`).
The billing module is account-scoped, not user-scoped.

## Milestone Plan

| Milestone | Scope | Exit Condition |
|-----------|-------|----------------|
| B0 | Domain model, plan catalog, and plan access foundation | Migrations, entities, plan seed data, and PlanAccessResolver are implemented and tested with mock adapter |
| B1 | BillingAccount lifecycle and subscription state machine | BillingAccount auto-created with accounts; subscription CRUD with state transitions tested |
| B2 | Plan Gate and Capacity Gate integration into authorization | `plan_allows()` and `capacity_available()` are enforced in the request path with correct denial codes |
| B3 | Stripe provider integration and webhook processing | Stripe adapter, checkout flow, and webhook inbox processor are operational |
| B4 | Self-serve billing workflows | Portal session, invoice history, cancel/resume/change plan flows are complete |
| B5 | Frontend billing UI and hardening | Plan selection, usage display, grace period policy, dunning, admin tools |

> **Sequencing note**: B0 and B1 can start independently of M5-T5 (resource ownership backlog), but B2 depends on M5-T5 being complete so that the plan/capacity contract is agreed. B3 depends on B1. B4 depends on B3. B5 depends on B4.

---

## B0: Domain Model and Plan Access Foundation

### Task B0-T1: Create Flyway migrations for billing core tables

**Target**
- Create durable schema for billing entities that the rest of the module depends on.

**Tables to create (in `billing` schema)**

```sql
-- billing.billing_accounts: one per security.accounts row
-- billing.plans: FREE, PRO, BUSINESS
-- billing.prices: money + interval + trial linked to a plan
-- billing.plan_actions: plan allowlist rows (plan_id, action_key)
-- billing.plan_quotas: plan quota rows (plan_id, metric_key, limit_value, period)
-- billing.subscriptions: account + chosen price + lifecycle state
-- billing.subscription_items: line items per subscription
-- billing.invoices: billing documents
-- billing.payment_transactions: payment attempt records
-- billing.plan_access_snapshots: resolved access cache per account
-- billing.account_usage_counters: durable periodic quota counters
-- billing.provider_event_inbox: webhook idempotency inbox
```

**Critical constraints**
- `UNIQUE(account_id)` on `billing_accounts`
- `UNIQUE(plan_id, action_key)` on `plan_actions`
- `UNIQUE(plan_id, metric_key, period)` on `plan_quotas`
- `UNIQUE(account_id, status) WHERE status IN ('TRIALING','ACTIVE','PAST_DUE')` on `subscriptions` (enforce one active base subscription per account)
- `UNIQUE(account_id, metric_key, period_start, period_end)` on `account_usage_counters`
- `UNIQUE(provider, provider_event_id)` on `provider_event_inbox`
- `version BIGINT` (optimistic locking) on all mutable aggregates

**Critical indexes**
- `subscriptions(account_id, status)`
- `subscriptions(current_period_end)` — for renewal jobs
- `invoices(account_id, created_at DESC)`
- `provider_event_inbox(status, next_retry_at)`
- `plan_actions(plan_id)` — for plan allowlist resolution
- `plan_quotas(plan_id)` — for quota resolution
- `account_usage_counters(account_id, metric_key, period_start, period_end)`

**How to Implement**
1. Create migration under `service/kotlin/billing/src/main/resources/db/migration/`.
2. Follow naming convention matching existing migrations in security module.
3. Use `DEFAULT uuidv7()` for primary keys per [Entity Pattern](../../../05-backend/patterns/entity-pattern.md#database-generated-uuid-uuidv7).
4. Add all constraints and indexes above.

**Validation (Done when)**
- [ ] Migration applies cleanly on empty database.
- [ ] Migration applies on local schema without data loss.
- [ ] `./scripts/cli/cli validate --service billing --skip-coverage` passes.

**Depends On**: B0 milestone entry (no code deps)
**Estimate**: 1.5 days

---

### Task B0-T2: Implement billing domain entities and enums

**Target**
- Provide JPA persistence models that map to the migration schema.

**Entities**
- `BillingAccountEntity`
- `PlanEntity`, `PriceEntity`
- `PlanActionEntity` (allowlist row)
- `PlanQuotaEntity` (quota definition row)
- `SubscriptionEntity`, `SubscriptionItemEntity`
- `InvoiceEntity`
- `PaymentTransactionEntity`
- `PlanAccessSnapshotEntity`
- `AccountUsageCounterEntity`
- `ProviderEventInboxEntity`

**Enums**
- `SubscriptionStatus`: `INCOMPLETE`, `TRIALING`, `ACTIVE`, `PAST_DUE`, `PAUSED`, `CANCELED`, `EXPIRED`
- `InvoiceStatus`: `DRAFT`, `OPEN`, `PAID`, `VOID`, `UNCOLLECTIBLE`
- `ProviderEventStatus`: `PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`
- `BillingInterval`: `MONTHLY`, `ANNUAL`
- `BillingChannel`: `WEB`, `IOS_IAP`, `ANDROID_PLAY`

**How to Implement**
1. Follow the [Database-generated UUID (uuidv7)](../../../05-backend/patterns/entity-pattern.md#database-generated-uuid-uuidv7) convention.
2. Add optimistic locking (`@Version`) on mutable aggregates.
3. Add audit timestamps (`created_at`, `updated_at`) on all tables.

**Validation (Done when)**
- [ ] Entity mappings match migration schema exactly (column names, types, constraints).
- [ ] Unit tests cover entity constructors, enum mappings, and version field behavior.
- [ ] `./gradlew :billing:test --no-daemon` passes.

**Depends On**: B0-T1
**Estimate**: 1.5 days

---

### Task B0-T3: Implement billing repositories and query contracts

**Target**
- Provide efficient repository operations for billing workflows.

**Key queries to implement**
- `findActiveSubscriptionByAccountId(accountId)` — returns at most one active base subscription
- `findPlanActionsByPlanId(planId)` — resolves action allowlist for a plan
- `findPlanQuotasByPlanId(planId)` — resolves quota definitions for a plan
- `findOrCreateUsageCounter(accountId, metricKey, periodStart, periodEnd)` — for quota enforcement
- `incrementUsageCounterAtomic(counterId, amount, limit)` — compare-and-increment; must be single-statement or transactional (no split check-then-increment)
- `findPendingInboxEvents(limit, now)` — for inbox processor
- `markInboxEventProcessed/Failed(eventId, metadata)` — with idempotency lock

**How to Implement**
1. Add repository interfaces with custom JPQL/native queries where needed.
2. The `incrementUsageCounterAtomic` query must use a `WHERE current_value + :amount <= :limit` predicate to avoid overshoots. Return affected rows count to detect capacity denial.
3. Add pagination/sorting support consistent with existing billing repo patterns.

**Validation (Done when)**
- [ ] Repository integration tests cover all query contracts above.
- [ ] `incrementUsageCounterAtomic` concurrency test confirms no quota overshoot under parallel requests.
- [ ] Explain plan verified for critical queries.
- [ ] `./gradlew :billing:test --no-daemon` passes.

**Depends On**: B0-T2
**Estimate**: 1.5 days

---

### Task B0-T4: Seed plan catalog (FREE, PRO, BUSINESS)

**Target**
- Establish the canonical plan definitions, allowlists, and quota limits so that account plan resolution works from day one.

**Plans to seed**

| Plan | Action keys allowed | Key quotas |
|------|--------------------|----|
| `FREE` | `account:create`, `account:invite`, `account:members_view`, `report:view`, `budget:create` | `budgets.max = 3`, `storage.gb = 1`, `api.rate_limit.rpm = 60` |
| `PRO` | All FREE + `report:export` | `budgets.max = 20`, `storage.gb = 50`, `api.rate_limit.rpm = 600` |
| `BUSINESS` | All PRO + future enterprise keys | `budgets.max = 200`, `storage.gb = 500`, `api.rate_limit.rpm = 6000` |

> **Important**: Action keys must come exclusively from [Action and Capacity Registries](../security/resource-ownership/action-capacity-registries.md). Do not introduce new keys here without updating that document first.

**How to Implement**
1. Add a Flyway data migration (seed file) for plans, prices, plan_actions, and plan_quotas.
2. Seed rows for MONTHLY and ANNUAL prices for PRO and BUSINESS; FREE has no price.
3. All seed action keys must be present in the Action Catalog before applying migration.

**Validation (Done when)**
- [ ] Seed migration applies without constraint violations.
- [ ] `findPlanActionsByPlanId` returns correct keys for each plan.
- [ ] `findPlanQuotasByPlanId` returns correct limits for each plan.
- [ ] Actions absent from FREE's allowlist return `PLAN_NOT_ALLOWED` in plan gate test.

**Depends On**: B0-T3
**Estimate**: 0.5 day

---

### Task B0-T5: Implement PlanAccessResolver

**Target**
- Provide the billing module's primary output to the security authorization pipeline: `plan_allows(action_key)` and `quota(metric_key)`.

**Interface to implement**

```kotlin
interface PlanAccessResolver {
    /**
     * Returns true if the account's current plan includes the action key.
     * Absent in allowlist = false (denied). No default allow.
     */
    fun planAllows(accountId: UUID, actionKey: String): Boolean

    /**
     * Returns the quota limit for the metric, or null if unlimited.
     */
    fun quotaLimit(accountId: UUID, metricKey: String): Long?

    /**
     * Returns the fully resolved PlanAccessSnapshot for the account.
     * Applies resolution order: base plan → add-ons/overrides → temp grants → suspension rules.
     */
    fun resolveSnapshot(accountId: UUID): PlanAccessSnapshot
}
```

**Resolution order** (from billing blueprint)
1. Base plan action/limits (from active subscription → plan)
2. Add-ons / overrides (future)
3. Temporary grants/overrides (`grantTemporaryPlanOverride`)
4. Suspension rules (`PAST_DUE`, fraud hold, manual admin lock)

**Access states that grant plan access**: `TRIALING`, `ACTIVE`.
**Grace period state**: `PAST_DUE` — keep access for configured grace period (configurable, TBD open decision).
**No access states**: `CANCELED`, `EXPIRED`, `INCOMPLETE`.

**Cache strategy**
- Cache `PlanAccessSnapshot` per account with short TTL (suggested: 60s configurable).
- Invalidate on subscription state change and on temporary override grant/revoke.
- Expose cache hit/miss metrics: `billing_plan_access_cache_hits_total`, `billing_plan_access_cache_misses_total`.
- Fall through to FREE plan if no active subscription (accounts without billing setup get FREE access).

**How to Implement**
1. Implement `PlanAccessResolver` using subscription + plan_actions + plan_quotas repositories.
2. Wire cache with invalidation hooks.
3. Expose `billing_plan_access_resolution_latency_ms` metric.

**Validation (Done when)**
- [ ] Unit tests for each subscription status produce correct `planAllows` result.
- [ ] Grace period logic tested: `PAST_DUE` within window allows; outside window denies.
- [ ] Temp override tests: override grants access to otherwise-denied action; revoke removes it.
- [ ] Cache invalidation test: subscription cancellation clears snapshot within propagation window.
- [ ] Resolution latency < 5ms (cached) per NFR-1.

**Depends On**: B0-T4
**Estimate**: 2 days

---

### Task B0-T6: Define and implement BillingProviderAdapter port

**Target**
- Establish the provider abstraction layer so billing logic is vendor-neutral from the start.

**Port interface**

```kotlin
interface BillingProviderAdapter {
    fun createCustomer(input: CreateCustomerInput): ProviderCustomerRef
    fun startSubscription(input: StartSubscriptionInput): ProviderSubscriptionRef
    fun changeSubscription(input: ChangeSubscriptionInput): ProviderSubscriptionRef
    fun cancelSubscription(input: CancelSubscriptionInput): ProviderSubscriptionRef
    fun createPortalSession(input: CreatePortalSessionInput): PortalSessionRef
    fun listInvoices(input: ListInvoicesInput): List<ProviderInvoiceRef>
    fun verifyWebhook(payload: ByteArray, signature: String): Boolean
    fun mapExternalEvent(payload: ByteArray): DomainBillingEvent
}
```

**Provider reference fields** (kept in billing entities, never replace with raw provider IDs in domain model)
- `providerCustomerRef`, `providerSubscriptionRef`, `providerInvoiceRef`, `providerPriceRef`

**How to Implement**
1. Define the port interface and input/output DTOs in the billing module's `provider` package.
2. Implement `MockBillingProviderAdapter` for testing: returns deterministic fake refs, simulates state transitions.
3. Wire mock as default adapter in dev/test environments.

**Validation (Done when)**
- [ ] Interface compiles and is used as the injection target (not the concrete class).
- [ ] MockAdapter passes all service unit tests without real network calls.
- [ ] Adapter can be swapped by dependency injection without changing service code.

**Depends On**: B0-T2
**Estimate**: 1 day

---

## B1: BillingAccount Lifecycle and Subscription State Machine

### Task B1-T1: Auto-create BillingAccount when security.Account is created

**Target**
- Every new account (PERSONAL, FAMILY, BUSINESS) gets a corresponding `BillingAccount` with FREE plan access from the start.

**How to Implement**
1. Subscribe to the account creation event (domain event or direct call from security module's `AccountService`).
2. Create `BillingAccount` row with `accountId = security.account.id`, `currency = default`, `billingEmail = account.owner email`.
3. No subscription row is created yet — `PlanAccessResolver` defaults to FREE for accounts with no active subscription.
4. Ensure the creation is in the same transaction or has a compensating retry if billing creation fails.

**Validation (Done when)**
- [ ] Integration test: create account in security module → `BillingAccount` row exists with correct `accountId`.
- [ ] `PlanAccessResolver.planAllows(accountId, "report:view")` returns `true` (FREE allows) immediately after account creation.
- [ ] `PlanAccessResolver.planAllows(accountId, "report:export")` returns `false` (FREE denies) immediately after account creation.
- [ ] Failure in billing creation does not silently succeed account creation (atomicity test).

**Depends On**: B0-T5, security M2-T1 (AccountService)
**Estimate**: 1 day

---

### Task B1-T2: Implement subscription state machine

**Target**
- Model the full subscription lifecycle with correct state transitions and invariants.

**State machine**

```
INCOMPLETE
    │ payment collected or trial started
    ▼
TRIALING ──────────────────────────────────┐
    │ trial ends / payment succeeds         │
    ▼                                       │ cancel
ACTIVE ◀──────────────────────────────┐    │
    │                │                 │    │
    │ payment fails  │ cancel (now)    │    │
    ▼                ▼ cancelAtPeriod  │    ▼
PAST_DUE       period ends ──────────►CANCELED
    │               │
    │ payment ok    │ grace period expires
    └───────────────┘
                │
                ▼
             EXPIRED
```

**Key rules**
- Only one subscription with status IN (`TRIALING`, `ACTIVE`, `PAST_DUE`) per account at a time (unique constraint + service enforcement).
- Upgrades apply immediately (with optional proration).
- Downgrades apply next cycle by default (configurable open decision).
- `cancelAtPeriodEnd = true` → subscription moves to `CANCELED` when `current_period_end` passes.

**How to Implement**
1. Add `SubscriptionService` with state transition methods.
2. Guard each transition with explicit allowed-from-state checks (throw `InvalidSubscriptionTransitionException` if violated).
3. Emit domain events for each transition (e.g., `SubscriptionActivated`, `SubscriptionCanceled`).
4. Invalidate `PlanAccessSnapshot` cache on every transition.

**Validation (Done when)**
- [ ] Unit tests cover all valid state transitions.
- [ ] Invalid transitions are rejected with a clear exception.
- [ ] Concurrent activation attempts for same account are serialized (optimistic locking via `version` field).
- [ ] Domain events are emitted for each transition.
- [ ] `PlanAccessResolver` cache is invalidated on transition.

**Depends On**: B0-T5, B0-T6, B1-T1
**Estimate**: 2 days

---

### Task B1-T3: Implement BillingAccountService GraphQL mutations

**Target**
- Expose billing operations on the GraphQL surface per billing blueprint.

**Mutations to implement (Phase 0/1 surface)**
- `startSubscription(input: StartSubscriptionInput!): Subscription!`
- `changePlan(input: ChangePlanInput!): Subscription!`
- `cancelSubscription(input: CancelSubscriptionInput!): Subscription!`
- `resumeSubscription: Subscription!`

**Queries to implement**
- `myBillingAccount: BillingAccount`
- `myCurrentPlan: Plan`
- `mySubscription: Subscription`
- `myPlanAccess: PlanAccessSnapshot`

**Authorization**
- All mutations require `billing:manage` RBAC permission AND `OWNER`/`ADMIN` account role ceiling.
- All queries require `billing:read` RBAC permission AND active account membership.
- Use `current_account` from JWT/principal for all resolution (no account ID parameter accepted from caller).

**How to Implement**
1. Add GraphQL schema under `service/kotlin/billing/src/main/resources/graphql/`.
2. Add resolvers that delegate to `SubscriptionService` and `PlanAccessResolver`.
3. Apply `GraphQLPermissionHelper` with `billing:manage` / `billing:read` action keys.

**Validation (Done when)**
- [ ] `./scripts/cli/cli graphql validate` passes.
- [ ] Integration tests: non-OWNER cannot call `cancelSubscription` (ACCOUNT_ROLE_INSUFFICIENT).
- [ ] Integration tests: missing `billing:manage` RBAC permission returns RBAC_DENIED.
- [ ] `myPlanAccess` returns correct snapshot for FREE, PRO, BUSINESS plans.

**Depends On**: B1-T2
**Estimate**: 2 days

---

### Task B1-T4: Implement ProviderEventInbox and async processor

**Target**
- Reliable, idempotent webhook processing that protects against retries, duplicates, and out-of-order events.

**Inbox pattern**
```
1. Receive webhook POST
2. Verify signature (delegate to adapter.verifyWebhook)
3. Insert into provider_event_inbox with status=PENDING (unique constraint on provider+eventId)
4. Return 200 immediately
5. Background processor picks PENDING events
6. Apply domain transition via SubscriptionService
7. Mark PROCESSED (or FAILED with retry metadata and next_retry_at)
```

**How to Implement**
1. Add `BillingWebhookController` (REST endpoint) for provider webhook ingestion.
2. Signature verification is mandatory; reject and return 400 on failure.
3. Duplicate events: unique constraint causes insert to fail silently → return 200 (already received).
4. Add `BillingEventInboxProcessor` (scheduled job or message consumer) with idempotency lock per event.
5. Add exponential backoff for failed events with `max_retry` ceiling.
6. Expose metrics: `billing_webhook_events_total{status}`, `billing_webhook_processing_latency_ms`.

**Validation (Done when)**
- [ ] Duplicate event insertion is idempotent (second insert returns 200, no duplicate processing).
- [ ] Invalid signature returns 400; valid signature proceeds.
- [ ] Processor retries failed events with backoff; stops after max_retry.
- [ ] Integration test sends mock webhook → subscription state transitions correctly.
- [ ] Inbox backlog alert fires when pending events exceed threshold.

**Depends On**: B1-T2, B0-T6
**Estimate**: 1.5 days

---

## B2: Plan Gate and Capacity Gate Integration

> **Depends on M5-T5** (account-resource-ownership-backlog) being complete so that the plan/capacity enforcement contract is finalized.

### Task B2-T1: Integrate Plan Gate (Layer 3) into the request authorization path

**Target**
- `plan_allows(action_key)` is evaluated at Layer 3 of the authorization evaluation order for all account-owned operations. This makes the billing module's plan allowlist a first-class gate enforced on every request.

**How to Implement**
1. Expose `PlanAccessResolver` as a shared bean accessible from the common security layer.
2. Update `GraphQLPermissionHelper` (or equivalent interceptor/guard) to call `planAccessResolver.planAllows(currentAccountId, actionKey)` after account role check and before RBAC check.
3. On denial: throw `PlanGateDeniedException` with reason code `PLAN_NOT_ALLOWED`.
4. Ensure plan gate is **not** called for operations that are explicitly plan-unrestricted (e.g., internal/admin operations).
5. Add `billing_plan_access_resolution_latency_ms` metric at gate call site.

**Resolution shortcut**: For operations with no plan action key defined in the Action Catalog, the gate must **fail closed** (deny) unless the operation is explicitly excluded from plan gating.

**Validation (Done when)**
- [ ] Integration test: account on FREE plan calling `report:export` returns `PLAN_NOT_ALLOWED`.
- [ ] Integration test: account on PRO plan calling `report:export` succeeds through plan gate.
- [ ] Integration test: RBAC-denied action does not reach plan gate (correct layer ordering).
- [ ] Plan gate resolution latency meets NFR-1 (< 5ms cached).
- [ ] Audit/observability: plan gate denials are logged with `account_id`, `action_key`, `plan_id`.

**Depends On**: B0-T5, M5-T5 (contract defined)
**Estimate**: 1.5 days

---

### Task B2-T2: Implement Capacity Gate (Layer 7) — periodic quota enforcement

**Target**
- `capacity_available(metric_key)` is evaluated as the final authorization gate for quota-limited operations. Uses atomic compare-and-increment semantics to prevent race condition overshoots.

**Quota enforcement contract**

```kotlin
interface QuotaService {
    /**
     * Atomically checks and increments usage for the metric.
     * Returns true if increment succeeded (capacity was available).
     * Returns false if capacity would be exceeded — does NOT increment.
     * Throws QuotaCapacityDeniedException with reason QUOTA_EXCEEDED on false.
     */
    fun checkAndIncrement(
        accountId: UUID,
        metricKey: String,
        amount: Long = 1,
        period: QuotaPeriod  // MONTHLY, DAILY, ROLLING
    ): Boolean

    /**
     * Decrements usage (for rollback/delete scenarios).
     */
    fun decrement(accountId: UUID, metricKey: String, amount: Long = 1, period: QuotaPeriod)

    /**
     * Returns current usage for a metric in the current period.
     */
    fun currentUsage(accountId: UUID, metricKey: String, period: QuotaPeriod): Long
}
```

**Atomic increment implementation** (single-statement, no split check-then-increment)
```sql
UPDATE billing.account_usage_counters
SET current_value = current_value + :amount
WHERE account_id = :accountId
  AND metric_key = :metricKey
  AND period_start = :periodStart
  AND period_end = :periodEnd
  AND current_value + :amount <= :limit
RETURNING current_value;
-- 0 rows affected = capacity denied
```

**How to Implement**
1. Implement `QuotaService` backed by `AccountUsageCounterRepository`.
2. Add `findOrCreate` for the counter row for the current period window (with insert-on-conflict).
3. Resolve the limit from `PlanAccessResolver.quotaLimit(accountId, metricKey)`.
4. On capacity denial, throw `QuotaCapacityDeniedException` with reason code `QUOTA_EXCEEDED`.
5. Expose metrics: `billing_quota_denials_total{metric}`, `billing_quota_check_latency_ms`.
6. Add quota reset job: clear/reset counters at period boundaries (daily/monthly calendar or rolling).

**Validation (Done when)**
- [ ] Concurrency test: 50 parallel `checkAndIncrement` requests against a limit of 3 → exactly 3 succeed, 47 fail.
- [ ] Unit test: period rollover resets counter; new period allows full quota again.
- [ ] Integration test: `budget:create` on FREE plan (limit 3) → 4th attempt returns `QUOTA_EXCEEDED`.
- [ ] `decrement` after resource deletion reduces counter correctly.
- [ ] `billing_quota_denials_total` metric increments on each denial.

**Depends On**: B0-T5, B2-T1
**Estimate**: 2 days

---

### Task B2-T3: Implement rate limiter for high-frequency throttles

**Target**
- `api.rate_limit.rpm` and similar high-frequency throttles use a distributed rate limiter (token bucket or sliding window counter), NOT relational quota counters.

**Design**
- Use Redis (or equivalent in-memory distributed store) with sliding window algorithm.
- Key: `rate_limit:{accountId}:{metricKey}:{window}`
- Limit: resolved from `PlanAccessResolver.quotaLimit(accountId, "api.rate_limit.rpm")`.
- On limit exceeded: return `RATE_LIMITED` with `Retry-After` header.
- Graceful degradation: if rate limiter store is unavailable, log and allow (fail-open) or deny (fail-closed, configurable).

**How to Implement**
1. Add `RateLimiterService` with a Redis/Lettuce backend (or Bucket4j with Redis).
2. Integrate with request interceptor/filter (not at service layer — this is a network-layer throttle).
3. Expose metrics: `billing_rate_limit_denials_total{metric}`, `billing_rate_limit_check_latency_ms`.

**Validation (Done when)**
- [ ] RPM test: 61 requests in 60 seconds from same account → 61st returns `RATE_LIMITED`.
- [ ] Limit resolves per plan: FREE=60rpm, PRO=600rpm, BUSINESS=6000rpm.
- [ ] Fail-open/fail-closed behavior is tested when Redis is unavailable.
- [ ] Retry-After header is present on rate-limit response.

**Depends On**: B2-T1
**Estimate**: 1.5 days

---

## B3: Stripe Provider Integration

### Task B3-T1: Implement StripeBillingProviderAdapter

**Target**
- Concrete implementation of `BillingProviderAdapter` using the Stripe SDK.

**How to Implement**
1. Add Stripe SDK dependency to billing module.
2. Implement all methods of `BillingProviderAdapter`:
   - `createCustomer` → `Customer.create()`
   - `startSubscription` → `Subscription.create()`
   - `changeSubscription` → `Subscription.update()` with proration
   - `cancelSubscription` → `Subscription.cancel()` or `cancelAtPeriodEnd`
   - `createPortalSession` → `Session.create()` (Billing Portal)
   - `listInvoices` → `Invoice.list()`
   - `verifyWebhook` → `Webhook.constructEvent()` signature check
   - `mapExternalEvent` → translate Stripe event types to `DomainBillingEvent`
3. Map Stripe subscription statuses to internal `SubscriptionStatus` enum.
4. Store only `providerCustomerRef`, `providerSubscriptionRef`, etc. in billing entities — never store raw Stripe objects.
5. Configure via environment variables: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`.

**Validation (Done when)**
- [ ] All adapter methods call the correct Stripe SDK endpoints (verified with Stripe mock/test mode).
- [ ] Webhook signature verification correctly rejects tampered payloads.
- [ ] Event mapping test: `customer.subscription.updated` maps to `SubscriptionUpdated` domain event.
- [ ] Idempotency keys are used for Stripe mutation calls to prevent duplicate charges.
- [ ] No raw card data or Stripe object internals are persisted.

**Depends On**: B0-T6, B1-T4
**Estimate**: 2 days

---

### Task B3-T2: Build checkout and subscription start flow

**Target**
- End-to-end flow: account selects a plan → Stripe checkout session → webhook confirms → subscription becomes ACTIVE.

**Flow**
```
1. User calls startSubscription(priceId)
2. BillingAccountService creates/retrieves Stripe customer (providerCustomerRef)
3. Creates Stripe checkout session
4. Returns checkout URL to frontend
5. User completes payment in Stripe
6. Stripe sends webhook: checkout.session.completed
7. ProviderEventInbox receives and processes event
8. SubscriptionService transitions: INCOMPLETE → ACTIVE (or TRIALING)
9. PlanAccessSnapshot cache invalidated
10. User now has PRO/BUSINESS plan access
```

**Validation (Done when)**
- [ ] E2E test (Stripe test mode): complete checkout → subscription is ACTIVE → `planAllows("report:export")` returns true.
- [ ] Idempotency: processing the same webhook twice produces identical final state (no duplicate subscription rows).
- [ ] If webhook arrives before checkout completes, inbox processor retries until consistent.

**Depends On**: B3-T1
**Estimate**: 1.5 days

---

## B4: Self-Serve Billing Workflows

### Task B4-T1: Billing portal session and invoice history

**Target**
- Account owners can access Stripe billing portal and view invoice history.

**GraphQL operations**
- `createBillingPortalSession: PortalSession!` — returns redirect URL
- `myInvoices(first: Int, after: String): InvoiceConnection!` — paginated

**How to Implement**
1. `createBillingPortalSession` delegates to `adapter.createPortalSession()`.
2. `myInvoices` reads from `billing.invoices` (synchronized from webhooks).
3. Require `billing:manage` permission for portal; `billing:read` for invoices.

**Validation (Done when)**
- [ ] Portal session URL is returned and redirects correctly in Stripe test mode.
- [ ] Invoice history paginates correctly with cursor-based pagination.
- [ ] Non-OWNER cannot create portal session.

**Depends On**: B3-T1
**Estimate**: 1 day

---

### Task B4-T2: Cancel, resume, and change plan flows

**Target**
- Account can cancel at period end, resume before cancellation, and change plan (upgrade/downgrade).

**Mutations**
- `cancelSubscription(input: { cancelAtPeriodEnd: Boolean! })`
- `resumeSubscription`
- `changePlan(input: ChangePlanInput!)`

**Rules**
- Upgrade → immediate, with proration.
- Downgrade → scheduled for next cycle by default (open decision, configurable).
- `cancelAtPeriodEnd=true` → status stays ACTIVE until period end, then CANCELED.
- `resumeSubscription` cancels pending cancellation if still in same period.

**Validation (Done when)**
- [ ] Cancellation at period end: subscription stays ACTIVE until end, then transitions.
- [ ] Resume: `cancelAtPeriodEnd` flag cleared; subscription stays active.
- [ ] Upgrade: proration calculated; plan access updates immediately.
- [ ] Downgrade: new plan applies next cycle; old plan access until then.

**Depends On**: B3-T2
**Estimate**: 1.5 days

---

## B5: Hardening, Frontend, and Expansion

### Task B5-T1: Grace period policy for PAST_DUE subscriptions

**Target**
- Define and enforce the configurable grace period during which `PAST_DUE` accounts retain plan access.

**How to Implement**
1. Add `billing.grace_period_days` configuration property (default: 7, open decision).
2. `PlanAccessResolver.planAllows()` must check: `PAST_DUE AND now < period_end + grace_period_days`.
3. After grace period: treat as access denied (effectively downgrade to FREE behavior).

**Validation (Done when)**
- [ ] `PAST_DUE` within grace window → `planAllows("report:export")` = true.
- [ ] `PAST_DUE` after grace window → `planAllows("report:export")` = false.
- [ ] Grace period is configurable without code change.

**Depends On**: B0-T5
**Estimate**: 0.5 day

---

### Task B5-T2: Admin operations and repair tooling

**Target**
- Internal/admin operations for manual overrides and incident recovery.

**Operations**
- `syncSubscriptionFromProvider(subscriptionId)` — force-sync subscription state from Stripe
- `grantTemporaryPlanOverride(accountId, planId, expiresAt, reason)` — bypass subscription for support
- `revokeTemporaryPlanOverride(accountId)` — remove override
- Webhook replay tool — reprocess a specific inbox event

**How to Implement**
1. Expose as internal GraphQL mutations with `billing:admin` permission (separate from `billing:manage`).
2. All admin operations emit audit log events with actor ID, target account ID, and reason.
3. Temporary overrides are stored in a dedicated table, not in subscription table.

**Validation (Done when)**
- [ ] `grantTemporaryPlanOverride` for PRO on FREE account → `planAllows("report:export")` = true.
- [ ] `revokeTemporaryPlanOverride` removes access.
- [ ] Expired overrides are not applied (TTL check in resolver).
- [ ] Audit logs are emitted for all admin operations.

**Depends On**: B0-T5, B3-T1
**Estimate**: 1.5 days

---

### Task B5-T3: Billing observability and alerts

**Target**
- All billing metrics from the blueprint are instrumented and alerting is configured.

**Metrics to instrument**
- `billing_subscription_state_transitions_total{from,to}` — on every transition
- `billing_webhook_events_total{status}` — PENDING / PROCESSED / FAILED
- `billing_webhook_processing_latency_ms` — time from inbox insert to processed
- `billing_plan_access_resolution_latency_ms` — per plan gate call
- `billing_quota_check_latency_ms` — per quota gate call
- `billing_quota_denials_total{metric}` — per quota denial
- `billing_rate_limit_denials_total{metric}` — per rate limit denial
- `billing_payment_failures_total` — on payment failed webhook

**Alerts to configure**
- Webhook failure rate spike (> 5% failures in 5 min window)
- Inbox backlog growth (> 100 pending events for > 10 min)
- Plan-access resolution errors (any 5xx on plan gate)
- Quota/throttle denial spikes (> 2x baseline)
- Renewal job failures

**Validation (Done when)**
- [ ] All metrics above exist in `/metrics` endpoint after exercising the code paths.
- [ ] Alert rules are committed to infrastructure config.
- [ ] Runbook link is present in alert annotation.

**Depends On**: B3-T2, B2-T2, B2-T3
**Estimate**: 1 day

---

### Task B5-T4: Frontend billing UI

**Target**
- Authenticated users can view their plan, usage, and manage subscription from the web app.

**UI components**
- Current plan card: plan name, status, renewal date, limits
- Plan selector / upgrade CTA: show available plans with price comparison
- Usage indicators: quota meters for `budgets.max`, `storage.gb`
- Invoice history list: paginated, downloadable links
- Cancel/resume plan action (account OWNER only)

**How to Implement**
1. Add GraphQL operations under `web/src/lib/graphql/operations/billing/`.
2. Regenerate TypeScript types.
3. Add `/settings/billing` route and components.
4. Usage indicators read from `myPlanAccess.quotas`.
5. Show plan gate denial messages inline (e.g., "Upgrade to PRO to export reports").

**Validation (Done when)**
- [ ] `./scripts/cli/cli graphql validate` passes.
- [ ] Plan gate denial is surfaced as an upgrade prompt, not a generic error.
- [ ] Quota meters update after user creates/deletes budgets.
- [ ] Billing UI is only visible to OWNER/ADMIN members.

**Depends On**: B4-T1, B4-T2
**Estimate**: 3 days

---

## Integration with Groups and RBAC (Users and their permissions)

> **Scope note**: This section documents how the RBAC layer (Layer 4) interacts with billing's plan gate (Layer 3). Full RBAC/group implementation is owned by the security module but is listed here for cross-cutting clarity.

### How users get permissions (summary)

```
User
 ├── AccountMembership (role: OWNER/ADMIN/MEMBER/VIEWER)
 │    └── Sets ceiling: max allowed action categories (read/write/delete/share)
 └── GroupMembership → Group → RBAC role assignment
      └── Grants specific action keys: e.g., "report:export", "budget:create"
           └── RBAC gate (Layer 4): user's groups/roles must include the action key
```

**Non-overlap between Plan Gate and RBAC Gate**
- Plan Gate (Layer 3): "Does the **account's plan** allow `action_key`?"
- RBAC Gate (Layer 4): "Does **this user's roles/groups** allow `action_key`?"
- Both must pass. An action denied by the plan gate is never re-evaluated by RBAC.

**Example: `report:export`**

| Plan | RBAC role has `report:export` | Outcome | Reason |
|------|-------------------------------|---------|--------|
| FREE | Yes | Denied | `PLAN_NOT_ALLOWED` (Layer 3 stops here) |
| PRO | No | Denied | `RBAC_DENIED` (Layer 4) |
| PRO | Yes | Allowed | Both gates pass |
| FREE | No | Denied | `PLAN_NOT_ALLOWED` (first gate reached) |

### Pending security module tasks for RBAC/Groups

These tasks are **not in the existing account-resource-ownership-backlog.md** and need to be created separately:

| Task | Description | Depends on |
|------|-------------|------------|
| SEC-G1: Group entity and GroupMembership | Create `security.groups` and `security.group_memberships` tables and entities | M1 complete |
| SEC-G2: RBAC role and permission assignment | Create permission assignment to groups/roles; implement RBAC evaluation | SEC-G1 |
| SEC-G3: RBAC gate enforcement | Integrate group/role permission check at Layer 4 in `GraphQLPermissionHelper` | SEC-G2, M4 complete |
| SEC-G4: Group management UI and API | CRUD for groups; add/remove members from groups | SEC-G3 |

> **Recommendation**: Create a `rbac-groups-backlog.md` under `docs/03-features/security/rbac/` for these tasks.

---

## Open Decisions (from billing blueprint)

Decisions that must be resolved before affected tasks can close:

| Decision | Affects | Status |
|----------|---------|--------|
| Billing anchor day policy (signup day vs fixed calendar day) | B0-T2 (price/interval seed), B4-T2 | Open |
| Grace period duration for `PAST_DUE` | B5-T1 | Open (suggested: 7 days) |
| Proration policy defaults for upgrades/downgrades | B4-T2 | Open (suggested: upgrade=prorate, downgrade=next-cycle) |
| Whether v1 includes scheduled plan changes | B4-T2 | Open |
| Action catalog governance change process | B0-T4 | Resolved → see [Action and Capacity Registries](../security/resource-ownership/action-capacity-registries.md) |
| Quota reset policy: calendar vs rolling windows | B2-T2 | Open (suggested: monthly = calendar, rpm = rolling) |

---

## Definition of Done for Billing Backlog

This feature is done when:
- [ ] All phases B0–B4 are implemented and tested.
- [ ] `plan_allows(action_key)` is enforced at Layer 3 for all account-owned operations.
- [ ] `capacity_available(metric_key)` is enforced at Layer 7 for quota-limited operations.
- [ ] Rate limiter is enforced for `api.rate_limit.rpm`.
- [ ] Stripe integration handles checkout, webhooks, and self-serve billing flows.
- [ ] All billing mutations are gated by `billing:manage` permission + OWNER/ADMIN role ceiling.
- [ ] Plan gate and capacity gate denial codes (`PLAN_NOT_ALLOWED`, `QUOTA_EXCEEDED`, `RATE_LIMITED`) are deterministic and observable.
- [ ] Observability metrics and alerts from B5-T3 are live.
- [ ] All open decisions above are resolved.
- [ ] Security audit: no PCI-scope data stored; webhook endpoints require signature verification.

## Suggested Execution Order

1. **B0** in parallel with security **M1–M3** (no direct dependency).
2. **B1-T1** after security **M2-T1** (AccountService created).
3. **B2-T1** after security **M5-T5** (contract agreed) and **B0-T5** (PlanAccessResolver).
4. **B3** after **B1** is complete.
5. **B4** after **B3** is complete.
6. **B5-T1** (grace period) can be done in parallel with **B3**.
7. **B5-T4** (frontend) after **B4** is complete.
8. RBAC/Groups tasks (**SEC-G1–G4**) can be tracked in a separate backlog and run in parallel with B2–B3.
