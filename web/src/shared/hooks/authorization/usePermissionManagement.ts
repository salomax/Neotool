"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
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
  const previousSearchQueryRef = useRef<string>(options.initialSearchQuery || "");
  const previousAfterRef = useRef<string | null>(null);
  // Cursor history for backward navigation
  const cursorHistoryRef = useRef<string[]>([]);

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
    return permissionsData?.permissions?.edges?.map(e => e.node) || [];
  }, [permissionsData?.permissions?.edges]);

  const pageInfo = useMemo(() => {
    return permissionsData?.permissions?.pageInfo || null;
  }, [permissionsData?.permissions?.pageInfo]);

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

  // Pagination functions
  const loadNextPage = useCallback(() => {
    if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
      setAfter(pageInfo.endCursor);
    }
  }, [pageInfo]);

  const goToFirstPage = useCallback(() => {
    setAfter(null);
    cursorHistoryRef.current = []; // Clear history when going to first page
  }, []);

  const loadPreviousPage = useCallback(() => {
    if (pageInfo?.hasPreviousPage) {
      // Pop the last cursor from history to go back
      const previousCursor = cursorHistoryRef.current.pop();
      
      if (previousCursor !== undefined) {
        // Use the previous cursor
        setAfter(previousCursor);
      } else if (after !== null) {
        // If no history but we're not on first page, go to first page
        goToFirstPage();
      }
    }
  }, [pageInfo, after, goToFirstPage]);

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

