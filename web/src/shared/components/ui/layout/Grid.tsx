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
  const getColumnsValue = () => {
    if (cols) {
      return typeof cols === 'object' 
        ? cols.sm || '1fr'
        : `repeat(${cols}, 1fr)`;
    }
    
    const minWidth = typeof minColWidth === 'object' 
      ? minColWidth.sm || '250px'
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
