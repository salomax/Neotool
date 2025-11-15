"use client";

import React from 'react';
import { Box, Typography } from '@mui/material';
import { Button } from '../primitives/Button';
import { Stack } from './Stack';
import { getTestIdProps } from '@/shared/utils/testid';

export interface PageHeaderProps {
  /** Page title */
  title: string;
  /** Action buttons to display on the right */
  actions?: React.ReactNode;
  /** Subtitle or description */
  subtitle?: string;
  /** Whether to show loading state */
  loading?: boolean;
  /** Name for generating test ID */
  name?: string;
  /** Test identifier */
  'data-testid'?: string;
}

export function PageHeader({ 
  title, 
  actions, 
  subtitle,
  loading = false,
  name,
  'data-testid': dataTestId
}: PageHeaderProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('PageHeader', name, dataTestId);
  
  return (
    <Box sx={{ 
      display: "flex", 
      justifyContent: "space-between", 
      alignItems: "center",
      flexWrap: 'wrap',
      gap: 2
    }} {...testIdProps}>
      <Box>
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      
      {actions && (
        <Box sx={{ 
          display: 'flex', 
          gap: 1, 
          alignItems: 'center',
          flexWrap: 'wrap'
        }}>
          {actions}
        </Box>
      )}
    </Box>
  );
}

export default PageHeader;
