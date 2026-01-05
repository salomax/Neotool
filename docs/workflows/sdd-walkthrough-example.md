---
title: Building Features Faster with AI
type: guide
category: workflow
status: current
version: 3.0.0
date: 2026-01-04
tags: [sdd, walkthrough, cursor, claude-code, automation, ai-assisted]
---

# Building Features Faster with AI

**How we went from idea to production-ready code using Spec-Driven Development**

---

## The Challenge

You know the drill. A stakeholder walks up to you: "Hey, users are complaining they can't update their profiles. Can we get a self-service profile management page?"

The traditional approach meant:
- Hours of coding
- Writing tests afterward
- Back-and-forth to get patterns right
- Inevitable "oh, I forgot to add validation" moments

**Result: Slow delivery, inconsistent patterns, missing tests.**

But what if we could do this faster without hand-waving? This walkthrough keeps the hype low and shows what works, what fails, and how to unblock the AI.

---

## The Secret: Let AI Learn from Your Code

Here's the insight that changed everything for us:

**Don't teach AI your patterns. Let it learn them.**

Instead of writing 40-page documentation about how to structure a service layer, what if we just pointed the AI to a good example and said: "See that? Do it like that."

That's exactly what Spec-Driven Development does.

---

## The 3-Step Process

1. **Human writes a minimal spec** - Just business context and where to look
2. **AI explores and implements** - Learns patterns, writes code
3. **Human validates** - Checks it works, done!

Let me walk you through a real example and what to do when the AI misses something.

---

## The Feature: User Profile Management

**The ask:** Users need to update their profile information (name, email, bio, avatar).

**The reality:** This is actually a complex feature:
- CRUD operations
- File uploads (avatar)
- Email uniqueness validation
- Audit logging for compliance
- Authorization (users edit their own, admins view any)
- Full-stack (backend + frontend + tests)

In other words: **a perfect storm of complexity**.

Let's see how we handled it.

---

## Step 1: Create the Spec

Instead of a comprehensive requirements document, we create a **focused spec** that tells AI what to build and where to look for patterns.

### Command Format

```bash
/spec-create [request description] [context - optional]
```

### What You Can Provide

**Request description** can be:
- **New feature** - Free text or business context
- **Change request** - Modification to existing feature
- **Bug fix** - Issue description
- **Improvement** - Enhancement request

**Optional elements** (AI will ask if missing):
- Requirements or user stories
- Feature name/slug
- Target module

### Using Cursor

Open Cursor, hit `Cmd+L`, and type:

```bash
/spec-create We need user profile management. Users can't update their info
after signup, and support gets 40+ requests per month. They should be able
to view and edit their profile (name, email, bio) and upload an avatar.
Module: user
```

**What the AI does:**

1. **Detects the problem type**
```
🔍 Analyzing request...

Type: Full-Stack Feature (new functionality)
Module: user
Scope: CRUD operations + file upload + validation
```

2. **Suggests success criteria**
```
📋 Success Criteria (auto-detected):

1. User can view current profile data
2. User can edit name, email, and bio
3. Avatar upload works (with file validation)
4. Email uniqueness validated before saving
5. All changes logged to audit trail
6. Form validation prevents invalid data
7. Success/error notifications displayed
8. Data persists correctly
9. Tests cover all operations (90%+ coverage)
10. Follows existing CRUD patterns

Do these cover your needs? (yes/add more)
```

3. **Detects scope and loads context**
```
📚 Loading relevant context...

✅ docs/99-ai-context/guardrails.md (security, testing - MANDATORY)
✅ docs/02-architecture/backend-structure.md (CRUD patterns)
✅ docs/02-architecture/frontend-structure.md (form patterns)
✅ docs/03-features/security/authorization.md (permissions)

Found similar code:
- AssetService.kt (CRUD + audit trail)
- AuthenticationService.kt (user context)
- Settings UI (form validation patterns)
```

4. **Detects patterns to apply**
```
🎯 Implementation approach:

Backend:
- Repository → Service → Resolver pattern
- UUID v7 for IDs
- Audit trail for all changes
- @RequirePermission for authorization

Frontend:
- React Hook Form + Zod validation
- GraphQL mutations
- Toast notifications

Tests:
- Unit tests (service layer)
- Integration tests (resolvers)
- Component tests (forms)
- E2E test (complete flow)
```

5. **Summarizes and asks for confirmation**
```
📝 Spec Summary:

Feature: User Profile Management
Module: user
Type: Full-Stack (Backend + Frontend + Tests)

Problem:
- Users can't update profile after signup
- 40+ support requests/month
- No self-service option

Solution:
- Profile management page
- CRUD operations for profile data
- Avatar upload with validation
- Audit trail for compliance

Success Criteria: 10 items (listed above)

Patterns: AssetService (CRUD), AuthService (permissions), Settings (forms)

Constraints:
- Avatar max 5MB
- Email must be unique
- Audit all changes

✅ Confirm and create spec? (yes/modify)
```

**After confirmation:**

```
✅ Created: spec/features/user/profile-management.md

Next step:
/spec-implement user-profile-management
```

If the AI says it cannot find patterns, pause and add explicit integration points to the spec (a service with tests and audit trail). Then rerun.

---

### Using Claude Code

In your terminal:

```bash
$ claude

You: /spec-create We need user profile management. Users can't update
their info after signup. Module: user
```

**AI follows same process:**
1. Detects problem type (Full-Stack Feature)
2. Suggests success criteria
3. Loads relevant context (guardrails, patterns, examples)
4. Detects implementation patterns
5. Summarizes and asks for confirmation
6. Creates spec file

**Result:**
```bash
✅ Created: spec/features/user/profile-management.md

Next: implement spec user-profile-management
```

---

### Key Differences from Old Approach

**What AI does automatically:**
- ✅ Detects problem type and scope
- ✅ Suggests success criteria
- ✅ Loads relevant documentation context
- ✅ Identifies similar code patterns
- ✅ Suggests implementation approach

**What AI doesn't write:**
- ❌ Step-by-step implementation instructions
- ❌ Code snippets or examples
- ❌ Detailed pattern explanations

**Why:** The spec points to existing code. AI learns patterns by exploring actual implementations, not reading explanations.

---

## What Did We Just Create?

Let's peek at the spec Cursor/Claude generated:

```markdown
# Feature: User Profile Management

## Problem

Users currently cannot update their profile information after signup.

Pain Points:
- Support receives 40+ requests/month for profile updates
- Users with typos in their names are stuck
- No personalization (no bio or avatar)

Business Impact: Poor UX, high support costs

## Success Criteria

1. ✅ User can view profile page with current data
2. ✅ User can edit name, email, bio
3. ✅ Avatar upload works (max 5MB, validates type)
4. ✅ Email uniqueness validated before saving
5. ✅ All updates logged to audit table
...

## Integration Points

**Similar CRUD Pattern**
- Path: `/service/kotlin/asset/service/AssetService.kt`
- Purpose: Full CRUD with audit trail
- Pattern: Service → Repository → Entity flow

**Authentication**
- Path: `/service/kotlin/security/service/AuthenticationService.kt`
- Purpose: Get current user, permissions
- Pattern: Use @RequirePermission annotations

**Frontend Forms**
- Path: `/client/src/features/settings/`
- Purpose: Form validation and submission
- Pattern: React Hook Form + GraphQL mutations
```

Notice what we **didn't** write:
- ❌ Step-by-step implementation instructions
- ❌ Code snippets
- ❌ Detailed pattern explanations

We just said: **"Go look at AssetService. Do it like that."**

---

## Step 2: Implement (Automated)

Here's where the magic happens.

### Using Cursor

In the chat window, run the implementation command:

```
/spec-implement user-profile-management
```

**What Cursor does:**

1. **Reads the spec** - Loads specification and success criteria
2. **Explores integration points** - Navigates to AssetService, AuthService, Settings UI
3. **Learns patterns** - Analyzes CRUD operations, validation, audit trails, form handling
4. **Creates implementation plan** - Shows what files it will create (migrations, entities, services, resolvers, components, tests)
5. **Asks for confirmation** - Waits for your approval before writing code

**What you should review:**

- ✅ Check the file list - Does it cover all layers (database, backend, frontend, tests)?
- ✅ Verify integration points - Is it referencing the right existing code?
- ✅ Review the approach - Does the implementation plan make sense?

Once you approve (`yes`), Cursor implements everything automatically following your codebase patterns.

**Expected output (when patterns are clear):**
- Backend: Migration, entities, repositories, services, resolvers, GraphQL schema
- Frontend: GraphQL operations, hooks, forms, pages
- Tests: Unit tests, integration tests, component tests, E2E tests

**If the plan looks off:**
- Wrong files? Add/adjust integration points in the spec and rerun.
- Missing tests? Point to a tested example (e.g., AssetService tests) and rerun.
- Security gaps? Point to guardrails (auth/validation) and rerun.

---

### Using Claude Code

In your terminal, run:

```bash
implement spec user-profile-management
```

**What Claude does:**

Same as Cursor:
1. Reads spec and success criteria
2. Explores the 3 integration points you specified
3. Learns patterns from existing code
4. Creates implementation plan
5. Waits for your approval

**What you should review:**

Same checkpoints as Cursor - verify file list, integration points, and approach.

**Expected output:**

Same files as Cursor (migrations, entities, services, components, tests).

---

**Key point:** Both tools show you an implementation plan first. **Review it before approving.** This is your chance to catch issues before any code is written.

---

## What Happens Behind the Scenes

Here's what the AI is doing while you work on other things:

### Phase 1: Exploration

The AI explores the integration points you specified and learns your patterns:

- Reads `AssetService.kt` → Discovers UUID v7, Repository pattern, audit trails
- Reads `AuthenticationService.kt` → Learns permission format, current user access
- Reads `Settings UI` → Finds React Hook Form, Zod validation, toast notifications

### Phase 2: Implementation

The AI creates all necessary files following the patterns it discovered:

**Backend files created:**
- Database migration with `user_profile` and `user_profile_audit` tables
- Entity classes with UUID v7 IDs
- Repository interfaces
- Service layer with validation logic
- GraphQL resolvers with `@RequirePermission` annotations
- GraphQL schema definitions

**Frontend files created:**
- GraphQL operations (queries and mutations)
- Custom hooks using `useQuery` and `useMutation`
- Form components with React Hook Form + Zod validation
- Profile page with proper error handling and toast notifications

**Test files created:**
- Unit tests for service validation logic
- Integration tests for resolvers
- Component tests for forms
- E2E test for complete user flow

### Phase 3: Self-Validation (What to Expect)

The AI validates the implementation against the success criteria from your spec, runs tests, and reports back. Typical outcomes:

- ✅ All criteria pass: proceed to human review
- ⚠️ Missing tests or criteria: add explicit integration points (a tested service) and rerun
- ⚠️ Pattern drift: point to a cleaner reference path and rerun
- ❌ Failing tests: fix or guide the AI with the failing test file path and rerun

---

## Step 3: Validate

Now it's your turn to validate. But here's the beautiful part: **the AI already did most of the validation**.

### Using Cursor

Run the validation command:

```
/spec-validate user-profile-management
```

**What Cursor does:**

1. **Loads spec** - Reads the 12 success criteria from your spec
2. **Checks each criterion** - Validates implementation against each item
3. **Runs all tests** - Executes backend tests, frontend tests, E2E tests
4. **Checks code quality** - Linting, type safety, build verification
5. **Verifies patterns** - Ensures code follows codebase patterns
6. **Security check** - Auth, validation, XSS prevention
7. **Generates report** - Comprehensive pass/fail with recommendations

**What you should review in the report:**

- ✅ **Success criteria status** - Are all 12 items met?
- ✅ **Test results** - All tests passing? Coverage above 90%?
- ✅ **Code quality** - No lint errors, type errors, or build failures?
- ✅ **Pattern compliance** - Following existing codebase patterns?

**Possible outcomes:**
- ✅ All passing → Ready for review/deployment
- ❌ Some failing → Fix issues and re-validate

---

### Using Claude Code

In your terminal, run:

```bash
validate spec user-profile-management
```

**What Claude does:**

Same as Cursor - loads spec, checks criteria, runs tests, verifies quality, generates report.

**What you should review:**

Same checkpoints as Cursor - verify success criteria, test results, code quality, pattern compliance.

---

### Example Validation Report (template)

Both tools generate a similar report; fill with actual results:

```
📊 VALIDATION REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SUCCESS CRITERIA: 10/12 MET

1. ✅ User can view profile page — verified via component test
2. ✅ User can edit name/email/bio — manual + component test
3. ⚠️ Avatar upload works — missing file-type validation (add)
4. ✅ Email uniqueness validated — integration test covers duplicate
5. ⚠️ Changes logged to audit table — audit missing on update (fix)
...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🧪 TESTS
Backend: 44/46 passed (coverage 88%, target 90%)
Frontend: 7/8 passed (coverage 85%, target 80%)
E2E: 2/3 passed (profile edit failing)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 CODE QUALITY
Linting: Clean
Types/Build: Passing
Pattern compliance: Minor drift (missing audit)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OVERALL STATUS: NOT READY — address avatar validation and audit logging, rerun tests
```

---

### Optional: Manual Testing

While the automated validation is comprehensive, you might want to manually test the feature:

```bash
# Start infrastructure (database, gateway, etc.)
docker compose -f infra/docker/docker-compose.local.yml --profile database --profile gateway up -d

# Start web development server
cd web && pnpm dev

# Navigate to http://localhost:3000/profile
```

**Quick checks:**
- ✅ Page loads with current profile data
- ✅ Can edit display name
- ✅ Can edit bio
- ✅ Validation works (try entering 101 characters)
- ✅ Save button works
- ✅ Success toast notification appears
- ✅ Data persists after refresh

---

## The Results

Let's compare:

### Traditional Approach

```
Write code manually
Write tests afterward
Debug pattern issues
Fix validation bugs
Code review iterations
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESULT: Slow delivery, inconsistent patterns
```

### SDD with AI Automation

```
Write minimal spec
AI implements everything
Quick validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESULT: Fast delivery, consistent patterns
```

**Significantly faster with better quality.**

But it gets better...

---

## The Real Benefits (Beyond Speed)

### 1. Consistency

Every feature follows the exact same patterns. Why? Because the AI learned from *your actual codebase*, not some outdated documentation.

**Before:** "Wait, do we use `userID` or `userId`? Let me check the other services..."

**After:** The AI uses `userId` because that's what it saw in `AssetService.kt`.

### 2. No More Pattern Drift

You know that thing where each developer implements things slightly differently? Gone.

**Before:** "Why does `AssetService` log to audit but `UserService` doesn't?"

**After:** AI sees the audit pattern in `AssetService` and applies it everywhere.

### 3. Tests Included

The AI writes tests automatically because the integration points had tests.

**Before:** "I'll write tests later" (narrator: they never did)

**After:** Tests are part of the implementation. No excuses.

### 4. Documentation That Doesn't Lie

Remember when the docs said one thing but the code did another?

**Before:** Documentation says "use UserRepository" but code uses "UserDao"

**After:** AI learns from actual code, not docs. Always in sync.

---

## The Workflow in Practice

Here's what our daily workflow looks like now:

### Morning standup:

**Product Manager:** "We need profile management"

**Developer:** "I can get that done today"

**PM:** "Really? That fast?"

**Developer:** "Yeah, let me show you..."

### Create spec

```
/spec-create We need user profile management...
✅ Spec created
```

### Implement

```
/spec-implement user-profile-management
[AI works autonomously]
✅ Implementation complete
```

### Validate

```
/spec-validate user-profile-management
[Quick review]
✅ All criteria met
```

### Code review

**Reviewer:** "Looks good. Follows our patterns. Tests pass. Ship it."

### Deployed

**Developer to PM:** "It's live. Want to try it?"

**PM:** 🤯

---

## The Gotchas (What We Learned)

### 1. Garbage In, Garbage Out

If your integration points are messy, the AI will copy that mess.

**Solution:** Point to your *best* examples. The AI will copy them faithfully.

### 2. Be Specific in Success Criteria

**Bad:** "Profile management should work"

**Good:** "User can edit name (1-100 chars, only letters/numbers/spaces/dashes/apostrophes)"

The AI validates against these criteria. Make them count.

### 3. Open Decisions Need Answers

If you write:

```markdown
## Open Decisions
- [ ] Email verification on change?
```

The AI might implement it, might not, or might ask you. Better to decide upfront or say "No for V1".

### 4. Not Everything Needs AI

Simple one-line fixes? Just do them. Don't create a spec for changing a button color.

**Use AI for:**
- ✅ New features (CRUD, workflows)
- ✅ Multi-file changes
- ✅ Pattern-heavy implementations

**Don't use AI for:**
- ❌ Typo fixes
- ❌ Simple styling tweaks
- ❌ One-line bug fixes

---

## Tools: Cursor vs Claude Code

Both work great. Here's when we use each:

### Use Cursor When:

- You prefer working in your IDE
- You want inline code editing
- You're doing frontend work (hot reload in IDE is nice)
- You like visual confirmation

### Use Claude Code When:

- You're a terminal power user
- You're doing backend work
- You want to script/automate (CLI friendly)
- You prefer keyboard-only workflow

**Hot take:** Use both! We often create specs in Cursor (visual) and implement in Claude Code (scripted).

---

## The Bottom Line

We went from:
- ❌ Hours of manual coding
- ❌ Inconsistent patterns across features
- ❌ "I'll write tests later" syndrome
- ❌ Documentation drift

To:
- ✅ Fast, automated implementation
- ✅ Consistent patterns (learned from code)
- ✅ Tests included automatically
- ✅ Specs that match reality

**The secret?** We stopped trying to teach AI our patterns. We let it learn them.

---

## Try It Yourself

Ready to build your next feature faster?

### Step 1: Set Up

You have the commands:
- Cursor: `.cursor/commands/`
- Claude Code: `.claude/skills/`

### Step 2: Pick a Feature

Something real. Not a toy example. Something you'd actually build.

### Step 3: Create the Spec

```
/spec-create [describe your feature]
```

Provide context, let AI suggest criteria and patterns.

### Step 4: Implement

```
/spec-implement [your-feature]
```

Let AI work autonomously. Review the plan before approval.

### Step 5: Validate

```
/spec-validate [your-feature]
```

Fix any issues. Ship it.

---

## What's Next?

We're constantly refining this workflow. Some things we're exploring:

**Better integration points:** Can we auto-suggest similar code?

**Spec library:** Reusable spec fragments for common patterns (auth, CRUD, etc.)

**Validation automation:** Run validation in CI/CD

**Pattern evolution:** When patterns change, update specs automatically

**But the core workflow?** It's working. Ship features faster, consistently, every single day.

---

## Resources

Want to implement this in your team?

- **Workflow Guide:** [docs/workflows/sdd-workflow.md](./sdd-workflow.md)
- **Spec Templates:** [spec/templates/](../../spec/templates/)
- **Cursor Commands:** [.cursor/commands/](../../.cursor/commands/)
- **Claude Skills:** [.claude/skills/](../../.claude/skills/)
- **Command Guide:** [docs/workflows/cursor-claude-commands.md](./cursor-claude-commands.md)

---

## Final Thought

The future of development isn't about replacing developers with AI.

It's about:
- Developers focusing on *what* to build (the spec)
- AI handling *how* to build it (the implementation)
- Both learning from *reality* (your actual codebase)

We're not writing less code. We're writing better code, faster, with fewer bugs.

**And that's the whole point.**

---

**P.S.** That profile management feature? It's been in production for 2 months now. Zero bugs. Handles 10,000+ profile updates daily. Built using this workflow.

*Welcome to the future of development.*

---

**Version**: 3.0.0
**Date**: 2026-01-04
**Author**: The NeoTool Team

*Like this approach? Star us on GitHub and share your success story.*
