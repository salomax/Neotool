/**
 * Theme utility functions for working with MUI theme
 */

import type { Theme } from "@mui/material/styles";

/**
 * Derives a font size from a base typography token using a ratio
 *
 * @param theme - MUI theme object
 * @param baseToken - Typography token key (e.g., 'body2', 'h6')
 * @param ratio - Size ratio to apply (e.g., 10/14 to get 10px from 14px base)
 * @returns Computed font size as a string with px unit
 *
 * @example
 * ```ts
 * // Get 10px from body2 (14px)
 * getDerivedFontSize(theme, 'body2', 10/14) // "10px"
 *
 * // Get 11.2px from body2 (14px)
 * getDerivedFontSize(theme, 'body2', 11.2/14) // "11.2px"
 * ```
 */
export function getDerivedFontSize(
  theme: Theme,
  baseToken: keyof Theme['typography'],
  ratio: number
): string {
  const baseSize = (theme.typography[baseToken] as any)?.fontSize ?? 14;
  const size = typeof baseSize === 'string'
    ? parseFloat(baseSize) || 14
    : baseSize ?? 14;

  return `${size * ratio}px`;
}

/**
 * Safely access custom theme properties with type safety
 *
 * @param theme - MUI theme object
 * @param path - Dot-notation path to custom property (e.g., 'custom.border.default')
 * @param fallback - Fallback value if property doesn't exist
 * @returns The custom theme value or fallback
 *
 * @example
 * ```ts
 * const borderWidth = getCustomThemeValue(theme, 'custom.border.default', 1);
 * const positiveColor = getCustomThemeValue(theme, 'custom.palette.currencyPositive', 'success.main');
 * ```
 */
export function getCustomThemeValue<T = any>(
  theme: Theme,
  path: string,
  fallback: T
): T {
  const customTheme = theme as any;
  const parts = path.split('.');

  let value = customTheme;
  for (const part of parts) {
    if (value?.[part] === undefined) {
      return fallback;
    }
    value = value[part];
  }

  return value ?? fallback;
}
