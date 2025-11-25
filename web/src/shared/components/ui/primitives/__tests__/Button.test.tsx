import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from '../Button';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderButton = (props = {}) => {
  return render(
    <AppThemeProvider>
      <Button {...props}>Test Button</Button>
    </AppThemeProvider>
  );
};

describe('Button', () => {
  it('renders button with children', () => {
    renderButton();
    expect(screen.getByText('Test Button')).toBeInTheDocument();
  });

  it('renders with default size', () => {
    renderButton();
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('renders with small size', () => {
    renderButton({ size: 'small' });
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('renders with large size', () => {
    renderButton({ size: 'large' });
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('shows loading state', () => {
    renderButton({ loading: true });
    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
  });

  it('shows loading text when provided', () => {
    renderButton({ loading: true, loadingText: 'Loading...' });
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('disables button when loading', () => {
    renderButton({ loading: true });
    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
  });

  it('disables button when disabled prop is true', () => {
    renderButton({ disabled: true });
    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
  });

  it('handles click events', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    renderButton({ onClick: handleClick });

    const button = screen.getByRole('button');
    await user.click(button);

    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('does not call onClick when disabled', () => {
    const handleClick = vi.fn();
    renderButton({ onClick: handleClick, disabled: true });

    const button = screen.getByRole('button');
    fireEvent.click(button);

    expect(handleClick).not.toHaveBeenCalled();
  });

  it('does not call onClick when loading', () => {
    const handleClick = vi.fn();
    renderButton({ onClick: handleClick, loading: true });

    const button = screen.getByRole('button');
    fireEvent.click(button);

    expect(handleClick).not.toHaveBeenCalled();
  });

  it('renders with custom data-testid', () => {
    renderButton({ 'data-testid': 'custom-button' });
    expect(screen.getByTestId('custom-button')).toBeInTheDocument();
  });

  it('generates data-testid from name prop', () => {
    renderButton({ name: 'submit' });
    expect(screen.getByTestId('button-submit')).toBeInTheDocument();
  });

  it('renders with variant', () => {
    renderButton({ variant: 'contained' });
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('renders with color prop', () => {
    renderButton({ color: 'primary' });
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('renders with startIcon', () => {
    const StartIcon = () => <span data-testid="start-icon">Icon</span>;
    renderButton({ startIcon: <StartIcon /> });
    expect(screen.getByTestId('start-icon')).toBeInTheDocument();
  });

  it('replaces startIcon with loading spinner when loading', () => {
    const StartIcon = () => <span data-testid="start-icon">Icon</span>;
    renderButton({ startIcon: <StartIcon />, loading: true });
    expect(screen.queryByTestId('start-icon')).not.toBeInTheDocument();
  });
});

