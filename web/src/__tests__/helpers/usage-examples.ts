/**
 * Usage examples for test helper utilities
 * 
 * These examples show how to use the helper utilities to fix common test issues.
 */

// ============================================================================
// Example 1: Fixing "Found multiple elements" errors
// ============================================================================

import { safeQueries, getFirstByTestId } from '@/__tests__/helpers';
import { screen } from '@testing-library/react';

// ❌ Before (fails when component renders multiple times):
// const button = screen.getByTestId('my-button');

// ✅ After (handles multiple renders):
const button1 = safeQueries.getByTestId('my-button');
// OR
const button2 = getFirstByTestId('my-button');

// For clicking/interacting:
import userEvent from '@testing-library/user-event';
const user = userEvent.setup();
const buttons = screen.getAllByTestId('my-button');
await user.click(buttons[0]!); // Use first instance

// ============================================================================
// Example 2: Apollo Client mocking (when hoisting works)
// ============================================================================

import { createGraphQLResponse } from '@/__tests__/helpers';

// In test:
// Example: Create a mock mutation function
// const mockMutate = vi.fn();
// mockMutate.mockResolvedValue(
//   createGraphQLResponse('signIn', {
//     token: 'test-token',
//     user: { id: '1', email: 'test@example.com' }
//   })
// );

// ============================================================================
// Example 3: Storage mocking
// ============================================================================

import { setupStorageMocks } from '@/__tests__/helpers';

describe('MyComponent', () => {
  let localStorageMock: ReturnType<typeof import('@/__tests__/helpers/storage-helpers').createMockStorage>;
  let sessionStorageMock: ReturnType<typeof import('@/__tests__/helpers/storage-helpers').createMockStorage>;

  beforeEach(() => {
    const mocks = setupStorageMocks();
    localStorageMock = mocks.localStorageMock;
    sessionStorageMock = mocks.sessionStorageMock;
  });

  it('stores data', () => {
    localStorageMock.setItem('key', 'value');
    expect(localStorageMock.getItem('key')).toBe('value');
  });
});

// ============================================================================
// Example 4: Complete test file pattern
// ============================================================================

/*
import { safeQueries, setupStorageMocks } from '@/__tests__/helpers';
import { render, screen } from '@testing-library/react';

describe('MyComponent', () => {
  beforeEach(() => {
    setupStorageMocks();
  });

  it('renders button', () => {
    render(<MyComponent />);
    const button = safeQueries.getByTestId('my-button');
    expect(button).toBeInTheDocument();
  });
});
*/
