/**
 * Customer status constants
 * Centralized status values to avoid duplication
 */
export const CUSTOMER_STATUSES = {
  ALL: 'ALL',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  PENDING: 'PENDING',
} as const;

export type CustomerStatus = typeof CUSTOMER_STATUSES[keyof typeof CUSTOMER_STATUSES];

/**
 * Default customer status
 */
export const DEFAULT_CUSTOMER_STATUS = CUSTOMER_STATUSES.ACTIVE;

/**
 * Customer status options for filters and forms
 * These map to translation keys: 'all', 'active', 'inactive', 'pending'
 */
export const CUSTOMER_STATUS_OPTIONS = [
  { value: CUSTOMER_STATUSES.ALL, labelKey: 'all' },
  { value: CUSTOMER_STATUSES.ACTIVE, labelKey: 'active' },
  { value: CUSTOMER_STATUSES.INACTIVE, labelKey: 'inactive' },
  { value: CUSTOMER_STATUSES.PENDING, labelKey: 'pending' },
] as const;

