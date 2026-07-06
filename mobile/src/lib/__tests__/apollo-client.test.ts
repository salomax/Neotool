import {
  CurrentUserDocument,
  RequestPasswordResetDocument,
} from '@invistus/shared-graphql';

jest.mock('../cache-persistence', () => ({
  restorePersistedCache: jest.fn().mockResolvedValue(undefined),
  attachCachePersistence: jest.fn().mockReturnValue(jest.fn()),
  clearPersistedCache: jest.fn().mockResolvedValue(undefined),
}));

const currentUserPayload = {
  currentUser: {
    id: 'user-1',
    email: 'user@example.com',
    displayName: 'Test User',
    avatarUrl: null,
    emailVerified: true,
    roles: [{ id: 'role-1', name: 'USER' }],
  },
};

const refreshedSessionPayload = {
  refreshAccessToken: {
    token: 'refreshed-access-token',
    refreshToken: 'refreshed-refresh-token',
    user: {
      id: 'user-1',
      email: 'user@example.com',
      displayName: 'Test User',
      avatarUrl: null,
      emailVerified: true,
      roles: [{ id: 'role-1', name: 'USER' }],
    },
  },
};

function jsonResponse(payload: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  );
}

function getOperationDetails(init?: RequestInit) {
  const body = JSON.parse(String(init?.body));
  const query = typeof body.query === 'string' ? body.query : '';

  return {
    operationName: body.operationName as string | undefined,
    query,
  };
}

function loadApolloClientModule() {
  jest.resetModules();
  let apolloClientModule: typeof import('../apollo-client') | undefined;
  let secureStoreModule: jest.Mocked<typeof import('expo-secure-store')> | undefined;

  jest.isolateModules(() => {
    secureStoreModule = require('expo-secure-store') as jest.Mocked<
      typeof import('expo-secure-store')
    >;
    apolloClientModule = require('../apollo-client') as typeof import('../apollo-client');
  });

  if (!apolloClientModule || !secureStoreModule) {
    throw new Error('Failed to load apollo-client module');
  }

  return {
    apolloClientModule,
    secureStoreModule,
  };
}

describe('apollo-client refresh flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn() as jest.MockedFunction<typeof fetch>;
  });

  it('refreshes on UNAUTHENTICATED and retries the failed operation', async () => {
    let currentUserAttempts = 0;
    let refreshAttempts = 0;

    (global.fetch as jest.MockedFunction<typeof fetch>).mockImplementation(async (_input, init) => {
      const { operationName, query } = getOperationDetails(init);

      if (operationName === 'CurrentUser' || query.includes('query CurrentUser')) {
        currentUserAttempts += 1;

        return currentUserAttempts === 1
          ? jsonResponse({
              errors: [{ message: 'UNAUTHENTICATED', extensions: { code: 'UNAUTHENTICATED' } }],
            })
          : jsonResponse({ data: currentUserPayload });
      }

      if (operationName === 'RefreshAccessToken' || query.includes('mutation RefreshAccessToken')) {
        refreshAttempts += 1;
        return jsonResponse({ data: refreshedSessionPayload });
      }

      throw new Error(`Unexpected operation: ${operationName}`);
    });

    const { apolloClientModule, secureStoreModule } = loadApolloClientModule();
    secureStoreModule.getItemAsync = jest.fn().mockImplementation(async (key) => {
      if (key === 'accessToken') return 'saved-access-token';
      if (key === 'refreshToken') return 'saved-refresh-token';
      return null;
    });
    secureStoreModule.setItemAsync = jest.fn().mockResolvedValue(undefined);
    secureStoreModule.deleteItemAsync = jest.fn().mockResolvedValue(undefined);
    const { initializeApolloClient } = apolloClientModule;
    const client = await initializeApolloClient();

    const result = await client.query({
      query: CurrentUserDocument,
      fetchPolicy: 'network-only',
    });

    expect(result.data?.currentUser?.email).toBe('user@example.com');
    expect(refreshAttempts).toBe(1);
    expect(secureStoreModule.setItemAsync).toHaveBeenNthCalledWith(
      1,
      'accessToken',
      'refreshed-access-token'
    );
    expect(secureStoreModule.setItemAsync).toHaveBeenNthCalledWith(
      2,
      'refreshToken',
      'refreshed-refresh-token'
    );
  });

  it('emits session expired once when refresh fails', async () => {
    const listener = jest.fn();

    (global.fetch as jest.MockedFunction<typeof fetch>).mockImplementation(async (_input, init) => {
      const { operationName, query } = getOperationDetails(init);

      if (operationName === 'CurrentUser' || query.includes('query CurrentUser')) {
        return jsonResponse({
          errors: [{ message: 'UNAUTHENTICATED', extensions: { code: 'UNAUTHENTICATED' } }],
        });
      }

      if (operationName === 'RefreshAccessToken' || query.includes('mutation RefreshAccessToken')) {
        return jsonResponse({
          errors: [
            {
              message: 'AUTH_SESSION_EXPIRED',
              extensions: { code: 'AUTH_SESSION_EXPIRED' },
            },
          ],
        });
      }

      throw new Error(`Unexpected operation: ${operationName}`);
    });

    const { apolloClientModule, secureStoreModule } = loadApolloClientModule();
    secureStoreModule.getItemAsync = jest.fn().mockImplementation(async (key) => {
      if (key === 'accessToken') return 'saved-access-token';
      if (key === 'refreshToken') return 'saved-refresh-token';
      return null;
    });
    secureStoreModule.setItemAsync = jest.fn().mockResolvedValue(undefined);
    secureStoreModule.deleteItemAsync = jest.fn().mockResolvedValue(undefined);
    const { initializeApolloClient, subscribeToSessionExpired } = apolloClientModule;
    const unsubscribe = subscribeToSessionExpired(listener);
    const client = await initializeApolloClient();

    await expect(
      client.query({
        query: CurrentUserDocument,
        fetchPolicy: 'network-only',
      })
    ).rejects.toThrow();

    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
  });

  it('deduplicates concurrent UNAUTHENTICATED refreshes', async () => {
    let currentUserAttempts = 0;
    let requestPasswordResetAttempts = 0;
    let refreshAttempts = 0;

    (global.fetch as jest.MockedFunction<typeof fetch>).mockImplementation(async (_input, init) => {
      const { operationName, query } = getOperationDetails(init);

      if (operationName === 'CurrentUser' || query.includes('query CurrentUser')) {
        currentUserAttempts += 1;

        return currentUserAttempts === 1
          ? jsonResponse({
              errors: [{ message: 'UNAUTHENTICATED', extensions: { code: 'UNAUTHENTICATED' } }],
            })
          : jsonResponse({ data: currentUserPayload });
      }

      if (
        operationName === 'RequestPasswordReset' ||
        query.includes('mutation RequestPasswordReset')
      ) {
        requestPasswordResetAttempts += 1;

        return requestPasswordResetAttempts === 1
          ? jsonResponse({
              errors: [{ message: 'UNAUTHENTICATED', extensions: { code: 'UNAUTHENTICATED' } }],
            })
          : jsonResponse({
              data: {
                requestPasswordReset: {
                  success: true,
                  message: 'ok',
                },
              },
            });
      }

      if (operationName === 'RefreshAccessToken' || query.includes('mutation RefreshAccessToken')) {
        refreshAttempts += 1;
        return jsonResponse({ data: refreshedSessionPayload });
      }

      throw new Error(`Unexpected operation: ${operationName}`);
    });

    const { apolloClientModule, secureStoreModule } = loadApolloClientModule();
    secureStoreModule.getItemAsync = jest.fn().mockImplementation(async (key) => {
      if (key === 'accessToken') return 'saved-access-token';
      if (key === 'refreshToken') return 'saved-refresh-token';
      return null;
    });
    secureStoreModule.setItemAsync = jest.fn().mockResolvedValue(undefined);
    secureStoreModule.deleteItemAsync = jest.fn().mockResolvedValue(undefined);
    const { initializeApolloClient } = apolloClientModule;
    const client = await initializeApolloClient();

    const [currentUserResult, requestPasswordResetResult] = await Promise.all([
      client.query({
        query: CurrentUserDocument,
        fetchPolicy: 'network-only',
      }),
      client.mutate({
        mutation: RequestPasswordResetDocument,
        variables: {
          input: {
            email: 'user@example.com',
          },
        },
      }),
    ]);

    expect(currentUserResult.data?.currentUser?.id).toBe('user-1');
    expect(requestPasswordResetResult.data?.requestPasswordReset.success).toBe(true);
    expect(refreshAttempts).toBe(1);
  });
});
