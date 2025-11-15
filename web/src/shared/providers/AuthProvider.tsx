"use client";

import * as React from "react";
import { useRouter } from "next/navigation";

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
          console.error("Error parsing stored user:", e);
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

      if (result.data?.signIn) {
        const { token: newToken, refreshToken, user: newUser } = result.data.signIn;
        
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
      console.error("Sign in error:", error);
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
    signOut,
    isAuthenticated: !!user && !!token,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

