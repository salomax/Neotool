import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleManagement } from '../RoleManagement';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { Role } from '@/shared/hooks/authorization/useRoleManagement';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
  }),
}));

// Mock useRoleManagement hook
const mockRoles: Role[] = [
  { id: '1', name: 'Admin Role' },
  { id: '2', name: 'User Role' },
];

const DEFAULT_PAGE_SIZE = 13;

const buildRoleManagementHookReturn = (overrides: Record<string, any> = {}) => ({
  roles: mockRoles,
  searchQuery: '',
  inputValue: '',
  handleInputChange: vi.fn(),
  handleSearch: vi.fn(),
  pageInfo: {
    hasNextPage: false,
    hasPreviousPage: false,
    startCursor: null,
    endCursor: null,
  },
  paginationRange: {
    start: 1,
    end: 2,
    total: 2,
  },
  canLoadPreviousPage: false,
  loadNextPage: vi.fn(),
  loadPreviousPage: vi.fn(),
  goToFirstPage: vi.fn(),
  loading: false,
  error: undefined,
  refetch: vi.fn(),
  deleteRole: vi.fn().mockResolvedValue(undefined),
  deleteLoading: false,
  orderBy: null,
  handleSort: vi.fn(),
  setFirst: vi.fn(),
  ...overrides,
});

const mockUseRoleManagement = vi.fn();

vi.mock('@/shared/hooks/authorization/useRoleManagement', () => ({
  useRoleManagement: (args?: any) => {
    mockUseRoleManagement(args);
    return mockUseRoleManagement();
  },
}));

// Mock ManagementLayout
const { ManagementLayoutComponent } = vi.hoisted(() => {
  const Component = ({ children, error, onErrorRetry }: any) => (
    <div data-testid="management-layout">
      {error && <div data-testid="layout-error">Error occurred</div>}
      {onErrorRetry && <button onClick={onErrorRetry}>Retry</button>}
      {children}
    </div>
  );
  
  const Header = ({ children }: any) => <div data-testid="layout-header">{children}</div>;
  Header.displayName = 'ManagementLayout.Header';
  Component.Header = Header;
  
  const Content = ({ children }: any) => <div data-testid="layout-content">{children}</div>;
  Content.displayName = 'ManagementLayout.Content';
  Component.Content = Content;
  
  const Drawer = ({ children }: any) => <div data-testid="layout-drawer">{children}</div>;
  Drawer.displayName = 'ManagementLayout.Drawer';
  Component.Drawer = Drawer;
  
  return { ManagementLayoutComponent: Component };
});

vi.mock('@/shared/components/management/ManagementLayout', () => ({
  ManagementLayout: ManagementLayoutComponent,
}));

// Mock child components
vi.mock('../RoleSearch', () => ({
  RoleSearch: ({ value, onChange, onSearch, placeholder }: any) => (
    <input
      data-testid="role-search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={() => onSearch?.(value)}
      placeholder={placeholder}
    />
  ),
}));

vi.mock('../RoleList', () => ({
  RoleList: (props: any) => {
    (window as any).__roleListProps = props;
    const { roles, onEdit, onDelete, emptyMessage, loading } = props;
    return (
      <div data-testid="role-list">
        {loading && <div data-testid="list-loading">Loading...</div>}
        {!loading && roles.length === 0 && <div>{emptyMessage}</div>}
        {!loading && roles.length > 0 && (
          <div>
            {roles.map((role: Role) => (
              <div key={role.id} data-testid={`role-${role.id}`}>
                {role.name}
                <button onClick={() => onEdit(role)} data-testid={`edit-${role.id}`}>
                  Edit
                </button>
                {onDelete && (
                  <button onClick={() => onDelete(role)} data-testid={`delete-${role.id}`}>
                    Delete
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  },
}));

vi.mock('../RoleDrawer', () => ({
  RoleDrawer: ({ open, onClose, roleId }: any) =>
    open ? (
      <div data-testid="role-drawer">
        <div>Drawer {roleId ? `Editing ${roleId}` : 'Creating'}</div>
        <button onClick={onClose}>Close Drawer</button>
      </div>
    ) : null,
}));

// Mock DeleteConfirmationDialog
vi.mock('@/shared/components/ui/feedback', () => ({
  DeleteConfirmationDialog: ({ open, onConfirm, onCancel, loading }: any) =>
    open ? (
      <div data-testid="delete-dialog">
        <div>Confirm deletion</div>
        <button onClick={onConfirm} disabled={loading}>
          {loading ? 'Deleting...' : 'Delete'}
        </button>
        <button onClick={onCancel}>Cancel</button>
      </div>
    ) : null,
}));

// Mock PermissionGate
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'roleManagement.searchPlaceholder': 'Search roles...',
        'roleManagement.newButton': 'New Role',
        'roleManagement.emptySearchResults': 'No roles found matching your search',
        'roleManagement.emptyList': 'No roles found',
        'roleManagement.toast.roleDeleted': `Role ${params?.name || ''} deleted`,
        'roleManagement.toast.roleDeleteError': 'Failed to delete role',
        'errors.loadFailed': 'Failed to load data',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock useToast
const mockToast = {
  error: vi.fn(),
  success: vi.fn(),
};

vi.mock('@/shared/providers', () => ({
  useToast: () => mockToast,
}));

// Mock Box component
vi.mock('@/shared/components/ui/layout', () => ({
  Box: ({ children, maxWidth, minWidth, maxHeight, minHeight, component, sx, ...props }: any) => (
    <div {...props}>{children}</div>
  ),
}));

const renderRoleManagement = (
  props = {},
  options: { skipMeasurement?: boolean; pageSize?: number } = {}
) => {
  const { skipMeasurement = false, pageSize = DEFAULT_PAGE_SIZE } = options;
  const defaultProps = {
    initialSearchQuery: '',
    ...props,
  };

  const utils = render(
    <AppThemeProvider>
      <RoleManagement {...defaultProps} />
    </AppThemeProvider>
  );

  const measureTable = (size = pageSize) => {
    const roleListProps = (window as any).__roleListProps;
    if (!roleListProps?.onTableResize) {
      throw new Error("RoleList measurement props are not available");
    }
    act(() => {
      roleListProps.onTableResize(size);
    });
  };

  if (!skipMeasurement) {
    measureTable(pageSize);
  }

  return { ...utils, measureTable };
};

describe('RoleManagement', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__roleListProps = undefined;
    mockUseRoleManagement.mockImplementation(() => buildRoleManagementHookReturn());
  });

  describe('Rendering', () => {
    it('should render management layout', () => {
      renderRoleManagement();

      expect(screen.getByTestId('management-layout')).toBeInTheDocument();
    });

    it('should render role search', () => {
      renderRoleManagement();

      expect(screen.getByTestId('role-search')).toBeInTheDocument();
    });

    it('should render new role button', () => {
      renderRoleManagement();

      expect(screen.getByText('New Role')).toBeInTheDocument();
    });

    it('should render role list', () => {
      renderRoleManagement();

      expect(screen.getByTestId('role-list')).toBeInTheDocument();
    });

    it('should display roles', () => {
      renderRoleManagement();

      expect(screen.getByTestId('role-1')).toBeInTheDocument();
      expect(screen.getByText('Admin Role')).toBeInTheDocument();
      expect(screen.getByText('User Role')).toBeInTheDocument();
    });

    it('should pass initialSearchQuery and measured page size to hook', () => {
      renderRoleManagement({ initialSearchQuery: 'admin' }, { pageSize: 19 });

      expect(mockUseRoleManagement).toHaveBeenCalledWith({
        initialSearchQuery: 'admin',
        initialFirst: 19,
      });
    });
  });

  describe('Create role', () => {
    it('should open drawer in create mode when new button is clicked', async () => {
      renderRoleManagement();

      const newButton = screen.getByText('New Role');
      await user.click(newButton);

      expect(screen.getByTestId('role-drawer')).toBeInTheDocument();
      expect(screen.getByText('Drawer Creating')).toBeInTheDocument();
    });
  });

  describe('Edit role', () => {
    it('should open drawer in edit mode when edit button is clicked', async () => {
      renderRoleManagement();

      const editButton = screen.getByTestId('edit-1');
      await user.click(editButton);

      expect(screen.getByTestId('role-drawer')).toBeInTheDocument();
      expect(screen.getByText('Drawer Editing 1')).toBeInTheDocument();
    });
  });

  describe('Delete role', () => {
    it('should open delete confirmation dialog when delete button is clicked', async () => {
      renderRoleManagement();

      const deleteButton = screen.getByTestId('delete-1');
      await user.click(deleteButton);

      expect(screen.getByTestId('delete-dialog')).toBeInTheDocument();
    });

    it('should call deleteRole when confirmed', async () => {
      const deleteRole = vi.fn().mockResolvedValue(undefined);
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          deleteRole,
        })
      );

      renderRoleManagement();

      const deleteButton = screen.getByTestId('delete-1');
      await user.click(deleteButton);

      const dialog = screen.getByTestId('delete-dialog');
      const confirmButton = within(dialog).getByRole('button', { name: 'Delete' });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(deleteRole).toHaveBeenCalledWith('1');
      });
    });

    it('should show success toast when role is deleted', async () => {
      const deleteRole = vi.fn().mockResolvedValue(undefined);
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          deleteRole,
        })
      );

      renderRoleManagement();

      const deleteButton = screen.getByTestId('delete-1');
      await user.click(deleteButton);

      const dialog = screen.getByTestId('delete-dialog');
      const confirmButton = within(dialog).getByRole('button', { name: 'Delete' });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(mockToast.success).toHaveBeenCalled();
      });
    });

    it('should show error toast when delete fails', async () => {
      const deleteRole = vi.fn().mockRejectedValue(new Error('Delete failed'));
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          deleteRole,
        })
      );

      renderRoleManagement();

      const deleteButton = screen.getByTestId('delete-1');
      await user.click(deleteButton);

      const dialog = screen.getByTestId('delete-dialog');
      const confirmButton = within(dialog).getByRole('button', { name: 'Delete' });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalled();
      });
    });

    it('should close dialog when cancel is clicked', async () => {
      renderRoleManagement();

      const deleteButton = screen.getByTestId('delete-1');
      await user.click(deleteButton);

      const cancelButton = screen.getByText('Cancel');
      await user.click(cancelButton);

      expect(screen.queryByTestId('delete-dialog')).not.toBeInTheDocument();
    });
  });

  describe('Empty states', () => {
    it('should show empty search results message when search has no results', () => {
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          roles: [],
          searchQuery: 'nonexistent',
        })
      );

      renderRoleManagement();

      expect(screen.getByText('No roles found matching your search')).toBeInTheDocument();
    });

    it('should show empty list message when no roles exist', () => {
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          roles: [],
          searchQuery: '',
        })
      );

      renderRoleManagement();

      expect(screen.getByText('No roles found')).toBeInTheDocument();
    });
  });

  describe('Loading state', () => {
    it('should show loading state', () => {
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          loading: true,
        })
      );

      renderRoleManagement();

      expect(screen.getByTestId('list-loading')).toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('should show error in layout when error occurs', () => {
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          error: new Error('Failed to load') as any,
        })
      );

      renderRoleManagement();

      expect(screen.getByTestId('layout-error')).toBeInTheDocument();
    });

    it('should call refetch when retry is clicked', async () => {
      const refetch = vi.fn();
      mockUseRoleManagement.mockImplementation(() =>
        buildRoleManagementHookReturn({
          error: new Error('Failed to load') as any,
          refetch,
        })
      );

      renderRoleManagement();

      const retryButton = screen.getByText('Retry');
      await user.click(retryButton);

      expect(refetch).toHaveBeenCalled();
    });
  });

  describe('Drawer management', () => {
    it('should close drawer when drawer close is clicked', async () => {
      renderRoleManagement();

      const newButton = screen.getByText('New Role');
      await user.click(newButton);

      const closeButton = screen.getByText('Close Drawer');
      await user.click(closeButton);

      expect(screen.queryByTestId('role-drawer')).not.toBeInTheDocument();
    });
  });
});
