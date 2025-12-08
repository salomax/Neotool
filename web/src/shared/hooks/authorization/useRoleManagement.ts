"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import {
  useGetRolesQuery,
  GetRolesDocument,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
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
import { CreateRoleInput, UpdateRoleInput, RoleOrderByInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import type { RoleSortState, RoleOrderField } from '@/shared/utils/sorting';

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
  orderBy: RoleSortState;
  setOrderBy: (orderBy: RoleSortState) => void;
  handleSort: (field: RoleOrderField) => void;
  
  // Dialog state
  dialogOpen: boolean;
  editingRole: Role | null;
  deleteConfirm: Role | null;
  openCreateDialog: () => void;
  openEditDialog: (role: Role) => void;
  closeDialog: () => void;
  setDeleteConfirm: (role: Role | null) => void;
  
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
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignPermissionLoading: boolean;
  removePermissionLoading: boolean;
  assignRoleToUserLoading: boolean;
  removeRoleFromUserLoading: boolean;
  assignRoleToGroupLoading: boolean;
  removeRoleFromGroupLoading: boolean;
  
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
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // Ref to preserve previous data during loading to prevent flicker
  const previousDataRef = useRef<typeof rolesData | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");

  // Sorting
  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<RoleOrderField>({
    initialSort: null,
    onSortChange: () => {
      // Reset cursor when sorting changes
      if (after !== null) {
        setAfter(null);
      }
    },
  });

  // GraphQL hooks
  const { data: rolesData, loading, error, refetch } = useGetRolesQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      // Cast to GraphQL type - the utility function returns compatible structure
      // but TypeScript sees them as different types due to separate type definitions
      orderBy: (graphQLOrderBy as RoleOrderByInput[] | undefined) || undefined,
    } as any, // Type assertion needed until GraphQL types are regenerated with orderBy
    skip: false,
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
  });

  // Update previous data ref when we have new data
  useEffect(() => {
    if (rolesData && !loading) {
      previousDataRef.current = rolesData;
    }
  }, [rolesData, loading]);

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
    refetchQuery: GetRolesDocument,
    refetchVariables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
      orderBy: (graphQLOrderBy as RoleOrderByInput[] | undefined) || undefined,
    },
    onRefetch: refetch,
    errorMessage: 'Failed to update role',
  });

  // Derived data - use previous data while loading to prevent flicker
  const roles = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = rolesData || (loading ? previousDataRef.current : null);
    return currentData?.roles?.edges?.map(e => e.node) || [];
  }, [rolesData, loading]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = rolesData || (loading ? previousDataRef.current : null);
    const info = currentData?.roles?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [rolesData, loading, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = rolesData || (loading ? previousDataRef.current : null);
    return currentData?.roles?.totalCount ?? null;
  }, [rolesData, loading]);

  // Use shared pagination hook
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  } = useRelayPagination(
    roles,
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
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Role | null>(null);

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
        closeDialog();
        return createdRole;
      }
      throw new Error('Failed to create role: no data returned');
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to create role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, createRoleMutation, closeDialog]);

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
      closeDialog();
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to update role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, updateRoleMutation, closeDialog]);

  const deleteRole = useCallback(async (roleId: string) => {
    try {
      await executeMutation(
        deleteRoleMutation,
        { roleId },
        `delete-role-${roleId}`
      );
      setDeleteConfirm(null);
    } catch (err) {
      const errorMessage = extractErrorMessage(err, 'Failed to delete role');
      throw new Error(errorMessage);
    }
  }, [executeMutation, deleteRoleMutation]);

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
    // Data
    roles,
    
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
    
    // User and group management
    assignRoleToUser,
    removeRoleFromUser,
    assignRoleToGroup,
    removeRoleFromGroup,
    
    // Loading states
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    assignPermissionLoading,
    removePermissionLoading,
    assignRoleToUserLoading,
    removeRoleFromUserLoading,
    assignRoleToGroupLoading,
    removeRoleFromGroupLoading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
