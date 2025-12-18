import { ResponsiveValue } from './types';

/**
 * Get responsive value based on breakpoint
 */
export function getResponsiveValue<T, R = T>(
  value: ResponsiveValue<T> | undefined,
  transformer?: (val: T) => R,
  breakpoint: 'xs' | 'sm' | 'md' | 'lg' | 'xl' = 'md'
): R | string | undefined {
  if (value === undefined) {
    return undefined;
  }
  
  let resolvedValue: T | undefined;
  if (typeof value === 'object' && value !== null) {
    const obj = value as { [key: string]: T | undefined };
    resolvedValue = obj[breakpoint] || obj.md || obj.sm || obj.xs || Object.values(obj)[0];
  } else {
    resolvedValue = value as T;
  }
  
  if (resolvedValue === undefined) {
    return undefined as R | string | undefined;
  }
  
  if (transformer) {
    return transformer(resolvedValue) as R | string;
  }
  
  return resolvedValue as R;
}

/**
 * Convert spacing value to CSS
 */
export function spacingToCSS(spacing: ResponsiveValue<string | number>): string {
  const value = getResponsiveValue(spacing);
  if (value === undefined) return '0px';
  return typeof value === 'number' ? `${value * 8}px` : (value as string);
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
