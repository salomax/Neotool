"use client";

import { useCallback } from "react";
import { useMutation } from "@apollo/client/react";
import {
  CreateGroupDocument,
  UpdateGroupDocument,
  DeleteGroupDocument,
  AssignRoleToGroupDocument,
  RemoveRoleFromGroupDocument,
  type CreateGroupMutation,
  type CreateGroupMutationVariables,
  type UpdateGroupMutation,
  type UpdateGroupMutationVariables,
  type DeleteGroupMutation,
  type DeleteGroupMutationVariables,
  type AssignRoleToGroupMutation,
  type AssignRoleToGroupMutationVariables,
  type RemoveRoleFromGroupMutation,
  type RemoveRoleFromGroupMutationVariables,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { CreateGroupInput, UpdateGroupInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { DocumentNode } from '@apollo/client';
import type { Group, GroupFormData } from './useGroupManagement';

/**
 * Options for useGroupMutations hook
 */
export interface UseGroupMutationsOptions {
  /**
   * Query document to refetch after successful mutations
   */
  refetchQuery?: DocumentNode;
  /**
   * Variables for the refetch query
   */
  refetchVariables?: Record<string, any>;
  /**
   * Callback to refetch (alternative to refetchQuery)
   */
  onRefetch?: () => void;
  /**
   * Callback called after successful group creation/update (for UI updates like closing dialogs)
   */
  onGroupSaved?: () => void;
  /**
   * Callback called after successful group deletion (for UI updates like closing confirmation)
   */
  onGroupDeleted?: () => void;
}

/**
 * Return type for useGroupMutations hook
 */
export interface UseGroupMutationsReturn {
  // CRUD operations
  createGroup: (data: GroupFormData) => Promise<void>;
  updateGroup: (groupId: string, data: GroupFormData) => Promise<void>;
  deleteGroup: (groupId: string) => Promise<void>;
  
  // Role management
  assignRoleToGroup: (groupId: string, roleId: string) => Promise<void>;
  removeRoleFromGroup: (groupId: string, roleId: string) => Promise<void>;
  
  // Loading states
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignRoleLoading: boolean;
  removeRoleLoading: boolean;
}

/**
 * Hook for group mutations
 * 
 * Provides all mutation functions for group management without the query logic.
 * This allows components like drawers to use mutations without triggering queries.
 * 
 * @param options - Configuration options
 * @returns Object containing all group mutation functions and loading states
 * 
 * @example
 * ```tsx
 * function GroupDrawer() {
 *   const {
 *     createGroup,
 *     updateGroup,
 *     createLoading,
 *     updateLoading,
 *   } = useGroupMutations({
 *     onRefetch: () => refetchGroups(),
 *   });
 * }
 * ```
 */
export function useGroupMutations(
  options: UseGroupMutationsOptions = {}
): UseGroupMutationsReturn {
  const {
    refetchQuery,
    refetchVariables,
    onRefetch,
    onGroupSaved,
    onGroupDeleted,
  } = options;

  // Mutation hooks
  const [createGroupMutation, { loading: createLoading }] = useMutation<
    CreateGroupMutation,
    CreateGroupMutationVariables
  >(CreateGroupDocument);
  const [updateGroupMutation, { loading: updateLoading }] = useMutation<
    UpdateGroupMutation,
    UpdateGroupMutationVariables
  >(UpdateGroupDocument);
  const [deleteGroupMutation, { loading: deleteLoading }] = useMutation<
    DeleteGroupMutation,
    DeleteGroupMutationVariables
  >(DeleteGroupDocument);
  const [assignRoleToGroupMutation, { loading: assignRoleLoading }] =
    useMutation<
      AssignRoleToGroupMutation,
      AssignRoleToGroupMutationVariables
    >(AssignRoleToGroupDocument);
  const [removeRoleFromGroupMutation, { loading: removeRoleLoading }] =
    useMutation<
      RemoveRoleFromGroupMutation,
      RemoveRoleFromGroupMutationVariables
    >(RemoveRoleFromGroupDocument);

  // Mutation hook with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery,
    refetchVariables,
    onRefetch,
    errorMessage: 'Failed to update group',
  });

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
      onGroupSaved?.();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to create group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, createGroupMutation, onGroupSaved]);

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
      onGroupSaved?.();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to update group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, updateGroupMutation, onGroupSaved]);

  const deleteGroup = useCallback(async (groupId: string) => {
    try {
      await executeMutation(
        deleteGroupMutation,
        { groupId },
        `delete-group-${groupId}`
      );
      onGroupDeleted?.();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to delete group');
      throw new Error(errorMessage);
    }
  }, [executeMutation, deleteGroupMutation, onGroupDeleted]);

  // Role management
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
    // CRUD operations
    createGroup,
    updateGroup,
    deleteGroup,
    
    // Role management
    assignRoleToGroup,
    removeRoleFromGroup,
    
    // Loading states
    createLoading,
    updateLoading,
    deleteLoading,
    assignRoleLoading,
    removeRoleLoading,
  };
}
