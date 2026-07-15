---
title: Cursor Commands & Claude Code Skills for SDD
type: guide
category: workflow
status: current
version: 1.0.0
date: 2026-01-03
tags: [cursor, claude-code, commands, skills, automation, sdd]
---

# Cursor Commands & Claude Code Skills for SDD

> **Purpose**: Automate the Spec-Driven Development workflow using Cursor commands or Claude Code skills.

## Overview

Both Cursor and Claude Code support workflow automation:
- **Cursor**: Uses "commands" (files in `.cursor/commands/`)
- **Claude Code**: Uses "skills" (files in `.claude/skills/`, a symlink to the canonical `.agents/skills/` — see [Agent Skills spec](../specs/agent-skills/spec.md))

Both follow the **same new SDD workflow** - just different interfaces.

---

## Available Automations

### Feature Spec Creation

**Purpose:** Create a minimal feature specification

**Cursor:**
```
/spec-create
```

**Claude Code:**
```
create spec
```
or
```
/spec-create
```

**What it does:**
1. Asks about your feature (problem, solution, requirements)
2. Helps identify integration points (similar code)
3. Creates `spec/features/[feature-slug].md`
4. Uses template from `spec/templates/feature-template.md`

**Output:** Minimal spec ready for implementation

**Time:** ~10 minutes

---

### Feature Implementation

**Purpose:** Implement complete feature from spec

**Cursor:**
```
/spec-implement user-profile-management
```

**Claude Code:**
```
implement spec user-profile-management
```
or
```
/spec-implement user-profile-management
```

**What it does:**
1. Reads the spec
2. Explores integration points (existing code)
3. Learns patterns automatically
4. Implements backend + frontend + tests
5. Validates against success criteria

**Output:** Complete working implementation

**Time:** ~15-20 minutes (automated)

---

### Implementation Validation

**Purpose:** Validate implementation meets spec

**Cursor:**
```
/spec-validate user-profile-management
```

**Claude Code:**
```
validate spec user-profile-management
```
or
```
/spec-validate user-profile-management
```

**What it does:**
1. Checks each success criterion
2. Runs all tests
3. Verifies code quality
4. Checks pattern compliance
5. Generates comprehensive report

**Output:** Pass/fail validation report

**Time:** ~5 minutes

---

## Complete Workflow

### Using Cursor

```
# Step 1: Open Cursor chat (Cmd+L / Ctrl+L)
You: /spec-create

# Answer questions interactively
Cursor: [Creates spec/features/user-profile-management.md]

# Step 2: Implement
You: /spec-implement user-profile-management

Cursor: [Explores code, learns patterns, implements]
Cursor: ✅ Implementation complete!

# Step 3: Validate
You: /spec-validate user-profile-management

Cursor: [Runs tests, checks quality]
Cursor: ✅ Ready for review!
```

**Total time:** ~30-45 minutes

---

### Using Claude Code

```bash
# Step 1: Start Claude Code
$ claude

# Create spec
You: create spec for user profile management

Claude: [Asks questions, creates spec]
Claude: ✅ Spec created!

# Implement
You: implement spec user-profile-management

Claude: [Explores, learns, implements]
Claude: ✅ Implementation complete!

# Validate
You: validate spec user-profile-management

Claude: [Validates thoroughly]
Claude: ✅ Ready for review!
```

**Total time:** ~30-45 minutes

---

## Comparison: Cursor vs Claude Code

| Aspect | Cursor | Claude Code |
|--------|--------|-------------|
| **Interface** | IDE chat panel | Terminal |
| **Trigger** | Slash commands (`/spec-create`) | Natural language or slash |
| **File editing** | Inline in IDE | File write operations |
| **Context** | Full IDE context | Explicit file loading |
| **Best for** | Visual developers, IDE fans | Terminal users, CLI fans |
| **Speed** | Fast (inline edits) | Fast (file ops) |
| **Automation files** | `.cursor/commands/` | `.claude/skills/` |

**Both produce identical results - choose based on preference!**

---

## Command/Skill Structure

### Cursor Commands (`.cursor/commands/`)

```
.cursor/commands/
├── README.md                  # Overview and usage
├── spec-create.md             # Create spec command
├── spec-implement.md          # Implement feature command
├── spec-validate.md           # Validate implementation command
├── request.md                 # DEPRECATED (old workflow)
├── implement.md               # DEPRECATED (old workflow)
├── validate.md                # DEPRECATED (old workflow)
├── review.md                  # Still valid
└── e2e.md                     # Still valid
```

### Claude Code Skills (`.claude/skills/`, symlink to `.agents/skills/`)

```
.claude/skills/
├── README.md                  # Overview and usage
├── spec-create.md             # Create spec skill
├── spec-implement.md          # Implement feature skill
└── spec-validate.md           # Validate implementation skill
```

**Note:** Claude skills reference Cursor command instructions to avoid duplication. Also note: `.claude/skills/` is a symlink to `.agents/skills/` (`.claude/skills -> ../.agents/skills`, versioned in git) — edit skill content in `.agents/skills/`; `.claude/skills/` just follows it, no build step involved.

---

## How Commands/Skills Work

### 1. Spec Creation (`spec-create`)

**User triggers:**
- Cursor: `/spec-create`
- Claude: "create spec"

**Process:**
1. **Gather info** - Ask user about feature
2. **Derive details** - Create feature slug, confirm scope
3. **Identify patterns** - Find similar code in codebase
4. **Create spec** - Use template, fill with user input
5. **Point to code** - Add integration points

**Template used:** `spec/templates/feature-template.md`

**Key sections filled:**
- Problem (business context)
- Solution (high-level approach)
- Scope (in/out)
- Success Criteria (measurable outcomes)
- Integration Points (code to explore)
- Technical Constraints (non-obvious requirements)

**Output:** `spec/features/[slug].md`

---

### 2. Implementation (`spec-implement`)

**User triggers:**
- Cursor: `/spec-implement [slug]`
- Claude: "implement spec [slug]"

**Process:**
1. **Read spec** - Load and understand requirements
2. **Explore code** - Navigate to integration points
3. **Learn patterns** - Analyze existing implementations
4. **Create plan** - Break down into tasks
5. **Implement** - Generate code following patterns
6. **Test** - Create unit/integration/e2e tests
7. **Validate** - Check against success criteria

**Example exploration:**

If spec says:
```markdown
**Integration Point:**
- Path: `/service/kotlin/asset/service/AssetService.kt`
- Purpose: CRUD with audit trail
```

LLM does:
1. Reads `AssetService.kt`
2. Identifies pattern: Repository → Service → Resolver
3. Notes: UUID v7, audit logging, validation
4. Applies same pattern to new feature

**Output:**
- Backend files (migrations, entities, services, resolvers)
- Frontend files (operations, hooks, components)
- Test files (unit, integration, e2e)

---

### 3. Validation (`spec-validate`)

**User triggers:**
- Cursor: `/spec-validate [slug]`
- Claude: "validate spec [slug]"

**Process:**
1. **Load spec** - Read success criteria
2. **Check criteria** - Validate each one
3. **Run tests** - Execute all test suites
4. **Check quality** - Linting, types, patterns
5. **Security check** - Auth, validation, XSS, etc.
6. **Performance check** - Response time, queries, etc.
7. **Generate report** - Comprehensive pass/fail

**Report includes:**
- ✅/❌ for each success criterion
- Test results (pass/fail counts, coverage)
- Code quality (linting, types, build)
- Pattern compliance
- Security validation
- Performance metrics
- Recommendations

**Possible outcomes:**
- ✅ All passing → Ready for review
- ❌ Some failing → List of issues to fix

---

## Real-World Example

### Scenario: Build User Profile Feature

**Step 1: Create Spec (10 min)**

```
Cursor/Claude: /spec-create

You:
- Feature: User Profile Management
- Problem: Users can't update profile after signup
- Users: All authenticated users
- Solution: Self-service profile editing
- Requirements:
  1. View/edit name, email, bio
  2. Upload avatar
  3. Validate email uniqueness
  4. Audit all changes

Cursor/Claude: [Asks follow-ups]
- Similar code? → Asset management (CRUD + audit)
- Constraints? → Avatar max 5MB, email unique

Cursor/Claude: ✅ Created spec/features/user-profile-management.md
```

**Spec contains:**
- Problem context (support overhead, user frustration)
- Success criteria (10 specific items)
- Integration points (Asset service, Auth service)
- Technical constraints (file size, validation rules)

---

**Step 2: Implement (20 min)**

```
Cursor/Claude: /spec-implement user-profile-management

Cursor/Claude: 🔍 Exploring integration points...

Explored: /service/kotlin/asset/
Patterns discovered:
✓ UUID v7 for IDs
✓ Repository → Service → Resolver flow
✓ Audit trail via separate entity
✓ @RequirePermission for auth

Implementing...

✅ Backend:
- Migration: V015__create_user_profile.sql
- Entities: UserProfile.kt, UserProfileAudit.kt
- Repository: UserProfileRepository.kt
- Service: UserProfileService.kt (with validation)
- Resolver: UserProfileResolver.kt
- Schema: user-profile.graphqls

✅ Frontend:
- Operations: profile.graphql
- Hook: useProfile.ts
- Form: ProfileForm.tsx (with React Hook Form)
- Page: profile/page.tsx

✅ Tests:
- Unit: UserProfileServiceTest.kt (92% coverage)
- Integration: UserProfileResolverTest.kt
- Component: ProfileForm.test.tsx
- E2E: profile.spec.ts

Success Criteria: 10/10 met ✅
```

---

**Step 3: Validate (5 min)**

```
Cursor/Claude: /spec-validate user-profile-management

Cursor/Claude: Validating...

✅ Success Criteria: 10/10 met
✅ Tests: All passing (Backend: 57/57, Frontend: 11/11, E2E: 3/3)
✅ Coverage: Backend 92%, Frontend 88%
✅ Code Quality: No lint errors, no type errors
✅ Pattern Compliance: 100%
✅ Security: Auth checks, input validation, XSS prevention
✅ Performance: Response < 200ms, optimized queries

OVERALL: ✅ READY FOR REVIEW

Recommendations: None - excellent implementation!
```

---

**Total Time:** 35 minutes
**Traditional Time:** 3-5 hours
**Time Saved:** 80%+

---

## Best Practices

### When Creating Specs

✅ **Do:**
- Keep specs minimal (1-2 pages)
- Focus on business context and WHY
- Point to 2-4 integration points (similar code)
- Make success criteria specific and testable
- List non-obvious technical constraints

❌ **Don't:**
- Write implementation steps
- Include code snippets
- Try to explain patterns
- Be overly comprehensive
- Prescribe exact approaches

### When Implementing

✅ **Do:**
- Let LLM explore and learn patterns
- Trust automated pattern discovery
- Validate against success criteria
- Review generated code
- Iterate based on feedback

❌ **Don't:**
- Micromanage the implementation
- Skip the exploration phase
- Ignore validation failures
- Force specific approaches

### When Validating

✅ **Do:**
- Check every success criterion
- Run all test suites
- Verify pattern compliance
- Test manually for UX
- Fix issues before review

❌ **Don't:**
- Skip validation step
- Ignore test failures
- Accept low coverage
- Skip manual testing

---

## Troubleshooting

### Command/Skill Not Found

**Problem:** Cursor/Claude doesn't recognize the command

**Solution:**
- Cursor: Check files exist in `.cursor/commands/`
- Claude: Check files exist in `.claude/skills/` (symlink to `.agents/skills/`)
- Restart IDE/terminal

### Spec File Not Found

**Problem:** "Can't find spec/features/[slug].md"

**Solution:**
- Verify file exists at correct path
- Check file name matches slug
- Use full path if needed

### Pattern Not Followed

**Problem:** Generated code doesn't match codebase patterns

**Solution:**
- Check integration points in spec point to correct code
- Verify pointed code is a good example
- Add more integration points for clarity
- Update spec with better examples

### Validation Failures

**Problem:** Validation reports failures

**Solution:**
- Read failure details carefully
- Fix each issue listed
- Re-run validation
- Iterate until all passing

### LLM Doesn't Explore Code

**Problem:** Implementation doesn't follow patterns

**Solution:**
- Ensure integration points are in spec
- Make sure paths are correct
- Point to specific files, not just directories
- Add more examples if pattern is complex

---

## Advanced Usage

### Quick Validation

For faster feedback:

**Cursor:**
```
/spec-validate user-profile-management --quick
```

**Output:**
```
✅ Success criteria: 10/10
✅ Tests: Passing
✅ Linting: Clean
Status: READY ✅
```

### Partial Implementation

Implement specific parts:

**Cursor:**
```
You: /spec-implement user-profile-management
You: Just the backend for now, skip frontend

Cursor: [Implements only backend]
```

### Iterative Refinement

Fix validation issues:

```
Cursor: /spec-validate user-profile-management
Cursor: ❌ 2 issues found
        1. Missing email validation
        2. Test coverage 75% (target 90%)

You: Fix those issues

Cursor: [Fixes issues]
Cursor: ✅ Re-validated, all passing!
```

---

## Migration from Legacy Commands

If you have old workflow commands:

### Old Commands (Deprecated)

```
/request      # Created .tasks.yml, .memory.yml, .feature files
/implement    # Phase-based manual implementation
/validate     # Generic validation
```

### New Commands (Current)

```
/spec-create     # Creates single .md spec file
/spec-implement  # Automatic full-stack implementation
/spec-validate   # Success-criteria validation
```

### Migration Steps

1. **Stop using old commands** - They're deprecated
2. **Use new commands** - Start with `/spec-create`
3. **Archive old specs** - Keep for reference but don't maintain
4. **Educate team** - Share new workflow guide

---

## Comparison: Manual vs Automated

### Manual (No Commands/Skills)

**Process:**
1. Write spec manually (30-40 min)
2. Manually explore code for patterns
3. Implement backend (60-90 min)
4. Implement frontend (60-90 min)
5. Write tests (30-45 min)
6. Manual validation (15-20 min)

**Total:** 3-5 hours minimum

**Error-prone:** High (pattern inconsistencies, missing tests)

---

### Automated (With Commands/Skills)

**Process:**
1. `/spec-create` - Interactive (10 min)
2. `/spec-implement` - Automated (20 min)
3. `/spec-validate` - Automated (5 min)

**Total:** 35 minutes

**Error-prone:** Low (automated pattern learning, comprehensive validation)

**Time saved:** 80%+

---

## Resources

- **Workflow Guide:** [docs/workflows/sdd-workflow.md](./sdd-workflow.md)
- **Complete Example:** [docs/workflows/sdd-walkthrough-example.md](./sdd-walkthrough-example.md)
- **Spec Templates:** [spec/templates/](../../spec/templates/)
- **Spec README:** [spec/README.md](../../spec/README.md)
- **Cursor Commands:** [.cursor/commands/](../../.cursor/commands/)
- **Agent Skills (canonical, edit here):** [.agents/skills/](../../.agents/skills/)
- **Claude Skills (symlink):** [.claude/skills/](../../.claude/skills/)

---

## FAQ

**Q: Should I use Cursor or Claude Code?**
**A:** Either! Both produce identical results. Choose based on preference (IDE vs terminal).

**Q: Can I customize the commands/skills?**
**A:** Yes! Edit the markdown files in `.cursor/commands/` or `.agents/skills/` (`.claude/skills/` is a symlink to `.agents/skills/`, so both paths point at the same files — no sync step needed).

**Q: What if I don't want to use commands?**
**A:** You can still follow the manual workflow from `sdd-workflow.md`. Commands just automate it.

**Q: Do commands work for all features?**
**A:** Yes! They adapt to your codebase patterns. Works for any feature type.

**Q: Can I add my own commands/skills?**
**A:** Yes! Create new `.md` files in `.cursor/commands/` or new skill folders in `.agents/skills/` (they'll be copied into `.claude/skills/` automatically).

**Q: How do commands know what patterns to follow?**
**A:** They read the integration points in your spec and explore that code to learn patterns automatically.

---

**Version:** 1.0.0
**Date:** 2026-01-03
**Workflow:** New Spec-Driven Development

*Automate your development workflow. Let AI learn patterns from your code.*
