import React from 'react';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getResponsiveValue, spacingToCSS } from './utils';
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
  
  const gapValue = getResponsiveValue(gap, spacingToCSS);
  
  // Handle columns - either explicit cols or minColWidth
  const getColumnsValue = (): ResponsiveValue<string> | string => {
    if (cols) {
      if (typeof cols === 'object' && cols !== null) {
        // Convert responsive number object to responsive string object
        const responsiveCols: { [key: string]: string } = {};
        const colsObj = cols as { [key: string]: number | undefined };
        if (colsObj.xs !== undefined) responsiveCols.xs = `repeat(${colsObj.xs}, 1fr)`;
        if (colsObj.sm !== undefined) responsiveCols.sm = `repeat(${colsObj.sm}, 1fr)`;
        if (colsObj.md !== undefined) responsiveCols.md = `repeat(${colsObj.md}, 1fr)`;
        if (colsObj.lg !== undefined) responsiveCols.lg = `repeat(${colsObj.lg}, 1fr)`;
        if (colsObj.xl !== undefined) responsiveCols.xl = `repeat(${colsObj.xl}, 1fr)`;
        // If no breakpoints defined, use sm as default
        if (Object.keys(responsiveCols).length === 0) {
          return `repeat(${colsObj.sm || 1}, 1fr)`;
        }
        return responsiveCols as ResponsiveValue<string>;
      }
      // Single number value
      return `repeat(${cols}, 1fr)`;
    }
    
    const minWidth = typeof minColWidth === 'object' 
      ? (minColWidth as { [key: string]: string | number | undefined }).sm || '250px'
      : typeof minColWidth === 'number' 
        ? `${minColWidth}px` 
        : minColWidth;
    
    return `repeat(auto-fit, minmax(${minWidth}, 1fr))`;
  };

  const columnsValue = getResponsiveValue(columns || getColumnsValue());
  const areasValue = getResponsiveValue(areas);
  const rowsValue = getResponsiveValue(rows);

  const gridStyles: React.CSSProperties = {
    display: 'grid',
    ...(gapValue && { gap: gapValue }),
    ...(columnsValue && { gridTemplateColumns: columnsValue }),
    ...(areasValue && { gridTemplateAreas: areasValue }),
    ...(rowsValue && { gridTemplateRows: rowsValue }),
    ...style,
  };

  return (
    <Component className={className} style={gridStyles} {...testIdProps} {...props}>
      {children}
    </Component>
  );
}
