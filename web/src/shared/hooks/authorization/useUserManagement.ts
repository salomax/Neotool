"use client";

import { useState, useMemo, useCallback, useEffect, startTransition } from "react";
import {
  useGetUsersQuery,
  GetUsersDocument,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import {
  useEnableUserMutation,
  useDisableUserMutation,
  useAssignGroupToUserMutation,
  useRemoveGroupFromUserMutation,
  useAssignRoleToUserMutation,
  useRemoveRoleFromUserMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { extractErrorMessage } from '@/shared/utils/error';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { UserSortState, UserOrderField } from '@/shared/utils/sorting';
import type { UserOrderByInput } from '@/lib/graphql/types/__generated__/graphql';

export type User = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

export type UseUserManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
};

export type UseUserManagementReturn = {
  // Data
  users: User[];
  
  // Pagination
  first: number;
  after: string | null;
  pageInfo: {
    hasNextPage: boolean;
    hasPreviousPage: boolean;
    startCursor: string | null;
    endCursor: string | null;
  } | null;
  totalCount: number | null;
  paginationRange: {
    start: number;
    end: number;
    total: number | null;
  };
  canLoadPreviousPage: boolean;
  setFirst: (first: number) => void;
  loadNextPage: () => void;
  loadPreviousPage: () => void;
  goToFirstPage: () => void;
  
  // Search
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  inputValue: string;
  handleInputChange: (value: string) => void;
  handleSearch: (value: string) => void;
  
  // Sorting
  orderBy: UserSortState;
  setOrderBy: (orderBy: UserSortState) => void;
  handleSort: (field: UserOrderField) => void;
  
  // Mutations
  enableUser: (userId: string) => Promise<void>;
  disableUser: (userId: string) => Promise<void>;
  assignGroupToUser: (userId: string, groupId: string) => Promise<void>;
  removeGroupFromUser: (userId: string, groupId: string) => Promise<void>;
  assignRoleToUser: (userId: string, roleId: string) => Promise<void>;
  removeRoleFromUser: (userId: string, roleId: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  enableLoading: boolean;
  disableLoading: boolean;
  assignGroupLoading: boolean;
  removeGroupLoading: boolean;
  assignRoleLoading: boolean;
  removeRoleLoading: boolean;
  
  // Error handling
  error: Error | undefined;
  
  // Utilities
  refetch: () => void;
};

/**
 * Custom hook for managing user data and operations
 * 
 * This hook encapsulates all user-related business logic including:
 * - Relay pagination for user listings
 * - Search functionality (by name, email, or identifier)
 * - Enable/disable user operations
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all user management functionality
 * 
 * @example
 * ```tsx
 * function UserManagementPage() {
 *   const {
 *     users,
 *     searchQuery,
 *     setSearchQuery,
 *     enableUser,
 *     disableUser,
 *     loadNextPage,
 *     loading,
 *     error
 *   } = useUserManagement();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchQuery} 
 *         onChange={(e) => setSearchQuery(e.target.value)} 
 *       />
 *       {users.map(user => (
 *         <div key={user.id}>
 *           {user.displayName} ({user.email})
 *           <button onClick={() => user.enabled ? disableUser(user.id) : enableUser(user.id)}>
 *             {user.enabled ? 'Disable' : 'Enable'}
 *           </button>
 *         </div>
 *       ))}
 *       {pageInfo?.hasNextPage && (
 *         <button onClick={loadNextPage}>Load More</button>
 *       )}
 *     </div>
 *   );
 * }
 * ```
 */
export function useUserManagement(options: UseUserManagementOptions = {}): UseUserManagementReturn {
  // Local state
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // State to preserve previous data during loading to prevent flicker
  const [previousData, setPreviousData] = useState<typeof usersData | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");

  // Sorting - memoize the callback to prevent unnecessary re-renders
  // Reset cursor when sorting changes (Apollo will automatically refetch when orderBy variable changes)
  const handleSortChange = useCallback(() => {
    // Reset cursor when sorting changes
    setAfter(null);
  }, []);

  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<UserOrderField>({
    initialSort: null,
    onSortChange: handleSortChange,
  });

  // GraphQL hooks
  const { data: usersData, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      // Cast to GraphQL type - the utility function returns compatible structure
      // but TypeScript sees them as different types due to separate type definitions
      orderBy: (graphQLOrderBy as UserOrderByInput[] | undefined) || undefined,
    },
    skip: false,
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
  });

  // Update previous data state when we have new data
  useEffect(() => {
    if (usersData && !loading) {
      startTransition(() => {
        setPreviousData(usersData);
      });
    }
  }, [usersData, loading]);

  const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();
  const [disableUserMutation, { loading: disableLoading }] = useDisableUserMutation();
  const [assignGroupToUserMutation, { loading: assignGroupLoading }] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation, { loading: removeGroupLoading }] = useRemoveGroupFromUserMutation();
  const [assignRoleToUserMutation, { loading: assignRoleLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleLoading }] = useRemoveRoleFromUserMutation();

  // Mutation hook with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery: GetUsersDocument,
    refetchVariables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: (graphQLOrderBy as UserOrderByInput[] | undefined) || undefined,
    },
    onRefetch: refetch,
    errorMessage: 'Failed to update user',
  });

  // Derived data - use previous data while loading to prevent flicker
  const users = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = usersData || (loading ? previousData : null);
    return currentData?.users?.edges?.map(e => e.node) || [];
  }, [usersData, loading, previousData]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = usersData || (loading ? previousData : null);
    const info = currentData?.users?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [usersData, loading, after, previousData]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = usersData || (loading ? previousData : null);
    return currentData?.users?.totalCount ?? null;
  }, [usersData, loading, previousData]);

  // Use shared pagination hook
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  } = useRelayPagination(
    users,
    pageInfo,
    totalCount,
    searchQuery,
    after,
    setAfter,
    {
      initialAfter: null,
      initialSearchQuery: options.initialSearchQuery,
    }
  );

  // Search with debounce - now that we have goToFirstPage
  const {
    inputValue: searchInputValue,
    handleInputChange: handleSearchInputChange,
    handleSearch: handleSearchChange,
  } = useDebouncedSearch({
    initialValue: searchQuery,
    debounceMs: 300,
    onSearchChange: goToFirstPage,
    setSearchQuery,
    searchQuery,
  });

  // Mutations
  const enableUser = useCallback(
    async (userId: string) => {
      await executeMutation(enableUserMutation, { userId }, userId);
    },
    [executeMutation, enableUserMutation]
  );

  const disableUser = useCallback(
    async (userId: string) => {
      await executeMutation(disableUserMutation, { userId }, userId);
    },
    [executeMutation, disableUserMutation]
  );

  const assignGroupToUser = useCallback(
    async (userId: string, groupId: string) => {
      await executeMutation(
        assignGroupToUserMutation,
        { userId, groupId },
        `assign-group-${userId}-${groupId}`
      );
    },
    [executeMutation, assignGroupToUserMutation]
  );

  const removeGroupFromUser = useCallback(
    async (userId: string, groupId: string) => {
      await executeMutation(
        removeGroupFromUserMutation,
        { userId, groupId },
        `remove-group-${userId}-${groupId}`
      );
    },
    [executeMutation, removeGroupFromUserMutation]
  );

  const assignRoleToUser = useCallback(
    async (userId: string, roleId: string) => {
      await executeMutation(
        assignRoleToUserMutation,
        { userId, roleId },
        `assign-role-${userId}-${roleId}`
      );
    },
    [executeMutation, assignRoleToUserMutation]
  );

  const removeRoleFromUser = useCallback(
    async (userId: string, roleId: string) => {
      await executeMutation(
        removeRoleFromUserMutation,
        { userId, roleId },
        `remove-role-${userId}-${roleId}`
      );
    },
    [executeMutation, removeRoleFromUserMutation]
  );

  return {
    // Data
    users,
    
    // Pagination
    first,
    after,
    pageInfo,
    totalCount,
    paginationRange,
    canLoadPreviousPage,
    setFirst,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    
    // Search
    searchQuery,
    setSearchQuery,
    inputValue: searchInputValue,
    handleInputChange: handleSearchInputChange,
    handleSearch: handleSearchChange,
    
    // Sorting
    orderBy,
    setOrderBy,
    handleSort,
    
    // Mutations
    enableUser,
    disableUser,
    assignGroupToUser,
    removeGroupFromUser,
    assignRoleToUser,
    removeRoleFromUser,
    
    // Loading states
    loading,
    enableLoading,
    disableLoading,
    assignGroupLoading,
    removeGroupLoading,
    assignRoleLoading,
    removeRoleLoading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
