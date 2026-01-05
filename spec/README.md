# Specifications (spec/)

> **Purpose**: Minimal, focused specifications for LLM-driven feature development.

## What is `spec/`?

The `spec/` directory contains **specifications for what to build**, optimized for AI assistants like Claude Code and Cursor.

**Key Principle**: Specifications define **WHAT** and **WHY**. The codebase shows **HOW**.

## Structure

```
spec/
├── features/          # Feature specifications
├── adr/              # Architecture Decision Records (for LLMs)
├── templates/        # Templates for creating specs
└── README.md         # This file
```

## How `spec/` is Different from `docs/`

| Aspect | `spec/` (LLMs) | `docs/` (Humans) |
|--------|----------------|------------------|
| **Audience** | AI assistants | Human developers |
| **Purpose** | What to build next | How things work |
| **Content** | Minimal, focused | Comprehensive, detailed |
| **Usage** | Read per feature | Reference when needed |
| **Updates** | Per feature | Per pattern/decision |
| **Length** | 1-2 pages | As needed |

**Think of it this way:**
- **`spec/`** = Work orders for LLMs ("Build this feature")
- **`docs/`** = Reference manual for humans ("How we build things here")

## Creating a Feature Spec

### 1. Copy the Template

```bash
cp spec/templates/feature-template.md spec/features/your-feature-name.md
```

### 2. Fill Out the Sections

Focus on:
- **Problem**: What business need are we addressing?
- **Solution**: What are we building (high-level)?
- **Scope**: What's included and what's not?
- **Success Criteria**: How do we know it's done?
- **Technical Constraints**: Non-obvious requirements
- **Integration Points**: Where should the LLM explore in the codebase?

### 3. Keep It Minimal

**Good**:
- "Must use existing AuthContext for user identification"
- "Follow the CRUD pattern in /service/kotlin/asset/"

**Too Much**:
- Step-by-step implementation instructions
- Code snippets (LLM learns from actual code)
- Detailed pattern explanations (that's in `docs/`)

### 4. Point to Code, Don't Duplicate It

Instead of explaining patterns, point to existing implementations:

```markdown
## Integration Points

**Storage**
- Path: `/service/kotlin/asset/service/BucketService`
- Purpose: File upload handling
- Pattern: Follow bucket selection and validation logic
```

The LLM will explore that code and learn the pattern.

## LLM Workflow

When you give a feature spec to an LLM (Claude Code, Cursor), the workflow is:

1. **LLM reads the spec** - Understands WHAT to build and WHY
2. **LLM explores integration points** - Learns HOW by reading existing code
3. **LLM implements** - Follows patterns discovered in the codebase
4. **Validate** - Check against success criteria in the spec

**No context loading strategies. No pattern documents. Just: spec → explore → implement.**

## When to Create a Spec

Create a feature spec when:
- ✅ Building a new feature
- ✅ Making significant changes to existing features
- ✅ Adding new domain concepts
- ✅ Complex refactoring with business impact

Don't create a spec for:
- ❌ Bug fixes (unless they change behavior)
- ❌ Tiny UI tweaks
- ❌ Dependency updates
- ❌ Code cleanup without functional changes

## Architecture Decision Records (ADRs)

The `spec/adr/` directory contains **decisions that LLMs need to know** when implementing features.

**What goes in `spec/adr/`:**
- Decisions that affect multiple features
- Non-obvious technical choices
- Constraints that aren't obvious from code

**Examples:**
- "Why we use UUID v7 instead of UUID v4"
- "Why profile updates are synchronous, not queued"
- "Why we encrypt user data at rest"

**What doesn't go here:**
- Detailed pattern explanations (those are in `docs/patterns/`)
- Historical context humans need (those are in `docs/adr/`)
- Implementation details (code is the source of truth)

See [spec/templates/adr-template.md](./templates/adr-template.md) for the format.

## Examples

### Good Feature Spec

```markdown
# Feature: User Profile Export

## Problem
Users want to download their profile data for record-keeping and portability.
GDPR requires we provide data export on request.

## Solution
Self-service profile export to JSON/PDF.

## Success Criteria
1. User can trigger export from profile page
2. Export includes all profile data
3. Available in JSON and PDF formats
4. Export is delivered via email within 5 minutes

## Integration Points
**Export Service**
- Path: `/service/kotlin/export/service/ExportService`
- Purpose: Async export pattern
- Pattern: Queue job, process async, email result

## Technical Constraints
- Must include all PII for GDPR compliance
- Export must be encrypted in transit
- Files expire after 7 days
```

**Why this is good:**
- Clear problem and solution
- Specific success criteria
- Points to existing code to learn from
- Lists non-obvious constraints
- Doesn't try to explain HOW to implement

### Bad Feature Spec (Too Much)

```markdown
# Feature: User Profile Export

## Implementation Steps
1. Create ExportController with @PostMapping
2. Add ExportService with these methods:
   - exportToJson(userId: UUID): ByteArray
   - exportToPdf(userId: UUID): ByteArray
3. Use JPA to query User entity
4. Convert to JSON using Jackson
5. Generate PDF using iText library
6. Send email using EmailService
...
```

**Why this is bad:**
- Trying to prescribe HOW instead of WHAT
- LLM should learn these patterns from code
- Brittle - breaks if our patterns evolve
- Redundant with existing code examples

## Tips for Writing Great Specs

### ✅ Do

- **Be specific about business context** - Why are we building this?
- **List measurable success criteria** - How do we know it's done?
- **Point to similar code** - Where should the LLM look to learn patterns?
- **Call out non-obvious constraints** - What's not obvious from code?
- **Keep it short** - 1-2 pages max

### ❌ Don't

- **Write implementation guides** - Let the LLM learn from code
- **Duplicate pattern docs** - Reference them, don't copy them
- **Include code snippets** - Point to actual code instead
- **Prescribe exact approach** - Give constraints, let LLM find best path
- **Explain obvious things** - Trust the LLM can read code

## Workflow Integration

### For Developers

1. **Plan Feature** → Write spec using template
2. **Hand to LLM** → "Implement spec/features/your-feature.md"
3. **LLM Implements** → Following patterns from codebase
4. **Validate** → Check success criteria
5. **Update Spec** → If decisions changed during implementation

### For LLMs (Claude Code, Cursor)

**Prompt pattern:**
```
Implement the feature specified in spec/features/user-profile-export.md

Steps:
1. Read the spec completely
2. Explore the integration points listed
3. Understand patterns from existing code
4. Implement following those patterns
5. Ensure all success criteria are met
```

## Maintenance

### Updating Specs

- ✅ Update spec if requirements change
- ✅ Mark open decisions as resolved
- ✅ Add new constraints discovered during implementation
- ❌ Don't add implementation details
- ❌ Don't turn it into documentation

### Archiving Specs

Once a feature is fully implemented and stable:
- Keep the spec in `spec/features/` (it's historical record)
- Link it from relevant `docs/` pages if humans need context
- Reference it in ADRs if decisions were made

### Spec Lifecycle

```
Draft → In Development → Implemented → Archived
```

- **Draft**: Being written, may have open decisions
- **In Development**: Handed to LLM, actively implementing
- **Implemented**: Feature complete, spec is reference
- **Archived**: Feature deprecated, moved to `spec/archive/`

## Questions?

**Q: Should I document the implementation approach?**
**A**: No. Just define WHAT needs to be built and point to similar code. The LLM will figure out HOW.

**Q: What if there's no similar code to reference?**
**A**: Then you're breaking new ground. In that case:
1. Implement a minimal version manually
2. Document the pattern in `docs/patterns/`
3. Future specs can reference that implementation

**Q: Should I update the spec after implementation?**
**A**: Only if:
- Requirements changed during development
- Open decisions were resolved
- New constraints were discovered

Don't add implementation details to the spec.

**Q: What about human documentation?**
**A**: That lives in `docs/`. The `spec/` is specifically for LLM-driven development.

---

## Related

- [Feature Template](./templates/feature-template.md) - Start here for new features
- [ADR Template](./templates/adr-template.md) - Document key decisions
- [SDD Workflow](../docs/workflows/sdd-workflow.md) - Complete development process
- [Human Documentation](../docs/README.md) - Comprehensive reference for developers
