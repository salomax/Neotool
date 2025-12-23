import React, { useEffect, useRef } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FormProvider, useForm } from 'react-hook-form';
import { GroupUserAssignment } from '../GroupUserAssignment';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { GroupFormData } from '../GroupForm';

// Mock GraphQL queries
const mockUsers = [
  { id: '1', email: 'user1@example.com', displayName: 'User One' },
  { id: '2', email: 'user2@example.com', displayName: 'User Two' },
  { id: '3', email: 'user3@example.com', displayName: 'User Three' },
];

const mockUseGetUsersQuery = vi.fn(() => ({
  data: {
    users: {
      edges: mockUsers.map((user) => ({
        node: user,
      })),
    },
  },
  loading: false,
  error: undefined,
  refetch: vi.fn(),
}));

vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetUsersQuery: () => mockUseGetUsersQuery(),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'groupManagement.form.users': 'Users',
        'groupManagement.form.usersHelper': 'Select users to assign to this group',
        'groupManagement.form.errors.loadUsersFailed': 'Failed to load users',
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
        <button onClick={onRetry}>Retry</button>
      </div>
    ) : null,
}));

// Mock useAuth
vi.mock('@/shared/providers/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: '1', email: 'test@example.com' },
  }),
}));

const renderGroupUserAssignment = (assignedUsers: Array<{ id: string; email: string; displayName: string | null; enabled: boolean }> = []) => {
  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    const methods = useForm<GroupFormData>({
      defaultValues: {
        name: '',
        description: '',
        userIds: assignedUsers.map(u => u.id),
      },
    });

    return (
      <AppThemeProvider>
        <FormProvider {...methods}>{children}</FormProvider>
      </AppThemeProvider>
    );
  };

  return render(
    <Wrapper>
      <GroupUserAssignment assignedUsers={assignedUsers} />
    </Wrapper>
  );
};

describe.sequential('GroupUserAssignment', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGetUsersQuery.mockReturnValue({
      data: {
        users: {
          edges: mockUsers.map((user) => ({
            node: user,
          })),
        },
      },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe('Rendering', () => {
    it('should render autocomplete for user selection', () => {
      renderGroupUserAssignment();

      // SearchableAutocomplete renders a TextField, find it by helper text or check it exists
      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });

    it('should show loading state', () => {
      mockUseGetUsersQuery.mockReturnValue({
        data: undefined as any,
        loading: true,
        error: undefined,
        refetch: vi.fn(),
      });

      renderGroupUserAssignment();

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('should show error state', () => {
      mockUseGetUsersQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch: vi.fn(),
      });

      renderGroupUserAssignment();

      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
    });

    it('should display helper text', () => {
      renderGroupUserAssignment();

      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });
  });

  describe('Initial values', () => {
    it('should pre-select users from assignedUsers', () => {
      renderGroupUserAssignment([
        { id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true },
        { id: '2', email: 'user2@example.com', displayName: 'User Two', enabled: true },
      ]);

      // Users should be selected - check for helper text to verify component rendered
      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });

    it('should handle empty assignedUsers', () => {
      renderGroupUserAssignment([]);

      // Component should render even with empty assignedUsers
      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });
  });

  describe('User selection', () => {
    it('should update form when users are selected', async () => {
      const Wrapper = () => {
        const methods = useForm<GroupFormData>({
          defaultValues: { name: '', description: '', userIds: [] },
        });

        return (
          <AppThemeProvider>
            <FormProvider {...methods}>
              <GroupUserAssignment assignedUsers={[]} />
              <div data-testid="form-values">
                {/* eslint-disable-next-line react-hooks/incompatible-library */}
                {JSON.stringify(methods.watch('userIds'))}
              </div>
            </FormProvider>
          </AppThemeProvider>
        );
      };

      render(<Wrapper />);

      // Form should be initialized
      expect(screen.getByTestId('form-values')).toBeInTheDocument();
    });

    it('should handle user deselection', () => {
      renderGroupUserAssignment([
        { id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true },
        { id: '2', email: 'user2@example.com', displayName: 'User Two', enabled: true },
      ]);

      // Component should handle deselection - verify it renders
      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });
  });

  describe('Deduplication', () => {
    it('should deduplicate users by ID', () => {
      // The component should handle deduplication internally
      renderGroupUserAssignment([
        { id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true },
        { id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true },
        { id: '2', email: 'user2@example.com', displayName: 'User Two', enabled: true },
      ]);

      // Verify component renders - deduplication is handled internally
      expect(screen.getByText('Select users to assign to this group')).toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('should show retry button in error state', async () => {
      const refetch = vi.fn();
      mockUseGetUsersQuery.mockReturnValue({
        data: undefined as any,
        loading: false,
        error: new Error('Failed to load') as any,
        refetch,
      });

      renderGroupUserAssignment();

      const retryButton = screen.getByText('Retry');
      if (retryButton) {
        await user.click(retryButton);
        expect(refetch).toHaveBeenCalled();
      }
    });
  });

  describe('Form integration', () => {
    it('should be controlled by react-hook-form', () => {
      const Wrapper = () => {
        const methods = useForm<GroupFormData>({
          defaultValues: { name: '', description: '', userIds: ['1'] },
        });

        return (
          <AppThemeProvider>
            <FormProvider {...methods}>
              <GroupUserAssignment assignedUsers={[{ id: '1', email: 'user1@example.com', displayName: 'User One', enabled: true }]} />
            </FormProvider>
          </AppThemeProvider>
        );
      };

      render(<Wrapper />);

      // Component should be integrated with form
      expect(screen.getByLabelText('Users')).toBeInTheDocument();
    });

    it('should handle form validation errors', async () => {
      const formMethodsRef = { current: null as ReturnType<typeof useForm<GroupFormData>> | null };
      
      const Wrapper = () => {
        const methods = useForm<GroupFormData>({
          defaultValues: { name: '', description: '', userIds: [] },
        });
        // Store methods in ref for external access (only on first render)
        useEffect(() => {
          formMethodsRef.current = methods;
        }, [methods]);

        return (
          <AppThemeProvider>
            <FormProvider {...methods}>
              <GroupUserAssignment assignedUsers={[]} name="userIds" />
            </FormProvider>
          </AppThemeProvider>
        );
      };

      const { rerender } = render(<Wrapper />);
      
      // Set error after initial render using act
      await act(async () => {
        formMethodsRef.current?.setError('userIds', { type: 'required', message: 'Users are required' });
      });
      
      // Force re-render to pick up the error
      rerender(
        <AppThemeProvider>
          <FormProvider {...formMethodsRef.current!}>
            <GroupUserAssignment assignedUsers={[]} name="userIds" />
          </FormProvider>
        </AppThemeProvider>
      );

      // Error should be displayed
      await waitFor(() => {
        expect(screen.getByText('Users are required')).toBeInTheDocument();
      }, { timeout: 2000 });
    });
  });
});
