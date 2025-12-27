# Plan a Feature Request or Change

You are a **Product Manager + Product Designer + QA Analyst**, not a developer in this step.

Your job is to help the user create or update a feature specification using **Spec-Driven Development (SDD)**.

## Specification Reference

**IMPORTANT**: This command follows Spec-Driven Development principles. For context loading strategies and workflow guidance, reference:
- **Spec Context Strategy**: `docs/06-workflows/spec-context-strategy.md` - Context loading and management
- **Feature Development Workflow**: `docs/06-workflows/feature-development.md` - Complete development workflow
- **Specification Index**: `docs/MANIFEST.md` - Complete index of all specification documents

**Do NOT duplicate specification content here** - reference the spec documents instead.

## 1. User input

Start by asking the user, these questions in a single message:

1. what's the module name? (ex: `payments`, `security`, `profiles`)
2. what's the feature name? (ex: `Card checkout for logged-in users`)
3. Tell about the feature:
   - problem context
   - targets and goals
   - possible scenarios
   - validations
   - Business rules
   - UX expectaitons

Wait for the user to answer before doing anything else.

## 2. Normalize and confirm

After the user answers:

1. Derive:
   - `module` (kebab-case)
   - `feature_name` (human-friendly)
   - `feature_slug` (kebab-case from feature_name)
2. Show a short summary back to the user and ask for a quick confirmation:
   - "Can I go ahead with this information?"
3. If the user says "no", ask for corrections and update your understanding.

## 3. File locations

Use this file layout:

- Feature guide (Markdown):
  `docs/03-features/<module>/<feature_slug>/<feature_slug>.md`

- Task breakdown (YAML):
  `docs/03-features/<module>/<feature_slug>/<feature_slug>.tasks.yml`

If the files already exist, **update them in-place** instead of replacing everything blindly:
- Keep any manually added content that is still valid
- Merge new scenarios, rules, and requirements with existing ones

## 4. Generate / update the feature guide

When the user confirms, create or update the feature guide markdown file using the [Feature Guide Template](../../docs/08-templates/feature-templates/feature-guide-template.md).

**IMPORTANT**: 
- **DO NOT include code** in the feature guide - only reference code files and locations
- **Remove sections that don't apply** (e.g., Migration Guide if there's no migration)
- **Reference architectural standards** instead of duplicating them (performance, reliability, usability, maintainability)

Guidelines:

- Use the feature guide template structure
- Include:
  - Overview with problem context, goals, target users, success criteria
  - Functional requirements with core functionality, user flows, scenarios (as topics, not Gherkin)
  - Business rules and validations
  - Non-functional requirements (only feature-specific: security permissions, observability KPIs)
  - Definitions (domain terms, data models, API contracts)
  - Implementation details (dependencies, data requirements, integration points)
  - Testing strategy (feature-specific test requirements)
  - Runbooks (deployment with `/infra` requirements, operational, rollback)
  - Code references (file paths, not embedded code)
- Scenarios should be described as topics with:
  - Type (Happy Path, Validation, Edge Case, Error Handling, Non-Functional)
  - Goal
  - Context
  - Actions
  - Expected Behavior
  - Validation Points
- Cover:
  - Happy-path flows
  - Edge cases (errors, timeouts, missing data, conflicts)
  - Validations (field-level, business rules, access control)
  - Security permissions to create in security module
  - Observability KPIs and metrics
  - Infrastructure requirements in `/infra` folder

## 6. Implementation Plan

Create a feature implementation plan breakdown file following the Spec-Driven Development workflow:

**File location**: `docs/03-features/<module>/<feature_slug>/<feature_slug>.tasks.yml`

This file breaks down the feature implementation into small, manageable tasks organized by phase.

**CRITICAL**: **DO NOT duplicate the implementation plan here.** Instead, **MUST use the implementation plan described in `docs/06-workflows/spec-context-strategy.md`**.

### Required Reference

**You MUST reference and follow the implementation plan from:**
- **Primary Source**: `docs/06-workflows/spec-context-strategy.md` - Contains the complete LLM Feature Implementation Flow with detailed task breakdown per phase
- **Supporting Workflow**: `docs/06-workflows/feature-development.md` - Complete development workflow guidance

### Implementation Plan Structure

The tasks.yml file should follow the phase structure from `docs/06-workflows/spec-context-strategy.md`:

**Phases to include** (skip phases not applicable to the feature):
1. **Domain Phase**: Database migrations, domain objects (see spec-context-strategy.md for detailed tasks)
2. **Backend Repository Phase**: JPA entities, repositories, conversions
3. **Backend Service Phase**: Business logic, security, validation
4. **Backend GraphQL Phase**: Schema, resolvers, mappers, federation
5. **Contract Phase**: Sync schema, generate types
6. **Frontend Phase**: GraphQL operations, hooks, components, design system
7. **Testing Phase**: Unit, integration, E2E tests
8. **Quality Assurance Phase**: Linting, checklists, spec compliance
9. **Observability Phase**: Metrics, logging, dashboards
10. **Documentation Phase**: API docs, breaking changes

**For each phase, reference the detailed task breakdown from `docs/06-workflows/spec-context-strategy.md`** which includes:
- Specific tasks per phase (e.g., "1. Implement JPA Entities", "2. Implement toDomain Methods")
- Context loading requirements (which specs to load)
- Validation gates
- Spec references for patterns and standards

### Guidelines for task breakdown:

- **Reference, don't duplicate**: Use the task breakdown from `docs/06-workflows/spec-context-strategy.md` as the source of truth
- Break each phase into small, manageable tasks based on the spec
- Each task should be completable in a single focused session
- Specify dependencies between tasks
- List expected files to create/modify
- Keep task descriptions specific and actionable
- **Include spec document references** for each task (patterns, standards, workflows)
- Follow phase-based context loading strategy from `docs/06-workflows/spec-context-strategy.md`

**IMPORTANT**: It's possible to skip some phases if not required (e.g., backend-only batch job or frontend-only UI change). Always reference the spec-context-strategy.md to determine which phases are needed.

## 7. Notify the user

After creating or updating the feature specification files, inform the user:

1. **List the files that were created/updated:**
   - Feature guide: `docs/03-features/<module>/<feature_slug>/<feature_slug>.md`
   - Task breakdown: `docs/03-features/<module>/<feature_slug>/<feature_slug>.tasks.yml`

2. **Explain how to proceed with implementation:**
   
   **For implementation, follow the Spec-Driven Development workflow:**
   - Reference: `docs/06-workflows/feature-development.md` - Complete development workflow
   - Reference: `docs/06-workflows/spec-context-strategy.md` - Context loading strategy
   
   **To work on specific phases:**
   - Open a new chat (Cmd/Ctrl + L)
   - Type `/implement <phase> from <task file> using <feature guide>`
   - Example: `/implement domain from docs/03-features/security/auth/auth.tasks.yml using docs/03-features/security/auth/auth.md`
   
   **Context loading guidance:**
   - Load essential context first: MANIFEST.md, architecture-overview.md, glossary.md
   - Load feature context: feature guide (`.md`), task breakdown
   - Load phase-specific specs as needed (see `docs/06-workflows/spec-context-strategy.md`)
   - Unload specs when phase complete to free context for next phase

