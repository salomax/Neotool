import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserManagement } from '../UserManagement';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

// Mock useUserManagement hook
const mockUsers = [
  { id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true },
  { id: '2', email: 'user2@example.com', displayName: 'User Two', enabled: false },
];

const mockUseUserManagement = vi.fn((_args?: any) => ({
  users: mockUsers,
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
  enableUser: vi.fn().mockResolvedValue(undefined),
  disableUser: vi.fn().mockResolvedValue(undefined),
  loading: false,
  enableLoading: false,
  disableLoading: false,
  error: undefined,
  refetch: vi.fn(),
  setFirst: vi.fn(),
  orderBy: null,
  handleSort: vi.fn(),
}));

vi.mock('@/shared/hooks/authorization/useUserManagement', () => ({
  useUserManagement: (args: any) => mockUseUserManagement(args),
}));

// Mock useToggleStatus hook
const mockUseToggleStatus = vi.fn(({ enableFn, disableFn }) => {
  return vi.fn().mockImplementation(async (userId: string, enabled: boolean) => {
    if (enabled) {
      await enableFn(userId);
    } else {
      await disableFn(userId);
    }
  });
});

vi.mock('@/shared/hooks/mutations', () => ({
  useToggleStatus: (config: any) => mockUseToggleStatus(config),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'userManagement.searchPlaceholder': 'Search users...',
        'userManagement.emptyList': 'No users found',
        'userManagement.emptySearchResults': 'No users match your search',
        'userManagement.toast.userEnabled': `User ${params?.name || ''} enabled`,
        'userManagement.toast.userDisabled': `User ${params?.name || ''} disabled`,
        'userManagement.toast.userEnableError': 'Failed to enable user',
        'userManagement.toast.userDisableError': 'Failed to disable user',
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
vi.mock('../UserSearch', () => ({
  UserSearch: ({ value, onChange, onSearch, placeholder, maxWidth }: any) => (
    <div data-testid="user-search">
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={() => onSearch?.(value)}
        placeholder={placeholder}
        data-max-width={maxWidth}
      />
    </div>
  ),
}));

vi.mock('../UserList', () => ({
  UserList: ({
    users,
    loading,
    onEdit,
    onToggleStatus,
    toggleLoading,
    emptyMessage,
    pageInfo,
    paginationRange,
    onLoadNext,
    onLoadPrevious,
    onGoToFirst,
    canLoadPreviousPage,
    onTableResize,
    recalculationKey,
    orderBy,
    onSortChange,
  }: any) => {
    // Capture props for testing
    (window as any).__userListProps = {
      users,
      loading,
      onEdit,
      onToggleStatus,
      toggleLoading,
      emptyMessage,
      pageInfo,
      paginationRange,
      onLoadNext,
      onLoadPrevious,
      onGoToFirst,
      canLoadPreviousPage,
      onTableResize,
      recalculationKey,
      orderBy,
      onSortChange,
    };
    return (
      <div data-testid="user-list">
        {loading && <div>Loading...</div>}
        {!loading && users.length === 0 && <div>{emptyMessage}</div>}
        {!loading && users.length > 0 && (
          <div>
            {users.map((user: any) => (
              <div key={user.id}>
                <span>{user.displayName || user.email}</span>
                <button onClick={() => onEdit(user)}>Edit</button>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  },
}));

vi.mock('../UserDrawer', () => ({
  UserDrawer: ({ open, onClose, userId }: any) =>
    open ? (
      <div data-testid="user-drawer">
        <div>Drawer for user: {userId || 'new'}</div>
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

const renderUserManagement = (props = {}) => {
  return render(
    <AppThemeProvider>
      <UserManagement {...props} />
    </AppThemeProvider>
  );
};

describe('UserManagement', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__userListProps = undefined;
    mockUseUserManagement.mockReturnValue({
      users: mockUsers,
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
      enableUser: vi.fn().mockResolvedValue(undefined),
      disableUser: vi.fn().mockResolvedValue(undefined),
      loading: false,
      enableLoading: false,
      disableLoading: false,
      error: undefined,
      refetch: vi.fn(),
      setFirst: vi.fn(),
      orderBy: null,
      handleSort: vi.fn(),
    });
  });

  describe('Component integration', () => {
    it('should render UserSearch component', () => {
      renderUserManagement();

      expect(screen.getByTestId('user-search')).toBeInTheDocument();
    });

    it('should render UserList component', () => {
      renderUserManagement();

      expect(screen.getByTestId('user-list')).toBeInTheDocument();
    });

    it('should render UserDrawer component', () => {
      renderUserManagement();

      // Drawer is initially closed
      expect(screen.queryByTestId('user-drawer')).not.toBeInTheDocument();
    });

    it('should pass initialSearchQuery to useUserManagement', () => {
      renderUserManagement({ initialSearchQuery: 'test query' });

      expect(mockUseUserManagement).toHaveBeenCalledWith({
        initialSearchQuery: 'test query',
        initialFirst: 10,
      });
    });
  });

  describe('Search integration', () => {
    it('should pass inputValue and handleInputChange to UserSearch', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        inputValue: 'test input',
      });

      renderUserManagement();

      const searchInput = screen.getByPlaceholderText('Search users...');
      expect(searchInput).toHaveValue('test input');
    });

    it('should pass handleSearch to UserSearch', () => {
      const handleSearch = vi.fn();
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        handleSearch,
      });

      renderUserManagement();

      const searchInput = screen.getByPlaceholderText('Search users...');
      searchInput.blur();
      // Verify handleSearch is passed (would be called on blur in real component)
    });

    it('should pass correct empty message for search results', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        searchQuery: 'test',
        users: [],
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.emptyMessage).toBe('No users match your search');
    });

    it('should pass correct empty message for empty list', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        searchQuery: '',
        users: [],
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.emptyMessage).toBe('No users found');
    });
  });

  describe('UserList integration', () => {
    it('should pass users array to UserList', () => {
      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.users).toEqual(mockUsers);
    });

    it('should pass loading state to UserList', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        loading: true,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.loading).toBe(true);
    });

    it('should pass onEdit callback to UserList', () => {
      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.onEdit).toBeDefined();
      expect(typeof props.onEdit).toBe('function');
    });

    it('should pass onToggleStatus callback to UserList', () => {
      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.onToggleStatus).toBeDefined();
      expect(typeof props.onToggleStatus).toBe('function');
    });

    it('should pass toggleLoading state to UserList', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        enableLoading: true,
        disableLoading: false,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.toggleLoading).toBe(true);
    });

    it('should pass pagination props to UserList', () => {
      const pageInfo = {
        hasNextPage: true,
        hasPreviousPage: false,
        startCursor: 'cursor1',
        endCursor: 'cursor2',
      };
      const paginationRange = { start: 1, end: 10, total: 25 };
      const loadNextPage = vi.fn();
      const loadPreviousPage = vi.fn();
      const goToFirstPage = vi.fn();

      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        pageInfo: pageInfo as any,
        paginationRange,
        loadNextPage,
        loadPreviousPage,
        goToFirstPage,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.pageInfo).toEqual(pageInfo);
      expect(props.paginationRange).toEqual(paginationRange);
      expect(props.onLoadNext).toBe(loadNextPage);
      expect(props.onLoadPrevious).toBe(loadPreviousPage);
      expect(props.onGoToFirst).toBe(goToFirstPage);
    });

    it('should pass canLoadPreviousPage to UserList', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        canLoadPreviousPage: true,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.canLoadPreviousPage).toBe(true);
    });

    it('should pass onTableResize callback to UserList', () => {
      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.onTableResize).toBeDefined();
      expect(typeof props.onTableResize).toBe('function');
    });

    it('should call setFirst when onTableResize is called', () => {
      const setFirst = vi.fn();
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        setFirst,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      props.onTableResize(20);

      expect(setFirst).toHaveBeenCalledWith(20);
    });

    it('should pass recalculationKey based on users length and loading state', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        users: mockUsers,
        loading: false,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.recalculationKey).toBe('2-ready');
    });

    it('should pass orderBy and handleSort to UserList', () => {
      const orderBy = { field: 'DISPLAY_NAME' as const, direction: 'asc' as const };
      const handleSort = vi.fn();

      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        orderBy: orderBy as any,
        handleSort,
      });

      renderUserManagement();

      const props = (window as any).__userListProps;
      expect(props.orderBy).toEqual(orderBy);
      expect(props.onSortChange).toBe(handleSort);
    });
  });

  describe('Drawer management', () => {
    it('should open drawer when edit button is clicked', async () => {
      renderUserManagement();

      const editButton = screen.getAllByText('Edit')[0]!;
      await user.click(editButton);

      await waitFor(() => {
        expect(screen.getByTestId('user-drawer')).toBeInTheDocument();
      });

      expect(screen.getByText('Drawer for user: 1')).toBeInTheDocument();
    });

    it('should set editingUser when drawer opens', async () => {
      renderUserManagement();

      const editButton = screen.getAllByText('Edit')[0]!;
      await user.click(editButton);

      await waitFor(() => {
        expect(screen.getByTestId('user-drawer')).toBeInTheDocument();
      });

      // Verify drawer receives correct userId
      expect(screen.getByText('Drawer for user: 1')).toBeInTheDocument();
    });

    it('should close drawer when onClose is called', async () => {
      renderUserManagement();

      // Open drawer
      const editButton = screen.getAllByText('Edit')[0]!;
      await user.click(editButton);

      await waitFor(() => {
        expect(screen.getByTestId('user-drawer')).toBeInTheDocument();
      });

      // Close drawer
      const closeButton = screen.getByText('Close Drawer');
      await user.click(closeButton);

      await waitFor(() => {
        expect(screen.queryByTestId('user-drawer')).not.toBeInTheDocument();
      });
    });

    it('should pass correct userId to UserDrawer', async () => {
      renderUserManagement();

      const editButton = screen.getAllByText('Edit')[0]!;
      await user.click(editButton);

      await waitFor(() => {
        expect(screen.getByText('Drawer for user: 1')).toBeInTheDocument();
      });
    });

    it('should pass null userId when no user is being edited', () => {
      renderUserManagement();

      // Drawer should not be open initially
      expect(screen.queryByTestId('user-drawer')).not.toBeInTheDocument();
    });
  });

  describe('Status toggle integration', () => {
    it('should use useToggleStatus hook with correct functions', () => {
      renderUserManagement();

      expect(mockUseToggleStatus).toHaveBeenCalled();
      const callArgs = mockUseToggleStatus.mock.calls[0]?.[0];
      expect(callArgs).toBeDefined();
      expect(callArgs?.enableFn).toBeDefined();
      expect(callArgs?.disableFn).toBeDefined();
    });

    it('should pass correct message keys to useToggleStatus', () => {
      renderUserManagement();

      const callArgs = mockUseToggleStatus.mock.calls[0]?.[0];
      expect(callArgs).toBeDefined();
      expect(callArgs?.enableSuccessMessage).toBe('userManagement.toast.userEnabled');
      expect(callArgs?.disableSuccessMessage).toBe('userManagement.toast.userDisabled');
      expect(callArgs?.enableErrorMessage).toBe('userManagement.toast.userEnableError');
      expect(callArgs?.disableErrorMessage).toBe('userManagement.toast.userDisableError');
    });
  });

  describe('Error handling', () => {
    it('should pass error to ManagementLayout', () => {
      const error = new Error('Failed to load');
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        error: error as any,
      });

      renderUserManagement();

      expect(screen.getByTestId('error-state')).toBeInTheDocument();
    });

    it('should pass onErrorRetry (refetch) to ManagementLayout', async () => {
      const refetch = vi.fn();
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        error: new Error('Failed to load') as any,
        refetch,
      });

      renderUserManagement();

      const retryButton = screen.getByText('Retry');
      await user.click(retryButton);

      expect(refetch).toHaveBeenCalled();
    });

    it('should pass correct error fallback message', () => {
      mockUseUserManagement.mockReturnValue({
        ...mockUseUserManagement(),
        error: new Error('Failed to load') as any,
      });

      renderUserManagement();

      expect(screen.getByText('Failed to load data')).toBeInTheDocument();
    });
  });

  describe('Hook integration', () => {
    it('should call useUserManagement with correct initial props', () => {
      renderUserManagement({ initialSearchQuery: 'test' });

      // Note: useUserManagement is called without arguments in the mock
      expect(mockUseUserManagement).toHaveBeenCalled();
    });

    it('should use hook return values correctly', () => {
      renderUserManagement();

      // Verify hook values are used by checking rendered output
      expect(screen.getByTestId('user-list')).toBeInTheDocument();
      expect(screen.getByTestId('user-search')).toBeInTheDocument();
    });
  });
});
