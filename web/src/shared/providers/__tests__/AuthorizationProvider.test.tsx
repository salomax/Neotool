import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { AuthProvider } from '../AuthProvider';
import { AuthorizationProvider, useAuthorization } from '../AuthorizationProvider';

// Mock Next.js router
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Apollo Client
const mockQuery = vi.fn();
vi.mock('@/lib/graphql/client', () => ({
  apolloClient: {
    query: mockQuery,
  },
}));

// Mock useCurrentUserQuery
const mockUseCurrentUserQuery = vi.fn();
vi.mock('@/lib/graphql/operations/auth/queries.generated', () => ({
  useCurrentUserQuery: (options: any) => mockUseCurrentUserQuery(options),
}));

// Mock AuthProvider's useAuth
const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
const defaultMockAuthContext = {
  user: mockUser,
  token: 'test-token',
  isLoading: false,
  signIn: vi.fn(),
  signInWithOAuth: vi.fn(),
  signUp: vi.fn(),
  signOut: vi.fn(),
  isAuthenticated: true,
};

// Create a mutable mock context that can be changed per test
let mockAuthContext = { ...defaultMockAuthContext };
const mockUseAuth = vi.fn(() => mockAuthContext);

vi.mock('../AuthProvider', async () => {
  const actual = await vi.importActual('../AuthProvider');
  return {
    ...actual,
    useAuth: mockUseAuth,
  };
});

describe('AuthorizationProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset mock auth context to default
    mockAuthContext = { ...defaultMockAuthContext };
    mockUseCurrentUserQuery.mockReturnValue({
      data: {
        currentUser: {
          id: '1',
          email: 'test@example.com',
          displayName: 'Test User',
          roles: [
            { id: '1', name: 'Admin' },
            { id: '2', name: 'User' },
          ],
          permissions: [
            { id: '1', name: 'security:user:view' },
            { id: '2', name: 'security:user:save' },
            { id: '3', name: 'security:role:view' },
          ],
        },
      },
      loading: false,
      refetch: vi.fn().mockResolvedValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [{ id: '1', name: 'Admin' }],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
      }),
    });
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <AuthProvider>
      <AuthorizationProvider>{children}</AuthorizationProvider>
    </AuthProvider>
  );

  describe('Initialization', () => {
    it('should initialize with permissions and roles from currentUser query', async () => {
      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.permissions.size).toBe(3);
      expect(result.current.permissions.has('security:user:view')).toBe(true);
      expect(result.current.permissions.has('security:user:save')).toBe(true);
      expect(result.current.permissions.has('security:role:view')).toBe(true);
      expect(result.current.roles).toHaveLength(2);
      expect(result.current.roles[0].name).toBe('Admin');
    });

    it('should handle empty permissions and roles', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.permissions.size).toBe(0);
      expect(result.current.roles).toHaveLength(0);
    });

    it('should reset state when user is not authenticated', async () => {
      // Override mock auth context for this test
      mockAuthContext = {
        ...defaultMockAuthContext,
        user: null,
        token: null,
        isAuthenticated: false,
      };

      mockUseCurrentUserQuery.mockReturnValue({
        data: null,
        loading: false,
        refetch: vi.fn(),
      });

      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.permissions.size).toBe(0);
      expect(result.current.roles).toHaveLength(0);
    });
  });

  describe('Permission checks', () => {
    it('should return true for has() when permission exists', async () => {
      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.has('security:user:view')).toBe(true);
      expect(result.current.has('security:user:save')).toBe(true);
      expect(result.current.has('nonexistent:permission')).toBe(false);
    });

    it('should return true for hasAny() when at least one permission exists', async () => {
      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.hasAny(['security:user:view', 'nonexistent'])).toBe(true);
      expect(result.current.hasAny(['nonexistent1', 'nonexistent2'])).toBe(false);
    });

    it('should return true for hasAll() when all permissions exist', async () => {
      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.hasAll(['security:user:view', 'security:user:save'])).toBe(true);
      expect(result.current.hasAll(['security:user:view', 'nonexistent'])).toBe(false);
    });
  });

  describe('refreshAuthorization', () => {
    it('should refetch currentUser query', async () => {
      const mockRefetch = vi.fn().mockResolvedValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [{ id: '1', name: 'Admin' }],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
      });

      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [],
          },
        },
        loading: false,
        refetch: mockRefetch,
      });

      const { result } = renderHook(() => useAuthorization(), { wrapper });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      await act(async () => {
        await result.current.refreshAuthorization();
      });

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('Error handling', () => {
    it('should throw error when used outside provider', () => {
      expect(() => {
        renderHook(() => useAuthorization());
      }).toThrow('useAuthorization must be used within AuthorizationProvider');
    });
  });
});
