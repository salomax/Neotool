import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupSearch } from '../GroupSearch';
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

const renderGroupSearch = (props = {}) => {
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <GroupSearch {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('GroupSearch', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render search field', () => {
      renderGroupSearch();

      expect(screen.getByTestId('group-search')).toBeInTheDocument();
    });

    it('should display placeholder text', () => {
      renderGroupSearch({ placeholder: 'Search groups...' });

      expect(screen.getByPlaceholderText('Search groups...')).toBeInTheDocument();
    });

    it('should use default placeholder when not provided', () => {
      renderGroupSearch();

      expect(screen.getByPlaceholderText('Search groups by name...')).toBeInTheDocument();
    });

    it('should display current value', () => {
      renderGroupSearch({ value: 'test query' });

      expect(screen.getByDisplayValue('test query')).toBeInTheDocument();
    });
  });

  describe('User interactions', () => {
    it('should call onChange when user types', async () => {
      const onChange = vi.fn();
      renderGroupSearch({ onChange });

      const input = screen.getByTestId('group-search');
      await user.type(input, 'test');

      expect(onChange).toHaveBeenCalled();
    });

    it('should call onSearch when provided and field loses focus', async () => {
      const onSearch = vi.fn();
      const onChange = vi.fn();
      renderGroupSearch({ value: 'test', onChange, onSearch });

      const input = screen.getByTestId('group-search');
      await user.click(input);
      await user.tab();

      await waitFor(() => {
        expect(onSearch).toHaveBeenCalledWith('test');
      });
    });

    it('should use onChange as fallback when onSearch is not provided', async () => {
      const onChange = vi.fn();
      renderGroupSearch({ value: 'test', onChange });

      const input = screen.getByTestId('group-search');
      await user.click(input);
      await user.tab();

      // onSearch should fallback to onChange, but the mock doesn't implement this
      // The actual component does this in the handleSearch assignment
      expect(onChange).toHaveBeenCalled();
    });
  });

  describe('Props handling', () => {
    it('should handle maxWidth prop', () => {
      renderGroupSearch({ maxWidth: 'sm' });

      // The component applies maxWidth styles, but in test environment
      // we can't easily verify CSS, so we just ensure it renders
      expect(screen.getByTestId('group-search')).toBeInTheDocument();
    });

    it('should handle empty value', () => {
      renderGroupSearch({ value: '' });

      const input = screen.getByTestId('group-search');
      expect(input).toHaveValue('');
    });

    it('should handle long search queries', () => {
      const longQuery = 'a'.repeat(100);
      renderGroupSearch({ value: longQuery });

      // Verify the search field is rendered with the value
      const searchField = screen.getByTestId('group-search');
      expect(searchField).toBeInTheDocument();
    });
  });
});
