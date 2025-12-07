import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PermissionGate } from '../PermissionGate';
import { AuthorizationProvider } from '@/shared/providers/AuthorizationProvider';
import { AuthProvider } from '@/shared/providers/AuthProvider';

// Mock useCurrentUserQuery
const mockUseCurrentUserQuery = vi.fn();
vi.mock('@/lib/graphql/operations/auth/queries.generated', () => ({
  useCurrentUserQuery: (options: any) => mockUseCurrentUserQuery(options),
}));

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}));

// Mock AuthProvider
const mockUser = { id: '1', email: 'test@example.com', displayName: 'Test User' };
const mockAuthContext = {
  user: mockUser,
  token: 'test-token',
  isLoading: false,
  signIn: vi.fn(),
  signInWithOAuth: vi.fn(),
  signUp: vi.fn(),
  signOut: vi.fn(),
  isAuthenticated: true,
};

vi.mock('@/shared/providers/AuthProvider', async () => {
  const actual = await vi.importActual('@/shared/providers/AuthProvider');
  return {
    ...actual,
    useAuth: () => mockAuthContext,
  };
});

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  return (
    <AuthProvider>
      <AuthorizationProvider>{children}</AuthorizationProvider>
    </AuthProvider>
  );
};

describe('PermissionGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('require prop', () => {
    it('should render children when user has required permission', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate require="security:user:view">
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('Authorized Content')).toBeInTheDocument();
    });

    it('should render fallback when user does not have required permission', async () => {
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

      render(
        <TestWrapper>
          <PermissionGate require="security:user:view" fallback={<div>No Access</div>}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });

    it('should require all permissions when require is an array', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [
              { id: '1', name: 'security:user:view' },
              { id: '2', name: 'security:user:save' },
            ],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate require={['security:user:view', 'security:user:save']}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('Authorized Content')).toBeInTheDocument();
    });

    it('should not render children when user is missing any required permission', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate
            require={['security:user:view', 'security:user:save']}
            fallback={<div>No Access</div>}
          >
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });
  });

  describe('anyOf prop', () => {
    it('should render children when user has at least one permission', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate anyOf={['security:user:view', 'security:user:save']}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('Authorized Content')).toBeInTheDocument();
    });

    it('should not render children when user has none of the permissions', async () => {
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

      render(
        <TestWrapper>
          <PermissionGate
            anyOf={['security:user:view', 'security:user:save']}
            fallback={<div>No Access</div>}
          >
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(await screen.findByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });

    it('should take precedence over require prop', async () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: {
          currentUser: {
            id: '1',
            email: 'test@example.com',
            displayName: 'Test User',
            roles: [],
            permissions: [{ id: '1', name: 'security:user:view' }],
          },
        },
        loading: false,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate
            require="security:user:save"
            anyOf={['security:user:view']}
          >
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      // anyOf should take precedence, so user with security:user:view should see content
      expect(await screen.findByText('Authorized Content')).toBeInTheDocument();
    });
  });

  describe('loading state', () => {
    it('should render loadingFallback while loading', () => {
      mockUseCurrentUserQuery.mockReturnValue({
        data: null,
        loading: true,
        refetch: vi.fn(),
      });

      render(
        <TestWrapper>
          <PermissionGate
            require="security:user:view"
            loadingFallback={<div>Loading...</div>}
          >
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(screen.getByText('Loading...')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });
  });
});
