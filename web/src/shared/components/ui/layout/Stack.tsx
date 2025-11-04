import React from 'react';
import { LayoutComponentProps } from './types';
import { spacingToCSS, alignToCSS, justifyToCSS } from './utils';
import { getTestIdProps } from '@/shared/utils/testid';

export interface StackProps extends Omit<LayoutComponentProps, 'justify'> {
  /** Justification along main axis (vertical for Stack) */
  justify?: 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly' | 'stretch';
}

export function Stack({
  as: Component = 'div',
  children,
  gap,
  align,
  justify,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: StackProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Stack', name, dataTestId);
  
  const gapValue = gap ? spacingToCSS(gap) : undefined;
  const alignValue = align ? alignToCSS(align) : undefined;
  const justifyValue = justify ? justifyToCSS(justify) : undefined;

  const stackStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    ...(gapValue && { gap: gapValue }),
    ...(alignValue && { alignItems: alignValue }),
    ...(justifyValue && { justifyContent: justifyValue }),
    ...style,
  };

  return (
    <Component className={className} style={stackStyles} {...testIdProps} {...props}>
      {children}
    </Component>
  );
}
