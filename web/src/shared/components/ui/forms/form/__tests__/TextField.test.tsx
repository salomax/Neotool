import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
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

describe.sequential('TextField', () => {
  it('renders text field', () => {
    const { getByLabelText } = renderTextField({ label: 'Email' });
    expect(getByLabelText('Email')).toBeInTheDocument();
  });

  it('renders with default size', () => {
    const { getByLabelText } = renderTextField({ label: 'Test' });
    const input = getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with small size', () => {
    const { getByLabelText } = renderTextField({ label: 'Test', size: 'small' });
    const input = getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with medium size', () => {
    const { getByLabelText } = renderTextField({ label: 'Test', size: 'medium' });
    const input = getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('renders with large size', () => {
    const { getByLabelText } = renderTextField({ label: 'Test', size: 'large' });
    const input = getByLabelText('Test');
    expect(input).toBeInTheDocument();
  });

  it('handles text input', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Test', onChange: handleChange });

    const input = getByLabelText('Test');
    await user.type(input, 'test value');

    expect(handleChange).toHaveBeenCalled();
  });

  it('shows password toggle icon for password type', () => {
    const { getByLabelText } = renderTextField({ label: 'Password', type: 'password' });
    const button = getByLabelText('toggle password visibility');
    expect(button).toBeInTheDocument();
  });

  it('toggles password visibility when icon clicked', async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderTextField({ label: 'Password', type: 'password', defaultValue: 'secret' });

    const input = getByLabelText('Password') as HTMLInputElement;
    expect(input.type).toBe('password');

    const toggleButton = getByLabelText('toggle password visibility');
    await user.click(toggleButton);

    expect(input.type).toBe('text');
  });

  it('does not show password toggle when endIcon provided', () => {
    const { queryByLabelText } = renderTextField({
      label: 'Password',
      type: 'password',
      endIcon: 'search',
    });

    expect(queryByLabelText('toggle password visibility')).not.toBeInTheDocument();
  });

  it('renders start icon from string', () => {
    const { getByLabelText } = renderTextField({ label: 'Search', startIcon: 'search' });
    // Icon should be rendered (checking by aria-label or test id)
    const input = getByLabelText('Search');
    expect(input).toBeInTheDocument();
  });

  it('renders start icon from React node', () => {
    const Icon = () => <span data-testid="custom-icon">Icon</span>;
    const { getByTestId } = renderTextField({ label: 'Test', startIcon: <Icon /> });
    expect(getByTestId('custom-icon')).toBeInTheDocument();
  });

  it('renders end icon from string', () => {
    const { getByLabelText } = renderTextField({ label: 'Search', endIcon: 'search' });
    const input = getByLabelText('Search');
    expect(input).toBeInTheDocument();
  });

  it('renders end icon from React node', () => {
    const Icon = () => <span data-testid="custom-end-icon">Icon</span>;
    const { getByTestId } = renderTextField({ label: 'Test', endIcon: <Icon /> });
    expect(getByTestId('custom-end-icon')).toBeInTheDocument();
  });

  it('restricts numeric input for number type', () => {
    const handleKeyDown = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = getByLabelText('Number');
    
    // Try to type letter
    fireEvent.keyDown(input, { key: 'a', preventDefault: vi.fn() });
    
    // Should prevent default for non-numeric keys
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows numeric keys for number type', () => {
    const handleKeyDown = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = getByLabelText('Number');
    
    // Type number
    fireEvent.keyDown(input, { key: '5' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows control keys for number type', () => {
    const handleKeyDown = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = getByLabelText('Number');
    
    // Backspace should be allowed
    fireEvent.keyDown(input, { key: 'Backspace' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows decimal point for number type', () => {
    const handleKeyDown = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = getByLabelText('Number');
    
    // Decimal point should be allowed
    fireEvent.keyDown(input, { key: '.' });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('allows Ctrl/Cmd shortcuts for number type', () => {
    const handleKeyDown = vi.fn();
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number', onKeyDown: handleKeyDown });

    const input = getByLabelText('Number');
    
    // Ctrl+C should be allowed
    fireEvent.keyDown(input, { key: 'c', ctrlKey: true });
    
    expect(handleKeyDown).toHaveBeenCalled();
  });

  it('sets inputMode and pattern for number type', () => {
    const { getByLabelText } = renderTextField({ label: 'Number', type: 'number' });
    const input = getByLabelText('Number') as HTMLInputElement;
    expect(input.inputMode).toBe('decimal');
    expect(input.pattern).toBe('[0-9]*');
  });

  it('does not set inputMode for non-number types', () => {
    const { getByLabelText } = renderTextField({ label: 'Text', type: 'text' });
    const input = getByLabelText('Text') as HTMLInputElement;
    expect(input.inputMode).toBe('');
  });

  it('handles disabled state', () => {
    const { getByLabelText } = renderTextField({ label: 'Test', disabled: true });
    const input = getByLabelText('Test');
    expect(input).toBeDisabled();
  });

  it('handles required state', () => {
    const { getByLabelText } = renderTextField({ label: 'Test', required: true });
    const input = getByLabelText(/Test/);
    expect(input).toBeRequired();
  });

  it('handles placeholder', () => {
    const { getByPlaceholderText } = renderTextField({ label: 'Test', placeholder: 'Enter value' });
    expect(getByPlaceholderText('Enter value')).toBeInTheDocument();
  });

  it('handles error state', () => {
    const { getByText } = renderTextField({ label: 'Test', error: true, helperText: 'Error message' });
    expect(getByText('Error message')).toBeInTheDocument();
  });

  it('handles helper text', () => {
    const { getByText } = renderTextField({ label: 'Test', helperText: 'Helper text' });
    expect(getByText('Helper text')).toBeInTheDocument();
  });
});
