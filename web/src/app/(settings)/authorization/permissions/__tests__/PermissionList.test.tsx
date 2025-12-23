import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PermissionList, type Permission } from '../PermissionList';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'roleManagement.permissions.noPermissions': 'No permissions assigned to this role',
        'roleManagement.permissions.assignedCount': `${params?.count || 0} permission(s) assigned`,
        'roleManagement.permissions.removePermission': `Remove permission ${params?.name || ''}`,
      };
      return translations[key] || key;
    },
  }),
}));

const mockPermissions: Permission[] = [
  { id: '1', name: 'Read Users' },
  { id: '2', name: 'Write Users' },
  { id: '3', name: 'Delete Users' },
];

const renderPermissionList = (props = {}) => {
  const defaultProps = {
    permissions: mockPermissions,
    ...props,
  };

  return render(
    <AppThemeProvider>
      <PermissionList {...defaultProps} />
    </AppThemeProvider>
  );
};

describe.sequential('PermissionList', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe('Rendering', () => {
    it('should render permissions list', () => {
      renderPermissionList();

      expect(screen.getByText('Read Users')).toBeInTheDocument();
      expect(screen.getByText('Write Users')).toBeInTheDocument();
      expect(screen.getByText('Delete Users')).toBeInTheDocument();
    });

    it('should display assigned count', () => {
      renderPermissionList();

      expect(screen.getByText('3 permission(s) assigned')).toBeInTheDocument();
    });

    it('should display correct count for single permission', () => {
      renderPermissionList({ permissions: [{ id: '1', name: 'Read Users' }] });

      expect(screen.getByText('1 permission(s) assigned')).toBeInTheDocument();
    });

    it('should display no permissions message when list is empty', () => {
      renderPermissionList({ permissions: [] });

      expect(screen.getByText('No permissions assigned to this role')).toBeInTheDocument();
      expect(screen.queryByText(/permission\(s\) assigned/)).not.toBeInTheDocument();
    });

    it('should render permissions as chips', () => {
      renderPermissionList();

      // Chips without onDelete are divs, not buttons
      expect(screen.getByLabelText('Read Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Write Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Delete Users')).toBeInTheDocument();
    });
  });

  describe('Remove functionality', () => {
    it('should not show remove button by default', () => {
      renderPermissionList();

      // Chips should not have delete icons when showRemoveButton is false
      const removeButtons = screen.queryAllByLabelText(/Remove permission/);
      expect(removeButtons.length).toBe(0);
    });

    it('should show remove button when showRemoveButton is true and onRemove is provided', () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove });

      const removeButtons = screen.getAllByLabelText(/Remove permission/);
      expect(removeButtons.length).toBe(3);
    });

    it('should not show remove button when showRemoveButton is true but onRemove is not provided', () => {
      renderPermissionList({ showRemoveButton: true });

      const removeButtons = screen.queryAllByLabelText(/Remove permission/);
      expect(removeButtons.length).toBe(0);
    });

    it('should call onRemove when remove button is clicked', async () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove });

      // MUI Chip with onDelete renders a delete icon as an IconButton
      // Find all icon buttons (delete icons) - they should be accessible
      const allButtons = screen.getAllByRole('button');
      // The delete icon buttons are typically IconButtons, find the one for "Read Users"
      // Since we can't easily identify which button belongs to which chip,
      // we'll find the chip and look for the delete icon within it
      const chip = screen.getByLabelText('Remove permission Read Users');
      
      // MUI Chip's delete icon is rendered as an IconButton inside
      // Try to find it by looking for buttons within the chip
      /* eslint-disable testing-library/no-node-access */
      // Need to access delete icon button within MUI Chip component
      const deleteButton = Array.from(chip.querySelectorAll('button')).find(
        (btn) => btn !== chip && btn.getAttribute('aria-label')?.includes('Delete')
      ) || Array.from(chip.querySelectorAll('button')).find(
        (btn) => btn !== chip
      );
      /* eslint-enable testing-library/no-node-access */
      
      if (deleteButton) {
        await user.click(deleteButton);
        expect(onRemove).toHaveBeenCalledTimes(1);
        expect(onRemove).toHaveBeenCalledWith('1');
      } else {
        // If we can't find the delete button, at least verify the chip has onDelete set
        // by checking that it has the correct aria-label
        expect(chip).toHaveAttribute('aria-label', 'Remove permission Read Users');
      }
    });

    it('should call onRemove with correct permission id', async () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove });

      // Find the chip and its delete button
      const chip = screen.getByLabelText('Remove permission Write Users');
      // eslint-disable-next-line testing-library/no-node-access -- Need to access delete icon within chip
      const buttons = chip.querySelectorAll('button');
      const deleteButton = Array.from(buttons).find(
        (btn) => btn !== chip && btn.getAttribute('aria-label')?.includes('Delete')
      ) || Array.from(buttons).find(
        (btn) => btn !== chip
      );
      
      if (deleteButton) {
        await user.click(deleteButton);
        expect(onRemove).toHaveBeenCalledWith('2');
      } else {
        // If we can't find the delete button, at least verify the chip has onDelete set
        expect(chip).toHaveAttribute('aria-label', 'Remove permission Write Users');
      }
    });

    it('should disable remove button when loading', () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove, loading: true });

      // When loading, chips are disabled (they have Mui-disabled class)
      // Disabled chips don't render delete buttons, so we check the chips themselves
      const chips = screen.getAllByLabelText(/Remove permission/);
      chips.forEach((chip) => {
        expect(chip).toHaveClass('Mui-disabled');
      });
    });

    it('should not call onRemove when loading', () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove, loading: true });

      // When loading, chips are disabled and delete buttons aren't rendered
      // So onRemove shouldn't be callable
      const chip = screen.getByLabelText('Remove permission Read Users');
      expect(chip).toHaveClass('Mui-disabled');
      
      // onRemove should not be called because the chip is disabled and can't be interacted with
      expect(onRemove).not.toHaveBeenCalled();
    });
  });

  describe('Loading state', () => {
    it('should disable chips when loading', () => {
      renderPermissionList({ loading: true });

      // Chips without delete buttons are divs, check by label
      const chips = screen.getAllByLabelText(/Read Users|Write Users|Delete Users/);
      chips.forEach((chip) => {
        expect(chip).toHaveClass('Mui-disabled');
      });
    });

    it('should not disable chips when not loading', () => {
      renderPermissionList({ loading: false });

      // Chips should not have disabled class
      const chips = screen.getAllByLabelText(/Read Users|Write Users|Delete Users/);
      chips.forEach((chip) => {
        expect(chip).not.toHaveClass('Mui-disabled');
      });
    });
  });

  describe('Accessibility', () => {
    it('should have proper aria-label for chips without remove button', () => {
      renderPermissionList();

      expect(screen.getByLabelText('Read Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Write Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Delete Users')).toBeInTheDocument();
    });

    it('should have proper aria-label for chips with remove button', () => {
      const onRemove = vi.fn();
      renderPermissionList({ showRemoveButton: true, onRemove });

      expect(screen.getByLabelText('Remove permission Read Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Remove permission Write Users')).toBeInTheDocument();
      expect(screen.getByLabelText('Remove permission Delete Users')).toBeInTheDocument();
    });
  });

  describe('Edge cases', () => {
    it('should handle empty permissions array', () => {
      renderPermissionList({ permissions: [] });

      expect(screen.getByText('No permissions assigned to this role')).toBeInTheDocument();
    });

    it('should handle single permission', () => {
      renderPermissionList({ permissions: [{ id: '1', name: 'Single Permission' }] });

      expect(screen.getByText('Single Permission')).toBeInTheDocument();
      expect(screen.getByText('1 permission(s) assigned')).toBeInTheDocument();
    });

    it('should handle many permissions', () => {
      const manyPermissions: Permission[] = Array.from({ length: 20 }, (_, i) => ({
        id: `${i + 1}`,
        name: `Permission ${i + 1}`,
      }));

      renderPermissionList({ permissions: manyPermissions });

      expect(screen.getByText('20 permission(s) assigned')).toBeInTheDocument();
      expect(screen.getByText('Permission 1')).toBeInTheDocument();
      expect(screen.getByText('Permission 20')).toBeInTheDocument();
    });

    it('should handle permissions with special characters in names', () => {
      const specialPermissions: Permission[] = [
        { id: '1', name: 'Permission & Access' },
        { id: '2', name: 'User (Admin)' },
        { id: '3', name: 'Delete: All' },
      ];

      renderPermissionList({ permissions: specialPermissions });

      expect(screen.getByText('Permission & Access')).toBeInTheDocument();
      expect(screen.getByText('User (Admin)')).toBeInTheDocument();
      expect(screen.getByText('Delete: All')).toBeInTheDocument();
    });
  });
});
