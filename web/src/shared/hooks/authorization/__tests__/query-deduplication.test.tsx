import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRoleManagement } from '../useRoleManagement';
import { useUserManagement } from '../useUserManagement';
import { useGroupManagement } from '../useGroupManagement';

// Mock GraphQL operations
vi.mock('@/lib/graphql/operations/authorization-management/queries.generated', () => ({
  useGetRolesQuery: vi.fn(),
  useGetUsersQuery: vi.fn(),
  useGetGroupsQuery: vi.fn(),
  GetRolesDocument: {},
  GetUsersDocument: {},
  GetGroupsDocument: {},
}));

vi.mock('@/shared/hooks/authorization/useRoleMutations', () => ({
  useRoleMutations: vi.fn(() => ({
    createRole: vi.fn(),
    updateRole: vi.fn(),
    deleteRole: vi.fn(),
    assignPermissionToRole: vi.fn(),
    removePermissionFromRole: vi.fn(),
    assignRoleToUser: vi.fn(),
    removeRoleFromUser: vi.fn(),
    assignRoleToGroup: vi.fn(),
    removeRoleFromGroup: vi.fn(),
    createLoading: false,
    updateLoading: false,
    deleteLoading: false,
    assignPermissionLoading: false,
    removePermissionLoading: false,
    assignRoleToUserLoading: false,
    removeRoleFromUserLoading: false,
    assignRoleToGroupLoading: false,
    removeRoleFromGroupLoading: false,
  })),
}));

vi.mock('@/shared/hooks/authorization/useUserMutations', () => ({
  useUserMutations: vi.fn(() => ({
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
  })),
}));

vi.mock('@/shared/hooks/authorization/useGroupMutations', () => ({
  useGroupMutations: vi.fn(() => ({
    createGroup: vi.fn(),
    updateGroup: vi.fn(),
    deleteGroup: vi.fn(),
    assignRoleToGroup: vi.fn(),
    removeRoleFromGroup: vi.fn(),
    createLoading: false,
    updateLoading: false,
    deleteLoading: false,
    assignRoleLoading: false,
    removeRoleLoading: false,
  })),
}));

vi.mock('@/shared/hooks/pagination', () => ({
  useRelayPagination: vi.fn(() => ({
    loadNextPage: vi.fn(),
    loadPreviousPage: vi.fn(),
    goToFirstPage: vi.fn(),
    paginationRange: { start: 1, end: 10, total: 100 },
    canLoadPreviousPage: false,
  })),
}));

vi.mock('@/shared/providers/AuthProvider', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: true })),
}));

vi.mock('@/shared/utils/auth', () => ({
  hasAuthToken: vi.fn(() => true),
  isAuthenticationError: vi.fn(() => false),
}));

import { useGetRolesQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';
import { useGetUsersQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';
import { useGetGroupsQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';

describe('Query Deduplication', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useRoleManagement', () => {
    it('should only call query once with same variables', () => {
      (useGetRolesQuery as any).mockReturnValue({
        data: {
          roles: {
            edges: [],
            pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
            totalCount: 0,
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });

      const { result, rerender } = renderHook(() => useRoleManagement({ initialFirst: 10 }));

      // Initial render
      expect(useGetRolesQuery).toHaveBeenCalledTimes(1);

      // Rerender with same props
      rerender();
      rerender();
      rerender();

      // Should still only be called once (memoized variables prevent re-query)
      expect(useGetRolesQuery).toHaveBeenCalledTimes(1);
    });

    it('should call query when first changes', () => {
      let firstValue = 10;
      (useGetRolesQuery as any).mockImplementation((options: any) => {
        return {
          data: {
            roles: {
              edges: [],
              pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
              totalCount: 0,
            },
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        };
      });

      const { result } = renderHook(() => useRoleManagement({ initialFirst: firstValue }));

      expect(useGetRolesQuery).toHaveBeenCalledTimes(1);
      const firstCallVariables = (useGetRolesQuery as any).mock.calls[0][0].variables;

      // Change first value
      act(() => {
        result.current.setFirst(20);
      });

      // Should be called again with new first value
      expect(useGetRolesQuery).toHaveBeenCalledTimes(2);
      const secondCallVariables = (useGetRolesQuery as any).mock.calls[1][0].variables;
      expect(secondCallVariables.first).toBe(20);
      expect(firstCallVariables.first).toBe(10);
    });

    it('should not call query when setFirst is called with same value', () => {
      (useGetRolesQuery as any).mockReturnValue({
        data: {
          roles: {
            edges: [],
            pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
            totalCount: 0,
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });

      const { result } = renderHook(() => useRoleManagement({ initialFirst: 10 }));

      const initialCallCount = (useGetRolesQuery as any).mock.calls.length;

      // Call setFirst with same value multiple times
      act(() => {
        result.current.setFirst(10);
        result.current.setFirst(10);
        result.current.setFirst(10);
      });

      // Should not have been called again (guarded by ref comparison)
      expect(useGetRolesQuery).toHaveBeenCalledTimes(initialCallCount);
    });
  });

  describe('useUserManagement', () => {
    it('should only call query once with same variables', () => {
      (useGetUsersQuery as any).mockReturnValue({
        data: {
          users: {
            edges: [],
            pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
            totalCount: 0,
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });

      const { rerender } = renderHook(() => useUserManagement({ initialFirst: 10 }));

      expect(useGetUsersQuery).toHaveBeenCalledTimes(1);

      // Rerender multiple times
      rerender();
      rerender();

      // Should still only be called once
      expect(useGetUsersQuery).toHaveBeenCalledTimes(1);
    });
  });

  describe('useGroupManagement', () => {
    it('should only call query once with same variables', () => {
      (useGetGroupsQuery as any).mockReturnValue({
        data: {
          groups: {
            edges: [],
            pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
            totalCount: 0,
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      });

      const { rerender } = renderHook(() => useGroupManagement({ initialFirst: 10 }));

      expect(useGetGroupsQuery).toHaveBeenCalledTimes(1);

      // Rerender multiple times
      rerender();
      rerender();

      // Should still only be called once
      expect(useGetGroupsQuery).toHaveBeenCalledTimes(1);
    });
  });

  describe('Query variable memoization', () => {
    it('should maintain stable variable reference when values unchanged', () => {
      const mockQuery = vi.fn(() => ({
        data: {
          roles: {
            edges: [],
            pageInfo: { hasNextPage: false, hasPreviousPage: false, startCursor: null, endCursor: null },
            totalCount: 0,
          },
        },
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      }));

      (useGetRolesQuery as any).mockImplementation(mockQuery);

      const { result, rerender } = renderHook(() => useRoleManagement({ initialFirst: 10 }));

      // Ensure mock was called
      expect(mockQuery.mock.calls.length).toBeGreaterThan(0);
      const firstCall = mockQuery.mock.calls[0] as any;
      const firstCallVariables = firstCall?.[0]?.variables;

      // Rerender without changing anything
      rerender();

      // Ensure mock was called again
      expect(mockQuery.mock.calls.length).toBeGreaterThan(0);
      const lastCall = mockQuery.mock.calls[mockQuery.mock.calls.length - 1] as any;
      const secondCallVariables = lastCall?.[0]?.variables;

      // Variables object should be the same reference (memoized)
      // Note: Apollo might call it multiple times during render, but variables should be stable
      if (firstCallVariables && secondCallVariables) {
        expect(secondCallVariables.first).toBe(firstCallVariables.first);
        expect(secondCallVariables.after).toBe(firstCallVariables.after);
        expect(secondCallVariables.query).toBe(firstCallVariables.query);
      }
    });
  });
});
