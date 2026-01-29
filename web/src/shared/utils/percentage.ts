/**
 * Percentage formatting utilities
 */

/**
 * Format percentage with sign
 * Converts decimal ratio (0.12) to percentage string with sign (+12%)
 * 
 * @param value - Decimal ratio (0.12 = 12%)
 * @param options - Formatting options
 * @returns Formatted percentage string (e.g., "+12%", "-5%")
 * 
 * @example
 * ```ts
 * formatPercentageWithSign(0.12) // "+12%"
 * formatPercentageWithSign(-0.05) // "-5%"
 * formatPercentageWithSign(null) // "0%"
 * ```
 */
export function formatPercentageWithSign(
  value: number | null | undefined,
  options?: {
    decimals?: number;
    showZeroSign?: boolean;
  }
): string {
  if (value == null) {
    return "0%";
  }

  const { decimals = 0, showZeroSign = false } = options || {};
  const percentage = value * 100;
  const sign = percentage > 0 ? "" : percentage < 0 ? "-" : showZeroSign ? "+" : "";
  const formatted = Math.abs(percentage).toFixed(decimals);
  return `${sign}${formatted}%`;
}

/**
 * Format percentage without sign
 * Converts decimal ratio (0.12) to percentage string (12%)
 * 
 * @param value - Decimal ratio (0.12 = 12%)
 * @param options - Formatting options
 * @returns Formatted percentage string (e.g., "12%", "5.5%")
 * 
 * @example
 * ```ts
 * formatPercentage(0.12) // "12%"
 * formatPercentage(0.155, { decimals: 1 }) // "15.5%"
 * formatPercentage(null) // "0%"
 * ```
 */
export function formatPercentage(
  value: number | null | undefined,
  options?: {
    decimals?: number;
  }
): string {
  if (value == null) {
    return "0%";
  }

  const { decimals = 1 } = options || {};
  const percentage = value * 100;
  return `${percentage.toFixed(decimals)}%`;
}

/**
 * Threshold-based color determination for percentage values
 * 
 * @param value - Percentage value (0-100 scale)
 * @param thresholds - Threshold configuration
 * @returns Color key from theme palette
 * 
 * @example
 * ```ts
 * getPercentageColor(8, { critical: 11, warning: 14 }) // "error.main"
 * getPercentageColor(12, { critical: 11, warning: 14 }) // "warning.main"
 * getPercentageColor(15, { critical: 11, warning: 14 }) // "success.main"
 * ```
 */
export function getPercentageColor(
  value: number | null | undefined,
  thresholds: {
    critical: number; // Below this is critical (red)
    warning: number; // Between critical and warning is warning (yellow)
  }
): "error.main" | "warning.main" | "success.main" {
  if (value == null) {
    return "success.main"; // Default to green for null
  }

  if (value < thresholds.critical) {
    return "error.main"; // Critical red
  } else if (value <= thresholds.warning) {
    return "warning.main"; // Warning yellow
  } else {
    return "success.main"; // Good green
  }
}
