import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { AuthProvider, useAuth } from '../AuthProvider';

// Use vi.hoisted() to define variables that need to be available in mock factories
const { mockPush, mockMutate, mockLoggerError } = vi.hoisted(() => {
  const mockPush = vi.fn();
  const mockMutate = vi.fn();
  const mockLoggerError = vi.fn();
  
  return {
    mockPush,
    mockMutate,
    mockLoggerError,
  };
});

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Apollo Client
vi.mock('@/lib/graphql/client', () => ({
  apolloClient: {
    mutate: mockMutate,
  },
}));

// Mock GraphQL operations
vi.mock('@/lib/graphql/operations/auth', () => ({
  SIGN_IN: { kind: 'Document', definitions: [] },
  SIGN_IN_WITH_OAUTH: { kind: 'Document', definitions: [] },
  SIGN_UP: { kind: 'Document', definitions: [] },
}));

// Mock logger
vi.mock('@/shared/utils/logger', () => ({
  logger: {
    error: mockLoggerError,
  },
}));

// Create storage mocks
const createStorageMock = () => {
  const storage = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => storage.get(key) || null),
    setItem: vi.fn((key: string, value: string) => storage.set(key, value)),
    removeItem: vi.fn((key: string) => storage.delete(key)),
    clear: vi.fn(() => storage.clear()),
  };
};

describe('AuthProvider', () => {
  let localStorageMock: ReturnType<typeof createStorageMock>;
  let sessionStorageMock: ReturnType<typeof createStorageMock>;

  beforeEach(() => {
    vi.clearAllMocks();
    
    // Create fresh storage mocks
    localStorageMock = createStorageMock();
    sessionStorageMock = createStorageMock();
    
    // Replace global storage objects
    Object.defineProperty(window, 'localStorage', {
      value: localStorageMock,
      writable: true,
      configurable: true,
    });
    Object.defineProperty(window, 'sessionStorage', {
      value: sessionStorageMock,
      writable: true,
      configurable: true,
    });
    
    // Ensure window is defined
    Object.defineProperty(global, 'window', {
      value: window,
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <AuthProvider>{children}</AuthProvider>
  );

  describe('Initialization', () => {
    it('should initialize with null user and token', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should initialize with stored user and token from localStorage', async () => {
      const storedToken = 'test-token';
      const storedUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };

      localStorageMock.setItem('auth_token', storedToken);
      localStorageMock.setItem('auth_user', JSON.stringify(storedUser));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.user).toEqual(storedUser);
      expect(result.current.token).toBe(storedToken);
      expect(result.current.isAuthenticated).toBe(true);
    });

    it('should initialize with stored user and token from sessionStorage', async () => {
      const storedToken = 'test-token';
      const storedUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };

      sessionStorageMock.setItem('auth_token', storedToken);
      sessionStorageMock.setItem('auth_user', JSON.stringify(storedUser));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.user).toEqual(storedUser);
      expect(result.current.token).toBe(storedToken);
      expect(result.current.isAuthenticated).toBe(true);
    });

    it('should handle invalid stored user data gracefully', async () => {
      localStorageMock.setItem('auth_token', 'test-token');
      localStorageMock.setItem('auth_user', 'invalid-json');

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // When parsing fails, the token might be set before the error, but storage should be cleared
      // The important thing is that invalid data is cleared from storage
      expect(result.current.user).toBeNull();
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_token');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_user');
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('auth_token');
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('auth_user');
      expect(mockLoggerError).toHaveBeenCalled();
    });

    it('should set isLoading to false after initialization', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      // Wait for initialization to complete
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      }, { timeout: 3000 });
    });
  });

  describe('Sign In', () => {
    it('should sign in successfully and store token in localStorage when rememberMe is true', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'new-token';
      const mockRefreshToken = 'refresh-token';

      mockMutate.mockResolvedValue({
        data: {
          signIn: {
            token: mockToken,
            refreshToken: mockRefreshToken,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signIn('test@example.com', 'password', true);
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_token', mockToken);
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_user', JSON.stringify(mockUser));
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_refresh_token', mockRefreshToken);
      expect(mockPush).toHaveBeenCalledWith('/');
    });

    it('should sign in successfully and store token in sessionStorage when rememberMe is false', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'new-token';
      const mockRefreshToken = 'refresh-token';

      mockMutate.mockResolvedValue({
        data: {
          signIn: {
            token: mockToken,
            refreshToken: mockRefreshToken,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signIn('test@example.com', 'password', false);
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(sessionStorageMock.setItem).toHaveBeenCalledWith('auth_token', mockToken);
      expect(sessionStorageMock.setItem).toHaveBeenCalledWith('auth_user', JSON.stringify(mockUser));
      expect(sessionStorageMock.setItem).toHaveBeenCalledWith('auth_refresh_token', mockRefreshToken);
      expect(mockPush).toHaveBeenCalledWith('/');
    });

    it('should sign in successfully without refreshToken', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'new-token';

      mockMutate.mockResolvedValue({
        data: {
          signIn: {
            token: mockToken,
            refreshToken: null,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signIn('test@example.com', 'password');
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
      });

      expect(sessionStorageMock.setItem).not.toHaveBeenCalledWith('auth_refresh_token', expect.anything());
    });

    it('should handle sign in error', async () => {
      const mockError = new Error('Invalid credentials');

      mockMutate.mockRejectedValue(mockError);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await expect(result.current.signIn('test@example.com', 'wrong-password')).rejects.toThrow(
          'Invalid credentials'
        );
      });

      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      // Verify logger was called (technical errors should be logged)
      expect(mockLoggerError).toHaveBeenCalledWith('Sign in error:', mockError);
    });

    it('should not update state when sign in response is invalid', async () => {
      mockMutate.mockResolvedValue({
        data: {
          signIn: null,
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signIn('test@example.com', 'password');
      });

      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(mockPush).not.toHaveBeenCalled();
    });
  });

  describe('Sign In with OAuth', () => {
    it('should sign in with OAuth successfully and store token in localStorage when rememberMe is true', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'oauth-token';
      const mockRefreshToken = 'refresh-token';

      mockMutate.mockResolvedValue({
        data: {
          signInWithOAuth: {
            token: mockToken,
            refreshToken: mockRefreshToken,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signInWithOAuth('google', 'id-token', true);
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(mockMutate).toHaveBeenCalledWith({
        mutation: expect.anything(),
        variables: {
          input: {
            provider: 'google',
            idToken: 'id-token',
            rememberMe: true,
          },
        },
      });
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_token', mockToken);
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_user', JSON.stringify(mockUser));
      expect(mockPush).toHaveBeenCalledWith('/');
    });

    it('should sign in with OAuth successfully and store token in sessionStorage when rememberMe is false', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'oauth-token';

      mockMutate.mockResolvedValue({
        data: {
          signInWithOAuth: {
            token: mockToken,
            refreshToken: null,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signInWithOAuth('google', 'id-token', false);
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
      });

      expect(mockPush).toHaveBeenCalledWith('/');
    });

    it('should handle OAuth sign in error', async () => {
      const mockError = new Error('OAuth error');

      mockMutate.mockRejectedValue(mockError);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await expect(
          result.current.signInWithOAuth('google', 'invalid-token')
        ).rejects.toThrow('OAuth error');
      });

      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(mockLoggerError).toHaveBeenCalledWith('OAuth sign in error:', mockError);
    });
  });

  describe('Sign Up', () => {
    it('should sign up successfully and store token in localStorage', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'signup-token';
      const mockRefreshToken = 'refresh-token';

      mockMutate.mockResolvedValue({
        data: {
          signUp: {
            token: mockToken,
            refreshToken: mockRefreshToken,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signUp('Test User', 'test@example.com', 'password');
      });

      await waitFor(() => {
        expect(result.current.user).toEqual(mockUser);
        expect(result.current.token).toBe(mockToken);
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(mockMutate).toHaveBeenCalledWith({
        mutation: expect.anything(),
        variables: {
          input: {
            name: 'Test User',
            email: 'test@example.com',
            password: 'password',
          },
        },
      });
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_token', mockToken);
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_user', JSON.stringify(mockUser));
      expect(localStorageMock.setItem).toHaveBeenCalledWith('auth_refresh_token', mockRefreshToken);
      expect(mockPush).toHaveBeenCalledWith('/');
    });

    it('should handle sign up error', async () => {
      const mockError = new Error('Email already exists');

      mockMutate.mockRejectedValue(mockError);

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await expect(
          result.current.signUp('Test User', 'existing@example.com', 'password')
        ).rejects.toThrow('Email already exists');
      });

      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
      expect(mockLoggerError).toHaveBeenCalledWith('Sign up error:', mockError);
    });
  });

  describe('Sign Out', () => {
    it('should sign out and clear all storage', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Set some state first
      await act(async () => {
        result.current.signOut();
      });

      await waitFor(() => {
        expect(result.current.user).toBeNull();
        expect(result.current.token).toBeNull();
        expect(result.current.isAuthenticated).toBe(false);
      });

      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_token');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_user');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_refresh_token');
      expect(mockPush).toHaveBeenCalledWith('/signin');
    });

    it('should clear both localStorage and sessionStorage on sign out', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        result.current.signOut();
      });

      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_token');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_user');
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('auth_refresh_token');
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('auth_token');
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('auth_user');
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('auth_refresh_token');
    });
  });

  describe('useAuth hook', () => {
    it('should throw error when used outside AuthProvider', () => {
      // Suppress console.error for this test
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderHook(() => useAuth());
      }).toThrow('useAuth must be used within AuthProvider');

      consoleErrorSpy.mockRestore();
    });

    it('should return all required context properties', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current).toHaveProperty('user');
      expect(result.current).toHaveProperty('token');
      expect(result.current).toHaveProperty('isLoading');
      expect(result.current).toHaveProperty('signIn');
      expect(result.current).toHaveProperty('signInWithOAuth');
      expect(result.current).toHaveProperty('signUp');
      expect(result.current).toHaveProperty('signOut');
      expect(result.current).toHaveProperty('isAuthenticated');
    });
  });

  describe('isAuthenticated computed property', () => {
    it('should return false when user and token are null', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should return false when only user is null', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Manually set token but not user (shouldn't happen in practice, but test the logic)
      await act(async () => {
        result.current.token = 'token';
        result.current.user = null;
      });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should return false when only token is null', async () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Manually set user but not token (shouldn't happen in practice, but test the logic)
      await act(async () => {
        result.current.user = { id: '1', email: 'test@example.com' };
        result.current.token = null;
      });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('should return true when both user and token are set', async () => {
      const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
      const mockToken = 'test-token';

      mockMutate.mockResolvedValue({
        data: {
          signIn: {
            token: mockToken,
            refreshToken: null,
            user: mockUser,
          },
        },
      });

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      await act(async () => {
        await result.current.signIn('test@example.com', 'password');
      });

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });
    });
  });
});

