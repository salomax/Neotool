"use client";

import { useCallback } from "react";
import {
  useEnableUserMutation,
  useDisableUserMutation,
  useAssignGroupToUserMutation,
  useRemoveGroupFromUserMutation,
  useAssignRoleToUserMutation,
  useRemoveRoleFromUserMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { DocumentNode } from '@apollo/client';

/**
 * Options for useUserMutations hook
 */
export interface UseUserMutationsOptions {
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
}

/**
 * Return type for useUserMutations hook
 */
export interface UseUserMutationsReturn {
  // User status management
  enableUser: (userId: string) => Promise<void>;
  disableUser: (userId: string) => Promise<void>;
  
  // Group management
  assignGroupToUser: (userId: string, groupId: string) => Promise<void>;
  removeGroupFromUser: (userId: string, groupId: string) => Promise<void>;
  
  // Role management
  assignRoleToUser: (userId: string, roleId: string) => Promise<void>;
  removeRoleFromUser: (userId: string, roleId: string) => Promise<void>;
  
  // Loading states
  enableLoading: boolean;
  disableLoading: boolean;
  assignGroupLoading: boolean;
  removeGroupLoading: boolean;
  assignRoleLoading: boolean;
  removeRoleLoading: boolean;
}

/**
 * Hook for user mutations
 * 
 * Provides all mutation functions for user management without the query logic.
 * This allows components to use mutations without triggering queries.
 * 
 * @param options - Configuration options
 * @returns Object containing all user mutation functions and loading states
 * 
 * @example
 * ```tsx
 * function UserDrawer() {
 *   const {
 *     enableUser,
 *     disableUser,
 *     enableLoading,
 *     disableLoading,
 *   } = useUserMutations({
 *     onRefetch: () => refetchUsers(),
 *   });
 * }
 * ```
 */
export function useUserMutations(
  options: UseUserMutationsOptions = {}
): UseUserMutationsReturn {
  const {
    refetchQuery,
    refetchVariables,
    onRefetch,
  } = options;

  // Mutation hooks
  const [enableUserMutation, { loading: enableLoading }] = useEnableUserMutation();
  const [disableUserMutation, { loading: disableLoading }] = useDisableUserMutation();
  const [assignGroupToUserMutation, { loading: assignGroupLoading }] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation, { loading: removeGroupLoading }] = useRemoveGroupFromUserMutation();
  const [assignRoleToUserMutation, { loading: assignRoleLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleLoading }] = useRemoveRoleFromUserMutation();

  // Mutation hook with refetch
  const { executeMutation } = useMutationWithRefetch({
    refetchQuery,
    refetchVariables,
    onRefetch,
    errorMessage: 'Failed to update user',
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
    // User status management
    enableUser,
    disableUser,
    
    // Group management
    assignGroupToUser,
    removeGroupFromUser,
    
    // Role management
    assignRoleToUser,
    removeRoleFromUser,
    
    // Loading states
    enableLoading,
    disableLoading,
    assignGroupLoading,
    removeGroupLoading,
    assignRoleLoading,
    removeRoleLoading,
  };
}

