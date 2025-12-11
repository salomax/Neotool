// Apollo Client imports for GraphQL operations
import { ApolloClient, InMemoryCache, HttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { relayStylePagination } from '@apollo/client/utilities';
import { getAuthToken, clearAuthStorage, isAuthenticationError } from '@/shared/utils/auth';

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

// Error link to handle authentication errors globally
const errorLink = onError(({ graphQLErrors, networkError }) => {
  // Check GraphQL errors
  if (graphQLErrors?.some(({ message }) => isAuthenticationError(null, message))) {
    clearAuthStorage();
  }

  // Check network errors (401 status codes)
  if (networkError && isAuthenticationError(networkError)) {
    clearAuthStorage();
  }
});

function createApolloClient() {
  // Create Apollo Client with proper configuration
  return new ApolloClient({
    link: from([errorLink, authLink, httpLink]),
    // Cache: stores the results of GraphQL operations in memory
    // InMemoryCache provides automatic normalization and caching of query results
    // This improves performance by avoiding duplicate requests and enabling optimistic updates
    cache: new InMemoryCache(),
    ssrMode: typeof window === 'undefined',
    defaultOptions: {
      // Prevent automatic refetches to avoid infinite loops
      watchQuery: {
        fetchPolicy: 'network-only', // Always fetch from network, no cache
        errorPolicy: 'all', // Return both data and errors
      },
      query: {
        fetchPolicy: 'network-only', // Always fetch from network, no cache
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