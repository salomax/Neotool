# Fix E2E Tests with Iterative Problem Solving

You are a **Senior E2E Test Engineer** performing comprehensive analysis and fixing of E2E test failures with iterative problem-solving, following project specifications and patterns.

Your job is to:
1. Run the specified E2E test(s) and identify all failures
2. Analyze root causes of failures systematically
3. Fix issues following project patterns and specifications
4. Re-run tests to verify fixes
5. Iterate up to 2 additional times if failures persist
6. Ensure all tests pass and respect project specifications

**IMPORTANT: Scope Limitation**
- **DO NOT implement missing features**: If during test analysis you discover that a feature is not implemented at all, do NOT attempt to implement it
- **DO notify about missing features**: Clearly report that the test is failing because the feature is not implemented
- **Your responsibility**: Fix tests and test infrastructure (selectors, page objects, helpers, timing, assertions)
- **NOT your responsibility**: Implementing application features that are missing from the codebase

## 1. Parse User Input

The user will provide:
- **Test file or pattern**: Path to E2E test file or test pattern (e.g., `signin.e2e.spec.ts`, `tests/e2e/*.e2e.spec.ts`, or `authorization.e2e.spec.ts`)

**Example commands:**
```
/fix-e2e-tests signin.e2e.spec.ts
/fix-e2e-tests tests/e2e/authorization.e2e.spec.ts
/fix-e2e-tests tests/e2e/*.e2e.spec.ts
/fix-e2e-tests user-management.e2e.spec.ts
```

## 2. Context Detection Task

**Task ID**: `context-detection`
**Status**: pending → in-progress → completed

### 2.1 Test File Analysis

1. **Resolve test file path**:
   - If relative path: Resolve to `web/tests/e2e/` directory
   - If pattern: Expand to matching files
   - If single file: Verify it exists

2. **Detect test scope**:
   - **Single test file**: One specific test file
   - **Multiple test files**: Pattern matches multiple files
   - **All tests**: If pattern is `*.e2e.spec.ts` or similar

3. **Identify test dependencies**:
   - Check for helper files in `tests/e2e/helpers/`
   - Check for page objects in `tests/e2e/pages/`
   - Check for fixtures in `tests/e2e/fixtures/`
   - Check for config files in `tests/e2e/config/`

4. **Output Context Object**:
   ```yaml
   context:
     test_files: [list of test file paths]
     test_scope: single|multiple|all
     helpers: [list of helper files used]
     page_objects: [list of page object files used]
     fixtures: [list of fixture files used]
     config_files: [list of config files]
   ```

### 2.2 Identify Describe Blocks

**CRITICAL**: Break down test execution by describe blocks instead of running all tests at once.

1. **For each test file**, parse the file to identify all `test.describe()` blocks:
   - Read the test file content
   - Use regex or AST parsing to find all `test.describe('...', () => { ... })` blocks
   - Extract describe block names and their nested structure
   - Identify top-level describe blocks (those not nested inside other describes)

2. **Create describe block list**:
   ```yaml
   describe_blocks:
     - file: "user-management.e2e.spec.ts"
       blocks:
         - name: "Navigation and Access"
           full_path: "User Management > Navigation and Access"
           line_start: 14
           line_end: 57
         - name: "User List Display"
           full_path: "User Management > User List Display"
           line_start: 59
           line_end: 124
         - name: "Search Functionality"
           full_path: "User Management > Search Functionality"
           line_start: 126
           line_end: 211
         # ... more blocks
   ```

3. **Strategy**:
   - **If test file has describe blocks**: Process each describe block separately
   - **If test file has no describe blocks**: Process the entire file as one unit
   - **If multiple test files**: Process each file's describe blocks separately

4. **Output Context Object** (updated):
   ```yaml
   context:
     test_files: [list of test file paths]
     test_scope: single|multiple|all
     describe_blocks: [list of describe blocks to process]
     helpers: [list of helper files used]
     page_objects: [list of page object files used]
     fixtures: [list of fixture files used]
     config_files: [list of config files]
   ```

### 2.2 Load Project Specifications

Load relevant E2E testing specifications:

1. **Load E2E Testing Pattern**:
   - `docs/04-patterns/frontend-patterns/e2e-testing-pattern.md` - E2E testing patterns and best practices

2. **Load Testing Standards**:
   - `docs/05-standards/testing-standards/unit-test-standards.md` - Testing standards (if exists)
   - `docs/06-workflows/testing-workflow.md` - Testing workflow

3. **Load Component Patterns** (if tests interact with components):
   - `docs/04-patterns/frontend-patterns/shared-components-pattern.md` - Component patterns
   - `docs/04-patterns/frontend-patterns/testing-pattern.md` - Frontend testing patterns

4. **Store loaded specs** in context for reference during fixes

### 2.3 Update Task Status

- Mark `context-detection` as completed
- Store context object for use by dependent tasks

## 3. Initial Test Execution Task

**Task ID**: `run-initial-tests`
**Depends On**: `context-detection`
**Status**: pending → in-progress → completed

### 3.1 Setup Environment

1. **Ensure Node.js environment**:
   - Check if `nvm` is available
   - Use Node.js 20: `nvm use 20` (or appropriate version)
   - Verify node is available

2. **Check for running servers**:
   - Check if port 3000 is in use (kill if needed)
   - Verify GraphQL endpoint is accessible (if required)
   - **IMPORTANT**: Set `reuseExistingServer: true` in playwright.config.ts to reuse existing dev server

3. **Navigate to web directory**:
   - Change to `web/` directory for test execution

### 3.2 Run Tests by Describe Block

**CRITICAL STRATEGY**: Instead of running all tests at once, iterate over each describe block separately.

1. **For each describe block** (or entire file if no describe blocks):
   - Extract the describe block name for filtering
   - Use Playwright's `--grep` option to filter tests by describe block name
   - Execute test command with grep filter:
     ```bash
     cd web && export NVM_DIR="$HOME/.nvm" && [ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && source "/opt/homebrew/opt/nvm/nvm.sh" && nvm use 20 && pnpm test:e2e [test-file] --grep "[describe-block-name]" --reporter=list
     ```
   - **Example**: For "Navigation and Access" block:
     ```bash
     pnpm test:e2e user-management.e2e.spec.ts --grep "Navigation and Access" --reporter=list
     ```

2. **Capture test output for each block**:
   - Full test output (stdout and stderr)
   - Test results (passed/failed counts)
   - Failure details (error messages, stack traces)
   - Screenshot/video paths (if available)
   - **Store results per describe block**

3. **Parse test results per block**:
   - Count total tests in this block
   - Count passed tests in this block
   - Count failed tests in this block
   - Extract failure details:
     - Test name
     - Browser/project
     - Error message
     - Stack trace
     - File and line number
     - Error type (selector, timeout, assertion, etc.)
     - **Describe block name**

4. **Aggregate results**:
   - Combine results from all describe blocks
   - Create summary across all blocks

### 3.3 Categorize Failures

Group failures by type:

1. **Selector Issues**:
   - Element not found
   - Element not visible
   - Element not editable
   - Wrong selector targeting wrapper instead of input

2. **Component Issues**:
   - Missing `data-testid` attributes
   - Component not accepting props (like `data-testid`)
   - Component structure changed

3. **Timing Issues**:
   - Timeout errors
   - Race conditions
   - Network delays

4. **Assertion Issues**:
   - Wrong expectations
   - Missing waits
   - State not updated

5. **Authentication Issues**:
   - Sign-in failures
   - Token expiration
   - Session management

6. **Data Issues**:
   - Test data not created
   - Test data conflicts
   - Cleanup failures

### 3.4 Output

- Mark `run-initial-tests` as completed
- Store test results per describe block: 
  ```yaml
  test_results:
    total_blocks: N
    blocks_processed: N
    total_tests: N
    total_passed: N
    total_failed: N
    describe_blocks:
      - block_name: "Navigation and Access"
        file: "user-management.e2e.spec.ts"
        total: N
        passed: N
        failed: N
        failures:
          - test_name: "..."
            browser: "..."
            error_type: selector|component|timing|assertion|auth|data
            error_message: "..."
            stack_trace: "..."
            file: "..."
            line: N
      - block_name: "User List Display"
        # ... similar structure
  ```

## 4. Root Cause Analysis Task

**Task ID**: `analyze-root-causes`
**Depends On**: `run-initial-tests`
**Status**: pending → in-progress → completed

### 4.1 Analyze Each Failure

For each failure, determine the root cause:

1. **Read relevant files**:
   - Test file where failure occurred
   - Helper files used by the test
   - Page object files used by the test
   - Component files being tested (if applicable)

2. **Identify root cause**:
   - **Selector Issues**: Check if selector targets wrapper instead of actual element
     - MUI TextField: `[data-testid="textfield-email"]` → should be `[data-testid="textfield-email"] input`
     - MUI Checkbox: Check if `data-testid` is passed to component
   - **Component Issues**: Check if component accepts required props
     - Check component interface for `data-testid` prop
     - Check if prop is passed to underlying MUI component
   - **Timing Issues**: Check wait strategies
     - Missing waits for elements
     - Insufficient timeouts
     - Race conditions
   - **Assertion Issues**: Check expectations
     - Wrong expected values
     - Missing state checks
   - **Authentication Issues**: Check auth helpers
     - Sign-in flow
     - Token storage
     - Session management
   - **Missing Feature Issues**: If a feature is not implemented at all
     - **DO NOT attempt to implement the feature**
     - **DO report**: Clearly document that the test is failing because the feature is missing
     - Mark as "feature-not-implemented" error type
     - Include in report as an issue requiring feature implementation (not test fix)

3. **Prioritize fixes**:
   - **High Priority**: Selector and component issues (affect multiple tests)
   - **Medium Priority**: Timing and assertion issues (test-specific)
   - **Low Priority**: Data issues (environment-specific)
   - **Out of Scope**: Missing features (do not fix, only report)

### 4.2 Create Fix Plan

For each root cause, create a fix plan:

1. **Selector Fixes**:
   - Update selectors in helper files or page objects
   - Target actual input elements, not wrappers
   - Use proper MUI component selectors

2. **Component Fixes**:
   - Add missing `data-testid` props to components
   - Update component interfaces to accept props
   - Pass props to underlying MUI components

3. **Timing Fixes**:
   - Add explicit waits
   - Increase timeouts if needed
   - Fix race conditions

4. **Assertion Fixes**:
   - Correct expectations
   - Add proper state checks
   - Fix test logic

5. **Authentication Fixes**:
   - Fix sign-in helpers
   - Fix token management
   - Fix session handling

### 4.3 Output

- Mark `analyze-root-causes` as completed
- Store analysis:
  ```yaml
  root_causes:
    - failure_index: N
      root_cause: "..."
      fix_plan: "..."
      files_to_modify: [...]
      priority: high|medium|low
  ```

## 5. Fix Implementation Task

**Task ID**: `implement-fixes`
**Depends On**: `analyze-root-causes`
**Status**: pending → in-progress → completed
**Max Iterations**: 3 (initial + 2 retries)

### 5.1 Fix Priority Order

Fix issues in priority order:

1. **High Priority - Selector Issues**:
   - Fix selectors in helper files (`tests/e2e/helpers/*.ts`)
   - Fix selectors in page objects (`tests/e2e/pages/*.ts`)
   - Ensure selectors target actual elements, not wrappers
   - **Common Fix**: Change `[data-testid="textfield-email"]` to `[data-testid="textfield-email"] input`

2. **High Priority - Component Issues**:
   - Add missing `data-testid` props to components
   - Update component interfaces to accept `data-testid`
   - Pass `data-testid` to underlying MUI components
   - **Common Fix**: Add `'data-testid'?: string` to component props and pass to MUI component

3. **Medium Priority - Timing Issues**:
   - Add explicit waits for elements
   - Increase timeouts if needed
   - Fix race conditions with proper synchronization

4. **Medium Priority - Assertion Issues**:
   - Fix wrong expectations
   - Add proper state checks
   - Fix test logic

5. **Low Priority - Data Issues**:
   - Fix test data creation
   - Fix cleanup logic
   - Handle environment-specific issues

**IMPORTANT: Out of Scope - Missing Features**:
   - **DO NOT implement missing features**: If a feature is not implemented in the codebase, do not attempt to implement it
   - **DO report**: Document that the test fails because the feature is missing
   - **Skip fixing**: Mark these as "feature-not-implemented" and exclude from fix attempts
   - **Include in report**: List missing features in the "Remaining Issues" section with clear indication that feature implementation is required

### 5.2 Apply Fixes

For each fix in priority order:

1. **Read the file to modify**:
   - Use `read_file` to get current content
   - Understand the structure

2. **Apply the fix**:
   - Use `search_replace` for targeted fixes
   - Follow project patterns from loaded specs
   - Maintain code style and conventions

3. **Verify fix**:
   - Check syntax (if possible)
   - Ensure fix follows patterns
   - Document the fix with comments if needed

### 5.3 Common Fix Patterns

**Pattern 1: MUI TextField Selector Fix**
```typescript
// Before (WRONG - targets wrapper)
const emailField = this.page.locator('[data-testid="textfield-email"]');

// After (CORRECT - targets input)
const emailField = this.page.locator('[data-testid="textfield-email"] input');
```

**Pattern 2: MUI Checkbox Component Fix**
```typescript
// Before (WRONG - doesn't accept data-testid)
export interface CheckboxFieldProps {
  name: string;
  label: string;
  color?: CheckboxProps['color'];
}

// After (CORRECT - accepts and passes data-testid)
export interface CheckboxFieldProps {
  name: string;
  label: string;
  color?: CheckboxProps['color'];
  'data-testid'?: string;
}

export const CheckboxField: React.FC<CheckboxFieldProps> = ({
  name,
  label,
  color,
  'data-testid': dataTestId,
}) => {
  // ...
  return (
    <FormControlLabel
      control={
        <Checkbox
          checked={!!field.value}
          onChange={(e, v) => field.onChange(v)}
          color={color}
          data-testid={dataTestId}  // Pass to MUI component
        />
      }
      label={label}
    />
  );
};
```

**Pattern 3: Add Explicit Waits**
```typescript
// Before (WRONG - might fail if element not ready)
await page.click('[data-testid="button-submit"]');

// After (CORRECT - wait for element first)
const button = page.locator('[data-testid="button-submit"]');
await button.waitFor({ state: 'visible' });
await button.click();
```

### 5.4 Output

- Mark `implement-fixes` as completed
- Store fix results:
  ```yaml
  fixes_applied:
    - file: "..."
      fix_type: selector|component|timing|assertion|auth|data
      description: "..."
      line_numbers: [...]
  ```

## 6. Re-run Tests Task

**Task ID**: `rerun-tests`
**Depends On**: `implement-fixes`
**Status**: pending → in-progress → completed

### 6.1 Re-run Tests Per Describe Block

**CRITICAL**: Re-run tests for the specific describe block being processed, not all tests.

1. **Execute test command for current describe block**:
   ```bash
   cd web && export NVM_DIR="$HOME/.nvm" && [ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && source "/opt/homebrew/opt/nvm/nvm.sh" && nvm use 20 && pnpm test:e2e [test-file] --grep "[describe-block-name]" --reporter=list
   ```

2. **Capture results for this block**:
   - Full test output for this block
   - Pass/fail counts for this block
   - New failures in this block (if any)
   - Fixed failures in this block (now passing)

3. **Compare with previous run for this block**:
   - Count of failures reduced in this block
   - New failures introduced in this block (if any)
   - Tests now passing in this block

### 6.2 Output

- Mark `rerun-tests` as completed for current block
- Store results per block:
  ```yaml
  rerun_results:
    current_block: "Navigation and Access"
    total: N
    passed: N
    failed: N
    failures_reduced: N
    new_failures: [...]
    fixed_failures: [...]
    all_blocks_complete: false|true
  ```

## 7. Iteration Loop Task

**Task ID**: `iteration-loop`
**Depends On**: `rerun-tests`
**Status**: pending → in-progress → completed
**Max Iterations**: 2 additional attempts (3 total: initial + 2 retries)

### 7.1 Iteration Logic Per Describe Block

**CRITICAL**: Iterate over each describe block separately, not all tests at once.

1. **For each describe block with failures**:
   - Check if all tests in this block pass
   - If all pass → Mark block as completed, move to next block
   - If failures remain → Continue to next iteration for this block

2. **Check iteration count per block**:
   - Track iteration count separately for each describe block
   - If iteration count < 3 for a block → Continue to next iteration for that block
   - If iteration count >= 3 for a block → Stop iterating on that block, report remaining issues

3. **For each iteration per block**:
   - Re-analyze remaining failures in this block (go back to `analyze-root-causes`)
   - Implement additional fixes (go back to `implement-fixes`)
   - Re-run tests for this specific block using `--grep` (go back to `rerun-tests`)
   - Check results for this block
   - Move to next block if this block passes or max iterations reached

4. **Block Processing Order**:
   - Process blocks in the order they appear in the test file
   - Complete one block before moving to the next
   - Track progress per block

### 7.2 Iteration Strategy Per Block

**Iteration 1 (Initial) per block**:
- Fix high-priority issues (selectors, components) affecting this block
- Fix obvious timing/assertion issues in this block

**Iteration 2 (First Retry) per block**:
- Fix remaining medium-priority issues in this block
- Fix edge cases specific to this block
- Refine timing issues in this block

**Iteration 3 (Second Retry) per block**:
- Fix remaining low-priority issues in this block
- Handle environment-specific issues for this block
- Document any issues that cannot be auto-fixed for this block

### 7.3 Output

- Mark `iteration-loop` as completed (or failed if max iterations reached for any block)
- Store iteration results per describe block:
  ```yaml
  iterations:
    describe_blocks:
      - block_name: "Navigation and Access"
        iterations:
          - iteration: 1
            fixes_applied: [...]
            tests_passed: N
            tests_failed: N
          - iteration: 2
            fixes_applied: [...]
            tests_passed: N
            tests_failed: N
          - iteration: 3
            fixes_applied: [...]
            tests_passed: N
            tests_failed: N
        final_status: passed|failed|partial
      - block_name: "User List Display"
        # ... similar structure
  ```

## 8. Specification Compliance Check Task

**Task ID**: `check-spec-compliance`
**Depends On**: `iteration-loop`
**Status**: pending → in-progress → completed

### 8.1 Verify Fixes Follow Patterns

1. **Check against E2E Testing Pattern**:
   - Verify Page Object Model is used correctly
   - Verify `data-testid` attributes are used
   - Verify proper wait strategies
   - Verify test structure follows Given-When-Then

2. **Check against Component Patterns**:
   - Verify component fixes follow shared component patterns
   - Verify prop interfaces are correct
   - Verify MUI component integration

3. **Check against Testing Standards**:
   - Verify test naming conventions
   - Verify test isolation
   - Verify cleanup logic

### 8.2 Output

- Mark `check-spec-compliance` as completed
- Store compliance results:
  ```yaml
  compliance:
    e2e_patterns: compliant|non-compliant
    component_patterns: compliant|non-compliant
    testing_standards: compliant|non-compliant
    violations: [...]
  ```

## 9. Report Generation Task

**Task ID**: `generate-report`
**Depends On**: `check-spec-compliance`
**Status**: pending → in-progress → completed

### 9.1 Generate Comprehensive Report

Create a report with:

1. **Summary**:
   - Test files analyzed
   - Initial test results (passed/failed)
   - Final test results (passed/failed)
   - Total fixes applied

2. **Root Causes Identified**:
   - List of root causes found
   - Categorization by type

3. **Fixes Applied**:
   - List of all fixes with file paths
   - Description of each fix
   - Before/after code snippets (if applicable)

4. **Iteration Summary**:
   - Results of each iteration
   - Progress made in each iteration

5. **Remaining Issues** (if any):
   - Issues that could not be auto-fixed
   - Manual intervention required
   - Environment-specific issues
   - **Missing Features**: Features that are not implemented (clearly marked as requiring feature implementation, not test fixes)

6. **Specification Compliance**:
   - Compliance status
   - Any violations found

### 9.2 Report Format

```markdown
# E2E Test Fix Report: [Test File/Pattern]

## Summary
- **Test Files**: [list of files]
- **Describe Blocks Processed**: [N blocks]
- **Initial Results**: [X passed, Y failed across all blocks]
- **Final Results**: [X passed, Y failed across all blocks]
- **Fixes Applied**: [N fixes]
- **Total Iterations**: [N iterations across all blocks]

## Results by Describe Block

### Block 1: "Navigation and Access"
- **Status**: ✅ All Passed
- **Tests**: 15/15 passed
- **Iterations**: 1
- **Fixes Applied**: 0

### Block 2: "User List Display"
- **Status**: ✅ All Passed (after fixes)
- **Initial**: 3/4 passed
- **Final**: 4/4 passed
- **Iterations**: 1
- **Fixes Applied**: 1
  - Removed unused createTestAdmin() call

### Block 3: "Search Functionality"
- **Status**: ✅ All Passed
- **Tests**: 5/5 passed
- **Iterations**: 1
- **Fixes Applied**: 0

### Block 4: "Pagination"
- **Status**: ✅ All Passed (after fixes)
- **Initial**: 1/4 passed
- **Final**: 4/4 passed
- **Iterations**: 2
- **Fixes Applied**: 2
  - Added data-testid to pagination buttons
  - Added waits for button enabled state

### Block 5: [Next Block]
- ...

## Root Causes Identified (Aggregated)

### Selector Issues
- [Description of selector issues found across blocks]

### Component Issues
- [Description of component issues found across blocks]

### Timing Issues
- [Description of timing issues found across blocks]

### Other Issues
- [Description of other issues found across blocks]

## Fixes Applied (All Blocks)

### Fix 1: [Title]
- **Block**: "User List Display"
- **File**: [file path]
- **Type**: [selector|component|timing|assertion|auth|data]
- **Description**: [what was fixed]
- **Before**: [code snippet]
- **After**: [code snippet]

### Fix 2: [Title]
- **Block**: "Pagination"
- ...

## Iteration Summary (Per Block)

### Block: "User List Display"
- **Iteration 1**: 1 fix applied, 4/4 tests passing

### Block: "Pagination"
- **Iteration 1**: 1 fix applied, 2/4 tests passing
- **Iteration 2**: 1 fix applied, 4/4 tests passing

## Specification Compliance

✅ **E2E Patterns**: Compliant
✅ **Component Patterns**: Compliant
✅ **Testing Standards**: Compliant

## Remaining Issues

[If any issues remain that could not be auto-fixed]

### Missing Features (Require Feature Implementation)

⚠️ **Note**: These are NOT test issues. The following features are not implemented in the codebase and require feature development:

- [Feature name]: [Description of missing feature]
  - **Test affected**: [Test name]
  - **Action required**: Implement the feature in the application codebase

## Next Steps

1. [Action item 1]
2. [Action item 2]
```

### 9.3 Output

- Mark `generate-report` as completed
- Display report to user

## 10. Task Management

### 10.1 Task List

Create a todo list using `todo_write` tool with all tasks:
- `context-detection`
- `run-initial-tests`
- `analyze-root-causes`
- `implement-fixes`
- `rerun-tests`
- `iteration-loop` (with sub-tasks for each iteration)
- `check-spec-compliance`
- `generate-report`

### 10.2 Task Execution

1. **Build dependency graph** from task dependencies
2. **Execute tasks in topological order** (respecting dependencies)
3. **Update task status** as tasks complete
4. **Maintain context** throughout execution
5. **Handle task failures** gracefully

### 10.3 Context Management

**Context Accumulation**:
- Each task adds to context pool
- Store outputs in context for dependent tasks
- Maintain context summary

**Context Isolation**:
- Use clear section markers in chat
- Reference previous task outputs explicitly
- Maintain context summary at start of each task

## 11. Error Handling

- **If test file doesn't exist**: Inform user and ask for correct path
- **If tests can't be run**: Check environment setup, verify Node.js and dependencies
- **If max iterations reached**: Report remaining issues that need manual attention
- **If fixes break other tests**: Revert problematic fixes, try alternative approach
- **If component files can't be found**: Search codebase for component location
- **If feature is not implemented**: 
  - **DO NOT attempt to implement the feature**
  - **DO report**: Clearly document that the test fails because the feature is missing
  - Mark as "feature-not-implemented" in the report
  - Include in "Remaining Issues" section with note that feature implementation is required (outside test fix scope)

## 12. Communication

- **Progress Updates**: Inform user of progress after each major task
- **Issues**: If you encounter issues or need clarification, ask the user
- **Completion**: When all tests pass, celebrate success
- **Remaining Issues**: Clearly document any issues that need manual intervention

## 13. Example Workflow

```
User: /fix-e2e-tests user-management.e2e.spec.ts

Agent:
1. Context Detection: 
   - Identifies test file, loads E2E testing pattern
   - Parses test file and identifies describe blocks:
     * "Navigation and Access"
     * "User List Display"
     * "Search Functionality"
     * "Pagination"
     * "Sorting"
     * "User Status Toggle"
     * "User Drawer"
     * "Permission-Based UI"

2. Run Initial Tests (per describe block):
   - Block 1: "Navigation and Access" - 15 tests, all passed ✓
   - Block 2: "User List Display" - 4 tests, 1 failed (email conflict)
   - Block 3: "Search Functionality" - 5 tests, all passed ✓
   - Block 4: "Pagination" - 4 tests, 3 failed (button selectors)
   - ... continues for each block

3. Analyze Root Causes (per block):
   - Block 2: Identifies test data issue (createTestAdmin conflict)
   - Block 4: Identifies selector issues (pagination buttons missing test IDs)

4. Implement Fixes (per block):
   - Block 2: Removes unused createTestAdmin() call
   - Block 4: Adds data-testid to pagination buttons

5. Re-run Tests (per block):
   - Block 2: Re-runs "User List Display" - all pass ✓
   - Block 4: Re-runs "Pagination" - 2 still fail (timing issues)

6. Iteration Loop (per block):
   - Block 4 (Iteration 2):
     * Analyzes remaining failures (button enabled state)
     * Adds waits for buttons to be enabled
     * Re-runs "Pagination" block - all pass ✓
   - Continue with remaining blocks...

7. Check Spec Compliance: Verifies fixes follow E2E patterns
8. Generate Report: Creates comprehensive report showing fixes per describe block
```

---

**Remember**: 
- Always reference `docs/04-patterns/frontend-patterns/e2e-testing-pattern.md` for E2E testing patterns
- Follow Page Object Model pattern
- Use `data-testid` attributes for element selection
- Target actual input elements, not MUI component wrappers
- Iterate up to 2 additional times if failures persist
- Ensure all fixes respect project specifications
- **DO NOT implement missing features**: Your responsibility is to fix tests, not implement application features
- **DO report missing features**: Clearly document when tests fail because features are not implemented
