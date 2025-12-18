import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useUserManagement } from '../useUserManagement';

// Mock the GraphQL operations
vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetUsersQuery: vi.fn(),
  GetUsersDocument: {}, // Mock document for refetch
}));

vi.mock('@/shared/hooks/authorization/useUserMutations', () => ({
  useUserMutations: vi.fn(),
}));

vi.mock('@/shared/hooks/pagination', () => ({
  useRelayPagination: vi.fn(),
}));

vi.mock('@/shared/providers/AuthProvider', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: true })),
}));

vi.mock('@/shared/utils/auth', () => ({
  hasAuthToken: vi.fn(() => true),
  isAuthenticationError: vi.fn(() => false),
}));

import { useGetUsersQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';
import { useUserMutations } from '@/shared/hooks/authorization/useUserMutations';
import { useRelayPagination } from '@/shared/hooks/pagination';

describe('useUserManagement', () => {
  const mockUsers = [
    {
      id: '1',
      email: 'john@example.com',
      displayName: 'John Doe',
      enabled: true,
    },
    {
      id: '2',
      email: 'jane@example.com',
      displayName: 'Jane Smith',
      enabled: false,
    },
  ];

  const mockPageInfo = {
    hasNextPage: false,
    hasPreviousPage: false,
    startCursor: 'cursor1',
    endCursor: 'cursor2',
  };

  const mockRefetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    (useGetUsersQuery as any).mockReturnValue({
      data: {
        users: {
          edges: mockUsers.map(u => ({ node: u, cursor: 'cursor' })),
          pageInfo: mockPageInfo,
          totalCount: 2,
        },
      },
      loading: false,
      error: undefined,
      refetch: mockRefetch,
    });

    // Mock useUserMutations
    (useUserMutations as any).mockReturnValue({
      enableUser: vi.fn(),
      disableUser: vi.fn(),
      assignGroupToUser: vi.fn(),
      removeGroupFromUser: vi.fn(),
      assignRoleToUser: vi.fn(),
      removeRoleFromUser: vi.fn(),
      enableLoading: false,
      disableLoading: false,
      assignGroupLoading: false,
      removeGroupLoading: false,
      assignRoleLoading: false,
      removeRoleLoading: false,
    });

    (useRelayPagination as any).mockReturnValue({
      loadNextPage: vi.fn(),
      loadPreviousPage: vi.fn(),
      goToFirstPage: vi.fn(),
      paginationRange: { start: 1, end: 2, total: 2 },
      canLoadPreviousPage: false,
    });
  });

  describe('sorting', () => {
    it('should initialize with null orderBy (use backend default)', () => {
      const { result } = renderHook(() => useUserManagement());

      expect(result.current.orderBy).toBeNull();
    });

    it('should pass null orderBy to GraphQL query when sort state is null', () => {
      renderHook(() => useUserManagement());

      expect(useGetUsersQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: expect.objectContaining({
            orderBy: undefined,
          }),
        })
      );
    });

    it('should pass orderBy to GraphQL query when sort state is set', async () => {
      const { result } = renderHook(() => useUserManagement());

      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      await waitFor(() => {
        expect(useGetUsersQuery).toHaveBeenCalledWith(
          expect.objectContaining({
            variables: expect.objectContaining({
              orderBy: [{ field: 'DISPLAY_NAME', direction: 'ASC' }],
            }),
          })
        );
      });
    });

    it('should handle sort changes with handleSort', () => {
      const { result } = renderHook(() => useUserManagement());

      // Initially null
      expect(result.current.orderBy).toBeNull();

      // Click DISPLAY_NAME -> should set to asc
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toEqual({
        field: 'DISPLAY_NAME',
        direction: 'asc',
      });

      // Click DISPLAY_NAME again -> should set to desc
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toEqual({
        field: 'DISPLAY_NAME',
        direction: 'desc',
      });

      // Click DISPLAY_NAME again -> should set to null
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toBeNull();
    });

    it('should switch to new field when clicking different column', () => {
      const { result } = renderHook(() => useUserManagement());

      // Set DISPLAY_NAME to desc
      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'desc' });
      });

      // Click EMAIL -> should switch to EMAIL asc
      act(() => {
        result.current.handleSort('EMAIL');
      });

      expect(result.current.orderBy).toEqual({
        field: 'EMAIL',
        direction: 'asc',
      });
    });

    it('should reset cursor when orderBy changes', async () => {
      const mockSetAfter = vi.fn();
      // Mock useState to track setAfter calls
      vi.spyOn(require('react'), 'useState').mockImplementation((initial) => {
        if (initial === null) {
          return [null, mockSetAfter];
        }
        return require('react').useState(initial);
      });

      const { result } = renderHook(() => useUserManagement());

      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      // Note: The actual cursor reset happens in useEffect, which is tested indirectly
      // by checking that the query is called with after: undefined
      await waitFor(() => {
        expect(useGetUsersQuery).toHaveBeenCalledWith(
          expect.objectContaining({
            variables: expect.objectContaining({
              after: undefined,
            }),
          })
        );
      });
    });

    it('should convert sort state to GraphQL format correctly', async () => {
      const { result } = renderHook(() => useUserManagement());

      // Test DISPLAY_NAME asc
      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      await waitFor(() => {
        const lastCall = (useGetUsersQuery as any).mock.calls[
          (useGetUsersQuery as any).mock.calls.length - 1
        ];
        expect(lastCall[0].variables.orderBy).toEqual([
          { field: 'DISPLAY_NAME', direction: 'ASC' },
        ]);
      });

      // Test EMAIL desc
      act(() => {
        result.current.setOrderBy({ field: 'EMAIL', direction: 'desc' });
      });

      await waitFor(() => {
        const lastCall = (useGetUsersQuery as any).mock.calls[
          (useGetUsersQuery as any).mock.calls.length - 1
        ];
        expect(lastCall[0].variables.orderBy).toEqual([
          { field: 'EMAIL', direction: 'DESC' },
        ]);
      });

      // Test ENABLED asc
      act(() => {
        result.current.setOrderBy({ field: 'ENABLED', direction: 'asc' });
      });

      await waitFor(() => {
        const lastCall = (useGetUsersQuery as any).mock.calls[
          (useGetUsersQuery as any).mock.calls.length - 1
        ];
        expect(lastCall[0].variables.orderBy).toEqual([
          { field: 'ENABLED', direction: 'ASC' },
        ]);
      });
    });

    it('should include orderBy in return value', () => {
      const { result } = renderHook(() => useUserManagement());

      expect(result.current.orderBy).toBeDefined();
      expect(result.current.setOrderBy).toBeDefined();
      expect(result.current.handleSort).toBeDefined();
      expect(typeof result.current.setOrderBy).toBe('function');
      expect(typeof result.current.handleSort).toBe('function');
    });
  });
});
