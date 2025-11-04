"use client";

import React from 'react';
import { LayoutComponentProps } from '../primitives/types';
import { Stack } from './Stack';
import { Paper, useTheme } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface PageLayoutProps extends LayoutComponentProps {
  /** Header content (title + actions) - common to all pages */
  header?: React.ReactNode;
  /** Main content area */
  children: React.ReactNode;
  /** Whether to show loading state */
  loading?: boolean;
  /** Error message to display */
  error?: string | null;
  /** Whether to use full height layout */
  fullHeight?: boolean;
  /** Padding for the page */
  padding?: number | string;
  /** Name for generating test ID */
  name?: string;
  /** Test identifier */
  'data-testid'?: string;
}

export function PageLayout({
  header,
  children,
  loading = false,
  error,
  fullHeight = true,
  padding = 4,
  gap = 12,
  className,
  style,
  name,
  'data-testid': dataTestId,
  ...props
}: PageLayoutProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('PageLayout', name, dataTestId);
  
  // Extract layout props that Stack can handle
  const { align, justify, ...restProps } = props;
  const theme = useTheme();
  
  // Get theme values from design tokens
  const layoutTokens = (theme as any).custom?.layout || {};
  const spacing = theme.spacing;
  const palette = theme.palette;
  
  // Use theme values with fallbacks to props
  const effectivePadding = padding ?? layoutTokens.pageLayout?.padding ?? 4;
  const effectiveGap = gap ?? layoutTokens.pageLayout?.gap ?? 12;
  const effectiveFullHeight = fullHeight ?? layoutTokens.pageLayout?.fullHeight ?? true;
  
  const pageStyles: React.CSSProperties = {
    padding: typeof effectivePadding === 'number' ? spacing(effectivePadding) : effectivePadding,
    ...(effectiveFullHeight && { 
      height: 'calc(100vh - 120px)',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }),
    ...style,
  };
  
  return (
    <Stack
      as="div"
      gap={effectiveGap}
      className={className}
      style={pageStyles}
      {...testIdProps}
      {...restProps}
    >
      {/* Header Section */}
      {header && (
        <Paper 
          elevation={0} 
          sx={{ 
            p: layoutTokens.paper?.padding ?? 2, 
            flexShrink: layoutTokens.paper?.flexShrink ?? 0,
            backgroundColor: 'transparent'
          }}
        >
          {header}
        </Paper>
      )}

      {/* Error Display */}
      {error && (
        <Paper 
          elevation={0} 
          sx={{ 
            p: layoutTokens.paper?.padding ?? 2, 
            flexShrink: layoutTokens.paper?.flexShrink ?? 0,
            backgroundColor: palette.error?.main,
            color: palette.error?.contrastText || palette.common.white
          }}
        >
          {error}
        </Paper>
      )}

      {/* Main Content - children can include filters, content, etc. */}
      {children}
    </Stack>
  );
}

export default PageLayout;
