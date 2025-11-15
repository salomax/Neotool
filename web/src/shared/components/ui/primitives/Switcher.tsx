import React from 'react';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getResponsiveValue, spacingToCSS } from './utils';

export interface SwitcherProps extends LayoutComponentProps {
  /** Minimum width for each item before switching to fewer columns */
  threshold?: ResponsiveValue<string | number>;
  /** Maximum number of columns */
  maxCols?: ResponsiveValue<number>;
}

export function Switcher({
  as: Component = 'div',
  children,
  gap,
  threshold = '30rem',
  maxCols = 4,
  className,
  style,
  ...props
}: SwitcherProps) {
  const gapValue = getResponsiveValue(gap, spacingToCSS);
  
  const getThresholdValue = () => {
    const thresholdValue = typeof threshold === 'object' 
      ? threshold.sm || '30rem'
      : typeof threshold === 'number' 
        ? `${threshold}px` 
        : threshold;
    
    const maxColsValue = typeof maxCols === 'object' 
      ? maxCols.sm || 4
      : maxCols;
    
    return `repeat(auto-fit, minmax(0, min(${thresholdValue}, 100%)))`;
  };

  const columnsValue = getResponsiveValue(getThresholdValue());

  const switcherStyles: React.CSSProperties = {
    display: 'grid',
    ...(gapValue && { gap: gapValue }),
    ...(columnsValue && { gridTemplateColumns: columnsValue }),
    ...style,
  };

  return (
    <Component className={className} style={switcherStyles} {...props}>
      {children}
    </Component>
  );
}
