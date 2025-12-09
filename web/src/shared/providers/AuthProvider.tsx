"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { logger } from "@/shared/utils/logger";

type User = {
  id: string;
  email: string;
  displayName?: string | null;
};

type AuthContextType = {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  signIn: (email: string, password: string, rememberMe?: boolean) => Promise<void>;
  signInWithOAuth: (provider: string, idToken: string, rememberMe?: boolean) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => void;
  isAuthenticated: boolean;
};

const AuthContext = React.createContext<AuthContextType | null>(null);

export const useAuth = (): AuthContextType => {
  const ctx = React.useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
};

type AuthProviderProps = {
  children: React.ReactNode;
};

const TOKEN_KEY = "auth_token";
const REFRESH_TOKEN_KEY = "auth_refresh_token";
const USER_KEY = "auth_user";

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = React.useState<User | null>(null);
  const [token, setToken] = React.useState<string | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const router = useRouter();

  // Initialize auth state from storage
  React.useEffect(() => {
    if (typeof window !== "undefined") {
      const storedToken = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
      const storedUser = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
      
      if (storedToken && storedUser) {
        try {
          setToken(storedToken);
          setUser(JSON.parse(storedUser));
        } catch (e) {
          logger.error("Error parsing stored user:", e);
          // Clear invalid data
          localStorage.removeItem(TOKEN_KEY);
          localStorage.removeItem(USER_KEY);
          sessionStorage.removeItem(TOKEN_KEY);
          sessionStorage.removeItem(USER_KEY);
        }
      }
      setIsLoading(false);
    }
  }, []);

  const signIn = React.useCallback(async (email: string, password: string, rememberMe = false) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_IN } = await import("@/lib/graphql/operations/auth");

      const result = await apolloClient.mutate({
        mutation: SIGN_IN,
        variables: {
          input: {
            email,
            password,
            rememberMe,
          },
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signIn' in result.data && result.data.signIn) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signIn as { token: string; refreshToken: string | null; user: any };
        
        setToken(newToken);
        setUser(newUser);

        // Store in appropriate storage based on rememberMe
        const storage = rememberMe ? localStorage : sessionStorage;
        storage.setItem(TOKEN_KEY, newToken);
        storage.setItem(USER_KEY, JSON.stringify(newUser));
        
        if (refreshToken) {
          storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        // Redirect to home
        router.push("/");
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (invalid credentials) are handled in UI components
      logger.error("Sign in error:", error);
      throw error;
    }
  }, [router]);

  const signInWithOAuth = React.useCallback(async (provider: string, idToken: string, rememberMe = false) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_IN_WITH_OAUTH } = await import("@/lib/graphql/operations/auth");

      const result = await apolloClient.mutate({
        mutation: SIGN_IN_WITH_OAUTH,
        variables: {
          input: {
            provider,
            idToken,
            rememberMe,
          },
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signInWithOAuth' in result.data && result.data.signInWithOAuth) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signInWithOAuth as { token: string; refreshToken: string | null; user: any };
        
        setToken(newToken);
        setUser(newUser);

        // Store in appropriate storage based on rememberMe
        const storage = rememberMe ? localStorage : sessionStorage;
        storage.setItem(TOKEN_KEY, newToken);
        storage.setItem(USER_KEY, JSON.stringify(newUser));
        
        if (refreshToken) {
          storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        // Redirect to home
        router.push("/");
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (OAuth denied/cancelled) are handled in UI components
      logger.error("OAuth sign in error:", error);
      throw error;
    }
  }, [router]);

  const signUp = React.useCallback(async (name: string, email: string, password: string) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_UP } = await import("@/lib/graphql/operations/auth");

      const result = await apolloClient.mutate({
        mutation: SIGN_UP,
        variables: {
          input: {
            name,
            email,
            password,
          },
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signUp' in result.data && result.data.signUp) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signUp as { token: string; refreshToken: string | null; user: any };
        
        setToken(newToken);
        setUser(newUser);

        // Store in localStorage (signup users are automatically signed in)
        localStorage.setItem(TOKEN_KEY, newToken);
        localStorage.setItem(USER_KEY, JSON.stringify(newUser));
        
        if (refreshToken) {
          localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        // Redirect to home
        router.push("/");
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (validation, duplicate email) are handled in UI components
      logger.error("Sign up error:", error);
      throw error;
    }
  }, [router]);

  const signOut = React.useCallback(() => {
    setUser(null);
    setToken(null);
    
    // Clear all storage
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);

    // Redirect to signin
    router.push("/signin");
  }, [router]);

  const value: AuthContextType = {
    user,
    token,
    isLoading,
    signIn,
    signInWithOAuth,
    signUp,
    signOut,
    isAuthenticated: !!user && !!token,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

