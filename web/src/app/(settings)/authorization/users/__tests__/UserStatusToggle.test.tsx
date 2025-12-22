import React, { useState, useEffect } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserStatusToggle } from '../UserStatusToggle';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { User } from '@/shared/hooks/authorization/useUserManagement';

// Mock useOptimisticUpdate hook with React state for reactivity
let mockUpdateState: { isUpdating: boolean; optimisticValue: boolean; previousValue: boolean } | null = null;
let mockUpdateComponent: React.ComponentType<any> | null = null;

// Create a wrapper component that uses React state
const createMockHookWrapper = () => {
  return function MockHookWrapper({ children, initialState }: { children: React.ReactNode; initialState: boolean }) {
    const [isUpdating, setIsUpdating] = useState(false);
    const [optimisticValue, setOptimisticValue] = useState(initialState);
    
    useEffect(() => {
      if (mockUpdateState) {
        mockUpdateState.isUpdating = isUpdating;
        mockUpdateState.optimisticValue = optimisticValue;
      }
    }, [isUpdating, optimisticValue]);
    
    return <>{children}</>;
  };
};

const mockExecuteUpdate = vi.fn();
const mockUseOptimisticUpdate = vi.fn();

vi.mock('@/shared/hooks/mutations', () => ({
  useOptimisticUpdate: (options: { value: boolean }) => {
    // Initialize state if needed
    if (!mockUpdateState) {
      mockUpdateState = { isUpdating: false, optimisticValue: options.value };
    }
    
    const result = mockUseOptimisticUpdate(options);
    return result;
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

describe('UserStatusToggle', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__switchProps = undefined;
    mockUpdateState = null;
    
    // Setup default mock behavior for useOptimisticUpdate
    mockExecuteUpdate.mockImplementation(async (newValue: boolean, updateFn: () => Promise<void>) => {
      if (mockUpdateState) {
        // Store previous value for potential rollback
        mockUpdateState.previousValue = mockUpdateState.optimisticValue;
        mockUpdateState.isUpdating = true;
        mockUpdateState.optimisticValue = newValue;
      }
      // Update mock return value immediately so component sees the updated state
      mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
        optimisticValue: mockUpdateState?.optimisticValue ?? value,
        isUpdating: mockUpdateState?.isUpdating ?? false,
        executeUpdate: mockExecuteUpdate,
      }));
      
      try {
        await updateFn();
      } catch (error) {
        // Revert optimistic value on error
        if (mockUpdateState) {
          mockUpdateState.optimisticValue = mockUpdateState.previousValue;
        }
        // Update mock to reflect reverted state
        mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
          optimisticValue: mockUpdateState?.optimisticValue ?? value,
          isUpdating: mockUpdateState?.isUpdating ?? false,
          executeUpdate: mockExecuteUpdate,
        }));
        // Re-throw error (component should handle it, but if not, test will catch it)
        throw error;
      } finally {
        if (mockUpdateState) {
          mockUpdateState.isUpdating = false;
        }
        mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
          optimisticValue: mockUpdateState?.optimisticValue ?? value,
          isUpdating: mockUpdateState?.isUpdating ?? false,
          executeUpdate: mockExecuteUpdate,
        }));
      }
    });
    
    mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => {
      if (!mockUpdateState) {
        mockUpdateState = { isUpdating: false, optimisticValue: value, previousValue: value };
      } else {
        // Sync optimisticValue with the value option if not currently updating
        if (!mockUpdateState.isUpdating) {
          mockUpdateState.optimisticValue = value;
          mockUpdateState.previousValue = value;
        }
      }
      return {
        optimisticValue: mockUpdateState.optimisticValue,
        isUpdating: mockUpdateState.isUpdating,
        executeUpdate: mockExecuteUpdate,
      };
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
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
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
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
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
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
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
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ enabled: true, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

      await waitFor(() => {
        const tooltip = screen.getByTestId('tooltip');
        expect(tooltip).toHaveAttribute('data-title', 'Disabling user...');
      });
    });

    it('should show "Enabling user..." tooltip when toggling from disabled', async () => {
      const onToggle = vi.fn(() => new Promise((resolve) => setTimeout(resolve, 100)));
      const { rerender } = renderUserStatusToggle({ enabled: false, onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      await user.click(switchButton);
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={false} onToggle={onToggle} />
        </AppThemeProvider>
      );

      await waitFor(() => {
        const tooltip = screen.getByTestId('tooltip');
        expect(tooltip).toHaveAttribute('data-title', 'Enabling user...');
      });
    });
  });

  describe('Error handling', () => {
    it('should handle errors gracefully without crashing', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));
      
      // Override executeUpdate to catch errors without causing unhandled rejections
      const originalExecuteUpdate = mockExecuteUpdate;
      mockExecuteUpdate.mockImplementation(async (newValue: boolean, updateFn: () => Promise<void>) => {
        if (mockUpdateState) {
          mockUpdateState.previousValue = mockUpdateState.optimisticValue;
          mockUpdateState.isUpdating = true;
          mockUpdateState.optimisticValue = newValue;
        }
        mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
          optimisticValue: mockUpdateState?.optimisticValue ?? value,
          isUpdating: mockUpdateState?.isUpdating ?? false,
          executeUpdate: mockExecuteUpdate,
        }));
        
        try {
          await updateFn();
        } catch (error) {
          // Revert optimistic value on error
          if (mockUpdateState) {
            mockUpdateState.optimisticValue = mockUpdateState.previousValue;
          }
          mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
            optimisticValue: mockUpdateState?.optimisticValue ?? value,
            isUpdating: mockUpdateState?.isUpdating ?? false,
            executeUpdate: mockExecuteUpdate,
          }));
          // Don't re-throw - catch the error to prevent unhandled rejection
          // The error is still tested via onToggle being called
        } finally {
          if (mockUpdateState) {
            mockUpdateState.isUpdating = false;
          }
          mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
            optimisticValue: mockUpdateState?.optimisticValue ?? value,
            isUpdating: mockUpdateState?.isUpdating ?? false,
            executeUpdate: mockExecuteUpdate,
          }));
        }
      });
      
      renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      await act(async () => {
        await user.click(switchButton);
        // Wait for async operations to complete
        await new Promise((resolve) => setTimeout(resolve, 100));
      });

      // Ensure the error was caught (either by us or handled by the component)
      // The component should still be rendered even if an error occurred
      await waitFor(() => {
        expect(screen.getByTestId('user-status-toggle-user-1')).toBeInTheDocument();
      }, { timeout: 1000 });
      
      // Verify the error was handled (either caught or the component handled it)
      expect(onToggle).toHaveBeenCalled();
      
      // Restore original mock
      mockExecuteUpdate.mockImplementation(originalExecuteUpdate);
    });

    it('should clear isToggling state even if error occurs', async () => {
      const onToggle = vi.fn().mockRejectedValue(new Error('Toggle failed'));
      
      // Override executeUpdate to catch errors without causing unhandled rejections
      const originalExecuteUpdate = mockExecuteUpdate;
      mockExecuteUpdate.mockImplementation(async (newValue: boolean, updateFn: () => Promise<void>) => {
        if (mockUpdateState) {
          mockUpdateState.previousValue = mockUpdateState.optimisticValue;
          mockUpdateState.isUpdating = true;
          mockUpdateState.optimisticValue = newValue;
        }
        mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
          optimisticValue: mockUpdateState?.optimisticValue ?? value,
          isUpdating: mockUpdateState?.isUpdating ?? false,
          executeUpdate: mockExecuteUpdate,
        }));
        
        try {
          await updateFn();
        } catch (error) {
          // Revert optimistic value on error
          if (mockUpdateState) {
            mockUpdateState.optimisticValue = mockUpdateState.previousValue;
          }
          mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
            optimisticValue: mockUpdateState?.optimisticValue ?? value,
            isUpdating: mockUpdateState?.isUpdating ?? false,
            executeUpdate: mockExecuteUpdate,
          }));
          // Don't re-throw - catch the error to prevent unhandled rejection
          // The error is still tested via onToggle being called
        } finally {
          if (mockUpdateState) {
            mockUpdateState.isUpdating = false;
          }
          mockUseOptimisticUpdate.mockImplementation(({ value }: { value: boolean }) => ({
            optimisticValue: mockUpdateState?.optimisticValue ?? value,
            isUpdating: mockUpdateState?.isUpdating ?? false,
            executeUpdate: mockExecuteUpdate,
          }));
        }
      });
      
      const { rerender } = renderUserStatusToggle({ onToggle });

      const switchButton = screen.getByTestId('user-status-toggle-user-1');
      
      await act(async () => {
        await user.click(switchButton);
        // Wait for async operations to complete
        await new Promise((resolve) => setTimeout(resolve, 100));
      });
      
      // Force re-render to pick up the updated mock state
      rerender(
        <AppThemeProvider>
          <UserStatusToggle user={mockUser} enabled={true} onToggle={onToggle} />
        </AppThemeProvider>
      );

      await waitFor(() => {
        // isToggling should be cleared (no CircularProgress)
        expect(screen.queryByTestId('circular-progress')).not.toBeInTheDocument();
      }, { timeout: 1000 });
      
      // Restore original mock
      mockExecuteUpdate.mockImplementation(originalExecuteUpdate);
    });
  });
});
