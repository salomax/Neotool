"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetGroupsQuery,
  GetUserWithRelationshipsDocument,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import {
  useCreateGroupMutation,
  useUpdateGroupMutation,
  useDeleteGroupMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { CreateGroupInput, UpdateGroupInput } from '@/lib/graphql/types/__generated__/graphql';
import { GroupFieldsFragment } from '@/lib/graphql/fragments/common.generated';
import { extractErrorMessage } from '@/shared/utils/error';

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
  setFirst: (first: number) => void;
  loadNextPage: () => void;
  loadPreviousPage: () => void;
  goToFirstPage: () => void;
  
  // Search
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  
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
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  
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
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<Group | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Group | null>(null);

  // GraphQL hooks
  const { data: groupsData, loading, error, refetch } = useGetGroupsQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
    },
    skip: false,
  });

  const [createGroupMutation, { loading: createLoading }] = useCreateGroupMutation();
  const [updateGroupMutation, { loading: updateLoading }] = useUpdateGroupMutation();
  const [deleteGroupMutation, { loading: deleteLoading }] = useDeleteGroupMutation();

  // Derived data - memoize to prevent unnecessary re-renders
  const groups = useMemo(() => {
    return groupsData?.groups?.nodes || [];
  }, [groupsData?.groups?.nodes]);

  const pageInfo = useMemo(() => {
    return groupsData?.groups?.pageInfo || null;
  }, [groupsData?.groups?.pageInfo]);

  // Pagination functions
  const loadNextPage = useCallback(() => {
    if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
      setAfter(pageInfo.endCursor);
    }
  }, [pageInfo]);

  const loadPreviousPage = useCallback(() => {
    if (pageInfo?.hasPreviousPage && pageInfo?.startCursor) {
      setAfter(pageInfo.startCursor);
    }
  }, [pageInfo]);

  const goToFirstPage = useCallback(() => {
    setAfter(null);
  }, []);

  // Dialog management
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

      const result = await createGroupMutation({
        variables: { input },
        // Refetch user query to update UserDrawer when group memberships change
        refetchQueries: [GetUserWithRelationshipsDocument],
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        closeDialog();
      }
    } catch (err) {
      console.error('Error creating group:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to create group');
      throw new Error(errorMessage);
    }
  }, [createGroupMutation, refetch, closeDialog]);

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

      const result = await updateGroupMutation({
        variables: {
          groupId,
          input,
        },
        // Refetch user query to update UserDrawer when group memberships change
        refetchQueries: [GetUserWithRelationshipsDocument],
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        closeDialog();
      }
    } catch (err) {
      console.error('Error updating group:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to update group');
      throw new Error(errorMessage);
    }
  }, [updateGroupMutation, refetch, closeDialog]);

  const deleteGroup = useCallback(async (groupId: string) => {
    try {
      const result = await deleteGroupMutation({
        variables: { groupId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        setDeleteConfirm(null);
      }
    } catch (err) {
      console.error('Error deleting group:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to delete group');
      throw new Error(errorMessage);
    }
  }, [deleteGroupMutation, refetch]);

  return {
    // Data
    groups,
    
    // Pagination
    first,
    after,
    pageInfo,
    setFirst,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    
    // Search
    searchQuery,
    setSearchQuery,
    
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
    
    // Loading states
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}

