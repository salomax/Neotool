"use client";

import { useState, useMemo, useCallback, useEffect, useRef } from "react";

/**
 * Generic sort state type
 */
export type SortState<F extends string> = {
  field: F;
  direction: "asc" | "desc";
} | null;

/**
 * GraphQL order direction
 */
export type GraphQLOrderDirection = "ASC" | "DESC";

/**
 * GraphQL order by input format
 */
export type GraphQLOrderByInput<F extends string> = {
  field: F;
  direction: GraphQLOrderDirection;
};

/**
 * Options for useSorting hook
 */
export interface UseSortingOptions<F extends string> {
  /**
   * Initial sort state
   */
  initialSort?: SortState<F> | null;
  /**
   * Callback to reset pagination when sort changes
   */
  onSortChange?: () => void;
}

/**
 * Return type for useSorting hook
 */
export interface UseSortingReturn<F extends string> {
  /**
   * Current sort state
   */
  orderBy: SortState<F>;
  /**
   * GraphQL orderBy format (converted from sort state)
   */
  graphQLOrderBy: GraphQLOrderByInput<F>[] | null;
  /**
   * Set sort state directly
   */
  setOrderBy: (orderBy: SortState<F>) => void;
  /**
   * Handle sort change (cycles through: null -> asc -> desc -> null)
   */
  handleSort: (field: F) => void;
}

/**
 * Hook for managing sort state and converting to GraphQL format.
 * 
 * Provides:
 * - Sort state management (null, asc, desc)
 * - Automatic conversion to GraphQL orderBy format
 * - Automatic pagination reset when sort changes
 * 
 * @param options - Configuration options
 * @returns Object with sort state, GraphQL format, and handlers
 * 
 * @example
 * ```tsx
 * type UserOrderField = 'DISPLAY_NAME' | 'EMAIL';
 *
 * function UserList() {
 *   const { orderBy, graphQLOrderBy, handleSort } = useSorting<UserOrderField>({
 *     initialSort: null,
 *     onSortChange: () => goToFirstPage(),
 *   });
 *
 *   return (
 *     <TableSortLabel
 *       active={orderBy?.field === 'DISPLAY_NAME'}
 *       direction={orderBy?.direction || 'asc'}
 *       onClick={() => handleSort('DISPLAY_NAME')}
 *     >
 *       Name
 *     </TableSortLabel>
 *   );
 * }
 * ```
 */
export function useSorting<F extends string>(
  options: UseSortingOptions<F> = {}
): UseSortingReturn<F> {
  const { initialSort = null, onSortChange } = options;

  const [orderBy, setOrderBy] = useState<SortState<F>>(initialSort);
  
  // Use refs to track previous values and callback
  const previousOrderByRef = useRef<SortState<F>>(initialSort);
  const onSortChangeRef = useRef(onSortChange);
  
  // Update callback ref when it changes
  useEffect(() => {
    onSortChangeRef.current = onSortChange;
  }, [onSortChange]);

  // Convert sort state to GraphQL format
  const graphQLOrderBy = useMemo(() => {
    if (orderBy === null) {
      return null;
    }

    return [
      {
        field: orderBy.field,
        direction: orderBy.direction.toUpperCase() as GraphQLOrderDirection,
      },
    ];
  }, [orderBy]);

  // Reset cursor when sorting actually changes (not on initial mount)
  useEffect(() => {
    const previousOrderBy = previousOrderByRef.current;
    const currentOrderBy = orderBy;
    
    // Only call onSortChange if the sort actually changed (not on initial mount)
    // Compare by serializing to JSON to handle object equality
    const previousStr = JSON.stringify(previousOrderBy);
    const currentStr = JSON.stringify(currentOrderBy);
    
    if (previousStr !== currentStr) {
      // Sort changed, call the callback
      onSortChangeRef.current?.();
      previousOrderByRef.current = currentOrderBy;
    }
  }, [orderBy]);

  // Handle sort change (cycles through: null -> asc -> desc -> null)
  const handleSort = useCallback(
    (field: F) => {
      // If not currently sorted by this field, sort ASC
      if (orderBy === null || orderBy.field !== field) {
        setOrderBy({ field, direction: "asc" });
        return;
      }

      // If sorted ASC, change to DESC
      if (orderBy.direction === "asc") {
        setOrderBy({ field, direction: "desc" });
        return;
      }

      // If sorted DESC, remove sort (use default)
      setOrderBy(null);
    },
    [orderBy]
  );

  return {
    orderBy,
    graphQLOrderBy,
    setOrderBy,
    handleSort,
  };
}

