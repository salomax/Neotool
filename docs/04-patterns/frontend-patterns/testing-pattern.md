---
title: Frontend Testing Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [testing, frontend, react, react-hook-form, vitest, testing-library]
ai_optimized: true
search_keywords: [testing, frontend, react, react-hook-form, vitest, testing-library, unit-tests, integration-tests]
related:
  - 05-standards/testing-standards/unit-test-standards.md
  - 06-workflows/testing-workflow.md
---

# Frontend Testing Pattern

> **Purpose**: Standard patterns for testing React components and frontend code, including form validation testing with React Hook Form.

## Overview

This document covers best practices for:
- Testing React components with React Testing Library
- Testing form validation with React Hook Form
- Avoiding common testing pitfalls
- Writing maintainable and reliable frontend tests

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
