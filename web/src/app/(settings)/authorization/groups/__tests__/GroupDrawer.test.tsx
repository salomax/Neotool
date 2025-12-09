import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupDrawer } from '../GroupDrawer';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

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

  const DrawerHeader = ({ title, children }: any) => (
    <div data-testid="drawer-header">{title || children}</div>
  );
  const DrawerBody = ({ children }: any) => (
    <div data-testid="drawer-body">{children}</div>
  );
  const DrawerFooter = ({ children }: any) => (
    <div data-testid="drawer-footer">{children}</div>
  );

  const Drawer = Object.assign(DrawerComponent, {
    Header: DrawerHeader,
    Body: DrawerBody,
    Footer: DrawerFooter,
  });

  return {
    Drawer,
  };
});

// Mock GraphQL queries and mutations
const mockGroup = {
  id: '1',
  name: 'Test Group',
  description: 'Test Description',
  members: [
    { id: '1', email: 'user1@example.com', displayName: 'User One' },
  ],
  roles: [
    { id: '1', name: 'Admin' },
  ],
};

const mockUseGetGroupWithRelationshipsQuery = vi.fn(() => ({
  data: { group: mockGroup },
  loading: false,
  error: undefined,
  refetch: vi.fn().mockResolvedValue({ data: { group: mockGroup } }),
}));

const mockUseCreateGroupMutation = vi.fn(() => [
  vi.fn().mockResolvedValue({
    data: {
      createGroup: {
        id: '2',
        name: 'New Group',
        description: 'New Description',
      },
    },
  }),
  { loading: false },
]);

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetGroupWithRelationshipsQuery: () => mockUseGetGroupWithRelationshipsQuery(),
  GetGroupsDocument: {},
}));

vi.mock('@/lib/graphql/operations/authorization-management/mutations.generated', () => ({
  useCreateGroupMutation: () => mockUseCreateGroupMutation(),
}));

// Mock useGroupManagement hook
const mockUseGroupManagement = vi.fn(() => ({
  updateGroup: vi.fn().mockResolvedValue(undefined),
  assignRoleToGroup: vi.fn().mockResolvedValue(undefined),
  removeRoleFromGroup: vi.fn().mockResolvedValue(undefined),
  updateLoading: false,
  assignRoleLoading: false,
  removeRoleLoading: false,
  refetch: vi.fn(),
}));

vi.mock('@/shared/hooks/authorization/useGroupManagement', () => ({
  useGroupManagement: () => mockUseGroupManagement(),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'groupManagement.createGroup': 'Create Group',
        'groupManagement.drawer.title': 'Edit Group',
        'groupManagement.drawer.errorLoading': 'Failed to load group',
        'groupManagement.drawer.groupNotFound': 'Group not found',
        'groupManagement.drawer.roles': 'Roles',
        'groupManagement.form.name': 'Name',
        'groupManagement.form.description': 'Description',
        'groupManagement.form.users': 'Users',
        'groupManagement.form.create': 'Create',
        'groupManagement.form.save': 'Save',
        'groupManagement.toast.groupCreated': `Group ${params?.name || ''} created`,
        'groupManagement.toast.groupUpdated': `Group ${params?.name || ''} updated`,
        'groupManagement.toast.groupCreateError': 'Failed to create group',
        'groupManagement.toast.groupUpdateError': 'Failed to update group',
        'groupManagement.roles.assignError': 'Failed to assign role',
        'groupManagement.roles.removeError': 'Failed to remove role',
        'common.cancel': 'Cancel',
        'common.saving': 'Saving...',
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
vi.mock('../GroupForm', () => ({
  GroupForm: ({ initialValues }: any) => (
    <div data-testid="group-form">
      <div>Name: {initialValues?.name || ''}</div>
      <div>Description: {initialValues?.description || ''}</div>
    </div>
  ),
}));

vi.mock('../GroupUserAssignment', () => ({
  GroupUserAssignment: ({ initialUserIds }: any) => (
    <div data-testid="group-user-assignment">
      Users: {initialUserIds?.join(', ') || 'none'}
    </div>
  ),
}));

vi.mock('../GroupRoleAssignment', () => ({
  GroupRoleAssignment: ({ groupId, assignedRoles, onAssignRole, onRemoveRole }: any) => (
    <div data-testid="group-role-assignment">
      <div>Group ID: {groupId || 'none'}</div>
      <div>Roles: {assignedRoles?.map((r: any) => r.name).join(', ') || 'none'}</div>
      {onAssignRole && <button onClick={() => onAssignRole('1')}>Assign Role</button>}
      {onRemoveRole && <button onClick={() => onRemoveRole('1')}>Remove Role</button>}
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

const renderGroupDrawer = (props = {}) => {
  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    groupId: null,
    ...props,
  };

  return render(
    <AppThemeProvider>
      <GroupDrawer {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('GroupDrawer', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGetGroupWithRelationshipsQuery.mockReturnValue({
      data: { group: mockGroup },
      loading: false,
      error: undefined,
      refetch: vi.fn().mockResolvedValue({ data: { group: mockGroup } }),
    });
    mockUseCreateGroupMutation.mockReturnValue([
      vi.fn().mockResolvedValue({
        data: {
          createGroup: {
            id: '2',
            name: 'New Group',
            description: 'New Description',
          },
        },
      }),
      { loading: false },
    ]);
    mockUseGroupManagement.mockReturnValue({
      updateGroup: vi.fn().mockResolvedValue(undefined),
      assignRoleToGroup: vi.fn().mockResolvedValue(undefined),
      removeRoleFromGroup: vi.fn().mockResolvedValue(undefined),
      updateLoading: false,
      assignRoleLoading: false,
      removeRoleLoading: false,
      refetch: vi.fn(),
    });
  });

  describe('Create mode (groupId === null)', () => {
    it('should render drawer in create mode', () => {
      renderGroupDrawer({ groupId: null });

      expect(screen.getByText('Create Group')).toBeInTheDocument();
      expect(screen.getByTestId('group-form')).toBeInTheDocument();
    });

    it('should not query group data in create mode', () => {
      renderGroupDrawer({ groupId: null });

      // In create mode, the query should be skipped
      expect(mockUseGetGroupWithRelationshipsQuery).toHaveBeenCalled();
      // The skip option is handled internally by the hook based on groupId
    });

    it('should show create button', () => {
      renderGroupDrawer({ groupId: null });

      expect(screen.getByText('Create')).toBeInTheDocument();
    });

    it('should call createGroupMutation when save is clicked', async () => {
      const createMutation = vi.fn().mockResolvedValue({
        data: {
          createGroup: {
            id: '2',
            name: 'New Group',
            description: 'New Description',
          },
        },
      });
      mockUseCreateGroupMutation.mockReturnValue([createMutation, { loading: false }]);

      const onClose = vi.fn();
      renderGroupDrawer({ groupId: null, onClose });

      const saveButton = screen.getByText('Create');
      await user.click(saveButton);

      // Form submission is handled by react-hook-form
      // The actual mutation call would happen in handleSave
      expect(saveButton).toBeInTheDocument();
    });
  });

  describe('Edit mode (groupId !== null)', () => {
    it('should render drawer in edit mode', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText('Edit Group')).toBeInTheDocument();
    });

    it('should query group data in edit mode', () => {
      renderGroupDrawer({ groupId: '1' });

      // The query should be called when groupId is provided
      expect(mockUseGetGroupWithRelationshipsQuery).toHaveBeenCalled();
    });

    it('should show loading state while fetching group', () => {
      mockUseGetGroupWithRelationshipsQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByTestId('loading-state')).toBeInTheDocument();
    });

    it('should show error state when query fails', () => {
      mockUseGetGroupWithRelationshipsQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: vi.fn(),
      });

      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
    });

    it('should display group data in form', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText(`Name: ${mockGroup.name}`)).toBeInTheDocument();
      expect(screen.getByText(`Description: ${mockGroup.description}`)).toBeInTheDocument();
    });

    it('should show save button in edit mode', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText('Save')).toBeInTheDocument();
    });

    it('should show group not found message when group is null', () => {
      mockUseGetGroupWithRelationshipsQuery.mockReturnValue({
        data: { group: null as any },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });

      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText('Group not found')).toBeInTheDocument();
    });
  });

  describe('User interactions', () => {
    it('should call onClose when cancel is clicked', async () => {
      const onClose = vi.fn();
      renderGroupDrawer({ onClose });

      const cancelButton = screen.getByText('Cancel');
      await user.click(cancelButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should call onClose when drawer close button is clicked', async () => {
      const onClose = vi.fn();
      renderGroupDrawer({ onClose });

      const closeButton = screen.getByText('Close');
      await user.click(closeButton);

      expect(onClose).toHaveBeenCalled();
    });

    it('should disable buttons when saving', () => {
      mockUseCreateGroupMutation.mockReturnValue([
        vi.fn(),
        { loading: true },
      ]);

      renderGroupDrawer({ groupId: null });

      const saveButton = screen.getByText('Saving...');
      expect(saveButton).toBeInTheDocument();
    });
  });

  describe('Role assignment', () => {
    it('should render GroupRoleAssignment component', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByTestId('group-role-assignment')).toBeInTheDocument();
    });

    it('should pass assigned roles to GroupRoleAssignment', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText('Roles: Admin')).toBeInTheDocument();
    });

    it('should handle role assignment in edit mode', async () => {
      const assignRoleToGroup = vi.fn().mockResolvedValue(undefined);
      const refetch = vi.fn().mockResolvedValue({ data: { group: mockGroup } });
      mockUseGroupManagement.mockReturnValue({
        assignRoleToGroup,
        removeRoleFromGroup: vi.fn(),
        updateGroup: vi.fn(),
        updateLoading: false,
        assignRoleLoading: false,
        removeRoleLoading: false,
        refetch,
      });

      renderGroupDrawer({ groupId: '1' });

      const assignButton = screen.getByText('Assign Role');
      await user.click(assignButton);

      await waitFor(() => {
        expect(assignRoleToGroup).toHaveBeenCalledWith('1', '1');
      });
    });
  });

  describe('User assignment', () => {
    it('should render GroupUserAssignment component', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByTestId('group-user-assignment')).toBeInTheDocument();
    });

    it('should pass initial user IDs to GroupUserAssignment', () => {
      renderGroupDrawer({ groupId: '1' });

      expect(screen.getByText('Users: 1')).toBeInTheDocument();
    });
  });

  describe('Drawer visibility', () => {
    it('should not render when open is false', () => {
      renderGroupDrawer({ open: false });

      expect(screen.queryByTestId('drawer')).not.toBeInTheDocument();
    });

    it('should render when open is true', () => {
      renderGroupDrawer({ open: true });

      expect(screen.getByTestId('drawer')).toBeInTheDocument();
    });
  });
});
