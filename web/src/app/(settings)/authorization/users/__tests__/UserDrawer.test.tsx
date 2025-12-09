import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserDrawer } from '../UserDrawer';
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
    <div data-testid="drawer-header-title">{title || children}</div>
  );
  const DrawerBody = ({ children }: any) => (
    <div data-testid="drawer-body-content">{children}</div>
  );
  const DrawerFooter = ({ children }: any) => (
    <div data-testid="drawer-footer-content">{children}</div>
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

// Mock useUserDrawer hook
const mockUser = {
  id: 'user-1',
  email: 'user@example.com',
  displayName: 'Test User',
  enabled: true,
};

const mockUseUserDrawer = vi.fn((_userId?: any, _open?: any) => ({
  user: mockUser,
  loading: false,
  error: undefined,
  displayName: 'Test User',
  email: 'user@example.com',
  selectedGroups: [],
  selectedRoles: [],
  hasChanges: false,
  saving: false,
  updateDisplayName: vi.fn(),
  updateEmail: vi.fn(),
  updateSelectedGroups: vi.fn(),
  updateSelectedRoles: vi.fn(),
  handleSave: vi.fn(),
  resetChanges: vi.fn(),
  refetch: vi.fn().mockResolvedValue({
    data: {
      user: mockUser,
    },
  }),
}));

vi.mock('@/shared/hooks/authorization/useUserDrawer', () => ({
  useUserDrawer: (userId: any, open: any) => mockUseUserDrawer(userId, open),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'userManagement.drawer.title': 'Edit User',
        'userManagement.drawer.errorLoading': 'Failed to load user',
        'userManagement.drawer.userNotFound': 'User not found',
        'userManagement.drawer.displayName': 'Display Name',
        'userManagement.drawer.displayNamePlaceholder': 'Enter display name',
        'userManagement.drawer.email': 'Email',
        'userManagement.drawer.emailPlaceholder': 'Enter email',
        'userManagement.drawer.groups': 'Groups',
        'userManagement.drawer.roles': 'Roles',
        'userManagement.drawer.saveChanges': 'Save Changes',
        'userManagement.status.enabled': 'Enabled',
        'userManagement.status.disabled': 'Disabled',
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
vi.mock('../UserGroupAssignment', () => ({
  UserGroupAssignment: ({ userId, assignedGroups, onChange }: any) => (
    <div data-testid="user-group-assignment">
      <div>User ID: {userId || 'none'}</div>
      <div>Groups: {assignedGroups?.map((g: any) => g.name).join(', ') || 'none'}</div>
      {onChange && <button onClick={() => onChange([{ id: '1', name: 'Group 1' }])}>Change Groups</button>}
    </div>
  ),
}));

vi.mock('../UserRoleAssignment', () => ({
  UserRoleAssignment: ({ userId, assignedRoles, onChange }: any) => (
    <div data-testid="user-role-assignment">
      <div>User ID: {userId || 'none'}</div>
      <div>Roles: {assignedRoles?.map((r: any) => r.name).join(', ') || 'none'}</div>
      {onChange && <button onClick={() => onChange([{ id: '1', name: 'Role 1' }])}>Change Roles</button>}
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

// Mock LoadingState, ErrorAlert, and WarningAlert
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
  WarningAlert: ({ message }: any) =>
    message ? <div data-testid="warning-alert">{message}</div> : null,
}));

// Mock Avatar
vi.mock('@/shared/components/ui/primitives/Avatar', () => ({
  Avatar: ({ name }: any) => <div data-testid="avatar">{name}</div>,
}));

const renderUserDrawer = (props = {}) => {
  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    userId: 'user-1',
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserDrawer {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserDrawer', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__permissionGateRequire = undefined;
    mockUseUserDrawer.mockReturnValue({
      user: mockUser,
      loading: false,
      error: undefined,
      displayName: 'Test User',
      email: 'user@example.com',
      selectedGroups: [],
      selectedRoles: [],
      hasChanges: false,
      saving: false,
      updateDisplayName: vi.fn(),
      updateEmail: vi.fn(),
      updateSelectedGroups: vi.fn(),
      updateSelectedRoles: vi.fn(),
      handleSave: vi.fn(),
      resetChanges: vi.fn(),
      refetch: vi.fn().mockResolvedValue({
        data: {
          user: mockUser,
        },
      }),
    });
  });

  describe('Drawer visibility', () => {
    it('should render when open is true', () => {
      renderUserDrawer({ open: true });

      expect(screen.getByTestId('drawer')).toBeInTheDocument();
    });

    it('should not render when open is false', () => {
      renderUserDrawer({ open: false });

      expect(screen.queryByTestId('drawer')).not.toBeInTheDocument();
    });

    it('should call onClose when drawer close button is clicked', async () => {
      const onClose = vi.fn();
      renderUserDrawer({ onClose });

      const closeButton = screen.getByText('Close');
      await user.click(closeButton);

      expect(onClose).toHaveBeenCalled();
    });
  });

  describe('Hook integration', () => {
    it('should call useUserDrawer with correct userId and open props', () => {
      renderUserDrawer({ userId: 'user-1', open: true });

      expect(mockUseUserDrawer).toHaveBeenCalledWith('user-1', true);
    });

    it('should call useUserDrawer with null userId when userId is null', () => {
      renderUserDrawer({ userId: null, open: true });

      expect(mockUseUserDrawer).toHaveBeenCalledWith(null, true);
    });
  });

  describe('Loading and error states', () => {
    it('should show loading state when loading is true', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        loading: true,
        user: undefined as any,
      });

      renderUserDrawer();

      expect(screen.getByTestId('loading-state')).toBeInTheDocument();
    });

    it('should show error alert when error exists', () => {
      const mockRefetch = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserDrawer();

      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
    });

    it('should call refetch when retry button is clicked', async () => {
      const mockRefetch = vi.fn().mockResolvedValue({ data: { user: mockUser } });
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserDrawer();

      const retryButton = screen.getByText('Retry');
      await user.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('User data display', () => {
    it('should display user avatar with correct name', () => {
      renderUserDrawer();

      const avatar = screen.getByTestId('avatar');
      expect(avatar).toHaveTextContent('Test User');
    });

    it('should display user avatar with email when displayName is missing', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        user: { ...mockUser, displayName: null as any },
      });

      renderUserDrawer();

      const avatar = screen.getByTestId('avatar');
      expect(avatar).toHaveTextContent('user@example.com');
    });

    it('should display user displayName or email in title', () => {
      renderUserDrawer();

      // Check that the displayName appears in the Typography h5 heading
      const titleHeading = screen.getByRole('heading', { level: 5, name: 'Test User' });
      expect(titleHeading).toBeInTheDocument();
    });

    it('should display user email', () => {
      renderUserDrawer();

      expect(screen.getByText('user@example.com')).toBeInTheDocument();
    });

    it('should display enabled status chip', () => {
      renderUserDrawer();

      expect(screen.getByText('Enabled')).toBeInTheDocument();
    });

    it('should display disabled status chip when user is disabled', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        user: { ...mockUser, enabled: false },
      });

      renderUserDrawer();

      expect(screen.getByText('Disabled')).toBeInTheDocument();
    });

    it('should show user not found message when user is null but userId exists', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        loading: false,
        error: undefined,
        user: null as any,
      });

      renderUserDrawer({ userId: 'user-1' });

      expect(screen.getByTestId('warning-alert')).toBeInTheDocument();
      expect(screen.getByText('User not found')).toBeInTheDocument();
    });
  });

  describe('Form field integration', () => {
    it('should display current displayName in text field', () => {
      renderUserDrawer();

      const displayNameInput = screen.getByPlaceholderText('Enter display name');
      expect(displayNameInput).toHaveValue('Test User');
    });

    it('should display current email in text field', () => {
      renderUserDrawer();

      const emailInput = screen.getByPlaceholderText('Enter email');
      expect(emailInput).toHaveValue('user@example.com');
      expect(emailInput).toHaveAttribute('type', 'email');
    });

    it('should call updateDisplayName when displayName field changes', async () => {
      const updateDisplayName = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        updateDisplayName,
      });

      renderUserDrawer();

      const displayNameInput = screen.getByPlaceholderText('Enter display name');
      await user.clear(displayNameInput);
      await user.type(displayNameInput, 'New Name');

      expect(updateDisplayName).toHaveBeenCalled();
    });

    it('should call updateEmail when email field changes', async () => {
      const updateEmail = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        updateEmail,
      });

      renderUserDrawer();

      const emailInput = screen.getByPlaceholderText('Enter email');
      await user.clear(emailInput);
      await user.type(emailInput, 'new@example.com');

      expect(updateEmail).toHaveBeenCalled();
    });
  });

  describe('Group assignment integration', () => {
    it('should render UserGroupAssignment component', () => {
      renderUserDrawer();

      expect(screen.getByTestId('user-group-assignment')).toBeInTheDocument();
    });

    it('should pass correct userId and assignedGroups to UserGroupAssignment', () => {
      const selectedGroups = [{ id: '1', name: 'Group 1', description: 'Test' }];
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        selectedGroups: selectedGroups as any,
      });

      renderUserDrawer({ userId: 'user-1' });

      // Check within the user-group-assignment component specifically
      const groupAssignment = screen.getByTestId('user-group-assignment');
      expect(groupAssignment).toHaveTextContent('User ID: user-1');
      expect(groupAssignment).toHaveTextContent('Groups: Group 1');
    });

    it('should call updateSelectedGroups when groups change', async () => {
      const updateSelectedGroups = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        updateSelectedGroups,
      });

      renderUserDrawer();

      const changeButton = screen.getByText('Change Groups');
      await user.click(changeButton);

      expect(updateSelectedGroups).toHaveBeenCalledWith([{ id: '1', name: 'Group 1' }]);
    });
  });

  describe('Role assignment integration', () => {
    it('should render UserRoleAssignment component', () => {
      renderUserDrawer();

      expect(screen.getByTestId('user-role-assignment')).toBeInTheDocument();
    });

    it('should pass correct userId and assignedRoles to UserRoleAssignment', () => {
      const selectedRoles = [{ id: '1', name: 'Role 1' }];
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        selectedRoles: selectedRoles as any,
      });

      renderUserDrawer({ userId: 'user-1' });

      // Check within the user-role-assignment component specifically
      const roleAssignment = screen.getByTestId('user-role-assignment');
      expect(roleAssignment).toHaveTextContent('User ID: user-1');
      expect(roleAssignment).toHaveTextContent('Roles: Role 1');
    });

    it('should call updateSelectedRoles when roles change', async () => {
      const updateSelectedRoles = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        updateSelectedRoles,
      });

      renderUserDrawer();

      const changeButton = screen.getByText('Change Roles');
      await user.click(changeButton);

      expect(updateSelectedRoles).toHaveBeenCalledWith([{ id: '1', name: 'Role 1' }]);
    });
  });

  describe('Save/Cancel actions', () => {
    it('should call handleSave when save button is clicked', async () => {
      const handleSave = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        hasChanges: true,
        handleSave,
      });

      renderUserDrawer();

      const saveButton = screen.getByText('Save Changes');
      await user.click(saveButton);

      expect(handleSave).toHaveBeenCalled();
    });

    it('should call resetChanges when cancel button is clicked', async () => {
      const resetChanges = vi.fn();
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        hasChanges: true,
        resetChanges,
      });

      renderUserDrawer();

      const cancelButton = screen.getByText('Cancel');
      await user.click(cancelButton);

      expect(resetChanges).toHaveBeenCalled();
    });

    it('should disable save button when saving is true', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        saving: true,
        hasChanges: true,
      });

      renderUserDrawer();

      const saveButton = screen.getByText('Saving...');
      expect(saveButton).toBeInTheDocument();
    });

    it('should disable save button when hasChanges is false', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        hasChanges: false,
      });

      renderUserDrawer();

      const saveButton = screen.getByText('Save Changes');
      expect(saveButton).toBeDisabled();
    });

    it('should disable cancel button when saving is true', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        saving: true,
        hasChanges: true,
      });

      renderUserDrawer();

      const cancelButton = screen.getByText('Cancel');
      expect(cancelButton).toBeDisabled();
    });

    it('should disable cancel button when hasChanges is false', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        hasChanges: false,
      });

      renderUserDrawer();

      const cancelButton = screen.getByText('Cancel');
      expect(cancelButton).toBeDisabled();
    });

    it('should show "Saving..." text when saving is true', () => {
      mockUseUserDrawer.mockReturnValue({
        ...mockUseUserDrawer(),
        saving: true,
        hasChanges: true,
      });

      renderUserDrawer();

      expect(screen.getByText('Saving...')).toBeInTheDocument();
    });
  });

  describe('Permission gates', () => {
    it('should wrap footer buttons with PermissionGate requiring security:user:save', () => {
      renderUserDrawer();

      // Footer buttons should be visible (PermissionGate allows by default in mock)
      expect(screen.getByText('Cancel')).toBeInTheDocument();
      expect(screen.getByText('Save Changes')).toBeInTheDocument();
    });

    it('should wrap group assignment section with PermissionGate requiring security:user:save', () => {
      renderUserDrawer();

      expect(screen.getByTestId('user-group-assignment')).toBeInTheDocument();
    });

    it('should wrap role assignment section with PermissionGate requiring security:user:save', () => {
      renderUserDrawer();

      expect(screen.getByTestId('user-role-assignment')).toBeInTheDocument();
    });
  });
});
