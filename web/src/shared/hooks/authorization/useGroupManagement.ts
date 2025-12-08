"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import {
  useGetGroupsQuery,
  GetGroupsDocument,
  GetUserWithRelationshipsDocument,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import {
  useCreateGroupMutation,
  useUpdateGroupMutation,
  useDeleteGroupMutation,
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { CreateGroupInput, UpdateGroupInput, GroupOrderByInput } from '@/lib/graphql/types/__generated__/graphql';
import { GroupFieldsFragment } from '@/lib/graphql/fragments/common.generated';
import { extractErrorMessage } from '@/shared/utils/error';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';

// Use the fragment type which includes all fields from the query
export type Group = GroupFieldsFragment;

export type GroupFormData = {
  name: string;
  description?: string | null;
  userIds?: string[];
};

export type UseGroupManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
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
  // Local state
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // Ref to preserve previous data during loading to prevent flicker
  const previousDataRef = useRef<typeof groupsData | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");

  // Sorting
  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<GroupOrderField>({
    initialSort: null,
    onSortChange: () => {
      // Reset cursor when sorting changes
      if (after !== null) {
        setAfter(null);
      }
    },
  });

  // GraphQL hooks
  // Note: orderBy is added to the query but generated types may not include it yet
  // Type assertion is used until GraphQL types are regenerated
  const { data: groupsData, loading, error, refetch } = useGetGroupsQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      // Cast to GraphQL type - the utility function returns compatible structure
      // but TypeScript sees them as different types due to separate type definitions
      // Also need to cast variables object since generated types may not include orderBy yet
      orderBy: (graphQLOrderBy as GroupOrderByInput[] | undefined) || undefined,
    } as any, // Type assertion needed until GraphQL types are regenerated with orderBy
    skip: false,
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
  });

  // Update previous data ref when we have new data
  useEffect(() => {
    if (groupsData && !loading) {
      previousDataRef.current = groupsData;
    }
  }, [groupsData, loading]);

  const [createGroupMutation, { loading: createLoading }] = useCreateGroupMutation();
  const [updateGroupMutation, { loading: updateLoading }] = useUpdateGroupMutation();
  const [deleteGroupMutation, { loading: deleteLoading }] = useDeleteGroupMutation();
  const [assignRoleToGroupMutation, { loading: assignRoleLoading }] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation, { loading: removeRoleLoading }] = useRemoveRoleFromGroupMutation();

  // Mutation hook with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery: GetGroupsDocument,
    refetchVariables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: (graphQLOrderBy as GroupOrderByInput[] | undefined) || undefined,
    },
    onRefetch: refetch,
    errorMessage: 'Failed to update group',
  });

  // Derived data - use previous data while loading to prevent flicker
  const groups = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = groupsData || (loading ? previousDataRef.current : null);
    return currentData?.groups?.edges?.map(e => e.node) || [];
  }, [groupsData, loading]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = groupsData || (loading ? previousDataRef.current : null);
    const info = currentData?.groups?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [groupsData, loading, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = groupsData || (loading ? previousDataRef.current : null);
    return currentData?.groups?.totalCount ?? null;
  }, [groupsData, loading]);

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
    onSearchChange: goToFirstPage,
    setSearchQuery,
    searchQuery,
  });

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

  // CRUD operations
  const createGroup = useCallback(async (data: GroupFormData) => {
    try {
      // Always include userIds - empty array is valid
      // The GraphQL schema includes userIds but generated types may not
      const input: CreateGroupInput & { userIds?: string[] } = {
        name: data.name.trim(),
        description: data.description?.trim() || null,
        userIds: data.userIds ?? [],
      };

      await executeMutation(
        createGroupMutation,
        { input },
        'create-group'
      );

      // Refetch user query to update UserDrawer when group memberships change
      // Note: This is done separately since it's not part of the main refetch
      await refetch();
      closeDialog();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to create group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, createGroupMutation, refetch, closeDialog]);

  const updateGroup = useCallback(async (groupId: string, data: GroupFormData) => {
    try {
      // Always include userIds - empty array means remove all users
      // The GraphQL schema includes userIds but generated types may not
      const input: UpdateGroupInput & { userIds?: string[] } = {
        name: data.name.trim(),
        description: data.description?.trim() || null,
        // Explicitly include userIds - empty array is valid (means remove all users)
        // undefined/null means don't change memberships (but we always want to sync)
        userIds: data.userIds ?? [],
      };

      await executeMutation(
        updateGroupMutation,
        { groupId, input },
        `update-group-${groupId}`
      );

      // Refetch user query to update UserDrawer when group memberships change
      // Note: This is done separately since it's not part of the main refetch
      await refetch();
      closeDialog();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to update group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, updateGroupMutation, refetch, closeDialog]);

  const deleteGroup = useCallback(async (groupId: string) => {
    try {
      await executeMutation(
        deleteGroupMutation,
        { groupId },
        `delete-group-${groupId}`
      );
      setDeleteConfirm(null);
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to delete group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, deleteGroupMutation]);

  const assignRoleToGroup = useCallback(
    async (groupId: string, roleId: string) => {
      await executeMutation(
        assignRoleToGroupMutation,
        { groupId, roleId },
        `assign-role-${groupId}-${roleId}`
      );
    },
    [executeMutation, assignRoleToGroupMutation]
  );

  const removeRoleFromGroup = useCallback(
    async (groupId: string, roleId: string) => {
      await executeMutation(
        removeRoleFromGroupMutation,
        { groupId, roleId },
        `remove-role-${groupId}-${roleId}`
      );
    },
    [executeMutation, removeRoleFromGroupMutation]
  );

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
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    assignRoleLoading,
    removeRoleLoading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
