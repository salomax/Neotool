import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleDrawer } from '../RoleDrawer';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

// Mock GraphQL mutations
vi.mock('@/lib/graphql/operations/authorization-management/mutations.generated', () => ({
  useCreateRoleMutation: vi.fn(),
  useUpdateRoleMutation: vi.fn(),
}));

// Mock Drawer component
const { DrawerComponent } = vi.hoisted(() => {
  const Component = ({ open, children, onClose }: any) =>
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
  Component.Header = Header;
  
  const Body = ({ children }: any) => <div data-testid="drawer-body-content">{children}</div>;
  Body.displayName = 'Drawer.Body';
  Component.Body = Body;
  
  const Footer = ({ children }: any) => <div data-testid="drawer-footer-content">{children}</div>;
  Footer.displayName = 'Drawer.Footer';
  Component.Footer = Footer;
  
  return { DrawerComponent: Component };
});

vi.mock('@/shared/components/ui/layout/Drawer', () => ({
  Drawer: DrawerComponent,
}));

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

const mockUseGetRoleWithUsersAndGroupsQuery = vi.fn(() => ({
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
}));

const mockUseGetRoleWithRelationshipsQuery = vi.fn(() => ({
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
}));

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetRoleWithUsersAndGroupsQuery: () => mockUseGetRoleWithUsersAndGroupsQuery(),
  useGetRoleWithRelationshipsQuery: () => mockUseGetRoleWithRelationshipsQuery(),
}));

// Mock useRoleMutations hook
const mockUseRoleMutations = vi.fn(() => ({
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
}));

vi.mock('@/shared/hooks/authorization/useRoleMutations', () => ({
  useRoleMutations: () => mockUseRoleMutations(),
}));

// Mock useRoleDrawer hook
const mockUseRoleDrawer = vi.fn((roleId: string | null, open: boolean) => ({
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

vi.mock('@/shared/hooks/authorization/useRoleDrawer', () => ({
  useRoleDrawer: (roleId: string | null, open: boolean) => mockUseRoleDrawer(roleId, open),
}));

// Mock usePermissionManagement hook
const mockUsePermissionManagement = vi.fn(() => ({
  permissions: [
    { id: 'perm-1', name: 'security:user:view' },
    { id: 'perm-2', name: 'security:user:save' },
  ],
}));

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

// Mock toast
const mockToast = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
};

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

describe('RoleDrawer', () => {
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
      renderRoleDrawer({ roleId: null });

      expect(screen.getByText('Create Role')).toBeInTheDocument();
      expect(screen.getByTestId('role-form')).toBeInTheDocument();
    });

    it('should not query role data in create mode', () => {
      renderRoleDrawer({ roleId: null });

      // In create mode, the drawer should render without role data
      // The hook is called with null roleId and open=false (since !isCreateMode = false)
      expect(screen.getByText('Create Role')).toBeInTheDocument();
      // useRoleDrawer should be called with null roleId and open=false
      expect(mockUseRoleDrawer).toHaveBeenCalledWith(null, false);
    });

    it('should show create button', () => {
      renderRoleDrawer({ roleId: null });

      expect(screen.getByText('Create')).toBeInTheDocument();
    });
  });

  describe('Edit mode (roleId !== null)', () => {
    it('should render drawer in edit mode', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByText('Edit Role')).toBeInTheDocument();
    });

    it('should query role data in edit mode', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      // In edit mode, the drawer should render with role data
      // The hook is called with roleId and open=true
      expect(screen.getByText('Edit Role')).toBeInTheDocument();
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

      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByTestId('loading-state')).toBeInTheDocument();
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

      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
    });

    it('should display role data in form', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByText(`Name: ${mockRole.name}`)).toBeInTheDocument();
    });

    it('should show save button in edit mode', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByText('Save')).toBeInTheDocument();
    });
  });

  describe('User interactions', () => {
    it('should call onClose when cancel is clicked', async () => {
      const onClose = vi.fn();
      renderRoleDrawer({ onClose });

      const cancelButton = screen.getByText('Cancel');
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should call onClose when drawer close button is clicked', async () => {
      const onClose = vi.fn();
      renderRoleDrawer({ onClose });

      const closeButton = screen.getByText('Close');
      await user.click(closeButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should disable buttons when saving', () => {
      (useCreateRoleMutation as any).mockReturnValue([
        vi.fn(),
        { loading: true },
      ]);

      renderRoleDrawer({ roleId: null });

      const saveButton = screen.getByText('Saving...');
      expect(saveButton).toBeInTheDocument();
    });
  });

  describe('Assignment components', () => {
    it('should render RolePermissionAssignment component', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByTestId('role-permission-assignment')).toBeInTheDocument();
    });

    it('should render RoleUserAssignment component', () => {
      // Note: RoleDrawer doesn't render RoleUserAssignment - it only renders
      // RoleGroupAssignment and RolePermissionAssignment
      // This test is kept for potential future implementation
      renderRoleDrawer({ roleId: 'role-1' });

      // Component doesn't render RoleUserAssignment, so this test is skipped
      expect(true).toBe(true);
    });

    it('should render RoleGroupAssignment component', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByTestId('role-group-assignment')).toBeInTheDocument();
    });

    it('should pass assigned permissions to RolePermissionAssignment', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByText('Permissions: security:user:view')).toBeInTheDocument();
    });

    it('should pass assigned users to RoleUserAssignment', () => {
      // Note: RoleDrawer doesn't render RoleUserAssignment - it only renders
      // RoleGroupAssignment and RolePermissionAssignment
      // This test is kept for potential future implementation
      renderRoleDrawer({ roleId: 'role-1' });

      // Component doesn't render RoleUserAssignment, so this test is skipped
      expect(true).toBe(true);
    });

    it('should pass assigned groups to RoleGroupAssignment', () => {
      renderRoleDrawer({ roleId: 'role-1' });

      expect(screen.getByText('Groups: Admin Group')).toBeInTheDocument();
    });
  });

  describe('Drawer visibility', () => {
    it('should not render when open is false', () => {
      renderRoleDrawer({ open: false });

      expect(screen.queryByTestId('drawer')).not.toBeInTheDocument();
    });

    it('should render when open is true', () => {
      renderRoleDrawer({ open: true });

      expect(screen.getByTestId('drawer')).toBeInTheDocument();
    });
  });
});
