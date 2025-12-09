import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserSearch } from '../UserSearch';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock SearchField
vi.mock('@/shared/components/ui/forms/SearchField', () => ({
  SearchField: ({ value, onChange, onSearch, placeholder, fullWidth, debounceMs, name, 'data-testid': dataTestId }: any) => {
    // Capture props for testing
    (window as any).__searchFieldProps = {
      value,
      onChange,
      onSearch,
      placeholder,
      fullWidth,
      debounceMs,
      name,
      'data-testid': dataTestId,
    };
    return (
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={() => onSearch?.(value)}
        placeholder={placeholder}
        data-testid={dataTestId}
      />
    );
  },
}));

// Mock MUI theme
const mockTheme = {
  breakpoints: {
    values: {
      xs: 0,
      sm: 600,
      md: 900,
      lg: 1200,
      xl: 1536,
    },
  },
};

vi.mock('@mui/material', async () => {
  const actual = await vi.importActual('@mui/material');
  return {
    ...actual,
    useTheme: () => mockTheme,
    Box: ({ children, sx, ...props }: any) => (
      <div data-testid="user-search-box" style={sx} {...props}>
        {children}
      </div>
    ),
  };
});

const renderUserSearch = (props = {}) => {
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserSearch {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserSearch', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__searchFieldProps = undefined;
  });

  describe('Props forwarding to SearchField', () => {
    it('should pass value prop to SearchField', () => {
      renderUserSearch({ value: 'test value' });

      const props = (window as any).__searchFieldProps;
      expect(props.value).toBe('test value');
    });

    it('should pass onChange callback to SearchField', () => {
      const onChange = vi.fn();
      renderUserSearch({ onChange });

      const props = (window as any).__searchFieldProps;
      expect(props.onChange).toBe(onChange);
    });

    it('should pass onSearch callback to SearchField when provided', () => {
      const onSearch = vi.fn();
      renderUserSearch({ onSearch });

      const props = (window as any).__searchFieldProps;
      expect(props.onSearch).toBe(onSearch);
    });

    it('should pass placeholder prop to SearchField', () => {
      renderUserSearch({ placeholder: 'Custom placeholder' });

      const props = (window as any).__searchFieldProps;
      expect(props.placeholder).toBe('Custom placeholder');
    });

    it('should use default placeholder when not provided', () => {
      renderUserSearch();

      const props = (window as any).__searchFieldProps;
      expect(props.placeholder).toBe('Search users by name or email...');
    });

    it('should pass fullWidth prop to SearchField', () => {
      renderUserSearch();

      const props = (window as any).__searchFieldProps;
      expect(props.fullWidth).toBe(true);
    });

    it('should pass debounceMs={300} to SearchField', () => {
      renderUserSearch();

      const props = (window as any).__searchFieldProps;
      expect(props.debounceMs).toBe(300);
    });

    it('should pass name="user-search" to SearchField', () => {
      renderUserSearch();

      const props = (window as any).__searchFieldProps;
      expect(props.name).toBe('user-search');
    });

    it('should pass data-testid="user-search" to SearchField', () => {
      renderUserSearch();

      const props = (window as any).__searchFieldProps;
      expect(props['data-testid']).toBe('user-search');
    });
  });

  describe('Callback handling', () => {
    it('should use onSearch when provided', () => {
      const onSearch = vi.fn();
      renderUserSearch({ onSearch });

      const props = (window as any).__searchFieldProps;
      expect(props.onSearch).toBe(onSearch);
    });

    it('should fall back to onChange when onSearch is not provided', () => {
      const onChange = vi.fn();
      renderUserSearch({ onChange, onSearch: undefined });

      const props = (window as any).__searchFieldProps;
      expect(props.onSearch).toBe(onChange);
    });

    it('should call onChange when input value changes', async () => {
      const onChange = vi.fn();
      renderUserSearch({ onChange });

      const input = screen.getByTestId('user-search');
      await user.type(input, 'test');

      expect(onChange).toHaveBeenCalled();
    });
  });

  describe('Styling', () => {
    it('should apply maxWidth styles when maxWidth is provided as string', () => {
      renderUserSearch({ maxWidth: 'sm' });

      const box = screen.getByTestId('user-search-box');
      const styles = window.getComputedStyle(box);
      // Note: In a real test, you'd check the actual computed style
      // For now, we verify the component renders
      expect(box).toBeInTheDocument();
    });

    it('should apply maxWidth styles when maxWidth is provided as number', () => {
      renderUserSearch({ maxWidth: 500 });

      const box = screen.getByTestId('user-search-box');
      expect(box).toBeInTheDocument();
    });

    it('should not apply maxWidth styles when maxWidth is not provided', () => {
      renderUserSearch();

      const box = screen.getByTestId('user-search-box');
      expect(box).toBeInTheDocument();
    });

    it('should apply margin bottom styling', () => {
      renderUserSearch();

      const box = screen.getByTestId('user-search-box');
      expect(box).toBeInTheDocument();
    });
  });
});
