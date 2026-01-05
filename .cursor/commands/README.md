# Cursor Commands for Spec-Driven Development

This directory contains Cursor AI commands that automate the **new** Spec-Driven Development (SDD) workflow.

## Available Commands

### New SDD Commands (Recommended)

| Command | Purpose | Usage |
|---------|---------|-------|
| `/spec-create` | Create a new feature spec | Interactive spec creation |
| `/spec-implement` | Implement a feature from spec | `/spec-implement [feature-slug]` |
| `/spec-validate` | Validate implementation | `/spec-validate [feature-slug]` |

### Legacy Commands (Deprecated)

| Command | Status | Migration Path |
|---------|--------|----------------|
| `/request` | ⚠️ Deprecated | Use `/spec-create` instead |
| `/implement` | ⚠️ Deprecated | Use `/spec-implement` instead |
| `/validate` | ⚠️ Deprecated | Use `/spec-validate` instead |
| `/review` | ✅ Still valid | No changes needed |
| `/e2e` | ✅ Still valid | No changes needed |

## Complete Workflow

### 1. Create a Feature Spec

```
/spec-create
```

**What it does:**
- Asks you about the feature (problem, solution, requirements)
- Helps identify integration points (similar code to learn from)
- Creates a minimal spec in `spec/features/[feature-slug].md`
- Points to existing code patterns

**Output:** `spec/features/user-profile-management.md`

**Time:** ~10 minutes

---

### 2. Implement the Feature

```
/spec-implement user-profile-management
```

**What it does:**
- Reads the spec
- Explores integration points in your codebase
- Learns patterns from existing code
- Implements following those patterns
- Validates against success criteria

**Output:** Complete implementation (backend + frontend + tests)

**Time:** ~15-20 minutes (automated)

---

### 3. Validate Implementation

```
/spec-validate user-profile-management
```

**What it does:**
- Checks each success criterion
- Runs all tests
- Verifies code quality (linting, types)
- Checks pattern compliance
- Validates security requirements
- Provides comprehensive report

**Output:** Validation report with pass/fail status

**Time:** ~5 minutes

---

### 4. Code Review (Optional)

```
/review
```

**What it does:**
- Reviews code for quality, patterns, security
- Suggests improvements

---

### 5. E2E Tests (If needed)

```
/e2e
```

**What it does:**
- Creates or improves E2E tests

---

## Quick Reference

### Creating a New Feature

```bash
# Step 1: Create spec
/spec-create

# Answer questions about your feature
# [Interactive dialog]

# Step 2: Implement
/spec-implement [feature-slug]

# Step 3: Validate
/spec-validate [feature-slug]

# Step 4: Review (optional)
/review

# Done!
```

### Typical Timeline

| Step | Time | Who |
|------|------|-----|
| Create spec | 10 min | Human + AI |
| Implement | 15-20 min | AI |
| Validate | 5 min | AI |
| Review | 5-10 min | Human |
| **Total** | **35-45 min** | **Both** |

Compare to traditional: 3-5 hours minimum

**Time saved: 70-80%**

---

## How It Works

### The New SDD Philosophy

**Specification defines WHAT and WHY. Codebase shows HOW.**

1. **Human writes minimal spec** (business context, success criteria, integration points)
2. **AI explores codebase** (learns patterns from existing code)
3. **AI implements** (follows discovered patterns automatically)
4. **Human validates** (checks against success criteria)

### Why Commands Help

✅ **Automate repetitive steps** - Template filling, pattern exploration
✅ **Enforce structure** - Consistent spec format
✅ **Guide the workflow** - Step-by-step process
✅ **Reduce errors** - Validation built-in
✅ **Faster iteration** - Quick feedback loops

---

## Migration from Legacy Commands

If you were using the old workflow:

### Old Way (Deprecated)
```
/request  # Creates .tasks.yml, .memory.yml, .feature files
/implement domain from tasks.yml  # Manual phase implementation
/validate  # Generic validation
```

**Problems:**
- Complex context loading strategies
- Phase-based implementation (manual)
- Heavy documentation requirements
- Gherkin scenarios

### New Way (Recommended)
```
/spec-create  # Creates single .md spec file
/spec-implement feature-slug  # Automatic full implementation
/spec-validate feature-slug  # Criteria-based validation
```

**Benefits:**
- Minimal spec (1-2 pages)
- Learn from code, not docs
- Automatic pattern discovery
- Full-stack implementation
- Success-criteria validation

---

## Command Details

### `/spec-create`

**Purpose:** Create a minimal feature specification

**Process:**
1. Asks about feature (problem, solution, requirements)
2. Helps identify similar code (integration points)
3. Creates spec from template
4. Points to existing patterns

**Output:**
- `spec/features/[feature-slug].md`

**Template:** `spec/templates/feature-template.md`

**Example:**
```
You: /spec-create

AI: I'll help you create a feature spec. Tell me:
1. Feature name?
2. What problem does it solve?
3. Who uses it?
4. High-level solution?
5. Key requirements?

You: [Answers]

AI: [Creates spec/features/user-profile-management.md]
```

---

### `/spec-implement`

**Purpose:** Implement feature from spec

**Process:**
1. Reads spec
2. Explores integration points
3. Learns patterns from code
4. Implements backend + frontend + tests
5. Validates against success criteria

**Usage:**
```
/spec-implement user-profile-management
/spec-implement spec/features/user-profile-management.md
```

**Output:**
- Complete implementation
- All files created/modified
- Tests included

**Example:**
```
You: /spec-implement user-profile-management

AI: [Reads spec]
AI: I'll explore these code paths:
    - /service/kotlin/asset/ (CRUD pattern)
    - /service/kotlin/security/ (auth pattern)

AI: [Explores and learns patterns]

AI: Patterns discovered:
    ✓ UUID v7 for IDs
    ✓ Repository → Service → Resolver
    ✓ Audit trail pattern
    ✓ React Hook Form + mutations

AI: [Implements following patterns]

AI: ✅ Implementation complete!
    - Backend: 7 files
    - Frontend: 4 files
    - Tests: 6 files
    - Success criteria: 7/7 met
```

---

### `/spec-validate`

**Purpose:** Validate implementation against spec

**Process:**
1. Reads spec success criteria
2. Checks each criterion
3. Runs tests
4. Verifies quality (linting, types, patterns)
5. Generates report

**Usage:**
```
/spec-validate user-profile-management
/spec-validate user-profile-management --quick
```

**Output:**
- Detailed validation report
- Pass/fail for each criterion
- Test results
- Quality checks
- Recommendations

**Example:**
```
You: /spec-validate user-profile-management

AI: [Checks success criteria]
AI: [Runs tests]
AI: [Verifies quality]

AI: ✅ VALIDATION REPORT
    Success Criteria: 7/7 met
    Tests: All passing
    Code Quality: Excellent
    Security: Compliant

    Status: ✅ READY FOR REVIEW
```

---

## Tips for Success

### Writing Great Specs

✅ **Do:**
- Keep it short (1-2 pages)
- Focus on business context
- Point to similar code
- Make success criteria specific

❌ **Don't:**
- Write implementation steps
- Include code snippets
- Explain patterns (code does that)
- Try to be comprehensive

### Using Commands

✅ **Do:**
- Let AI explore and learn
- Trust pattern discovery
- Validate against criteria
- Iterate based on feedback

❌ **Don't:**
- Micromanage implementation
- Skip validation step
- Ignore failed criteria
- Forget to update spec with decisions

---

## Prerequisites and Boundaries

- Run from the repo root with dependencies installed; commands do not install or start infra.
- Keep the working tree clean or be aware of pending edits; specs are written to `spec/features/<slug>.md`.
- Provide clean integration points (tested, current patterns); avoid pointing at legacy/experimental code.
- Commands do not force lint/build/test; run them after `/spec-validate` when needed.
- If the plan/output looks wrong, adjust spec paths/success criteria and rerun instead of forcing manual fixes mid-command.

---

## Troubleshooting

### "Spec not found"
**Solution:** Make sure spec exists at `spec/features/[slug].md`

### "No integration points"
**Solution:** Add similar code paths to spec's Integration Points section

### "Validation failing"
**Solution:** Check which criteria failed and implement missing pieces

### "Pattern not followed"
**Solution:** Check if integration points in spec point to correct code

---

## Resources

- **Workflow Guide:** `docs/workflows/sdd-workflow.md`
- **Complete Example:** `docs/workflows/sdd-walkthrough-example.md`
- **Spec Templates:** `spec/templates/`
- **Spec README:** `spec/README.md`

---

## Questions?

**Q: Can I still use the old `/request` command?**
A: Yes, but it's deprecated. Migrate to `/spec-create` for better results.

**Q: What if there's no similar code to reference?**
A: Note that in the spec. AI will create a new pattern, which future features can reference.

**Q: How detailed should the spec be?**
A: Minimal! Just business context, success criteria, and pointers to code. 1-2 pages max.

**Q: Do I need to write tests manually?**
A: No, `/spec-implement` includes tests automatically based on success criteria.

**Q: Can I customize the implementation?**
A: Yes! The AI follows patterns but you can guide it or refine afterwards.

---

**Version:** 1.0.0 (New SDD)
**Date:** 2026-01-03
**Status:** Current workflow

*These commands automate the new Spec-Driven Development workflow for maximum productivity.*
