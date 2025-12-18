import { describe, it, expect } from 'vitest';
import {
  getResponsiveValue,
  spacingToCSS,
  alignToCSS,
  justifyToCSS,
} from '../utils';
import type { ResponsiveValue } from '../types';

describe('getResponsiveValue', () => {
  it('should return undefined when value is undefined', () => {
    expect(getResponsiveValue(undefined)).toBeUndefined();
  });

  it('should return value directly when it is not an object', () => {
    expect(getResponsiveValue('test')).toBe('test');
    expect(getResponsiveValue(123)).toBe(123);
    expect(getResponsiveValue(true)).toBe(true);
  });

  it('should return value for current breakpoint', () => {
    const value: ResponsiveValue<string> = { md: 'medium', sm: 'small' };
    expect(getResponsiveValue(value, undefined, 'md')).toBe('medium');
    expect(getResponsiveValue(value, undefined, 'sm')).toBe('small');
  });

  it('should fallback to md when current breakpoint not found', () => {
    const value: ResponsiveValue<string> = { md: 'medium', xs: 'extra-small' };
    expect(getResponsiveValue(value, undefined, 'lg')).toBe('medium');
  });

  it('should fallback to sm when md not found', () => {
    const value: ResponsiveValue<string> = { sm: 'small', xs: 'extra-small' };
    expect(getResponsiveValue(value, undefined, 'lg')).toBe('small');
  });

  it('should fallback to xs when sm not found', () => {
    const value: ResponsiveValue<string> = { xs: 'extra-small' };
    expect(getResponsiveValue(value, undefined, 'lg')).toBe('extra-small');
  });

  it('should fallback to first available value', () => {
    const value: ResponsiveValue<string> = { lg: 'large', xl: 'extra-large' };
    expect(getResponsiveValue(value, undefined, 'md')).toBe('large');
  });

  it('should return undefined when no values found', () => {
    const value: ResponsiveValue<string> = {};
    expect(getResponsiveValue(value, undefined, 'md')).toBeUndefined();
  });

  it('should apply transformer when provided', () => {
    const value: ResponsiveValue<number> = { md: 10 };
    const transformer = (val: number) => val * 2;
    expect(getResponsiveValue(value, transformer, 'md')).toBe(20);
  });

  it('should return undefined when resolved value is undefined and transformer provided', () => {
    const value: ResponsiveValue<number> = {};
    const transformer = (val: number) => val * 2;
    expect(getResponsiveValue(value, transformer, 'md')).toBeUndefined();
  });
});

describe('spacingToCSS', () => {
  it('should convert number to pixels (multiply by 8)', () => {
    expect(spacingToCSS(1)).toBe('8px');
    expect(spacingToCSS(2)).toBe('16px');
    expect(spacingToCSS(0)).toBe('0px');
    expect(spacingToCSS(0.5)).toBe('4px');
  });

  it('should return string value as-is', () => {
    expect(spacingToCSS('10px')).toBe('10px');
    expect(spacingToCSS('1rem')).toBe('1rem');
    expect(spacingToCSS('50%')).toBe('50%');
  });

  it('should return 0px when value is undefined', () => {
    expect(spacingToCSS(undefined)).toBe('0px');
  });

  it('should handle responsive values', () => {
    const value: ResponsiveValue<number> = { md: 2, sm: 1 };
    expect(spacingToCSS(value)).toBe('16px'); // Uses md by default
  });

  it('should handle responsive string values', () => {
    const value: ResponsiveValue<string> = { md: '1rem', sm: '0.5rem' };
    expect(spacingToCSS(value)).toBe('1rem');
  });
});

describe('alignToCSS', () => {
  it('should convert start to flex-start', () => {
    expect(alignToCSS('start')).toBe('flex-start');
  });

  it('should convert end to flex-end', () => {
    expect(alignToCSS('end')).toBe('flex-end');
  });

  it('should convert center to center', () => {
    expect(alignToCSS('center')).toBe('center');
  });

  it('should convert stretch to stretch', () => {
    expect(alignToCSS('stretch')).toBe('stretch');
  });

  it('should default to flex-start for unknown values', () => {
    expect(alignToCSS('unknown' as any)).toBe('flex-start');
  });

  it('should handle responsive values', () => {
    const value: ResponsiveValue<'start' | 'center' | 'end' | 'stretch'> = {
      md: 'center',
      sm: 'start',
    };
    expect(alignToCSS(value)).toBe('center');
  });
});

describe('justifyToCSS', () => {
  it('should convert start to flex-start', () => {
    expect(justifyToCSS('start')).toBe('flex-start');
  });

  it('should convert end to flex-end', () => {
    expect(justifyToCSS('end')).toBe('flex-end');
  });

  it('should convert center to center', () => {
    expect(justifyToCSS('center')).toBe('center');
  });

  it('should convert between to space-between', () => {
    expect(justifyToCSS('between')).toBe('space-between');
  });

  it('should convert around to space-around', () => {
    expect(justifyToCSS('around')).toBe('space-around');
  });

  it('should convert evenly to space-evenly', () => {
    expect(justifyToCSS('evenly')).toBe('space-evenly');
  });

  it('should convert stretch to stretch', () => {
    expect(justifyToCSS('stretch')).toBe('stretch');
  });

  it('should default to flex-start for unknown values', () => {
    expect(justifyToCSS('unknown' as any)).toBe('flex-start');
  });

  it('should handle responsive values', () => {
    const value: ResponsiveValue<'start' | 'center' | 'end' | 'between' | 'around' | 'evenly' | 'stretch'> = {
      md: 'between',
      sm: 'start',
    };
    expect(justifyToCSS(value)).toBe('space-between');
  });
});

