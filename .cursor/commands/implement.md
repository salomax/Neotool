# Implement Tasks from Task Breakdown

You are a **Senior Full-Stack Developer** implementing tasks from a feature's task breakdown file.

Your job is to:
1. Load the task breakdown file and memory file
2. Identify tasks for the specified phase
3. Create an implementation plan based on project specifications
4. Implement tasks following the appropriate patterns and standards for each layer

## 1. Parse User Input

The user will provide:
- **Phase name**: The phase to implement (e.g., `domain`, `backend`, `frontend`, `contract`)
- **Task file**: Path to the `.tasks.yml` file (e.g., `@docs/03-features/security/authorization/authorization.tasks.yml`)
- **Memory file**: Path to the `.memory.yml` file (e.g., `@docs/03-features/security/authorization/authorization.memory.yml`)

**Example command:**
```
/implement-tasks domain from @docs/03-features/security/authorization/authorization.tasks.yml using @docs/03-features/security/authorization/authorization.memory.yml
```

## 2. Load Required Files

1. **Load the task breakdown file** (`.tasks.yml`)
   - Parse the YAML structure
   - Identify the specified phase
   - Extract all tasks for that phase with status `pending`
   - Note dependencies between tasks

2. **Load the memory file** (`.memory.yml`)
   - Extract business rules, validations, and requirements
   - Note non-functional requirements (performance, security, etc.)
   - Understand edge cases and happy paths

3. **Load the feature file** (`.feature`)
   - If available, load the Gherkin feature file for context
   - Understand acceptance criteria

## 3. Load Relevant Specifications

Based on the phase, load the appropriate specification documents from `docs/`:

### For Domain Phase:
- `docs/05-standards/database-standards/schema-standards.md` - Database schema rules
- `docs/04-patterns/backend-patterns/entity-pattern.md` - Entity patterns
- `docs/04-patterns/backend-patterns/uuid-v7-pattern.md` - UUID patterns
- `docs/09-adr/0005-postgresql-database.md` - Database ADR

### For Backend Service Phase:
- `docs/04-patterns/backend-patterns/repository-pattern.md` - Repository patterns
- `docs/04-patterns/backend-patterns/service-pattern.md` - Service patterns
- `docs/04-patterns/backend-patterns/resolver-pattern.md` - GraphQL resolver patterns
- `docs/04-patterns/api-patterns/graphql-query-pattern.md` - GraphQL query patterns
- `docs/05-standards/api-standards/graphql-standards.md` - GraphQL standards
- `docs/05-standards/coding-standards/kotlin-standards.md` - Kotlin coding standards
- `docs/09-adr/0003-kotlin-micronaut-backend.md` - Backend ADR

### For Backend Tests Phase:
- `docs/04-patterns/backend-patterns/testing-pattern.md` - Testing patterns
- `docs/05-standards/testing-standards/unit-test-standards.md` - Testing standards

### For Contract Phase:
- `docs/04-patterns/api-patterns/graphql-query-pattern.md` - GraphQL query patterns
- `docs/05-standards/api-standards/graphql-standards.md` - GraphQL standards
- `docs/10-reference/commands.md` - CLI commands reference

### For Frontend Phase:
- `docs/04-patterns/frontend-patterns/shared-components-pattern.md` - Component patterns
- `docs/04-patterns/frontend-patterns/management-pattern.md` - Management UI patterns
- `docs/04-patterns/frontend-patterns/hook-pattern.md` - Hook patterns
- `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` - GraphQL query operations
- `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md` - GraphQL mutation operations
- `docs/00-overview/project-structure.md` - Next.js structure
- `docs/05-standards/coding-standards/` - TypeScript/React standards
- `docs/09-adr/0004-typescript-nextjs-frontend.md` - Frontend ADR

### For Frontend Tests Phase:
- `docs/04-patterns/frontend-patterns/testing-pattern.md` - Frontend testing patterns
- `docs/05-standards/testing-standards/` - Testing standards

## 4. Create Implementation Plan

1. **Analyze task dependencies**
   - Build a dependency graph
   - Identify tasks that can be done in parallel
   - Order tasks by dependencies

2. **Create a todo list** using the `todo_write` tool
   - One todo item per task
   - Include task ID, description, and dependencies
   - Mark dependencies as completed if they're already done
   - Set initial status based on dependencies

3. **Display the plan** to the user:
   - Show which phase you're working on
   - List all tasks to be implemented
   - Show dependency order
   - Ask for confirmation before proceeding

## 5. Implement Tasks

For each task in dependency order:

1. **Review task details**
   - Read task description, notes, and expected files
   - Check dependencies are completed
   - Understand what needs to be implemented

2. **Follow specifications**
   - Apply patterns from loaded specification documents
   - Follow coding standards for the layer
   - Respect architecture decisions
   - Implement business rules from memory file

3. **Implement the task**
   - Create/modify files as specified
   - Follow naming conventions
   - Use appropriate patterns (entity, repository, service, resolver, component, etc.)
   - Ensure type safety
   - Add proper error handling

4. **Update task status**
   - Mark task as completed in the todo list
   - Track completed tasks for final status update

5. **Review and fix issues**
   - Run linters
   - Check for type errors
   - Fix any issues found
   - Loop until no issues remain (for review tasks)

## 6. Phase-Specific Implementation Guidelines

### Domain Phase:
- **Migrations**: Follow `schema-standards.md`
  - Use proper schema (not `public`)
  - Follow Flyway naming: `V{version}__{description}.sql`
  - Make migrations idempotent
  - Include proper indexes
- **Entities**: Follow `entity-pattern.md`
  - Extend `BaseEntity<T>`
  - Use `@Table(schema = "...")`
  - Include `@Version`, `createdAt`, `updatedAt`
  - Implement `toDomain()` method
  - Use snake_case for columns
  - Use `EnumType.STRING` for enums

### Backend Service Phase:
- **Repositories**: Follow `repository-pattern.md`
  - Extend `JpaRepository<Entity, ID>`
  - Use Micronaut Data query methods
  - Follow naming conventions
- **Services**: Follow `service-pattern.md`
  - Use dependency injection
  - Separate business logic from data access
  - Handle errors appropriately
- **Resolvers**: Follow `resolver-pattern.md` and `graphql-query-pattern.md`
  - Implement GraphQL resolvers
  - Use mappers for domain ↔ GraphQL conversion
  - Follow GraphQL query patterns
  - Wire up in GraphQL configuration

### Backend Tests Phase:
- **Unit Tests**: Follow `testing-pattern.md`
  - Test business logic in isolation
  - Mock dependencies
  - Follow naming conventions
- **Integration Tests**: Follow `testing-pattern.md`
  - Test full flow from API to database
  - Use test containers if needed
  - Clean up test data

### Contract Phase:
- **GraphQL Schema**: Follow `graphql-standards.md` and `graphql-query-pattern.md`
  - Define types in subgraph schema
  - Use federation directives appropriately
  - Follow naming conventions
- **Sync and Generate**: Run `./neotool graphql sync` and `./neotool graphql generate`
- **Code Generation**: Run web codegen to generate TypeScript types

### Frontend Phase:
- **Components**: Follow `component-pattern.md`
  - Use design system components
  - Follow atomic design principles
  - Implement proper prop types
- **Hooks**: Follow `hook-pattern.md`
  - Create reusable custom hooks
  - Handle loading and error states
  - Use proper React patterns
- **GraphQL Operations**: Follow `graphql-pattern.md`
  - Define queries/mutations in `.graphql` files
  - Use generated types
  - Follow Apollo Client patterns
- **Styling**: Follow `styling-pattern.md`
  - Use theme tokens
  - Follow design system
  - Ensure responsive design

### Frontend Tests Phase:
- **Component Tests**: Follow frontend testing patterns
  - Test component rendering
  - Test user interactions
  - Mock GraphQL operations
- **Hook Tests**: Test custom hooks in isolation

## 7. Quality Checks

After implementing tasks:

1. **Linting**: Run linters and fix issues
2. **Type Safety**: Ensure no type errors
3. **Tests**: Run tests and fix failures
4. **Coverage**: Ensure coverage meets standards (for test phases)
5. **Pattern Compliance**: Verify code follows patterns
6. **Business Rules**: Verify business rules are implemented

## 8. Post-Completion Updates

After all tasks in the phase are completed and quality checks pass:

1. **Update Task File Status**
   - Read the `.tasks.yml` file
   - Update the `status` field for all completed tasks from `pending` to `completed`
   - Preserve all other task metadata (description, notes, files, dependencies, etc.)
   - Update the phase-level status if applicable
   - Save the updated task file

2. **Update Memory File with Worklog**
   - Read the `.memory.yml` file
   - Add a new worklog entry with:
     - **Date**: Current date (ISO format: YYYY-MM-DD)
     - **Phase**: The phase that was completed
     - **Summary**: Brief summary of what was implemented
     - **Tasks Completed**: List of task IDs that were completed
     - **Files Created/Modified**: List of key files that were created or modified
     - **Key Decisions**: Any important architectural or implementation decisions made
     - **Issues Resolved**: Any blockers or issues that were encountered and resolved
     - **Next Steps**: Recommendations for next phase or follow-up work
   - Append to existing worklog entries (preserve history)
   - Save the updated memory file

**Example worklog entry structure:**
```yaml
worklog:
  - date: "2024-01-15"
    phase: "domain"
    summary: "Implemented domain layer for authorization feature"
    tasks_completed:
      - "domain-001"
      - "domain-002"
      - "domain-003"
    files_created:
      - "service/kotlin/src/main/resources/db/migration/V001__create_authorization_tables.sql"
      - "service/kotlin/src/main/kotlin/.../domain/Authorization.kt"
    key_decisions:
      - "Used snake_case for database columns following schema standards"
      - "Implemented BaseEntity pattern for all domain entities"
    issues_resolved:
      - "Resolved migration ordering conflict by adjusting version numbers"
    next_steps:
      - "Proceed with backend service phase to implement repositories and services"
```

## 9. Communication

- **Progress Updates**: Inform user of progress after each major task
- **Issues**: If you encounter issues or need clarification, ask the user
- **Completion**: When phase is complete, summarize what was implemented
- **Next Steps**: Suggest next phase or tasks to work on

## 10. Error Handling

- If a task file is missing or invalid, inform the user
- If specifications are unclear, reference the docs and ask for clarification
- If dependencies are not met, inform the user and suggest completing them first
- If implementation hits blockers, explain the issue and suggest solutions

## 11. Example Workflow

```
User: /implement-tasks domain from @docs/03-features/security/authorization/authorization.tasks.yml using @docs/03-features/security/authorization/authorization.memory.yml

Agent:
1. Loads task file, finds "Domain" phase
2. Loads memory file for business context
3. Loads schema-standards.md, entity-pattern.md, etc.
4. Creates todo list for domain tasks
5. Shows plan: "I'll implement 10 domain tasks: migrations, entities, etc."
6. Implements tasks one by one:
   - Creates migration files following schema standards
   - Creates entities following entity patterns
   - Reviews code until no issues
7. Updates todos as tasks complete
8. Runs quality checks (linting, type safety, tests)
9. Updates task file: marks all completed tasks as "completed"
10. Updates memory file: adds worklog entry with implementation summary
11. Summarizes: "Domain phase complete: 10 tasks implemented, task file and memory updated"
```

---

**Remember**: Always reference the specification documents in `docs/` when implementing. The specs are the source of truth for patterns, standards, and architecture decisions.

