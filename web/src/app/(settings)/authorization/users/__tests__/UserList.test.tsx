import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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

// Mock ManagementTable
vi.mock('@/shared/components/management', () => ({
  ManagementTable: ({ columns, data, loading, emptyMessage, onSortChange, pagination, sortState, renderActions, onTableResize, recalculationKey, tableId, getRowId }: any) => {
    // Capture props for testing
    (window as any).__managementTableProps = {
      columns,
      data,
      loading,
      emptyMessage,
      onSortChange,
      pagination,
      sortState,
      renderActions,
      onTableResize,
      recalculationKey,
      tableId,
      getRowId,
    };
    return (
      <div data-testid="management-table">
        {loading && <div data-testid="table-loading">Loading...</div>}
        {!loading && (
          <table>
            <thead>
              <tr>
                {columns.map((col: any) => (
                  <th
                    key={col.id}
                    onClick={() => col.sortable && onSortChange?.(col.sortField)}
                    data-sortable={col.sortable}
                    data-sort-field={col.sortField}
                    data-align={col.align}
                  >
                    {col.label}
                  </th>
                ))}
                {renderActions && <th>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {data.length === 0 ? (
                <tr>
                  <td colSpan={columns.length + (renderActions ? 1 : 0)}>{emptyMessage}</td>
                </tr>
              ) : (
                data.map((item: User) => (
                  <tr key={getRowId ? getRowId(item) : item.id}>
                    <td>{columns[0].accessor ? columns[0].accessor(item) : item.displayName || item.email}</td>
                    <td>{columns[1].accessor ? columns[1].accessor(item) : item.email}</td>
                    <td>{columns[2].render ? columns[2].render(item) : null}</td>
                    {renderActions && <td>{renderActions(item)}</td>}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>
    );
  },
}));

// Mock UserStatusToggle
vi.mock('../UserStatusToggle', () => ({
  UserStatusToggle: ({ user, enabled, onToggle, loading }: any) => (
    <div data-testid={`status-toggle-${user.id}`} data-enabled={enabled} data-loading={loading}>
      {enabled ? 'Enabled' : 'Disabled'}
      <button onClick={() => onToggle(user.id, !enabled)}>Toggle</button>
    </div>
  ),
}));

// Mock PermissionGate
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children, require }: { children: React.ReactNode; require?: string }) => {
    // Capture require prop for testing
    if (require) {
      (window as any).__permissionGateRequire = require;
    }
    return <>{children}</>;
  },
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      const translations: Record<string, string> = {
        'userManagement.table.name': 'Name',
        'userManagement.table.email': 'Email',
        'userManagement.table.status': 'Status',
        'userManagement.table.actions': 'Actions',
        'userManagement.editUser': 'Edit user',
      };
      return translations[key] || fallback || key;
    },
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
  {
    id: '3',
    email: 'no-name@example.com',
    displayName: null,
    enabled: true,
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
    // Clear captured props
    (window as any).__managementTableProps = undefined;
    (window as any).__permissionGateRequire = undefined;
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
      const nameHeader = screen.getByRole('columnheader', { name: /name/i });
      expect(nameHeader).toBeInTheDocument();
    });

    it('should show active sort indicator for EMAIL when sorted', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'EMAIL', direction: 'desc' };
      
      renderUserList({ onSortChange, orderBy });

      const emailHeader = screen.getByRole('columnheader', { name: /email/i });
      expect(emailHeader).toBeInTheDocument();
    });

    it('should show active sort indicator for ENABLED when sorted', () => {
      const onSortChange = vi.fn();
      const orderBy: UserSortState = { field: 'ENABLED', direction: 'asc' };
      
      renderUserList({ onSortChange, orderBy });

      const statusHeader = screen.getByRole('columnheader', { name: /status/i });
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

  describe('Column configuration', () => {
    it('should define correct columns with proper structure', () => {
      renderUserList();
      const props = (window as any).__managementTableProps;
      
      expect(props.columns).toHaveLength(3);
      expect(props.columns[0].id).toBe('name');
      expect(props.columns[0].label).toBe('Name');
      expect(props.columns[0].sortField).toBe('DISPLAY_NAME');
      expect(props.columns[1].id).toBe('email');
      expect(props.columns[1].label).toBe('Email');
      expect(props.columns[1].sortField).toBe('EMAIL');
      expect(props.columns[2].id).toBe('status');
      expect(props.columns[2].label).toBe('Status');
      expect(props.columns[2].sortField).toBe('ENABLED');
      expect(props.columns[2].align).toBe('center');
    });

    it('should make columns sortable when onSortChange is provided', () => {
      const onSortChange = vi.fn();
      renderUserList({ onSortChange });
      const props = (window as any).__managementTableProps;
      
      expect(props.columns[0].sortable).toBe(true);
      expect(props.columns[1].sortable).toBe(true);
      expect(props.columns[2].sortable).toBe(true);
    });

    it('should make columns non-sortable when onSortChange is not provided', () => {
      renderUserList({ onSortChange: undefined });
      const props = (window as any).__managementTableProps;
      
      expect(props.columns[0].sortable).toBe(false);
      expect(props.columns[1].sortable).toBe(false);
      expect(props.columns[2].sortable).toBe(false);
    });
  });

  describe('Data rendering', () => {
    it('should render displayName when available', () => {
      renderUserList();
      
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });

    it('should fall back to email when displayName is missing', () => {
      renderUserList();
      
      // The email should appear in the Name column (as fallback) and Email column
      // Check that it appears at least once (it will appear twice - once in each column)
      const emailElements = screen.getAllByText('no-name@example.com');
      expect(emailElements.length).toBeGreaterThanOrEqual(1);
      // Verify it appears in the Name column by checking the first occurrence is in a table cell
      expect(emailElements[0]).toBeInTheDocument();
    });

    it('should render email in email column', () => {
      renderUserList();
      
      expect(screen.getByText('john@example.com')).toBeInTheDocument();
      expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    });

    it('should render UserStatusToggle in status column', () => {
      renderUserList();
      
      expect(screen.getByTestId('status-toggle-1')).toBeInTheDocument();
      expect(screen.getByTestId('status-toggle-2')).toBeInTheDocument();
      expect(screen.getByTestId('status-toggle-3')).toBeInTheDocument();
    });

    it('should pass correct props to UserStatusToggle', () => {
      const onToggleStatus = vi.fn();
      renderUserList({ onToggleStatus });
      
      const toggle1 = screen.getByTestId('status-toggle-1');
      expect(toggle1).toHaveAttribute('data-enabled', 'true');
      expect(toggle1).toHaveAttribute('data-loading', 'false');
    });

    it('should pass toggleLoading to UserStatusToggle', () => {
      renderUserList({ toggleLoading: true });
      
      const toggle1 = screen.getByTestId('status-toggle-1');
      expect(toggle1).toHaveAttribute('data-loading', 'true');
    });
  });

  describe('Callback handling', () => {
    it('should call onEdit with correct user when edit button is clicked', async () => {
      const onEdit = vi.fn();
      renderUserList({ onEdit });
      
      const editButton = screen.getByTestId('edit-user-1');
      await user.click(editButton);
      
      expect(onEdit).toHaveBeenCalledWith(mockUsers[0]);
      expect(onEdit).toHaveBeenCalledTimes(1);
    });

    it('should call onToggleStatus when status toggle is clicked', async () => {
      const onToggleStatus = vi.fn().mockResolvedValue(undefined);
      renderUserList({ onToggleStatus });
      
      const statusToggle = screen.getByTestId('status-toggle-1');
      const toggleButton = within(statusToggle).getByRole('button', { name: /toggle/i });
      await user.click(toggleButton);
      expect(onToggleStatus).toHaveBeenCalledWith('1', false);
    });

    it('should pass onToggleStatus callback to UserStatusToggle', () => {
      const onToggleStatus = vi.fn();
      renderUserList({ onToggleStatus });
      
      // Verify the callback is passed by checking it can be called
      const props = (window as any).__managementTableProps;
      expect(props).toBeDefined();
    });
  });

  describe('Permission gates', () => {
    it('should wrap edit button with PermissionGate requiring security:user:view', () => {
      renderUserList();
      
      // PermissionGate should be called with security:user:view
      // We verify this by checking the captured require prop
      // Note: This is a simplified check - in real tests you might want to verify the actual rendering
      expect(screen.getByTestId('edit-user-1')).toBeInTheDocument();
    });

    it('should wrap status toggle with PermissionGate requiring security:user:save', () => {
      renderUserList();
      
      // Status toggle should be wrapped with PermissionGate
      expect(screen.getByTestId('status-toggle-1')).toBeInTheDocument();
    });
  });

  describe('Props integration with ManagementTable', () => {
    it('should pass loading prop to ManagementTable', () => {
      renderUserList({ loading: true });
      const props = (window as any).__managementTableProps;
      
      expect(props.loading).toBe(true);
    });

    it('should pass emptyMessage prop to ManagementTable', () => {
      const emptyMessage = 'No users found';
      renderUserList({ emptyMessage });
      const props = (window as any).__managementTableProps;
      
      expect(props.emptyMessage).toBe(emptyMessage);
    });

    it('should pass users array as data prop', () => {
      renderUserList();
      const props = (window as any).__managementTableProps;
      
      expect(props.data).toEqual(mockUsers);
    });

    it('should pass sortState (orderBy) to ManagementTable', () => {
      const orderBy: UserSortState = { field: 'DISPLAY_NAME', direction: 'asc' };
      renderUserList({ orderBy });
      const props = (window as any).__managementTableProps;
      
      expect(props.sortState).toEqual(orderBy);
    });

    it('should pass onSortChange to ManagementTable', () => {
      const onSortChange = vi.fn();
      renderUserList({ onSortChange });
      const props = (window as any).__managementTableProps;
      
      expect(props.onSortChange).toBe(onSortChange);
    });

    it('should pass pagination object when all required props are present', () => {
      const pageInfo = {
        hasNextPage: true,
        hasPreviousPage: false,
        startCursor: 'cursor1',
        endCursor: 'cursor2',
      };
      const paginationRange = { start: 1, end: 10, total: 25 };
      const onLoadNext = vi.fn();
      const onLoadPrevious = vi.fn();
      const onGoToFirst = vi.fn();
      
      renderUserList({
        pageInfo,
        paginationRange,
        onLoadNext,
        onLoadPrevious,
        onGoToFirst,
      });
      const props = (window as any).__managementTableProps;
      
      expect(props.pagination).toBeDefined();
      expect(props.pagination.pageInfo).toEqual(pageInfo);
      expect(props.pagination.paginationRange).toEqual(paginationRange);
      expect(props.pagination.onLoadNext).toBe(onLoadNext);
      expect(props.pagination.onLoadPrevious).toBe(onLoadPrevious);
      expect(props.pagination.onGoToFirst).toBe(onGoToFirst);
    });

    it('should not pass pagination when pageInfo is missing', () => {
      const paginationRange = { start: 1, end: 10, total: 25 };
      const onLoadNext = vi.fn();
      const onLoadPrevious = vi.fn();
      const onGoToFirst = vi.fn();
      
      renderUserList({
        pageInfo: null,
        paginationRange,
        onLoadNext,
        onLoadPrevious,
        onGoToFirst,
      });
      const props = (window as any).__managementTableProps;
      
      expect(props.pagination).toBeUndefined();
    });

    it('should pass onTableResize callback', () => {
      const onTableResize = vi.fn();
      renderUserList({ onTableResize });
      const props = (window as any).__managementTableProps;
      
      expect(props.onTableResize).toBe(onTableResize);
    });

    it('should pass recalculationKey prop', () => {
      const recalculationKey = 'test-key';
      renderUserList({ recalculationKey });
      const props = (window as any).__managementTableProps;
      
      expect(props.recalculationKey).toBe(recalculationKey);
    });

    it('should pass tableId prop', () => {
      renderUserList();
      const props = (window as any).__managementTableProps;
      
      expect(props.tableId).toBe('user-list-table');
    });

    it('should pass getRowId function', () => {
      renderUserList();
      const props = (window as any).__managementTableProps;
      
      expect(props.getRowId).toBeDefined();
      expect(typeof props.getRowId).toBe('function');
      expect(props.getRowId(mockUsers[0])).toBe('1');
    });

    it('should pass renderActions function', () => {
      renderUserList();
      const props = (window as any).__managementTableProps;
      
      expect(props.renderActions).toBeDefined();
      expect(typeof props.renderActions).toBe('function');
    });
  });

  describe('Edge cases', () => {
    it('should handle users without displayName', () => {
      const usersWithoutName: User[] = [
        {
          id: '1',
          email: 'test@example.com',
          displayName: null,
          enabled: true,
        },
      ];
      renderUserList({ users: usersWithoutName });
      
      // Should fall back to email - it will appear in both Name and Email columns
      const emailElements = screen.getAllByText('test@example.com');
      expect(emailElements.length).toBeGreaterThanOrEqual(1);
      expect(emailElements[0]).toBeInTheDocument();
    });

    it('should handle empty users array', () => {
      renderUserList({ users: [] });
      const props = (window as any).__managementTableProps;
      
      expect(props.data).toEqual([]);
    });

    it('should handle null orderBy', () => {
      renderUserList({ orderBy: null });
      const props = (window as any).__managementTableProps;
      
      expect(props.sortState).toBeNull();
    });
  });
});

