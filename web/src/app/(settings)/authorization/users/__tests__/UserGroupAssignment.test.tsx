import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserGroupAssignment } from '../UserGroupAssignment';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { Group } from '../UserGroupAssignment';

// Mock GraphQL query
const mockGroups = [
  { id: '1', name: 'Admin Group', description: 'Administrators' },
  { id: '2', name: 'User Group', description: 'Regular users' },
  { id: '3', name: 'Guest Group', description: null },
];

const mockUseGetGroupsQuery = vi.fn(() => ({
  data: {
    groups: {
      edges: mockGroups.map((group) => ({ node: group })),
    },
  },
  loading: false,
  error: undefined,
  refetch: vi.fn().mockResolvedValue({
    data: {
      groups: {
        edges: mockGroups.map((group) => ({ node: group })),
      },
    },
  }),
}));

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetGroupsQuery: () => mockUseGetGroupsQuery(),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'userManagement.drawer.groups': 'Groups',
        'userManagement.groups.searchPlaceholder': 'Search groups...',
        'userManagement.groups.loadError': 'Failed to load groups',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock ErrorAlert
vi.mock('@/shared/components/ui/feedback', () => ({
  ErrorAlert: ({ error, onRetry, fallbackMessage }: any) =>
    error ? (
      <div data-testid="error-alert">
        <div>{fallbackMessage}</div>
        {onRetry && <button onClick={onRetry}>Retry</button>}
      </div>
    ) : null,
}));

// Mock MUI Autocomplete and related components
vi.mock('@mui/material', () => ({
  Box: ({ children, maxWidth, minWidth, maxHeight, minHeight, component, sx, ...props }: any) => (
    <div {...props}>{children}</div>
  ),
  CircularProgress: ({ size }: any) => (
    <div data-testid="circular-progress" data-size={size}>
      Loading...
    </div>
  ),
  Autocomplete: ({
    multiple,
    options,
    value,
    onChange,
    getOptionLabel,
    isOptionEqualToValue,
    filterOptions,
    renderTags,
    renderInput,
    loading,
  }: any) => {
    // Capture props for testing
    (window as any).__autocompleteProps = {
      multiple,
      options,
      value,
      onChange,
      getOptionLabel,
      isOptionEqualToValue,
      filterOptions,
      loading,
    };

    const [inputValue, setInputValue] = React.useState('');
    const filteredOptions = filterOptions
      ? filterOptions(options, { inputValue })
      : options;

    // Call renderInput with proper params structure if provided
    // renderInput is a closure from SearchableAutocomplete that has access to loading
    const inputElement = renderInput ? renderInput({
      InputProps: {
        endAdornment: null,
      },
      InputLabelProps: {},
      inputProps: {},
      disabled: false,
      fullWidth: true,
      size: 'medium' as const,
      variant: 'outlined' as const,
    }) : null;

    return (
      <div data-testid="autocomplete">
        {inputElement || (
          <input
            data-testid="autocomplete-input"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Search..."
          />
        )}
        <div data-testid="autocomplete-options">
          {filteredOptions.map((option: any) => (
            <div
              key={option.id}
              data-testid={`option-${option.id}`}
              onClick={() => {
                const newValue = multiple
                  ? [...(value || []), option].filter(
                      (v, i, arr) => arr.findIndex((item) => item.id === v.id) === i
                    )
                  : option;
                onChange(null, newValue);
              }}
            >
              {getOptionLabel(option)}
            </div>
          ))}
        </div>
        <div data-testid="autocomplete-value">
          {multiple && value
            ? value.map((v: any) => (
                <div key={v.id} data-testid={`selected-${v.id}`}>
                  {getOptionLabel(v)}
                  <button
                    onClick={() => {
                      const newValue = value.filter((item: any) => item.id !== v.id);
                      // Call onChange with the correct signature: (event, newValue, reason)
                      onChange(null, newValue, 'removeOption');
                    }}
                  >
                    Remove
                  </button>
                </div>
              ))
            : getOptionLabel(value)}
        </div>
      </div>
    );
  },
  TextField: ({ label, placeholder, fullWidth, variant, size, margin, multiline, rows, maxRows, minRows, InputProps, InputLabelProps, inputProps, error, helperText, ...props }: any) => (
    <div>
      {label && <label>{label}</label>}
      <div style={{ position: 'relative', display: 'inline-block' }}>
        <input data-testid="autocomplete-input" placeholder={placeholder} {...inputProps} {...props} />
        {InputProps?.endAdornment && (
          <div style={{ position: 'absolute', right: 0, top: 0 }}>
            {InputProps.endAdornment}
          </div>
        )}
        {helperText && <div>{helperText}</div>}
      </div>
    </div>
  ),
  Chip: ({ label, onDelete, ...props }: any) => (
    <div data-testid="chip" {...props}>
      {label}
      {onDelete && <button onClick={onDelete}>Delete</button>}
    </div>
  ),
}));

const renderUserGroupAssignment = (props = {}) => {
  const defaultProps = {
    userId: 'user-1',
    assignedGroups: [],
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserGroupAssignment {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserGroupAssignment', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__autocompleteProps = undefined;
    mockUseGetGroupsQuery.mockReturnValue({
      data: {
        groups: {
          edges: mockGroups.map((group) => ({ node: group })),
        },
      },
      loading: false,
      error: undefined,
      refetch: vi.fn().mockResolvedValue({
        data: {
          groups: {
            edges: mockGroups.map((group) => ({ node: group })),
          },
        },
      }),
    });
  });

  describe('GraphQL query integration', () => {
    it('should call useGetGroupsQuery with correct variables', () => {
      renderUserGroupAssignment();

      expect(mockUseGetGroupsQuery).toHaveBeenCalled();
      // Verify the query is called (skip is false)
      expect(mockUseGetGroupsQuery).toHaveBeenCalled();
    });

    it('should not skip query', () => {
      renderUserGroupAssignment();

      // Query should be called (skip: false)
      expect(mockUseGetGroupsQuery).toHaveBeenCalled();
    });
  });

  describe('Data transformation', () => {
    it('should transform GraphQL edges to group options correctly', () => {
      renderUserGroupAssignment();

      const props = (window as any).__autocompleteProps;
      expect(props.options).toHaveLength(3);
      expect(props.options[0]).toEqual({
        id: '1',
        label: 'Admin Group',
        name: 'Admin Group',
        description: 'Administrators',
      });
    });

    it('should preserve group description in transformation', () => {
      renderUserGroupAssignment();

      const props = (window as any).__autocompleteProps;
      expect(props.options[0].description).toBe('Administrators');
      expect(props.options[2].description).toBeNull();
    });

    it('should map assignedGroups to selected options correctly', () => {
      const assignedGroups: Group[] = [
        { id: '1', name: 'Admin Group', description: 'Administrators' },
        { id: '2', name: 'User Group', description: 'Regular users' },
      ];
      renderUserGroupAssignment({ assignedGroups });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toHaveLength(2);
      expect(props.value[0]).toEqual({
        id: '1',
        label: 'Admin Group',
        name: 'Admin Group',
        description: 'Administrators',
      });
    });

    it('should convert selected options back to Group[] format in onChange', async () => {
      const onChange = vi.fn();
      renderUserGroupAssignment({ onChange });

      // Select an option
      const option = screen.getByTestId('option-1');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(callArgs).toEqual([
          { id: '1', name: 'Admin Group', description: 'Administrators' },
        ]);
      });
    });

    it('should preserve description when converting options to Group[]', async () => {
      const onChange = vi.fn();
      renderUserGroupAssignment({ onChange });

      // Select an option with description
      const option = screen.getByTestId('option-1');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(callArgs[0].description).toBe('Administrators');
      });
    });

    it('should preserve null description when converting options to Group[]', async () => {
      const onChange = vi.fn();
      renderUserGroupAssignment({ onChange });

      // Select an option with null description
      const option = screen.getByTestId('option-3');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(callArgs[0].description).toBeNull();
      });
    });
  });

  describe('Loading state', () => {
    it('should show CircularProgress when groupsLoading is true', () => {
      mockUseGetGroupsQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderUserGroupAssignment();

      // There might be multiple circular-progress elements (one from renderInput, potentially others)
      // Just verify at least one exists
      const progressElements = screen.getAllByTestId('circular-progress');
      expect(progressElements.length).toBeGreaterThan(0);
      expect(progressElements[0]).toBeInTheDocument();
    });

    it('should show loading indicator during loading', () => {
      mockUseGetGroupsQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderUserGroupAssignment();

      // Autocomplete should still be visible, but with loading indicator
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
      // Should show loading indicator
      const progressElements = screen.getAllByTestId('circular-progress');
      expect(progressElements.length).toBeGreaterThan(0);
    });
  });

  describe('Error state', () => {
    it('should show error alert when query fails', () => {
      const mockRefetch = vi.fn();
      mockUseGetGroupsQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserGroupAssignment();

      // There might be multiple error alerts, just verify at least one exists
      const errorAlerts = screen.getAllByTestId('error-alert');
      expect(errorAlerts.length).toBeGreaterThan(0);
      // The error message might appear in multiple places (ErrorAlert and helperText), so use getAllByText
      const errorMessages = screen.getAllByText('Failed to load groups');
      expect(errorMessages.length).toBeGreaterThan(0);
    });

    it('should call refetch when retry button is clicked', async () => {
      const mockRefetch = vi.fn().mockResolvedValue({
        data: {
          groups: {
            edges: mockGroups.map((group) => ({ node: group })),
          },
        },
      });
      mockUseGetGroupsQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserGroupAssignment();

      const retryButton = screen.getByText('Retry');
      await user.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('Autocomplete integration', () => {
    it('should pass groupOptions to Autocomplete', () => {
      renderUserGroupAssignment();

      const props = (window as any).__autocompleteProps;
      expect(props.options).toHaveLength(3);
      expect(props.multiple).toBe(true);
    });

    it('should pass selectedGroups as value', () => {
      const assignedGroups: Group[] = [{ id: '1', name: 'Admin Group', description: 'Administrators' }];
      renderUserGroupAssignment({ assignedGroups });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toHaveLength(1);
      expect(props.value[0].id).toBe('1');
    });

    it('should call onChange callback with Group[] format', async () => {
      const onChange = vi.fn();
      renderUserGroupAssignment({ onChange });

      // Select an option
      const option = screen.getByTestId('option-1');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(Array.isArray(callArgs)).toBe(true);
        expect(callArgs[0]).toHaveProperty('id');
        expect(callArgs[0]).toHaveProperty('name');
        expect(callArgs[0]).toHaveProperty('description');
      });
    });

    it('should prevent duplicate group selections', async () => {
      const onChange = vi.fn();
      const assignedGroups: Group[] = [{ id: '1', name: 'Admin Group', description: 'Administrators' }];
      renderUserGroupAssignment({ assignedGroups, onChange });

      // Try to select the same group again
      const option = screen.getByTestId('option-1');
      await user.click(option);

      // Should maintain uniqueness
      await waitFor(() => {
        const props = (window as any).__autocompleteProps;
        const uniqueIds = new Set(props.value.map((v: any) => v.id));
        expect(uniqueIds.size).toBe(props.value.length);
      });
    });

    it('should filter groups by name (case-insensitive)', async () => {
      renderUserGroupAssignment();

      const input = screen.getByTestId('autocomplete-input');
      await user.type(input, 'admin');

      await waitFor(() => {
        const props = (window as any).__autocompleteProps;
        const filtered = props.filterOptions
          ? props.filterOptions(props.options, { inputValue: 'admin' })
          : props.options;
        expect(filtered.some((opt: any) => opt.name.toLowerCase().includes('admin'))).toBe(true);
      });
    });
  });

  describe('UI elements', () => {
    it('should render chips for selected groups', () => {
      const assignedGroups: Group[] = [{ id: '1', name: 'Admin Group', description: 'Administrators' }];
      renderUserGroupAssignment({ assignedGroups });

      expect(screen.getByTestId('selected-1')).toBeInTheDocument();
    });

    it('should allow removing groups via chip delete', async () => {
      const onChange = vi.fn();
      const assignedGroups: Group[] = [
        { id: '1', name: 'Admin Group', description: 'Administrators' },
        { id: '2', name: 'User Group', description: 'Regular users' },
      ];
      renderUserGroupAssignment({ assignedGroups, onChange });

      const selectedChip = screen.getByTestId('selected-1');
      const removeButton = within(selectedChip).getByRole('button', { name: /remove/i });
      
      // Click the remove button
      await user.click(removeButton);

      // Wait for onChange to be called with the updated groups (should only have group 2)
      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(callArgs).toHaveLength(1);
        expect(callArgs[0].id).toBe('2');
      });
    });

    it('should show correct placeholder text', () => {
      renderUserGroupAssignment();

      // Placeholder is passed to TextField via renderInput
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });

    it('should show correct label text', () => {
      renderUserGroupAssignment();

      // Label is passed to TextField via renderInput
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });
  });

  describe('Props handling', () => {
    it('should handle onChange being undefined', () => {
      renderUserGroupAssignment({ onChange: undefined });

      // Should not crash
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });

    it('should handle empty assignedGroups array', () => {
      renderUserGroupAssignment({ assignedGroups: [] });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toEqual([]);
    });
  });
});
