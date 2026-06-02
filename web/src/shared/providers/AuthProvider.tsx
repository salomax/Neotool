"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { logger } from "@/shared/utils/logger";
import { identifyUser } from "@/shared/analytics/umami";
import { clearAuthSessionCookie, syncAuthSessionCookie } from "@/shared/utils/auth-session/client";
import { AUTH_HINT_COOKIE_NAME } from "@/shared/config/auth-route-policy";

type User = {
  id: string;
  email: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  avatarProvider?: string | null;
  emailVerified?: boolean | null;
  tenantId?: string | null;
  role?: string | null;
  roles?: Array<{ name: string }> | null;
  plan?: string | null;
};

type AuthContextType = {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  signIn: (email: string, password: string, rememberMe?: boolean | null) => Promise<void>;
  signInWithOAuth: (provider: string, idToken: string, rememberMe?: boolean | null) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => void;
  setUser: (user: User | null) => void;
  setPostLoginRedirect: (path: string | null) => void;
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
  const postLoginRedirectPathRef = React.useRef<string | null>(null);

  const setPostLoginRedirect = React.useCallback((path: string | null) => {
    postLoginRedirectPathRef.current = path;
  }, []);

  const syncAuthHintCookie = React.useCallback((hasAuthToken: boolean) => {
    if (typeof window === "undefined") return;
    if (hasAuthToken) {
      document.cookie = `${AUTH_HINT_COOKIE_NAME}=1; path=/; max-age=604800; SameSite=Lax`;
    } else {
      document.cookie = `${AUTH_HINT_COOKIE_NAME}=; path=/; max-age=0; SameSite=Lax`;
    }
  }, []);

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

  // Sync optimistic auth hint cookie for proxy (request-time redirect).
  // Cookie is a hint only; backend must still enforce authorization.
  React.useEffect(() => {
    syncAuthHintCookie(!!token);
  }, [token, syncAuthHintCookie]);

  const routeAfterAuthentication = React.useCallback(
    (nextUser: User | null, fallbackPath = "/", explicitRedirect?: string | null) => {
      if (nextUser?.emailVerified === false && nextUser.email) {
        sessionStorage.setItem("pendingVerificationEmail", nextUser.email);
        router.push("/verify-email");
        return;
      }
      const explicitTarget =
        typeof explicitRedirect === "string" && explicitRedirect !== ""
          ? explicitRedirect
          : null;
      const target = explicitTarget ?? postLoginRedirectPathRef.current ?? fallbackPath;
      postLoginRedirectPathRef.current = null;
      router.push(target);
    },
    [router]
  );

  // Listen for token refresh events from Apollo Client
  React.useEffect(() => {
    if (typeof window === 'undefined') return;

    const handleTokenRefresh = (event: CustomEvent) => {
      const { token: newToken, user: newUser } = event.detail;
      setToken(newToken);
      setUser(newUser);
      logger.debug("Token refreshed successfully");
    };

    window.addEventListener('auth:token-refreshed', handleTokenRefresh as (event: Event) => void);
    
    return () => {
      window.removeEventListener('auth:token-refreshed', handleTokenRefresh as (event: Event) => void);
    };
  }, []);

  const signIn = React.useCallback(async (email: string, password: string, rememberMe?: boolean | null) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_IN } = await import("@/lib/graphql/operations/auth");

      const input: Record<string, unknown> = {
        email,
        password,
      };
      if (typeof rememberMe === "boolean") {
        input.rememberMe = rememberMe;
      }

      const result = await apolloClient.mutate({
        mutation: SIGN_IN,
        variables: {
          input,
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signIn' in result.data && result.data.signIn) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signIn as { token: string; refreshToken: string | null; user: User };
        const shouldPersistSession = !!refreshToken;
        
        setToken(newToken);
        setUser(newUser);
        syncAuthHintCookie(true);

        // Store in localStorage when backend issued refreshToken (persistent session),
        // otherwise keep session-scoped storage.
        const storage = shouldPersistSession ? localStorage : sessionStorage;
        storage.setItem(TOKEN_KEY, newToken);
        storage.setItem(USER_KEY, JSON.stringify(newUser));

        if (refreshToken) {
          storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        void syncAuthSessionCookie({
          user: newUser,
          rememberMe: shouldPersistSession,
        });

        identifyUser(newUser.id);
        routeAfterAuthentication(newUser);
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (invalid credentials) are handled in UI components
      logger.error("Sign in error:", error);
      throw error;
    }
  }, [routeAfterAuthentication, syncAuthHintCookie]);

  const signInWithOAuth = React.useCallback(async (provider: string, idToken: string, rememberMe?: boolean | null) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_IN_WITH_OAUTH } = await import("@/lib/graphql/operations/auth");

      const input: Record<string, unknown> = {
        provider,
        idToken,
      };
      if (typeof rememberMe === "boolean") {
        input.rememberMe = rememberMe;
      }

      const result = await apolloClient.mutate({
        mutation: SIGN_IN_WITH_OAUTH,
        variables: {
          input,
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signInWithOAuth' in result.data && result.data.signInWithOAuth) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signInWithOAuth as { token: string; refreshToken: string | null; user: User };
        const shouldPersistSession = !!refreshToken;
        
        setToken(newToken);
        setUser(newUser);
        syncAuthHintCookie(true);

        // Store in localStorage when backend issued refreshToken (persistent session),
        // otherwise keep session-scoped storage.
        const storage = shouldPersistSession ? localStorage : sessionStorage;
        storage.setItem(TOKEN_KEY, newToken);
        storage.setItem(USER_KEY, JSON.stringify(newUser));

        if (refreshToken) {
          storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        void syncAuthSessionCookie({
          user: newUser,
          rememberMe: shouldPersistSession,
        });

        identifyUser(newUser.id);
        routeAfterAuthentication(newUser);
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (OAuth denied/cancelled) are handled in UI components
      logger.error("OAuth sign in error:", error);
      throw error;
    }
  }, [routeAfterAuthentication, syncAuthHintCookie]);

  const signUp = React.useCallback(async (name: string, email: string, password: string) => {
    try {
      // Dynamic import to avoid SSR issues
      const { apolloClient } = await import("@/lib/graphql/client");
      const { SIGN_UP } = await import("@/lib/graphql/operations/auth");
      const i18n = (await import("@/shared/i18n/config")).default;
      const locale = i18n.language || "pt";

      const result = await apolloClient.mutate({
        mutation: SIGN_UP,
        variables: {
          input: {
            name,
            email,
            password,
            locale,
          },
        },
      });

      if (result.data && typeof result.data === 'object' && result.data !== null && 'signUp' in result.data && result.data.signUp) {
        const payload = result.data.signUp as { token: string; refreshToken: string | null; user: User; requiresVerification?: boolean };
        const { token: newToken, refreshToken, user: newUser, requiresVerification } = payload;

        setToken(newToken);
        setUser(newUser);
        syncAuthHintCookie(true);

        // Store in localStorage (signup users are automatically signed in)
        localStorage.setItem(TOKEN_KEY, newToken);
        localStorage.setItem(USER_KEY, JSON.stringify(newUser));

        if (refreshToken) {
          localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }

        void syncAuthSessionCookie({
          user: newUser,
          rememberMe: true,
        });

        if (requiresVerification && newUser?.email) {
          sessionStorage.setItem("pendingVerificationEmail", newUser.email);
          router.push("/verify-email");
        } else {
          routeAfterAuthentication(newUser);
        }
      }
    } catch (error: any) {
      // Log technical errors (network failures, GraphQL errors)
      // Business errors (validation, duplicate email) are handled in UI components
      logger.error("Sign up error:", error);
      throw error;
    }
  }, [routeAfterAuthentication, router, syncAuthHintCookie]);

  const signOut = React.useCallback(() => {
    setUser(null);
    setToken(null);
    syncAuthHintCookie(false);
    
    // Clear all storage
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    void clearAuthSessionCookie();

    // Redirect to signin
    router.push("/signin");
  }, [router, syncAuthHintCookie]);

  const value: AuthContextType = {
    user,
    token,
    isLoading,
    signIn,
    signInWithOAuth,
    signUp,
    signOut,
    setUser,
    setPostLoginRedirect,
    isAuthenticated: !!user && !!token,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
