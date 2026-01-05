/**
 * Sorting utilities for converting between frontend sort state and GraphQL orderBy format.
 *
 * The backend supports sorting by DISPLAY_NAME, EMAIL, and ENABLED fields.
 * Default sorting is DISPLAY_NAME ASC, ID ASC (ID is always added as final sort for deterministic ordering).
 */

/**
 * Frontend sort state representation.
 * null means use backend default sorting.
 */
export type UserSortState = {
  field: 'DISPLAY_NAME' | 'EMAIL' | 'ENABLED';
  direction: 'asc' | 'desc';
} | null;

/**
 * GraphQL UserOrderField enum values.
 */
export type UserOrderField = 'DISPLAY_NAME' | 'EMAIL' | 'ENABLED';

/**
 * GraphQL OrderDirection enum values.
 */
export type OrderDirection = 'ASC' | 'DESC';

/**
 * GraphQL UserOrderByInput format.
 */
export type UserOrderByInput = {
  field: UserOrderField;
  direction: OrderDirection;
};

/**
 * Converts frontend sort state to GraphQL orderBy format.
 * 
 * @param sortState - Frontend sort state (null for default)
 * @returns Array of UserOrderByInput for GraphQL, or null to use backend default
 * 
 * @example
 * ```ts
 * toGraphQLOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' })
 * // Returns: [{ field: 'DISPLAY_NAME', direction: 'ASC' }]
 * 
 * toGraphQLOrderBy(null)
 * // Returns: null (backend will use default: DISPLAY_NAME ASC, ID ASC)
 * ```
 */
export function toGraphQLOrderBy(
  sortState: UserSortState
): UserOrderByInput[] | null {
  if (sortState === null) {
    return null;
  }

  return [
    {
      field: sortState.field,
      direction: sortState.direction.toUpperCase() as OrderDirection,
    },
  ];
}

/**
 * Converts GraphQL orderBy format to frontend sort state.
 * 
 * @param orderBy - GraphQL orderBy array (null for default)
 * @returns Frontend sort state, or null if using default
 * 
 * @example
 * ```ts
 * fromGraphQLOrderBy([{ field: 'DISPLAY_NAME', direction: 'ASC' }])
 * // Returns: { field: 'DISPLAY_NAME', direction: 'asc' }
 * 
 * fromGraphQLOrderBy(null)
 * // Returns: null
 * ```
 */
export function fromGraphQLOrderBy(
  orderBy: UserOrderByInput[] | null | undefined
): UserSortState {
  if (!orderBy || orderBy.length === 0) {
    return null;
  }

  // Take the first orderBy entry (ignoring ID which is always last)
  const firstOrder = orderBy[0];
  if (!firstOrder) {
    return null;
  }

  // Validate field is one of the allowed values
  const validFields: UserOrderField[] = ['DISPLAY_NAME', 'EMAIL', 'ENABLED'];
  if (!validFields.includes(firstOrder.field as UserOrderField)) {
    return null;
  }

  return {
    field: firstOrder.field as UserOrderField,
    direction: firstOrder.direction.toLowerCase() as 'asc' | 'desc',
  };
}

/**
 * Gets the next sort state when a column header is clicked.
 * 
 * Behavior:
 * - If not currently sorted by that field: sort ASC by that field
 * - If sorted ASC by that field: sort DESC by that field
 * - If sorted DESC by that field: remove sort (set to null, use default)
 * 
 * @param currentSort - Current sort state
 * @param field - Field to sort by
 * @returns New sort state
 * 
 * @example
 * ```ts
 * getNextSortState(null, 'DISPLAY_NAME')
 * // Returns: { field: 'DISPLAY_NAME', direction: 'asc' }
 * 
 * getNextSortState({ field: 'DISPLAY_NAME', direction: 'asc' }, 'DISPLAY_NAME')
 * // Returns: { field: 'DISPLAY_NAME', direction: 'desc' }
 * 
 * getNextSortState({ field: 'DISPLAY_NAME', direction: 'desc' }, 'DISPLAY_NAME')
 * // Returns: null
 * ```
 */
export function getNextSortState(
  currentSort: UserSortState,
  field: UserOrderField
): UserSortState {
  // If not currently sorted by this field, sort ASC
  if (currentSort === null || currentSort.field !== field) {
    return { field, direction: 'asc' };
  }

  // If sorted ASC, change to DESC
  if (currentSort.direction === 'asc') {
    return { field, direction: 'desc' };
  }

  // If sorted DESC, remove sort (use default)
  return null;
}

/**
 * Frontend sort state representation for roles.
 * null means use backend default sorting.
 */
export type RoleSortState = {
  field: 'NAME';
  direction: 'asc' | 'desc';
} | null;

/**
 * GraphQL RoleOrderField enum values.
 */
export type RoleOrderField = 'NAME';

