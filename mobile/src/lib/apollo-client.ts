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
const errorLink = onError((errorResponse: any) => {
  const { graphQLErrors, networkError, operation } = errorResponse;
  if (graphQLErrors) {
    graphQLErrors.forEach(({ message, locations, path, extensions }: any) => {
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

let apolloClient: ApolloClient;

/**
 * Initialize Apollo Client with cache persistence
 */
export async function initializeApolloClient(): Promise<ApolloClient> {
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
    console.error('Error restoring Apollo cache:', error);
  }

  apolloClient = new ApolloClient({
    link: from([retryLink, errorLink, authLink, httpLink]),
    cache,
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
      },
    },
  });

  return apolloClient;
}

/**
 * Get Apollo Client instance
 */
export function getApolloClient(): ApolloClient {
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
