import React from 'react';
import { useTheme } from '@mui/material/styles';
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
  const theme = useTheme();
  
  // Get the default gap from theme's custom layout tokens
  // Note: custom layout properties are added to theme in createAppTheme
  const layoutTokens = (theme as any).custom?.layout || {};
  const defaultGap = layoutTokens.stack?.gap ?? 2;
  
  // Use provided gap or fall back to theme default
  // If gap is explicitly 0, it should be used (not undefined)
  const effectiveGap = gap !== undefined ? gap : defaultGap;
  
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Stack', name, dataTestId);
  
  const gapValue = spacingToCSS(effectiveGap);
  const alignValue = align ? alignToCSS(align) : undefined;
  const justifyValue = justify ? justifyToCSS(justify) : undefined;

  const stackStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: gapValue,
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
