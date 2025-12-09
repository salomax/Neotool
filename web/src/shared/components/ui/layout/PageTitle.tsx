"use client";

import React from 'react';
import Typography from '@mui/material/Typography';
import { getTestIdProps } from '@/shared/utils/testid';

export interface PageTitleProps {
  /** Page title text */
  children: React.ReactNode;
  /** Name for generating test ID */
  name?: string;
  /** Test identifier */
  'data-testid'?: string;
  /** Additional sx props to merge with default styles */
  sx?: React.ComponentProps<typeof Typography>['sx'];
}

/**
 * PageTitle component for consistent page heading styling.
 * 
 * Provides a standardized h4 heading with h1 semantic meaning,
 * gutter bottom spacing, and horizontal padding.
 * 
 * @example
 * ```tsx
 * <PageTitle>Settings</PageTitle>
 * ```
 */
export function PageTitle({
  children,
  name,
  'data-testid': dataTestId,
  sx,
}: PageTitleProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('PageTitle', name, dataTestId);

  return (
    <Typography
      variant="h4"
      component="h1"
      gutterBottom
      sx={{ px: 2, ...sx }}
      {...testIdProps}
    >
      {children}
    </Typography>
  );
}

export default PageTitle;

