"use client";

import { useCallback } from "react";
import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
  useDeleteRoleMutation,
  useAssignPermissionToRoleMutation,
  useRemovePermissionFromRoleMutation,
  useAssignRoleToUserMutation,
  useRemoveRoleFromUserMutation,
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { CreateRoleInput, UpdateRoleInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { DocumentNode } from '@apollo/client';
import type { Role, RoleFormData } from './useRoleManagement';

/**
 * Options for useRoleMutations hook
 */
export interface UseRoleMutationsOptions {
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
   * Callback called after successful role creation/update (for UI updates like closing dialogs)
   */
  onRoleSaved?: () => void;
  /**
   * Callback called after successful role deletion (for UI updates like closing confirmation)
   */
  onRoleDeleted?: () => void;
}

/**
 * Return type for useRoleMutations hook
 */
export interface UseRoleMutationsReturn {
  // CRUD operations
  createRole: (data: RoleFormData) => Promise<Role>;
  updateRole: (roleId: string, data: RoleFormData) => Promise<void>;
  deleteRole: (roleId: string) => Promise<void>;
  
  // Permission management
  assignPermissionToRole: (roleId: string, permissionId: string) => Promise<void>;
  removePermissionFromRole: (roleId: string, permissionId: string) => Promise<void>;
  
  // User and group management
  assignRoleToUser: (userId: string, roleId: string) => Promise<void>;
  removeRoleFromUser: (userId: string, roleId: string) => Promise<void>;
  assignRoleToGroup: (groupId: string, roleId: string) => Promise<void>;
  removeRoleFromGroup: (groupId: string, roleId: string) => Promise<void>;
  
  // Loading states
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignPermissionLoading: boolean;
  removePermissionLoading: boolean;
  assignRoleToUserLoading: boolean;
  removeRoleFromUserLoading: boolean;
  assignRoleToGroupLoading: boolean;
  removeRoleFromGroupLoading: boolean;
}

/**
 * Hook for role mutations
 * 
 * Provides all mutation functions for role management without the query logic.
 * This allows components like drawers to use mutations without triggering queries.
 * 
 * @param options - Configuration options
 * @returns Object containing all role mutation functions and loading states
 * 
 * @example
 * ```tsx
 * function RoleDrawer() {
 *   const {
 *     createRole,
 *     updateRole,
 *     createLoading,
 *     updateLoading,
 *   } = useRoleMutations({
 *     onRefetch: () => refetchRoles(),
 *   });
 * }
 * ```
 */
export function useRoleMutations(
  options: UseRoleMutationsOptions = {}
): UseRoleMutationsReturn {
  const {
    refetchQuery,
    refetchVariables,
    onRefetch,
    onRoleSaved,
    onRoleDeleted,
  } = options;

  // Mutation hooks
  const [createRoleMutation, { loading: createLoading }] = useCreateRoleMutation();
  const [updateRoleMutation, { loading: updateLoading }] = useUpdateRoleMutation();
  const [deleteRoleMutation, { loading: deleteLoading }] = useDeleteRoleMutation();
  const [assignPermissionMutation, { loading: assignPermissionLoading }] = useAssignPermissionToRoleMutation();
  const [removePermissionMutation, { loading: removePermissionLoading }] = useRemovePermissionFromRoleMutation();
  const [assignRoleToUserMutation, { loading: assignRoleToUserLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleFromUserLoading }] = useRemoveRoleFromUserMutation();
  const [assignRoleToGroupMutation, { loading: assignRoleToGroupLoading }] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation, { loading: removeRoleFromGroupLoading }] = useRemoveRoleFromGroupMutation();

  // Mutation hook with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery,
    refetchVariables,
    onRefetch,
    errorMessage: 'Failed to update role',
  });

  // CRUD operations
  const createRole = useCallback(async (data: RoleFormData): Promise<Role> => {
    try {
      const input: CreateRoleInput = {
        name: data.name.trim(),
      };

      const result = await executeMutation(
        createRoleMutation,
        { input },
        'create-role'
      );

      if (result.data?.createRole) {
        const createdRole: Role = {
          id: result.data.createRole.id,
          name: result.data.createRole.name,
        };
        onRoleSaved?.();
        return createdRole;
      }
      throw new Error('Failed to create role: no data returned');
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to create role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, createRoleMutation, onRoleSaved]);

  const updateRole = useCallback(async (roleId: string, data: RoleFormData) => {
    try {
      const input: UpdateRoleInput = {
        name: data.name.trim(),
      };

      await executeMutation(
        updateRoleMutation,
        { roleId, input },
        `update-role-${roleId}`
      );
      onRoleSaved?.();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to update role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, updateRoleMutation, onRoleSaved]);

  const deleteRole = useCallback(async (roleId: string) => {
    try {
      await executeMutation(
        deleteRoleMutation,
        { roleId },
        `delete-role-${roleId}`
      );
      onRoleDeleted?.();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to delete role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, deleteRoleMutation, onRoleDeleted]);

  // Permission management
  const assignPermissionToRole = useCallback(
    async (roleId: string, permissionId: string) => {
      await executeMutation(
        assignPermissionMutation,
        { roleId, permissionId },
        `assign-permission-${roleId}-${permissionId}`
      );
    },
    [executeMutation, assignPermissionMutation]
  );

  const removePermissionFromRole = useCallback(
    async (roleId: string, permissionId: string) => {
      await executeMutation(
        removePermissionMutation,
        { roleId, permissionId },
        `remove-permission-${roleId}-${permissionId}`
      );
    },
    [executeMutation, removePermissionMutation]
  );

  // User and group management
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
    createRole,
    updateRole,
    deleteRole,
    
    // Permission management
    assignPermissionToRole,
    removePermissionFromRole,
    
    // User and group management
    assignRoleToUser,
    removeRoleFromUser,
    assignRoleToGroup,
    removeRoleFromGroup,
    
    // Loading states
    createLoading,
    updateLoading,
    deleteLoading,
    assignPermissionLoading,
    removePermissionLoading,
    assignRoleToUserLoading,
    removeRoleFromUserLoading,
    assignRoleToGroupLoading,
    removeRoleFromGroupLoading,
  };
}

