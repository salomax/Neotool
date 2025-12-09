import * as React from "react";
import { signInWithGoogle, loadGoogleIdentityServices } from "@/lib/auth/oauth/google";
import { logger } from "@/shared/utils/logger";

export type OAuthProvider = "google";

export interface UseOAuthOptions {
  onSuccess?: (idToken: string) => void;
  onError?: (error: Error) => void;
}

export interface UseOAuthReturn {
  signIn: (provider: OAuthProvider) => Promise<string | null>;
  isLoading: boolean;
  error: Error | null;
}

/**
 * Hook for OAuth sign-in.
 * 
 * Manages OAuth flow state and provides a simple interface for signing in with OAuth providers.
 */
export function useOAuth(options: UseOAuthOptions = {}): UseOAuthReturn {
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<Error | null>(null);

  // Load Google Identity Services on mount
  React.useEffect(() => {
    loadGoogleIdentityServices().catch((err) => {
      logger.error("Failed to load Google Identity Services:", err);
    });
  }, []);

  const signIn = React.useCallback(
    async (provider: OAuthProvider): Promise<string | null> => {
      setIsLoading(true);
      setError(null);

      try {
        let idToken: string;

        switch (provider) {
          case "google": {
            const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
            if (!clientId) {
              throw new Error("Google OAuth client ID is not configured");
            }
            idToken = await signInWithGoogle({ clientId });
            break;
          }
          default:
            throw new Error(`Unsupported OAuth provider: ${provider}`);
        }

        options.onSuccess?.(idToken);
        return idToken;
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err));
        setError(error);
        options.onError?.(error);
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    [options]
  );

  return {
    signIn,
    isLoading,
    error,
  };
}

