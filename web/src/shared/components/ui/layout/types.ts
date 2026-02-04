import React from 'react';

/**
 * Responsive value that can be a single value or an object with breakpoint keys
 */
export type ResponsiveValue<T> = 
  | T
  | {
      xs?: T;
      sm?: T;
      md?: T;
      lg?: T;
      xl?: T;
    };

/**
 * Spacing value for gaps and padding
 */
export type SpacingValue = string | number;

/**
 * Alignment value for cross-axis alignment
 */
export type AlignValue = 'start' | 'center' | 'end' | 'stretch';

/**
 * Justification value for main-axis alignment
 */
export type JustifyValue = 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';

/**
 * Flex direction for Stack
 */
export type DirectionValue = 'row' | 'column' | 'row-reverse' | 'column-reverse';

/**
 * Base props for all layout components
 */
export interface LayoutComponentProps {
  /** Spacing between items */
  gap?: ResponsiveValue<SpacingValue>;
  /** Cross-axis alignment */
  align?: ResponsiveValue<AlignValue>;
  /** Main-axis justification */
  justify?: ResponsiveValue<JustifyValue>;
  /** Flex direction (for Stack) */
  direction?: ResponsiveValue<DirectionValue>;
  /** Flex wrap */
  flexWrap?: ResponsiveValue<'wrap' | 'nowrap' | 'wrap-reverse'>;
  /** HTML element to render as */
  as?: React.ElementType;
  /** Additional CSS class name */
  className?: string;
  /** Inline styles */
  style?: React.CSSProperties;
  /** Name for generating test ID */
  name?: string;
  /** Test identifier */
  'data-testid'?: string;
  /** Children elements */
  children: React.ReactNode;
}

