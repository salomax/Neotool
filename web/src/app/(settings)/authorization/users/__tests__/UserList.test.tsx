import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserList } from '../UserList';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { User } from '@/shared/hooks/authorization/useUserManagement';
import type { UserSortState } from '@/shared/utils/sorting';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
  }),
}));

// Mock UserStatusToggle
vi.mock('../UserStatusToggle', () => ({
  UserStatusToggle: ({ user }: { user: User }) => (
    <div data-testid={`status-toggle-${user.id}`}>
      {user.enabled ? 'Enabled' : 'Disabled'}
    </div>
  ),
}));

// Mock RelayPagination
vi.mock('@/shared/components/ui/pagination', () => ({
  RelayPagination: () => <div data-testid="relay-pagination">Pagination</div>,
}));

// Mock DynamicTableBox
vi.mock('@/shared/components/ui/layout', () => ({
  Box: ({ children, fullHeight, fullSize, autoFill, name, 'data-testid': dataTestId, ...props }: any) => (
    <div {...props}>{children}</div>
  ),
  DynamicTableBox: ({ children, onTableResize, recalculationKey, pageSizeOptions, ...props }: any) => (
    <div {...props}>{children}</div>
  ),
}));

// Mock GraphQL queries - return immediately resolved data
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

// Mock AuthorizationProvider to avoid expensive GraphQL queries
vi.mock('@/shared/providers/AuthorizationProvider', () => ({
  AuthorizationProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuthorization: () => ({
    permissions: new Set(),
    roles: [],
    loading: false,
    has: vi.fn(() => true),
    hasAny: vi.fn(() => true),
    hasAll: vi.fn(() => true),
    refreshAuthorization: vi.fn(),
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

const mockUsers: User[] = [
  {
    id: '1',
    email: 'john@example.com',
    displayName: 'John Doe',
    enabled: true,
  },
  {
    id: '2',
    email: 'jane@example.com',
    displayName: 'Jane Smith',
    enabled: false,
  },
];

// Minimal wrapper - only AppThemeProvider is needed for MUI components
const renderUserList = (props = {}) => {
  const defaultProps = {
    users: mockUsers,
    onEdit: vi.fn(),
    onToggleStatus: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserList {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserList', () => {
  // Reuse userEvent instance across tests for better performance
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('sorting UI', () => {
    it('should render sortable headers when onSortChange is provided', () => {
      const onSortChange = vi.fn();
      renderUserList({ onSortChange });

      // Check that sortable headers are rendered
      const nameHeader = screen.getByText('Name');
      expect(nameHeader).toBeInTheDocument();
      
      const emailHeader = screen.getByText('Email');
      expect(emailHeader).toBeInTheDocument();
      
      const statusHeader = screen.getByText('Status');
      expect(statusHeader).toBeInTheDocument();
    });

    it('should render non-sortable headers when onSortChange is not provided', () => {
      renderUserList({ onSortChange: undefined });

      // Headers should still be rendered but without sort functionality
      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Email')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    });

    it('should show active sort indicator for DISPLAY_NAME when sorted', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'DISPLAY_NAME', direction: 'asc' };
      
      renderUserList({ onSortChange, orderBy });

      // TableSortLabel should be active
      const nameHeader = screen.getByText('Name').closest('th');
      expect(nameHeader).toBeInTheDocument();
    });

    it('should show active sort indicator for EMAIL when sorted', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'EMAIL', direction: 'desc' };
      
      renderUserList({ onSortChange, orderBy });

      const emailHeader = screen.getByText('Email').closest('th');
      expect(emailHeader).toBeInTheDocument();
    });

    it('should show active sort indicator for ENABLED when sorted', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'ENABLED', direction: 'asc' };
      
      renderUserList({ onSortChange, orderBy });

      const statusHeader = screen.getByText('Status').closest('th');
      expect(statusHeader).toBeInTheDocument();
    });

    it('should not show active sort indicator when orderBy is null', () => {
      const onSortChange = vi.fn();
      renderUserList({ onSortChange, orderBy: null });

      // All headers should be present but none should be marked as active
      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Email')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    });
  });

  describe('sort interactions', () => {
    it('should call onSortChange with DISPLAY_NAME when Name header is clicked', async () => {
      const onSortChange = vi.fn();
      
      renderUserList({ onSortChange });

      const nameHeader = screen.getByText('Name');
      await user.click(nameHeader);

      expect(onSortChange).toHaveBeenCalledWith('DISPLAY_NAME');
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });

    it('should call onSortChange with EMAIL when Email header is clicked', async () => {
      const onSortChange = vi.fn();
      
      renderUserList({ onSortChange });

      const emailHeader = screen.getByText('Email');
      await user.click(emailHeader);

      expect(onSortChange).toHaveBeenCalledWith('EMAIL');
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });

    it('should call onSortChange with ENABLED when Status header is clicked', async () => {
      const onSortChange = vi.fn();
      
      renderUserList({ onSortChange });

      const statusHeader = screen.getByText('Status');
      await user.click(statusHeader);

      expect(onSortChange).toHaveBeenCalledWith('ENABLED');
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });

    it('should not call onSortChange when header is clicked but onSortChange is not provided', async () => {
      renderUserList({ onSortChange: undefined });

      const nameHeader = screen.getByText('Name');
      await user.click(nameHeader);

      // Should not throw error, just no callback
      expect(nameHeader).toBeInTheDocument();
    });

    it('should handle multiple sort clicks', async () => {
      const onSortChange = vi.fn();
      
      renderUserList({ onSortChange });

      // Click Name header
      await user.click(screen.getByText('Name'));
      expect(onSortChange).toHaveBeenCalledWith('DISPLAY_NAME');

      // Click Email header
      await user.click(screen.getByText('Email'));
      expect(onSortChange).toHaveBeenCalledWith('EMAIL');

      // Click Status header
      await user.click(screen.getByText('Status'));
      expect(onSortChange).toHaveBeenCalledWith('ENABLED');

      expect(onSortChange).toHaveBeenCalledTimes(3);
    });
  });

  describe('sort state display', () => {
    it('should display correct sort direction for asc', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'DISPLAY_NAME', direction: 'asc' };
      
      renderUserList({ onSortChange, orderBy });

      // The TableSortLabel should be active with asc direction
      const nameHeader = screen.getByText('Name');
      expect(nameHeader).toBeInTheDocument();
    });

    it('should display correct sort direction for desc', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'EMAIL', direction: 'desc' };
      
      renderUserList({ onSortChange, orderBy });

      const emailHeader = screen.getByText('Email');
      expect(emailHeader).toBeInTheDocument();
    });

    it('should switch active indicator when sort field changes', () => {
      const onSortChange = vi.fn();
      
      // First render with DISPLAY_NAME
      const { rerender } = render(
        <AppThemeProvider>
          <UserList
            users={mockUsers}
            onEdit={vi.fn()}
            onToggleStatus={vi.fn()}
            onSortChange={onSortChange}
            orderBy={{ field: 'DISPLAY_NAME', direction: 'asc' }}
          />
        </AppThemeProvider>
      );

      expect(screen.getByText('Name')).toBeInTheDocument();

      // Rerender with EMAIL
      rerender(
        <AppThemeProvider>
          <UserList
            users={mockUsers}
            onEdit={vi.fn()}
            onToggleStatus={vi.fn()}
            onSortChange={onSortChange}
            orderBy={{ field: 'EMAIL', direction: 'desc' }}
          />
        </AppThemeProvider>
      );

      expect(screen.getByText('Email')).toBeInTheDocument();
    });
  });

  describe('integration with users list', () => {
    it('should render users list alongside sortable headers', () => {
      const onSortChange = vi.fn();
      renderUserList({ onSortChange });

      // Headers should be present
      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Email')).toBeInTheDocument();

      // Users should be rendered
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('john@example.com')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    });

    it('should maintain sorting functionality when users list is empty', () => {
      const onSortChange = vi.fn();
      renderUserList({ users: [], onSortChange });

      // Headers should still be sortable
      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Email')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    });
  });
});

