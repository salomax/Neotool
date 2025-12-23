# Test Helper Utilities

This directory contains reusable test helper utilities for common testing patterns.

## Available Helpers

### Query Helpers (`query-helpers.ts`)

Handles "Found multiple elements" errors that occur when components render multiple times in tests.

**Usage:**
```tsx
import { safeQueries } from '@/__tests__/helpers';

// Instead of:
const button = screen.getByTestId('my-button'); // ❌ Fails if multiple renders

// Use:
const button = safeQueries.getByTestId('my-button'); // ✅ Handles multiple renders
expect(button).toBeInTheDocument();
```

**Available helpers:**
- `safeQueries.getByTestId(testId)`
- `safeQueries.getByLabelText(text)`
- `safeQueries.getByRole(role, options?)`
- `safeQueries.getByText(text)`
- `safeQueries.getByPlaceholderText(text)`
- `safeQueries.getByDisplayValue(value)`
- `safeQueries.getByTitle(title)`
- `safeQueries.getByAltText(text)`

### Apollo Client Mock Helpers (`apollo-mock-helpers.ts`)

Creates properly structured Apollo Client mocks for testing.

**Usage:**
```tsx
import { createGraphQLResponse } from '@/__tests__/helpers';

// In test setup (using vi.hoisted):
const { mockMutate } = vi.hoisted(() => {
  const mockMutate = vi.fn();
  return { mockMutate };
});

// Create Apollo Client mock
vi.mock('@/lib/graphql/client', () => {
  const mockApolloClient = {
    mutate: mockMutate,
    query: vi.fn(),
    // ... other methods
  };
  return {
    apolloClient: mockApolloClient,
    getApolloClient: () => mockApolloClient,
  };
});

// In tests:
mockMutate.mockResolvedValue(
  createGraphQLResponse('signIn', {
    token: 'test-token',
    user: { id: '1', email: 'test@example.com' }
  })
);
```

### Storage Helpers (`storage-helpers.ts`)

Creates mock localStorage and sessionStorage for testing.

**Usage:**
```tsx
import { setupStorageMocks } from '@/__tests__/helpers';

describe('MyComponent', () => {
  let localStorageMock: ReturnType<typeof import('@/__tests__/helpers/storage-helpers').createMockStorage>;
  let sessionStorageMock: ReturnType<typeof import('@/__tests__/helpers/storage-helpers').createMockStorage>;

  beforeEach(() => {
    const mocks = setupStorageMocks();
    localStorageMock = mocks.localStorageMock;
    sessionStorageMock = mocks.sessionStorageMock;
  });

  it('stores data in localStorage', () => {
    localStorageMock.setItem('key', 'value');
    expect(localStorageMock.getItem('key')).toBe('value');
  });
});
```

## Migration Guide

### Fixing "Found multiple elements" errors

**Before:**
```tsx
const button = screen.getByTestId('my-button');
```

**After:**
```tsx
import { safeQueries } from '@/__tests__/helpers';
const button = safeQueries.getByTestId('my-button');
```

Or use the individual helpers:
```tsx
import { getFirstByTestId } from '@/__tests__/helpers';
const button = getFirstByTestId('my-button');
```

### Fixing Apollo Client mocks

**Before:**
```tsx
mockMutate.mockResolvedValue({
  data: {
    signIn: {
      token: 'test',
      user: {...}
    }
  }
});
```

**After:**
```tsx
import { createGraphQLResponse } from '@/__tests__/helpers';
mockMutate.mockResolvedValue(
  createGraphQLResponse('signIn', {
    token: 'test',
    user: {...}
  })
);
```

### Fixing storage mocks

**Before:**
```tsx
const createStorageMock = () => {
  const storage = new Map();
  return {
    getItem: vi.fn((key) => storage.get(key) || null),
    setItem: vi.fn((key, value) => storage.set(key, value)),
    // ...
  };
};
```

**After:**
```tsx
import { setupStorageMocks } from '@/__tests__/helpers';
const { localStorageMock, sessionStorageMock } = setupStorageMocks();
```
