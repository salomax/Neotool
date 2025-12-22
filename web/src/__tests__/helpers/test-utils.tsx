import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach } from 'vitest';

/**
 * Creates a test wrapper with QueryClient that automatically clears cache after each test.
 * This prevents memory leaks from cache accumulation across tests.
 * 
 * @param options - Optional QueryClient configuration
 * @returns A React component wrapper for tests
 * 
 * @example
 * ```tsx
 * const wrapper = createTestQueryWrapper();
 * const { result } = renderHook(() => useMyHook(), { wrapper });
 * ```
 */
export function createTestQueryWrapper(options?: {
  defaultOptions?: {
    queries?: {
      retry?: boolean;
      cacheTime?: number;
      staleTime?: number;
    };
    mutations?: {
      retry?: boolean;
    };
  };
}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        cacheTime: 0, // Disable cache to prevent memory leaks
        staleTime: 0, // Always consider data stale
        ...options?.defaultOptions?.queries,
      },
      mutations: {
        retry: false,
        ...options?.defaultOptions?.mutations,
      },
    },
  });

  // Clear cache after each test to prevent memory leaks
  afterEach(() => {
    queryClient.clear();
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  };

  Wrapper.displayName = 'TestQueryWrapper';

  return Wrapper;
}

/**
 * Creates a test wrapper with both QueryClient and Apollo Client providers.
 * Automatically clears both caches after each test.
 * 
 * @param options - Optional configuration
 * @returns A React component wrapper for tests
 * 
 * @example
 * ```tsx
 * const wrapper = createTestProvidersWrapper();
 * const { result } = renderHook(() => useMyHook(), { wrapper });
 * ```
 */
export async function createTestProvidersWrapper(options?: {
  queryClientOptions?: Parameters<typeof createTestQueryWrapper>[0];
  apolloClientMocks?: any[];
}) {
  const QueryWrapper = createTestQueryWrapper(options?.queryClientOptions);

  // Dynamically import Apollo Provider to avoid issues in test environments
  let ApolloProvider: React.ComponentType<any> | null = null;
  let MockedProvider: React.ComponentType<any> | null = null;
  let apolloClient: any = null;

  try {
    const apolloModule = await import('@apollo/client/react');
    ApolloProvider = apolloModule.ApolloProvider;

    // Try to use MockedProvider if mocks are provided
    if (options?.apolloClientMocks) {
      const testingModule = await import('@apollo/client/testing');
      MockedProvider = testingModule.MockedProvider;
    } else {
      // Use real Apollo Client
      const { getApolloClient } = await import('@/lib/graphql/client');
      apolloClient = getApolloClient();
    }
  } catch (error) {
    // Apollo Client not available in this test environment
    // Return just QueryClient wrapper
    return QueryWrapper;
  }

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const innerContent = <QueryWrapper>{children}</QueryWrapper>;

    if (MockedProvider && options?.apolloClientMocks) {
      return (
        <MockedProvider mocks={options.apolloClientMocks} addTypename={false}>
          {innerContent}
        </MockedProvider>
      );
    }

    if (ApolloProvider && apolloClient) {
      return <ApolloProvider client={apolloClient}>{innerContent}</ApolloProvider>;
    }

    return innerContent;
  };

  Wrapper.displayName = 'TestProvidersWrapper';

  return Wrapper;
}
