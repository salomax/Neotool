import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserStatusToggle } from '../UserStatusToggle';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { User } from '@/shared/hooks/authorization/useUserManagement';

// Mock Switch component
vi.mock('@/shared/components/ui/primitives/Switch', () => ({
  Switch: ({ checked, onChange, disabled, size, showStatus, name, 'data-testid': dataTestId }: any) => {
    // Capture props for testing
    (window as any).__switchProps = {
      checked,
      onChange,
      disabled,
      size,
      showStatus,
      name,
      'data-testid': dataTestId,
    };
    return (
      <button
        onClick={() => onChange(!checked)}
        disabled={disabled}
        data-testid={dataTestId}
        data-checked={checked}
      >
        {checked ? 'ON' : 'OFF'}
      </button>
    );
  },
}));

// Mock MUI components
vi.mock('@mui/material', () => ({
  Box: ({ children, sx, ...props }: any) => <div {...props}>{children}</div>,
  Tooltip: ({ children, title }: any) => (
    <div data-testid="tooltip" data-title={title}>
      {children}
    </div>
  ),
  CircularProgress: ({ size, sx }: any) => (
    <div data-testid="circular-progress" data-size={size}>
      Loading...
    </div>
  ),
}));

const mockUser: User = {
  id: 'user-1',
  email: 'user@example.com',
  displayName: 'Test User',
  enabled: true,
};

const renderUserStatusToggle = (props = {}) => {
  const defaultProps = {
    user: mockUser,
    enabled: true,
    onToggle: vi.fn().mockResolvedValue(undefined),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserStatusToggle {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserStatusToggle', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__switchProps = undefined;
  });

  describe('Props forwarding to Switch', () => {
    it('should pass checked prop (enabled state)', () => {
      renderUserStatusToggle({ enabled: true });

      const props = (window as any).__switchProps;
      expect(props.checked).toBe(true);
    });

    it('should pass onChange handler', () => {
      renderUserStatusToggle();

      const props = (window as any).__switchProps;
      expect(props.onChange).toBeDefined();
      expect(typeof props.onChange).toBe('function');
    });

    it('should pass disabled state (isToggling || loading)', () => {
      renderUserStatusToggle({ loading: true });

      const props = (window as any).__switchProps;
      expect(props.disabled).toBe(true);
    });

    it('should pass size="small"', () => {
      renderUserStatusToggle();

      const props = (window as any).__switchProps;
      expect(props.size).toBe('small');
    });

    it('should pass showStatus={false}', () => {
      renderUserStatusToggle();

      const props = (window as any).__switchProps;
      expect(props.showStatus).toBe(false);
    });

    it('should pass correct name attribute', () => {
      renderUserStatusToggle({ user: mockUser });

      const props = (window as any).__switchProps;
      expect(props.name).toBe('user-status-user-1');
    });

    it('should pass correct data-testid attribute', () => {
      renderUserStatusToggle({ user: mockUser });

      const props = (window as any).__switchProps;
      expect(props['data-testid']).toBe('user-status-toggle-user-1');
    });
  });

  describe('Toggle functionality', () => {
    it('should call onToggle with correct userId and new enabled state', async () => {
      const onToggle = vi.fn().mockResolvedValue(undefined);
      renderUserStatusToggle({ enabled: true, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      await waitFor(() => {
        expect(onToggle).toHaveBeenCalledWith('user-1', false);
      });
    });

    it('should call onToggle with enabled=true when toggling from disabled', async () => {
      const onToggle = vi.fn().mockResolvedValue(undefined);
      renderUserStatusToggle({ enabled: false, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      await waitFor(() => {
        expect(onToggle).toHaveBeenCalledWith('user-1', true);
      });
    });

    it('should set isToggling state to true during operation', async () => {
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      // Check that CircularProgress appears (indicating isToggling is true)
      await waitFor(() => {
        expect(screen.getByTestId('circular-progress')).toBeInTheDocument();
      });
    });

    it('should clear isToggling state after operation completes', async () => {
      const onToggle = vi.fn().mockResolvedValue(undefined);
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      await waitFor(() => {
        expect(screen.queryByTestId('circular-progress')).not.toBeInTheDocument();
      });
    });

    it('should prevent toggle when loading is true', async () => {
      const onToggle = vi.fn();
      renderUserStatusToggle({ loading: true, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      expect(switchButton).toBeDisabled();

      // Try to click (should not work)
      await user.click(switchButton);

      expect(onToggle).not.toHaveBeenCalled();
    });

    it('should prevent toggle when isToggling is true', async () => {
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Click once to start toggle
      await user.click(switchButton);

      // Try to click again while toggling (should be disabled)
      await waitFor(() => {
        expect(switchButton).toBeDisabled();
      });
    });
  });

  describe('Loading state display', () => {
    it('should show CircularProgress when isToggling is true', async () => {
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      await waitFor(() => {
        expect(screen.getByTestId('circular-progress')).toBeInTheDocument();
      });
    });

    it('should hide CircularProgress when isToggling is false', () => {
      renderUserStatusToggle();

      expect(screen.queryByTestId('circular-progress')).not.toBeInTheDocument();
    });
  });

  describe('Tooltip', () => {
    it('should show "Disable user" tooltip when enabled is true', () => {
      renderUserStatusToggle({ enabled: true });

      const tooltip = screen.getByTestId('tooltip');
      expect(tooltip).toHaveAttribute('data-title', 'Disable user');
    });

    it('should show "Enable user" tooltip when enabled is false', () => {
      renderUserStatusToggle({ enabled: false });

      const tooltip = screen.getByTestId('tooltip');
      expect(tooltip).toHaveAttribute('data-title', 'Enable user');
    });
  });

  describe('Error handling', () => {
    it('should handle errors gracefully without crashing', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Should not throw
      await user.click(switchButton);

      await waitFor(() => {
        // Component should still be rendered
        expect(screen.getByTestId('user-status-toggle-user-1')).toBeInTheDocument();
      });
    });

    it('should clear isToggling state even if error occurs', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);

      await waitFor(() => {
        // isToggling should be cleared (no CircularProgress)
        expect(screen.queryByTestId('circular-progress')).not.toBeInTheDocument();
      });
    });
  });
});
