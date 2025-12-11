import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  getAuthToken,
  hasAuthToken,
  clearAuthStorage,
  isAuthenticationError,
  handleAuthError,
} from '../auth';

// Mock window.location
const mockLocation = {
  href: '',
  assign: vi.fn(),
  replace: vi.fn(),
  reload: vi.fn(),
};

Object.defineProperty(window, 'location', {
  value: mockLocation,
  writable: true,
});

describe('auth utilities', () => {
  beforeEach(() => {
    // Clear all storage before each test
    localStorage.clear();
    sessionStorage.clear();
    // Reset location mock
    mockLocation.href = '';
    vi.clearAllMocks();
  });

  describe('getAuthToken', () => {
    it('should return null when no token exists', () => {
      expect(getAuthToken()).toBeNull();
    });

    it('should return token from localStorage', () => {
      localStorage.setItem('auth_token', 'test-token-123');
      expect(getAuthToken()).toBe('test-token-123');
    });

    it('should return token from sessionStorage when localStorage is empty', () => {
      sessionStorage.setItem('auth_token', 'session-token-456');
      expect(getAuthToken()).toBe('session-token-456');
    });

    it('should prefer localStorage over sessionStorage', () => {
      localStorage.setItem('auth_token', 'local-token');
      sessionStorage.setItem('auth_token', 'session-token');
      expect(getAuthToken()).toBe('local-token');
    });

    it('should return null in SSR environment', () => {
      const originalWindow = global.window;
      // @ts-ignore - temporarily remove window
      delete (global as any).window;
      
      expect(getAuthToken()).toBeNull();
      
      // Restore window
      global.window = originalWindow;
    });
  });

  describe('hasAuthToken', () => {
    it('should return false when no token exists', () => {
      expect(hasAuthToken()).toBe(false);
    });

    it('should return true when token exists in localStorage', () => {
      localStorage.setItem('auth_token', 'test-token');
      expect(hasAuthToken()).toBe(true);
    });

    it('should return true when token exists in sessionStorage', () => {
      sessionStorage.setItem('auth_token', 'test-token');
      expect(hasAuthToken()).toBe(true);
    });

    it('should return false for empty string token', () => {
      localStorage.setItem('auth_token', '');
      expect(hasAuthToken()).toBe(false);
    });
  });

  describe('clearAuthStorage', () => {
    it('should clear all auth-related keys from localStorage', () => {
      localStorage.setItem('auth_token', 'token');
      localStorage.setItem('auth_user', 'user');
      localStorage.setItem('auth_refresh_token', 'refresh');
      localStorage.setItem('other_key', 'should-remain');

      clearAuthStorage();

      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('auth_user')).toBeNull();
      expect(localStorage.getItem('auth_refresh_token')).toBeNull();
      expect(localStorage.getItem('other_key')).toBe('should-remain');
    });

    it('should clear all auth-related keys from sessionStorage', () => {
      sessionStorage.setItem('auth_token', 'token');
      sessionStorage.setItem('auth_user', 'user');
      sessionStorage.setItem('auth_refresh_token', 'refresh');
      sessionStorage.setItem('other_key', 'should-remain');

      clearAuthStorage();

      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('auth_user')).toBeNull();
      expect(sessionStorage.getItem('auth_refresh_token')).toBeNull();
      expect(sessionStorage.getItem('other_key')).toBe('should-remain');
    });

    it('should clear from both localStorage and sessionStorage', () => {
      localStorage.setItem('auth_token', 'local-token');
      sessionStorage.setItem('auth_token', 'session-token');

      clearAuthStorage();

      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('auth_token')).toBeNull();
    });

    it('should not throw when storage is empty', () => {
      expect(() => clearAuthStorage()).not.toThrow();
    });

    it('should handle SSR environment gracefully', () => {
      const originalWindow = global.window;
      // @ts-ignore - temporarily remove window
      delete (global as any).window;
      
      expect(() => clearAuthStorage()).not.toThrow();
      
      // Restore window
      global.window = originalWindow;
    });
  });

  describe('isAuthenticationError', () => {
    describe('message-based detection', () => {
      it('should detect "Authentication required" message', () => {
        expect(isAuthenticationError(new Error('Authentication required'))).toBe(true);
      });

      it('should detect "authentication" in message (case insensitive)', () => {
        expect(isAuthenticationError(new Error('Authentication failed'))).toBe(true);
        expect(isAuthenticationError(new Error('AUTHENTICATION ERROR'))).toBe(true);
        expect(isAuthenticationError(new Error('authentication required'))).toBe(true);
      });

      it('should detect "invalid or expired access token"', () => {
        expect(isAuthenticationError(new Error('Invalid or expired access token'))).toBe(true);
        expect(isAuthenticationError(new Error('invalid or expired access token'))).toBe(true);
      });

      it('should detect "access token is required"', () => {
        expect(isAuthenticationError(new Error('Access token is required'))).toBe(true);
        expect(isAuthenticationError(new Error('access token is required'))).toBe(true);
      });

      it('should detect "unauthorized" in message', () => {
        expect(isAuthenticationError(new Error('Unauthorized access'))).toBe(true);
        expect(isAuthenticationError(new Error('User is unauthorized'))).toBe(true);
      });

      it('should work with pre-extracted error message', () => {
        expect(isAuthenticationError(null, 'Authentication required')).toBe(true);
        expect(isAuthenticationError(null, 'Invalid or expired access token')).toBe(true);
      });

      it('should return false for non-auth errors', () => {
        expect(isAuthenticationError(new Error('Network error'))).toBe(false);
        expect(isAuthenticationError(new Error('Validation failed'))).toBe(false);
        expect(isAuthenticationError(new Error('Server error'))).toBe(false);
      });

      it('should return false for null/undefined error without message', () => {
        expect(isAuthenticationError(null)).toBe(false);
        expect(isAuthenticationError(undefined)).toBe(false);
      });

      it('should return false for empty message', () => {
        expect(isAuthenticationError(new Error(''))).toBe(false);
        expect(isAuthenticationError(null, '')).toBe(false);
      });
    });

    describe('401 status code detection', () => {
      it('should detect 401 status code in network error', () => {
        const networkError = {
          statusCode: 401,
        };
        expect(isAuthenticationError(networkError)).toBe(true);
      });

      it('should detect 401 in networkError.response.status', () => {
        const networkError = {
          response: {
            status: 401,
          },
        };
        expect(isAuthenticationError(networkError)).toBe(true);
      });

      it('should prefer statusCode over response.status', () => {
        const networkError = {
          statusCode: 401,
          response: {
            status: 200,
          },
        };
        expect(isAuthenticationError(networkError)).toBe(true);
      });

      it('should return false for non-401 status codes', () => {
        expect(isAuthenticationError({ statusCode: 400 })).toBe(false);
        expect(isAuthenticationError({ statusCode: 403 })).toBe(false);
        expect(isAuthenticationError({ statusCode: 500 })).toBe(false);
        expect(isAuthenticationError({ response: { status: 404 } })).toBe(false);
      });

      it('should return false when status code is missing', () => {
        expect(isAuthenticationError({})).toBe(false);
        expect(isAuthenticationError({ response: {} })).toBe(false);
      });
    });

    describe('combined detection', () => {
      it('should detect auth error from message even if status code is missing', () => {
        expect(isAuthenticationError(new Error('Authentication required'))).toBe(true);
      });

      it('should detect auth error from status code even if message is generic', () => {
        expect(isAuthenticationError({ statusCode: 401, message: 'Error' })).toBe(true);
      });
    });
  });

  describe('handleAuthError', () => {
    it('should clear all auth storage', () => {
      localStorage.setItem('auth_token', 'token');
      localStorage.setItem('auth_user', 'user');
      sessionStorage.setItem('auth_token', 'token');

      handleAuthError();

      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('auth_user')).toBeNull();
      expect(sessionStorage.getItem('auth_token')).toBeNull();
    });

    it('should redirect to default /signin path', () => {
      handleAuthError();
      expect(mockLocation.href).toBe('/signin');
    });

    it('should redirect to custom path', () => {
      handleAuthError('/custom-login');
      expect(mockLocation.href).toBe('/custom-login');
    });

    it('should clear storage before redirecting', () => {
      localStorage.setItem('auth_token', 'token');
      const clearSpy = vi.spyOn(Storage.prototype, 'removeItem');

      handleAuthError();

      // Verify storage was cleared
      expect(clearSpy).toHaveBeenCalled();
      expect(localStorage.getItem('auth_token')).toBeNull();
      // Verify redirect happened
      expect(mockLocation.href).toBe('/signin');
    });

    it('should handle SSR environment gracefully', () => {
      const originalWindow = global.window;
      // @ts-ignore - temporarily remove window
      delete (global as any).window;
      
      expect(() => handleAuthError()).not.toThrow();
      
      // Restore window
      global.window = originalWindow;
    });
  });
});
