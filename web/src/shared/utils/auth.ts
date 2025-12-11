/**
 * Authentication utility functions
 * Shared utilities for authentication-related operations
 */

// Storage keys - must match AuthProvider constants
const TOKEN_KEY = 'auth_token';
const REFRESH_TOKEN_KEY = 'auth_refresh_token';
const USER_KEY = 'auth_user';

/**
 * Gets the authentication token from storage
 * Checks both localStorage and sessionStorage
 */
export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
}

/**
 * Checks if an authentication token exists in storage
 */
export function hasAuthToken(): boolean {
  return !!getAuthToken();
}

/**
 * Clears all authentication-related data from storage
 * Clears from both localStorage and sessionStorage
 */
export function clearAuthStorage(): void {
  if (typeof window === 'undefined') return;
  
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  sessionStorage.removeItem(USER_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
}

/**
 * Checks if an error is an authentication error
 * @param error - The error to check
 * @param errorMessage - Optional pre-extracted error message
 * @returns True if the error is an authentication error
 */
export function isAuthenticationError(
  error: unknown,
  errorMessage?: string
): boolean {
  const message = errorMessage || (error instanceof Error ? error.message : String(error));
  
  if (!message) return false;
  
  const lowerMessage = message.toLowerCase();
  
  // Check for auth error patterns
  if (
    message === 'Authentication required' ||
    lowerMessage.includes('authentication') ||
    lowerMessage.includes('invalid or expired access token') ||
    lowerMessage.includes('access token is required') ||
    lowerMessage.includes('unauthorized')
  ) {
    return true;
  }
  
  // Check for 401 status codes in network errors
  if (error && typeof error === 'object') {
    // Handle Apollo Client network errors (error is the networkError itself)
    const networkError = error as any;
    const statusCode = networkError?.statusCode || networkError?.response?.status;
    if (statusCode === 401) {
      return true;
    }
  }
  
  return false;
}

/**
 * Handles authentication errors by clearing storage and redirecting
 * @param redirectTo - Path to redirect to (default: '/signin')
 */
export function handleAuthError(redirectTo: string = '/signin'): void {
  clearAuthStorage();
  if (typeof window !== 'undefined') {
    window.location.href = redirectTo;
  }
}
