import React from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Switch } from '../Switch';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderSwitch = (props = {}) => {
  return render(
    <AppThemeProvider>
      <Switch {...props} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('Switch', () => {
  describe('Rendering', () => {
    it('renders switch component', () => {
      renderSwitch();
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeInTheDocument();
    });

    it('renders with label', () => {
      renderSwitch({ label: 'Toggle Switch' });
      expect(screen.getByText('Toggle Switch')).toBeInTheDocument();
    });

    it('does not render label when showLabel is false', () => {
      renderSwitch({ label: 'Toggle Switch', showLabel: false });
      expect(screen.queryByText('Toggle Switch')).not.toBeInTheDocument();
    });

    it('renders helper text', () => {
      renderSwitch({ helperText: 'This is helper text' });
      expect(screen.getByText('This is helper text')).toBeInTheDocument();
    });

    it('renders with custom label component', () => {
      const CustomLabel = () => <span>Custom Label</span>;
      renderSwitch({ labelComponent: <CustomLabel /> });
      expect(screen.getByText('Custom Label')).toBeInTheDocument();
    });
  });

  describe('Controlled state', () => {
    it('renders as checked when controlled with checked prop', () => {
      renderSwitch({ checked: true });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeChecked();
    });

    it('renders as unchecked when controlled with checked prop', () => {
      renderSwitch({ checked: false });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).not.toBeChecked();
    });

    it('calls onChange when toggled in controlled mode', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderSwitch({ checked: false, onChange: handleChange });

      const switchElement = screen.getByRole('switch');
      await user.click(switchElement);

      expect(handleChange).toHaveBeenCalledWith(true);
      expect(handleChange).toHaveBeenCalledTimes(1);
    });

    it('does not update internal state when controlled', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      const { rerender } = renderSwitch({ checked: false, onChange: handleChange });

      const switchElement = screen.getByRole('switch');
      await user.click(switchElement);

      // Re-render with same checked value
      rerender(
        <AppThemeProvider>
          <Switch checked={false} onChange={handleChange} />
        </AppThemeProvider>
      );

      expect(switchElement).not.toBeChecked();
    });
  });

  describe('Uncontrolled state', () => {
    it('renders with defaultChecked', () => {
      renderSwitch({ defaultChecked: true });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeChecked();
    });

    it('updates internal state when toggled in uncontrolled mode', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderSwitch({ defaultChecked: false, onChange: handleChange });

      const switchElement = screen.getByRole('switch');
      await user.click(switchElement);

      expect(switchElement).toBeChecked();
      expect(handleChange).toHaveBeenCalledWith(true);
    });
  });

  describe('Disabled and read-only', () => {
    it('disables switch when disabled prop is true', () => {
      renderSwitch({ disabled: true });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeDisabled();
    });

    it('does not call onChange when disabled', () => {
      const handleChange = vi.fn();
      renderSwitch({ disabled: true, onChange: handleChange });

      const switchElement = screen.getByRole('switch');
      fireEvent.click(switchElement);

      expect(handleChange).not.toHaveBeenCalled();
    });

    it('does not call onChange when readOnly', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderSwitch({ readOnly: true, defaultChecked: false, onChange: handleChange });

      const switchElement = screen.getByRole('switch');
      await user.click(switchElement);

      expect(handleChange).not.toHaveBeenCalled();
    });
  });

  describe('Sizes', () => {
    it('renders with small size', () => {
      renderSwitch({ size: 'small' });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeInTheDocument();
    });

    it('renders with medium size', () => {
      renderSwitch({ size: 'medium' });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeInTheDocument();
    });

    it('renders with large size', () => {
      renderSwitch({ size: 'large' });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeInTheDocument();
    });
  });

  describe('Label placement', () => {
    it('renders label at end by default', () => {
      renderSwitch({ label: 'Switch Label' });
      expect(screen.getByText('Switch Label')).toBeInTheDocument();
    });

    it('renders label at start', () => {
      renderSwitch({ label: 'Switch Label', labelPlacement: 'start' });
      expect(screen.getByText('Switch Label')).toBeInTheDocument();
    });

    it('renders label at top', () => {
      renderSwitch({ label: 'Switch Label', labelPlacement: 'top' });
      expect(screen.getByText('Switch Label')).toBeInTheDocument();
    });

    it('renders label at bottom', () => {
      renderSwitch({ label: 'Switch Label', labelPlacement: 'bottom' });
      expect(screen.getByText('Switch Label')).toBeInTheDocument();
    });
  });

  describe('Status and labels', () => {
    it('shows status text when showStatus is true', () => {
      renderSwitch({ showStatus: true, defaultChecked: true });
      expect(screen.getByText('On')).toBeInTheDocument();
    });

    it('shows custom status with statusFormatter', () => {
      renderSwitch({
        showStatus: true,
        defaultChecked: true,
        statusFormatter: (checked: boolean) => checked ? 'Enabled' : 'Disabled'
      });
      expect(screen.getByText('Enabled')).toBeInTheDocument();
    });

    it('shows checked and unchecked labels', () => {
      renderSwitch({
        checkedLabel: 'Yes',
        uncheckedLabel: 'No',
        defaultChecked: true
      });
      expect(screen.getByText('Yes')).toBeInTheDocument();
      expect(screen.getByText('No')).toBeInTheDocument();
    });
  });

  describe('Error state', () => {
    it('renders with error state', () => {
      renderSwitch({ error: true, helperText: 'Error message' });
      const switchElement = screen.getByRole('switch');
      expect(switchElement).toBeInTheDocument();
    });

    it('renders error message', () => {
      renderSwitch({ error: true, helperText: 'Error message' });
      expect(screen.getByText('Error message')).toBeInTheDocument();
    });
  });

  describe('Test IDs', () => {
    it('renders with custom data-testid', () => {
      renderSwitch({ 'data-testid': 'custom-switch' });
      expect(screen.getByTestId('custom-switch')).toBeInTheDocument();
    });

    it('generates data-testid from name prop', () => {
      renderSwitch({ name: 'toggle' });
      expect(screen.getByTestId('switch-toggle')).toBeInTheDocument();
    });
  });

  afterEach(() => {
    cleanup();
  });
});

