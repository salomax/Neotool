import React from 'react';
import { Box } from '@mui/material';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getTestIdProps } from '@/shared/utils/testid';

export interface GridProps extends LayoutComponentProps {
  /** Minimum column width for auto-fit grid */
  minColWidth?: ResponsiveValue<string | number>;
  /** Number of columns (overrides minColWidth) */
  cols?: ResponsiveValue<number>;
  /** Grid template areas */
  areas?: ResponsiveValue<string>;
  /** Grid template columns */
  columns?: ResponsiveValue<string>;
  /** Grid template rows */
  rows?: ResponsiveValue<string>;
}

export function Grid({
  as: Component = 'div',
  children,
  gap,
  minColWidth = '250px',
  cols,
  areas,
  columns,
  rows,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: GridProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Grid', name, dataTestId);
  
  // Handle columns - either explicit cols or minColWidth
  const getColumnsValue = (): ResponsiveValue<string> | string => {
    if (cols) {
      if (typeof cols === 'object' && cols !== null) {
        // Convert responsive number object to responsive string object
        const responsiveCols: { [key: string]: string } = {};
        const colsObj = cols as { [key: string]: number | undefined };
        
        const breakpoints = ['xs', 'sm', 'md', 'lg', 'xl'] as const;
        breakpoints.forEach(bp => {
          const val = colsObj[bp];
          if (val !== undefined) {
            responsiveCols[bp] = `repeat(${val}, 1fr)`;
          }
        });

        // If no breakpoints defined, use sm or 1 as default
        if (Object.keys(responsiveCols).length === 0) {
          return `repeat(${colsObj.sm || 1}, 1fr)`;
        }
        return responsiveCols;
      }
      // Single number value
      return `repeat(${cols}, 1fr)`;
    }
    
    // Logic for minColWidth with responsive support
    if (typeof minColWidth === 'object' && minColWidth !== null) {
      const responsiveCols: { [key: string]: string } = {};
      const minWidthObj = minColWidth as { [key: string]: string | number | undefined };
      
      const breakpoints = ['xs', 'sm', 'md', 'lg', 'xl'] as const;
      breakpoints.forEach(bp => {
        const val = minWidthObj[bp];
        if (val !== undefined) {
          const width = typeof val === 'number' ? `${val}px` : val;
          responsiveCols[bp] = `repeat(auto-fit, minmax(${width}, 1fr))`;
        }
      });
      
      if (Object.keys(responsiveCols).length > 0) return responsiveCols;
    }

    const minWidth = typeof minColWidth === 'object' 
      ? (minColWidth as { [key: string]: string | number | undefined }).sm || '250px'
      : typeof minColWidth === 'number' 
        ? `${minColWidth}px` 
        : minColWidth;
    
    return `repeat(auto-fit, minmax(${minWidth}, 1fr))`;
  };

  return (
    <Box
      component={Component}
      display="grid"
      gap={gap}
      gridTemplateColumns={columns || getColumnsValue()}
      gridTemplateAreas={areas}
      gridTemplateRows={rows}
      className={className}
      style={style}
      {...testIdProps}
      {...props}
    >
      {children}
    </Box>
  );
}
