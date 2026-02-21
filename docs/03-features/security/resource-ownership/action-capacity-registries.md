---
title: Action and Capacity Registries
type: reference
category: security
status: draft
version: 0.1.0
tags: [security, authorization, billing, action-catalog, capacity, plan-allowlist, quota]
ai_optimized: true
search_keywords: [action key, capacity metric, plan allowlist, quota, RBAC]
related:
  - docs/03-features/security/resource-ownership/account-resource-ownership-requirements.md
  - docs/03-features/billing/billing-blueprint.md
  - docs/03-features/security/resource-ownership/account-resource-ownership-backlog.md
last_updated: 2026-02-11
---

# Action and Capacity Registries

This document defines the **canonical action catalog** and **capacity metric catalog** shared by security (plan gate, RBAC, permission matrix) and billing (plan allowlist, quota/rate limits). All plan allowlist and RBAC action keys, and all quota/rate-limit metric keys, must come from these catalogs. Used by [Account Resource Ownership Requirements](account-resource-ownership-requirements.md) (permission matrix) and [Billing Blueprint](../../billing/billing-blueprint.md) (plan access and quota model).

---

## Overview

A single **action catalog** is used for:

- Plan allowlist: `plan_allows(action_key)` — account-level commercial ceiling
- RBAC and permission matrix: which actions a user/role can perform

A single **capacity metric catalog** is used for:

- Plan limits and quota checks: `capacity_available(metric_key)` or equivalent
- Permission matrix "Metric key" column where operations are capacity-limited

No duplicate keys; no `feature.*`-style key for a behavior already represented by an action key (e.g. use `report:export`, not `feature.advanced_reports`).

---

## Action Catalog

The permission matrix and plan allowlist **must only use action keys from this catalog**. Do not introduce a key of the form `feature.*` for a behavior that is already represented by an action key.

| Action key | Namespace | Description | Source |
|------------|-----------|-------------|--------|
| `account:create` | account | Create new account (FAMILY/BUSINESS) | Permission matrix (FR-5.1) |
| `account:update` | account | Update account details (OWNER only) | Permission matrix (FR-5.2) |
| `account:delete` | account | Delete account or personal account (keep profile) | Permission matrix (FR-5.3, FR-5.4) |
| `account:switch` | account | Switch current account context | API / M4 |
| `account:invite` | account | Send invitation to join account | Permission matrix (FR-4.1) |
| `account:invitation_cancel` | account | Cancel pending invitation | Permission matrix (FR-4.4) |
| `account:invitation_accept` | account | Accept invitation (invitee) | Permission matrix (FR-4.2) |
| `account:invitation_decline` | account | Decline invitation (invitee) | Permission matrix (FR-4.3) |
| `account:members_view` | account | View account members | Permission matrix (FR-6.1) |
| `account:members_change_role` | account | Change member role (OWNER only) | Permission matrix (FR-6.2) |
| `account:members_remove` | account | Remove member | Permission matrix (FR-6.3) |
| `account:leave` | account | Leave account | Permission matrix (FR-6.4) |
| `account:transfer_ownership` | account | Transfer ownership to another member | Permission matrix (FR-6.5) |
| `report:view` | report | View reports | Billing blueprint |
| `report:export` | report | Export reports | Billing blueprint |
| `budget:create` | budget | Create budget | Billing blueprint |

---

## Capacity Metric Catalog

Plan limits and quota checks use **metric keys from this catalog**. For periodic quotas (daily/monthly), use durable account usage counters and atomic compare-and-increment semantics (see billing blueprint).

| Metric key | Description | Period (if applicable) | Source |
|------------|-------------|------------------------|--------|
| `invitations.pending_per_account` | Max pending invitations per account | — | Permission matrix (Phase 2 optional) |
| `accounts.max_per_user` | Max accounts per user | — | Permission matrix (Phase 2 optional) |
| `budgets.max` | Max budgets per account | e.g. monthly | Billing blueprint |
| `storage.gb` | Storage quota (GB) per account | — | Billing blueprint |
| `api.rate_limit.rpm` | API rate limit (requests per minute) | rolling | Billing blueprint |

---

## Ownership and Change Process

- **Action catalog**
  - **Security** owns `account:*` and authorization-related keys. New account action keys require a PR that updates this document and the [Permission Matrix](account-resource-ownership-requirements.md#permission-matrix-for-account-roles) as needed.
  - **Billing / product** owns product action keys (e.g. `report:*`, `budget:*`). New keys require a PR that updates this document and billing blueprint or plan config as needed.
  - No duplicate keys. No `feature.*` key for a behavior that already has an action key.

- **Capacity metric catalog**
  - **Billing** owns metric definitions and plan-quota mapping. Security/requirements reference metric keys for account operations (e.g. invitations, accounts).
  - New metrics require a PR that updates this document and any plan/quota configuration.

This satisfies the billing blueprint open decision on [Action catalog governance](../../billing/billing-blueprint.md#open-decisions).

---

## Plan Gate Semantics

**Absent in plan allowlist = denied.**

If an action key is **not** present in the account's plan allowlist, the plan gate **denies** the action. The denial reason is `PLAN_NOT_ALLOWED`. There is no default allow. This applies to all account-owned features and aligns with the billing blueprint: "If an action key is absent from the plan allowlist, it is denied by default."
