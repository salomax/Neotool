---
title: LLM Orchestrator Implementation Backlog
type: backlog
category: ai
status: draft
version: 1.0.0
tags: [backlog, implementation, tasks, open-source]
related:
  - llm-orchestrator-blueprint.md
  - ../../92-adr/0011-llm-orchestrator-service.md
last_updated: 2026-02-05
---

# LLM Orchestrator Implementation Backlog

> **Focus**: Open-source and free alternatives prioritized

## Technology Stack (Open-Source Focus)

| Component | Technology | Why Open-Source |
|-----------|-----------|-----------------|
| **LLM Provider** | Ollama (primary) | Run models locally, free, supports Llama 3, Mistral, Gemma, etc. |
| **Fallback Provider** | OpenAI/Anthropic | External APIs for complex tasks (pay-as-you-go) |
| **Caching** | PostgreSQL | Already in stack, no additional dependency |
| **Streaming** | SSE (Server-Sent Events) | HTTP-based, no WebSocket server needed |
| **Token Counting** | tiktoken-kt (Kotlin port) | Open-source, fast, local |
| **PII Detection** | Custom regex + NER | Avoid paid APIs, use open NLP libraries |
| **Embeddings** | sentence-transformers (local) | Self-hosted embeddings for semantic cache |

## Phase 0: Foundation & Setup (Week 1-2)

### Epic: Project Setup
**Goal**: Initialize the assistant service with basic structure

#### Tasks

- [ ] **ASSIST-001**: Create service structure in `services/kotlin/assistant`
  - Set up Micronaut project with Gradle/Kotlin
  - Configure base dependencies (Micronaut, GraphQL, PostgreSQL)
  - Set up basic application.yml configuration
  - Create Docker Compose for local development

- [ ] **ASSIST-002**: Database schema design and migrations
  - Create `assistant_db` database
  - Design core tables: prompts, prompt_versions, providers, models
  - Create Flyway migration V001: Initial schema
  - Add indexes for performance

- [ ] **ASSIST-003**: Set up observability (metrics, logging)
  - Configure Micrometer for Prometheus metrics
  - Set up structured logging (Logback + JSON)
  - Create custom metrics: `assistant_requests_total`, `assistant_tokens_total`, `assistant_cost_total`
  - Add health check endpoint

- [ ] **ASSIST-004**: GraphQL schema definition
  - Define base types: `Prompt`, `PromptVersion`, `CompletionRequest`, `CompletionResponse`
  - Create mutations: `generateCompletion`, `createPrompt`
  - Create queries: `prompts`, `myUsage`
  - Set up GraphQL Federation directives

**Acceptance Criteria**:
- ✅ Service starts successfully on port 8090
- ✅ Database migrations run successfully
- ✅ GraphQL playground accessible at `/graphql`
- ✅ Health check returns 200 OK
- ✅ Metrics endpoint returns Prometheus format

---

## Phase 1: Ollama Integration (Week 3-4)

### Epic: Open-Source LLM Provider
**Goal**: Integrate Ollama for local, free LLM inference

#### Tasks

- [ ] **ASSIST-101**: Set up Ollama in Docker Compose
  - Add Ollama service to docker-compose.yml
  - Configure GPU support (if available) or CPU fallback
  - Pull initial models: `llama3.2:3b`, `mistral:7b`, `gemma:7b`
  - Document model selection trade-offs (speed vs quality)

- [ ] **ASSIST-102**: Implement Ollama provider client
  - Create `OllamaProviderClient` implementing `LLMProviderClient` interface
  - Implement `/api/generate` endpoint integration
  - Implement `/api/chat` endpoint integration
  - Handle streaming responses (SSE)
  - Add error handling and retries

- [ ] **ASSIST-103**: Implement token counting (local)
  - Integrate `tiktoken-kt` library (Kotlin port of tiktoken)
  - Create `TokenCounter` service
  - Implement token estimation for prompts
  - Add token tracking in usage logs

- [ ] **ASSIST-104**: Create basic completion endpoint
  - Implement `generateCompletion` GraphQL mutation
  - Wire up Ollama provider
  - Add basic error handling
  - Return completion response with token usage

- [ ] **ASSIST-105**: Add model management
  - Create `llm_models` table with Ollama models
  - Seed data: Llama 3.2 3B, Mistral 7B, Gemma 7B
  - Add `availableModels` GraphQL query
  - Implement model selection logic (default to fastest)

**Acceptance Criteria**:
- ✅ Ollama runs in Docker and responds to API calls
- ✅ Can generate completions using Llama 3.2 model
- ✅ Token counting works accurately (±5% of actual)
- ✅ GraphQL mutation returns completion in <5s for simple prompts
- ✅ Metrics track tokens and model used

**Cost**: $0 (fully local, uses existing hardware)

---

## Phase 2: Prompt Management (Week 5)

### Epic: Versioned Prompt Catalog
**Goal**: Manage prompts as versioned, testable assets

#### Tasks

- [ ] **ASSIST-201**: Implement prompt CRUD operations
  - Create `PromptService` with create/read/update/delete
  - Create `PromptVersionService` with versioning logic
  - Implement `createPrompt` GraphQL mutation
  - Implement `prompts` GraphQL query
  - Implement `publishPromptVersion` mutation

- [ ] **ASSIST-202**: Prompt template rendering
  - Implement Mustache/Handlebars template engine
  - Support variable substitution: `{{variable}}`
  - Add validation for required variables
  - Add template syntax error handling

- [ ] **ASSIST-203**: Seed sample prompts
  - Create migration V002: Sample prompts
  - Add prompts: `summarize-text`, `generate-title`, `extract-keywords`, `classify-sentiment`
  - Test with Ollama models
  - Document best practices for prompt engineering

- [ ] **ASSIST-204**: Prompt performance tracking
  - Add `performance_metrics` JSONB column to `prompt_versions`
  - Track: avg latency, token usage, success rate
  - Update metrics after each completion
  - Add `promptPerformance` GraphQL query

**Acceptance Criteria**:
- ✅ Can create prompts via GraphQL
- ✅ Can version prompts (v1, v2, v3)
- ✅ Template rendering works with variables
- ✅ 4+ sample prompts available
- ✅ Performance metrics tracked per prompt version

**Cost**: $0 (local PostgreSQL)

---

## Phase 3: Caching with PostgreSQL (Week 6)

### Epic: Cost Optimization via Caching
**Goal**: Implement multi-level caching using PostgreSQL

#### Tasks

- [ ] **ASSIST-301**: Design cache schema
  - Create `cache_entries` table
  - Add indexes: `cache_key`, `expires_at`, `semantic_hash`
  - Add partitioning by `created_at` (monthly)
  - Create cleanup job for expired entries

- [ ] **ASSIST-302**: Implement exact-match caching
  - Create `CacheManager` service
  - Generate cache key: `hash(prompt + messages + params)`
  - Check cache before LLM call
  - Store response in cache with TTL (5 min)
  - Track cache hits/misses in metrics

- [ ] **ASSIST-303**: Implement semantic caching (basic)
  - Use sentence embeddings (all-MiniLM-L6-v2 via ONNX)
  - Calculate cosine similarity for cache lookup
  - Set similarity threshold: 0.95
  - Store embeddings in PostgreSQL vector extension (pgvector)
  - Fallback to exact match if semantic fails

- [ ] **ASSIST-304**: Cache warming for common prompts
  - Identify top 10 most used prompts
  - Create background job to pre-populate cache
  - Schedule daily cache warming
  - Monitor cache hit rates

**Acceptance Criteria**:
- ✅ Exact match cache works (100% hit on identical requests)
- ✅ Semantic cache finds similar requests (>90% threshold)
- ✅ Cache hit rate >50% after 1 week of usage
- ✅ Cache cleanup job runs daily
- ✅ Metrics show `cache_hit_rate` by cache level

**Cost**: $0 (using existing PostgreSQL)

**Savings**: ~70-80% reduction in LLM calls

---

## Phase 4: Resilience & Routing (Week 7-8)

### Epic: Reliability & Intelligent Routing
**Goal**: Add retries, fallbacks, circuit breakers, and intelligent model selection

#### Tasks

- [ ] **ASSIST-401**: Implement retry logic
  - Add retry policy: max 3 attempts, exponential backoff
  - Retry on: connection errors, timeouts, rate limits
  - Don't retry on: invalid input, authentication errors
  - Log retry attempts with context

- [ ] **ASSIST-402**: Implement circuit breaker
  - Use Resilience4j library (open-source)
  - Configure per provider: failure threshold = 5, timeout = 60s
  - Track circuit state: CLOSED, OPEN, HALF_OPEN
  - Expose circuit state in metrics

- [ ] **ASSIST-403**: Add OpenAI as fallback provider (optional)
  - Create `OpenAIProviderClient` implementing `LLMProviderClient`
  - Configure API key (env var)
  - Set as fallback when Ollama fails
  - Track fallback usage in metrics

- [ ] **ASSIST-404**: Implement model routing
  - Create `ModelRouter` service
  - Routing factors: context window, cost (local = $0), latency
  - Default: Llama 3.2 3B (fastest local model)
  - Fallback chain: Llama 3.2 3B → Mistral 7B → OpenAI GPT-3.5 (if configured)

- [ ] **ASSIST-405**: Add routing rules (database-driven)
  - Create `model_routing_rules` table
  - Support rule conditions: `promptCategory`, `maxTokens`, `requiresAccuracy`
  - Example: If `category = "code"`, use Mistral (better at code)
  - Implement rule matching engine

**Acceptance Criteria**:
- ✅ Retries work automatically on transient failures
- ✅ Circuit breaker opens after 5 consecutive failures
- ✅ Fallback to OpenAI works when Ollama down (if configured)
- ✅ Routing selects appropriate model based on requirements
- ✅ Can configure routing rules via database

**Cost**: $0 with Ollama only, minimal with optional OpenAI fallback

---

## Phase 5: Tool Calling (Week 9-10)

### Epic: Function Calling Framework
**Goal**: Enable LLMs to call external functions/tools

#### Tasks

- [ ] **ASSIST-501**: Design tool registry
  - Create `tools` table with schema
  - Create `ToolRegistry` singleton service
  - Define `ToolExecutor` interface
  - Implement tool registration API

- [ ] **ASSIST-502**: Implement tool execution framework
  - Create `ToolExecutionService`
  - Handle tool input validation (JSON Schema)
  - Execute tools with timeout (5s default)
  - Handle tool errors gracefully
  - Log tool executions

- [ ] **ASSIST-503**: Add built-in tools
  - `get_current_time`: Returns current date/time
  - `calculate`: Evaluate math expressions (safe eval)
  - `format_date`: Format date strings
  - `validate_email`: Validate email format
  - `uuid_generate`: Generate UUIDs

- [ ] **ASSIST-504**: Integrate tool calling with Ollama
  - Research Ollama function calling support
  - Implement tool call detection in responses
  - Execute tools and inject results back
  - Handle multi-turn tool conversations

- [ ] **ASSIST-505**: Create `registerTool` admin mutation
  - Allow services to register custom tools
  - Validate tool schema
  - Store in `tools` table
  - Return tool ID for reference

**Acceptance Criteria**:
- ✅ Can register custom tools via GraphQL
- ✅ 5+ built-in tools available
- ✅ LLM can call tools and get results
- ✅ Tool execution times out after 5s
- ✅ Metrics track tool usage

**Cost**: $0 (local execution)

---

## Phase 6: Streaming Support (Week 11)

### Epic: Real-Time Streaming Responses
**Goal**: Support token-by-token streaming for better UX

#### Tasks

- [ ] **ASSIST-601**: Implement SSE streaming endpoint
  - Add `/api/stream` REST endpoint (SSE)
  - Configure Content-Type: `text/event-stream`
  - Handle keep-alive and connection management
  - Implement reconnection logic

- [ ] **ASSIST-602**: Integrate Ollama streaming
  - Use Ollama `/api/generate` streaming mode
  - Parse SSE events from Ollama
  - Transform to internal format
  - Forward to client

- [ ] **ASSIST-603**: Add GraphQL subscription (optional)
  - Create `completionStream` subscription
  - Use GraphQL subscriptions over WebSocket
  - Emit `CompletionChunk` events
  - Handle subscription lifecycle

- [ ] **ASSIST-604**: Client SDK for streaming
  - Create TypeScript SDK for web clients
  - Handle SSE connection and reconnection
  - Provide callback for each chunk
  - Example usage documentation

**Acceptance Criteria**:
- ✅ SSE endpoint streams tokens in real-time
- ✅ Ollama streaming works end-to-end
- ✅ Client can receive chunks and update UI
- ✅ Connection gracefully handles disconnects
- ✅ First token arrives in <500ms

**Cost**: $0 (using SSE, no WebSocket server)

---

## Phase 7: Security & PII Protection (Week 12)

### Epic: Security, Privacy, and Compliance
**Goal**: Detect and redact PII, filter harmful content

#### Tasks

- [ ] **ASSIST-701**: Implement PII detection
  - Use regex patterns for: email, phone, SSN, credit card
  - Integrate open-source NER (Named Entity Recognition)
  - Option: Use local Spacy model or BERT-based NER
  - Create `PIIDetector` service

- [ ] **ASSIST-702**: Implement PII redaction
  - Redact detected PII before sending to LLM
  - Replace with placeholder: `[EMAIL]`, `[PHONE]`, `[SSN]`
  - Store original (encrypted) in audit log
  - Add `redactPII` configuration flag

- [ ] **ASSIST-703**: Content filtering (basic)
  - Use keyword blocklist for harmful content
  - Integrate open-source toxicity classifier (Detoxify)
  - Validate input and output content
  - Return error if content violates policy

- [ ] **ASSIST-704**: Audit logging
  - Create `audit_logs` table
  - Log: userId, serviceId, input (redacted), output, timestamp
  - Add retention policy (90 days)
  - Create admin query to view audit logs

**Acceptance Criteria**:
- ✅ PII detected with >95% accuracy
- ✅ PII redacted before sending to LLM
- ✅ Harmful content blocked (input and output)
- ✅ All interactions logged to audit table
- ✅ Audit logs retained for 90 days

**Cost**: $0 (local models and regex)

---

## Phase 8: Cost Tracking & Usage Limits (Week 13)

### Epic: Cost Control and Budgeting
**Goal**: Track usage, enforce budgets, prevent runaway costs

#### Tasks

- [ ] **ASSIST-801**: Implement usage tracking
  - Create `usage_logs` table
  - Track: service, user, feature, tokens, cost, timestamp
  - Log every completion request
  - Add partitioning by month for performance

- [ ] **ASSIST-802**: Calculate costs (including $0 for Ollama)
  - Ollama models: cost = $0 (local)
  - OpenAI models: cost = (input_tokens * $0.0005) + (output_tokens * $0.0015)
  - Store cost in `usage_logs`
  - Aggregate costs by service/user/period

- [ ] **ASSIST-803**: Implement rate limiting
  - Create `rate_limits` table (scope: global, service, user)
  - Implement `RateLimiter` service
  - Check limits before processing request
  - Return 429 error if limit exceeded
  - Use token bucket algorithm

- [ ] **ASSIST-804**: Create usage dashboard (GraphQL queries)
  - `myUsage(period)`: User's usage stats
  - `serviceUsage(serviceId, period)`: Service usage stats
  - `topUsers(period, limit)`: Top users by tokens/cost
  - `usageTrends(period)`: Daily/weekly/monthly trends

- [ ] **ASSIST-805**: Add budget enforcement
  - Create `usage_budgets` table
  - Support scopes: global, per-service, per-user
  - Block requests when budget exceeded
  - Send alerts at 80% threshold
  - Allow budget overrides (admin only)

**Acceptance Criteria**:
- ✅ Usage tracked for every request
- ✅ Costs calculated accurately (including $0 for local)
- ✅ Rate limits enforced per service/user
- ✅ GraphQL queries return usage stats
- ✅ Budget enforcement works, blocks over-budget requests

**Cost**: $0 (PostgreSQL tracking)

**Savings**: Prevents runaway costs from external APIs

---

## Phase 9: Documentation & Developer Experience (Week 14)

### Epic: Developer Tools and Documentation
**Goal**: Make it easy for developers to integrate and use the assistant service

#### Tasks

- [ ] **ASSIST-901**: Create API documentation
  - Document all GraphQL queries and mutations
  - Add code examples (Kotlin, TypeScript)
  - Document error codes and handling
  - Create Postman/Insomnia collection

- [ ] **ASSIST-902**: Create integration guide
  - Step-by-step guide for adding assistant to a service
  - Example: Summarize transactions in app service
  - Example: Generate email content in notification service
  - Include best practices and anti-patterns

- [ ] **ASSIST-903**: Create prompt engineering guide
  - Best practices for writing prompts
  - Examples of good vs bad prompts
  - Model-specific tips (Llama vs Mistral)
  - Template library (reusable prompts)

- [ ] **ASSIST-904**: Create runbook for operations
  - How to deploy and configure
  - How to add new models to Ollama
  - How to troubleshoot common issues
  - Monitoring and alerting setup

- [ ] **ASSIST-905**: Create admin tools
  - Simple CLI for common operations
  - Scripts: seed prompts, clear cache, export usage
  - Model management: pull, remove, list
  - Budget management: set, check, reset

**Acceptance Criteria**:
- ✅ Complete API documentation available
- ✅ Integration guide with 2+ examples
- ✅ Prompt engineering guide with 10+ examples
- ✅ Runbook covers deployment and ops
- ✅ Admin CLI with 5+ commands

**Cost**: $0 (documentation and tooling)

---

## Phase 10: Production Readiness (Week 15-16)

### Epic: Scalability, Performance, and Production Deployment
**Goal**: Prepare for production deployment

#### Tasks

- [ ] **ASSIST-1001**: Performance testing
  - Load test: 100 concurrent requests
  - Benchmark: latency at p50, p95, p99
  - Identify bottlenecks (DB queries, LLM calls)
  - Optimize slow queries
  - Add database indexes

- [ ] **ASSIST-1002**: Horizontal scaling setup
  - Ensure service is stateless
  - Test with 2+ instances
  - Load balancing configuration
  - Shared PostgreSQL for cache
  - Shared Ollama instance or per-instance Ollama

- [ ] **ASSIST-1003**: Observability enhancements
  - Add distributed tracing (Jaeger/Zipkin)
  - Create Grafana dashboards
  - Set up alerts in Prometheus/AlertManager
  - Add error tracking (Sentry or similar)

- [ ] **ASSIST-1004**: Security hardening
  - API authentication (JWT validation)
  - Rate limiting per API key
  - Input sanitization and validation
  - Secrets management (Vault or env vars)
  - TLS/HTTPS enforcement

- [ ] **ASSIST-1005**: Deployment automation
  - Create Kubernetes manifests
  - Set up CI/CD pipeline (GitHub Actions)
  - Automated tests (unit, integration, e2e)
  - Rolling deployments with health checks
  - Rollback strategy

**Acceptance Criteria**:
- ✅ Service handles 100 req/s with <1s p95 latency
- ✅ Scales horizontally (2+ instances)
- ✅ Grafana dashboards show key metrics
- ✅ Alerts fire on critical issues
- ✅ CI/CD pipeline deploys automatically

**Cost**: Infrastructure costs (K8s cluster, if using cloud)

---

## Summary & Estimates

### Total Timeline
- **Phase 0**: Foundation (2 weeks)
- **Phase 1**: Ollama Integration (2 weeks)
- **Phase 2**: Prompt Management (1 week)
- **Phase 3**: Caching (1 week)
- **Phase 4**: Resilience (2 weeks)
- **Phase 5**: Tool Calling (2 weeks)
- **Phase 6**: Streaming (1 week)
- **Phase 7**: Security (1 week)
- **Phase 8**: Usage Tracking (1 week)
- **Phase 9**: Documentation (1 week)
- **Phase 10**: Production (2 weeks)

**Total**: ~16 weeks (4 months) for full implementation

### MVP Timeline (Phases 0-4)
- **Core functionality**: 7 weeks (~2 months)
- **Includes**: Ollama, prompts, caching, routing
- **Ready for**: Internal testing and first use cases

### Open-Source Cost Breakdown

| Component | Technology | Cost |
|-----------|-----------|------|
| LLM Inference | Ollama (local) | $0/month |
| Caching | PostgreSQL | $0/month (existing) |
| Token Counting | tiktoken-kt | $0/month |
| PII Detection | Regex + local NER | $0/month |
| Embeddings | sentence-transformers | $0/month |
| Streaming | SSE | $0/month |
| Monitoring | Prometheus + Grafana | $0/month (self-hosted) |
| **Total** | - | **$0/month** |

**Optional paid services** (for fallback/scaling):
- OpenAI API: ~$10-50/month (pay-as-you-go)
- Anthropic API: ~$10-50/month (pay-as-you-go)

### Resource Requirements

**Development**:
- 1 backend developer (Kotlin)
- 1 DevOps engineer (part-time)

**Infrastructure** (self-hosted):
- CPU: 4-8 cores (Ollama inference)
- RAM: 16-32 GB (model loading)
- Storage: 50 GB (models + cache)
- GPU: Optional (speeds up Ollama 3-10x)

**Cloud Alternative** (if needed):
- AWS g4dn.xlarge: ~$0.50/hr (~$350/month)
- Or use CPU instances for smaller models

---

## Next Steps

1. **Review and prioritize** this backlog
2. **Set up Phase 0** (foundation) first
3. **Choose model size** for Ollama (3B vs 7B vs 13B)
4. **Decide on GPU** vs CPU for inference
5. **Create tickets** in your project management tool

---

**Last Updated**: 2026-02-05
**Status**: Draft - Ready for review
**Owner**: AI Platform Team
