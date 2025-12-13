"use client";

import { useState, useMemo, useCallback, useEffect, useRef, startTransition } from "react";
import {
  useGetUsersQuery,
  GetUsersDocument,
  GetUsersQueryVariables,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import { extractErrorMessage } from '@/shared/utils/error';
import { hasAuthToken, isAuthenticationError } from '@/shared/utils/auth';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useUserMutations } from './useUserMutations';
import { useAuth } from '@/shared/providers/AuthProvider';
import type { UserSortState, UserOrderField } from '@/shared/utils/sorting';
import type { UserOrderByInput } from '@/lib/graphql/types/__generated__/graphql';
import { logger } from '@/shared/utils/logger';

export type User = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

export type UseUserManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
  /**
   * When true, skips executing the query (useful while waiting for dynamic sizing).
   */
  skip?: boolean;
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
  const { isAuthenticated } = useAuth();
  const waitingForPageSize = options.skip ?? false;
  
  // Local state
  const [firstState, setFirstState] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // Ref to track current first value for guard
  const firstRef = useRef(firstState);
  firstRef.current = firstState;

  // State to preserve previous data during loading to prevent flicker
  const [previousData, setPreviousData] = useState<any>(null);
  
  // Check if we actually have a token (even if isAuthenticated is true, token might be invalid)
  const hasToken = hasAuthToken();
  const shouldSkipQuery = waitingForPageSize || !isAuthenticated || !hasToken;

  // Search state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");

  // Sorting - memoize the callback to prevent unnecessary re-renders
  // Reset cursor when sorting changes (Apollo will automatically refetch when orderBy variable changes)
  const handleSortChange = useCallback(() => {
    // Reset cursor when sorting changes (non-urgent update)
    if (after !== null) {
      startTransition(() => {
        setAfter(null);
      });
    }
  }, [after]);

  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<UserOrderField>({
    initialSort: null,
    onSortChange: handleSortChange,
  });

  // Memoize query variables to prevent unnecessary re-renders
  const queryVariables = useMemo<GetUsersQueryVariables>(() => {
    const vars: GetUsersQueryVariables = {
      first: firstState,
      ...(after && { after }),
      ...(searchQuery && { query: searchQuery }),
      ...(graphQLOrderBy && graphQLOrderBy.length > 0 && { orderBy: graphQLOrderBy as UserOrderByInput[] }),
    };
    return vars;
  }, [firstState, after, searchQuery, graphQLOrderBy]);

  // GraphQL hooks - skip query if not authenticated or if we've seen an auth error
  const { data: usersData, loading, error, refetch } = useGetUsersQuery({
    variables: queryVariables,
    skip: shouldSkipQuery, // Skip if not authenticated, no token, auth error, or awaiting page size
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
    fetchPolicy: 'network-only', // Always fetch from network, no cache
  });

  // Helper to check if error is auth error
  const authErrorMessage = useMemo(() => {
    if (!error) return null;
    return extractErrorMessage(error);
  }, [error]);

  const isAuthError = useMemo(() => {
    if (!error) return false;
    return isAuthenticationError(error, authErrorMessage || undefined);
  }, [error, authErrorMessage]);

  // Log authentication errors, actual redirect/refresh handled globally by Apollo error link
  useEffect(() => {
    if (error && isAuthError) {
      logger.debug('[useUserManagement] Authentication error detected (delegated to Apollo error link)', {
        message: authErrorMessage,
        hasToken,
      });
    }
  }, [error, isAuthError, authErrorMessage, hasToken]);

  // Update previous data state when we have new data
  useEffect(() => {
    if (usersData && !loading) {
      startTransition(() => {
        setPreviousData(usersData);
      });
    }
  }, [usersData, loading]);

  // Use mutation hook internally
  const {
    enableUser,
    disableUser,
    assignGroupToUser,
    removeGroupFromUser,
    enableLoading,
    disableLoading,
    assignGroupLoading,
    removeGroupLoading,
  } = useUserMutations({
    refetchQuery: GetUsersDocument,
    refetchVariables: queryVariables,
    onRefetch: refetch,
  });

  // Guard setFirst to only update if value actually changed
  const setFirst = useCallback((newFirst: number) => {
    if (firstRef.current === newFirst) {
      return; // No change, prevent unnecessary state update and query
    }
    startTransition(() => {
      setFirstState(newFirst);
    });
  }, []);

  // Use the state value
  const first = firstState;

  // Get current data (use previous data while loading to prevent flicker)
  const currentData = useMemo(() => {
    return usersData || (loading ? previousData : null);
  }, [usersData, loading, previousData]);

  // Derived data - use previous data while loading to prevent flicker
  const users = useMemo(() => {
    return currentData?.users?.edges?.map((e: { node: User }) => e.node) || [];
  }, [currentData]);

  const pageInfo = useMemo(() => {
    const info = currentData?.users?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [currentData, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    return currentData?.users?.totalCount ?? null;
  }, [currentData]);

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
    // Removed onSearchChange - useRelayPagination already handles pagination reset when searchQuery changes
    setSearchQuery,
    searchQuery,
  });

  const effectiveLoading = waitingForPageSize || loading;

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
    
    // Loading states
    loading: effectiveLoading,
    enableLoading,
    disableLoading,
    assignGroupLoading,
    removeGroupLoading,
    
    // Error handling - consistent with useRoleManagement and useGroupManagement
    error: !waitingForPageSize && error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
