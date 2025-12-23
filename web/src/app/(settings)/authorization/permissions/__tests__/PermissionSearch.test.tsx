import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PermissionSearch } from '../PermissionSearch';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock SearchField component
vi.mock('@/shared/components/ui/forms/SearchField', () => ({
  SearchField: ({ value, onChange, placeholder, 'data-testid': testId }: any) => (
    <input
      data-testid={testId}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  ),
}));

const renderPermissionSearch = (props = {}) => {
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <PermissionSearch {...defaultProps} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid duplicate renders across parallel threads
describe.sequential('PermissionSearch', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render search field', () => {
      renderPermissionSearch();

      expect(screen.getByTestId('permission-search')).toBeInTheDocument();
    });

    it('should display default placeholder text', () => {
      renderPermissionSearch();

      expect(screen.getByPlaceholderText('Search permissions by name...')).toBeInTheDocument();
    });

    it('should display custom placeholder when provided', () => {
      renderPermissionSearch({ placeholder: 'Custom placeholder...' });

      expect(screen.getByPlaceholderText('Custom placeholder...')).toBeInTheDocument();
    });

    it('should display current value', () => {
      renderPermissionSearch({ value: 'test query' });

      expect(screen.getByDisplayValue('test query')).toBeInTheDocument();
    });

    it('should render with empty value by default', () => {
      renderPermissionSearch();

      const input = screen.getByTestId('permission-search');
      expect(input).toHaveValue('');
    });
  });

  describe('User interactions', () => {
    it('should call onChange when user types', async () => {
      const onChange = vi.fn();
      renderPermissionSearch({ onChange });

      const input = screen.getByTestId('permission-search');
      await user.type(input, 'test');

      expect(onChange).toHaveBeenCalled();
    });

    it('should call onChange with correct value', async () => {
      const onChange = vi.fn();
      renderPermissionSearch({ onChange });

      const input = screen.getByTestId('permission-search');
      await user.type(input, 'permission');

      // onChange is called for each character typed (mock calls onChange with individual characters)
      expect(onChange).toHaveBeenCalled();
      expect(onChange).toHaveBeenCalledTimes(10); // 'permission' has 10 characters
      // Verify it was called with the last character value
      expect(onChange).toHaveBeenLastCalledWith('n');
    });

    it('should update value when onChange is called', async () => {
      const onChange = vi.fn();
      const { rerender } = renderPermissionSearch({ value: '', onChange });

      const input = screen.getByTestId('permission-search');
      await user.type(input, 'new');

      // Simulate parent component updating value
      rerender(
        <AppThemeProvider>
          <PermissionSearch value="new" onChange={onChange} />
        </AppThemeProvider>
      );

      expect(screen.getByDisplayValue('new')).toBeInTheDocument();
    });

    it('should handle clearing the search', async () => {
      const onChange = vi.fn();
      renderPermissionSearch({ value: 'existing query', onChange });

      const input = screen.getByTestId('permission-search') as HTMLInputElement;
      await user.clear(input);

      expect(onChange).toHaveBeenCalledWith('');
    });
  });

  describe('Props handling', () => {
    it('should handle empty value', () => {
      renderPermissionSearch({ value: '' });

      const input = screen.getByTestId('permission-search');
      expect(input).toHaveValue('');
    });

    it('should handle long search queries', () => {
      const longQuery = 'a'.repeat(100);
      renderPermissionSearch({ value: longQuery });

      expect(screen.getByDisplayValue(longQuery)).toBeInTheDocument();
    });

    it('should handle special characters in search query', () => {
      const specialQuery = 'test & query (with) special: chars';
      renderPermissionSearch({ value: specialQuery });

      expect(screen.getByDisplayValue(specialQuery)).toBeInTheDocument();
    });

    it('should handle numeric search queries', () => {
      renderPermissionSearch({ value: '123' });

      expect(screen.getByDisplayValue('123')).toBeInTheDocument();
    });

    it('should handle value changes from parent component', () => {
      const { rerender } = renderPermissionSearch({ value: 'initial' });

      expect(screen.getByDisplayValue('initial')).toBeInTheDocument();

      rerender(
        <AppThemeProvider>
          <PermissionSearch value="updated" onChange={vi.fn()} />
        </AppThemeProvider>
      );

      expect(screen.getByDisplayValue('updated')).toBeInTheDocument();
    });
  });

  describe('Integration with SearchField', () => {
    it('should pass correct props to SearchField', () => {
      const onChange = vi.fn();
      renderPermissionSearch({ value: 'test', onChange, placeholder: 'Custom' });

      const input = screen.getByTestId('permission-search');
      expect(input).toHaveValue('test');
      expect(input).toHaveAttribute('placeholder', 'Custom');
    });

    it('should use default placeholder when not provided', () => {
      renderPermissionSearch();

      const input = screen.getByTestId('permission-search');
      expect(input).toHaveAttribute('placeholder', 'Search permissions by name...');
    });
  });

  describe('Edge cases', () => {
    it('should handle undefined onChange gracefully', () => {
      // This shouldn't happen in practice, but we test for robustness
      renderPermissionSearch({ onChange: undefined as any });

      const input = screen.getByTestId('permission-search');
      expect(input).toBeInTheDocument();
    });

    it('should handle rapid typing', async () => {
      const onChange = vi.fn();
      renderPermissionSearch({ onChange });

      const input = screen.getByTestId('permission-search');
      
      // Simulate rapid typing
      await user.type(input, 'abc');

      expect(onChange).toHaveBeenCalled();
    });

    it('should handle whitespace-only queries', () => {
      renderPermissionSearch({ value: '   ' });

      const input = screen.getByTestId('permission-search') as HTMLInputElement;
      expect(input.value).toBe('   ');
    });
  });
});
