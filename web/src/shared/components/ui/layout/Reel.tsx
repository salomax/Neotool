import React from 'react';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getResponsiveValue, spacingToCSS } from './utils';

export interface ReelProps extends LayoutComponentProps {
  /** Height of the reel */
  height?: ResponsiveValue<string | number>;
  /** Whether to show scroll indicators */
  showScrollbar?: boolean;
  /** Scroll snap alignment */
  snapAlign?: 'start' | 'center' | 'end' | 'none';
  /** Item width for scroll snap */
  itemWidth?: ResponsiveValue<string | number>;
}

export function Reel({
  as: Component = 'div',
  children,
  gap,
  height = 'auto',
  showScrollbar = false,
  snapAlign = 'start',
  itemWidth,
  className,
  style,
  ...props
}: ReelProps) {
  const gapValue = getResponsiveValue(gap, spacingToCSS);
  
  const getHeightValue = () => {
    return typeof height === 'object' 
      ? height.sm || 'auto'
      : typeof height === 'number' 
        ? `${height}px` 
        : height;
  };

  const getItemWidthValue = () => {
    if (!itemWidth) return 'auto';
    return typeof itemWidth === 'object' 
      ? itemWidth.sm || 'auto'
      : typeof itemWidth === 'number' 
        ? `${itemWidth}px` 
        : itemWidth;
  };

  const reelStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'row',
    overflowX: 'auto',
    overflowY: 'hidden',
    scrollSnapType: snapAlign === 'none' ? 'none' : 'x mandatory',
    scrollbarWidth: showScrollbar ? 'auto' : 'none',
    WebkitOverflowScrolling: 'touch',
    height: getHeightValue(),
    ...(gapValue && { gap: gapValue }),
    ...style,
  };

  // Hide scrollbar for webkit browsers
  const scrollbarStyles = !showScrollbar ? {
    '&::-webkit-scrollbar': {
      display: 'none',
    },
  } : {};

  const childrenWithSnap = React.Children.map(children, (child) => {
    if (React.isValidElement(child)) {
      const itemWidthValue = getItemWidthValue();
      return React.cloneElement(child, {
        style: {
          flexShrink: 0,
          scrollSnapAlign: snapAlign === 'none' ? 'none' : snapAlign,
          ...(itemWidthValue !== 'auto' && { width: itemWidthValue }),
          ...child.props.style,
        },
      });
    }
    return child;
  });

  return (
    <Component 
      className={className} 
      style={{ ...reelStyles, ...scrollbarStyles }} 
      {...props}
    >
      {childrenWithSnap}
    </Component>
  );
}
