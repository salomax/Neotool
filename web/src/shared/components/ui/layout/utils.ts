import { ResponsiveValue } from './types';

/**
 * Get responsive value based on breakpoint
 */
export function getResponsiveValue<T>(
  value: ResponsiveValue<T>,
  breakpoint: 'xs' | 'sm' | 'md' | 'lg' | 'xl' = 'md'
): T {
  if (typeof value === 'object' && value !== null) {
    return value[breakpoint] || value.md || value.sm || value.xs || Object.values(value)[0];
  }
  return value as T;
}

/**
 * Convert spacing value to CSS
 */
export function spacingToCSS(spacing: ResponsiveValue<string | number>): string {
  const value = getResponsiveValue(spacing);
  return typeof value === 'number' ? `${value * 8}px` : value;
}

/**
 * Convert alignment value to CSS
 */
export function alignToCSS(align: ResponsiveValue<'start' | 'center' | 'end' | 'stretch'>): string {
  const value = getResponsiveValue(align);
  switch (value) {
    case 'start':
      return 'flex-start';
    case 'end':
      return 'flex-end';
    case 'center':
      return 'center';
    case 'stretch':
      return 'stretch';
    default:
      return 'flex-start';
  }
}

/**
 * Convert justify value to CSS
 */
export function justifyToCSS(justify: ResponsiveValue<'start' | 'center' | 'end' | 'between' | 'around' | 'evenly' | 'stretch'>): string {
  const value = getResponsiveValue(justify);
  switch (value) {
    case 'start':
      return 'flex-start';
    case 'end':
      return 'flex-end';
    case 'center':
      return 'center';
    case 'between':
      return 'space-between';
    case 'around':
      return 'space-around';
    case 'evenly':
      return 'space-evenly';
    case 'stretch':
      return 'stretch';
    default:
      return 'flex-start';
  }
}
