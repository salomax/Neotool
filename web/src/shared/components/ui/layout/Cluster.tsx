import React from 'react';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getResponsiveValue, spacingToCSS, alignToCSS, justifyToCSS } from './utils';
import { getTestIdProps } from '@/shared/utils/testid';

export interface ClusterProps extends LayoutComponentProps {
  /** Minimum width for each item before wrapping */
  minItemWidth?: ResponsiveValue<string | number>;
}

export function Cluster({
  as: Component = 'div',
  children,
  gap,
  align,
  justify,
  minItemWidth = 'auto',
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: ClusterProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Cluster', name, dataTestId);
  
  const gapValue = getResponsiveValue(gap, spacingToCSS);
  const alignValue = getResponsiveValue(align, alignToCSS);
  const justifyValue = getResponsiveValue(justify, justifyToCSS);

  const clusterStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'row',
    flexWrap: 'wrap',
    ...(gapValue && { gap: gapValue }),
    ...(alignValue && { alignItems: alignValue }),
    ...(justifyValue && { justifyContent: justifyValue }),
    ...style,
  };

  // Apply min-width to children
  const childrenWithMinWidth = React.Children.map(children, (child) => {
    if (React.isValidElement(child)) {
      const minWidth = typeof minItemWidth === 'object' 
        ? minItemWidth.sm || 'auto'
        : typeof minItemWidth === 'number' 
          ? `${minItemWidth}px` 
          : minItemWidth;
      
      return React.cloneElement(child, {
        style: {
          minWidth,
          ...child.props.style,
        },
      });
    }
    return child;
  });

  return (
    <Component className={className} style={clusterStyles} {...testIdProps} {...props}>
      {childrenWithMinWidth}
    </Component>
  );
}
