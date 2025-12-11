// Apollo Client imports for GraphQL operations
import { ApolloClient, InMemoryCache, HttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { relayStylePagination } from '@apollo/client/utilities';
import { getAuthToken, clearAuthStorage, isAuthenticationError, getRefreshToken, updateAuthToken, updateAuthUser } from '@/shared/utils/auth';

// HTTP Link configuration - defines how Apollo Client communicates with the GraphQL server
const httpLink = new HttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
});

// Auth link to add token to requests
const authLink = setContext((_, { headers }) => {
  const token = getAuthToken();
  
  return {
    headers: {
      ...headers,
      ...(token && { authorization: `Bearer ${token}` }),
    },
  };
});

// Track if we're currently refreshing to avoid multiple refresh attempts
let isRefreshing = false;
let refreshPromise: Promise<string | null> | null = null;

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
      const tempClient = new ApolloClient({
        link: httpLink,
        cache: new InMemoryCache(),
        ssrMode: typeof window === 'undefined',
      });

      const result = await tempClient.mutate({
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
      
      return null;
    } catch (error) {
      // Refresh failed - clear storage and redirect
      clearAuthStorage();
      if (typeof window !== 'undefined') {
        window.location.href = '/signin';
      }
      return null;
    } finally {
      isRefreshing = false;
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

// Error link to handle authentication errors globally
const errorLink = onError(({ graphQLErrors, networkError, operation, forward }) => {
  // Comprehensive error detection: check GraphQL errors, network errors, and error extensions
  const isAuthError = 
    // Check GraphQL errors with message matching and error extensions
    graphQLErrors?.some(({ message, extensions }) => 
      isAuthenticationError(null, message) ||
      extensions?.code === 'UNAUTHENTICATED' ||
      extensions?.code === 'UNAUTHORIZED'
    ) ||
    // Check network errors (401 status, auth-related messages)
    (networkError && isAuthenticationError(networkError));

  if (isAuthError) {
    const refreshToken = getRefreshToken();
    
    // If we have a refresh token, try to refresh the access token
    if (refreshToken) {
      // Return Promise - Apollo Client v4 automatically converts it to Observable
      // and handles Observables returned from the promise correctly
      return refreshAccessToken().then((newToken) => {
        if (newToken) {
          // Update operation context with new token
          const oldHeaders = operation.getContext().headers;
          operation.setContext({
            headers: {
              ...oldHeaders,
              authorization: `Bearer ${newToken}`,
            },
          });
          // Retry the original request - forward returns an Observable
          // Apollo Client will handle the Observable correctly
          return forward(operation);
        }
        // Refresh failed - return undefined to stop the chain
        // The redirect is already handled in refreshAccessToken
        return;
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
    link: from([errorLink, authLink, httpLink]),
    // Cache: stores the results of GraphQL operations in memory
    // InMemoryCache provides automatic normalization and caching of query results
    // This improves performance by avoiding duplicate requests and enabling optimistic updates
    cache: new InMemoryCache({
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
        fetchPolicy: 'cache-and-network', // Check cache first, then fetch from network
        errorPolicy: 'all', // Return both data and errors
      },
      query: {
        fetchPolicy: 'cache-and-network', // Check cache first, then fetch from network
        errorPolicy: 'all', // Return both data and errors
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