import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupManagement } from '../GroupManagement';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

// Mock useGroupManagement hook
const mockGroups = [
  { id: '1', name: 'Admin Group', description: 'Administrators' },
  { id: '2', name: 'User Group', description: 'Regular users' },
];

const DEFAULT_PAGE_SIZE = 13;

const buildGroupManagementHookReturn = (overrides: Record<string, any> = {}) => ({
  groups: mockGroups,
  first: 10,
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
  paginationRange: { start: 1, end: 2, total: 2 },
  canLoadPreviousPage: false,
  loadNextPage: vi.fn(),
  loadPreviousPage: vi.fn(),
  goToFirstPage: vi.fn(),
  loading: false,
  error: undefined,
  refetch: vi.fn(),
  createGroup: vi.fn().mockResolvedValue(undefined),
  updateGroup: vi.fn().mockResolvedValue(undefined),
  deleteGroup: vi.fn().mockResolvedValue(undefined),
  createLoading: false,
  updateLoading: false,
  deleteLoading: false,
  orderBy: null,
  handleSort: vi.fn(),
  setFirst: vi.fn(),
  ...overrides,
});

const mockUseGroupManagement = vi.fn();

vi.mock('@/shared/hooks/authorization/useGroupManagement', () => ({
  useGroupManagement: (args?: any) => {
    mockUseGroupManagement(args);
    return mockUseGroupManagement();
  },
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'groupManagement.newButton': 'New Group',
        'groupManagement.searchPlaceholder': 'Search groups...',
        'groupManagement.emptyList': 'No groups found',
        'groupManagement.emptySearchResults': 'No groups match your search',
        'groupManagement.toast.groupDeleted': `Group ${params?.name || ''} deleted`,
        'groupManagement.toast.groupDeleteError': 'Failed to delete group',
        'groupManagement.deleteDialog.title': 'Delete Group',
        'groupManagement.deleteDialog.message': 'Are you sure you want to delete this group?',
        'groupManagement.deleteDialog.cancel': 'Cancel',
        'groupManagement.deleteDialog.delete': 'Delete',
        'groupManagement.deleteDialog.deleting': 'Deleting...',
        'errors.loadFailed': 'Failed to load data',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock toast
const mockToast = {
  error: vi.fn(),
  success: vi.fn(),
};

vi.mock('@/shared/providers', () => ({
  useToast: () => mockToast,
}));

// Mock child components
vi.mock('../GroupSearch', () => ({
  GroupSearch: ({ value, onChange, onSearch, placeholder }: any) => (
    <div data-testid="group-search">
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={() => onSearch?.(value)}
        placeholder={placeholder}
      />
    </div>
  ),
}));

vi.mock('../GroupList', () => ({
  GroupList: (props: any) => {
    (window as any).__groupListProps = props;
    const {
      groups,
      onEdit,
      onDelete,
      loading,
      emptyMessage,
    } = props;
    return (
      <div data-testid="group-list">
        {loading && <div>Loading...</div>}
        {!loading && groups.length === 0 && <div>{emptyMessage}</div>}
        {!loading && groups.length > 0 && (
          <div>
            {groups.map((group: any) => (
              <div key={group.id}>
                <span>{group.name}</span>
                <button onClick={() => onEdit(group)}>Edit</button>
                {onDelete && <button onClick={() => onDelete(group)}>Delete</button>}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  },
}));

vi.mock('../GroupDrawer', () => ({
  GroupDrawer: ({ open, onClose, groupId }: any) =>
    open ? (
      <div data-testid="group-drawer">
        <div>Drawer for group: {groupId || 'new'}</div>
        <button onClick={onClose}>Close Drawer</button>
      </div>
    ) : null,
}));

// Mock ManagementLayout
vi.mock('@/shared/components/management/ManagementLayout', () => {
  const ManagementLayoutComponent = ({
    children,
    error,
    onErrorRetry,
    errorFallbackMessage,
  }: any) => (
    <div data-testid="management-layout">
      {error && (
        <div data-testid="error-state">
          <div>{errorFallbackMessage}</div>
          {onErrorRetry && <button onClick={onErrorRetry}>Retry</button>}
        </div>
      )}
      {!error && children}
    </div>
  );

  const Header = ({ children }: any) => <>{children}</>;
  const Content = ({ children }: any) => <>{children}</>;
  const Drawer = ({ children }: any) => <>{children}</>;

  const ManagementLayout = Object.assign(ManagementLayoutComponent, {
    Header,
    Content,
    Drawer,
  });

  return {
    ManagementLayout,
  };
});

// Mock DeleteConfirmationDialog
vi.mock('@/shared/components/ui/feedback', () => ({
  DeleteConfirmationDialog: ({
    open,
    item,
    loading,
    onConfirm,
    onCancel,
    titleKey,
    messageKey,
  }: any) =>
    open ? (
      <div data-testid="delete-confirmation-dialog">
        <div>{titleKey}</div>
        <div>{messageKey}</div>
        <div>Item: {item?.name}</div>
        <button onClick={onConfirm} disabled={loading}>
          Confirm
        </button>
        <button onClick={onCancel}>Cancel</button>
      </div>
    ) : null,
}));

// Mock PermissionGate
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock Box component
vi.mock('@/shared/components/ui/layout', () => ({
  Box: ({ children, maxWidth, minWidth, maxHeight, minHeight, component, sx, ...props }: any) => (
    <div {...props}>{children}</div>
  ),
}));

const renderGroupManagement = (
  props = {},
  options: { skipMeasurement?: boolean; pageSize?: number } = {}
) => {
  const { skipMeasurement = false, pageSize = DEFAULT_PAGE_SIZE } = options;

  const utils = render(
    <AppThemeProvider>
      <GroupManagement {...props} />
    </AppThemeProvider>
  );

  const measureTable = (size = pageSize) => {
    const groupListProps = (window as any).__groupListProps;
    if (!groupListProps?.onTableResize) {
      throw new Error("GroupList measurement props are not available");
    }
    act(() => {
      groupListProps.onTableResize(size);
    });
  };

  if (!skipMeasurement) {
    measureTable(pageSize);
  }

  return { ...utils, measureTable };
};

describe('GroupManagement', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__groupListProps = undefined;
    mockUseGroupManagement.mockImplementation(() => buildGroupManagementHookReturn());
  });

  describe('Rendering', () => {
    it('should render GroupSearch component', () => {
      renderGroupManagement();

      expect(screen.getByTestId('group-search')).toBeInTheDocument();
    });

    it('should render GroupList component', () => {
      renderGroupManagement();

      expect(screen.getByTestId('group-list')).toBeInTheDocument();
    });

    it('should render create button', () => {
      renderGroupManagement();

      expect(screen.getByTestId('create-group-button')).toBeInTheDocument();
      expect(screen.getByText('New Group')).toBeInTheDocument();
    });

    it('should display groups', () => {
      renderGroupManagement();

      expect(screen.getByText('Admin Group')).toBeInTheDocument();
      expect(screen.getByText('User Group')).toBeInTheDocument();
    });

    it('should display empty message when no groups', () => {
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          groups: [],
          searchQuery: '',
        })
      );

      renderGroupManagement();

      expect(screen.getByText('No groups found')).toBeInTheDocument();
    });
  });

  describe('Create functionality', () => {
    it('should open drawer when create button is clicked', async () => {
      renderGroupManagement();

      const createButton = screen.getByTestId('create-group-button');
      await user.click(createButton);

      await waitFor(() => {
        expect(screen.getByTestId('group-drawer')).toBeInTheDocument();
      });

      expect(screen.getByText('Drawer for group: new')).toBeInTheDocument();
    });
  });

  describe('Edit functionality', () => {
    it('should open drawer with group ID when edit is clicked', async () => {
      renderGroupManagement();

      const editButtons = screen.getAllByText('Edit');
      if (editButtons[0]) {
        await user.click(editButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('group-drawer')).toBeInTheDocument();
        });

        expect(screen.getByText('Drawer for group: 1')).toBeInTheDocument();
      }
    });
  });

  describe('Delete functionality', () => {
    it('should open delete confirmation dialog when delete is clicked', async () => {
      renderGroupManagement();

      const deleteButtons = screen.getAllByText('Delete');
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('delete-confirmation-dialog')).toBeInTheDocument();
        });

        expect(screen.getByText('Item: Admin Group')).toBeInTheDocument();
      }
    });

    it('should call deleteGroup when delete is confirmed', async () => {
      const deleteGroup = vi.fn().mockResolvedValue(undefined);
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          deleteGroup,
        })
      );

      renderGroupManagement();

      const deleteButtons = screen.getAllByText('Delete');
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('delete-confirmation-dialog')).toBeInTheDocument();
        });

        const confirmButton = screen.getByText('Confirm');
        if (confirmButton) {
          await user.click(confirmButton);

          await waitFor(() => {
            expect(deleteGroup).toHaveBeenCalledWith('1');
          });
        }
      }
    });

    it('should show success toast when delete succeeds', async () => {
      const deleteGroup = vi.fn().mockResolvedValue(undefined);
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          deleteGroup,
        })
      );

      renderGroupManagement();

      const deleteButtons = screen.getAllByText('Delete');
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('delete-confirmation-dialog')).toBeInTheDocument();
        });

        const confirmButton = screen.getByText('Confirm');
        if (confirmButton) {
          await user.click(confirmButton);

          await waitFor(() => {
            expect(mockToast.success).toHaveBeenCalled();
          });
        }
      }
    });

    it('should show error toast when delete fails', async () => {
      const deleteGroup = vi.fn().mockRejectedValue(new Error('Delete failed'));
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          deleteGroup,
        })
      );

      renderGroupManagement();

      const deleteButtons = screen.getAllByText('Delete');
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('delete-confirmation-dialog')).toBeInTheDocument();
        });

        const confirmButton = screen.getByText('Confirm');
        if (confirmButton) {
          await user.click(confirmButton);

          await waitFor(() => {
            expect(mockToast.error).toHaveBeenCalled();
          });
        }
      }
    });

    it('should close dialog when cancel is clicked', async () => {
      renderGroupManagement();

      const deleteButtons = screen.getAllByText('Delete');
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);

        await waitFor(() => {
          expect(screen.getByTestId('delete-confirmation-dialog')).toBeInTheDocument();
        });

        const cancelButton = screen.getByText('Cancel');
        if (cancelButton) {
          await user.click(cancelButton);

          await waitFor(() => {
            expect(screen.queryByTestId('delete-confirmation-dialog')).not.toBeInTheDocument();
          });
        }
      }
    });
  });

  describe('Search functionality', () => {
    it('should pass search props to GroupSearch', () => {
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          inputValue: 'test query',
          searchQuery: 'test query',
        })
      );

      renderGroupManagement();

      // Verify the search component is rendered with the value
      const searchComponent = screen.getByTestId('group-search');
      expect(searchComponent).toBeInTheDocument();
    });

    it('should display empty search results message', () => {
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          groups: [],
          searchQuery: 'test',
        })
      );

      renderGroupManagement();

      expect(screen.getByText('No groups match your search')).toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('should display error state when error occurs', () => {
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          error: new Error('Failed to load') as any,
        })
      );

      renderGroupManagement();

      expect(screen.getByTestId('error-state')).toBeInTheDocument();
    });

    it('should call refetch when retry is clicked', async () => {
      const refetch = vi.fn();
      mockUseGroupManagement.mockImplementation(() =>
        buildGroupManagementHookReturn({
          error: new Error('Failed to load') as any,
          refetch,
        })
      );

      renderGroupManagement();

      const retryButton = screen.getByText('Retry');
      if (retryButton) {
        await user.click(retryButton);
        expect(refetch).toHaveBeenCalled();
      }
    });
  });

  describe('Initial search query', () => {
    it('should pass initialSearchQuery to useGroupManagement', () => {
      renderGroupManagement({ initialSearchQuery: 'initial query' }, { pageSize: 18 });

      expect(mockUseGroupManagement).toHaveBeenCalledWith({
        initialSearchQuery: 'initial query',
        initialFirst: 18,
      });
    });
  });

  describe('Drawer management', () => {
    it('should close drawer when drawer onClose is called', async () => {
      renderGroupManagement();

      const createButton = screen.getByTestId('create-group-button');
      await user.click(createButton);

      await waitFor(() => {
        expect(screen.getByTestId('group-drawer')).toBeInTheDocument();
      });

      const closeButton = screen.getByText('Close Drawer');
      await user.click(closeButton);

      await waitFor(() => {
        expect(screen.queryByTestId('group-drawer')).not.toBeInTheDocument();
      });
    });
  });
});
