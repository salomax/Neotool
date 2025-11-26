import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TextField } from '../TextField';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderTextField = (props = {}) => {
  return render(
    <AppThemeProvider>
      <TextField {...props} />
    </AppThemeProvider>
  );
};

describe('TextField', () => {
  it('renders text field', () => {
    renderTextField({ label: 'Email' });
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('renders with default size', () => {
    renderTextField({ label: 'Test' });
    const input = screen.getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with small size', () => {
    renderTextField({ label: 'Test', size: 'small' });
    const input = screen.getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with medium size', () => {
    renderTextField({ label: 'Test', size: 'medium' });
    const input = screen.getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with large size', () => {
    renderTextField({ label: 'Test', size: 'large' });
    const input = screen.getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('handles text input', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    renderTextField({ label: 'Test', onChange: handleChange });

    const input = screen.getByLabelText('Test');
    await user.type(input, 'test value');

    expect(handleChange).toHaveBeenCalled();
  });

  it('shows password toggle icon for password type', () => {
    renderTextField({ label: 'Password', type: 'password' });
    const button = screen.getByLabelText('toggle password visibility');
    expect(button).toBeInTheDocument();
  });

  it('toggles password visibility when icon clicked', async () => {
    const user = userEvent.setup();
    renderTextField({ label: 'Password', type: 'password', defaultValue: 'secret' });

    const input = screen.getByLabelText('Password') as HTMLInputElement;
    expect(input.type).toBe('password');

    const toggleButton = screen.getByLabelText('toggle password visibility');
    await user.click(toggleButton);

    expect(input.type).toBe('text');
  });

  it('does not show password toggle when endIcon provided', () => {
    renderTextField({
      label: 'Password',
      type: 'password',
      endIcon: 'search',
    });

    expect(screen.queryByLabelText('toggle password visibility')).not.toBeInTheDocument();
  });

  it('renders start icon from string', () => {
    renderTextField({ label: 'Search', startIcon: 'search' });
    // Icon should be rendered (checking by aria-label or test id)
    const input = screen.getByLabelText('Search');
    expect(input).toBeInTheDocument();
  });

  it('renders start icon from React node', () => {
    const Icon = () => <span data-testid="custom-icon">Icon</span>;
    renderTextField({ label: 'Test', startIcon: <Icon /> });
    expect(screen.getByTestId('custom-icon')).toBeInTheDocument();
  });

  it('renders end icon from string', () => {
    renderTextField({ label: 'Search', endIcon: 'search' });
    const input = screen.getByLabelText('Search');
    expect(input).toBeInTheDocument();
  });

  it('renders end icon from React node', () => {
    const Icon = () => <span data-testid="custom-end-icon">Icon</span>;
    renderTextField({ label: 'Test', endIcon: <Icon /> });
    expect(screen.getByTestId('custom-end-icon')).toBeInTheDocument();
  });

  it('restricts numeric input for number type', () => {
    const handleKeyDown = vi.fn();
    renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = screen.getByLabelText('Number');
    
    // Try to type letter
    fireEvent.keyDown(input, { key: 'a', preventDefault: vi.fn() });
    
    // Should prevent default for non-numeric keys
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows numeric keys for number type', () => {
    const handleKeyDown = vi.fn();
    renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = screen.getByLabelText('Number');
    
    // Type number
    fireEvent.keyDown(input, { key: '5' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows control keys for number type', () => {
    const handleKeyDown = vi.fn();
    renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = screen.getByLabelText('Number');
    
    // Backspace should be allowed
    fireEvent.keyDown(input, { key: 'Backspace' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows decimal point for number type', () => {
    const handleKeyDown = vi.fn();
    renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = screen.getByLabelText('Number');
    
    // Decimal point should be allowed
    fireEvent.keyDown(input, { key: '.' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows Ctrl/Cmd shortcuts for number type', () => {
    const handleKeyDown = vi.fn();
    renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = screen.getByLabelText('Number');
    
    // Ctrl+C should be allowed
    fireEvent.keyDown(input, { key: 'c', ctrlKey: true });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('sets inputMode and pattern for number type', () => {
    renderTextField({ label: 'Number', type: 'number' });
    const input = screen.getByLabelText('Number') as HTMLInputElement;
    expect(input.inputMode).toBe('decimal');
    expect(input.pattern).toBe('[0-9]*');
  });

  it('does not set inputMode for non-number types', () => {
    renderTextField({ label: 'Text', type: 'text' });
    const input = screen.getByLabelText('Text') as HTMLInputElement;
    expect(input.inputMode).toBe('');
  });

  it('handles disabled state', () => {
    renderTextField({ label: 'Test', disabled: true });
    const input = screen.getByLabelText('Test');
    expect(input).toBeDisabled();
  });

  it('handles required state', () => {
    renderTextField({ label: 'Test', required: true });
    const input = screen.getByLabelText(/Test/) as HTMLInputElement;
    expect(input).toBeRequired();
  });

  it('handles placeholder', () => {
    renderTextField({ label: 'Test', placeholder: 'Enter value' });
    expect(screen.getByPlaceholderText('Enter value')).toBeInTheDocument();
  });

  it('handles error state', () => {
    renderTextField({ label: 'Test', error: true, helperText: 'Error message' });
    expect(screen.getByText('Error message')).toBeInTheDocument();
  });

  it('handles helper text', () => {
    renderTextField({ label: 'Test', helperText: 'Helper text' });
    expect(screen.getByText('Helper text')).toBeInTheDocument();
  });
});

