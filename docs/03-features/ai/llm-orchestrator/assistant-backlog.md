---
title: Assistant Service Implementation Backlog
type: backlog
category: ai
status: draft
version: 1.0.0
tags: [ai, assistant, llm, backlog, epics, stories, tasks, openai, tool-calling, streaming]
ai_optimized: true
search_keywords: [assistant service backlog, llm orchestrator tasks, openai integration, tool calling backlog, faq chatbot escalation, structured output]
related:
  - 03-features/ai/llm-orchestrator/llm-orchestrator-blueprint.md
  - 02-architecture/service-architecture.md
  - 05-backend/patterns/service-pattern.md
  - 05-backend/patterns/repository-pattern.md
  - 06-contracts/graphql-standards.md
last_updated: 2026-03-04
---

# Assistant Service Implementation Backlog

> **Purpose**: Break down the `assistant` service implementation into actionable Epics, Stories, Tasks, and Subtasks. Each item maps directly to the blueprint spec and follows project patterns (Kotlin + Micronaut, GraphQL Federation subgraph, PostgreSQL + Flyway).

## 1) Hierarchy Model

```
Feature: Assistant Service
  Epic:    Major deliverable aligned to a blueprint phase
  Story:   System or user outcome that can be independently validated
  Task:    Technical unit of work (single PR scope)
  Subtask: Precise implementation step within a task
```

## 2) Scope Summary

### In Scope (v1 — Epics 1–5)

- Service scaffold as Apollo Federation subgraph
- Prompt management (CRUD, versioning, variables)
- One-shot completion via OpenAI (text and JSON mode)
- Short conversation context (FAQ chatbot, sliding window)
- Escalation pattern (LLM signals human handoff)
- Tool calling (registry + OpenAI function calling format)
- Rate limiting (account, service, feature) + budget enforcement
- Retry logic + circuit breaker for provider
- Token counting and cost tracking (`UsageLog`)
- Observability: metrics, structured logging, alerts

### Deferred (Epics 6–8)

- Streaming SSE (Epic 6)
- Multi-provider / Anthropic (Epic 7)
- Response caching (Epic 8 — when volume justifies)

### Out of Scope

- PII detection and redaction pipeline
- Embedding generation or vector search
- Prompt A/B testing framework
- Admin UI for prompt management

## 3) Epic Roadmap

| Code | Epic | Phases |
|------|------|--------|
| `AST-E01` | Foundation + One-shot Completion | Phase 1 |
| `AST-E02` | Structured Output | Phase 2 |
| `AST-E03` | FAQ Conversation + Escalation | Phase 3 |
| `AST-E04` | Tool Calling | Phase 4 |
| `AST-E05` | Rate Limiting + Resilience | Phase 5 |
| `AST-E06` | Streaming | Phase 6 |
| `AST-E07` | Multi-Provider | Phase 7 |
| `AST-E08` | Caching | Phase 8 |

---

## Epic `AST-E01` — Foundation + One-shot Completion

### Story `AST-S011` — Service Scaffold and Federation Registration

**Outcome**: `assistant` subgraph is reachable via Apollo Router and follows the same project conventions as `app` and `security`.

#### Tasks

- [ ] `AST-S011-T01` Bootstrap Kotlin + Micronaut service module.
  - **Subtasks**:
    - [ ] Update `assistant` module to root `build.gradle.kts`.
    - [ ] Set up `application.yml` with service name, port, and datasource config.
    - [ ] Configure Apollo Federation subgraph headers and SDL endpoint.
    - [ ] Add `AssistantApplication.kt` entry point.
  - **Files**:
    - `service/kotlin/build.gradle.kts`
    - `service/kotlin/assistant/build.gradle.kts` (new)
    - `service/kotlin/assistant/src/main/resources/application.yml` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/AssistantApplication.kt` (new)

- [ ] `AST-S011-T02` Register subgraph in Apollo Router configuration.
  - **Subtasks**:
    - [ ] Add `assistant` subgraph URL to `router.yaml`.
    - [ ] Add service to local `docker-compose` and K8s manifests.
    - [ ] Verify introspection returns valid SDL.
  - **Files**:
    - `service/gateway/router.yaml`
    - `infra/docker/docker-compose.local.yml`

- [ ] `AST-S011-T03` Add GraphQL wiring factory.
  - **Subtasks**:
    - [ ] Create `AssistantWiringFactory` implementing project's wiring pattern.
    - [ ] Register query and mutation type wiring stubs (empty resolvers).
    - [ ] Ensure service starts without errors.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/AssistantWiringFactory.kt` (new)
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls` (new)

#### Acceptance Criteria

- [ ] `./gradlew :assistant:run` starts without errors.
- [ ] Apollo Router resolves `assistant` subgraph SDL.
- [ ] Service follows the same module layout as `app` and `security`.

---

### Story `AST-S012` — Database Schema (Flyway Migrations)

**Outcome**: All assistant tables exist in the database with correct constraints and indexes.

#### Tasks

- [ ] `AST-S012-T01` Create Flyway migration `V001__initial_schema.sql`.
  - **Subtasks**:
    - [ ] Create `assistant` schema.
    - [ ] Table `prompts`: `id`, `name`, `category`, `description`, `tags`, `created_by`, `created_at`, `updated_at`.
    - [ ] Table `prompt_versions`: `id`, `prompt_id`, `version`, `status`, `template`, `variables_schema`, `output_format`, `model_requirements`, `performance_metrics`, `created_at`, `updated_at`.
    - [ ] Table `llm_providers`: `id`, `name`, `type`, `base_url`, `api_key_ref`, `status`, `priority`, `created_at`, `updated_at`.
    - [ ] Table `llm_models`: `id`, `provider_id`, `model_name`, `display_name`, `capabilities`, `context_window`, `max_output_tokens`, `cost_per_input_token`, `cost_per_output_token`, `status`, `created_at`, `updated_at`.
    - [ ] Table `completion_requests`: `id`, `account_id`, `requested_by_service`, `user_id`, `prompt_id`, `prompt_version_id`, `messages`, `variables`, `options`, `purpose`, `metadata`, `created_at`.
    - [ ] Table `completion_responses`: `id`, `request_id`, `account_id`, `provider_id`, `model_id`, `response`, `finish_reason`, `prompt_tokens`, `completion_tokens`, `total_tokens`, `latency_ms`, `cost`, `escalate`, `completed_at`.
    - [ ] Table `usage_log`: `id`, `account_id`, `request_id`, `service`, `user_id`, `feature`, `purpose`, `prompt_tokens`, `completion_tokens`, `total_tokens`, `cost`, `model_id`, `timestamp`.
    - [ ] Table `usage_budgets`: `id`, `scope`, `scope_id`, `period`, `limit_amount`, `current_usage`, `alert_threshold`, `created_at`, `updated_at`.
    - [ ] Add all `NOT NULL` and `UNIQUE` constraints per blueprint spec.
    - [ ] Add all indexes per blueprint spec.
    - [ ] Use `DEFAULT uuidv7()` for all primary keys.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V001__initial_schema.sql` (new)

- [ ] `AST-S012-T02` Verify migration integrity.
  - **Subtasks**:
    - [ ] Run `./gradlew :assistant:flywayMigrate` on empty database.
    - [ ] Run `./gradlew :assistant:flywayInfo` and confirm all migrations applied.
    - [ ] Verify `NOT NULL account_id` on `completion_requests`, `completion_responses`, `usage_log`.
    - [ ] Verify `UNIQUE (prompt_id, version)` on `prompt_versions`.
    - [ ] Verify `UNIQUE WHERE status = 'active'` partial index for active prompt version.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V001__initial_schema.sql`

#### Acceptance Criteria

- [ ] Migration applies cleanly on empty database.
- [ ] All constraints and indexes are present and named consistently.
- [ ] `account_id NOT NULL` is enforced on all scoped tables.

---

### Story `AST-S013` — Domain Entities and Repositories

**Outcome**: JPA entities and repositories are implemented, tested, and ready for service layer use.

#### Tasks

- [ ] `AST-S013-T01` Implement domain enums and value types.
  - **Subtasks**:
    - [ ] `PromptVersionStatus`: `DRAFT`, `ACTIVE`, `DEPRECATED`.
    - [ ] `OutputFormat`: `TEXT`, `JSON`.
    - [ ] `ProviderType`: `OPENAI`, `ANTHROPIC`, `GOOGLE`, `AZURE`.
    - [ ] `ModelStatus`: `ACTIVE`, `DEPRECATED`, `DISABLED`.
    - [ ] `BudgetScope`: `ACCOUNT`, `SERVICE`, `FEATURE`.
    - [ ] `BudgetPeriod`: `DAILY`, `WEEKLY`, `MONTHLY`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/domain/enums/` (new package)

- [ ] `AST-S013-T02` Implement JPA entities.
  - **Subtasks**:
    - [ ] `PromptEntity`, `PromptVersionEntity`.
    - [ ] `LLMProviderEntity`, `LLMModelEntity`.
    - [ ] `CompletionRequestEntity`, `CompletionResponseEntity`.
    - [ ] `UsageLogEntity`, `UsageBudgetEntity`.
    - [ ] Add `@Version` (optimistic locking) on all mutable entities.
    - [ ] Add audit timestamps (`created_at`, `updated_at`) following project entity pattern.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/entity/` (new package)

- [ ] `AST-S013-T03` Implement repositories.
  - **Subtasks**:
    - [ ] `PromptRepository`: `findByCategory`, `findByTags`, `findActiveVersion(promptId)`.
    - [ ] `PromptVersionRepository`: `findByPromptIdAndStatus(promptId, ACTIVE)`.
    - [ ] `LLMModelRepository`: `findByStatusAndCapabilitiesContaining(ACTIVE, capability)`.
    - [ ] `CompletionRequestRepository`: `findByAccountIdAndCreatedAtDesc`, `findByRequestedByService`.
    - [ ] `UsageLogRepository`: `findByAccountIdAndTimestampBetween`, `findByServiceAndTimestampBetween`.
    - [ ] `UsageBudgetRepository`: `findByScopeAndScopeId`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/repository/` (new package)

- [ ] `AST-S013-T04` Add repository integration tests.
  - **Subtasks**:
    - [ ] Test `findActiveVersion` returns exactly one active prompt version.
    - [ ] Test `findByAccountId` queries do not return other account rows.
    - [ ] Test `UsageBudget` upsert logic.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/integration/repository/` (new package)

#### Acceptance Criteria

- [ ] Entity mappings match migration schema (column names, types, constraints).
- [ ] Repository integration tests use Testcontainers and pass.
- [ ] `findActiveVersion` enforces single-active-version invariant.

---

### Story `AST-S014` — Prompt Management (CRUD + Versioning)

**Outcome**: Operators can create, version, publish, and deprecate prompts via GraphQL.

#### Tasks

- [ ] `AST-S014-T01` Implement `PromptService`.
  - **Subtasks**:
    - [ ] `createPrompt(input)` — creates a prompt with an initial `DRAFT` version.
    - [ ] `createPromptVersion(promptId, input)` — adds a new `DRAFT` version.
    - [ ] `publishPromptVersion(promptId, versionId)` — sets target version to `ACTIVE`, deprecates previous `ACTIVE`.
    - [ ] `findPrompts(category, tags)` — list with filter.
    - [ ] `findPromptById(id)` — fetch with active version.
    - [ ] `renderTemplate(versionId, variables)` — interpolates template with validated variables.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/PromptService.kt` (new)

- [ ] `AST-S014-T02` Implement GraphQL schema types and resolvers for prompts.
  - **Subtasks**:
    - [ ] Add `Prompt`, `PromptVersion`, `PromptVariable` types to schema.
    - [ ] Add queries: `prompts(category, tags)`, `prompt(id)`, `promptVersions(promptId)`.
    - [ ] Add mutations: `createPrompt`, `publishPromptVersion`.
    - [ ] Implement `PromptResolver` injecting `PromptService`.
    - [ ] Register in `AssistantWiringFactory`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `contracts/graphql/subgraphs/assistant/schema.graphqls` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/PromptResolver.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/dto/PromptDTOs.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/mapper/PromptMapper.kt` (new)

- [ ] `AST-S014-T03` Add unit and integration tests for prompt management.
  - **Subtasks**:
    - [ ] Unit test: `publishPromptVersion` sets previous `ACTIVE` to `DEPRECATED`.
    - [ ] Unit test: `renderTemplate` replaces all declared variables correctly.
    - [ ] Unit test: `renderTemplate` raises error for missing required variables.
    - [ ] Integration test: full prompt lifecycle via GraphQL.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/PromptServiceTest.kt` (new)
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/integration/PromptIntegrationTest.kt` (new)

#### Acceptance Criteria

- [ ] At most one `ACTIVE` version exists per prompt at any time.
- [ ] Publishing a version deprecates the previous active version atomically.
- [ ] Template rendering fails fast on missing required variables.

---

### Story `AST-S015` — OpenAI Provider Client

**Outcome**: The service can call OpenAI's completion API via a typed, testable abstraction.

#### Tasks

- [ ] `AST-S015-T01` Define `LLMProviderClient` interface and request/response models.
  - **Subtasks**:
    - [ ] Define `LLMProviderClient` interface with `complete` and `estimateTokens`.
    - [ ] Define `ProviderCompletionRequest` (model, messages, temperature, maxTokens, tools, responseFormat, stream).
    - [ ] Define `ProviderCompletionResponse` (id, content, finishReason, usage, toolCalls).
    - [ ] Define `ProviderMessage`, `ProviderTool`, `ProviderToolCall` value types.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/LLMProviderClient.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/model/` (new package)

- [ ] `AST-S015-T02` Implement `OpenAIProviderClient`.
  - **Subtasks**:
    - [ ] Configure HTTP client with OpenAI base URL and API key from `apiKeyRef`.
    - [ ] Implement `complete(request)` — maps internal request to OpenAI chat completions API format and back.
    - [ ] Implement `estimateTokens(text)` — use tiktoken-compatible byte-pair encoding.
    - [ ] Handle HTTP error codes: `429` (rate limit), `500-503` (server errors), `401` (auth errors).
    - [ ] Set `User-Agent` and request timeout headers.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIProviderClient.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIMapper.kt` (new)

- [ ] `AST-S015-T03` Implement `MockProviderClient` for tests.
  - **Subtasks**:
    - [ ] `MockProviderClient` returns configurable fixed responses.
    - [ ] Supports simulating rate limits, timeouts, and server errors.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/mock/MockProviderClient.kt` (new)

- [ ] `AST-S015-T04` Add unit tests for provider client.
  - **Subtasks**:
    - [ ] Test OpenAI request mapping (messages, tools, JSON mode).
    - [ ] Test response parsing (content, finishReason, usage, toolCalls).
    - [ ] Test error mapping (429 → `RATE_LIMIT`, 500 → `SERVER_ERROR`).
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/provider/OpenAIProviderClientTest.kt` (new)

#### Acceptance Criteria

- [ ] `OpenAIProviderClient` maps requests and responses without losing data.
- [ ] HTTP error codes are translated to typed `ErrorType` enum values.
- [ ] `MockProviderClient` is usable as drop-in replacement in all service tests.

---

### Story `AST-S016` — `generateCompletion` Endpoint

**Outcome**: Any authenticated service can request a one-shot LLM completion.

#### Tasks

- [ ] `AST-S016-T01` Implement `CompletionService`.
  - **Subtasks**:
    - [ ] `generateCompletion(input, accountId)` orchestration:
      1. Resolve prompt template (if `promptId` provided) and render variables.
      2. Build message list (system prompt + input messages).
      3. Select model (primary model from active `LLMProvider`).
      4. Call `LLMProviderClient.complete()`.
      5. Persist `CompletionRequest` + `CompletionResponse`.
      6. Return `CompletionResponse` domain object.
    - [ ] Validate `purpose` field is present (LGPD requirement).
    - [ ] Reject request if resolved message list is empty.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt` (new)

- [ ] `AST-S016-T02` Implement `generateCompletion` mutation resolver.
  - **Subtasks**:
    - [ ] Add `CompletionInput`, `CompletionResponse`, `TokenUsage` types to schema.
    - [ ] Add `generateCompletion(input: CompletionInput!): CompletionResponse!` mutation.
    - [ ] Implement `CompletionResolver` injecting `CompletionService`.
    - [ ] Extract `accountId` from authenticated JWT context — never from client input.
    - [ ] Register in `AssistantWiringFactory`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/CompletionResolver.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/dto/CompletionDTOs.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/mapper/CompletionMapper.kt` (new)

- [ ] `AST-S016-T03` Add unit and integration tests.
  - **Subtasks**:
    - [ ] Unit test: `CompletionService` with `MockProviderClient`.
    - [ ] Unit test: request missing `purpose` is rejected.
    - [ ] Integration test: full `generateCompletion` mutation via GraphQL with mock provider.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/CompletionServiceTest.kt` (new)
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/integration/CompletionIntegrationTest.kt` (new)
  - **Commands**:
    - `./neotool graphql sync` — regenerate supergraph after schema change
    - `./neotool graphql generate --all` — regenerate typed clients

#### Acceptance Criteria

- [ ] `generateCompletion` persists both `CompletionRequest` and `CompletionResponse` on success.
- [ ] `accountId` is derived from JWT — not from `CompletionInput`.
- [ ] Requests without `purpose` are rejected with a validation error.
- [ ] Integration tests pass using `MockProviderClient`.

---

### Story `AST-S017` — Token Counting and Cost Tracking

**Outcome**: Every completion records token usage and cost in `UsageLog`.

#### Tasks

- [ ] `AST-S017-T01` Implement `UsageTrackingService`.
  - **Subtasks**:
    - [ ] `recordUsage(completionResponse, accountId, service, feature, purpose)` — writes `UsageLog` row.
    - [ ] `getUsageSummary(accountId, period)` — aggregates tokens and cost.
    - [ ] `getServiceUsage(serviceId, period)` — aggregates by service.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/UsageTrackingService.kt` (new)

- [ ] `AST-S017-T02` Add usage queries to GraphQL schema.
  - **Subtasks**:
    - [ ] Add `UsageStats`, `ModelUsage`, `FeatureUsage` types to schema.
    - [ ] Add queries: `myUsage(period)`, `serviceUsage(serviceId, period)`.
    - [ ] Implement `UsageResolver`.
    - [ ] Register in `AssistantWiringFactory`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/UsageResolver.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/dto/UsageDTOs.kt` (new)

#### Acceptance Criteria

- [ ] Every `CompletionResponse` triggers a `UsageLog` write.
- [ ] `myUsage` and `serviceUsage` aggregate correctly over the requested period.
- [ ] `cost` and `tokens` are never negative (check constraint enforced at DB level).

---

### Story `AST-S018` — Observability Foundation

**Outcome**: All LLM requests are measurable, debuggable, and auditable.

#### Tasks

- [ ] `AST-S018-T01` Add Micrometer metrics.
  - **Subtasks**:
    - [ ] `assistant_requests_total{service, model, status, output_format}` counter.
    - [ ] `assistant_request_duration_ms` histogram.
    - [ ] `assistant_tokens_total{service, model, token_type}` counter.
    - [ ] `assistant_cost_total{service, model}` counter.
    - [ ] `assistant_provider_errors_total{provider, error_type}` counter.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/observability/AssistantMetrics.kt` (new)

- [ ] `AST-S018-T02` Add structured logging.
  - **Subtasks**:
    - [ ] `completion_request` log: `service`, `accountId`, `promptId`, `purpose`, `model`, `outputFormat`, `estimatedTokens`.
    - [ ] `completion_response` log: `requestId`, `model`, `tokens`, `latencyMs`, `cost`, `escalate`.
    - [ ] `provider_error` log: `provider`, `errorType`, `statusCode`, `requestId`.
    - [ ] Ensure no PII in log fields.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`

- [ ] `AST-S018-T03` Configure Grafana dashboard and alerts.
  - **Subtasks**:
    - [ ] Create dashboard with: request rate, error rate, P95 latency, cost/hour, tokens/hour.
    - [ ] Add alert: error rate >5% in 5-minute window.
    - [ ] Add alert: P95 latency >10s.
    - [ ] Add alert: circuit breaker open (Phase 5, placeholder panel now).
  - **Files**:
    - `infra/observability/dashboards/assistant-service.json` (new)
    - `infra/observability/alerts/assistant-alerts.yml` (new)

#### Acceptance Criteria

- [ ] Metrics are scraped by Prometheus and visible in Grafana.
- [ ] Structured logs include `requestId`, `accountId`, `model`, and `purpose` on every request.
- [ ] No PII (user content, email, names) appears in logs or metric labels.

---

## Epic `AST-E02` — Structured Output

### Story `AST-S021` — JSON Mode Support

**Outcome**: Callers can request a structured JSON response, validated against a schema.

#### Tasks

- [ ] `AST-S021-T01` Add `OutputFormat.JSON` to `CompletionService`.
  - **Subtasks**:
    - [ ] When `outputFormat = JSON`, set OpenAI `response_format: { type: "json_object" }`.
    - [ ] Store `outputFormat` on `CompletionRequest` for audit.
    - [ ] Parse response body as JSON; return error if LLM response is not valid JSON.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIMapper.kt`

- [ ] `AST-S021-T02` Add `outputFormat` to `PromptVersion` configuration.
  - **Subtasks**:
    - [ ] Store `output_format` on `prompt_versions` table (migration already includes field).
    - [ ] When `promptId` is provided and `PromptVersion.outputFormat = JSON`, default the request to JSON mode.
    - [ ] Allow `CompletionInput.outputFormat` to override prompt-level default.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/PromptService.kt`

- [ ] `AST-S021-T03` Add unit tests for JSON mode.
  - **Subtasks**:
    - [ ] Test: `outputFormat = JSON` sets correct OpenAI request parameter.
    - [ ] Test: invalid JSON response from provider raises typed error.
    - [ ] Test: prompt-level `outputFormat` is applied when no override is provided.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/CompletionServiceJsonModeTest.kt` (new)

#### Acceptance Criteria

- [ ] `generateCompletion` with `outputFormat: JSON` consistently returns parseable JSON.
- [ ] Malformed JSON responses from the provider return a typed `PROVIDER_RESPONSE_INVALID` error.
- [ ] `outputFormat` is stored on the `CompletionRequest` for audit purposes.

---

## Epic `AST-E03` — FAQ Conversation + Escalation

### Story `AST-S031` — Conversation Domain and CRUD

**Outcome**: Authenticated users can start, continue, and close FAQ conversations.

#### Tasks

- [ ] `AST-S031-T01` Create Flyway migration for conversation tables.
  - **Subtasks**:
    - [ ] Table `conversations`: `id`, `account_id`, `user_id`, `status`, `metadata`, `escalated_at`, `created_at`, `expires_at`.
    - [ ] Table `conversation_messages`: `id`, `conversation_id`, `role`, `content`, `tool_calls`, `timestamp`.
    - [ ] Constraints: `NOT NULL account_id`, `status` check (`active`, `escalated`, `closed`).
    - [ ] Index: `conversations(account_id, status, created_at DESC)`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V002__conversations.sql` (new)

- [ ] `AST-S031-T02` Implement conversation entities and repositories.
  - **Subtasks**:
    - [ ] `ConversationEntity`, `ConversationMessageEntity`.
    - [ ] `ConversationStatus` enum: `ACTIVE`, `ESCALATED`, `CLOSED`.
    - [ ] `MessageRole` enum: `USER`, `ASSISTANT`, `SYSTEM`, `TOOL`.
    - [ ] `ConversationRepository`: `findByIdAndAccountId`, `findActiveByAccountId`.
    - [ ] `ConversationMessageRepository`: `findByConversationIdOrderByTimestamp`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/entity/ConversationEntity.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/repository/ConversationRepository.kt` (new)

- [ ] `AST-S031-T03` Implement `ConversationService`.
  - **Subtasks**:
    - [ ] `startConversation(accountId, userId, metadata)` — creates `ACTIVE` conversation.
    - [ ] `getConversation(conversationId, accountId)` — enforces account scope (throws if accountId mismatch).
    - [ ] `closeConversation(conversationId, accountId)` — sets status to `CLOSED`.
    - [ ] `markEscalated(conversationId, accountId, reason)` — sets status to `ESCALATED`, sets `escalatedAt`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ConversationService.kt` (new)

- [ ] `AST-S031-T04` Add conversation mutations and queries to GraphQL schema.
  - **Subtasks**:
    - [ ] Types: `Conversation`, `Message`, `ConversationStatus`, `MessageRole`.
    - [ ] Mutations: `startConversation(metadata)`, `closeConversation(conversationId)`.
    - [ ] Query: `conversation(id)`.
    - [ ] Implement `ConversationResolver`.
    - [ ] Register in `AssistantWiringFactory`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/ConversationResolver.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/dto/ConversationDTOs.kt` (new)

#### Acceptance Criteria

- [ ] `getConversation` throws authorization error if `accountId` does not match conversation's `accountId`.
- [ ] Conversation status transitions are enforced: `ACTIVE → ESCALATED | CLOSED`.
- [ ] All conversation queries are scoped by `accountId` — no cross-account data access possible.

---

### Story `AST-S032` — `sendMessage` with Sliding Window Context

**Outcome**: Users can send messages in a conversation; context stays within bounds via sliding window.

#### Tasks

- [ ] `AST-S032-T01` Implement sliding window context truncation.
  - **Subtasks**:
    - [ ] `ContextWindowService.truncateToFit(messages, maxTokens)`:
      - Always keep `SYSTEM` role messages.
      - Keep the last `RECENT_MESSAGES_LIMIT` (default: 10) non-system messages.
      - Never truncate if total tokens already fit.
    - [ ] `RECENT_MESSAGES_LIMIT` is configurable via `application.yml`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ContextWindowService.kt` (new)

- [ ] `AST-S032-T02` Implement `sendMessage` in `ConversationService`.
  - **Subtasks**:
    - [ ] Append user message to conversation history.
    - [ ] Apply sliding window truncation.
    - [ ] Call `CompletionService.generateCompletion()` with full context.
    - [ ] Append assistant response to conversation history.
    - [ ] Detect escalation signal in response (see `AST-S033`).
    - [ ] Return `ConversationResponse` (updated conversation + last message).
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ConversationService.kt`

- [ ] `AST-S032-T03` Add `sendMessage` mutation to GraphQL schema.
  - **Subtasks**:
    - [ ] Add `sendMessage(conversationId: ID!, message: MessageInput!): ConversationResponse!`.
    - [ ] Types: `ConversationResponse` with `conversation` and `lastMessage`.
    - [ ] Implement resolver and register in wiring factory.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/ConversationResolver.kt`

- [ ] `AST-S032-T04` Add unit tests for context management.
  - **Subtasks**:
    - [ ] Test: system prompt is always preserved after truncation.
    - [ ] Test: messages beyond `RECENT_MESSAGES_LIMIT` are dropped from context (oldest non-system first).
    - [ ] Test: conversation with <10 messages is never truncated.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/ContextWindowServiceTest.kt` (new)

#### Acceptance Criteria

- [ ] `sendMessage` persists both user and assistant messages.
- [ ] Context sent to LLM never exceeds `RECENT_MESSAGES_LIMIT` non-system messages.
- [ ] System prompt is always included in context regardless of conversation length.

---

### Story `AST-S033` — Escalation Pattern

**Outcome**: When the LLM cannot answer, it signals escalation, the service detects it, and returns conversation history for human handoff.

#### Tasks

- [ ] `AST-S033-T01` Implement escalation signal detection.
  - **Subtasks**:
    - [ ] Define escalation JSON pattern: `{ "escalate": true, "reason": "<string>" }`.
    - [ ] `EscalationDetector.detect(content)`:
      - Parse response as JSON.
      - Return `EscalationSignal(escalate=true, reason)` if pattern matches.
      - Return `EscalationSignal(escalate=false)` otherwise (including non-JSON responses).
    - [ ] Never fail the request if detection parsing throws — treat as non-escalation.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/EscalationDetector.kt` (new)

- [ ] `AST-S033-T02` Integrate escalation detection into `sendMessage` flow.
  - **Subtasks**:
    - [ ] After receiving LLM response, run `EscalationDetector.detect(content)`.
    - [ ] If escalation: call `ConversationService.markEscalated(conversationId, reason)`.
    - [ ] Set `CompletionResponse.escalate = true` in response.
    - [ ] Return full conversation history in `ConversationResponse.conversation.messages`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ConversationService.kt`

- [ ] `AST-S033-T03` Add `escalate` metric counter.
  - **Subtasks**:
    - [ ] `assistant_escalations_total{reason}` counter.
    - [ ] Increment on every escalation detection.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/observability/AssistantMetrics.kt`

- [ ] `AST-S033-T04` Add unit tests for escalation.
  - **Subtasks**:
    - [ ] Test: valid escalation JSON is detected correctly.
    - [ ] Test: non-JSON response returns `escalate=false`.
    - [ ] Test: malformed JSON in escalation response returns `escalate=false` (never throws).
    - [ ] Test: conversation status is set to `ESCALATED` after detection.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/EscalationDetectorTest.kt` (new)

#### Acceptance Criteria

- [ ] Escalation detection never causes a request failure — parse errors fall back to `escalate=false`.
- [ ] `CompletionResponse.escalate = true` is set when the LLM signals escalation.
- [ ] Escalated conversation status is persisted and the full message history is available for handoff.

---

## Epic `AST-E04` — Tool Calling

### Story `AST-S041` — Tool Registry and Executor Interface

**Outcome**: Domain tools can be registered by client services and executed during LLM conversations.

#### Tasks

- [ ] `AST-S041-T01` Create Flyway migration for tool tables.
  - **Subtasks**:
    - [ ] Table `tools`: `id`, `name`, `description`, `category`, `input_schema`, `output_schema`, `executor_service`, `executor_endpoint`, `timeout_ms`, `retry_policy`, `status`, `created_at`, `updated_at`.
    - [ ] Table `tool_executions`: `id`, `completion_request_id`, `tool_id`, `input`, `output`, `status`, `error`, `latency_ms`, `executed_at`.
    - [ ] Index: `tool_executions(completion_request_id)`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V003__tools.sql` (new)

- [ ] `AST-S041-T02` Define `ToolExecutor` interface and `ToolRegistry`.
  - **Subtasks**:
    - [ ] `ToolExecutor`: `suspend fun execute(accountId: UUID, input: JsonObject): Result<JsonObject>`.
    - [ ] `ToolRegistry`: thread-safe map of `toolName → ToolExecutor`; `register(name, executor)`, `execute(name, accountId, input)`.
    - [ ] `ToolRegistry.execute` returns `Result.failure(ToolNotFoundException)` for unknown tools.
    - [ ] `accountId` is always passed to executors — enforcement of account scope.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/tool/ToolExecutor.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/tool/ToolRegistry.kt` (new)

- [ ] `AST-S041-T03` Implement `ToolService` (persistence + registration).
  - **Subtasks**:
    - [ ] `registerTool(input)` — persists tool metadata to `tools` table.
    - [ ] `getAvailableTools()` — returns all `ACTIVE` tools.
    - [ ] `recordExecution(completionRequestId, toolId, input, output, status, latencyMs)` — writes `tool_executions` row.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ToolService.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/repository/ToolRepository.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/repository/ToolExecutionRepository.kt` (new)

- [ ] `AST-S041-T04` Add `availableTools` query and `registerTool` mutation to schema.
  - **Subtasks**:
    - [ ] Types: `Tool`, `ToolStatus`, `RegisterToolInput`.
    - [ ] Add `availableTools: [Tool!]!` query.
    - [ ] Add `registerTool(input: RegisterToolInput!): Tool!` mutation (admin only).
    - [ ] Implement `ToolResolver`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/ToolResolver.kt` (new)

#### Acceptance Criteria

- [ ] `ToolRegistry` is a singleton and thread-safe.
- [ ] `execute` always receives `accountId` — no executor can be called without it.
- [ ] Unknown tool name returns typed `ToolNotFoundException`, not a 500.

---

### Story `AST-S042` — OpenAI Function Calling Integration

**Outcome**: The LLM can invoke registered tools during a conversation, with results returned back to the LLM.

#### Tasks

- [ ] `AST-S042-T01` Implement tool calling loop in `CompletionService`.
  - **Subtasks**:
    - [ ] When `tools` are provided in `CompletionInput`, map registered tools to OpenAI function definitions.
    - [ ] After initial LLM response: check if `finishReason == "tool_calls"`.
    - [ ] For each `toolCall` in response: call `ToolRegistry.execute(toolName, accountId, input)`.
    - [ ] Append `TOOL` role message with result to context.
    - [ ] Call LLM again with tool results to get final response.
    - [ ] Repeat until `finishReason != "tool_calls"` or `MAX_TOOL_ITERATIONS` (default: 5) is reached.
    - [ ] Persist each `ToolExecution` row via `ToolService.recordExecution()`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIMapper.kt`

- [ ] `AST-S042-T02` Map `Tool` schema to OpenAI function definition format.
  - **Subtasks**:
    - [ ] `toolToOpenAIFunction(tool: Tool): OpenAIFunction` — maps `name`, `description`, `inputSchema`.
    - [ ] `openAIToolCallToInput(toolCall: ProviderToolCall): JsonObject` — parses arguments JSON.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIMapper.kt`

- [ ] `AST-S042-T03` Implement `get_current_time` built-in tool.
  - **Subtasks**:
    - [ ] `GetCurrentTimeExecutor` implementing `ToolExecutor`.
    - [ ] Returns `{ "datetime": "ISO-8601", "timezone": "UTC" }`.
    - [ ] Register on application startup via `ToolRegistry`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/tool/builtin/GetCurrentTimeExecutor.kt` (new)

- [ ] `AST-S042-T04` Add unit tests for tool calling loop.
  - **Subtasks**:
    - [ ] Test: single tool call resolved in two LLM turns.
    - [ ] Test: `MAX_TOOL_ITERATIONS` limit stops infinite tool call loops.
    - [ ] Test: tool execution failure records error in `ToolExecution` and returns error message to LLM.
    - [ ] Test: `get_current_time` returns valid ISO-8601 datetime.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/service/ToolCallingTest.kt` (new)
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/tool/GetCurrentTimeExecutorTest.kt` (new)

#### Acceptance Criteria

- [ ] Tool calling loop terminates: either LLM returns a non-tool response or `MAX_TOOL_ITERATIONS` is hit.
- [ ] Every tool call is persisted as a `ToolExecution` record.
- [ ] Tool execution always receives the authenticated `accountId` — tools cannot query cross-account data.

---

## Epic `AST-E05` — Rate Limiting + Resilience

### Story `AST-S051` — Retry Logic with Exponential Backoff

**Outcome**: Transient provider errors are retried transparently.

#### Tasks

- [ ] `AST-S051-T01` Implement `RetryPolicy` and retry executor.
  - **Subtasks**:
    - [ ] `RetryPolicy`: `maxAttempts`, `initialDelay`, `maxDelay`, `multiplier`, `retryableErrors`.
    - [ ] `withRetry(retryPolicy, block)`: executes `block` with exponential backoff on retryable errors.
    - [ ] Retryable errors: `RATE_LIMIT`, `TIMEOUT`, `SERVER_ERROR`.
    - [ ] Non-retryable errors: `AUTH_ERROR`, `INVALID_REQUEST`, `CONTEXT_LENGTH_EXCEEDED`.
    - [ ] Log each retry attempt with attempt number and delay.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/resilience/RetryPolicy.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/resilience/RetryExecutor.kt` (new)

- [ ] `AST-S051-T02` Integrate retry into `CompletionService`.
  - **Subtasks**:
    - [ ] Wrap `LLMProviderClient.complete()` call with `RetryExecutor`.
    - [ ] Use configurable `RetryPolicy` (injectable, defaults from `application.yml`).
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`

- [ ] `AST-S051-T03` Add unit tests for retry behavior.
  - **Subtasks**:
    - [ ] Test: retryable error triggers retry up to `maxAttempts`.
    - [ ] Test: non-retryable error does not retry.
    - [ ] Test: success on second attempt returns response (not error).
    - [ ] Test: delays follow exponential backoff progression.
  - **Files**:
    - `service/kotlin/assistant/src/test/kotlin/io/github/salomax/neotool/assistant/test/unit/resilience/RetryExecutorTest.kt` (new)

#### Acceptance Criteria

- [ ] `RATE_LIMIT` errors trigger up to 3 retries with exponential delay.
- [ ] `AUTH_ERROR` never retries.
- [ ] All retry attempts are logged with attempt number and delay.

---

### Story `AST-S052` — Circuit Breaker

**Outcome**: Sustained provider failures open the circuit, preventing cascading load to a failing provider.

#### Tasks

- [ ] `AST-S052-T01` Implement `CircuitBreaker`.
  - **Subtasks**:
    - [ ] States: `CLOSED`, `OPEN`, `HALF_OPEN`.
    - [ ] `failureThreshold` (default: 5 failures within window).
    - [ ] `timeout` (default: 60s) before transitioning from `OPEN` to `HALF_OPEN`.
    - [ ] `halfOpenRequests` (default: 1) allowed through in `HALF_OPEN` state.
    - [ ] `successThreshold` (default: 2) consecutive successes in `HALF_OPEN` to close circuit.
    - [ ] Thread-safe state transitions.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/resilience/CircuitBreaker.kt` (new)

- [ ] `AST-S052-T02` Integrate circuit breaker into `CompletionService`.
  - **Subtasks**:
    - [ ] Wrap provider call with circuit breaker check.
    - [ ] Expose `assistant_circuit_breaker_state{provider}` metric gauge.
    - [ ] Return `PROVIDER_UNAVAILABLE` error when circuit is `OPEN`.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/observability/AssistantMetrics.kt`

#### Acceptance Criteria

- [ ] Circuit opens after `failureThreshold` consecutive failures.
- [ ] Requests are rejected immediately when circuit is `OPEN` (no provider call).
- [ ] Circuit transitions back to `CLOSED` after `successThreshold` successes in `HALF_OPEN`.
- [ ] `assistant_circuit_breaker_state` metric reflects current state.

---

### Story `AST-S053` — Rate Limiting

**Outcome**: Requests exceeding configured limits per account, service, or feature are rejected before reaching the provider.

#### Tasks

- [ ] `AST-S053-T01` Create Flyway migration for rate limit counters.
  - **Subtasks**:
    - [ ] Table `rate_limit_counters`: `id`, `scope`, `scope_id`, `window_start`, `window_size_ms`, `request_count`, `token_count`, `created_at`, `updated_at`.
    - [ ] Unique constraint: `(scope, scope_id, window_start, window_size_ms)`.
    - [ ] Index: `rate_limit_counters(scope, scope_id, window_start)`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V004__rate_limits.sql` (new)

- [ ] `AST-S053-T02` Implement `RateLimiterService`.
  - **Subtasks**:
    - [ ] `checkAndIncrement(scope, scopeId, estimatedTokens)`:
      - Atomic check-and-increment: single statement `WHERE count + 1 <= limit`.
      - Returns `RateLimitResult(allowed=true/false, remaining, resetAt)`.
      - Checks hierarchy: PROVIDER → ACCOUNT → SERVICE → FEATURE in order.
    - [ ] Configurable limits per scope via `UsageBudget` entities.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/RateLimiterService.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/repository/RateLimitCounterRepository.kt` (new)

- [ ] `AST-S053-T03` Integrate rate limiting into `CompletionService`.
  - **Subtasks**:
    - [ ] Call `RateLimiterService.checkAndIncrement()` before calling provider.
    - [ ] Return `RATE_LIMIT_EXCEEDED` error with `resetAt` timestamp if denied.
    - [ ] Add `assistant_rate_limit_exceeded_total{scope}` counter metric.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/CompletionService.kt`

#### Acceptance Criteria

- [ ] Rate limit check is atomic — no race condition can overshoot the limit.
- [ ] `RATE_LIMIT_EXCEEDED` includes `resetAt` so clients can back off.
- [ ] Rate limit is checked before any provider call — rejected requests do not consume tokens.

---

### Story `AST-S054` — Usage Budget Enforcement

**Outcome**: Accounts and features have configurable cost budgets with alert thresholds.

#### Tasks

- [ ] `AST-S054-T01` Implement `UsageBudgetService`.
  - **Subtasks**:
    - [ ] `setBudget(scope, scopeId, period, limitAmount, alertThreshold)` — upserts `UsageBudget`.
    - [ ] `checkBudget(scope, scopeId, estimatedCost)` — returns `BudgetCheckResult(allowed, percentUsed)`.
    - [ ] `recordCost(scope, scopeId, cost)` — increments `current_usage`.
    - [ ] After each completion, check if `currentUsage / limitAmount >= alertThreshold`; fire alert metric if crossed.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/UsageBudgetService.kt` (new)

- [ ] `AST-S054-T02` Add `setUsageBudget` mutation to schema.
  - **Subtasks**:
    - [ ] Add `UsageBudget` type, `SetBudgetInput`, `BudgetPeriod`, `BudgetScope` enums.
    - [ ] Add `setUsageBudget(input: SetBudgetInput!): UsageBudget!` mutation (admin only).
    - [ ] Implement `BudgetResolver`.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/BudgetResolver.kt` (new)

- [ ] `AST-S054-T03` Add `assistant_budget_alert_total{scope}` metric and alert.
  - **Subtasks**:
    - [ ] Metric counter incremented when `alertThreshold` crossed.
    - [ ] Add Grafana alert: budget >80% consumed.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/observability/AssistantMetrics.kt`
    - `infra/observability/alerts/assistant-alerts.yml`

#### Acceptance Criteria

- [ ] Budget check runs after rate limit check, before provider call.
- [ ] `currentUsage` is incremented after every successful completion.
- [ ] Alert fires when `currentUsage >= alertThreshold * limitAmount`.

---

## Epic `AST-E06` — Streaming

### Story `AST-S061` — SSE Streaming via `messageStream` Subscription

**Outcome**: FAQ chatbot clients receive tokens as they are generated, improving perceived response time.

#### Tasks

- [ ] `AST-S061-T01` Add `streamComplete` to `LLMProviderClient` interface.
  - **Subtasks**:
    - [ ] `streamComplete(request): Flow<ProviderCompletionChunk>`.
    - [ ] Implement `OpenAIProviderClient.streamComplete()` using SSE with OpenAI's streaming endpoint.
    - [ ] Each chunk: `delta` (text), `finishReason` (null until last), `usage` (only on last chunk).
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/LLMProviderClient.kt`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/openai/OpenAIProviderClient.kt`

- [ ] `AST-S061-T02` Add `messageStream` subscription to schema and resolver.
  - **Subtasks**:
    - [ ] Types: `CompletionChunk` with `delta`, `finishReason`, `usage`, `escalate`.
    - [ ] Add `messageStream(conversationId: ID!, input: MessageInput!): CompletionChunk!` subscription.
    - [ ] Detect escalation signal from accumulated `delta` text on stream completion.
    - [ ] Persist conversation messages and `CompletionResponse` after stream ends.
    - [ ] Update `assistant_requests_total` with `stream=true` label.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/graphql/schema.graphqls`
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/graphql/resolver/StreamingResolver.kt` (new)

#### Acceptance Criteria

- [ ] `messageStream` emits chunks until `finishReason` is set.
- [ ] Escalation is detected on the final accumulated content, same logic as non-streaming.
- [ ] Full conversation and `CompletionResponse` are persisted after stream ends.

---

## Epic `AST-E07` — Multi-Provider

### Story `AST-S071` — Gemini Provider Client

**Outcome**: The service can route completions to Gemini Claude models.

#### Tasks

- [ ] `AST-S071-T01` Implement `GeminiProviderClient`.
  - **Subtasks**:
    - [ ] Map internal `ProviderCompletionRequest` to Gemini Messages API format.
    - [ ] Map system prompt to Gemini's `system` field (not a message role).
    - [ ] Map tool definitions to Gemini tool format.
    - [ ] Map response back to `ProviderCompletionResponse`.
    - [ ] Handle Gemini-specific error codes.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/gemini/GeminiProviderClient.kt` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/provider/gemini/GeminiMapper.kt` (new)

#### Acceptance Criteria

- [ ] `GeminiProviderClient` passes same unit tests as `OpenAIProviderClient` via the shared `LLMProviderClient` interface contract.
- [ ] System prompt is mapped to Gemini's `system` field, not as a `user` message.

---

### Story `AST-S072` — Provider Routing and Failover

**Outcome**: Requests are routed to the best available provider; failover happens automatically on circuit open.

#### Tasks

- [ ] `AST-S072-T01` Implement `ProviderRouter`.
  - **Subtasks**:
    - [ ] `selectModel(request)`: filter eligible models by capabilities and context window, then score by cost + latency + circuit health.
    - [ ] Apply routing rules as preferences only (never bypass hard requirements).
    - [ ] Return `NoAvailableModelException` if no model passes all requirements.
  - **Files**:
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ProviderRouter.kt` (new)

- [ ] `AST-S072-T02` Implement gradual traffic shifting.
  - **Subtasks**:
    - [ ] `ModelRoutingRule` entity: `id`, `priority`, `conditions`, `targetModelId`, `weightPercent`, `enabled`.
    - [ ] `ProviderRouter` respects `weightPercent` for A/B traffic splitting.
    - [ ] Add migration for `model_routing_rules` table.
  - **Files**:
    - `service/kotlin/assistant/src/main/resources/db/migration/V005__routing_rules.sql` (new)
    - `service/kotlin/assistant/src/main/kotlin/io/github/salomax/neotool/assistant/service/ProviderRouter.kt`

#### Acceptance Criteria

- [ ] When primary provider circuit is `OPEN`, requests are routed to the next eligible model.
- [ ] Traffic splitting is deterministic per request (e.g., hash-based, not random per token).

---

## Epic `AST-E08` — Caching (When Volume Justifies)

> **Note**: This epic is deferred and has no implementation scheduled. It will be prioritized when cache hit analysis shows meaningful cost savings.

### Story `AST-S081` — Exact Match Cache (PostgreSQL-backed)

**Outcome**: Identical repeated requests within an account are served from cache.

#### Tasks

- [ ] `AST-S081-T01` Create Flyway migration for `cache_entries` table.
  - **Subtasks**:
    - [ ] Table `cache_entries`: `id`, `account_id`, `cache_key` (hash), `prompt_hash`, `messages_hash`, `cached_response`, `model_id`, `prompt_version_id`, `hit_count`, `expires_at`, `created_at`, `last_accessed_at`.
    - [ ] Unique constraint: `(account_id, cache_key)`.
    - [ ] Index: `(account_id, cache_key, expires_at)`.
    - [ ] Index: `(expires_at)` for TTL cleanup job.

- [ ] `AST-S081-T02` Implement `CacheService`.
  - **Subtasks**:
    - [ ] Cache key = `hash(accountId + promptVersionId + messages + parameters)`.
    - [ ] TTL: 5 minutes (configurable).
    - [ ] `get(key, accountId)` — enforces `accountId` isolation on every read.
    - [ ] `put(key, accountId, response)` — stores response with TTL.
    - [ ] Scheduled job: delete expired entries.

- [ ] `AST-S081-T03` Add cache hit/miss metrics.
  - **Subtasks**:
    - [ ] `assistant_cache_hits_total` counter.
    - [ ] `assistant_cache_misses_total` counter.
    - [ ] `cached: true` on `CompletionResponse` when served from cache.

#### Acceptance Criteria

- [ ] Cache never returns a response from a different `accountId`.
- [ ] Cache is invalidated when the prompt version changes.
- [ ] LGPD deletion workflow removes cache entries for an account on data erasure request.

---

## 4) Dependency Graph

### Epic-level Ordering

```
AST-E01 (Foundation)
  └─► AST-E02 (Structured Output)
        └─► AST-E03 (FAQ Conversation)
              └─► AST-E04 (Tool Calling)
                    └─► AST-E05 (Resilience)
                          └─► AST-E06 (Streaming)
                                └─► AST-E07 (Multi-Provider)
                                      └─► AST-E08 (Caching — deferred)
```

### Story-level Critical Path (E01)

```
AST-S011 (service scaffold)
  └─► AST-S012 (DB schema)
        └─► AST-S013 (entities + repositories)
              ├─► AST-S014 (prompt management)
              │     └─► AST-S015 (OpenAI client)
              │           └─► AST-S016 (generateCompletion)
              │                 └─► AST-S017 (usage tracking)
              │                       └─► AST-S018 (observability)
              └─► AST-S015 (OpenAI client) — can run in parallel with AST-S014
```

### Parallel Tracks Available

- `AST-S014` (prompt management) and `AST-S015` (OpenAI client) can run in parallel after `AST-S013`.
- `AST-S017` (usage tracking) and `AST-S018` (observability) can run in parallel after `AST-S016`.
- `AST-S051` (retry) and `AST-S052` (circuit breaker) can run in parallel in `AST-E05`.

---

## 5) Validation Command Set

```bash
# Run all assistant tests
cd service/kotlin && ./gradlew :assistant:test

# Run with coverage report
cd service/kotlin && ./gradlew :assistant:test :assistant:jacocoTestReport

# Sync GraphQL supergraph after schema change
./neotool graphql sync

# Regenerate typed GraphQL clients for all services
./neotool graphql generate --all

# Lint Kotlin code
cd service/kotlin && ./gradlew :assistant:ktlintCheck

# Run integration tests only
cd service/kotlin && ./gradlew :assistant:test --tests "*.integration.*"

# Validate Flyway migrations
cd service/kotlin && ./gradlew :assistant:flywayInfo
```

---

## 6) Feature Definition of Done

- [ ] All stories in `AST-E01` through `AST-E05` are implemented and accepted.
- [ ] `accountId` isolation is enforced on all completion, conversation, usage, and cache tables.
- [ ] All LLM requests record `purpose` field (LGPD).
- [ ] `generateCompletion` and `sendMessage` are covered by integration tests using `MockProviderClient`.
- [ ] Tool calling loop terminates safely under all error conditions.
- [ ] Rate limiting is atomic — no race condition overshoots.
- [ ] Circuit breaker state is observable via metrics.
- [ ] All structured logs include `requestId`, `accountId`, `model`, and `purpose`.
- [ ] Grafana dashboard shows: request rate, error rate, P95 latency, cost/hour, escalation rate.
- [ ] GraphQL schema is synced to `contracts/graphql/subgraphs/assistant/schema.graphqls`.
- [ ] `./gradlew :assistant:test` passes with ≥80% coverage on service layer.
