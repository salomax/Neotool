---
title: Specification-Driven Development (SDD) Workflow
type: workflow
category: development
status: current
version: 4.0.0
date: 2026-01-03
tags: [sdd, workflow, llm, ai-assisted]
---

# Specification-Driven Development (SDD) Workflow

> **Philosophy**: Specification defines WHAT and WHY. Codebase shows HOW.

## Overview

Specification-Driven Development (SDD) is a pragmatic approach to building software where:

1. **Humans write minimal specs** defining WHAT to build and WHY
2. **LLMs explore the codebase** to learn HOW we build things
3. **LLMs implement** following patterns discovered in existing code
4. **Humans validate** against success criteria

**Not**: Extensive documentation → LLM context loading → Implementation
**But**: Minimal spec → LLM exploration → Pattern-following implementation

## The Two Documentation Systems

NeoTool uses two separate documentation systems with different purposes:

### `docs/` - For Humans

**Purpose**: Comprehensive reference for human developers

**Contains**:
- Architecture diagrams and explanations
- Detailed pattern documentation
- Coding standards and conventions
- Domain model documentation
- Security guidelines
- Historical context and reasoning

**Usage**:
- Read when onboarding
- Reference when unsure about a pattern
- Update when patterns evolve
- **NOT loaded as LLM context**

### `spec/` - For LLMs

**Purpose**: Minimal specifications for AI-assisted development

**Contains**:
- Feature specifications (what to build)
- Architecture Decision Records (key constraints)
- Pointers to code examples (where to explore)

**Usage**:
- Write before implementing a feature
- Give to LLM as implementation input
- Update if requirements change
- **Loaded as LLM context**

## The SDD Workflow

### Step 1: Write the Spec (10-15 minutes)

**Action**: Create a feature specification using the template.

```bash
cp spec/templates/feature-template.md spec/features/my-feature.md
```

**Fill out**:
- **Problem**: Business context and user need
- **Solution**: High-level approach
- **Scope**: What's in/out
- **Success Criteria**: Measurable validation points
- **Technical Constraints**: Non-obvious requirements
- **Integration Points**: Where to explore in codebase

**Example**:
```markdown
# Feature: User Profile Management

## Problem
Users cannot update their profile information after signup.
Support receives 50+ requests/month for manual profile changes.

## Solution
Self-service profile management interface.

## Success Criteria
1. User can view current profile
2. User can update name, email, avatar
3. Email changes validate uniqueness
4. All changes are audit logged
5. Updates trigger analytics events

## Integration Points
**Similar CRUD Pattern**
- Path: `/service/kotlin/asset/service/AssetService.kt`
- Purpose: Learn CRUD + audit trail pattern

**Authentication**
- Path: `/service/kotlin/security/service/AuthenticationService.kt`
- Purpose: Learn auth context usage
```

**Key Principles**:
- ✅ Define WHAT and WHY
- ✅ Point to similar code
- ✅ List non-obvious constraints
- ❌ Don't write implementation steps
- ❌ Don't duplicate pattern docs
- ❌ Don't include code snippets

### Step 2: Hand to LLM (1 minute)

**Action**: Give the spec to your LLM (Claude Code, Cursor).

**Prompt Template**:
```
Implement the feature specified in spec/features/my-feature.md

Process:
1. Read the complete spec
2. Explore the integration points listed
3. Learn patterns from the existing code
4. Implement following those patterns
5. Ensure all success criteria are met
```

**What the LLM does**:
1. Reads the spec to understand WHAT and WHY
2. Explores code paths mentioned in "Integration Points"
3. Infers patterns from actual implementations
4. Generates code following discovered patterns
5. Validates against success criteria

**No manual context loading. No pattern documents. Just: spec → explore → implement.**

### Step 3: LLM Implementation (Automated)

**The LLM workflow** (handled automatically):

1. **Read Spec**
   - Understand the problem and solution
   - Note success criteria for validation
   - Identify technical constraints

2. **Explore Codebase**
   - Navigate to integration points
   - Read similar implementations
   - Identify patterns and conventions
   - Understand project structure

3. **Implement**
   - Follow discovered patterns
   - Apply learned conventions
   - Respect technical constraints
   - Create consistent code

4. **Self-Validate**
   - Check against success criteria
   - Ensure constraints are met
   - Follow project standards

**If the plan looks wrong or incomplete:**
- Add/adjust integration points in the spec (point to a clean, tested example) and rerun.
- If security/guardrails are missing, point to the right code path (auth/validation) and rerun.
- If the AI cannot find patterns, narrow the scope (one layer at a time) or provide a single exemplar path.

### Step 4: Human Validation (5-10 minutes)

**Action**: Review and validate the implementation.

**Run the validation command first:**
- Cursor: `/spec-validate <feature-slug>`
- Claude Code: `validate spec <feature-slug>`
- Then run project tests as needed (`./gradlew test`, `npm test`, `lint`).

**Checklist**:
- [ ] All success criteria met?
- [ ] Follows existing patterns?
- [ ] Tests included and passing?
- [ ] No obvious bugs or issues?
- [ ] Documentation updated (if needed)?

**If issues found**: Give feedback to LLM and iterate.

**If looks good**: Proceed to testing and review.

### Step 5: Update Spec (Optional, 2-3 minutes)

**Action**: Update the spec if anything changed during implementation.

**Update if**:
- Requirements changed
- Open decisions were resolved
- New constraints discovered
- Approach significantly different than planned

**Don't update with**:
- Implementation details
- Code snippets
- Step-by-step how-to

**Example updates**:
```markdown
## Open Decisions
- [x] Email verification on change? → **Decision**: Yes, send verification email
- [x] Avatar resize: client or server? → **Decision**: Server-side, 200x200 and 800x800

## Technical Constraints (Added)
- Avatar uploads must be scanned for malware (requirement added during security review)
```

## Real-World Example

### Before: Traditional Approach

Developer writes detailed implementation plan:
```markdown
1. Create UserProfileController.kt with these endpoints:
   - GET /api/profile/{id}
   - PUT /api/profile/{id}
2. Add UserProfileService.kt with methods:
   - findProfile(id: UUID): UserProfile
   - updateProfile(id: UUID, input: UpdateProfileInput): UserProfile
3. Create UserProfile entity with fields:
   - id: UUID (v7, indexed)
   - userId: UUID (FK to User)
   - displayName: String (max 100 chars)
   ...
```

**Problems**:
- Prescriptive, brittle
- Duplicates pattern docs
- Breaks if patterns evolve
- LLM just copies the instructions

### After: SDD Approach

Developer writes minimal spec:
```markdown
# Feature: User Profile Management

## Problem
Users need to update profile info. Currently requires support.

## Success Criteria
1. User can view profile
2. User can update name, email, avatar
3. Email uniqueness validated
4. Changes audit logged

## Integration Points
**Similar CRUD**: `/service/kotlin/asset/service/AssetService.kt`
**Auth**: `/service/kotlin/security/service/AuthenticationService.kt`

## Constraints
- Max 5 profile updates/hour/user
- Email changes require password confirmation
```

**LLM explores AssetService.kt**:
- Discovers CRUD pattern
- Learns audit trail pattern
- Sees validation approach
- Understands error handling

**LLM generates code** matching those patterns automatically.

**Benefits**:
- Minimal spec writing time
- LLM learns real patterns
- Adapts to pattern evolution
- Consistent with codebase

## Common Scenarios

### Scenario 1: Completely New Feature

**Situation**: Building something we've never built before.

**Approach**:
1. Write spec with business context
2. Note: "No similar implementation exists"
3. Implement first version manually or with minimal LLM help
4. Document the pattern in `docs/patterns/` for humans
5. Future specs can reference this implementation

**Example**:
```markdown
## Integration Points
**Note**: This is the first real-time feature. No existing pattern to follow.

## Technical Constraints
- Must use WebSockets for real-time updates
- Fall back to polling if WebSocket fails
- Maximum 100 concurrent connections per user
```

After implementation, document the pattern in `docs/patterns/websocket-pattern.md` for human reference.

### Scenario 2: Similar to Existing Feature

**Situation**: Building a feature very similar to something that exists.

**Approach**:
1. Write spec pointing to similar feature
2. Note differences/additions
3. LLM explores and adapts existing pattern

**Example**:
```markdown
## Integration Points
**Very similar to**: `/service/kotlin/asset/service/AssetService.kt`

## Differences
- Profile updates are synchronous (assets are async)
- Profile has owner-only edit (assets have share permissions)
- No versioning needed (assets are versioned)
```

### Scenario 3: Cross-Cutting Change

**Situation**: Need to update pattern across many features.

**Approach**:
1. Make the change in one place first
2. Document decision in `spec/adr/`
3. Update `docs/patterns/` for humans
4. Use LLM to apply pattern to other features

**Example**:
```markdown
# ADR-042: Standardize Error Handling

All services must use Result<T, Error> instead of throwing exceptions.

## For LLMs
When implementing or refactoring services:
- Return Result<T, Error> from all public methods
- See updated pattern in `/service/kotlin/asset/service/AssetService.kt`
- Do NOT throw exceptions from service layer
```

### Scenario 4: Bug Fix with Behavior Change

**Situation**: Fixing a bug that changes expected behavior.

**Approach**:
- Small bug, no spec needed - just fix it
- Behavior change? Write a minimal spec

**Example**:
```markdown
# Fix: Email Case Sensitivity

## Problem
Email "User@Example.com" and "user@example.com" treated as different users.
Causes duplicate accounts and login confusion.

## Solution
Normalize emails to lowercase before storage and comparison.

## Success Criteria
1. All new emails stored in lowercase
2. Login works regardless of email case
3. Existing emails migrated to lowercase

## Integration Points
- Similar normalization: `/service/kotlin/security/service/AuthenticationService.kt` (username normalization)
```

## When to Write a Spec

### ✅ Write a Spec For

- New features
- Significant behavior changes
- Complex refactoring
- Cross-cutting changes
- Anything requiring LLM implementation

### ❌ Don't Write a Spec For

- Trivial bug fixes
- Dependency updates
- Code formatting/linting
- Documentation updates
- Single-line changes

**Rule of thumb**: If you'd give it to an LLM to implement, write a spec. If you'd just fix it yourself in 2 minutes, skip the spec.

## Best Practices

### Writing Specs

✅ **Do**:
- Keep it short (1-2 pages)
- Focus on business context
- List measurable success criteria
- Point to similar code
- Call out non-obvious constraints

❌ **Don't**:
- Write implementation steps
- Duplicate pattern docs
- Include code snippets
- Prescribe exact approach
- Explain obvious things

### Working with LLMs

✅ **Do**:
- Give complete spec upfront
- Let LLM explore freely
- Trust pattern learning
- Validate output against criteria
- Iterate on feedback

❌ **Don't**:
- Micromanage implementation
- Force specific approaches
- Load unnecessary context
- Skip validation
- Assume first output is perfect

### Maintaining Specs

✅ **Do**:
- Update if requirements change
- Mark decisions as resolved
- Add discovered constraints
- Keep specs after implementation (historical record)

❌ **Don't**:
- Add implementation details post-hoc
- Turn specs into documentation
- Delete specs after implementation
- Let specs diverge from reality

## Handling Failures (Quick Fixes)

- **Plan looks wrong**: Add/adjust integration points to a clean, tested path and rerun `/spec-implement`.
- **Missing tests**: Point to a test-heavy exemplar and rerun. Keep success criteria explicit.
- **Pattern drift**: Reduce scope (one layer) and show the best reference file before rerun.
- **Security gaps**: Point to guardrail code (auth/validation) and rerun; do not rely on memory.
- **LLM stuck on context**: Remove extra instructions; keep only spec + paths. Avoid loading docs as context.

## FAQ

### Q: Should I still write human documentation?

**A**: Yes! Human documentation (`docs/`) is still valuable:
- Architecture understanding
- Pattern explanations with WHY
- Onboarding materials
- Historical context

Just don't load it as LLM context. It's reference, not input.

### Q: What if the LLM doesn't follow patterns correctly?

**A**:
1. Check if your integration points are clear
2. Verify the pointed-to code is a good example
3. Add explicit constraints if pattern is subtle
4. Consider documenting pattern in `docs/` for next human who hits this

### Q: How detailed should success criteria be?

**A**: Detailed enough to validate objectively.

**Good**: "User can update email with uniqueness validation"
**Better**: "Email update checks uniqueness before saving and returns clear error if duplicate exists"

### Q: Should I include test requirements in the spec?

**A**: Yes, in success criteria.

```markdown
## Success Criteria
1. User can update profile (+ unit tests)
2. Email uniqueness validated (+ integration test)
3. Audit trail logged (+ verify in test)
4. E2E test for full flow
```

### Q: What about non-functional requirements?

**A**: Include in "Technical Constraints":

```markdown
## Technical Constraints
- Response time < 200ms (p95)
- Support 1000 concurrent users
- Must work offline with sync
- Accessible (WCAG 2.1 AA)
```

### Q: How do I handle breaking changes?

**A**: Document in spec and ADR:

```markdown
## Breaking Changes
- Email field changing from optional to required
- Migration: Set empty emails to username@generated.local

## Migration Plan
1. Add migration script
2. Update API (deprecate old, add new)
3. Update clients
4. Remove deprecated after 2 releases
```

Create `spec/adr/XXX-email-required.md` with decision rationale.

## Tools and Commands

### Creating a New Spec

```bash
# Copy template
cp spec/templates/feature-template.md spec/features/my-feature.md

# Edit the spec
vim spec/features/my-feature.md
```

### Implementing a Spec (Claude Code)

```bash
# In Claude Code
claude "Implement spec/features/my-feature.md"
```

### Implementing a Spec (Cursor)

```
# In Cursor chat
Implement the feature in spec/features/my-feature.md

Process:
1. Read the spec
2. Explore integration points
3. Learn patterns from existing code
4. Implement following those patterns
5. Validate against success criteria
```

### Validating Implementation

```bash
# Run tests
./gradlew test
npm test

# Check linting
./gradlew ktlintCheck
npm run lint

# Check against success criteria
# (Manual - compare implementation to spec)
```

## Measuring Success

### Quantitative Metrics

| Metric | Target | How to Measure |
|--------|--------|----------------|
| **Spec Completeness** | 100% of features have specs | Count specs vs. features |
| **LLM Success Rate** | 80%+ first implementation acceptable | Code review feedback |
| **Spec Update Rate** | < 30 days stale | Last modified date |
| **Pattern Consistency** | 90%+ code follows patterns | Code review or linting |

### Qualitative Indicators

✅ **Good signs**:
- "I just pointed the LLM to similar code and it worked"
- "Specs are quick to write"
- "LLM implementations are consistent"
- "Less back-and-forth with LLM"

❌ **Warning signs**:
- "Specs take forever to write"
- "LLM keeps generating wrong patterns"
- "I have to explain everything step-by-step"
- "Easier to just code it myself"

If you see warning signs, simplify further. The spec is too detailed or pointing to wrong examples.

## Migration from Old Approach

If you have existing documentation-heavy SDD:

### Week 1: Setup
1. Create `spec/` directory structure
2. Copy templates
3. Pick one feature to try new approach

### Week 2: Practice
4. Write 2-3 feature specs using new template
5. Use with LLM, gather feedback
6. Refine template based on learnings

### Week 3: Transition
7. Move relevant ADRs to `spec/adr/`
8. Keep `docs/` as human reference
9. Stop loading `docs/` as LLM context

### Week 4: Adopt
10. Make new approach the default
11. Update team workflow
12. Archive old workflow docs

**Don't** try to migrate all docs at once. Let new approach prove itself first.

## Related Documentation

- [spec/README.md](../../spec/README.md) - Detailed guide to the spec/ directory
- [spec/templates/feature-template.md](../../spec/templates/feature-template.md) - Feature spec template
- [spec/templates/adr-template.md](../../spec/templates/adr-template.md) - ADR template
- [docs/README.md](../README.md) - Human documentation system

---

**Version**: 4.0.0
**Date**: 2026-01-03
**Philosophy**: Minimal specs. Maximum learning. Pattern-driven implementation.

*Let the code teach. Let the spec guide.*
