import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleSearch } from '../RoleSearch';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock SearchField component
vi.mock('@/shared/components/ui/forms/SearchField', () => ({
  SearchField: ({ value, onChange, onSearch, placeholder, 'data-testid': testId }: any) => (
    <input
      data-testid={testId}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={() => onSearch?.(value)}
      placeholder={placeholder}
    />
  ),
}));

const renderRoleSearch = (props = {}) => {
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <RoleSearch {...defaultProps} />
    </AppThemeProvider>
  );
};

describe.sequential('RoleSearch', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe('Rendering', () => {
    it('should render search field', () => {
      renderRoleSearch();

      expect(screen.getByTestId('role-search')).toBeInTheDocument();
    });

    it('should display placeholder text', () => {
      renderRoleSearch({ placeholder: 'Search roles...' });

      expect(screen.getByPlaceholderText('Search roles...')).toBeInTheDocument();
    });

    it('should use default placeholder when not provided', () => {
      renderRoleSearch();

      expect(screen.getByPlaceholderText('Search roles by name...')).toBeInTheDocument();
    });

    it('should display current value', () => {
      renderRoleSearch({ value: 'test query' });

      expect(screen.getByDisplayValue('test query')).toBeInTheDocument();
    });
  });

  describe('User interactions', () => {
    it('should call onChange when user types', async () => {
      const onChange = vi.fn();
      renderRoleSearch({ onChange });

      const input = screen.getByTestId('role-search');
      await user.type(input, 'test');

      expect(onChange).toHaveBeenCalled();
    });

    it('should call onSearch when provided and field loses focus', async () => {
      const onSearch = vi.fn();
      const onChange = vi.fn();
      renderRoleSearch({ value: 'test', onChange, onSearch });

      const input = screen.getByTestId('role-search');
      await user.click(input);
      await user.tab();

      await waitFor(() => {
        expect(onSearch).toHaveBeenCalledWith('test');
      });
    });

    it('should use onChange as fallback when onSearch is not provided', async () => {
      const onChange = vi.fn();
      renderRoleSearch({ value: 'test', onChange });

      const input = screen.getByTestId('role-search');
      await user.click(input);
      await user.tab();

      expect(onChange).toHaveBeenCalled();
    });
  });

  describe('Props handling', () => {
    it('should handle maxWidth prop', () => {
      renderRoleSearch({ maxWidth: 'sm' });

      expect(screen.getByTestId('role-search')).toBeInTheDocument();
    });

    it('should handle empty value', () => {
      renderRoleSearch({ value: '' });

      const input = screen.getByTestId('role-search');
      expect(input).toHaveValue('');
    });

    it('should handle long search queries', () => {
      const longQuery = 'a'.repeat(100);
      renderRoleSearch({ value: longQuery });

      expect(screen.getByDisplayValue(longQuery)).toBeInTheDocument();
    });
  });
});










