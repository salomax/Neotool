"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetPermissionsQuery,
} from '@/lib/graphql/operations/authorization-management/queries.generated';
import { extractErrorMessage } from '@/shared/utils/error';

export type Permission = {
  id: string;
  name: string;
};

export type UsePermissionManagementOptions = {
  initialSearchQuery?: string;
  initialFirst?: number;
};

export type UsePermissionManagementReturn = {
  // Data
  permissions: Permission[];
  
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
  
  // Loading states
  loading: boolean;
  
  // Error handling
  error: Error | undefined;
  
  // Utilities
  refetch: () => void;
};

/**
 * Custom hook for managing permission data and operations
 * 
 * This hook encapsulates all permission-related business logic including:
 * - Relay pagination for permission listings
 * - Search functionality (by name)
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all permission management functionality
 * 
 * @example
 * ```tsx
 * function PermissionManagementPage() {
 *   const {
 *     permissions,
 *     searchQuery,
 *     setSearchQuery,
 *     loadNextPage,
 *     loading,
 *     error
 *   } = usePermissionManagement();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchQuery} 
 *         onChange={(e) => setSearchQuery(e.target.value)} 
 *       />
 *       {permissions.map(permission => (
 *         <div key={permission.id}>{permission.name}</div>
 *       ))}
 *       {pageInfo?.hasNextPage && (
 *         <button onClick={loadNextPage}>Load More</button>
 *       )}
 *     </div>
 *   );
 * }
 * ```
 */
export function usePermissionManagement(options: UsePermissionManagementOptions = {}): UsePermissionManagementReturn {
  // Local state
  const [searchQuery, setSearchQuery] = useState(options.initialSearchQuery || "");
  const [first, setFirst] = useState(options.initialFirst || 10);
  const [after, setAfter] = useState<string | null>(null);

  // GraphQL hooks
  const { data: permissionsData, loading, error, refetch } = useGetPermissionsQuery({
    variables: {
      first,
      after: after || undefined,
      query: searchQuery || undefined,
    },
    skip: false,
  });

  // Derived data - memoize to prevent unnecessary re-renders
  const permissions = useMemo(() => {
    return permissionsData?.permissions?.nodes || [];
  }, [permissionsData?.permissions?.nodes]);

  const pageInfo = useMemo(() => {
    return permissionsData?.permissions?.pageInfo || null;
  }, [permissionsData?.permissions?.pageInfo]);

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

  return {
    // Data
    permissions,
    
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
    
    // Loading states
    loading,
    
    // Error handling
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}

