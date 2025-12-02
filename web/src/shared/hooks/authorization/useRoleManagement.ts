"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetRolesQuery,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
  useDeleteRoleMutation,
  useAssignPermissionToRoleMutation,
  useRemovePermissionFromRoleMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { CreateRoleInput, UpdateRoleInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';

export type Role = {
  id: string;
  name: string;
};

// Note: Permission type is exported from usePermissionManagement to avoid duplication
// This type is only used internally in this hook

export type RoleFormData = {
  name: string;
};

export type UseRoleManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
};

export type UseRoleManagementReturn = {
  // Data
  roles: Role[];
  
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
  editingRole: Role | null;
  deleteConfirm: Role | null;
  openCreateDialog: () => void;
  openEditDialog: (role: Role) => void;
  closeDialog: () => void;
  setDeleteConfirm: (role: Role | null) => void;
  
  // CRUD operations
  createRole: (data: RoleFormData) => Promise<void>;
  updateRole: (roleId: string, data: RoleFormData) => Promise<void>;
  deleteRole: (roleId: string) => Promise<void>;
  
  // Permission management
  assignPermissionToRole: (roleId: string, permissionId: string) => Promise<void>;
  removePermissionFromRole: (roleId: string, permissionId: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignPermissionLoading: boolean;
  removePermissionLoading: boolean;
  
  // Error handling
  error: Error | undefined;
  
  // Utilities
  refetch: () => void;
};

/**
 * Custom hook for managing role data and operations
 * 
 * This hook encapsulates all role-related business logic including:
 * - Relay pagination for role listings
 * - Search functionality (by name)
 * - CRUD operations (Create, Read, Update, Delete)
 * - Permission assignment and removal
 * - Dialog state management
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all role management functionality
 * 
 * @example
 * ```tsx
 * function RoleManagementPage() {
 *   const {
 *     roles,
 *     searchQuery,
 *     setSearchQuery,
 *     createRole,
 *     updateRole,
 *     deleteRole,
 *     assignPermissionToRole,
 *     loading,
 *     error
 *   } = useRoleManagement();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchQuery} 
 *         onChange={(e) => setSearchQuery(e.target.value)} 
 *       />
 *       {roles.map(role => (
 *         <div key={role.id}>{role.name}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useRoleManagement(options: UseRoleManagementOptions = {}): UseRoleManagementReturn {
  // Local state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Role | null>(null);

  // GraphQL hooks
  const { data: rolesData, loading, error, refetch } = useGetRolesQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
    },
    skip: false,
  });

  const [createRoleMutation, { loading: createLoading }] = useCreateRoleMutation();
  const [updateRoleMutation, { loading: updateLoading }] = useUpdateRoleMutation();
  const [deleteRoleMutation, { loading: deleteLoading }] = useDeleteRoleMutation();
  const [assignPermissionMutation, { loading: assignPermissionLoading }] = useAssignPermissionToRoleMutation();
  const [removePermissionMutation, { loading: removePermissionLoading }] = useRemovePermissionFromRoleMutation();

  // Derived data - memoize to prevent unnecessary re-renders
  const roles = useMemo(() => {
    return rolesData?.roles?.nodes || [];
  }, [rolesData?.roles?.nodes]);

  const pageInfo = useMemo(() => {
    return rolesData?.roles?.pageInfo || null;
  }, [rolesData?.roles?.pageInfo]);

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
    setEditingRole(null);
    setDialogOpen(true);
  }, []);

  const openEditDialog = useCallback((role: Role) => {
    setEditingRole(role);
    setDialogOpen(true);
  }, []);

  const closeDialog = useCallback(() => {
    setDialogOpen(false);
    setEditingRole(null);
  }, []);

  // CRUD operations
  const createRole = useCallback(async (data: RoleFormData) => {
    try {
      const input: CreateRoleInput = {
        name: data.name.trim(),
      };

      const result = await createRoleMutation({
        variables: { input },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        closeDialog();
      }
    } catch (err) {
      console.error('Error creating role:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to create role');
      throw new Error(errorMessage);
    }
  }, [createRoleMutation, refetch, closeDialog]);

  const updateRole = useCallback(async (roleId: string, data: RoleFormData) => {
    try {
      const input: UpdateRoleInput = {
        name: data.name.trim(),
      };

      const result = await updateRoleMutation({
        variables: {
          roleId,
          input,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        closeDialog();
      }
    } catch (err) {
      console.error('Error updating role:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to update role');
      throw new Error(errorMessage);
    }
  }, [updateRoleMutation, refetch, closeDialog]);

  const deleteRole = useCallback(async (roleId: string) => {
    try {
      const result = await deleteRoleMutation({
        variables: { roleId },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
        setDeleteConfirm(null);
      }
    } catch (err) {
      console.error('Error deleting role:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to delete role');
      throw new Error(errorMessage);
    }
  }, [deleteRoleMutation, refetch]);

  // Permission management
  const assignPermissionToRole = useCallback(async (roleId: string, permissionId: string) => {
    try {
      const result = await assignPermissionMutation({
        variables: {
          roleId,
          permissionId,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error assigning permission to role:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to assign permission to role');
      throw new Error(errorMessage);
    }
  }, [assignPermissionMutation, refetch]);

  const removePermissionFromRole = useCallback(async (roleId: string, permissionId: string) => {
    try {
      const result = await removePermissionMutation({
        variables: {
          roleId,
          permissionId,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error removing permission from role:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to remove permission from role');
      throw new Error(errorMessage);
    }
  }, [removePermissionMutation, refetch]);

  return {
    // Data
    roles,
    
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
    editingRole,
    deleteConfirm,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    setDeleteConfirm,
    
    // CRUD operations
    createRole,
    updateRole,
    deleteRole,
    
    // Permission management
    assignPermissionToRole,
    removePermissionFromRole,
    
    // Loading states
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    assignPermissionLoading,
    removePermissionLoading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}

