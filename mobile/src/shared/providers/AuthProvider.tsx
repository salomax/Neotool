import { ReactNode, useCallback, useEffect, useState } from 'react';
import { useApolloClient } from '@apollo/client/react';
import * as SecureStore from 'expo-secure-store';
import { AuthContext, User } from '@/shared/hooks/useAuth';
import i18n from '@/shared/config/i18n';
import {
  CurrentUserDocument,
  SignInDocument,
  SignUpDocument,
  type CurrentUserQuery,
  type SignInMutation,
  type SignInMutationVariables,
  type SignUpMutation,
  type SignUpMutationVariables,
} from '@invistus/shared-graphql';
import { refreshAccessToken, subscribeToSessionExpired } from '@/lib/apollo-client';

interface AuthProviderProps {
  children: ReactNode;
}

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

type SessionUserPayload = {
  id: string;
  email: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  emailVerified: boolean;
  roles: Array<{ id: string; name: string }>;
};

export function AuthProvider({ children }: AuthProviderProps) {
  const client = useApolloClient();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const mapSessionUser = useCallback(
    (currentUser: SessionUserPayload): User => ({
      id: currentUser.id,
      email: currentUser.email,
      displayName: currentUser.displayName ?? null,
      avatarUrl: currentUser.avatarUrl ?? null,
      emailVerified: currentUser.emailVerified,
      roles: currentUser.roles.map((role) => ({
        id: role.id,
        name: role.name,
      })),
    }),
    []
  );

  const storeSessionTokens = useCallback(async (token: string, refreshToken?: string | null) => {
    await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, token);
    if (refreshToken) {
      await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
      return;
    }
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  }, []);

  const clearSessionTokens = useCallback(async () => {
    await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
  }, []);

  const clearSession = useCallback(
    async ({ clearStore = true }: { clearStore?: boolean } = {}) => {
      await clearSessionTokens();
      setUser(null);

      if (clearStore) {
        await client.clearStore();
      }
    },
    [clearSessionTokens, client]
  );

  const hydrateCurrentUser = useCallback(async (): Promise<User> => {
    const { data } = await client.query<CurrentUserQuery>({
      query: CurrentUserDocument,
      fetchPolicy: 'network-only',
    });

    const currentUser = data?.currentUser;

    if (!currentUser) {
      throw new Error('Current user was not returned by the API.');
    }

    return mapSessionUser(currentUser);
  }, [client, mapSessionUser]);

  const establishSession = useCallback(
    async (token: string, refreshToken?: string | null) => {
      await storeSessionTokens(token, refreshToken);

      try {
        const currentUser = await hydrateCurrentUser();
        setUser(currentUser);
        return currentUser;
      } catch (error) {
        await clearSessionTokens();
        setUser(null);
        throw error;
      }
    },
    [clearSessionTokens, hydrateCurrentUser, storeSessionTokens]
  );

  const restoreSession = useCallback(async () => {
    const token = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);

    if (!token) {
      setUser(null);
      return;
    }

    try {
      const restoredUser = await hydrateCurrentUser();
      setUser(restoredUser);
      return;
    } catch (error) {
      const refreshedSession = await refreshAccessToken();

      if (refreshedSession) {
        setUser(mapSessionUser(refreshedSession.user));
        return;
      }

      await clearSession({ clearStore: true });
    }
  }, [clearSession, hydrateCurrentUser, mapSessionUser]);

  useEffect(() => {
    let isMounted = true;

    const bootstrap = async () => {
      try {
        await restoreSession();
      } catch (error) {
        console.error('Failed to restore session:', error);
        await clearSession({ clearStore: true });
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    void bootstrap();

    return () => {
      isMounted = false;
    };
  }, [clearSession, restoreSession]);

  useEffect(() => {
    const unsubscribe = subscribeToSessionExpired(() => {
      void clearSession({ clearStore: true });
    });

    return unsubscribe;
  }, [clearSession]);

  const login = async (email: string, password: string) => {
    try {
      setIsLoading(true);

      const { data } = await client.mutate<SignInMutation, SignInMutationVariables>({
        mutation: SignInDocument,
        variables: {
          input: {
            email,
            password,
          },
        },
      });

      if (!data?.signIn) {
        throw new Error('Sign in did not return a session payload.');
      }

      await establishSession(data.signIn.token, data.signIn.refreshToken);
    } catch (error) {
      console.error('Login failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      setIsLoading(true);
      await clearSession({ clearStore: true });
    } catch (error) {
      console.error('Logout failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const signup = async (name: string, email: string, password: string) => {
    try {
      setIsLoading(true);

      const { data } = await client.mutate<SignUpMutation, SignUpMutationVariables>({
        mutation: SignUpDocument,
        variables: {
          input: {
            name,
            email,
            password,
            locale: i18n.resolvedLanguage ?? i18n.language ?? 'en',
          },
        },
      });

      if (!data?.signUp) {
        throw new Error('Sign up did not return a session payload.');
      }

      await establishSession(data.signUp.token, data.signUp.refreshToken);
    } catch (error) {
      console.error('Signup failed:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: user !== null,
        login,
        logout,
        signup,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
