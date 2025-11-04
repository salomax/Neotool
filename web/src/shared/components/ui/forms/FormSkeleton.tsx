"use client";

import React from 'react';
import { Box, Paper, Stack, Typography } from '@mui/material';
import { Skeleton } from '../primitives/Skeleton';

export interface FormSkeletonProps {
  fields?: number;
  showTitle?: boolean;
  showActions?: boolean;
  title?: string;
  height?: number | string;
  sx?: any;
}

export const FormSkeleton: React.FC<FormSkeletonProps> = ({
  fields = 4,
  showTitle = true,
  showActions = true,
  title = "Loading...",
  height = 'auto',
  sx,
}) => {
  return (
    <Paper sx={{ p: 3, height, ...sx }}>
      {showTitle && (
        <Typography variant="h6" sx={{ mb: 3 }}>
          <Skeleton variant="text" width="40%" height={32} />
        </Typography>
      )}
      
      <Stack spacing={3}>
        {Array.from({ length: fields }).map((_, index) => (
          <Box key={`field-${index}`}>
            <Skeleton variant="text" width="25%" height={20} sx={{ mb: 1 }} />
            <Skeleton variant="rectangular" height={56} />
          </Box>
        ))}
        
        {showActions && (
          <Stack direction="row" spacing={2} sx={{ mt: 4, justifyContent: 'flex-end' }}>
            <Skeleton variant="rectangular" width={100} height={36} />
            <Skeleton variant="rectangular" width={120} height={36} />
          </Stack>
        )}
      </Stack>
    </Paper>
  );
};

export default FormSkeleton;
