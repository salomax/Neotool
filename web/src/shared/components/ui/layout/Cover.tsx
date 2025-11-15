import React from 'react';
import { LayoutComponentProps } from './types';
import { getResponsiveValue, spacingToCSS } from './utils';

export interface CoverProps extends LayoutComponentProps {
  /** Minimum height of the cover */
  minHeight?: ResponsiveValue<string | number>;
  /** Content to place at the top */
  top?: React.ReactNode;
  /** Content to place at the bottom */
  bottom?: React.ReactNode;
  /** Whether to center the main content */
  center?: boolean;
}

export function Cover({
  as: Component = 'div',
  children,
  gap,
  minHeight = '50vh',
  top,
  bottom,
  center = true,
  className,
  style,
  ...props
}: CoverProps) {
  const gapValue = getResponsiveValue(gap, spacingToCSS);
  
  const getMinHeightValue = () => {
    return typeof minHeight === 'object' 
      ? minHeight.sm || '50vh'
      : typeof minHeight === 'number' 
        ? `${minHeight}px` 
        : minHeight;
  };

  const coverStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    minHeight: getMinHeightValue(),
    ...(gapValue && { gap: gapValue }),
    ...style,
  };

  const centerStyles: React.CSSProperties = center ? {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
  } : {};

  return (
    <Component className={className} style={coverStyles} {...props}>
      {top && <div>{top}</div>}
      <div style={centerStyles}>
        {children}
      </div>
      {bottom && <div>{bottom}</div>}
    </Component>
  );
}
