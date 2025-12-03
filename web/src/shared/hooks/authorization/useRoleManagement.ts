"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import {
  useGetRolesQuery,
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
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);
  const [cumulativeItemsLoaded, setCumulativeItemsLoaded] = useState(0);
  const previousSearchQueryRef = useRef<string>(options.initialSearchQuery || "");
  const previousAfterRef = useRef<string | null>(null);
  // Cursor history for backward navigation
  const cursorHistoryRef = useRef<string[]>([]);
  // Track if we're navigating backward to prevent cumulative count from being overridden
  const isNavigatingBackwardRef = useRef<boolean>(false);
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
  const [assignRoleToUserMutation, { loading: assignRoleToUserLoading }] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation, { loading: removeRoleFromUserLoading }] = useRemoveRoleFromUserMutation();
  const [assignRoleToGroupMutation, { loading: assignRoleToGroupLoading }] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation, { loading: removeRoleFromGroupLoading }] = useRemoveRoleFromGroupMutation();

  // Derived data - memoize to prevent unnecessary re-renders
  const roles = useMemo(() => {
    return rolesData?.roles?.nodes || [];
  }, [rolesData?.roles?.nodes]);

  const pageInfo = useMemo(() => {
    return rolesData?.roles?.pageInfo || null;
  }, [rolesData?.roles?.pageInfo]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    return rolesData?.roles?.totalCount ?? null;
  }, [rolesData?.roles?.totalCount]);

  // Track cursor history for backward navigation
  useEffect(() => {
    // When we navigate forward (after changes from one value to another),
    // add the previous cursor to history
    if (after !== null && previousAfterRef.current !== after) {
      // Only add to history if we're actually moving forward (not resetting)
      if (previousAfterRef.current !== null) {
        cursorHistoryRef.current.push(previousAfterRef.current);
      }
    }
    
    // Reset history when going to first page or search changes
    if (after === null || previousSearchQueryRef.current !== searchQuery) {
      cursorHistoryRef.current = [];
    }
    
    previousAfterRef.current = after;
    previousSearchQueryRef.current = searchQuery;
  }, [after, searchQuery]);

  // Track cumulative items loaded for pagination range calculation
  // For cursor-based pagination, we track items loaded in the current "session"
  // (since last reset). This works for forward navigation and first page.
  useEffect(() => {
    const searchChanged = previousSearchQueryRef.current !== searchQuery;
    const wentToFirstPage = previousAfterRef.current !== null && after === null;
    const cursorChanged = previousAfterRef.current !== after;
    
    // Reset cumulative count when search changes or going to first page
    if (searchChanged || wentToFirstPage) {
      setCumulativeItemsLoaded(roles.length);
    } else if (roles.length > 0 && cursorChanged) {
      if (after === null) {
        // First page - set count to current items
        setCumulativeItemsLoaded(roles.length);
        isNavigatingBackwardRef.current = false;
      } else if (isNavigatingBackwardRef.current) {
        // We're navigating backward - cumulative count was already adjusted in loadPreviousPage
        // Just update the ref to match current state
        isNavigatingBackwardRef.current = false;
      } else {
        // Moving forward to a new page (not first page)
        const wasOnFirstPage = previousAfterRef.current === null;
        if (!wasOnFirstPage) {
          // Moving forward - add current items to cumulative count
          setCumulativeItemsLoaded(prev => prev + roles.length);
        } else {
          setCumulativeItemsLoaded(roles.length);
        }
      }
    } else if (after === null && roles.length > 0 && cumulativeItemsLoaded === 0) {
      // Initial load on first page
      setCumulativeItemsLoaded(roles.length);
    }
    
    previousSearchQueryRef.current = searchQuery;
    previousAfterRef.current = after;
  }, [roles.length, after, searchQuery, cumulativeItemsLoaded]);

  // Calculate pagination range
  const paginationRange = useMemo(() => {
    if (roles.length === 0) {
      return { start: 0, end: 0, total: totalCount };
    }
    
    // Calculate range based on cumulative items loaded
    const start = cumulativeItemsLoaded > 0 ? cumulativeItemsLoaded - roles.length + 1 : 1;
    const end = cumulativeItemsLoaded > 0 ? cumulativeItemsLoaded : roles.length;
    
    return {
      start: Math.max(1, start),
      end: Math.max(1, end),
      total: totalCount,
    };
  }, [roles.length, cumulativeItemsLoaded, totalCount]);

  // Pagination functions
  const loadNextPage = useCallback(() => {
    if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
      setAfter(pageInfo.endCursor);
    }
  }, [pageInfo]);

  const goToFirstPage = useCallback(() => {
    setAfter(null);
    setCumulativeItemsLoaded(0);
    cursorHistoryRef.current = []; // Clear history when going to first page
  }, []);

  const loadPreviousPage = useCallback(() => {
    if (pageInfo?.hasPreviousPage) {
      // Pop the last cursor from history to go back
      const previousCursor = cursorHistoryRef.current.pop();
      
      if (previousCursor !== undefined) {
        // Capture current page size before navigating
        const currentPageSize = roles.length;
        // Use the previous cursor
        setAfter(previousCursor);
        // Adjust cumulative count - subtract current page size (not first, as last page might be smaller)
        setCumulativeItemsLoaded(prev => Math.max(0, prev - currentPageSize));
      } else if (after !== null) {
        // If no history but we're not on first page, go to first page
        goToFirstPage();
      }
    }
  }, [pageInfo, after, roles.length, goToFirstPage]);

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
  const createRole = useCallback(async (data: RoleFormData): Promise<Role> => {
    try {
      const input: CreateRoleInput = {
        name: data.name.trim(),
      };

      const result = await createRoleMutation({
        variables: { input },
      });

      // Only refetch if mutation was successful
      if (result.data?.createRole) {
        const createdRole: Role = {
          id: result.data.createRole.id,
          name: result.data.createRole.name,
        };
        refetch();
        closeDialog();
        return createdRole;
      }
      throw new Error('Failed to create role: no data returned');
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

  // User and group management
  const assignRoleToUser = useCallback(async (userId: string, roleId: string) => {
    try {
      const result = await assignRoleToUserMutation({
        variables: {
          userId,
          roleId,
        },
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
        variables: {
          userId,
          roleId,
        },
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

  const assignRoleToGroup = useCallback(async (groupId: string, roleId: string) => {
    try {
      const result = await assignRoleToGroupMutation({
        variables: {
          groupId,
          roleId,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error assigning role to group:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to assign role to group');
      throw new Error(errorMessage);
    }
  }, [assignRoleToGroupMutation, refetch]);

  const removeRoleFromGroup = useCallback(async (groupId: string, roleId: string) => {
    try {
      const result = await removeRoleFromGroupMutation({
        variables: {
          groupId,
          roleId,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error removing role from group:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to remove role from group');
      throw new Error(errorMessage);
    }
  }, [removeRoleFromGroupMutation, refetch]);

  return {
    // Data
    roles,
    
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

