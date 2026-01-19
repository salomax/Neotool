"use client";

import React from 'react';
import { useTheme } from '@mui/material/styles';
import { Box } from './Box';
import { Container } from './Container';
import { LayoutComponentProps } from './types';
import { getTestIdProps } from '@/shared/utils/testid';
import { RAIL_W } from '@/shared/ui/navigation/SidebarRail';
import { useElementHeight } from '@/shared/hooks/ui';

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
   * Top offset for the fixed sidebar, useful when there is a fixed header.
   * Default: 73 (AppHeader height)
   */
  headerHeight?: number | string;
  /**
   * CSS selector for the header element to measure dynamically if headerHeight is not provided.
   * Default: 'header'
   */
  headerSelector?: string;
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
export const SidebarLayout = React.forwardRef<HTMLDivElement, SidebarLayoutProps>(({
  size = 'md',
  side = 'left',
  headerHeight,
  headerSelector = 'header',
  fullHeight = false,
  sidebar,
  children,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}, ref) => {
  const theme = useTheme();
  
  // Use provided headerHeight or calculated dynamicHeaderHeight
  const dynamicHeaderHeight = useElementHeight(headerSelector);
  const effectiveHeaderHeight = headerHeight ?? dynamicHeaderHeight;

  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('SidebarLayout', name, dataTestId);
  const generatedTestId = testIdProps['data-testid'];
  
  // Calculate sidebar width
  const sidebarWidth = getSidebarWidth(size);
  
  // Determine flex direction based on side
  const isLeft = side === 'left';
  const sidebarEdge = isLeft ? 'left' : 'right';
  const contentOffset = size === 'full' ? 0 : (typeof sidebarWidth === 'number' ? `${sidebarWidth}px` : sidebarWidth);
  
  // Get sidebar background color from theme (same as SidebarRail)
  const sidebarBg = (theme as any).custom?.palette?.sidebarBg || '#ffffff';
  
  // Outer container styles
  const outerStyles: React.CSSProperties = React.useMemo(() => ({
    position: 'relative',
    width: '100%',
    ...style,
  }), [style]);
  
  // Sidebar styles
  const sidebarStyles: React.CSSProperties = React.useMemo(() => {
    const edgePosition: Partial<React.CSSProperties> = isLeft ? { left: `${RAIL_W}px` } : { right: `${RAIL_W}px` };
    
    return {
      position: 'fixed',
      top: typeof effectiveHeaderHeight === 'number' ? `${effectiveHeaderHeight}px` : effectiveHeaderHeight,
      bottom: 0,
      ...edgePosition,
      width: typeof sidebarWidth === 'number' ? `${sidebarWidth}px` : sidebarWidth,
      overflow: 'auto',
      // Ensure sidebar sits below the app bar so the header's bottom border is visible
      zIndex: (theme.zIndex?.appBar || 1100) - 1,
      ...(isLeft ? { borderRight: '1px solid' } : { borderLeft: '1px solid' }),
      borderColor: theme.palette.divider,
    };
  }, [effectiveHeaderHeight, isLeft, sidebarWidth, theme.zIndex?.appBar, theme.palette.divider]);
  
  // Main content area container styles
  // We don't need separate styles as they are applied to the wrapper


  return (
    <Box
      ref={ref}
      fullHeight={fullHeight}
      className={className}
      style={outerStyles}
      {...testIdProps}
      {...props}
    >
      {/* Fixed sidebar column */}
      <Box
        component="aside"
        style={sidebarStyles}
        sx={{ bgcolor: sidebarBg }}
        data-testid={generatedTestId ? `${generatedTestId}-sidebar` : undefined}
      >
        {sidebar}
      </Box>
      
      {/* Main content wrapper - handles sidebar offset and scrolling */}
      <Box
        component="main"
        fullHeight={fullHeight}
        sx={{
          ...(isLeft ? { marginLeft: contentOffset } : { marginRight: contentOffset }),
          // Calculate width to fit remaining space exactly to prevent overflow in flex container
          width: contentOffset === 0 ? '100%' : `calc(100% - ${contentOffset})`,
          minWidth: 0,
          // When fullHeight, this wrapper handles scrolling so the scrollbar is on the edge
          ...(fullHeight && { overflow: 'auto' }),
        }}
        data-testid={generatedTestId ? `${generatedTestId}-content` : undefined}
      >
        <Container
          disableGutters
          // Don't use fullHeight prop as it adds overflow: hidden.
          // Instead manually add flex properties to support full height children.
          sx={{
            ...(fullHeight && {
              minHeight: '100%',
              display: 'flex',
              flexDirection: 'column',
            }),
          }}
        >
          {children}
        </Container>
      </Box>
    </Box>
  );
});

SidebarLayout.displayName = 'SidebarLayout';

export default SidebarLayout;
