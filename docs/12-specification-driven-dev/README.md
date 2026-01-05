# Specification-Driven Development (SDD)

This section documents **how to use** the NeoTool documentation system for AI-assisted development with Cursor and Claude Code.

## What is SDD?

Specification-Driven Development is NeoTool's approach where:
1. **Documentation drives implementation** — Specs are written first, code follows
2. **AI assistants execute from specs** — Cursor/Claude Code reads specs to generate code
3. **Validation ensures compliance** — Checklists verify spec adherence
4. **Continuous improvement** — Specs evolve based on implementation learnings

## How This Documentation is Organized

### Core Learning Path (01-12)
Sequential documentation for understanding and building:
1. **01-overview** — What is NeoTool?
2. **02-architecture** — How is it designed?
3. **03-features** — What can it do? (Feature specs)
4. **04-domain** — What are we modeling?
5. **05-backend** — How do we build backend services?
6. **06-contracts** — How do services communicate?
7. **07-frontend** — How do we build user interfaces?
8. **08-workflows** — How do we work?
9. **09-security** — How do we stay secure?
10. **10-observability** — How do we monitor?
11. **infrastructure** — How do we deploy?
12. **12-specification-driven-dev** — How do we use this documentation? (You are here)

### Supporting Resources (90-94)
Quick lookup and reference materials:
- **90-examples** — Concrete code implementations
- **91-templates** — Reusable boilerplate
- **92-adr** — Architecture Decision Records
- **93-reference** — Quick reference guides
- **94-validation** — Checklists and validation scripts

## Using with AI Assistants

### With Cursor

**Workflow**:
1. Open feature spec in `03-features/[feature]/`
2. Use Cursor's "Add to Context" to include relevant patterns from `05-backend/patterns/` or `07-frontend/patterns/`
3. Reference standards from `05-backend/kotlin/` or `07-frontend/standards/`
4. Ask Cursor to implement following the spec

**Prompt Engineering** (to be documented):
- See `cursor-workflow.md` (planned)
- See `prompt-engineering.md` (planned)

### With Claude Code

**Workflow**:
1. Claude Code automatically indexes all `docs/` with RAG
2. Reference feature spec: "Implement feature from `docs/03-features/authentication/signin/`"
3. Claude Code retrieves relevant patterns and standards
4. Implementation follows spec automatically

**Context Management** (to be documented):
- See `claude-code-workflow.md` (planned)
- See `context-management.md` (planned)

## Safeguards

To ensure quality when using AI assistants:

1. **Always read the spec first** — Don't ask AI to guess requirements
2. **Reference patterns explicitly** — Link to specific pattern documents
3. **Validate with checklists** — Use `94-validation/` after implementation
4. **Run validation scripts** — Execute `./neotool validate` before PR
5. **Review security standards** — Check `09-security/` for auth/authz code
6. **Check test coverage** — Maintain 90% backend, 80% frontend minimums

See `safeguards.md` (planned) for detailed guidelines.

## Validation Steps

After AI-assisted implementation:

1. **Feature checklist** — [94-validation/feature-checklist.md](../94-validation/feature-checklist.md)
2. **Code review checklist** — [94-validation/code-review-checklist.md](../94-validation/code-review-checklist.md)
3. **PR checklist** — [94-validation/pr-checklist.md](../94-validation/pr-checklist.md)
4. **Run validators** — `./neotool validate`

See `validation-steps.md` (planned) for step-by-step process.

## Best Practices

### When Creating Features
1. Start with [feature form template](../91-templates/feature-templates/feature-form.md)
2. Fill out [questionnaire](../91-templates/feature-templates/questionnaire.md)
3. Reference [AI prompts](../91-templates/ai-prompts/) for guidance
4. Follow [feature development workflow](../08-workflows/feature-development.md)

### When Implementing
1. Read architecture docs ([02-architecture/](../02-architecture/))
2. Review domain model ([04-domain/domain-model.md](../04-domain/domain-model.md))
3. Follow implementation patterns ([05-backend/patterns/](../05-backend/patterns/) or [07-frontend/patterns/](../07-frontend/patterns/))
4. Use code templates ([91-templates/code-templates/](../91-templates/code-templates/))
5. Check examples ([90-examples/](../90-examples/))

### When Reviewing
1. Verify spec compliance
2. Check pattern adherence
3. Validate test coverage
4. Review security implications
5. Confirm observability hooks

## Future Documentation

The following documents will be created to expand this section:

- **cursor-workflow.md** — Detailed Cursor integration guide
- **claude-code-workflow.md** — Claude Code best practices
- **prompt-engineering.md** — Effective prompts for AI assistants
- **context-management.md** — Managing AI context windows
- **safeguards.md** — Quality gates and validation
- **validation-steps.md** — Step-by-step validation process
- **ai-assisted-implementation.md** — Complete SDD workflow

## Related Documentation

- **Feature templates**: [91-templates/feature-templates/](../91-templates/feature-templates/)
- **AI prompts**: [91-templates/ai-prompts/](../91-templates/ai-prompts/)
- **Workflows**: [08-workflows/](../08-workflows/)
- **Validation**: [94-validation/](../94-validation/)
- **manifest.md**: [Complete document index](../manifest.md)

## Philosophy

> **"Documentation is code. Code follows documentation."**

In NeoTool's SDD approach:
- Specs are the source of truth
- Implementation is a translation of specs
- AI assistants accelerate translation
- Validation ensures fidelity
- Feedback improves specs

This creates a virtuous cycle where documentation and code co-evolve, maintaining alignment between intent and implementation.
