import {
  ApolloClient,
  type ErrorLike,
  type FetchResult,
  InMemoryCache,
  Observable,
  createHttpLink,
  from,
} from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';
import { CombinedGraphQLErrors } from '@apollo/client/errors';
import { persistCache, AsyncStorageWrapper } from 'apollo3-cache-persist';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import {
  RefreshAccessTokenDocument,
  type RefreshAccessTokenMutation,
  type RefreshAccessTokenMutationVariables,
} from '@invistus/shared-graphql';
import { env } from '@/shared/config/env';
import { buildTraceparent } from './trace-context';
import { classifyAppError, extractErrorCode, isRetryableAppError } from './error-handling';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

export type SessionRefreshUser = {
  id: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  emailVerified: boolean;
  roles: Array<{ id: string; name: string }>;
};

export type SessionRefreshResult = {
  token: string;
  refreshToken: string | null;
  user: SessionRefreshUser;
};

type SessionExpiredListener = () => void;

const sessionExpiredListeners = new Set<SessionExpiredListener>();

let refreshPromise: Promise<SessionRefreshResult | null> | null = null;
let apolloClient: ApolloClient | undefined;

// HTTP Link
const httpLink = createHttpLink({
  uri: env.graphqlEndpoint,
});

function notifySessionExpired() {
  sessionExpiredListeners.forEach((listener) => {
    listener();
  });
}

export function subscribeToSessionExpired(listener: SessionExpiredListener) {
  sessionExpiredListeners.add(listener);

  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

function isUnauthenticatedError(error: ErrorLike): boolean {
  return classifyAppError(error).category === 'auth';
}

async function persistRefreshedSession(session: SessionRefreshResult) {
  await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, session.token);

  if (session.refreshToken) {
    await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, session.refreshToken);
    return;
  }

  await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
}

export async function refreshAccessToken(): Promise<SessionRefreshResult | null> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const refreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);

    if (!refreshToken) {
      notifySessionExpired();
      return null;
    }

    const tempClient = new ApolloClient({
      link: httpLink,
      cache: new InMemoryCache(),
    });

    try {
      const { data } = await tempClient.mutate<
        RefreshAccessTokenMutation,
        RefreshAccessTokenMutationVariables
      >({
        mutation: RefreshAccessTokenDocument,
        variables: {
          input: {
            refreshToken,
          },
        },
      });

      const refreshedSession = data?.refreshAccessToken;

      if (!refreshedSession) {
        notifySessionExpired();
        return null;
      }

      const result: SessionRefreshResult = {
        token: refreshedSession.token,
        refreshToken: refreshedSession.refreshToken ?? null,
        user: {
          id: refreshedSession.user.id,
          email: refreshedSession.user.email,
          displayName: refreshedSession.user.displayName ?? null,
          avatarUrl: refreshedSession.user.avatarUrl ?? null,
          emailVerified: refreshedSession.user.emailVerified,
          roles: refreshedSession.user.roles.map((role) => ({
            id: role.id,
            name: role.name,
          })),
        },
      };

      await persistRefreshedSession(result);
      return result;
    } catch (error) {
      console.error('Failed to refresh access token:', error);
      notifySessionExpired();
      return null;
    } finally {
      await tempClient.clearStore().catch(() => undefined);
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

// Auth Link - Add JWT token to requests
const authLink = setContext(async (_, { headers }) => {
  try {
    const token = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
    const existingTraceparent = headers?.traceparent || headers?.Traceparent;
    return {
      headers: {
        ...headers,
        authorization: token ? `Bearer ${token}` : '',
        ...(existingTraceparent ? {} : { traceparent: buildTraceparent() }),
      },
    };
  } catch (error) {
    console.error('Failed to get access token:', error);
    return { headers };
  }
});

// Error Link - Handle GraphQL and network errors
const errorLink = onError(({ error, operation, forward }) => {
  if (CombinedGraphQLErrors.is(error)) {
    error.errors.forEach(({ message, locations, path }) => {
      console.error(
        `[GraphQL error]: Message: ${message}, Location: ${locations}, Path: ${path}`
      );
    });
  } else {
    console.error(`[Network error ${operation.operationName}]: ${error.message}`);
  }

  const errorDescriptor = classifyAppError(error);

  if (errorDescriptor.category !== 'auth') {
    return undefined;
  }

  if (operation.getContext().retryAfterRefresh) {
    notifySessionExpired();
    return undefined;
  }

  return new Observable<FetchResult<Record<string, unknown>>>((observer) => {
    let retrySubscription: { unsubscribe(): void } | undefined;

    void refreshAccessToken()
      .then((session) => {
        if (!session) {
          observer.error(new Error('AUTH_SESSION_EXPIRED'));
          return;
        }

        operation.setContext(({ headers = {} }: { headers?: Record<string, string> }) => ({
          headers: {
            ...headers,
            authorization: `Bearer ${session.token}`,
          },
          retryAfterRefresh: true,
        }));

        retrySubscription = forward(operation).subscribe({
          next: (result: FetchResult<Record<string, unknown>>) => observer.next(result),
          error: (error: unknown) => observer.error(error),
          complete: () => observer.complete(),
        });
      })
      .catch((error) => observer.error(error));

    return () => {
      retrySubscription?.unsubscribe();
    };
  });
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
      return !!error && isRetryableAppError(error) && extractErrorCode(error) !== 'GRAPHQL_VALIDATION_FAILED';
    },
  },
});

// In-memory cache
const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {},
    },
    User: {
      keyFields: ['id'],
    },
  },
});

/**
 * Initialize Apollo Client with cache persistence
 */
export async function initializeApolloClient(): Promise<ApolloClient> {
  if (apolloClient) {
    return apolloClient;
  }

  try {
    await persistCache({
      cache,
      storage: new AsyncStorageWrapper(AsyncStorage),
      maxSize: 1048576 * 10,
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
  }
}
