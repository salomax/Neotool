import { vi } from 'vitest';

/**
 * Creates a mock Apollo Client for testing.
 * 
 * This helper creates a properly structured Apollo Client mock that works
 * with dynamic imports used in AuthProvider and other components.
 * 
 * @param options - Configuration for the mock
 * @returns Mock Apollo Client object
 * 
 * @example
 * ```tsx
 * const mockMutate = vi.fn();
 * const mockApolloClient = createMockApolloClient({ mutate: mockMutate });
 * 
 * vi.mock('@/lib/graphql/client', () => ({
 *   apolloClient: mockApolloClient,
 *   getApolloClient: () => mockApolloClient,
 * }));
 * ```
 */
export function createMockApolloClient(options?: {
  mutate?: ReturnType<typeof vi.fn>;
  query?: ReturnType<typeof vi.fn>;
  watchQuery?: ReturnType<typeof vi.fn>;
  subscribe?: ReturnType<typeof vi.fn>;
  readQuery?: ReturnType<typeof vi.fn>;
  writeQuery?: ReturnType<typeof vi.fn>;
  resetStore?: ReturnType<typeof vi.fn>;
  clearStore?: ReturnType<typeof vi.fn>;
}) {
  return {
    mutate: options?.mutate || vi.fn(),
    query: options?.query || vi.fn(),
    watchQuery: options?.watchQuery || vi.fn(),
    subscribe: options?.subscribe || vi.fn(),
    readQuery: options?.readQuery || vi.fn(),
    writeQuery: options?.writeQuery || vi.fn(),
    resetStore: options?.resetStore || vi.fn().mockResolvedValue(undefined),
    clearStore: options?.clearStore || vi.fn().mockResolvedValue(undefined),
  };
}

/**
 * Sets up Apollo Client mocks for a test file.
 * 
 * NOTE: This function cannot be used due to Vitest hoisting limitations.
 * Instead, set up mocks directly in your test file using vi.mock() with vi.hoisted().
 * 
 * @deprecated Use vi.mock() with vi.hoisted() directly in your test file instead.
 * 
 * @example
 * ```tsx
 * const { mockMutate } = vi.hoisted(() => ({
 *   mockMutate: vi.fn(),
 * }));
 * 
 * vi.mock('@/lib/graphql/client', () => {
 *   const mockApolloClient = {
 *     mutate: mockMutate,
 *     // ... other methods
 *   };
 *   return {
 *     apolloClient: mockApolloClient,
 *     getApolloClient: () => mockApolloClient,
 *   };
 * });
 * ```
 */
export function setupApolloClientMock(mockClient: ReturnType<typeof createMockApolloClient>) {
  // This function cannot work due to Vitest hoisting - vi.mock() is hoisted to top of file
  // and cannot access function parameters. Use vi.mock() directly in your test file instead.
  throw new Error(
    'setupApolloClientMock cannot be used due to Vitest hoisting. ' +
    'Set up mocks directly using vi.mock() with vi.hoisted() in your test file.'
  );
}

/**
 * Creates a standard GraphQL mutation response structure.
 * 
 * @param operationName - The GraphQL operation name (e.g., 'signIn', 'signUp')
 * @param data - The response data
 * @returns Properly structured GraphQL response
 * 
 * @example
 * ```tsx
 * const response = createGraphQLResponse('signIn', {
 *   token: 'test-token',
 *   user: { id: '1', email: 'test@example.com' }
 * });
 * mockMutate.mockResolvedValue(response);
 * ```
 */
export function createGraphQLResponse<T extends Record<string, any>>(
  operationName: string,
  data: T
) {
  return {
    data: {
      [operationName]: data,
    },
  };
}

/**
 * Creates a standard GraphQL query response structure.
 * 
 * @param operationName - The GraphQL operation name
 * @param data - The response data
 * @returns Properly structured GraphQL response
 */
export function createGraphQLQueryResponse<T extends Record<string, any>>(
  operationName: string,
  data: T
) {
  return {
    data: {
      [operationName]: data,
    },
  };
}

/**
 * Creates a GraphQL error response.
 * 
 * @param errors - Array of error objects or error messages
 * @returns GraphQL error response structure
 * 
 * @example
 * ```tsx
 * const errorResponse = createGraphQLErrorResponse([
 *   { message: 'Invalid credentials', extensions: { code: 'UNAUTHENTICATED' } }
 * ]);
 * mockMutate.mockRejectedValue(errorResponse);
 * ```
 */
export function createGraphQLErrorResponse(
  errors: Array<{ message: string; extensions?: Record<string, any> } | string>
) {
  return {
    errors: errors.map((error) =>
      typeof error === 'string'
        ? { message: error }
        : error
    ),
  };
}
