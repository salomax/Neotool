/**
 * Financial metrics constants and thresholds
 */

/**
 * Basel Index (Basileia) thresholds for Brazilian financial institutions
 *
 * According to Brazilian Central Bank regulations:
 * - Below 11%: Critical (non-compliant)
 * - 11% to 14%: Warning (minimum compliance)
 * - Above 14%: Good (healthy)
 */
export const BASILEIA_THRESHOLDS = {
  critical: 11,
  warning: 14,
} as const;

/**
 * Immobilization Index thresholds for Brazilian financial institutions
 *
 * According to Brazilian Central Bank regulations:
 * - Below 40%: Good
 * - 40% to 50%: Warning
 * - Above 50%: Critical
 */
export const IMMOBILIZATION_THRESHOLDS = {
  good: 40,
  warning: 50,
  critical: 100,
} as const;
