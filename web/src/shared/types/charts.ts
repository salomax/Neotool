/**
 * Shared types for chart components
 */

/**
 * Represents a single data point in a time series chart
 */
export interface TimeSeriesDataPoint {
  quarter?: string;
  value: number;
}

/**
 * Period options for chart filtering
 */
export interface PeriodOption {
  value: string;
  label: string;
  quarters: number;
}

/**
 * Standard period configurations
 */
export const PERIOD_QUARTERS: Record<string, number> = {
  "1Y": 4,
  "3Y": 12,
  "5Y": 20,
} as const;

/**
 * Get the number of quarters for a given period
 */
export function getQuartersForPeriod(period: string): number {
  return PERIOD_QUARTERS[period] ?? 20;
}
