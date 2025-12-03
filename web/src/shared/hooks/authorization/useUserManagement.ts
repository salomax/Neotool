"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetUsersQuery,
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
  setFirst: (first: number) => void;
  loadNextPage: () => void;
  loadPreviousPage: () => void;
  goToFirstPage: () => void;
  
  // Search
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  
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

  // GraphQL hooks
  const { data: usersData, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
    },
    skip: false,
  });

  const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();
  const [disableUserMutation, { loading: disableLoading }] = useDisableUserMutation();
  const [assignGroupToUserMutation, { loading: assignGroupLoading }] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation, { loading: removeGroupLoading }] = useRemoveGroupFromUserMutation();
  const [assignRoleToUserMutation, { loading: assignRoleLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleLoading }] = useRemoveRoleFromUserMutation();

  // Derived data - memoize to prevent unnecessary re-renders
  const users = useMemo(() => {
    return usersData?.users?.nodes || [];
  }, [usersData?.users?.nodes]);

  const pageInfo = useMemo(() => {
    const info = usersData?.users?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [usersData?.users?.pageInfo, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    return usersData?.users?.totalCount ?? null;
  }, [usersData?.users?.totalCount]);

  // Use shared pagination hook
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
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

  // Mutations
  const enableUser = useCallback(async (userId: string) => {
    try {
      const result = await enableUserMutation({
        variables: { userId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error enabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to enable user');
      throw new Error(errorMessage);
    }
  }, [enableUserMutation, refetch]);

  const disableUser = useCallback(async (userId: string) => {
    try {
      const result = await disableUserMutation({
        variables: { userId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error disabling user:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to disable user');
      throw new Error(errorMessage);
    }
  }, [disableUserMutation, refetch]);

  const assignGroupToUser = useCallback(async (userId: string, groupId: string) => {
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
    }
  }, [assignGroupToUserMutation, refetch]);

  const removeGroupFromUser = useCallback(async (userId: string, groupId: string) => {
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
    }
  }, [removeGroupFromUserMutation, refetch]);

  const assignRoleToUser = useCallback(async (userId: string, roleId: string) => {
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
    }
  }, [assignRoleToUserMutation, refetch]);

  const removeRoleFromUser = useCallback(async (userId: string, roleId: string) => {
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
    setFirst,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    
    // Search
    searchQuery,
    setSearchQuery,
    
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
