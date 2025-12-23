"use client";

import React from 'react';
import { Box } from './Box';
import { Container } from './Container';
import { LayoutComponentProps } from './types';
import { getTestIdProps } from '@/shared/utils/testid';
import { RAIL_W } from '@/shared/ui/navigation/SidebarRail';

// Drawer size presets (same as Drawer component for consistency)
export type SidebarSize = 'sm' | 'md' | 'lg' | 'full';

const SIDEBAR_SIZES: Record<SidebarSize, number | string> = {
  sm: 400,
  md: 600,
  lg: 800,
  full: `calc(100% - ${RAIL_W}px)`,
};

/**
 * Get sidebar width based on size prop
 */
function getSidebarWidth(size: SidebarSize | undefined): number | string {
  return size ? SIDEBAR_SIZES[size] : SIDEBAR_SIZES.md;
}

export interface SidebarLayoutProps extends LayoutComponentProps {
  /** 
   * Predefined size: 'sm' (400px), 'md' (600px), 'lg' (800px), or 'full' (100% - sidebar width).
   * Default: 'md'
   */
  size?: SidebarSize;
  /** 
   * Position of the fixed column: 'left' or 'right'.
   * Default: 'left'
   */
  side?: 'left' | 'right';
  /** 
   * When true, applies full-height layout to both outer container and main content area.
   * The main content area will have vertical scroll enabled when content overflows.
   */
  fullHeight?: boolean;
  /** Content for the fixed sidebar column */
  sidebar: React.ReactNode;
  /** Main content (rendered as Container) */
  children: React.ReactNode;
}

/**
 * SidebarLayout component - A fixed column layout similar to a drawer but permanently visible
 * 
 * Provides a layout with a fixed sidebar column on one side (left or right) and a main content area.
 * The main content is rendered as a Container component, and when fullHeight is enabled, vertical
 * scrolling is automatically enabled when content overflows.
 * 
 * @example
 * ```tsx
 * <SidebarLayout
 *   side="left"
 *   size="md"
 *   fullHeight
 *   sidebar={<FilterPanel />}
 * >
 *   <ProductList />
 * </SidebarLayout>
 * ```
 * 
 * @example
 * ```tsx
 * <SidebarLayout
 *   side="right"
 *   size="lg"
 *   sidebar={<DetailsPanel />}
 * >
 *   <MainContent />
 * </SidebarLayout>
 * ```
 */
export function SidebarLayout({
  size = 'md',
  side = 'left',
  fullHeight = false,
  sidebar,
  children,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: SidebarLayoutProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('SidebarLayout', name, dataTestId);
  const generatedTestId = testIdProps['data-testid'];
  
  // Calculate sidebar width
  const sidebarWidth = getSidebarWidth(size);
  
  // Determine flex direction based on side
  const flexDirection = side === 'left' ? 'row' : 'row-reverse';
  
  // Outer container styles
  const outerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection,
    width: '100%',
    ...style,
  };
  
  // Sidebar styles
  const sidebarStyles: React.CSSProperties = {
    width: typeof sidebarWidth === 'number' ? `${sidebarWidth}px` : sidebarWidth,
    flexShrink: 0,
    ...(fullHeight && {
      height: '100%',
      overflow: 'auto',
    }),
  };
  
  // Main content area container styles
  const mainContentContainerStyles: React.CSSProperties = {
    flex: 1,
    minWidth: 0, // Prevent flex item from overflowing
  };

  return (
    <Box
      fullHeight={fullHeight}
      className={className}
      style={outerStyles}
      {...testIdProps}
      {...props}
    >
      {/* Fixed sidebar column */}
      <Box
        style={sidebarStyles}
        data-testid={generatedTestId ? `${generatedTestId}-sidebar` : undefined}
      >
        {sidebar}
      </Box>
      
      {/* Main content area - rendered as Container */}
      <Container
        fullHeight={fullHeight}
        disableGutters
        sx={mainContentContainerStyles}
        data-testid={generatedTestId ? `${generatedTestId}-content` : undefined}
      >
        {/* Inner Box with overflow: auto for scrolling when content overflows */}
        {fullHeight ? (
          <Box
            sx={{
              flex: 1,
              overflow: 'auto',
              minHeight: 0,
            }}
          >
            {children}
          </Box>
        ) : (
          children
        )}
      </Container>
    </Box>
  );
}

export default SidebarLayout;
