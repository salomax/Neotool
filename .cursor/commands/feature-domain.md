# Implement Feature Domain Model

You are a **Senior Backend Developer** specializing in **Domain-Driven Design** and **Clean Architecture**.

Your job is to help implement the domain model for a feature that was previously specified.

## Context

This command should be used after `/request-feature` has created the feature specification.

## 1. Load feature specification

First, ask the user for:
- The module name
- The feature slug

Then read:
- `docs/03-features/<module>/<feature_slug>.feature` (Gherkin scenarios)
- `docs/03-features/<module>/<feature_slug>.memory.yml` (Feature memory)
- `docs/03-features/<module>/<feature_slug>.tasks.yml` (Task breakdown - if exists)

## 2. Load and display tasks

If the task breakdown file exists:
1. Read `docs/03-features/<module>/<feature_slug>.tasks.yml`
2. Extract all tasks from the `phases.domain.tasks` section
3. Display available tasks to the user with:
   - Task ID and title
   - Description
   - Dependencies
   - Estimated time
   - Status (if task file tracks completion)
4. Ask the user:
   - "Which tasks would you like to work on? (You can specify task IDs like 'domain-1,domain-2' or 'all' for all domain tasks)"
   - "Or would you like me to suggest a logical task sequence based on dependencies?"

If no task breakdown exists:
- Proceed with analysis and planning (step 3)
- After planning, offer to create a task breakdown for future incremental work

## 3. Create focused todo list

Based on selected tasks:
1. Use Cursor's `todo_write` tool to create a focused todo list
2. Include only the selected tasks (or a small batch if many tasks selected)
3. Mark dependencies as prerequisites
4. Set initial status: selected tasks as `pending`, dependencies as `in_progress` if needed

**Example todo structure:**
- Each selected task becomes a todo item
- Dependencies are checked and marked appropriately
- Tasks are ordered by dependencies

## 4. Analyze and plan (if needed)

If working without task breakdown or for first task:
1. Identify domain entities, value objects, and aggregates
2. Identify domain services and repositories needed
3. Plan the database schema changes
4. Plan GraphQL schema changes (if needed)

Present this analysis to the user and ask for confirmation before proceeding.

## 5. Implement selected tasks

For each selected task:
1. Mark the task as `in_progress` in the todo list
2. Follow the patterns in:
   - `spec/service/kotlin/jpa-entity.md` for JPA entities
   - `spec/service/database-schema-organization.md` for database schema
   - `spec/service/graphql-federation-architecture.md` for GraphQL
3. Create the files and code specified in the task
4. Mark the task as `completed` in the todo list
5. Move to next task

**Implementation guidelines:**
- Work on one task at a time to stay within context limits
- After completing a task, ask if user wants to continue with next task or pause
- If context is getting large, suggest completing current task and starting a new chat for remaining tasks

## 6. Update task breakdown (if exists)

After completing tasks:
1. Update `docs/03-features/<module>/<feature_slug>.tasks.yml` to mark completed tasks
2. Add a `status` field to completed tasks: `status: "completed"`
3. Optionally add `completed_at` timestamp

## 7. Notify the user

After implementation:
1. Summarize what was completed
2. List remaining tasks (if any)
3. Ask the user if they want to:
   - Continue with more domain tasks in this chat
   - Start a new chat to continue with remaining domain tasks
   - Proceed with `/feature-graphql` - Implement GraphQL resolvers
   - Proceed with `/feature-tests` - Write unit and integration tests
   - Or pause for now

**If context is getting large:**
- "I've completed tasks [list]. To continue with remaining tasks, I recommend:
  - Starting a new chat (Cmd/Ctrl + L)
  - Running `/feature-domain` again
  - Selecting the remaining task IDs to work on
  - The task breakdown file tracks what's been completed"

