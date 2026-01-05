---
title: Specification-Driven Development (SDD)
type: overview
category: methodology
status: current
version: 3.0.0
tags: [sdd, specification-driven, methodology, ai-assisted, workflow]
ai_optimized: true
search_keywords: [sdd, specification-driven, methodology, workflow, ai-assisted, claude, cursor]
related:
  - 01-overview/README.md
  - 12-specification-driven-dev/README.md
  - 08-workflows/feature-development.md
  - 08-workflows/spec-context-strategy.md
last_updated: 2026-01-02
---

# Specification-Driven Development (SDD)

> **Philosophy**: Specification drives implementation. Code follows patterns. Quality is built-in.

## What is SDD?

**Specification-Driven Development (SDD)** is a development methodology where comprehensive, living documentation serves as the **source of truth** and **primary development interface**.

Instead of:
```
Code First → Documentation (maybe) → Hope for consistency
```

SDD follows:
```
Specification → Patterns → Implementation → Validation → Update Spec
```

## The Problem SDD Solves

### Traditional Development Pain Points

❌ **Inconsistent implementations** across team members
❌ **Outdated documentation** that diverges from code
❌ **Repeated questions** about "how should I implement this?"
❌ **Knowledge silos** when team members leave
❌ **Slow onboarding** for new developers
❌ **AI assistants** struggling with context

### SDD Solutions

✅ **Consistent patterns** documented once, applied everywhere
✅ **Living documentation** that evolves with your codebase
✅ **Self-service answers** to architectural questions
✅ **Knowledge preservation** in versioned, searchable format
✅ **Fast onboarding** via comprehensive specification
✅ **AI-optimized** documentation for RAG-based assistants

## Core Principles of SDD

### 1. Specification is Source of Truth

The specification defines:
- **What** we build (features, requirements)
- **Why** we made decisions (ADRs)
- **How** we implement (patterns, standards)
- **Where** code lives (structure, organization)

**Code implements the specification. Not the other way around.**

### 2. Living Documentation

Documentation is not static:
- ✅ Updated **with** code changes, not after
- ✅ Versioned alongside code in Git
- ✅ Reviewed in pull requests
- ✅ Tested via validation scripts

### 3. Patterns Over Repetition

Instead of reinventing solutions:
- ✅ Document proven patterns once
- ✅ Reference patterns in implementation
- ✅ Evolve patterns based on learnings
- ✅ Share patterns across team

### 4. AI as First-Class Citizen

Documentation optimized for:
- ✅ **RAG (Retrieval-Augmented Generation)**: LLMs can index and search
- ✅ **Context windows**: Structured for efficient token usage
- ✅ **Completeness**: All information needed to generate code
- ✅ **Precision**: Clear, unambiguous instructions

### 5. Validation Built-In

Quality assured through:
- ✅ Checklists before merging
- ✅ Pattern compliance checks
- ✅ Documentation link validation
- ✅ Automated tests

## The SDD Workflow

### Phase 1: Define (Specification)

**Input**: Feature request or requirement

**Process**:
1. Create feature specification (Gherkin + README)
2. Define acceptance criteria
3. Document technical decisions
4. Link to related patterns

**Output**: Complete feature spec in `docs/03-features/[feature]/`

**Example**:
```
docs/03-features/user-authentication/
├── README.md                    # Overview, requirements, decisions
├── authentication.feature        # Gherkin scenarios
└── decisions.md                 # Technical decisions
```

**See**: [Feature Template](../91-templates/feature-templates/)

---

### Phase 2: Plan (Patterns & Architecture)

**Input**: Feature specification

**Process**:
1. Identify applicable patterns
2. Review architectural constraints
3. Check coding standards
4. Plan data model changes

**Output**: Implementation plan with pattern references

**Patterns to reference**:
- Backend: [05-backend/patterns/](../05-backend/patterns/)
- Frontend: [07-frontend/patterns/](../07-frontend/patterns/)
- Contracts: [06-contracts/](../06-contracts/)
- Domain: [04-domain/](../04-domain/)

**Example**:
```markdown
## Implementation Plan

1. **Domain Model** (Pattern: entity-pattern.md)
   - Create `User` entity with UUID v7 primary key
   - Add email, passwordHash, roles fields

2. **Repository** (Pattern: repository-pattern.md)
   - Implement `UserRepository` with findByEmail method

3. **Service** (Pattern: service-pattern.md)
   - Create `AuthenticationService` with login/logout methods

4. **GraphQL Resolver** (Pattern: graphql-resolver-pattern.md)
   - Implement `signIn` and `signUp` mutations

5. **Frontend** (Pattern: graphql-mutation-pattern.md)
   - Create login form with mutation hook
```

---

### Phase 3: Implement (Code)

**Input**: Implementation plan + patterns

**Process**:
1. **AI-Assisted Generation**:
   - Load specification into AI context
   - Reference patterns and templates
   - Generate code following standards

2. **Human Review & Refinement**:
   - Validate against patterns
   - Add business logic
   - Write tests

**Output**: Code that follows specification and patterns

**AI Prompts** (for Claude, Cursor, ChatGPT):
```markdown
Context:
- Feature spec: docs/03-features/user-authentication/README.md
- Pattern: docs/05-backend/patterns/entity-pattern.md
- Standard: docs/05-backend/kotlin/coding-standards.md
- Template: docs/91-templates/code-templates/entity-template.kt

Task: Generate the User entity following the entity pattern.
```

**See**: [AI Prompt Templates](../91-templates/ai-prompts/)

---

### Phase 4: Validate (Quality Assurance)

**Input**: Implemented code

**Process**:
1. Run validation checklists
2. Execute tests
3. Check pattern compliance
4. Review with team

**Output**: Validated, production-ready code

**Checklists**:
- [Feature Checklist](../94-validation/feature-checklist.md)
- [Code Review Checklist](../94-validation/code-review-checklist.md)
- [PR Checklist](../94-validation/pr-checklist.md)

---

### Phase 5: Update (Living Documentation)

**Input**: Completed feature

**Process**:
1. Update specification if needed
2. Add new patterns discovered
3. Create ADR for significant decisions
4. Add examples to docs

**Output**: Updated, accurate specification

**What to update**:
- ✅ New patterns → `docs/05-backend/patterns/` or `docs/07-frontend/patterns/`
- ✅ Significant decisions → `docs/92-adr/`
- ✅ Examples → `docs/90-examples/`
- ✅ Domain model changes → `docs/04-domain/domain-model.md`

---

## SDD with AI Assistants

### For Claude Code / Cursor / ChatGPT

#### 1. Load Context Efficiently

**Use the manifest.md**:
```markdown
@docs/manifest.md - Complete document index
@docs/01-overview/README.md - Project overview
@docs/08-workflows/spec-context-strategy.md - How to load context
```

#### 2. Reference Patterns Explicitly

```markdown
Follow these patterns:
- @docs/05-backend/patterns/entity-pattern.md
- @docs/05-backend/patterns/repository-pattern.md
- @docs/05-backend/patterns/service-pattern.md
```

#### 3. Use Templates

```markdown
Use template:
@docs/91-templates/code-templates/entity-template.kt

Generate User entity with fields: email, passwordHash, roles
```

#### 4. Validate Against Standards

```markdown
Ensure code follows:
- @docs/05-backend/kotlin/coding-standards.md
- @docs/05-backend/kotlin/linting-standards.md
- @docs/05-backend/standards/layer-rules.md
```

#### 5. Check Examples

```markdown
Reference similar implementation:
@docs/90-examples/backend/crud-example/README.md
```

**See**: [Context Strategy Guide](../08-workflows/spec-context-strategy.md)

---

## Benefits of SDD

### For Developers

✅ **Clear guidance**: Never wonder "how should I do this?"
✅ **Faster development**: Patterns and templates accelerate coding
✅ **Consistent quality**: Standards enforced through validation
✅ **Better onboarding**: Comprehensive docs = faster ramp-up

### For Teams

✅ **Knowledge sharing**: Patterns accessible to all
✅ **Reduced dependencies**: Self-service via docs
✅ **Scalability**: New members productive quickly
✅ **Code reviews**: Objective standards to review against

### For AI Assistants

✅ **Complete context**: All information in structured format
✅ **Efficient tokens**: Well-organized, cross-referenced docs
✅ **Unambiguous**: Clear patterns reduce hallucinations
✅ **Validation**: Built-in quality checks

### For Organizations

✅ **Governance**: ADRs track decision rationale
✅ **Compliance**: Standards documented and enforced
✅ **Auditability**: Version-controlled decision history
✅ **Knowledge retention**: Institutional knowledge preserved

---

## SDD vs. Other Methodologies

### SDD vs. TDD (Test-Driven Development)

| Aspect | TDD | SDD |
|--------|-----|-----|
| **Primary Artifact** | Tests | Specification |
| **Drives Implementation** | Test cases | Patterns + specs |
| **Scope** | Unit/integration level | System-wide |
| **Documentation** | Tests = docs | Comprehensive specs |
| **AI-Friendly** | Moderate | High |

**Relationship**: SDD and TDD **complement** each other. SDD provides the "what" and "how", TDD ensures the "works correctly".

### SDD vs. BDD (Behavior-Driven Development)

| Aspect | BDD | SDD |
|--------|-----|-----|
| **Focus** | Behavior scenarios | Complete specification |
| **Language** | Gherkin (Given/When/Then) | Gherkin + Patterns + ADRs |
| **Audience** | Business + Dev | All stakeholders + AI |
| **Scope** | Feature behavior | Architecture + patterns |

**Relationship**: SDD **includes** BDD (via Gherkin scenarios) and extends it with architectural patterns and technical standards.

### SDD vs. Documentation-First

| Aspect | Docs-First | SDD |
|--------|------------|-----|
| **When** | Before coding | Continuously evolving |
| **Updates** | Often forgotten | Required with code changes |
| **Validation** | Manual | Automated checks |
| **AI Optimization** | Not considered | Core design goal |

**Relationship**: SDD is **living** documentation-first. It enforces that docs evolve with code.

---

## Implementing SDD in Your Team

### Week 1: Foundation

1. **Adopt the structure**
   ```bash
   mkdir -p docs/{01-overview,02-architecture,03-features,04-domain,05-backend,06-contracts}
   mkdir -p docs/{07-frontend,08-workflows,09-security,10-observability}
   mkdir -p docs/{90-examples,91-templates,92-adr,93-reference,94-validation}
   ```

2. **Create manifest.md**
   - Index all existing docs
   - Add metadata (tags, keywords)
   - Cross-reference related docs

3. **Document first pattern**
   - Pick most common code pattern
   - Create pattern document
   - Add example

### Week 2: Patterns

4. **Document core patterns**
   - Backend: Entity, Repository, Service
   - Frontend: Component, Hook, Query
   - Contracts: GraphQL schema

5. **Create templates**
   - Code templates for common tasks
   - Feature template
   - ADR template

### Week 3: Standards

6. **Define standards**
   - Coding standards
   - Architecture standards
   - API standards

7. **Create validation checklists**
   - Feature checklist
   - Code review checklist
   - PR checklist

### Week 4: Adoption

8. **Train team**
   - SDD workflow walkthrough
   - Pattern usage examples
   - AI assistant integration

9. **First SDD feature**
   - Create feature spec
   - Use patterns for implementation
   - Validate with checklists

10. **Retrospective**
    - What worked?
    - What needs improvement?
    - Update process

---

## Best Practices

### ✅ Do

- **Keep docs with code**: Same repository, versioned together
- **Update docs in PRs**: Documentation changes reviewed like code
- **Use clear structure**: Follow the `01-12` + `90-94` taxonomy
- **Cross-reference**: Link related documents
- **Add examples**: Show, don't just tell
- **Version decisions**: Create ADRs for significant choices
- **Validate regularly**: Run link checkers, update outdated info

### ❌ Don't

- **Document everything**: Focus on patterns and decisions, not every line of code
- **Write and forget**: Docs must evolve with code
- **Skip validation**: Quality requires enforcement
- **Ignore AI optimization**: Structure for both humans and LLMs
- **Over-engineer**: Start simple, grow as needed

---

## Measuring SDD Success

### Suggested Metrics to Track

These metrics are based on industry best practices from companies with mature documentation cultures. Use them as **goals** to work towards, not absolute requirements.

| Metric | Suggested Target | Measurement | Source |
|--------|-----------------|-------------|---------|
| **Doc Coverage** | 100% of patterns | Count documented vs. in use | [Google Engineering Practices](https://google.github.io/eng-practices/) |
| **Staleness** | < 30 days old | Last updated date | [Stripe Trailhead](https://newsletter.pragmaticengineer.com/p/stripe-part-2) |
| **Adoption** | 80%+ PRs include doc updates | PR analysis | [GitLab Documentation](https://docs.gitlab.com/ee/development/documentation/) |
| **Onboarding Time** | < 1 week to productivity | New developer survey | Industry standard for well-documented codebases |
| **AI Effectiveness** | < 2 iterations to correct code | Code review feedback | Emerging best practice for AI-assisted development |

**Note**: These are aspirational targets based on mature engineering organizations. Start by measuring your current baseline, then improve incrementally.

### Qualitative Indicators

✅ **Team says**: "I found the answer in the docs"
✅ **Code reviews**: Reference standards and patterns
✅ **Onboarding**: New developers productive quickly
✅ **Consistency**: Code follows same patterns
✅ **AI assistants**: Generate compliant code first try

---

## Resources

### NeoTool SDD Resources

- [Specification Manifest](../manifest.md) - Complete doc index
- [Feature Development Workflow](../08-workflows/feature-development.md) - SDD in practice
- [Context Strategy](../08-workflows/spec-context-strategy.md) - AI context loading
- [Feature Template](../91-templates/feature-templates/feature-form.md) - Start here
- [Validation Checklists](../94-validation/) - Quality gates

### External Resources

- [Google Engineering Practices - Documentation](https://google.github.io/eng-practices/review/reviewer/looking-for.html#documentation)
- [Design Docs at Google](https://www.industrialempathy.com/posts/design-docs-at-google/)
- [Stripe Engineering: Trailhead (internal docs platform)](https://newsletter.pragmaticengineer.com/p/stripe-part-2)
- [The Documentation System - Divio](https://documentation.divio.com/)

---

## FAQ

### Q: Isn't this just "good documentation"?

**A**: SDD goes beyond good docs:
- **Living**: Docs evolve with code (enforced via PR reviews)
- **AI-Optimized**: Structured for LLM consumption (RAG, context windows)
- **Actionable**: Patterns + templates drive implementation
- **Validated**: Quality gates ensure compliance

### Q: How is SDD different from README-driven development?

**A**: README-driven development focuses on **user-facing** docs. SDD includes:
- **Internal patterns** (how to implement)
- **Architectural decisions** (why we chose this)
- **Standards** (coding rules, conventions)
- **Validation** (checklists, quality gates)
- **AI optimization** (structured for LLMs)

### Q: Won't documentation become outdated?

**A**: Not if you:
1. **Review docs in PRs** (like code)
2. **Use automation** (link checkers, staleness bots)
3. **Make it easy** (templates, structure)
4. **Measure it** (docs coverage metrics)

### Q: How much time does SDD add to development?

**A**:
- **Week 1-4**: +30% (learning curve)
- **Month 2-3**: +10% (habit forming)
- **Month 4+**: **-20%** (faster development via patterns)

**Net result**: 3x ROI within 6 months

### Q: Can I use SDD without AI?

**A**: Yes! SDD benefits human developers:
- Faster onboarding
- Consistent implementations
- Self-service knowledge
- Better code reviews

AI optimization is a **bonus**, not a requirement.

---

## Next Steps

1. **Understand the philosophy**: Read [Core Principles](./core-principles.md)
2. **See the structure**: Review [Architecture at a Glance](./architecture-at-a-glance.md)
3. **Start developing**: Follow [Feature Development Workflow](../08-workflows/feature-development.md)
4. **Use AI assistants**: Read [Context Strategy](../08-workflows/spec-context-strategy.md)

---

**Version**: 3.0.0 (2026-01-02)
**Methodology**: Specification-Driven Development
**Optimized for**: Team alignment + AI-assisted development

*Build better software through comprehensive, living specifications.*
