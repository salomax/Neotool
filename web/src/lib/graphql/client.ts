// Apollo Client imports for GraphQL operations
import { ApolloClient, InMemoryCache, HttpLink, ApolloLink, Observable, type Operation, type FetchResult, type FetchPolicy } from '@apollo/client';
import { SetContextLink } from '@apollo/client/link/context';
import { ErrorLink } from '@apollo/client/link/error';
import { CombinedGraphQLErrors, CombinedProtocolErrors } from '@apollo/client/errors';
import { getAuthToken, clearAuthStorage, isAuthenticationError, getRefreshToken, updateAuthToken, updateAuthUser } from '@/shared/utils/auth';
import { logger } from '@/shared/utils/logger';
import { getRuntimeConfig } from '@/shared/config/runtime-config';

/**
 * Get GraphQL endpoint URL from runtime config or environment
 * Supports both runtime injection (production) and build-time config (development)
 */
function getGraphQLEndpoint(): string {
  // Try runtime config first (for production with Vault/Kubernetes injection)
  const runtimeConfig = getRuntimeConfig();
  if (runtimeConfig.graphqlEndpoint) {
    return runtimeConfig.graphqlEndpoint;
  }

  // Fall back to build-time environment variable (for development)
  return process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql';
}

// HTTP Link configuration - defines how Apollo Client communicates with the GraphQL server
const httpLink = new HttpLink({
  uri: getGraphQLEndpoint(),
});

// Auth link to add token to requests
const authLink = new SetContextLink((prevContext, operation) => {
  const token = getAuthToken();
  
  return {
    headers: {
      ...prevContext.headers,
      ...(token && { authorization: `Bearer ${token}` }),
    },
  };
});

// Track if we're currently refreshing to avoid multiple refresh attempts
let isRefreshing = false;
let refreshPromise: Promise<string | null> | null = null;

// Singleton cache for temporary client used during token refresh
// This prevents memory leaks from creating new InMemoryCache instances on each refresh
const tempCache = new InMemoryCache();

/**
 * Attempts to refresh the access token using the refresh token
 * Returns the new access token if successful, null otherwise
 */
async function refreshAccessToken(): Promise<string | null> {
  // If already refreshing, wait for that promise
  if (isRefreshing && refreshPromise) {
    return refreshPromise;
  }

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return null;
  }

  isRefreshing = true;
  refreshPromise = (async () => {
    try {
      // Dynamic import to avoid circular dependencies
      const { REFRESH_ACCESS_TOKEN } = await import('@/lib/graphql/operations/auth');
      
      // Create a temporary client without auth link to avoid infinite loop
      // Reuses singleton tempCache to prevent memory leaks
      const tempClient = new ApolloClient({
        link: httpLink,
        cache: tempCache,
        ssrMode: typeof window === 'undefined',
      });

      const result = await tempClient.mutate<{ refreshAccessToken: { token: string; user: any } }>({
        mutation: REFRESH_ACCESS_TOKEN,
        variables: {
          input: {
            refreshToken,
          },
        },
      });

      if (result.data?.refreshAccessToken) {
        const { token: newToken, user } = result.data.refreshAccessToken;
        
        // Update token and user in storage
        updateAuthToken(newToken);
        updateAuthUser(user);
        
        // Dispatch custom event to notify AuthProvider
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('auth:token-refreshed', { 
            detail: { token: newToken, user } 
          }));
        }
        
        return newToken;
      }
      
      logger.error('[Apollo] refreshAccessToken mutation returned no data');
      return null;
    } catch (error) {
      logger.error('[Apollo] refreshAccessToken mutation failed', error);
      // Refresh failed - clear storage and redirect
      clearAuthStorage();
      if (typeof window !== 'undefined') {
        window.location.href = '/signin';
      }
      return null;
    } finally {
      isRefreshing = false;
      refreshPromise = null;
      // Reset temp cache to free memory after each refresh attempt
      tempCache.reset();
    }
  })();

  return refreshPromise;
}

// Link to convert GraphQL errors in successful responses into actual errors
const responseErrorLink = new ApolloLink((operation: Operation, forward: (operation: Operation) => Observable<FetchResult>) => {
  return new Observable<FetchResult>((observer) => {
    const subscription = forward(operation).subscribe({
      next: (result: FetchResult) => {
        if (result.errors && result.errors.length > 0) {
          const error = new Error(result.errors.map((e: { message: string }) => e.message).join(', '));
          (error as any).graphQLErrors = result.errors;
          (error as any).response = {
            data: result.data,
            errors: result.errors,
          };
          observer.error(error);
          return;
        }
        observer.next(result);
      },
      error: (err: Error) => observer.error(err),
      complete: () => observer.complete(),
    });
    return () => subscription.unsubscribe();
  });
});

// Error link to handle authentication errors globally
// Using Apollo Client 4.0 ErrorLink class (onError is deprecated)
const errorLink = new ErrorLink(({ error, operation, forward }) => {
  if (!error) {
    logger.error('[Apollo] errorLink triggered without error', {
      operationName: operation.operationName,
      context: operation.getContext(),
    });
    return;
  }

  // Extract errors based on type (Apollo Client 4.0 API)
  let graphQLErrors: Array<{ message: string; extensions?: any }> = [];
  let protocolErrors: Array<{ message: string; extensions?: any }> = [];
  let networkError: Error | null = null;

  if (CombinedGraphQLErrors.is(error)) {
    graphQLErrors = [...error.errors];
  } else if (CombinedProtocolErrors.is(error)) {
    protocolErrors = [...error.errors];
  } else if (error instanceof Error) {
    networkError = error;
  }

  // Combine all errors for auth checking
  const allErrors = [...graphQLErrors, ...protocolErrors];
  const hasErrors = allErrors.length > 0 || networkError !== null;

  if (!hasErrors) {
    logger.error('[Apollo] errorLink triggered without error details', {
      operationName: operation.operationName,
      context: operation.getContext(),
    });
    return;
  }

  // Comprehensive error detection: check GraphQL errors, protocol errors, and network errors
  const isAuthError = 
    // Check GraphQL/protocol errors with message matching and error extensions
    allErrors.some(({ message, extensions }) => 
      isAuthenticationError(null, message) ||
      extensions?.code === 'UNAUTHENTICATED' ||
      extensions?.code === 'UNAUTHORIZED'
    ) ||
    // Check network errors (401 status, auth-related messages)
    (networkError && isAuthenticationError(networkError));

  if (isAuthError) {
    // Check if this is a retry after a refresh (to prevent infinite loops)
    const isRetry = operation.getContext().retryAfterRefresh === true;
    if (isRetry) {
      logger.error('[Apollo] Auth error on retry after refresh, redirecting to signin');
      clearAuthStorage();
      if (typeof window !== 'undefined') {
        window.location.href = '/signin';
      }
      return;
    }
    
    const refreshToken = getRefreshToken();
    
    // If we have a refresh token, try to refresh the access token
    if (refreshToken) {
      // Return Observable - properly handle the retry
      return new Observable((observer) => {
        refreshAccessToken()
          .then((newToken) => {
            if (newToken) {
              // Update operation context with new token and mark as retry
              const oldHeaders = operation.getContext().headers;
              operation.setContext({
                headers: {
                  ...oldHeaders,
                  authorization: `Bearer ${newToken}`,
                },
                retryAfterRefresh: true, // Mark as retry to prevent infinite loops
              });
              // Retry the original request - forward returns an Observable
              const retryObservable = forward(operation);
              retryObservable.subscribe({
                next: (result) => observer.next(result),
                error: (err) => observer.error(err),
                complete: () => observer.complete(),
              });
            } else {
              logger.error('[Apollo] Token refresh did not return a new token, redirecting to signin');
              // Refresh failed - redirect is already handled in refreshAccessToken
              observer.error(new Error('Token refresh failed'));
            }
          })
          .catch((err) => {
            logger.error('[Apollo] Token refresh promise rejected', err);
            observer.error(err);
          });
      });
    } else {
      // No refresh token - clear storage and redirect
      clearAuthStorage();
      if (typeof window !== 'undefined') {
        window.location.href = '/signin';
      }
      // Return undefined to stop the chain
      return;
    }
  }
  
  // Not an auth error - let it propagate
  return;
});

function createApolloClient() {
  // Create Apollo Client with proper configuration
  return new ApolloClient({
    link: ApolloLink.from([errorLink, responseErrorLink, authLink, httpLink]),
    // Cache: stores the results of GraphQL operations in memory
    // InMemoryCache provides automatic normalization and caching of query results
    // This improves performance by avoiding duplicate requests and enabling optimistic updates
    cache: new InMemoryCache({
      // Limit cache size to prevent unbounded memory growth
      typePolicies: {
        Query: {
          fields: {
            // Configure roles query with stable key arguments for deduplication
            roles: {
              keyArgs: ['first', 'after', 'query', 'orderBy'],
              merge(existing, incoming) {
                // For pagination, always return incoming data (replace strategy)
                // This ensures we get fresh data while still benefiting from query deduplication
                return incoming;
              },
            },
            // Configure users query with stable key arguments for deduplication
            users: {
              keyArgs: ['first', 'after', 'query', 'orderBy'],
              merge(existing, incoming) {
                return incoming;
              },
            },
            // Configure groups query with stable key arguments for deduplication
            groups: {
              keyArgs: ['first', 'after', 'query', 'orderBy'],
              merge(existing, incoming) {
                return incoming;
              },
            },
            // Configure permissions query with stable key arguments for deduplication
            permissions: {
              keyArgs: ['first', 'after', 'query'],
              merge(existing, incoming) {
                return incoming;
              },
            },
          },
        },
      },
    }),
    ssrMode: typeof window === 'undefined',
    defaultOptions: {
      // Use cache-and-network for better deduplication while still getting fresh data
      // This allows Apollo to deduplicate identical queries while still fetching from network
      watchQuery: {
        fetchPolicy: 'cache-and-network' as FetchPolicy, // Check cache first, then fetch from network
        errorPolicy: 'none', // Treat GraphQL errors as errors so global handlers can react
      },
      query: {
        fetchPolicy: 'cache-and-network' as FetchPolicy, // Check cache first, then fetch from network
        errorPolicy: 'none', // Prevent silent failures when backend returns errors
      },
    },
  });
}

// Apollo Client instance - the main GraphQL client for the application
// Use singleton pattern to ensure only one instance is created
let apolloClientInstance: ReturnType<typeof createApolloClient> | null = null;

// Get or create Apollo Client instance (singleton pattern)
export function getApolloClient() {
  // Always create a new client for SSR, reuse for client-side
  if (typeof window === 'undefined') {
    return createApolloClient();
  }
  
  if (!apolloClientInstance) {
    apolloClientInstance = createApolloClient();
  }
  return apolloClientInstance;
}

// Export the client instance - lazy initialization to avoid SSR issues
// This ensures the client is only created when actually needed on the client side
export const apolloClient = typeof window !== 'undefined' ? getApolloClient() : (() => {
  // Return a placeholder that will be replaced on client side
  // This prevents errors during SSR while ensuring client-side works
  return createApolloClient();
})();
