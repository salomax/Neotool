import {
  ApolloClient,
  InMemoryCache,
  createHttpLink,
  from,
  NormalizedCacheObject,
} from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';
import { persistCache, AsyncStorageWrapper } from 'apollo3-cache-persist';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import NetInfo from '@react-native-community/netinfo';

// HTTP Link
const httpLink = createHttpLink({
  uri: process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT || 'http://localhost:4000/graphql',
});

// Auth Link - Add JWT token to requests
const authLink = setContext(async (_, { headers }) => {
  try {
    const token = await SecureStore.getItemAsync('accessToken');
    return {
      headers: {
        ...headers,
        authorization: token ? `Bearer ${token}` : '',
      },
    };
  } catch (error) {
    console.error('Failed to get access token:', error);
    return { headers };
  }
});

// Error Link - Handle GraphQL and network errors
const errorLink = onError(({ graphQLErrors, networkError, operation }) => {
  if (graphQLErrors) {
    graphQLErrors.forEach(({ message, locations, path, extensions }) => {
      console.error(
        `[GraphQL error]: Message: ${message}, Location: ${locations}, Path: ${path}`
      );

      // Handle unauthorized errors
      if (extensions?.code === 'UNAUTHENTICATED') {
        // TODO: Trigger logout or token refresh
        console.warn('Unauthenticated - user should be logged out');
      }
    });
  }

  if (networkError) {
    console.error(`[Network error ${operation.operationName}]: ${networkError.message}`);
  }
});

// Retry Link - Retry failed requests
const retryLink = new RetryLink({
  delay: {
    initial: 300,
    max: 3000,
    jitter: true,
  },
  attempts: {
    max: 5,
    retryIf: (error, _operation) => {
      // Don't retry on authentication errors
      return !!error && !error.message.includes('401') && !error.message.includes('UNAUTHENTICATED');
    },
  },
});

// In-memory cache
const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        // Add cache policies here
      },
    },
    User: {
      keyFields: ['id'],
    },
  },
});

let apolloClient: ApolloClient<NormalizedCacheObject>;

/**
 * Initialize Apollo Client with cache persistence
 */
export async function initializeApolloClient(): Promise<ApolloClient<NormalizedCacheObject>> {
  if (apolloClient) {
    return apolloClient;
  }

  try {
    // Persist cache to AsyncStorage
    await persistCache({
      cache,
      storage: new AsyncStorageWrapper(AsyncStorage),
      maxSize: 1048576 * 10, // 10 MB
      debug: __DEV__,
    });
  } catch (error) {
    console.error('Error persisting cache:', error);
  }

  apolloClient = new ApolloClient({
    link: from([retryLink, errorLink, authLink, httpLink]),
    cache,
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
        errorPolicy: 'all',
      },
      query: {
        fetchPolicy: 'cache-first',
        errorPolicy: 'all',
      },
      mutate: {
        errorPolicy: 'all',
      },
    },
  });

  // Listen to network status changes
  NetInfo.addEventListener((state) => {
    if (state.isConnected && state.isInternetReachable) {
      console.log('Network connected - refetching active queries');
      apolloClient.refetchQueries({ include: 'active' }).catch((error) => {
        console.error('Error refetching queries:', error);
      });
    }
  });

  return apolloClient;
}

/**
 * Get Apollo Client instance
 */
export function getApolloClient(): ApolloClient<NormalizedCacheObject> {
  if (!apolloClient) {
    throw new Error('Apollo Client not initialized. Call initializeApolloClient() first.');
  }
  return apolloClient;
}

/**
 * Clear Apollo Client cache
 */
export async function clearApolloCache(): Promise<void> {
  if (apolloClient) {
    await apolloClient.clearStore();
    console.log('Apollo cache cleared');
  }
}
