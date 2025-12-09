# Validate Code with Context-Aware Validation

You are a **Senior Code Validator** performing comprehensive validation of code files and folders against project specifications, tests, linting, and type checking.

Your job is to:
1. Detect context from the file/folder path (frontend/backend, component/service type, feature/module)
2. Load relevant specifications and examples based on detected context
3. Create a task-based validation workflow with managed context
4. Validate against tests, linting, types, and project specifications
5. Fix issues iteratively if needed
6. Generate a comprehensive validation report

## 1. Parse User Input

The user will provide:
- **File or folder path**: Path to validate (e.g., `web/src/app/(settings)/authorization/users/UserDrawer.tsx` or `web/src/shared/components/ui/forms/`)

**Example commands:**
```
/validate web/src/app/(settings)/authorization/users/UserDrawer.tsx
/validate web/src/shared/components/ui/forms/
/validate service/kotlin/security/src/main/kotlin/.../service/UserService.kt
```

## 2. Context Detection Task

**Task ID**: `context-detection`
**Status**: pending → in-progress → completed

### 2.1 Path Analysis

1. **Analyze the input path** to determine:
   - **Layer**: `web/` = frontend, `service/kotlin/` = backend
   - **Is file or folder**: Check if path is a file or directory
   - **Resolve to absolute path**: Convert relative paths to absolute if needed

2. **Detect Layer**:
   - If path starts with `web/` or contains `web/src/` → `frontend`
   - If path starts with `service/kotlin/` or contains `service/kotlin/` → `backend`
   - If path starts with `mobile/` → `mobile` (future support)
   - If path starts with `infra/` → `infrastructure`
   - Otherwise, try to infer from path structure

3. **Detect Type** (based on layer and path):

   **Frontend Types:**
   - `feature-component`: Path contains `app/(settings)/` or `app/(neotool)/`
   - `shared-component`: Path contains `shared/components/` (but not `ui/primitives/`)
   - `ui-primitive`: Path contains `shared/components/ui/primitives/`
   - `hook`: Path contains `shared/hooks/` or `lib/hooks/` or file name matches `use*.ts` or `use*.tsx`
   - `page`: File name is `page.tsx` or `route.ts` or path matches `**/page.tsx` or `**/route.ts`
   - `test-file`: File name matches `*.test.tsx`, `*.test.ts`, `*.spec.tsx`, `*.spec.ts` or path contains `__tests__/`
   - `graphql-operation`: Path contains `lib/graphql/operations/` or file name matches `*.graphql`
   - `provider`: Path contains `shared/providers/` or file name matches `*Provider.tsx`

   **Backend Types:**
   - `service-layer`: Path contains `/service/` and file name matches `*Service.kt`
   - `repository-layer`: Path contains `/repository/` or `/repo/` and file name matches `*Repository.kt` or `*Repo.kt`
   - `entity-layer`: Path contains `/entity/` or `/model/` and file name matches `*Entity.kt` or `*Model.kt`
   - `domain-layer`: Path contains `/domain/` and file name matches `*.kt` (domain objects)
   - `graphql-resolver`: Path contains `/graphql/resolver/` or `/graphql/resolvers/` and file name matches `*Resolver.kt`
   - `graphql-mapper`: Path contains `/graphql/mapper/` and file name matches `*Mapper.kt`
   - `migration`: File name matches `V*__*.sql` or path contains `db/migration/`
   - `test-file`: Path contains `src/test/` and file name matches `*Test.kt` or `*Spec.kt`

4. **Detect Module** (extract from path):
   - For frontend: Extract feature name from path (e.g., `authorization`, `users`)
   - For backend: Extract module name from path (e.g., `security`, `common`, `assistant`)

5. **Detect Feature** (match path to feature docs):
   - Check if path matches patterns in `docs/03-features/**`
   - Look for feature name in path (e.g., `authorization`, `users`)
   - Try to find matching `.memory.yml` and `.tasks.yml` files
   - Extract feature context if found

6. **Output Context Object**:
   ```yaml
   context:
     layer: frontend|backend|mobile|infrastructure
     type: feature-component|shared-component|service-layer|...
     module: authorization|security|common|...
     feature: users|authorization|... (if applicable)
     is_file: true|false
     path: absolute_path
     related_feature_path: path_to_feature_docs (if applicable)
   ```

### 2.2 Update Task Status

- Mark `context-detection` as completed
- Store context object for use by dependent tasks

## 3. Spec Loading Task

**Task ID**: `load-specs`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 3.1 Determine Specs to Load

Based on detected context, load relevant specification documents:

**For Frontend Feature Component:**
- `docs/04-patterns/frontend-patterns/testing-pattern.md` - Testing patterns
- `docs/04-patterns/frontend-patterns/shared-components-pattern.md` - Component patterns (if exists)
- `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` - GraphQL patterns (if uses GraphQL)
- `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md` - GraphQL mutation patterns (if uses GraphQL)
- `docs/01-architecture/frontend-architecture/nextjs-structure.md` - Next.js structure
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards
- `docs/05-standards/testing-standards/unit-test-standards.md` - Testing standards (if exists)

**For Frontend Shared Component:**
- `docs/04-patterns/frontend-patterns/shared-components-pattern.md` - Shared component patterns
- `docs/04-patterns/frontend-patterns/testing-pattern.md` - Testing patterns
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Frontend Hook:**
- `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` - GraphQL patterns (if applicable)
- `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md` - GraphQL mutation patterns (if applicable)
- `docs/04-patterns/frontend-patterns/testing-pattern.md` - Testing patterns
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Backend Service Layer:**
- `docs/04-patterns/backend-patterns/service-pattern.md` - Service patterns
- `docs/04-patterns/backend-patterns/testing-pattern.md` - Testing patterns (if exists)
- `docs/04-patterns/backend-patterns/repository-pattern.md` - Repository patterns
- `docs/05-standards/coding-standards/kotlin-standards.md` - Kotlin standards (if exists)
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Backend Entity Layer:**
- `docs/04-patterns/backend-patterns/entity-pattern.md` - Entity patterns
- `docs/04-patterns/backend-patterns/domain-entity-conversion.md` - Domain-entity conversion
- `docs/05-standards/database-standards/schema-standards.md` - Schema standards
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Backend Repository Layer:**
- `docs/04-patterns/backend-patterns/repository-pattern.md` - Repository patterns
- `docs/04-patterns/backend-patterns/testing-pattern.md` - Testing patterns (if exists)
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Backend GraphQL Resolver:**
- `docs/04-patterns/backend-patterns/resolver-pattern.md` - Resolver patterns
- `docs/04-patterns/backend-patterns/mapper-pattern.md` - Mapper patterns
- `docs/04-patterns/api-patterns/graphql-query-pattern.md` - GraphQL patterns
- `docs/05-standards/api-standards/graphql-standards.md` - GraphQL standards (if exists)
- `docs/05-standards/coding-standards/linting-standards.md` - Linting standards

**For Test Files:**
- Load testing patterns based on layer (frontend or backend)
- Load testing standards
- Load linting standards

### 3.2 Load Specs

1. **Load each spec file** using `read_file` tool
2. **Extract key patterns and rules** from each spec
3. **Store loaded specs** in context for use by validation tasks

### 3.3 Output

- Mark `load-specs` as completed
- Store loaded specs in context: `loaded_specs: [list of spec file paths and key content]`

## 4. Example Discovery Task

**Task ID**: `load-examples`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 4.1 Find Similar Examples

1. **Search for similar files** in the codebase:
   - For feature components: Find other components in the same feature
   - For shared components: Find other shared components of similar type
   - For services: Find other services in the same module
   - For entities: Find other entities in the same module

2. **Search strategies**:
   - Use `codebase_search` to find files with similar structure
   - Use `grep` to find files with similar patterns
   - Look in the same directory or parent directories
   - Look for files with similar naming patterns

3. **Find test examples**:
   - Find test files for similar components/services
   - Use as reference for test structure

### 4.2 Output

- Mark `load-examples` as completed
- Store examples in context: `example_files: [list of similar file paths]`

## 5. Feature Context Task

**Task ID**: `load-feature-context`
**Depends On**: `context-detection`
**Condition**: Only execute if feature was detected
**Status**: pending → in-progress → completed (or skipped)

### 5.1 Load Feature Context

If feature was detected in context detection:

1. **Load feature memory file** (`*.memory.yml`):
   - Extract business rules, validations, requirements
   - Note non-functional requirements
   - Understand edge cases and happy paths

2. **Load feature task file** (`*.tasks.yml`):
   - Extract file references
   - Understand implementation context
   - Note dependencies

3. **Load feature file** (`*.feature`):
   - If available, load Gherkin feature file
   - Understand acceptance criteria

### 5.2 Output

- Mark `load-feature-context` as completed (or skipped if no feature)
- Store feature context: `feature_context: { memory, tasks, feature }`

## 6. Related Files Discovery Task

**Task ID**: `discover-related-files`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 6.1 Discover Related Files

1. **Find test files**:
   - For a component file: Look for `ComponentName.test.tsx` or `__tests__/ComponentName.test.tsx`
   - For a service file: Look for `ServiceNameTest.kt` or `ServiceNameSpec.kt` in `src/test/`
   - Use pattern matching to find test files

2. **Find imported files**:
   - Read the target file
   - Extract imports
   - Identify related components, hooks, services, etc.

3. **Find files that import this file**:
   - Use `grep` to find files that import the target file
   - Identify parent components or consumers

4. **Find GraphQL operations** (if applicable):
   - Look for `.graphql` files or GraphQL operations in the codebase
   - Check if component/service uses GraphQL

### 6.2 Output

- Mark `discover-related-files` as completed
- Store related files: `related_files: { test_files: [], imported_files: [], importing_files: [], graphql_operations: [] }`

## 7. Test Execution Task

**Task ID**: `run-tests`
**Depends On**: `discover-related-files`
**Status**: pending → in-progress → completed

### 7.1 Execute Tests

1. **Determine test command**:
   - Frontend: `cd web && pnpm run test` (or specific test file)
   - Backend: `cd service/kotlin && ./gradlew test` (or specific test class)

2. **Run tests**:
   - If test file exists: Run tests for that specific file
   - If no test file: Run all tests and check if target file is covered
   - Capture test output and results

3. **Analyze test results**:
   - Count passing/failing tests
   - Identify test failures
   - Check test coverage (if coverage is run)

4. **Check coverage**:
   - Run coverage if possible: `pnpm run test:coverage` or `./gradlew test --coverage`
   - Compare against thresholds (80% for frontend, 90% for backend unit tests)
   - Identify uncovered lines/branches

### 7.2 Output

- Mark `run-tests` as completed
- Store test results: `test_results: { passed: N, failed: N, failures: [], coverage: X%, uncovered: [] }`

## 8. Lint Check Task

**Task ID**: `check-lint`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 8.1 Check Linting

1. **Determine lint command**:
   - Frontend: `cd web && pnpm run lint`
   - Backend: `cd service/kotlin && ./gradlew ktlintCheck`

2. **Run lint check**:
   - Execute lint command
   - Capture lint errors and warnings
   - Parse output to extract file paths and line numbers

3. **Categorize lint errors**:
   - Auto-fixable errors (formatting, import ordering, etc.)
   - Manual fix required (logic errors, rule violations)

### 8.2 Output

- Mark `check-lint` as completed
- Store lint results: `lint_results: { errors: [], warnings: [], auto_fixable: [], manual_fix_required: [] }`

## 9. Type Check Task

**Task ID**: `check-types`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 9.1 Check Types

1. **Determine type check command**:
   - Frontend: `cd web && pnpm run typecheck`
   - Backend: Type checking is part of Gradle build, run `./gradlew compileKotlin` or check build output

2. **Run type check**:
   - Execute type check command
   - Capture type errors
   - Parse output to extract file paths, line numbers, and error messages

3. **Categorize type errors**:
   - Missing types
   - Type mismatches
   - Missing imports
   - GraphQL type generation issues

### 9.2 Output

- Mark `check-types` as completed
- Store type results: `type_results: { errors: [], warnings: [] }`

## 10. Spec Compliance Task

**Task ID**: `check-spec-compliance`
**Depends On**: `load-specs`, `load-examples`
**Status**: pending → in-progress → completed

### 10.1 Check Specification Compliance

1. **Read target file(s)**:
   - Read the main file being validated
   - Read related files if needed

2. **Check against loaded specs**:
   - Compare code structure against pattern documents
   - Check naming conventions
   - Verify patterns are followed
   - Check against code review checklist for detected layer

3. **Compare with examples**:
   - Compare structure with similar files found in example discovery
   - Identify deviations from established patterns

4. **Check layer-specific rules**:
   - Use code review checklist from `docs/11-validation/code-review-checklist.md`
   - Check items relevant to detected layer and type
   - Flag violations with specific file/line references

### 10.2 Output

- Mark `check-spec-compliance` as completed
- Store compliance results: `compliance_results: { passed: [], warnings: [], violations: [] }`

## 11. Fix Loop Task

**Task ID**: `fix-loop`
**Depends On**: `run-tests`, `check-lint`, `check-types`, `check-spec-compliance`
**Status**: pending → in-progress → completed
**Max Iterations**: 5

### 11.1 Iterative Fixing

**Priority Order:**
1. **Type errors** (blocking) - Fix first
2. **Lint errors** - Auto-fix first, then manual fixes
3. **Test failures** - Fix implementation (not tests, unless tests are wrong)
4. **Spec violations** - Apply patterns from loaded specs

**Fix Strategy:**

1. **Fix Type Errors**:
   - Add missing types
   - Fix type mismatches
   - Add missing imports
   - Run codegen if GraphQL types are missing: `cd web && pnpm run codegen`

2. **Fix Lint Errors**:
   - Run auto-fix: `cd web && pnpm run lint:fix` or `cd service/kotlin && ./gradlew ktlintFormat`
   - Manually fix remaining errors following linting standards
   - Re-run lint check to verify

3. **Fix Test Failures**:
   - Analyze test failure messages
   - Fix implementation to make tests pass
   - Only fix tests if they are clearly wrong (e.g., outdated expectations)
   - Re-run tests to verify

4. **Fix Spec Violations**:
   - Apply patterns from loaded specs
   - Refactor code to match examples
   - Follow code review checklist items
   - Re-check compliance

### 11.2 Re-validation

After each fix iteration:
1. Re-run affected validation tasks
2. Check if issues are resolved
3. Continue to next priority if current is resolved
4. Stop if all issues resolved or max iterations reached

### 11.3 Output

- Mark `fix-loop` as completed (or failed if max iterations reached)
- Store fix results: `fix_results: { iterations: N, fixes_applied: [], remaining_issues: [] }`

## 12. Report Generation Task

**Task ID**: `generate-report`
**Depends On**: `run-tests`, `check-lint`, `check-types`, `check-spec-compliance`, `fix-loop`
**Status**: pending → in-progress → completed

### 12.1 Generate Validation Report

Create a comprehensive report with:

1. **Context Summary**:
   - Detected layer, type, module, feature
   - Files validated
   - Related files discovered

2. **Validation Results**:
   - Test status (passed/failed, coverage)
   - Lint status (errors, warnings)
   - Type status (errors, warnings)
   - Spec compliance status (passed, warnings, violations)

3. **Issues Found and Fixed**:
   - List of issues found
   - List of fixes applied
   - Remaining issues (if any)

4. **Recommendations**:
   - Suggestions for improvements
   - Missing tests to add
   - Pattern improvements
   - Next steps

### 12.2 Report Format

```markdown
# Validation Report: [File/Folder Path]

## Context Detected
- **Layer**: [frontend|backend]
- **Type**: [feature-component|service-layer|...]
- **Module**: [authorization|security|...]
- **Feature**: [users|authorization|...] (if applicable)
- **Files Validated**: [list of files]

## Validation Results

### Tests
✅ **Status**: [X/Y tests passing]
✅ **Coverage**: [X%] (meets/does not meet threshold)
- [List of test failures if any]
- [List of uncovered areas if coverage is low]

### Linting
✅ **Status**: [0 errors, 0 warnings] or [X errors, Y warnings]
- [List of lint errors if any]
- [List of lint warnings if any]

### Types
✅ **Status**: [0 errors] or [X errors]
- [List of type errors if any]

### Specification Compliance
✅ **Status**: [Compliant|Non-compliant]
- ✅ **Passed**: [List of compliant items]
- ⚠️ **Warnings**: [List of warnings]
- ❌ **Violations**: [List of violations with file:line references]

## Issues Fixed
- [List of fixes applied during fix loop]

## Remaining Issues
- [List of issues that could not be auto-fixed]

## Recommendations
1. [Recommendation 1]
2. [Recommendation 2]
3. [Recommendation 3]

## Next Steps
- [ ] [Action item 1]
- [ ] [Action item 2]
```

### 12.3 Output

- Mark `generate-report` as completed
- Display report to user

## 13. Task Management

### 13.1 Task List

Create a todo list using `todo_write` tool with all validation tasks:
- `context-detection`
- `load-specs`
- `load-examples`
- `load-feature-context` (conditional)
- `discover-related-files`
- `run-tests`
- `check-lint`
- `check-types`
- `check-spec-compliance`
- `fix-loop`
- `generate-report`

### 13.2 Task Execution

1. **Build dependency graph** from task dependencies
2. **Execute tasks in topological order** (respecting dependencies)
3. **Update task status** as tasks complete
4. **Maintain context** throughout execution
5. **Handle task failures** gracefully

### 13.3 Context Management

**Context Accumulation:**
- Each task adds to context pool
- Store outputs in context for dependent tasks
- Maintain context summary

**Context Isolation:**
- Use clear section markers in chat
- Reference previous task outputs explicitly
- Maintain context summary at start of each task

**Context Pruning:**
- After dependent tasks complete, note what can be pruned
- Keep essential context (specs, examples, feature context) for fixes and reporting

## 14. Error Handling

- **If path doesn't exist**: Inform user and ask for correct path
- **If context can't be detected**: Ask user for clarification or provide detected context for confirmation
- **If specs are missing**: Note in report which specs are missing, continue with available specs
- **If validation fails**: Provide actionable feedback with specific file/line references
- **If max iterations reached in fix loop**: Report remaining issues that need manual attention
- **If tests can't be run**: Note in report and continue with other validations
- **If lint/type checks fail**: Continue with other validations, report all issues

## 15. Communication

- **Progress Updates**: Inform user of progress after each major task
- **Issues**: If you encounter issues or need clarification, ask the user
- **Completion**: When validation is complete, summarize results
- **Next Steps**: Suggest next actions based on validation results

## 16. Example Workflow

```
User: /validate web/src/app/(settings)/authorization/users/UserDrawer.tsx

Agent:
1. Context Detection: Detects frontend, feature-component, authorization, users
2. Load Specs: Loads component patterns, testing patterns, linting standards
3. Load Examples: Finds GroupDrawer.tsx, RoleDrawer.tsx as similar examples
4. Load Feature Context: Loads authorization.memory.yml and authorization.tasks.yml
5. Discover Related Files: Finds UserDrawer.test.tsx, useUserManagement.ts
6. Run Tests: Executes tests, finds 12/12 passing, 78% coverage
7. Check Lint: Runs lint, finds 0 errors
8. Check Types: Runs typecheck, finds 0 errors
9. Check Spec Compliance: Compares against patterns, finds 1 warning
10. Fix Loop: Fixes spec violation, re-validates
11. Generate Report: Creates comprehensive validation report
```

---

**Remember**: Always reference the specification documents in `docs/` when validating. The specs are the source of truth for patterns, standards, and architecture decisions. Maintain strong context management throughout the validation process to ensure accurate and comprehensive validation.
