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
  canLoadPreviousPage: boolean;
}

/**
 * Shared hook for Relay-style cursor-based pagination
 * 
 * This hook manages cursor history, range tracking, and pagination state
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
  const [rangeStart, setRangeStart] = useState(1);
  const rangeStartRef = useRef<number>(1);
  const cursorHistoryRef = useRef<{ cursor: string | null; start: number }[]>([]);
  const previousSearchQueryRef = useRef<string>(options.initialSearchQuery || "");
  const previousAfterRef = useRef<string | null>(options.initialAfter || null);

  // Reset pagination tracking when search query changes externally
  useEffect(() => {
    if (previousSearchQueryRef.current !== searchQuery) {
      cursorHistoryRef.current = [];
      rangeStartRef.current = 1;
      setRangeStart(1);
      previousSearchQueryRef.current = searchQuery;
      setAfter(null);
    }
  }, [searchQuery, setAfter]);

  // Reset pagination tracking when after cursor is reset to null externally
  // This happens when sorting changes or other operations reset pagination
  useEffect(() => {
    // If after changed from non-null to null, and we have cursor history or rangeStart > 1,
    // it means pagination was reset externally (e.g., by sorting)
    if (previousAfterRef.current !== null && after === null) {
      if (cursorHistoryRef.current.length > 0 || rangeStartRef.current > 1) {
        cursorHistoryRef.current = [];
        rangeStartRef.current = 1;
        setRangeStart(1);
      }
    }
    previousAfterRef.current = after;
  }, [after]);

  // Ensure rangeStartRef stays in sync when rangeStart changes elsewhere
  useEffect(() => {
    rangeStartRef.current = rangeStart;
  }, [rangeStart]);

  // Calculate pagination range
  const paginationRange = useMemo(() => {
    if (items.length === 0) {
      return { start: 0, end: 0, total: totalCount };
    }
    
    const start = Math.max(1, rangeStart);
    const end = Math.max(start, rangeStart + items.length - 1);
    
    return {
      start,
      end,
      total: totalCount,
    };
  }, [items.length, rangeStart, totalCount]);

  // Pagination functions
  const loadNextPage = useCallback(() => {
    if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
      const nextCursor = pageInfo.endCursor;
      const nextStart = Math.max(1, rangeStartRef.current) + items.length;
      cursorHistoryRef.current.push({
        cursor: after,
        start: rangeStartRef.current,
      });
      rangeStartRef.current = nextStart;
      setRangeStart(nextStart);
      setAfter(nextCursor);
    }
  }, [pageInfo, setAfter, items.length, after]);

  const goToFirstPage = useCallback(() => {
    cursorHistoryRef.current = [];
    rangeStartRef.current = 1;
    setRangeStart(1);
    setAfter(null);
  }, [setAfter]);

  const loadPreviousPage = useCallback(() => {
    const previousEntry = cursorHistoryRef.current.pop();

    if (previousEntry) {
      rangeStartRef.current = Math.max(1, previousEntry.start);
      setRangeStart(rangeStartRef.current);
      setAfter(previousEntry.cursor);
    }
  }, [setAfter]);

  const canLoadPreviousPage = cursorHistoryRef.current.length > 0;

  return {
    after,
    setAfter,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    paginationRange,
    canLoadPreviousPage,
  };
}
