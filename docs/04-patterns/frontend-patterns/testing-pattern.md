---
title: Frontend Testing Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [testing, frontend, react, react-hook-form, vitest, testing-library, test-isolation, shared-context]
ai_optimized: true
search_keywords: [testing, frontend, react, react-hook-form, vitest, testing-library, unit-tests, integration-tests, describe.sequential, test-isolation, shared-context, cleanup]
related:
  - 05-standards/testing-standards/unit-test-standards.md
  - 06-workflows/testing-workflow.md
---

# Frontend Testing Pattern

> **Purpose**: Standard patterns for testing React components and frontend code, including form validation testing with React Hook Form and handling shared context problems.

## Overview

This document covers best practices for:
- Testing React components with React Testing Library
- Testing form validation with React Hook Form
- **Test isolation and handling shared context problems** (using `describe.sequential` + `cleanup()`)
- Avoiding common testing pitfalls
- Writing maintainable and reliable frontend tests

## Recently added standards

- Prefer RTL queries over DOM traversal: avoid `querySelector`/`parentElement`; use role-based queries or `within(...)` to satisfy `testing-library/no-node-access`.
- Use a single `data-testid` per element (or `getByRole`) instead of `getAllByTestId()[0]` to prevent undefined lookups and type errors.
- When tests share global mocks/providers, mark suites `describe.sequential` to prevent cross-test leakage under threaded runs.
- For optimistic update mocks, implement a small stateful mock (React state + `isUpdating` flip + rollback on error) rather than manual state bags to keep UI state in sync.
- For storage-backed providers, hydrate only when state is empty to avoid overwriting in-flight state (e.g., fast user interactions in tests).
- If provider renders trigger state updates on mount, wrap initial render/interaction in `act` to keep warnings quiet.

## Quick Reference: Fixing Shared Context Problems

**Problem**: Tests fail when run together but pass individually due to shared context state (Theme, Auth, i18n providers) leaking between tests.

**Solution**: Use `describe.sequential` + `cleanup()`:

```typescript
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

// Run sequentially to avoid shared context state leaking between tests
describe.sequential('ComponentName', () => {
  afterEach(() => {
    cleanup(); // Clears DOM and context state
  });

  it('test 1', () => { /* ... */ });
  it('test 2', () => { /* ... */ });
});
```

**When to Use**: Tests using context providers (Theme, Auth, i18n) or global state that can interfere between concurrent test runs.

See [Test Isolation and Shared Context](#test-isolation-and-shared-context) section for complete details and examples.

## React Hook Form Testing

### Rule: Never Use `console.error` as Error Handler in Tests

**Rule**: When testing form validation with React Hook Form's `handleSubmit`, never use `console.error` as the error handler callback. Instead, use a mock function (e.g., `vi.fn()` from Vitest).

**Rationale**:
- `console.error` will log validation errors to stderr, cluttering test output
- Using a mock function allows you to verify the error handler is called correctly
- Tests should be silent unless there's an actual failure
- Mock functions provide better testability and assertions

**Example**:

```typescript
// ✅ CORRECT: Using mock function
import { vi } from 'vitest';

it('should show validation error when name is empty and field is touched', async () => {
  const onError = vi.fn();
  const Wrapper = () => {
    const methods = useForm<FormData>({
      defaultValues: { name: '', description: '' },
    });

    return (
      <FormProvider {...methods}>
        <FormComponent />
        <button onClick={methods.handleSubmit(() => {}, onError)}>
          Submit
        </button>
      </FormProvider>
    );
  };

  render(<Wrapper />);

  const submitButton = screen.getByText('Submit');
  await user.click(submitButton);

  await waitFor(() => {
    expect(screen.getByText('Name is required')).toBeInTheDocument();
  });

  // Verify error handler was called with validation errors
  expect(onError).toHaveBeenCalledWith(
    expect.objectContaining({
      name: expect.objectContaining({
        type: 'required',
        message: 'Name is required',
      }),
    }),
    expect.anything()
  );
});
```

**❌ Incorrect Approach**:

```typescript
// ❌ INCORRECT: Using console.error - will log to stderr
it('should show validation error when name is empty and field is touched', async () => {
  const Wrapper = () => {
    const methods = useForm<FormData>({
      defaultValues: { name: '', description: '' },
    });

    return (
      <FormProvider {...methods}>
        <FormComponent />
        <button onClick={methods.handleSubmit(() => {}, console.error)}>
          Submit
        </button>
      </FormProvider>
    );
  };

  // This will log validation errors to stderr, cluttering test output
  render(<Wrapper />);
  // ...
});
```

**What Happens with `console.error`**:
- React Hook Form calls the error handler with validation errors when validation fails
- `console.error` logs these errors to stderr, including:
  - The errors object with field-level validation details
  - The React synthetic event object
- This creates noisy test output that makes it harder to identify actual test failures

**Benefits of Using Mock Functions**:
1. **Silent Tests**: No console output unless there's a real failure
2. **Verifiable**: You can assert that the error handler was called with expected errors
3. **Maintainable**: Clear intent that you're testing error handling
4. **Debuggable**: Mock functions can be inspected to see what was called

### Rule: Test Form Validation Errors

**Rule**: When testing form validation, verify both:
1. That validation error messages are displayed in the UI
2. That the error handler is called with the correct validation errors (when applicable)

**Rationale**: Ensures end-to-end validation behavior is working correctly.

**Example**:

```typescript
it('should show validation error when name is empty and field is touched', async () => {
  const onError = vi.fn();
  const onSubmit = vi.fn();
  
  const Wrapper = () => {
    const methods = useForm<FormData>({
      defaultValues: { name: '', description: '' },
    });

    return (
      <FormProvider {...methods}>
        <FormComponent />
        <button onClick={methods.handleSubmit(onSubmit, onError)}>
          Submit
        </button>
      </FormProvider>
    );
  };

  render(<Wrapper />);

  const submitButton = screen.getByText('Submit');
  await user.click(submitButton);

  // 1. Verify error message is displayed
  await waitFor(() => {
    expect(screen.getByText('Name is required')).toBeInTheDocument();
  });

  // 2. Verify error handler was called (not success handler)
  expect(onError).toHaveBeenCalled();
  expect(onSubmit).not.toHaveBeenCalled();

  // 3. Verify error handler received correct errors
  expect(onError).toHaveBeenCalledWith(
    expect.objectContaining({
      name: expect.objectContaining({
        type: 'required',
        message: 'Name is required',
      }),
    }),
    expect.anything() // React synthetic event
  );
});
```

## React Testing Library Best Practices

### Rule: Use Test IDs for Stable Element Selection

**Rule**: Use `data-testid` attributes for elements that need to be reliably selected in tests, especially form fields and interactive components.

**Rationale**:
- Text content can change (i18n, design updates)
- CSS selectors are brittle and tied to implementation
- Test IDs provide stable, semantic identifiers
- Test IDs are removed in production builds (when configured)

**Example**:

```typescript
// Component
<TextField
  data-testid="group-form-name"
  label={t('groupManagement.form.name')}
  {...register('name', { required: 'Name is required' })}
/>

// Test
const nameField = screen.getByTestId('group-form-name');
expect(nameField).toBeInTheDocument();
```

### Rule: Use User Event for Interactions

**Rule**: Prefer `@testing-library/user-event` over `fireEvent` for user interactions.

**Rationale**:
- `user-event` simulates real user behavior more accurately
- Handles focus, blur, and other side effects automatically
- More reliable for form interactions

**Example**:

```typescript
// ✅ CORRECT: Using user-event
import userEvent from '@testing-library/user-event';

const user = userEvent.setup();

it('should allow typing in name field', async () => {
  render(<FormComponent />);
  const nameField = screen.getByTestId('form-name');
  await user.type(nameField, 'Test Name');
  expect(nameField).toHaveValue('Test Name');
});
```

## Test Organization

### Rule: Use Descriptive Test Names

**Rule**: Use descriptive test names that clearly state the expected behavior and conditions.

**Rationale**: Makes test failures easier to understand and tests easier to maintain.

**Example**:

```typescript
// ✅ CORRECT: Descriptive names
it('should show validation error when name is empty and field is touched', async () => {
  // ...
});

it('should not show error when name has value', async () => {
  // ...
});

// ❌ INCORRECT: Vague names
it('should validate form', async () => {
  // ...
});

it('test name field', async () => {
  // ...
});
```

### Rule: Use `describe.sequential` and Cleanup for Test Isolation and Shared Context Problems

**Rule**: When tests experience isolation issues or shared context problems (e.g., "multiple elements found" errors when running with the full suite, shared state between tests, or context providers leaking between tests), use `describe.sequential` to run tests sequentially and add `cleanup()` in `afterEach` to ensure proper test isolation.

**Rationale**:
- Concurrent test execution can cause DOM elements from previous tests to interfere
- **Shared context providers (Theme, Auth, i18n, etc.) can leak state between concurrent tests** - this is the primary use case
- `describe.sequential` ensures tests run one at a time, preventing interference and shared context pollution
- `cleanup()` removes rendered components from the DOM after each test, clearing any context state
- This is simpler than scoping every query with `within(container)` or manually resetting context state
- Prevents "multiple elements found" errors when running the full test suite
- Prevents shared context state from affecting subsequent tests

**Primary Use Case: Shared Context Problems**:
- **Theme providers** maintaining theme mode (light/dark) state between tests
- **Auth providers** with cached user/token state persisting across tests
- **i18n/locale providers** with translation state leaking between tests
- **Global mocks** (window properties, localStorage, sessionStorage) shared across tests
- **React Context** that maintains state across renders and test runs
- **Shared mock state** that can interfere between concurrent tests

**When to Use**:
- Tests pass in isolation but fail when run with the full suite
- "Multiple elements found" errors occur
- Test isolation issues with shared DOM state
- **Shared context problems** (Theme providers, Auth providers, i18n state, etc.) - **PRIMARY USE CASE**
- Tests that render components that might persist between test runs
- Tests using context providers that maintain state across renders
- Tests that share mocks or global state that can interfere with each other

**Example**:

```typescript
// ✅ CORRECT: Using describe.sequential with cleanup
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('ComponentName', () => {
  beforeEach(() => {
    // Setup mocks, etc.
  });

  afterEach(() => {
    cleanup();
    // Restore mocks, etc.
  });

  it('should render correctly', () => {
    render(<Component />);
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });
});
```

**❌ Incorrect Approach**:

```typescript
// ❌ INCORRECT: Concurrent execution can cause interference
describe('ComponentName', () => {
  it('should render correctly', () => {
    render(<Component />);
    // May fail with "multiple elements found" when run with full suite
    expect(screen.getByText('Hello')).toBeInTheDocument();
  });
});
```

**Note**: Only use `describe.sequential` when necessary. Most tests can run concurrently. Use this pattern when you experience:
- **Shared context problems** (context providers, global state, mocks interfering between tests) - **PRIMARY USE CASE**
- Test isolation issues (multiple elements found, DOM state leaking)
- Tests that pass individually but fail when run with the full suite

**Key Insight**: The most common reason to use `describe.sequential` is when tests share React Context providers (Theme, Auth, i18n) or global state that persists between test runs. Sequential execution ensures each test starts with a clean context state.

**Common Shared Context Problems This Fixes**:
- Theme providers maintaining state between tests
- Auth providers with cached user/token state
- i18n/locale state persisting between tests
- Global mocks or window properties shared across tests
- React Context state leaking between concurrent test executions

### Rule: Group Related Tests with `describe` Blocks

**Rule**: Use `describe` blocks to organize related tests into logical groups.

**Rationale**: Improves test readability and organization.

**Example**:

```typescript
describe('FormComponent', () => {
  describe('Rendering', () => {
    it('should render name and description fields', () => {
      // ...
    });
  });

  describe('Form validation', () => {
    it('should show validation error when name is empty', async () => {
      // ...
    });
  });

  describe('User interactions', () => {
    it('should allow typing in name field', async () => {
      // ...
    });
  });
});
```

## Test Isolation and Shared Context

### Rule: Use `describe.sequential` for Shared Context Problems

**Rule**: When tests share context providers, global state, or mocks that can interfere with each other, use `describe.sequential` to run tests sequentially and prevent shared context pollution. This is the **primary solution** for fixing shared context problems in tests.

**Problem**: When tests run concurrently, they may share:
- React Context providers (Theme, Auth, i18n) that maintain state
- Global browser APIs (localStorage, sessionStorage, window properties)
- Mock state that persists between test runs
- Component state that leaks between concurrent renders

**Solution**: `describe.sequential` ensures tests run one at a time, and `cleanup()` clears all state after each test.

**Common Shared Context Scenarios**:
- Tests using `AppThemeProvider` that maintain theme state (light/dark mode)
- Tests using `AuthProvider` with cached authentication state (user, token)
- Tests using i18n providers with locale/translation state
- Tests with global mocks (window properties, localStorage, etc.)
- Tests using React Context that maintains state across renders
- Tests sharing mock functions or state that can interfere with each other

**Example**:

```typescript
// ✅ CORRECT: Using describe.sequential for shared context
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Run sequentially to avoid shared context state leaking between tests
describe.sequential('ComponentWithTheme', () => {
  beforeEach(() => {
    // Setup mocks
  });

  afterEach(() => {
    cleanup(); // Clears DOM and any context state
  });

  it('should use theme context', () => {
    render(
      <AppThemeProvider>
        <Component />
      </AppThemeProvider>
    );
    // Test implementation
  });
});
```

**Why This Works**:
- Sequential execution prevents concurrent tests from accessing the same context instance
- `cleanup()` ensures context state is cleared after each test
- Prevents theme mode, auth state, or locale from persisting between tests
- Eliminates race conditions in context updates

**Real-World Examples**:

```typescript
// Example 1: Theme Provider with shared state
describe.sequential('ThemeComponent', () => {
  afterEach(() => {
    cleanup(); // Clears theme mode from localStorage and DOM
  });

  it('should use light theme', () => {
    render(<AppThemeProvider><Component /></AppThemeProvider>);
    // Test light theme
  });

  it('should use dark theme', () => {
    render(<AppThemeProvider><Component /></AppThemeProvider>);
    // Test dark theme - won't be affected by previous test's theme state
  });
});

// Example 2: Auth Provider with cached state
describe.sequential('AuthComponent', () => {
  afterEach(() => {
    cleanup(); // Clears auth token and user from storage
  });

  it('should show login when not authenticated', () => {
    render(<AuthProvider><Component /></AuthProvider>);
    // Test unauthenticated state
  });

  it('should show user when authenticated', () => {
    // Set up auth state
    render(<AuthProvider><Component /></AuthProvider>);
    // Test authenticated state - won't be affected by previous test
  });
});

// Example 3: i18n with locale state
describe.sequential('I18nComponent', () => {
  afterEach(() => {
    cleanup(); // Clears i18n locale state
  });

  it('should use English translations', () => {
    render(<I18nProvider><Component /></I18nProvider>);
    // Test English translations
  });

  it('should use Portuguese translations', () => {
    // Change locale
    render(<I18nProvider><Component /></I18nProvider>);
    // Test Portuguese translations - locale won't leak from previous test
  });
});
```

**When NOT to Use**:
- Simple unit tests without context providers
- Tests that don't share any global state
- Tests that are already properly isolated
- Performance-critical test suites where sequential execution would be too slow

**Performance Consideration**: Sequential execution is slower than concurrent execution. Only use when necessary to fix isolation or shared context issues. The trade-off is worth it for reliable, deterministic tests.

**Summary**: `describe.sequential` + `cleanup()` is the standard solution for fixing shared context problems in React component tests. Use it whenever tests share context providers or global state that can interfere with each other.

## Mocking and Setup

### Rule: Mock External Dependencies

**Rule**: Mock external dependencies (i18n, API clients, etc.) to isolate component behavior.

**Rationale**: Tests should focus on component logic, not external dependencies.

**Example**:

```typescript
// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'groupManagement.form.name': 'Name',
        'groupManagement.form.validation.nameRequired': 'Name is required',
      };
      return translations[key] || key;
    },
  }),
}));
```

### Rule: Create Reusable Test Helpers

**Rule**: Create helper functions for common test setup (rendering components with providers, etc.).

**Rationale**: Reduces test boilerplate and ensures consistent setup.

**Example**:

```typescript
const renderForm = (initialValues?: Partial<FormData>) => {
  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const methods = useForm<FormData>({
      defaultValues: {
        name: initialValues?.name || '',
        description: initialValues?.description || '',
      },
    });

    return (
      <AppThemeProvider>
        <FormProvider {...methods}>{children}</FormProvider>
      </AppThemeProvider>
    );
  };

  return render(
    <Wrapper>
      <FormComponent initialValues={initialValues} />
    </Wrapper>
  );
};
```

## Memory Configuration

### Heap Size Configuration

**Issue**: Large test suites may encounter "JavaScript heap out of memory" errors when running tests.

**Solution**: Test scripts in `package.json` are configured with `NODE_OPTIONS=--max-old-space-size=4096` to allocate 4GB of heap memory for test execution.

**Configuration**:
- All test commands (`test`, `test:watch`, `test:coverage`, etc.) include the memory limit
- This matches the memory configuration used in the Dockerfile for builds
- The Vitest thread pool is automatically configured based on available CPU cores to balance performance and memory usage

**If you still encounter memory issues**:
1. Reduce the number of test threads in `vitest.config.mts` (`maxThreads` option)
2. Increase the heap size further: `NODE_OPTIONS=--max-old-space-size=6144` (6GB)
3. Run tests in smaller batches or specific test files
4. Disable coverage collection if not needed: `pnpm vitest run` (without `--coverage`)

## Running Tests During Development

### Running Specific Test Files

When developing or debugging, you often want to run only specific test files:

```bash
# Run tests matching a pattern (recommended)
pnpm test usePageTitle                    # Matches any file containing "usePageTitle"
pnpm test UserDrawer                      # Matches any test file with "UserDrawer" in path

# Full path pattern
pnpm test "**/usePageTitle.test.tsx"

# Direct Vitest command (alternative)
pnpm vitest run usePageTitle
```

**Important**: Pass the file pattern directly without the `--` separator. Using `pnpm run test -- <pattern>` does not work correctly with Vitest's file filtering.

### Watch Mode

Run tests in watch mode to automatically re-run tests when files change:

```bash
pnpm test:watch
```

## Related Documentation

- [E2E Testing Pattern](./e2e-testing-pattern.md) - End-to-end testing patterns with Playwright
- [Testing Standards](../../05-standards/testing-standards/unit-test-standards.md) - Testing standards and rules
- [Testing Workflow](../../06-workflows/testing-workflow.md) - Testing workflow and process
