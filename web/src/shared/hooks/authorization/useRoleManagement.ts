"use client";

import { useState, useMemo, useCallback, useEffect, useRef, startTransition } from "react";
import {
  useGetRolesQuery,
  GetRolesDocument,
  GetRolesQueryVariables,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import { RoleOrderByInput } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { useRoleMutations } from './useRoleMutations';
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
  /**
   * When true, skips executing the query (useful while waiting for dynamic sizing).
   */
  skip?: boolean;
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
  
  // Group management
  assignRoleToGroup: (groupId: string, roleId: string) => Promise<void>;
  removeRoleFromGroup: (groupId: string, roleId: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  assignPermissionLoading: boolean;
  removePermissionLoading: boolean;
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
  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<RoleOrderField>({
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
  const queryVariables = useMemo<GetRolesQueryVariables>(() => {
    const vars: GetRolesQueryVariables = {
      first: firstState,
      ...(after && { after }),
      ...(searchQuery && { query: searchQuery }),
      ...(graphQLOrderBy && graphQLOrderBy.length > 0 && { orderBy: graphQLOrderBy as RoleOrderByInput[] }),
    };
    return vars;
  }, [firstState, after, searchQuery, graphQLOrderBy]);

  // GraphQL hooks
  const { data: rolesData, loading, error, refetch } = useGetRolesQuery({
    variables: queryVariables,
    skip: waitingForPageSize,
    notifyOnNetworkStatusChange: true, // Keep loading state accurate during transitions
  });

  // Update previous data state when we have new data
  useEffect(() => {
    if (rolesData && !loading) {
      startTransition(() => {
        setPreviousData(rolesData);
      });
    }
  }, [rolesData, loading]);

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

  // Use mutation hook internally
  const {
    createRole: createRoleMutation,
    updateRole: updateRoleMutation,
    deleteRole: deleteRoleMutation,
    assignPermissionToRole,
    removePermissionFromRole,
    assignRoleToGroup,
    removeRoleFromGroup,
    createLoading,
    updateLoading,
    deleteLoading,
    assignPermissionLoading,
    removePermissionLoading,
    assignRoleToGroupLoading,
    removeRoleFromGroupLoading,
  } = useRoleMutations({
    refetchQuery: GetRolesDocument,
    refetchVariables: queryVariables,
    onRefetch: refetch,
    onRoleSaved: closeDialog,
    onRoleDeleted: () => setDeleteConfirm(null),
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
  const roles = useMemo(() => {
    // Keep previous data visible while loading new data
    const currentData = rolesData || (loading ? previousData : null);
    return currentData?.roles?.edges?.map((e: { node: Role }) => e.node) || [];
  }, [rolesData, loading, previousData]);

  const pageInfo = useMemo(() => {
    // Use previous data if current is loading
    const currentData = rolesData || (loading ? previousData : null);
    const info = currentData?.roles?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [rolesData, loading, after, previousData]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    // Use previous data if current is loading
    const currentData = rolesData || (loading ? previousData : null);
    return currentData?.roles?.totalCount ?? null;
  }, [rolesData, loading, previousData]);

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
    setSearchQuery,
    searchQuery,
  });

  const effectiveLoading = waitingForPageSize || loading;

  // Wrap mutation functions to handle dialog state
  const createRole = useCallback(async (data: RoleFormData): Promise<Role> => {
    return createRoleMutation(data);
  }, [createRoleMutation]);

  const updateRole = useCallback(async (roleId: string, data: RoleFormData) => {
    return updateRoleMutation(roleId, data);
  }, [updateRoleMutation]);

  const deleteRole = useCallback(async (roleId: string) => {
    return deleteRoleMutation(roleId);
  }, [deleteRoleMutation]);

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
    
    // Group management
    assignRoleToGroup,
    removeRoleFromGroup,
    
    // Loading states
    loading: effectiveLoading,
    createLoading,
    updateLoading,
    deleteLoading,
    assignPermissionLoading,
    removePermissionLoading,
    assignRoleToGroupLoading,
    removeRoleFromGroupLoading,
    
    // Error handling
    error: waitingForPageSize ? undefined : error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}
