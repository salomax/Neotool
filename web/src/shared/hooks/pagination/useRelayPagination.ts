"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import type { PaginationRangeData } from "@/shared/components/ui/pagination";

export interface PageInfo {
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  startCursor: string | null;
  endCursor: string | null;
}

export interface UseRelayPaginationOptions {
  initialAfter?: string | null;
  initialSearchQuery?: string;
}

export interface UseRelayPaginationReturn {
  // Cursor state
  after: string | null;
  setAfter: (cursor: string | null) => void;
  
  // Pagination functions
  loadNextPage: () => void;
  loadPreviousPage: () => void;
  goToFirstPage: () => void;
  
  // Pagination data
  paginationRange: PaginationRangeData;
}

/**
 * Shared hook for Relay-style cursor-based pagination
 * 
 * This hook manages cursor history, cumulative item counting, and pagination state
 * for GraphQL Relay connections. It can be used with any Relay connection that
 * follows the Relay pagination spec.
 * 
 * @param items - Array of items from the current page
 * @param pageInfo - PageInfo object from Relay connection
 * @param totalCount - Total count of items (can be null)
 * @param searchQuery - Current search query (used to reset pagination on search change)
 * @param options - Configuration options
 * @returns Pagination state and functions
 * 
 * @example
 * ```tsx
 * function MyComponent() {
 *   const [after, setAfter] = useState<string | null>(null);
 *   const { data } = useQuery(GET_ITEMS, { variables: { after } });
 *   
 *   const pagination = useRelayPagination({
 *     items: data?.items?.nodes || [],
 *     pageInfo: data?.items?.pageInfo,
 *     totalCount: data?.items?.totalCount,
 *     searchQuery: searchQuery,
 *     after,
 *     setAfter,
 *   });
 *   
 *   return (
 *     <div>
 *       {data?.items?.nodes.map(item => <div key={item.id}>{item.name}</div>)}
 *       <RelayPagination
 *         pageInfo={pagination.pageInfo}
 *         paginationRange={pagination.paginationRange}
 *         onLoadNext={pagination.loadNextPage}
 *         onLoadPrevious={pagination.loadPreviousPage}
 *         onGoToFirst={pagination.goToFirstPage}
 *       />
 *     </div>
 *   );
 * }
 * ```
 */
export function useRelayPagination<T>(
  items: T[],
  pageInfo: PageInfo | null,
  totalCount: number | null,
  searchQuery: string,
  after: string | null,
  setAfter: (cursor: string | null) => void,
  options: UseRelayPaginationOptions = {}
): UseRelayPaginationReturn {
  const [cumulativeItemsLoaded, setCumulativeItemsLoaded] = useState(0);
  const historyAfterRef = useRef<string | null>(options.initialAfter || null);
  const historySearchQueryRef = useRef<string>(options.initialSearchQuery || "");
  const paginationAfterRef = useRef<string | null>(options.initialAfter || null);
  const paginationSearchQueryRef = useRef<string>(options.initialSearchQuery || "");
  const previousCumulativeRef = useRef<number>(0);
  // Cursor history for backward navigation (null represents first page)
  const cursorHistoryRef = useRef<(string | null)[]>([]);
  // Track if we're navigating backward to prevent cumulative count from being overridden
  const isNavigatingBackwardRef = useRef<boolean>(false);

  // Track cursor history for backward navigation
  useEffect(() => {
    const previousAfter = historyAfterRef.current;
    const previousSearch = historySearchQueryRef.current;
    const cursorChanged = previousAfter !== after;
    const searchChanged = previousSearch !== searchQuery;
    
    // When navigating forward, save the current cursor to history
    if (
      cursorChanged &&
      after !== null &&
      !isNavigatingBackwardRef.current
    ) {
      // Moving forward to a new page - save the previous cursor (can be null for first page)
      cursorHistoryRef.current.push(previousAfter);
    }
    
    // Reset history when going to first page or search changes
    if (after === null || searchChanged) {
      cursorHistoryRef.current = [];
    }
    
    historyAfterRef.current = after;
    historySearchQueryRef.current = searchQuery;
  }, [after, searchQuery]);

  // Track cumulative items loaded for pagination range calculation
  // For cursor-based pagination, we track items loaded in the current "session"
  // (since last reset). This works for forward navigation and first page.
  useEffect(() => {
    // Only update when we have actual data
    if (items.length === 0 && after !== null) {
      // Still loading, don't update yet
      return;
    }

    const previousSearch = paginationSearchQueryRef.current;
    const previousAfter = paginationAfterRef.current;
    const searchChanged = previousSearch !== searchQuery;
    const wentToFirstPage = previousAfter !== null && after === null;
    const cursorChanged = previousAfter !== after;
    
    // Reset cumulative count when search changes or going to first page
    if (searchChanged || wentToFirstPage) {
      const newCumulative = items.length;
      setCumulativeItemsLoaded(newCumulative);
      previousCumulativeRef.current = newCumulative;
      paginationSearchQueryRef.current = searchQuery;
      paginationAfterRef.current = after;
      isNavigatingBackwardRef.current = false;
      return;
    }
    
    // Handle cursor changes (pagination navigation)
    if (cursorChanged) {
      if (after === null) {
        // First page - set count to current items
        const newCumulative = items.length;
        setCumulativeItemsLoaded(newCumulative);
        previousCumulativeRef.current = newCumulative;
        isNavigatingBackwardRef.current = false;
      } else if (isNavigatingBackwardRef.current) {
        // We're navigating backward - cumulative count was already adjusted in loadPreviousPage
        // Don't recalculate - just sync the ref to match the adjusted state
        previousCumulativeRef.current = cumulativeItemsLoaded;
        isNavigatingBackwardRef.current = false;
      } else {
        // Moving forward to a new page (not first page)
        // The ref should have the cumulative count from BEFORE the cursor changed
        // Use the current state value if ref is 0 (shouldn't happen, but defensive)
        const previousCumulative = previousCumulativeRef.current || cumulativeItemsLoaded;
        const newCumulative = previousCumulative > 0 
          ? previousCumulative + items.length 
          : items.length;
        setCumulativeItemsLoaded(newCumulative);
        // Update ref for next cursor change
        previousCumulativeRef.current = newCumulative;
      }
    } else if (after === null && items.length > 0 && cumulativeItemsLoaded === 0) {
      // Initial load on first page
      const newCumulative = items.length;
      setCumulativeItemsLoaded(newCumulative);
      previousCumulativeRef.current = newCumulative;
    }
    
    // Always keep ref in sync when cursor is stable (not changing)
    // This ensures the ref has the correct value for the next cursor change
    if (!cursorChanged && cumulativeItemsLoaded > 0) {
      previousCumulativeRef.current = cumulativeItemsLoaded;
    }
    
    paginationSearchQueryRef.current = searchQuery;
    paginationAfterRef.current = after;
  }, [items.length, after, searchQuery, cumulativeItemsLoaded]);

  // Calculate pagination range
  const paginationRange = useMemo(() => {
    if (items.length === 0) {
      return { start: 0, end: 0, total: totalCount };
    }
    
    // Calculate range based on cumulative items loaded
    const start = cumulativeItemsLoaded > 0 ? cumulativeItemsLoaded - items.length + 1 : 1;
    const end = cumulativeItemsLoaded > 0 ? cumulativeItemsLoaded : items.length;
    
    return {
      start: Math.max(1, start),
      end: Math.max(1, end),
      total: totalCount,
    };
  }, [items.length, cumulativeItemsLoaded, totalCount]);

  // Pagination functions
  const loadNextPage = useCallback(() => {
    if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
      setAfter(pageInfo.endCursor);
    }
  }, [pageInfo, setAfter]);

  const goToFirstPage = useCallback(() => {
    // Reset cumulative count immediately to avoid race conditions
    setCumulativeItemsLoaded(0);
    setAfter(null);
    cursorHistoryRef.current = []; // Clear history when going to first page
    isNavigatingBackwardRef.current = false;
  }, [setAfter]);

  const loadPreviousPage = useCallback(() => {
    if (pageInfo?.hasPreviousPage) {
      // Pop the last cursor from history to go back
      const previousCursor = cursorHistoryRef.current.pop();
      
      if (previousCursor !== undefined) {
        // Capture current page size before navigating
        const currentPageSize = items.length;
        // Mark that we're navigating backward
        isNavigatingBackwardRef.current = true;
        // Adjust cumulative count BEFORE setting the cursor
        // This ensures the useEffect respects our adjustment
        setCumulativeItemsLoaded(prev => Math.max(0, prev - currentPageSize));
        // Use the previous cursor
        setAfter(previousCursor);
      } else if (after !== null) {
        // If no history but we're not on first page, go to first page
        goToFirstPage();
      }
    }
  }, [pageInfo, after, items.length, goToFirstPage, setAfter]);

  return {
    after,
    setAfter,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
  };
}
