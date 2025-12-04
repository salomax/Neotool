"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
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
import { toGraphQLOrderBy, getNextSortState, type UserSortState, type UserOrderField } from '@/shared/utils/sorting';
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
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);
  const [orderBy, setOrderBy] = useState<UserSortState>(null);

  // Ref to preserve previous data during loading to prevent flicker
  const previousDataRef = useRef<typeof usersData | null>(null);
  
  // Ref to track in-flight mutations to prevent race conditions
  const mutationInFlightRef = useRef<Set<string>>(new Set());

  // Convert sort state to GraphQL format
  const graphQLOrderBy = useMemo(() => toGraphQLOrderBy(orderBy), [orderBy]);

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

  // Update previous data ref when we have new data
  useEffect(() => {
    if (usersData && !loading) {
      previousDataRef.current = usersData;
    }
  }, [usersData, loading]);

  const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();
  const [disableUserMutation, { loading: disableLoading }] = useDisableUserMutation();
  const [assignGroupToUserMutation, { loading: assignGroupLoading }] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation, { loading: removeGroupLoading }] = useRemoveGroupFromUserMutation();
  const [assignRoleToUserMutation, { loading: assignRoleLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleLoading }] = useRemoveRoleFromUserMutation();

  // Derived data - use previous data while loading to prevent flicker
  const users = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = usersData || (loading ? previousDataRef.current : null);
    return currentData?.users?.nodes || [];
  }, [usersData?.users?.nodes, loading]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = usersData || (loading ? previousDataRef.current : null);
    const info = currentData?.users?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [usersData?.users?.pageInfo, loading, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = usersData || (loading ? previousDataRef.current : null);
    return currentData?.users?.totalCount ?? null;
  }, [usersData?.users?.totalCount, loading]);

  // Reset cursor when sorting changes (similar to search)
  // Note: We intentionally omit 'after' from dependencies because we only want
  // to reset when orderBy changes, not when after changes. Including 'after'
  // would cause the effect to run on every pagination change, which is not desired.
  useEffect(() => {
    if (after !== null) {
      setAfter(null);
    }
  }, [orderBy]); // eslint-disable-line react-hooks/exhaustive-deps

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

  // Sorting handlers
  const handleSort = useCallback((field: UserOrderField) => {
    const nextSort = getNextSortState(orderBy, field);
    setOrderBy(nextSort);
  }, [orderBy]);

  // Mutations
  const enableUser = useCallback(async (userId: string) => {
    // Prevent race conditions: if a mutation is already in flight for this user, skip
    if (mutationInFlightRef.current.has(userId)) {
      return;
    }

    mutationInFlightRef.current.add(userId);
    try {
      const result = await enableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: (graphQLOrderBy as UserOrderByInput[] | undefined) || undefined,
            },
          },
        ],
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error enabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to enable user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(userId);
    }
  }, [enableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  const disableUser = useCallback(async (userId: string) => {
    // Prevent race conditions: if a mutation is already in flight for this user, skip
    if (mutationInFlightRef.current.has(userId)) {
      return;
    }

    mutationInFlightRef.current.add(userId);
    try {
      const result = await disableUserMutation({
        variables: { userId },
        refetchQueries: [
          {
            query: GetUsersDocument,
            variables: {
              first,
              after: after || undefined,
              query: searchQuery || undefined,
              orderBy: (graphQLOrderBy as UserOrderByInput[] | undefined) || undefined,
            },
          },
        ],
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error disabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to disable user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(userId);
    }
  }, [disableUserMutation, refetch, first, after, searchQuery, graphQLOrderBy]);

  const assignGroupToUser = useCallback(async (userId: string, groupId: string) => {
    // Create unique key for this user-group combination to prevent duplicate assignments
    const mutationKey = `assign-group-${userId}-${groupId}`;
    if (mutationInFlightRef.current.has(mutationKey)) {
      return;
    }

    mutationInFlightRef.current.add(mutationKey);
    try {
      const result = await assignGroupToUserMutation({
        variables: { userId, groupId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error assigning group to user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to assign group to user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(mutationKey);
    }
  }, [assignGroupToUserMutation, refetch]);

  const removeGroupFromUser = useCallback(async (userId: string, groupId: string) => {
    // Create unique key for this user-group combination to prevent duplicate removals
    const mutationKey = `remove-group-${userId}-${groupId}`;
    if (mutationInFlightRef.current.has(mutationKey)) {
      return;
    }

    mutationInFlightRef.current.add(mutationKey);
    try {
      const result = await removeGroupFromUserMutation({
        variables: { userId, groupId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error removing group from user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to remove group from user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(mutationKey);
    }
  }, [removeGroupFromUserMutation, refetch]);

  const assignRoleToUser = useCallback(async (userId: string, roleId: string) => {
    // Create unique key for this user-role combination to prevent duplicate assignments
    const mutationKey = `assign-role-${userId}-${roleId}`;
    if (mutationInFlightRef.current.has(mutationKey)) {
      return;
    }

    mutationInFlightRef.current.add(mutationKey);
    try {
      const result = await assignRoleToUserMutation({
        variables: { userId, roleId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error assigning role to user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to assign role to user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(mutationKey);
    }
  }, [assignRoleToUserMutation, refetch]);

  const removeRoleFromUser = useCallback(async (userId: string, roleId: string) => {
    // Create unique key for this user-role combination to prevent duplicate removals
    const mutationKey = `remove-role-${userId}-${roleId}`;
    if (mutationInFlightRef.current.has(mutationKey)) {
      return;
    }

    mutationInFlightRef.current.add(mutationKey);
    try {
      const result = await removeRoleFromUserMutation({
        variables: { userId, roleId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error removing role from user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to remove role from user');
      throw new Error(errorMessage);
    } finally {
      mutationInFlightRef.current.delete(mutationKey);
    }
  }, [removeRoleFromUserMutation, refetch]);

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
