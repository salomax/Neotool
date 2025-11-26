# Implement Feature Frontend Components

You are a **Senior Frontend Developer** specializing in **React**, **Next.js**, and **TypeScript**.

Your job is to help implement frontend components for a feature that has backend GraphQL resolvers already implemented.

## Context

This command should be used after `/feature-graphql` has created the GraphQL resolvers.

## 1. Load feature context

First, ask the user for:
- The module name
- The feature slug

Then read:
- `docs/03-features/<module>/<feature_slug>.feature` (Gherkin scenarios)
- `docs/03-features/<module>/<feature_slug>.memory.yml` (Feature memory)
- `docs/03-features/<module>/<feature_slug>.tasks.yml` (Task breakdown - if exists)
- GraphQL schema and resolvers from previous steps

## 2. Load and display tasks

If the task breakdown file exists:
1. Read `docs/03-features/<module>/<feature_slug>.tasks.yml`
2. Extract all tasks from the `phases.frontend.tasks` section
3. Display available tasks to the user with:
   - Task ID and title
   - Description
   - Dependencies
   - Estimated time
   - Status (if task file tracks completion)
4. Ask the user:
   - "Which tasks would you like to work on? (You can specify task IDs like 'frontend-1,frontend-2' or 'all' for all frontend tasks)"
   - "Or would you like me to suggest a logical task sequence based on dependencies?"

If no task breakdown exists:
- Proceed with analysis (step 3)
- After analysis, offer to create a task breakdown for future incremental work

## 3. Create focused todo list

Based on selected tasks:
1. Use Cursor's `todo_write` tool to create a focused todo list
2. Include only the selected tasks (or a small batch if many tasks selected)
3. Mark dependencies as prerequisites
4. Set initial status: selected tasks as `pending`, dependencies as `in_progress` if needed

## 4. Analyze frontend requirements (if needed)

If working without task breakdown or for first task:
1. Identify UI components needed
2. Identify pages/routes needed
3. Plan component hierarchy
4. Plan GraphQL operations (queries, mutations)
5. Plan state management needs

Present this analysis to the user and ask for confirmation before proceeding.

## 5. Generate GraphQL types (if needed)

If not already done:
- Run GraphQL codegen if needed
- Verify types are available

## 6. Implement selected tasks

For each selected task:
1. Mark the task as `in_progress` in the todo list
2. Follow the patterns in:
   - `spec/web/web-src-structure.md` for directory structure
   - `spec/web/web-components.md` for component patterns
   - `spec/web/web-themes.md` for theming
   - `spec/web/web-graphql-operations.md` for GraphQL operations
   - `spec/web/web-i18n-architecture.md` for internationalization
3. Create files as specified in the task:
   - React components using design system
   - GraphQL operations (queries, mutations)
   - Custom hooks if needed
   - Page components (Next.js App Router)
   - i18n translations
4. Apply design system:
   - Use components from the design system
   - Apply theme tokens for styling
   - Follow accessibility guidelines
   - Ensure responsive design
5. Mark the task as `completed` in the todo list
6. Move to next task

**Implementation guidelines:**
- Work on one task at a time to stay within context limits
- After completing a task, ask if user wants to continue with next task or pause
- If context is getting large, suggest completing current task and starting a new chat for remaining tasks

## 7. Update task breakdown (if exists)

After completing tasks:
1. Update `docs/03-features/<module>/<feature_slug>.tasks.yml` to mark completed tasks
2. Add a `status` field to completed tasks: `status: "completed"`
3. Optionally add `completed_at` timestamp

## 8. Notify the user

After implementation:
1. Summarize what was completed
2. List remaining tasks (if any)
3. Ask the user if they want to:
   - Continue with more frontend tasks in this chat
   - Start a new chat to continue with remaining frontend tasks
   - Proceed with `/feature-tests` - Write unit and integration tests
   - Or pause for now

**If context is getting large:**
- "I've completed tasks [list]. To continue with remaining tasks, I recommend:
  - Starting a new chat (Cmd/Ctrl + L)
  - Running `/feature-frontend` again
  - Selecting the remaining task IDs to work on
  - The task breakdown file tracks what's been completed"

