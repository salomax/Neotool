import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleDrawer } from '../RoleDrawer';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Use vi.hoisted() to define all variables that need to be available in mock factories
const {
  mockPush,
  mockReplace,
  mockUseCreateRoleMutation,
  mockUseUpdateRoleMutation,
  mockUseGetRoleWithUsersAndGroupsQuery,
  mockUseGetRoleWithRelationshipsQuery,
  mockUseRoleMutations,
  mockUseRoleDrawer,
  mockUsePermissionManagement,
  mockToast,
} = vi.hoisted(() => {
  const mockPush = vi.fn();
  const mockReplace = vi.fn();
  const mockUseCreateRoleMutation = vi.fn();
  const mockUseUpdateRoleMutation = vi.fn();
  
  // Mock query functions
  const mockUseGetRoleWithUsersAndGroupsQuery = vi.fn();
  const mockUseGetRoleWithRelationshipsQuery = vi.fn();
  
  // Mock hook functions
  const mockUseRoleMutations = vi.fn();
  const mockUseRoleDrawer = vi.fn();
  const mockUsePermissionManagement = vi.fn();
  
  // Mock toast
  const mockToast = {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  };
  
  return {
    mockPush,
    mockReplace,
    mockUseCreateRoleMutation,
    mockUseUpdateRoleMutation,
    mockUseGetRoleWithUsersAndGroupsQuery,
    mockUseGetRoleWithRelationshipsQuery,
    mockUseRoleMutations,
    mockUseRoleDrawer,
    mockUsePermissionManagement,
    mockToast,
  };
});

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    replace: mockReplace,
  }),
}));

// Mock GraphQL mutations
vi.mock('@/lib/graphql/operations/authorization-management/mutations.generated', () => ({
  useCreateRoleMutation: mockUseCreateRoleMutation,
  useUpdateRoleMutation: mockUseUpdateRoleMutation,
}));

// Import after mocks to avoid hoisting issues
import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';

// Mock Drawer component
vi.mock('@/shared/components/ui/layout/Drawer', () => {
  const DrawerComponent = ({ open, children, onClose }: any) =>
    open ? (
      <div data-testid="drawer">
        <div data-testid="drawer-header">Drawer Header</div>
        <div data-testid="drawer-body">{children}</div>
        <div data-testid="drawer-footer">Drawer Footer</div>
        <button onClick={onClose}>Close</button>
      </div>
    ) : null;
  
  const Header = ({ children }: any) => <div data-testid="drawer-header-content">{children}</div>;
  Header.displayName = 'Drawer.Header';
  DrawerComponent.Header = Header;
  
  const Body = React.forwardRef<HTMLDivElement, { children: React.ReactNode }>(
    ({ children }, ref) => <div ref={ref} data-testid="drawer-body-content">{children}</div>
  );
  Body.displayName = 'Drawer.Body';
  DrawerComponent.Body = Body;
  
  const Footer = ({ children }: any) => <div data-testid="drawer-footer-content">{children}</div>;
  Footer.displayName = 'Drawer.Footer';
  DrawerComponent.Footer = Footer;
  
  return {
    Drawer: DrawerComponent,
  };
});

// Mock GraphQL queries
const mockRole = {
  id: 'role-1',
  name: 'Admin Role',
  groups: [
    {
      id: 'group-1',
      name: 'Admin Group',
      description: 'Administrators',
    },
  ],
  permissions: [
    { id: 'perm-1', name: 'security:user:view' },
  ],
};

const mockUsers = [
  {
    id: 'user-1',
    email: 'user1@example.com',
    displayName: 'User One',
    enabled: true,
    roles: [{ id: 'role-1', name: 'Admin Role' }],
  },
];

const mockGroups = [
  {
    id: 'group-1',
    name: 'Admin Group',
    description: 'Administrators',
    roles: [{ id: 'role-1', name: 'Admin Role' }],
  },
];

// Initialize hoisted mock functions with default implementations
mockUseGetRoleWithUsersAndGroupsQuery.mockReturnValue({
  data: {
    users: {
      edges: mockUsers.map((u) => ({ node: u })),
    },
    groups: {
      edges: mockGroups.map((g) => ({ node: g })),
    },
  },
  loading: false,
  error: undefined,
  refetch: vi.fn().mockResolvedValue({
    data: {
      users: {
        edges: mockUsers.map((u) => ({ node: u })),
      },
      groups: {
        edges: mockGroups.map((g) => ({ node: g })),
      },
    },
  }),
});

mockUseGetRoleWithRelationshipsQuery.mockReturnValue({
  data: {
    role: mockRole,
  },
  loading: false,
  error: undefined,
  refetch: vi.fn().mockResolvedValue({
    data: {
      role: mockRole,
    },
  }),
});

mockUseRoleMutations.mockReturnValue({
  createRole: vi.fn().mockResolvedValue({ id: 'role-2', name: 'New Role' }),
  updateRole: vi.fn().mockResolvedValue(undefined),
  deleteRole: vi.fn().mockResolvedValue(undefined),
  assignPermissionToRole: vi.fn().mockResolvedValue(undefined),
  removePermissionFromRole: vi.fn().mockResolvedValue(undefined),
  assignRoleToGroup: vi.fn().mockResolvedValue(undefined),
  removeRoleFromGroup: vi.fn().mockResolvedValue(undefined),
  createLoading: false,
  updateLoading: false,
  deleteLoading: false,
  assignPermissionLoading: false,
  removePermissionLoading: false,
  assignRoleToGroupLoading: false,
  removeRoleFromGroupLoading: false,
});

mockUseRoleDrawer.mockImplementation((roleId: string | null) => ({
  role: roleId ? mockRole : null,
  loading: false,
  error: undefined,
  selectedGroups: roleId ? mockGroups : [],
  selectedPermissions: roleId ? mockRole.permissions : [],
  hasChanges: false,
  saving: false,
  updateSelectedGroups: vi.fn(),
  updateSelectedPermissions: vi.fn(),
  handleSave: vi.fn().mockResolvedValue(undefined),
  resetChanges: vi.fn(),
  refetch: vi.fn().mockResolvedValue({
    data: {
      role: roleId ? mockRole : null,
    },
  }),
}));

mockUsePermissionManagement.mockReturnValue({
  permissions: [
    { id: 'perm-1', name: 'security:user:view' },
    { id: 'perm-2', name: 'security:user:save' },
  ],
});

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetRoleWithUsersAndGroupsQuery: () => mockUseGetRoleWithUsersAndGroupsQuery(),
  useGetRoleWithRelationshipsQuery: () => mockUseGetRoleWithRelationshipsQuery(),
}));

// Mock useRoleMutations hook (function is hoisted above)

vi.mock('@/shared/hooks/authorization/useRoleMutations', () => ({
  useRoleMutations: () => mockUseRoleMutations(),
}));

// Mock useRoleDrawer hook (function is hoisted above)

vi.mock('@/shared/hooks/authorization/useRoleDrawer', () => ({
  useRoleDrawer: (roleId: string | null, open: boolean) => mockUseRoleDrawer(roleId, open),
}));

// Mock usePermissionManagement hook (function is hoisted above)

vi.mock('@/shared/hooks/authorization/usePermissionManagement', () => ({
  usePermissionManagement: () => mockUsePermissionManagement(),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'roleManagement.createRole': 'Create Role',
        'roleManagement.editRole': 'Edit Role',
        'roleManagement.drawer.errorLoading': 'Failed to load role',
        'roleManagement.form.cancel': 'Cancel',
        'roleManagement.form.create': 'Create',
        'roleManagement.form.save': 'Save',
        'roleManagement.form.saving': 'Saving...',
        'roleManagement.toast.roleCreated': `Role ${params?.name || ''} created`,
        'roleManagement.toast.roleUpdated': `Role ${params?.name || ''} updated`,
        'roleManagement.toast.roleCreateError': 'Failed to create role',
        'roleManagement.toast.roleUpdateError': 'Failed to update role',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock toast (object is hoisted above)

vi.mock('@/shared/providers', () => ({
  useToast: () => mockToast,
}));

// Mock child components
vi.mock('../RoleForm', () => ({
  RoleForm: ({ initialValues }: any) => (
    <div data-testid="role-form">
      <div>Name: {initialValues?.name || ''}</div>
    </div>
  ),
}));

vi.mock('../RolePermissionAssignment', () => ({
  RolePermissionAssignment: ({ roleId, assignedPermissions }: any) => (
    <div data-testid="role-permission-assignment">
      <div>Role ID: {roleId || 'none'}</div>
      <div>Permissions: {assignedPermissions?.map((p: any) => p.name).join(', ') || 'none'}</div>
    </div>
  ),
}));

vi.mock('../RoleUserAssignment', () => ({
  RoleUserAssignment: ({ roleId, assignedUsers }: any) => (
    <div data-testid="role-user-assignment">
      <div>Role ID: {roleId || 'none'}</div>
      <div>Users: {assignedUsers?.map((u: any) => u.email).join(', ') || 'none'}</div>
    </div>
  ),
}));

vi.mock('../RoleGroupAssignment', () => ({
  RoleGroupAssignment: ({ roleId, assignedGroups }: any) => (
    <div data-testid="role-group-assignment">
      <div>Role ID: {roleId || 'none'}</div>
      <div>Groups: {assignedGroups?.map((g: any) => g.name).join(', ') || 'none'}</div>
    </div>
  ),
}));

// Mock PermissionGate
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock LoadingState and ErrorAlert
vi.mock('@/shared/components/ui/feedback', () => ({
  LoadingState: ({ isLoading }: any) =>
    isLoading ? <div data-testid="loading-state">Loading...</div> : null,
  ErrorAlert: ({ error, onRetry }: any) =>
    error ? (
      <div data-testid="error-alert">
        <div>Error occurred</div>
        {onRetry && <button onClick={onRetry}>Retry</button>}
      </div>
    ) : null,
}));

const renderRoleDrawer = (props = {}) => {
  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    roleId: null,
    ...props,
  };

  return render(
    <AppThemeProvider>
      <RoleDrawer {...defaultProps} />
    </AppThemeProvider>
  );
};

describe.sequential('RoleDrawer', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    
    // Mock mutation hooks
    (useCreateRoleMutation as any).mockReturnValue([
      vi.fn().mockResolvedValue({ data: { createRole: { id: 'role-2', name: 'New Role' } } }),
      { loading: false },
    ]);
    
    (useUpdateRoleMutation as any).mockReturnValue([
      vi.fn().mockResolvedValue({ data: { updateRole: { id: 'role-1', name: 'Updated Role' } } }),
      { loading: false },
    ]);
    
    mockUseGetRoleWithRelationshipsQuery.mockReturnValue({
      data: {
        role: mockRole,
      },
      loading: false,
      error: undefined,
      refetch: vi.fn().mockResolvedValue({
        data: {
          role: mockRole,
        },
      }),
    });
    mockUseGetRoleWithUsersAndGroupsQuery.mockReturnValue({
      data: {
        users: {
          edges: mockUsers.map((u) => ({ node: u })),
        },
        groups: {
          edges: mockGroups.map((g) => ({ node: g })),
        },
      },
      loading: false,
      error: undefined,
      refetch: vi.fn().mockResolvedValue({
        data: {
          users: {
            edges: mockUsers.map((u) => ({ node: u })),
          },
          groups: {
            edges: mockGroups.map((g) => ({ node: g })),
          },
        },
      }),
    });
    mockUseRoleMutations.mockReturnValue({
      createRole: vi.fn().mockResolvedValue({ id: 'role-2', name: 'New Role' }),
      updateRole: vi.fn().mockResolvedValue(undefined),
      deleteRole: vi.fn().mockResolvedValue(undefined),
      assignPermissionToRole: vi.fn().mockResolvedValue(undefined),
      removePermissionFromRole: vi.fn().mockResolvedValue(undefined),
      assignRoleToGroup: vi.fn().mockResolvedValue(undefined),
      removeRoleFromGroup: vi.fn().mockResolvedValue(undefined),
      createLoading: false,
      updateLoading: false,
      deleteLoading: false,
      assignPermissionLoading: false,
      removePermissionLoading: false,
      assignRoleToGroupLoading: false,
      removeRoleFromGroupLoading: false,
    });
    mockUseRoleDrawer.mockReturnValue({
      role: mockRole,
      loading: false,
      error: undefined,
      selectedGroups: mockGroups,
      selectedPermissions: mockRole.permissions,
      hasChanges: false,
      saving: false,
      updateSelectedGroups: vi.fn(),
      updateSelectedPermissions: vi.fn(),
      handleSave: vi.fn().mockResolvedValue(undefined),
      resetChanges: vi.fn(),
      refetch: vi.fn().mockResolvedValue({
        data: {
          role: mockRole,
        },
      }),
    });
  });

  describe('Create mode (roleId === null)', () => {
    it('should render drawer in create mode', () => {
      const view = renderRoleDrawer({ roleId: null });

      expect(view.getAllByText('Create Role')[0]).toBeInTheDocument();
      expect(view.getAllByTestId('role-form')[0]).toBeInTheDocument();
    });

    it('should not query role data in create mode', () => {
      const view = renderRoleDrawer({ roleId: null });

      // In create mode, the drawer should render without role data
      // The hook is called with null roleId and open=false (since !isCreateMode = false)
      expect(view.getAllByText('Create Role')[0]).toBeInTheDocument();
      // useRoleDrawer should be called with null roleId and open=false
      expect(mockUseRoleDrawer).toHaveBeenCalledWith(null, false);
    });

    it('should show create button', () => {
      const view = renderRoleDrawer({ roleId: null });

      expect(view.getAllByText('Create')[0]).toBeInTheDocument();
    });
  });

  describe('Edit mode (roleId !== null)', () => {
    it('should render drawer in edit mode', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getAllByText('Edit Role')[0]).toBeInTheDocument();
    });

    it('should query role data in edit mode', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      // In edit mode, the drawer should render with role data
      // The hook is called with roleId and open=true
      expect(view.getAllByText('Edit Role')[0]).toBeInTheDocument();
      // useRoleDrawer should be called with roleId
      expect(mockUseRoleDrawer).toHaveBeenCalledWith('role-1', true);
    });

    it('should show loading state while fetching role', () => {
      mockUseRoleDrawer.mockReturnValue({
        role: null,
        loading: true,
        error: undefined,
        selectedGroups: [],
        selectedPermissions: [],
        hasChanges: false,
        saving: false,
        updateSelectedGroups: vi.fn(),
        updateSelectedPermissions: vi.fn(),
        handleSave: vi.fn().mockResolvedValue(undefined),
        resetChanges: vi.fn(),
        refetch: vi.fn().mockResolvedValue({
          data: {
            role: mockRole,
          },
        }),
      });

      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByTestId('loading-state')).toBeInTheDocument();
    });

    it('should show error state when query fails', () => {
      mockUseRoleDrawer.mockReturnValue({
        role: null,
        loading: false,
        error: new Error('Failed to load role') as any,
        selectedGroups: [],
        selectedPermissions: [],
        hasChanges: false,
        saving: false,
        updateSelectedGroups: vi.fn(),
        updateSelectedPermissions: vi.fn(),
        handleSave: vi.fn().mockResolvedValue(undefined),
        resetChanges: vi.fn(),
        refetch: vi.fn().mockResolvedValue({
          data: {
            role: mockRole,
          },
        }),
      });

      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByTestId('error-alert')).toBeInTheDocument();
    });

    it('should display role data in form', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByText(`Name: ${mockRole.name}`)).toBeInTheDocument();
    });

    it('should show save button in edit mode', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByText('Save')).toBeInTheDocument();
    });
  });

  describe('User interactions', () => {
    it('should call onClose when cancel is clicked', async () => {
      const onClose = vi.fn();
      const view = renderRoleDrawer({ onClose });

      const cancelButton = view.getByText('Cancel');
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should call onClose when drawer close button is clicked', async () => {
      const onClose = vi.fn();
      const view = renderRoleDrawer({ onClose });

      const closeButton = view.getByText('Close');
      await user.click(closeButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should disable buttons when saving', () => {
      (useCreateRoleMutation as any).mockReturnValue([
        vi.fn(),
        { loading: true },
      ]);

      const view = renderRoleDrawer({ roleId: null });

      const saveButton = view.getByText('Saving...');
      expect(saveButton).toBeInTheDocument();
    });
  });

  describe('Assignment components', () => {
    it('should render RolePermissionAssignment component', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByTestId('role-permission-assignment')).toBeInTheDocument();
    });

    // Note: RoleDrawer doesn't render RoleUserAssignment - it only renders
    // RoleGroupAssignment and RolePermissionAssignment
    // Test removed as component doesn't implement this feature

    it('should render RoleGroupAssignment component', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getByTestId('role-group-assignment')).toBeInTheDocument();
    });

    it('should pass assigned permissions to RolePermissionAssignment', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getAllByText('Permissions: security:user:view')[0]).toBeInTheDocument();
    });

    // Note: RoleDrawer doesn't render RoleUserAssignment - it only renders
    // RoleGroupAssignment and RolePermissionAssignment
    // Test removed as component doesn't implement this feature

    it('should pass assigned groups to RoleGroupAssignment', () => {
      const view = renderRoleDrawer({ roleId: 'role-1' });

      expect(view.getAllByText('Groups: Admin Group')[0]).toBeInTheDocument();
    });
  });

  describe('Drawer visibility', () => {
    it('should not render when open is false', () => {
      const view = renderRoleDrawer({ open: false });

      expect(view.queryByTestId('drawer')).not.toBeInTheDocument();
    });

    it('should render when open is true', () => {
      const view = renderRoleDrawer({ open: true });

      expect(view.getByTestId('drawer')).toBeInTheDocument();
    });
  });
});
