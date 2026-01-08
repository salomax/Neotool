"use client";

import { useState, useMemo, useCallback, useEffect, useRef, startTransition } from "react";
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useDebouncedSearch } from '@/shared/hooks/search';
import { useSorting } from '@/shared/hooks/sorting';
import { extractErrorMessage } from '@/shared/utils/error';
import type { PageInfo } from '@/shared/hooks/pagination';
import type { PaginationRangeData } from '@/shared/components/ui/pagination';
import type { SortState } from '@/shared/hooks/sorting';

/**
 * Generic Relay connection data structure
 */
export interface RelayConnection<T> {
  edges?: Array<{ node: T }> | null;
  pageInfo?: PageInfo | null;
  totalCount?: number | null;
}

/**
 * Options for useRelayConnectionManagement hook
 */
export interface UseRelayConnectionManagementOptions<TData, TItem, TOrderField extends string> {
  /**
   * Initial search query
   */
  initialSearchQuery?: string;
  /**
   * Initial page size (first parameter)
   */
  initialFirst?: number;
  /**
   * GraphQL query data from Apollo (should be passed reactively as it changes)
   */
  data: TData | undefined;
  /**
   * Loading state from Apollo (should be passed reactively as it changes)
   */
  loading: boolean;
  /**
   * Error from Apollo (should be passed reactively as it changes)
   */
  error?: any;
  /**
   * Refetch function from Apollo
   */
  refetch: () => void;
  /**
   * Function to extract the connection from the query data
   * Example: (data) => data?.users for a users query
   */
  getConnection: (data: TData) => RelayConnection<TItem> | null | undefined;
  /**
   * Function to create stable empty array (to prevent re-renders)
   * Should return the same array reference on each call
   * Example: () => [] or use a module-level constant
   */
  createEmptyArray: () => TItem[];
  /**
   * Initial sort state
   */
  initialSort?: SortState<TOrderField> | null;
}

/**
 * Return type for useRelayConnectionManagement hook
 */
export interface UseRelayConnectionManagementReturn<TItem, TOrderField extends string> {
  // Data
  items: TItem[];
  
  // Pagination
  first: number;
  after: string | null;
  pageInfo: PageInfo | null;
  totalCount: number | null;
  paginationRange: PaginationRangeData;
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
  orderBy: SortState<TOrderField>;
  setOrderBy: (orderBy: SortState<TOrderField>) => void;
  handleSort: (field: TOrderField) => void;
  graphQLOrderBy: Array<{ field: string; direction: string }> | null;
  
  // Loading and error
  loading: boolean;
  error: Error | undefined;
  
  // Utilities
  refetch: () => void;
}

/**
 * Generic hook for managing Relay connection queries with pagination, search, and sorting.
 * 
 * This hook encapsulates the common patterns used across all management hooks:
 * - Relay pagination (cursor-based)
 * - Debounced search
 * - Sorting with GraphQL orderBy conversion
 * - Previous data preservation to prevent flicker
 * - Stable empty array references
 * 
 * @param options - Configuration options
 * @returns Object containing all connection management functionality
 * 
 * @example
 * ```tsx
 * type UserOrderField = 'DISPLAY_NAME' | 'EMAIL';
 * 
 * function useUserManagement() {
 *   const { data, loading, error, refetch } = useGetUsersQuery({ ... });
 *   
 *   return useRelayConnectionManagement({
 *     data,
 *     loading,
 *     error,
 *     refetch,
 *     getConnection: (data) => data?.users,
 *     createEmptyArray: () => [],
 *     initialSort: null,
 *   });
 * }
 * ```
 */
export function useRelayConnectionManagement<TData, TItem, TOrderField extends string>(
  options: UseRelayConnectionManagementOptions<TData, TItem, TOrderField>
): UseRelayConnectionManagementReturn<TItem, TOrderField> {
  const {
    initialSearchQuery = "",
    initialFirst = 10,
    data,
    loading,
    error,
    refetch,
    getConnection,
    createEmptyArray,
    initialSort = null,
  } = options;

  // Local state
  const [first, setFirst] = useState(initialFirst);
  const [after, setAfter] = useState<string | null>(null);

  // State to preserve previous data during loading to prevent flicker
  const [previousData, setPreviousData] = useState<TData | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState(initialSearchQuery);

  // Sorting - reset cursor when sorting changes
  const handleSortChange = useCallback(() => {
    if (after !== null) {
      setAfter(null);
    }
  }, [after]);

  const { orderBy, graphQLOrderBy, setOrderBy, handleSort } = useSorting<TOrderField>({
    initialSort,
    onSortChange: handleSortChange,
  });

  // Update previous data state when we have new data
  // Set it whenever we have data (even if still loading) to prevent flicker
  // Use startTransition to mark as non-urgent and avoid cascading renders
  useEffect(() => {
    if (data) {
      startTransition(() => {
        setPreviousData(data);
      });
    }
  }, [data]);

  // Get current data (use previous data while loading to prevent flicker)
  const currentData = useMemo(() => {
    return data || (loading ? previousData : null);
  }, [data, loading, previousData]);

  // Extract connection from current data
  const connection = useMemo(() => {
    if (!currentData) return null;
    return getConnection(currentData);
  }, [currentData, getConnection]);

  // Stable empty array reference (memoized to prevent re-creation)
  // createEmptyArray should be stable (typically a constant function)
  const emptyArray = useMemo(() => createEmptyArray(), [createEmptyArray]);

  // Derived data - use previous data while loading to prevent flicker
  const items = useMemo(() => {
    const edges = connection?.edges;
    if (!edges || edges.length === 0) {
      return emptyArray;
    }
    return edges.map(e => e.node);
  }, [connection, emptyArray]);

  const pageInfo = useMemo(() => {
    const info = connection?.pageInfo || null;
    if (!info) {
      return null;
    }
    return {
      ...info,
      hasPreviousPage: info.hasPreviousPage || after !== null,
    };
  }, [connection, after]);

  // totalCount is always calculated by the backend (never null)
  // When query is null/empty: totalCount = total count of all items
  // When query is provided: totalCount = total count of filtered items
  const totalCount = useMemo(() => {
    return connection?.totalCount ?? null;
  }, [connection]);

  // Use shared pagination hook
  const {
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  } = useRelayPagination(
    items,
    pageInfo,
    totalCount,
    searchQuery,
    after,
    setAfter,
    {
      initialAfter: null,
      initialSearchQuery,
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

  return {
    // Data
    items,
    
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
    graphQLOrderBy,
    
    // Loading and error
    loading,
    error: error ? new Error(extractErrorMessage(error)) : undefined,
    
    // Utilities
    refetch,
  };
}

