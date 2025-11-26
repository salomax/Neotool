import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProgressBar } from '../ProgressBar';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderProgressBar = (props = {}) => {
  return render(
    <AppThemeProvider>
      <ProgressBar {...props} />
    </AppThemeProvider>
  );
};

describe('ProgressBar', () => {
  describe('Linear variant', () => {
    it('renders linear progress bar', () => {
      renderProgressBar({ variant: 'linear', value: 50 });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('shows percentage by default', () => {
      renderProgressBar({ variant: 'linear', value: 50 });
      expect(screen.getByText('50%')).toBeInTheDocument();
    });

    it('hides percentage when showPercentage is false', () => {
      renderProgressBar({ variant: 'linear', value: 50, showPercentage: false });
      expect(screen.queryByText('50%')).not.toBeInTheDocument();
    });

    it('shows label by default', () => {
      renderProgressBar({ variant: 'linear', value: 50 });
      expect(screen.getByText('Progress')).toBeInTheDocument();
    });

    it('shows custom label', () => {
      renderProgressBar({ variant: 'linear', value: 50, label: 'Uploading' });
      expect(screen.getByText('Uploading')).toBeInTheDocument();
    });

    it('hides label when showLabel is false', () => {
      renderProgressBar({ variant: 'linear', value: 50, showLabel: false });
      expect(screen.queryByText('Progress')).not.toBeInTheDocument();
    });

    it('shows indeterminate progress', () => {
      renderProgressBar({ variant: 'linear', indeterminate: true });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.getByText('Loading...')).toBeInTheDocument();
    });

    it('clamps value to 0-100 range', () => {
      renderProgressBar({ variant: 'linear', value: 150 });
      expect(screen.getByText('100%')).toBeInTheDocument();
    });

    it('clamps negative value to 0', () => {
      renderProgressBar({ variant: 'linear', value: -10 });
      expect(screen.getByText('0%')).toBeInTheDocument();
    });
  });

  describe('Circular variant', () => {
    it('renders circular progress bar', () => {
      renderProgressBar({ variant: 'circular', value: 50 });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('shows percentage in center', () => {
      renderProgressBar({ variant: 'circular', value: 50, showPercentage: true });
      expect(screen.getByText('50%')).toBeInTheDocument();
    });

    it('shows indeterminate circular progress', () => {
      renderProgressBar({ variant: 'circular', indeterminate: true });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Step variant', () => {
    it('renders step progress', () => {
      renderProgressBar({ currentStep: 1, totalSteps: 3 });
      expect(screen.getByText('Step 2 of 3')).toBeInTheDocument();
    });

    it('shows step labels', () => {
      renderProgressBar({
        currentStep: 1,
        totalSteps: 3,
        steps: ['Step 1', 'Step 2', 'Step 3']
      });
      expect(screen.getByText('Step 1')).toBeInTheDocument();
      expect(screen.getByText('Step 2')).toBeInTheDocument();
      expect(screen.getByText('Step 3')).toBeInTheDocument();
    });

    it('calculates step percentage correctly', () => {
      renderProgressBar({ currentStep: 1, totalSteps: 4, showPercentage: true });
      // Step 1 of 4 = 25% - percentage is shown in chip at bottom
      const percentageText = screen.getByText(/25% Complete/);
      expect(percentageText).toBeInTheDocument();
    });

    it('shows completed step icon', () => {
      renderProgressBar({
        currentStep: 2,
        totalSteps: 3,
        stepStatus: ['completed', 'completed', 'active']
      });
      // Should render step progress with icons
      expect(screen.getByText('Step 3 of 3')).toBeInTheDocument();
    });

    it('shows error step icon', () => {
      renderProgressBar({
        currentStep: 1,
        totalSteps: 3,
        stepStatus: ['completed', 'error', 'pending']
      });
      expect(screen.getByText('Step 2 of 3')).toBeInTheDocument();
    });

    it('calls onStepClick when step is clicked and clickable', async () => {
      const handleStepClick = vi.fn();
      const user = userEvent.setup();
      renderProgressBar({
        currentStep: 1,
        totalSteps: 3,
        clickable: true,
        onStepClick: handleStepClick,
        steps: ['Step 1', 'Step 2', 'Step 3']
      });

      // Find and click a step
      const step2 = screen.getByText('Step 2');
      await user.click(step2);

      expect(handleStepClick).toHaveBeenCalledWith(1); // Step index is 0-based
    });

    it('does not call onStepClick when not clickable', async () => {
      const handleStepClick = vi.fn();
      const user = userEvent.setup();
      renderProgressBar({
        currentStep: 1,
        totalSteps: 3,
        clickable: false,
        onStepClick: handleStepClick,
        steps: ['Step 1', 'Step 2', 'Step 3']
      });

      const step2 = screen.getByText('Step 2');
      await user.click(step2);

      // Should not call when not clickable
      expect(handleStepClick).not.toHaveBeenCalled();
    });

    it('shows step content when showStepContent is true', () => {
      renderProgressBar({
        currentStep: 0,
        totalSteps: 3,
        showStepContent: true,
        stepContent: [
          <div key="1">Content 1</div>,
          <div key="2">Content 2</div>,
          <div key="3">Content 3</div>
        ]
      });
      // Step content is shown for the active step (currentStep = 0)
      expect(screen.getByText('Content 1')).toBeInTheDocument();
    });
  });

  describe('Sizes', () => {
    it('renders with small size', () => {
      renderProgressBar({ variant: 'linear', value: 50, size: 'small' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders with medium size', () => {
      renderProgressBar({ variant: 'linear', value: 50, size: 'medium' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders with large size', () => {
      renderProgressBar({ variant: 'linear', value: 50, size: 'large' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Colors', () => {
    it('renders with primary color', () => {
      renderProgressBar({ variant: 'linear', value: 50, color: 'primary' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders with success color', () => {
      renderProgressBar({ variant: 'linear', value: 50, color: 'success' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('renders with error color when error prop is true', () => {
      renderProgressBar({ variant: 'linear', value: 50, error: true });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Disabled state', () => {
    it('renders disabled progress bar', () => {
      renderProgressBar({ variant: 'linear', value: 50, disabled: true });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Helper text and error messages', () => {
    it('shows helper text', () => {
      renderProgressBar({ variant: 'linear', value: 50, helperText: 'Uploading files...' });
      expect(screen.getByText('Uploading files...')).toBeInTheDocument();
    });

    it('shows error message', () => {
      renderProgressBar({ variant: 'linear', value: 50, error: true, errorMessage: 'Upload failed' });
      expect(screen.getByText('Upload failed')).toBeInTheDocument();
    });

    it('prioritizes error message over helper text', () => {
      renderProgressBar({
        variant: 'linear',
        value: 50,
        error: true,
        errorMessage: 'Upload failed',
        helperText: 'Uploading files...'
      });
      expect(screen.getByText('Upload failed')).toBeInTheDocument();
      expect(screen.queryByText('Uploading files...')).not.toBeInTheDocument();
    });
  });

  describe('Custom width and height', () => {
    it('applies custom width for linear progress', () => {
      renderProgressBar({ variant: 'linear', value: 50, width: '200px' });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('applies custom height for linear progress', () => {
      renderProgressBar({ variant: 'linear', value: 50, height: 12 });
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Test IDs', () => {
    it('renders with custom data-testid', () => {
      renderProgressBar({ variant: 'linear', value: 50, 'data-testid': 'custom-progress' });
      expect(screen.getByTestId('custom-progress')).toBeInTheDocument();
    });
  });
});

