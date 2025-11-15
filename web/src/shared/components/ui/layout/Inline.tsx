import React from 'react';
import { LayoutComponentProps } from './types';
import { spacingToCSS, alignToCSS, justifyToCSS } from './utils';
import { getTestIdProps } from '@/shared/utils/testid';

export interface InlineProps extends LayoutComponentProps {
  /** Whether to wrap items to new lines when they don't fit */
  wrap?: boolean;
}

export function Inline({
  as: Component = 'div',
  children,
  gap,
  align,
  justify,
  wrap = true,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: InlineProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Inline', name, dataTestId);
  
  // Transform functions handle responsive values internally
  const gapValue = gap ? spacingToCSS(gap) : undefined;
  const alignValue = align ? alignToCSS(align) : undefined;
  const justifyValue = justify ? justifyToCSS(justify) : undefined;

  const inlineStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'row',
    flexWrap: wrap ? 'wrap' : 'nowrap',
    ...(gapValue && { gap: gapValue }),
    ...(alignValue && { alignItems: alignValue }),
    ...(justifyValue && { justifyContent: justifyValue }),
    ...style,
  };

  return (
    <Component className={className} style={inlineStyles} {...testIdProps} {...props}>
      {children}
    </Component>
  );
}
