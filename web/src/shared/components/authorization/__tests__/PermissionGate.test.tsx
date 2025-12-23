import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { PermissionGate } from '../PermissionGate';

// Mock useCurrentUserQuery - not needed since we're mocking providers directly
vi.mock('@/lib/graphql/operations/auth/queries.generated', () => ({
  useCurrentUserQuery: vi.fn(() => ({
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
  })),
}));

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}));

// Mock AuthProvider to avoid localStorage reads
vi.mock('@/shared/providers/AuthProvider', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({
    user: { id: '1', email: 'test@example.com', displayName: 'Test User' },
    token: 'test-token',
    isLoading: false,
    signIn: vi.fn(),
    signInWithOAuth: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
    isAuthenticated: true,
  }),
}));

// Mock AuthorizationProvider - we'll control permissions per test
const mockPermissions = new Set<string>();
let mockLoading = false; // Mutable so we can change it in tests
const mockHas = vi.fn((permission: string) => mockPermissions.has(permission));
const mockHasAny = vi.fn((permissions: string[]) => 
  permissions.some(p => mockPermissions.has(p))
);
const mockHasAll = vi.fn((permissions: string[]) => 
  permissions.every(p => mockPermissions.has(p))
);

vi.mock('@/shared/providers/AuthorizationProvider', () => ({
  AuthorizationProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuthorization: () => ({
    permissions: mockPermissions,
    roles: [],
    loading: mockLoading,
    has: mockHas,
    hasAny: mockHasAny,
    hasAll: mockHasAll,
    refreshAuthorization: vi.fn(),
  }),
}));

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  return <>{children}</>;
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('PermissionGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset permissions for each test
    mockPermissions.clear();
    // Reset loading state
    mockLoading = false;
    // Reset mock implementations
    mockHas.mockImplementation((permission: string) => mockPermissions.has(permission));
    mockHasAny.mockImplementation((permissions: string[]) => 
      permissions.some(p => mockPermissions.has(p))
    );
    mockHasAll.mockImplementation((permissions: string[]) => 
      permissions.every(p => mockPermissions.has(p))
    );
  });

  afterEach(() => {
    cleanup();
  });

  describe('require prop', () => {
    it('should render children when user has required permission', () => {
      mockPermissions.add('security:user:view');

      render(
        <TestWrapper>
          <PermissionGate require="security:user:view">
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(screen.getByText('Authorized Content')).toBeInTheDocument();
    });

    it('should render fallback when user does not have required permission', () => {
      // No permissions added

      render(
        <TestWrapper>
          <PermissionGate require="security:user:view" fallback={<div>No Access</div>}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(screen.getByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });

    it('should require all permissions when require is an array', () => {
      mockPermissions.add('security:user:view');
      mockPermissions.add('security:user:save');

      render(
        <TestWrapper>
          <PermissionGate require={['security:user:view', 'security:user:save']}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      // Use getAllByText and get first element to handle multiple renders
      const contentElements = screen.getAllByText('Authorized Content');
      expect(contentElements[0]).toBeInTheDocument();
    });

    it('should not render children when user is missing any required permission', () => {
      mockPermissions.add('security:user:view');
      // Missing 'security:user:save'

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

      expect(screen.getByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });
  });

  describe('anyOf prop', () => {
    it('should render children when user has at least one permission', () => {
      mockPermissions.add('security:user:view');

      render(
        <TestWrapper>
          <PermissionGate anyOf={['security:user:view', 'security:user:save']}>
            <div>Authorized Content</div>
          </PermissionGate>
        </TestWrapper>
      );

      expect(screen.getByText('Authorized Content')).toBeInTheDocument();
    });

    it('should not render children when user has none of the permissions', () => {
      // No permissions added

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

      expect(screen.getByText('No Access')).toBeInTheDocument();
      expect(screen.queryByText('Authorized Content')).not.toBeInTheDocument();
    });

    it('should take precedence over require prop', () => {
      mockPermissions.add('security:user:view');
      // Missing 'security:user:save' which is in require

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
      // Use getAllByText and get first element to handle multiple renders
      const contentElements = screen.getAllByText('Authorized Content');
      expect(contentElements[0]).toBeInTheDocument();
    });
  });

  describe('loading state', () => {
    it('should render loadingFallback while loading', () => {
      // Set loading to true for this test
      mockLoading = true;

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
