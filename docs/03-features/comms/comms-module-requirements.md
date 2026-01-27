# Comms Module Requirements

## Overview
- Purpose: centralize all synchronous and asynchronous communications in a single `comms` module with clear contracts and clean boundaries.
- Scope: internal 1:1 chat, async notifications via multiple channels, template handling, and Kafka-based orchestration.
- Out of scope (explicit): advanced chat features, scheduling/prioritization, marketing automation, and governance controls (see TODO section).

## Goals
- Centralize all communications (sync + async) in one module.
- Provide a simple internal chat (not Slack-like).
- Support async notifications via multiple channels.
- Keep the module easy to extend later, but only implement what is explicitly required now.
- Favor clear contracts, clean boundaries, and evolution paths.

## Functional Requirements

### 1) Synchronous Communication (Internal Chat)
**Scope:** lightweight internal chat only.

**Must implement now:**
- Internal chat 1:1 (user-to-user).
- Conversation threads.
- Message reactions (👍 👀 etc.).
- Read receipts.
- User presence (online / away / offline).
- Typing indicator.

**Explicitly NOT required now:**
- Mentions.
- File uploads.
- Search.
- Bots.
- Advanced moderation.

**Prepared (no implementation):**
- Group / team chat.
  - Data model and APIs must not block future group chat support.

### 2) Asynchronous Communication (Notifications)

**Channels to implement now:**
- Email (SMTP / external provider).
- Push notifications (Web + Mobile).
- WhatsApp Business API.
- In-app notifications.
- Outbound webhooks (event-driven).

**Prepared only (no implementation):**
- SMS (define interfaces/contracts only).

### 3) Message Orchestration

**Backbone:** Kafka.

**Flow:** Request → Kafka event → channel-specific consumers.

**Required:**
- Retry mechanism.
- Dead Letter Queue (DLQ).

**Not required:**
- Message prioritization.
- Scheduling.
- Complex routing logic.

### 4) Templates & Content

**Requirements:**
- Template engine per channel (email, chat, WhatsApp, push, in-app).
- Variable substitution (placeholders).
- i18n support (e.g., pt-BR, en-US).
- Simple template lookup by key.

**Explicitly NOT required:**
- Template versioning.
- A/B testing.
- Advanced personalization.
- Marketing automation.

### 5) Integrations & Providers

#### Email
- Use Micronaut email support.
- Implement Strategy Pattern for providers.
- Example providers: Gmail, Hostinger, SMTP.

#### Push
- Abstract provider interface.
- Assume Firebase / APNs / Web Push behind the scenes.
- Module handles tokens and send requests only.

#### WhatsApp
- WhatsApp Business API.
- Support outbound messages.
- Support inbound webhooks (status updates, replies).
- Webhook signature verification is required.

#### Webhooks (Outbound)
- Event-based delivery.
- Signed payloads (HMAC or equivalent).
- Retry + DLQ.

## Non-Goals (Explicit)
- Mentions, file uploads, search, bots, and advanced moderation for chat.
- Message prioritization, scheduling, or complex routing.
- Template versioning, A/B testing, advanced personalization, or marketing automation.

## Prepared for Future (Document Only)
- Group/team chat support (data model and APIs must remain compatible).
- SMS channel (interfaces/contracts only, no provider integration).

## Explicit TODO (Do NOT implement now)
- User preferences & consent (opt-in / opt-out).
- Observability dashboards & advanced metrics.
- Governance features:
  - Approval workflows.
  - Quotas.
  - Cost control.
  - Advanced analytics.

## Integration Notes
- Kafka is the central orchestration backbone for all async channels.
- Channel consumers are isolated per provider/channel.
- Signature verification is mandatory for inbound WhatsApp webhooks.
- Signed payloads are mandatory for outbound webhooks.

## Implementation Plan (Checkpoints)

> Goal: deliver incrementally, with clear checkpoints per feature area and dependencies respected.

### Phase 0 — Foundations
**Checkpoint 0.1: Module setup (baseline)**
- Create `service/kotlin/comms` module mirroring structure of `service/kotlin/assets`.
- Define package root `io.github.salomax.neotool.comms`.
- Add basic Micronaut wiring, module build config, and CI wiring.
- No features yet; only scaffolding.

### Phase 1 — Email (first channel)
**Checkpoint 1.1: Email API surface**
- Create email send request DTOs and validation.
- Define service interface and use-case boundary (e.g., `EmailSendService`).
- Define event schema for `EMAIL_SEND_REQUESTED`.

**Checkpoint 1.2: Email orchestration**
- Produce Kafka event on email send request.
- Implement email consumer with retry + DLQ.
- Implement Micronaut email integration with Strategy Pattern provider registry.
- Implement provider selection (e.g., Gmail, Hostinger, SMTP).

### Phase 2 — Kafka backbone (shared orchestration)
**Checkpoint 2.1: Event bus standardization**
- Standardize event envelope format (id, type, trace, payload, createdAt).
- Define topic naming conventions per channel.
- Implement retry policy configuration shared across channels.

**Checkpoint 2.2: DLQ handling**
- Implement DLQ topics and dead-letter consumer hooks.
- Define minimal operational logs for failed deliveries.

### Phase 3 — Templates & Content
**Checkpoint 3.1: Template registry**
- Define template lookup by key and locale.
- Implement variable substitution interface.
- Per-channel template engine contracts (email, chat, WhatsApp, push, in-app).

**Checkpoint 3.2: i18n**
- Add locale-aware template resolution (e.g., `pt-BR`, `en-US`).
- Define fallback rules (e.g., locale -> default locale).

### Phase 4 — In-app Notifications
**Checkpoint 4.1: In-app data model**
- Define `InAppNotification` entity and storage.
- Define read/unread tracking and basic CRUD.

**Checkpoint 4.2: In-app delivery**
- Consume Kafka events for in-app notifications.
- Persist and expose API to fetch and mark as read.

### Phase 5 — Push Notifications
**Checkpoint 5.1: Push contracts**
- Define device token model (web + mobile).
- Define provider interface and send request contract.

**Checkpoint 5.2: Push delivery**
- Produce Kafka events for push send requests.
- Implement consumer and provider abstraction (Firebase/APNs/Web Push behind interface).
- Retry + DLQ.

### Phase 6 — WhatsApp Business API
**Checkpoint 6.1: Outbound WhatsApp**
- Define outbound message contract and event schema.
- Implement consumer + provider integration.
- Retry + DLQ.

**Checkpoint 6.2: Inbound WhatsApp webhooks**
- Implement webhook endpoint.
- Add signature verification.
- Map inbound events (status updates, replies) to internal events.

### Phase 7 — Outbound Webhooks
**Checkpoint 7.1: Webhook dispatcher**
- Define webhook registration/config contract.
- Define payload signing (HMAC or equivalent).
- Implement event-driven delivery with retry + DLQ.

### Phase 8 — Internal Chat (Sync)
**Checkpoint 8.1: 1:1 chat base**
- Implement conversation threads and messages.
- Add reactions, read receipts.

**Checkpoint 8.2: Presence & typing**
- Implement presence state (online/away/offline).
- Implement typing indicator events.

### Phase 9 — Prepared Only (No Implementation)
**Checkpoint 9.1: Group chat readiness**
- Ensure data models and APIs support future group conversations without breaking changes.

**Checkpoint 9.2: SMS contracts**
- Define `SmsProvider` interface and request/response contracts only.

### Explicitly Excluded in All Phases
- User preferences & consent (opt-in/opt-out).
- Observability dashboards & advanced metrics.
- Governance features: approval workflows, quotas, cost control, advanced analytics.
