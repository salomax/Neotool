import React from 'react';
import { LayoutComponentProps, ResponsiveValue } from './types';
import { getResponsiveValue, spacingToCSS } from './utils';
import { getTestIdProps } from '@/shared/utils/testid';

export interface FrameProps extends LayoutComponentProps {
  /** Aspect ratio (e.g., '16/9', '4/3', '1/1') */
  ratio?: ResponsiveValue<string>;
  /** Whether to crop content that overflows */
  crop?: boolean;
  /** Background color for the frame */
  background?: ResponsiveValue<string>;
}

export function Frame({
  as: Component = 'div',
  children,
  gap,
  ratio = '16/9',
  crop = true,
  background,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: FrameProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Frame', name, dataTestId);
  
  // Get responsive values - note: aspect-ratio and background don't support responsive inline styles
  // For true responsiveness, consider using CSS custom properties or className-based styles
  const ratioValue = getResponsiveValue(ratio);
  const backgroundValue = getResponsiveValue(background);
  
  // Convert gap to CSS if provided - apply as padding on content wrapper since gap doesn't work with absolute positioning
  const gapValue = gap ? spacingToCSS(gap) : undefined;

  const frameStyles: React.CSSProperties = {
    position: 'relative',
    aspectRatio: ratioValue,
    overflow: crop ? 'hidden' : 'visible',
    ...(backgroundValue && { backgroundColor: backgroundValue }),
    ...style,
  };

  const contentStyles: React.CSSProperties = {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    ...(gapValue && { padding: gapValue }),
  };

  return (
    <Component className={className} style={frameStyles} {...testIdProps} {...props}>
      <div style={contentStyles}>
        {children}
      </div>
    </Component>
  );
}
