## LLM Orchestrator - Quick Recap

**Goal**: Centralized LLM service for NeoTool platform

**Approach**: Open-source focused
- Ollama (Llama 3, Mistral) for local inference ($0/month)
- PostgreSQL caching (no Redis)
- SSE streaming (no WebSocket)
- services/kotlin/assistant

**Docs**: docs/03-features/ai/llm-orchestrator/
- Blueprint: Complete spec
- ADR-0011: Architecture decision  
- IMPLEMENTATION_BACKLOG.md: 10 phases, 90+ tasks

**MVP**: 7 weeks (Phases 0-4)
**Full**: 16 weeks (Phases 0-10)

**First Tasks** (Phase 0):
- ASSIST-001: Project setup
- ASSIST-002: Database migrations
- ASSIST-003: Observability
- ASSIST-004: GraphQL schema
- ASSIST-101: Ollama setup
