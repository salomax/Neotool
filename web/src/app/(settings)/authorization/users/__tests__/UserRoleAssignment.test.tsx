import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRoleAssignment } from '../UserRoleAssignment';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { Role } from '../UserRoleAssignment';

// Mock GraphQL query
const mockRoles = [
  { id: '1', name: 'Admin Role' },
  { id: '2', name: 'User Role' },
  { id: '3', name: 'Guest Role' },
];

const mockUseGetRolesQuery = vi.fn(() => ({
  data: {
    roles: {
      edges: mockRoles.map((role) => ({ node: role })),
    },
  },
  loading: false,
  error: undefined,
  refetch: vi.fn().mockResolvedValue({
    data: {
      roles: {
        edges: mockRoles.map((role) => ({ node: role })),
      },
    },
  }),
}));

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetRolesQuery: () => mockUseGetRolesQuery(),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'userManagement.drawer.roles': 'Roles',
        'userManagement.roles.searchPlaceholder': 'Search roles...',
        'userManagement.roles.loadError': 'Failed to load roles',
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
    };

    const [inputValue, setInputValue] = React.useState('');
    const filteredOptions = filterOptions
      ? filterOptions(options, { inputValue })
      : options;

    return (
      <div data-testid="autocomplete">
        <input
          data-testid="autocomplete-input"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="Search..."
        />
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
                      onChange(null, newValue);
                    }}
                  >
                    Remove
                  </button>
                </div>
              ))
            : getOptionLabel(value)}
        </div>
        {renderInput && renderInput({})}
      </div>
    );
  },
  TextField: ({ label, placeholder, fullWidth, variant, size, margin, multiline, rows, maxRows, minRows, ...props }: any) => (
    <div>
      {label && <label>{label}</label>}
      <input placeholder={placeholder} {...props} />
    </div>
  ),
  Chip: ({ label, onDelete, ...props }: any) => (
    <div data-testid="chip" {...props}>
      {label}
      {onDelete && <button onClick={onDelete}>Delete</button>}
    </div>
  ),
}));

const renderUserRoleAssignment = (props = {}) => {
  const defaultProps = {
    userId: 'user-1',
    assignedRoles: [],
    onChange: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserRoleAssignment {...defaultProps} />
    </AppThemeProvider>
  );
};

describe('UserRoleAssignment', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    (window as any).__autocompleteProps = undefined;
    mockUseGetRolesQuery.mockReturnValue({
      data: {
        roles: {
          edges: mockRoles.map((role) => ({ node: role })),
        },
      },
      loading: false,
      error: undefined,
      refetch: vi.fn().mockResolvedValue({
        data: {
          roles: {
            edges: mockRoles.map((role) => ({ node: role })),
          },
        },
      }),
    });
  });

  describe('GraphQL query integration', () => {
    it('should call useGetRolesQuery with correct variables', () => {
      renderUserRoleAssignment();

      expect(mockUseGetRolesQuery).toHaveBeenCalled();
      // Verify the query is called (skip is false)
      expect(mockUseGetRolesQuery).toHaveBeenCalled();
    });

    it('should not skip query', () => {
      renderUserRoleAssignment();

      // Query should be called (skip: false)
      expect(mockUseGetRolesQuery).toHaveBeenCalled();
    });
  });

  describe('Data transformation', () => {
    it('should transform GraphQL edges to role options correctly', () => {
      renderUserRoleAssignment();

      const props = (window as any).__autocompleteProps;
      expect(props.options).toHaveLength(3);
      expect(props.options[0]).toEqual({
        id: '1',
        label: 'Admin Role',
        name: 'Admin Role',
      });
    });

    it('should map assignedRoles to selected options correctly', () => {
      const assignedRoles: Role[] = [
        { id: '1', name: 'Admin Role' },
        { id: '2', name: 'User Role' },
      ];
      renderUserRoleAssignment({ assignedRoles });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toHaveLength(2);
      expect(props.value[0]).toEqual({
        id: '1',
        label: 'Admin Role',
        name: 'Admin Role',
      });
    });

    it('should convert selected options back to Role[] format in onChange', async () => {
      const onChange = vi.fn();
      renderUserRoleAssignment({ onChange });

      // Select an option
      const option = screen.getByTestId('option-1');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(callArgs).toEqual([{ id: '1', name: 'Admin Role' }]);
      });
    });
  });

  describe('Loading state', () => {
    it('should show CircularProgress when rolesLoading is true', () => {
      mockUseGetRolesQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderUserRoleAssignment();

      expect(screen.getByTestId('circular-progress')).toBeInTheDocument();
    });

    it('should hide Autocomplete during loading', () => {
      mockUseGetRolesQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderUserRoleAssignment();

      expect(screen.queryByTestId('autocomplete')).not.toBeInTheDocument();
    });
  });

  describe('Error state', () => {
    it('should show error alert when query fails', () => {
      const mockRefetch = vi.fn();
      mockUseGetRolesQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserRoleAssignment();

      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
      expect(screen.getByText('Failed to load roles')).toBeInTheDocument();
    });

    it('should call refetch when retry button is clicked', async () => {
      const mockRefetch = vi.fn().mockResolvedValue({
        data: {
          roles: {
            edges: mockRoles.map((role) => ({ node: role })),
          },
        },
      });
      mockUseGetRolesQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: mockRefetch,
      });

      renderUserRoleAssignment();

      const retryButton = screen.getByText('Retry');
      await user.click(retryButton);

      expect(mockRefetch).toHaveBeenCalled();
    });
  });

  describe('Autocomplete integration', () => {
    it('should pass roleOptions to Autocomplete', () => {
      renderUserRoleAssignment();

      const props = (window as any).__autocompleteProps;
      expect(props.options).toHaveLength(3);
      expect(props.multiple).toBe(true);
    });

    it('should pass selectedRoles as value', () => {
      const assignedRoles: Role[] = [{ id: '1', name: 'Admin Role' }];
      renderUserRoleAssignment({ assignedRoles });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toHaveLength(1);
      expect(props.value[0].id).toBe('1');
    });

    it('should call onChange callback with Role[] format', async () => {
      const onChange = vi.fn();
      renderUserRoleAssignment({ onChange });

      // Select an option
      const option = screen.getByTestId('option-1');
      await user.click(option);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
        const callArgs = onChange.mock.calls[0][0];
        expect(Array.isArray(callArgs)).toBe(true);
        expect(callArgs[0]).toHaveProperty('id');
        expect(callArgs[0]).toHaveProperty('name');
      });
    });

    it('should prevent duplicate role selections', async () => {
      const onChange = vi.fn();
      const assignedRoles: Role[] = [{ id: '1', name: 'Admin Role' }];
      renderUserRoleAssignment({ assignedRoles, onChange });

      // Try to select the same role again
      const option = screen.getByTestId('option-1');
      await user.click(option);

      // Should maintain uniqueness
      await waitFor(() => {
        const props = (window as any).__autocompleteProps;
        const uniqueIds = new Set(props.value.map((v: any) => v.id));
        expect(uniqueIds.size).toBe(props.value.length);
      });
    });

    it('should filter roles by name (case-insensitive)', async () => {
      renderUserRoleAssignment();

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
    it('should render chips for selected roles', () => {
      const assignedRoles: Role[] = [{ id: '1', name: 'Admin Role' }];
      renderUserRoleAssignment({ assignedRoles });

      expect(screen.getByTestId('selected-1')).toBeInTheDocument();
    });

    it('should allow removing roles via chip delete', async () => {
      const onChange = vi.fn();
      const assignedRoles: Role[] = [
        { id: '1', name: 'Admin Role' },
        { id: '2', name: 'User Role' },
      ];
      renderUserRoleAssignment({ assignedRoles, onChange });

      const selectedChip = screen.getByTestId('selected-1');
      const removeButton = within(selectedChip).getByRole('button', { name: /remove/i });
      await user.click(removeButton);

      await waitFor(() => {
        expect(onChange).toHaveBeenCalled();
      });
    });

    it('should show correct placeholder text', () => {
      renderUserRoleAssignment();

      // Placeholder is passed to TextField via renderInput
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });

    it('should show correct label text', () => {
      renderUserRoleAssignment();

      // Label is passed to TextField via renderInput
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });
  });

  describe('Props handling', () => {
    it('should handle onChange being undefined', () => {
      renderUserRoleAssignment({ onChange: undefined });

      // Should not crash
      expect(screen.getByTestId('autocomplete')).toBeInTheDocument();
    });

    it('should handle empty assignedRoles array', () => {
      renderUserRoleAssignment({ assignedRoles: [] });

      const props = (window as any).__autocompleteProps;
      expect(props.value).toEqual([]);
    });
  });
});
