import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserStatusToggle } from '../UserStatusToggle';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { User } from '@/shared/hooks/authorization/useUserManagement';

const mockExecuteUpdate = vi.fn();

vi.mock('@/shared/hooks/mutations', () => ({
  useOptimisticUpdate: ({ value }: { value: boolean }) => {
    const [optimisticValue, setOptimisticValue] = React.useState(value);
    const [isUpdating, setIsUpdating] = React.useState(false);
    const previousValueRef = React.useRef(value);

    React.useEffect(() => {
      if (!isUpdating) {
        previousValueRef.current = value;
        setOptimisticValue(value);
      }
    }, [value, isUpdating]);

    const executeUpdate = async (newValue: boolean, updateFn: () => Promise<void>) => {
      previousValueRef.current = optimisticValue;
      setIsUpdating(true);
      setOptimisticValue(newValue);
      try {
        await mockExecuteUpdate(newValue, updateFn);
      } catch (error) {
        setOptimisticValue(previousValueRef.current);
      } finally {
        setIsUpdating(false);
      }
    };

    return { optimisticValue, isUpdating, executeUpdate };
  },
}));

// Mock i18n
vi.mock('@/shared/i18n', () => ({
  useTranslation: vi.fn(() => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'userManagement.status.enable': 'Enable user',
        'userManagement.status.disable': 'Disable user',
        'userManagement.status.enabling': 'Enabling user...',
        'userManagement.status.disabling': 'Disabling user...',
      };
      return translations[key] || key;
    },
  })),
}));

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

// Run sequentially to avoid concurrent renders interfering with shared mocks
describe.sequential('UserStatusToggle', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__switchProps = undefined;
    
    mockExecuteUpdate.mockImplementation(async (_newValue: boolean, updateFn: () => Promise<void>) => {
      await updateFn();
    });
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

    it('should pass disabled state based on isToggling', () => {
      renderUserStatusToggle();

      const props = (window as any).__switchProps;
      // Initially not disabled (not toggling)
      expect(props.disabled).toBe(false);
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
      const onToggle = vi.fn<[string, boolean], Promise<void>>(() => new Promise<void>((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Click to start the operation
      await user.click(switchButton);
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

      // Check that CircularProgress appears (indicating isToggling is true)
      await waitFor(() => {
        expect(screen.getByTestId('circular-progress')).toBeInTheDocument();
      }, { timeout: 2000 });
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

    it('should not be disabled when loading prop is provided (deprecated)', () => {
      // The loading prop is deprecated and no longer affects the disabled state
      const onToggle = vi.fn();
      renderUserStatusToggle({ loading: true, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      // Should not be disabled since loading prop is no longer used
      expect(switchButton).not.toBeDisabled();
    });

    it('should prevent toggle when isToggling is true', async () => {
      const onToggle = vi.fn<[string, boolean], Promise<void>>(() => new Promise<void>((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Click once to start toggle
      await user.click(switchButton);
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

      // Try to click again while toggling (should be disabled)
      await waitFor(() => {
        expect(switchButton).toBeDisabled();
      });
    });
  });

  describe('Loading state display', () => {
    it('should show CircularProgress when isToggling is true', async () => {
      const onToggle = vi.fn<[string, boolean], Promise<void>>(() => new Promise<void>((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

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

    it('should show "Disabling user..." tooltip when toggling from enabled', async () => {
      const onToggle = vi.fn<[string, boolean], Promise<void>>(() => new Promise<void>((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ enabled: true, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Click the button to start the toggle - this should trigger executeUpdate
      // which sets isUpdating=true and optimisticValue=false
      const clickPromise = user.click(switchButton);
      
      // Force re-render so the component picks up updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );
      
      // Check the tooltip while the async operation is in progress
      await waitFor(() => {
        const tooltip = screen.getByTestId('tooltip');
        expect(tooltip).toHaveAttribute('data-title', 'Disabling user...');
      }, { timeout: 200 });
      
      await clickPromise;
    });

    it('should show "Enabling user..." tooltip when toggling from disabled', async () => {
      const onToggle = vi.fn<[string, boolean], Promise<void>>(() => new Promise<void>((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ enabled: false, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      // Click the button to start the toggle - this should trigger executeUpdate
      // which sets isUpdating=true and optimisticValue=true
      const clickPromise = user.click(switchButton);
      
      // Force re-render so the component picks up updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={false} onToggle={onToggle} />
        </AppThemeProvider>
      );
      
      // Check the tooltip while the async operation is in progress
      await waitFor(() => {
        const tooltip = screen.getByTestId('tooltip');
        expect(tooltip).toHaveAttribute('data-title', 'Enabling user...');
      }, { timeout: 200 });
      
      await clickPromise;
    });
  });

  describe('Error handling', () => {
    it('should handle errors gracefully without crashing', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));

      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      await act(async () => {
        await user.click(switchButton);
      });

      await waitFor(() => {
        expect(screen.getByTestId('user-status-toggle-user-1')).toBeInTheDocument();
      }, { timeout: 1000 });
      
      expect(onToggle).toHaveBeenCalled();
    });

    it('should clear isToggling state even if error occurs', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));
      
      const { rerender } = renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      await act(async () => {
        await user.click(switchButton);
      });
      
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

      await waitFor(() => {
        // isToggling should be cleared (no CircularProgress)
        expect(screen.queryByTestId('circular-progress')).not.toBeInTheDocument();
      }, { timeout: 1000 });
    });
  });

  afterEach(() => {
    cleanup();
  });
});
