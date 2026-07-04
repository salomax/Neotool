import { ReactNode } from 'react';
import { MockedResponse } from '@apollo/client/testing';
import { MockedProvider } from '@apollo/client/testing/react';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import * as SecureStore from 'expo-secure-store';
import { AuthProvider } from '../AuthProvider';
import { useAuth } from '../../hooks/useAuth';
import {
  CurrentUserDocument,
  SignInDocument,
  SignUpDocument,
} from '@invistus/shared-graphql';
import { refreshAccessToken, subscribeToSessionExpired } from '@/lib/apollo-client';

jest.mock('@/lib/apollo-client', () => ({
  refreshAccessToken: jest.fn(),
  subscribeToSessionExpired: jest.fn(() => jest.fn()),
}));

jest.mock('@/shared/config/i18n', () => ({
  __esModule: true,
  default: {
    resolvedLanguage: 'en',
    language: 'en',
  },
}));

const secureStoreMock = SecureStore as jest.Mocked<typeof SecureStore>;
const refreshAccessTokenMock = refreshAccessToken as jest.MockedFunction<typeof refreshAccessToken>;
const subscribeToSessionExpiredMock = subscribeToSessionExpired as jest.MockedFunction<
  typeof subscribeToSessionExpired
>;
let consoleErrorSpy: jest.SpyInstance;
let sessionExpiredListener: (() => void) | undefined;

function createWrapper(mocks: MockedResponse[]) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MockedProvider mocks={mocks}>
        <AuthProvider>{children}</AuthProvider>
      </MockedProvider>
    );
  };
}

describe('AuthProvider', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    secureStoreMock.setItemAsync = jest.fn().mockResolvedValue(undefined);
    secureStoreMock.deleteItemAsync = jest.fn().mockResolvedValue(undefined);
    secureStoreMock.getItemAsync = jest.fn().mockResolvedValue(null);
    refreshAccessTokenMock.mockResolvedValue(null);
    subscribeToSessionExpiredMock.mockImplementation((listener) => {
      sessionExpiredListener = listener;
      return jest.fn();
    });
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('signs in and hydrates the current user', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignInDocument,
          variables: {
            input: {
              email: 'test@example.com',
              password: 'password123',
            },
          },
        },
        result: {
          data: {
            signIn: {
              token: 'access-token',
              refreshToken: 'refresh-token',
              user: {
                id: 'ignored',
                email: 'ignored@example.com',
                displayName: 'Ignored',
                avatarUrl: null,
                emailVerified: true,
                roles: [],
              },
            },
          },
        },
      },
      {
        request: {
          query: CurrentUserDocument,
        },
        result: {
          data: {
            currentUser: {
              id: 'user-1',
              email: 'test@example.com',
              displayName: 'Test User',
              avatarUrl: 'https://example.com/avatar.png',
              emailVerified: true,
              roles: [{ id: 'role-1', name: 'USER' }],
            },
          },
        },
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.login('test@example.com', 'password123');
    });

    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    expect(secureStoreMock.setItemAsync).toHaveBeenNthCalledWith(1, 'accessToken', 'access-token');
    expect(secureStoreMock.setItemAsync).toHaveBeenNthCalledWith(2, 'refreshToken', 'refresh-token');
    expect(result.current.user).toEqual({
      id: 'user-1',
      email: 'test@example.com',
      displayName: 'Test User',
      avatarUrl: 'https://example.com/avatar.png',
      emailVerified: true,
      roles: [{ id: 'role-1', name: 'USER' }],
    });
  });

  it('restores the session from the saved access token', async () => {
    secureStoreMock.getItemAsync.mockImplementation(async (key) =>
      key === 'accessToken' ? 'saved-access-token' : null
    );

    const wrapper = createWrapper([
      {
        request: {
          query: CurrentUserDocument,
        },
        result: {
          data: {
            currentUser: {
              id: 'user-1',
              email: 'restored@example.com',
              displayName: 'Restored User',
              avatarUrl: null,
              emailVerified: true,
              roles: [{ id: 'role-1', name: 'USER' }],
            },
          },
        },
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.email).toBe('restored@example.com');
  });

  it('refreshes the session when restore hits an expired token', async () => {
    secureStoreMock.getItemAsync.mockImplementation(async (key) =>
      key === 'accessToken' ? 'expired-access-token' : key === 'refreshToken' ? 'refresh-token' : null
    );

    refreshAccessTokenMock.mockResolvedValue({
      token: 'refreshed-access-token',
      refreshToken: 'refreshed-refresh-token',
      user: {
        id: 'user-1',
        email: 'restored@example.com',
        displayName: 'Restored User',
        avatarUrl: null,
        emailVerified: true,
        roles: [{ id: 'role-1', name: 'USER' }],
      },
    });

    const wrapper = createWrapper([
      {
        request: {
          query: CurrentUserDocument,
        },
        error: new Error('UNAUTHENTICATED'),
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1);
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.displayName).toBe('Restored User');
  });

  it('clears the session when restore refresh fails', async () => {
    secureStoreMock.getItemAsync.mockImplementation(async (key) =>
      key === 'accessToken' ? 'expired-access-token' : key === 'refreshToken' ? 'refresh-token' : null
    );

    const wrapper = createWrapper([
      {
        request: {
          query: CurrentUserDocument,
        },
        error: new Error('UNAUTHENTICATED'),
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(refreshAccessTokenMock).toHaveBeenCalledTimes(1);
    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('accessToken');
    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('signs up and hydrates the current user', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignUpDocument,
          variables: {
            input: {
              name: 'Test User',
              email: 'test@example.com',
              password: 'password123',
              locale: 'en',
            },
          },
        },
        result: {
          data: {
            signUp: {
              token: 'access-token',
              refreshToken: 'refresh-token',
              requiresVerification: false,
              user: {
                id: 'ignored',
                email: 'ignored@example.com',
                displayName: 'Ignored',
                avatarUrl: null,
                emailVerified: true,
                roles: [],
              },
            },
          },
        },
      },
      {
        request: {
          query: CurrentUserDocument,
        },
        result: {
          data: {
            currentUser: {
              id: 'user-1',
              email: 'test@example.com',
              displayName: 'Test User',
              avatarUrl: null,
              emailVerified: false,
              roles: [{ id: 'role-1', name: 'USER' }],
            },
          },
        },
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.signup('Test User', 'test@example.com', 'password123');
    });

    await waitFor(() => expect(result.current.user?.displayName).toBe('Test User'));
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('clears the session on logout', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignInDocument,
          variables: {
            input: {
              email: 'test@example.com',
              password: 'password123',
            },
          },
        },
        result: {
          data: {
            signIn: {
              token: 'access-token',
              refreshToken: 'refresh-token',
              user: {
                id: 'ignored',
                email: 'ignored@example.com',
                displayName: 'Ignored',
                avatarUrl: null,
                emailVerified: true,
                roles: [],
              },
            },
          },
        },
      },
      {
        request: {
          query: CurrentUserDocument,
        },
        result: {
          data: {
            currentUser: {
              id: 'user-1',
              email: 'test@example.com',
              displayName: 'Test User',
              avatarUrl: null,
              emailVerified: true,
              roles: [],
            },
          },
        },
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.login('test@example.com', 'password123');
    });

    await act(async () => {
      await result.current.logout();
    });

    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('accessToken');
    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('keeps the session empty when sign in fails', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignInDocument,
          variables: {
            input: {
              email: 'test@example.com',
              password: 'wrong-password',
            },
          },
        },
        error: new Error('Invalid credentials'),
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let receivedError: { message: string } | null = null;

    await act(async () => {
      try {
        await result.current.login('test@example.com', 'wrong-password');
      } catch (error) {
        receivedError = error as Error;
      }
    });

    expect((receivedError as Error | null)?.message).toBe('Invalid credentials');
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('rolls back stored tokens when currentUser hydration fails after sign in', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignInDocument,
          variables: {
            input: {
              email: 'test@example.com',
              password: 'password123',
            },
          },
        },
        result: {
          data: {
            signIn: {
              token: 'access-token',
              refreshToken: 'refresh-token',
              user: {
                id: 'ignored',
                email: 'ignored@example.com',
                displayName: 'Ignored',
                avatarUrl: null,
                emailVerified: true,
                roles: [],
              },
            },
          },
        },
      },
      {
        request: {
          query: CurrentUserDocument,
        },
        error: new Error('Current user query failed'),
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let receivedMessage: string | null = null;

    await act(async () => {
      try {
        await result.current.login('test@example.com', 'password123');
      } catch (error) {
        receivedMessage = error instanceof Error ? error.message : String(error);
      }
    });

    expect(receivedMessage).toBe('Current user query failed');
    expect(secureStoreMock.setItemAsync).toHaveBeenNthCalledWith(1, 'accessToken', 'access-token');
    expect(secureStoreMock.setItemAsync).toHaveBeenNthCalledWith(2, 'refreshToken', 'refresh-token');
    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('accessToken');
    expect(secureStoreMock.deleteItemAsync).toHaveBeenCalledWith('refreshToken');
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('keeps the session empty when sign up fails', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignUpDocument,
          variables: {
            input: {
              name: 'Existing User',
              email: 'existing@example.com',
              password: 'password123',
              locale: 'en',
            },
          },
        },
        error: new Error('Email already in use'),
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let receivedError: { message: string } | null = null;

    await act(async () => {
      try {
        await result.current.signup('Existing User', 'existing@example.com', 'password123');
      } catch (error) {
        receivedError = error as Error;
      }
    });

    expect((receivedError as Error | null)?.message).toBe('Email already in use');
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('clears the current session when the Apollo layer emits session expired', async () => {
    const wrapper = createWrapper([
      {
        request: {
          query: SignInDocument,
          variables: {
            input: {
              email: 'test@example.com',
              password: 'password123',
            },
          },
        },
        result: {
          data: {
            signIn: {
              token: 'access-token',
              refreshToken: 'refresh-token',
              user: {
                id: 'ignored',
                email: 'ignored@example.com',
                displayName: 'Ignored',
                avatarUrl: null,
                emailVerified: true,
                roles: [],
              },
            },
          },
        },
      },
      {
        request: {
          query: CurrentUserDocument,
        },
        result: {
          data: {
            currentUser: {
              id: 'user-1',
              email: 'test@example.com',
              displayName: 'Test User',
              avatarUrl: null,
              emailVerified: true,
              roles: [],
            },
          },
        },
      },
    ]);

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.login('test@example.com', 'password123');
    });

    await act(async () => {
      sessionExpiredListener?.();
    });

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });
});
