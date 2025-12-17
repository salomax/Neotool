"use client";

import { useState, useMemo, useCallback, useEffect, useRef, startTransition } from "react";
import {
  useGetGroupsQuery,
  GetGroupsDocument,
  GetUserWithRelationshipsDocument,
  GetGroupsQueryVariables,
  GetGroupsQuery,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import { GroupOrderByInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useGroupMutations } from './useGroupMutations';

// Extract Group type from the query result - each query defines its own strict scope
export type Group = GetGroupsQuery['groups']['edges'][number]['node'];

export type GroupFormData = {
  name: string;
  description?: string | null;
  userIds?: string[];
};

export type UseGroupManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
  /**
   * When true, skips executing the query (useful while waiting for dynamic sizing).
   */
  skip?: boolean;
};

export type GroupOrderField = 'NAME';

export type UseGroupManagementReturn = {
  // Data
  groups: Group[];
  
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
  orderBy: ReturnType<typeof useSorting<GroupOrderField>>['orderBy'];
  setOrderBy: ReturnType<typeof useSorting<GroupOrderField>>['setOrderBy'];
  handleSort: (field: GroupOrderField) => void;
  
  // Dialog state
  dialogOpen: boolean;
  editingGroup: Group | null;
  deleteConfirm: Group | null;
  openCreateDialog: () => void;
  openEditDialog: (group: Group) => void;
  closeDialog: () => void;
  setDeleteConfirm: (group: Group | null) => void;
  
  // CRUD operations
  createGroup: (data: GroupFormData) => Promise<void>;
  updateGroup: (groupId: string, data: GroupFormData) => Promise<void>;
  deleteGroup: (groupId: string) => Promise<void>;
  assignRoleToGroup: (groupId: string, roleId: string) => Promise<void>;
  removeRoleFromGroup: (groupId: string, roleId: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignRoleLoading: boolean;
  removeRoleLoading: boolean;
  
  // Error handling
  error: Error | undefined;
  
  // Utilities
  refetch: () => void;
};

/**
 * Custom hook for managing group data and operations
 * 
 * This hook encapsulates all group-related business logic including:
 * - Relay pagination for group listings
 * - Search functionality (by name)
 * - CRUD operations (Create, Read, Update, Delete)
 * - Dialog state management
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all group management functionality
 * 
 * @example
 * ```tsx
 * function GroupManagementPage() {
 *   const {
 *     groups,
 *     searchQuery,
 *     setSearchQuery,
 *     createGroup,
 *     updateGroup,
 *     deleteGroup,
 *     openCreateDialog,
 *     loading,
 *     error
 *   } = useGroupManagement();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchQuery} 
 *         onChange={(e) => setSearchQuery(e.target.value)} 
 *       />
 *       {groups.map(group => (
 *         <div key={group.id}>{group.name}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useGroupManagement(options: UseGroupManagementOptions = {}): UseGroupManagementReturn {
  const waitingForPageSize = options.skip ?? false;

  // Local state
  const [firstState, setFirstState] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // Ref to track current first value for guard
  const firstRef = useRef(firstState);

  // Update ref in effect to avoid updating during render
  useEffect(() => {
    firstRef.current = firstState;
  }, [firstState]);

  // State to preserve previous data during loading to prevent flicker
  const [previousData, setPreviousData] = useState<any>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");

  // Sorting
  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<GroupOrderField>({
    initialSort: null,
    onSortChange: () => {
      // Reset cursor when sorting changes (non-urgent update)
      if (after !== null) {
        startTransition(() => {
          setAfter(null);
        });
      }
    },
  });

  // Memoize query variables to prevent unnecessary re-renders
  const queryVariables = useMemo<GetGroupsQueryVariables>(() => {
    const vars: GetGroupsQueryVariables = {
      first: firstState,
      ...(after && { after }),
      ...(searchQuery && { query: searchQuery }),
      ...(graphQLOrderBy && graphQLOrderBy.length > 0 && { orderBy: graphQLOrderBy as GroupOrderByInput[] }),
    };
    return vars;
  }, [firstState, after, searchQuery, graphQLOrderBy]);

  // GraphQL hooks
  // Note: orderBy is added to the query but generated types may not include it yet
  // Type assertion is used until GraphQL types are regenerated
  const { data: groupsData, loading, error, refetch } = useGetGroupsQuery({
    variables: queryVariables,
    skip: waitingForPageSize,
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
  });

  // Update previous data state when we have new data
  useEffect(() => {
    if (groupsData && !loading) {
      startTransition(() => {
        setPreviousData(groupsData);
      });
    }
  }, [groupsData, loading]);

  // Dialog management (kept for backward compatibility, but not used in new pattern)
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<Group | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Group | null>(null);

  const openCreateDialog = useCallback(() => {
    setEditingGroup(null);
    setDialogOpen(true);
  }, []);

  const openEditDialog = useCallback((group: Group) => {
    setEditingGroup(group);
    setDialogOpen(true);
  }, []);

  const closeDialog = useCallback(() => {
    setDialogOpen(false);
    setEditingGroup(null);
  }, []);

  // Use mutation hook internally
  const {
    createGroup: createGroupMutation,
    updateGroup: updateGroupMutation,
    deleteGroup: deleteGroupMutation,
    assignRoleToGroup,
    removeRoleFromGroup,
    createLoading,
    updateLoading,
    deleteLoading,
    assignRoleLoading,
    removeRoleLoading,
  } = useGroupMutations({
    refetchQuery: GetGroupsDocument,
    refetchVariables: queryVariables,
    onRefetch: refetch,
    onGroupSaved: closeDialog,
    onGroupDeleted: () => setDeleteConfirm(null),
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

  // Derived data - use previous data while loading to prevent flicker
  const groups = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = groupsData || (loading ? previousData : null);
    return currentData?.groups?.edges?.map((e: { node: Group }) => e.node) || [];
  }, [groupsData, loading, previousData]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = groupsData || (loading ? previousData : null);
    const info = currentData?.groups?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [groupsData, loading, after, previousData]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = groupsData || (loading ? previousData : null);
    return currentData?.groups?.totalCount ?? null;
  }, [groupsData, loading, previousData]);

  // Use shared pagination hook
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  } = useRelayPagination(
    groups,
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

  // Wrap mutation functions to handle refetch of user query if needed
  const createGroup = useCallback(async (data: GroupFormData) => {
    await createGroupMutation(data);
    // Refetch user query to update UserDrawer when group memberships change
    // Note: This is done separately since it's not part of the main refetch
    await refetch();
  }, [createGroupMutation, refetch]);

  const updateGroup = useCallback(async (groupId: string, data: GroupFormData) => {
    await updateGroupMutation(groupId, data);
    // Refetch user query to update UserDrawer when group memberships change
    // Note: This is done separately since it's not part of the main refetch
    await refetch();
  }, [updateGroupMutation, refetch]);

  const deleteGroup = useCallback(async (groupId: string) => {
    return deleteGroupMutation(groupId);
  }, [deleteGroupMutation]);

  return {
    // Data
    groups,
    
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
    
    // Dialog state
    dialogOpen,
    editingGroup,
    deleteConfirm,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    setDeleteConfirm,
    
    // CRUD operations
    createGroup,
    updateGroup,
    deleteGroup,
    assignRoleToGroup,
    removeRoleFromGroup,
    
    // Loading states
    loading: effectiveLoading,
    createLoading,
    updateLoading,
    deleteLoading,
    assignRoleLoading,
    removeRoleLoading,
    
    // Error handling
    error: waitingForPageSize ? undefined : error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
