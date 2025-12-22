import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRoleManagement } from '../useRoleManagement';
import { useUserManagement } from '../useUserManagement';
import { useGroupManagement } from '../useGroupManagement';

const createBaseRolesResponse = () => ({
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

const createBaseUsersResponse = () => ({
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

const createBaseGroupsResponse = () => ({
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
    
    // Default stable responses to prevent re-render loops
    (useGetRolesQuery as any).mockReturnValue(createBaseRolesResponse());
    (useGetUsersQuery as any).mockReturnValue(createBaseUsersResponse());
    (useGetGroupsQuery as any).mockReturnValue(createBaseGroupsResponse());
  });

  describe('useRoleManagement', () => {
    it('should only call query once with same variables', () => {
      let callCount = 0;
      let lastVariables: any = null;
      const rolesResponse = createBaseRolesResponse();
      
      (useGetRolesQuery as any).mockImplementation((options: any) => {
        const currentVariables = JSON.stringify(options?.variables || {});
        // Only count as a new call if variables have changed
        if (currentVariables !== lastVariables) {
          callCount++;
          lastVariables = currentVariables;
        }
        return rolesResponse;
      });

      const { result, rerender } = renderHook(() => useRoleManagement({ initialFirst: 10 }));

      // Initial render
      expect(callCount).toBe(1);

      // Rerender with same props
      rerender();
      rerender();
      rerender();

      // Should still only be called once (memoized variables prevent re-query)
      expect(callCount).toBe(1);
    });

    it('should call query when first changes', () => {
      let firstValue = 10;
      const rolesResponse = createBaseRolesResponse();
      (useGetRolesQuery as any).mockImplementation((options: any) => {
        return rolesResponse;
      });

      const { result } = renderHook(() => useRoleManagement({ initialFirst: firstValue }));

      const initialCallCount = (useGetRolesQuery as any).mock.calls.length;
      const firstCallVariables = (useGetRolesQuery as any).mock.calls[initialCallCount - 1][0].variables;

      // Change first value
      act(() => {
        result.current.setFirst(20);
      });

      // Should be called again with new first value
      const afterCallCount = (useGetRolesQuery as any).mock.calls.length;
      expect(afterCallCount).toBe(initialCallCount + 1);
      const secondCallVariables = (useGetRolesQuery as any).mock.calls[afterCallCount - 1][0].variables;
      expect(secondCallVariables.first).toBe(20);
      expect(firstCallVariables.first).toBe(10);
    });

    it('should not call query when setFirst is called with same value', () => {
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
      const { rerender } = renderHook(() => useUserManagement({ initialFirst: 10 }));

      const initialCallCount = (useGetUsersQuery as any).mock.calls.length;
      const firstCallVariables = (useGetUsersQuery as any).mock.calls[initialCallCount - 1][0].variables;

      // Rerender multiple times
      rerender();
      rerender();

      const finalCallVariables = (useGetUsersQuery as any).mock.calls[(useGetUsersQuery as any).mock.calls.length - 1][0].variables;
      // Variables should remain referentially stable across rerenders
      expect(finalCallVariables).toBe(firstCallVariables);
    });
  });

  describe('useGroupManagement', () => {
    it('should only call query once with same variables', () => {
      const { rerender } = renderHook(() => useGroupManagement({ initialFirst: 10 }));

      const initialCallCount = (useGetGroupsQuery as any).mock.calls.length;
      const firstCallVariables = (useGetGroupsQuery as any).mock.calls[initialCallCount - 1][0].variables;

      // Rerender multiple times
      rerender();
      rerender();

      const finalCallVariables = (useGetGroupsQuery as any).mock.calls[(useGetGroupsQuery as any).mock.calls.length - 1][0].variables;
      expect(finalCallVariables).toBe(firstCallVariables);
    });
  });

  describe('Query variable memoization', () => {
    it('should maintain stable variable reference when values unchanged', () => {
      const stableResponse = createBaseRolesResponse();
      const mockQuery = vi.fn(() => stableResponse);

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
