"use client";

import React from 'react';
import { Box, Paper, Stack } from '@mui/material';
import { Skeleton } from '../primitives/Skeleton';

export interface DataTableSkeletonProps {
  rows?: number;
  columns?: number;
  showToolbar?: boolean;
  showPagination?: boolean;
  height?: number | string;
  sx?: any;
}

export const DataTableSkeleton: React.FC<DataTableSkeletonProps> = ({
  rows = 8,
  columns = 5,
  showToolbar = true,
  showPagination = true,
  height = '100%',
  sx,
}) => {
  return (
    <Paper sx={{ p: 0, height, ...sx }}>
      {/* Toolbar Skeleton */}
      {showToolbar && (
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
            <Stack direction="row" spacing={2} alignItems="center">
              <Skeleton variant="rectangular" width={200} height={40} />
              <Skeleton variant="rectangular" width={120} height={40} />
              <Skeleton variant="rectangular" width={100} height={40} />
            </Stack>
            <Stack direction="row" spacing={1}>
              <Skeleton variant="circular" width={40} height={40} />
              <Skeleton variant="circular" width={40} height={40} />
              <Skeleton variant="circular" width={40} height={40} />
            </Stack>
          </Stack>
        </Box>
      )}

      {/* Table Content Skeleton */}
      <Box sx={{ p: 2 }}>
        <Skeleton 
          variant="table" 
          rows={rows} 
          columns={columns} 
          spacing={1}
          height="auto"
        />
      </Box>

      {/* Pagination Skeleton */}
      {showPagination && (
        <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider', display: 'flex', justifyContent: 'center' }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Skeleton variant="rectangular" width={32} height={32} />
            <Skeleton variant="rectangular" width={32} height={32} />
            <Skeleton variant="rectangular" width={32} height={32} />
            <Skeleton variant="rectangular" width={32} height={32} />
            <Skeleton variant="rectangular" width={32} height={32} />
          </Stack>
        </Box>
      )}
    </Paper>
  );
};

export default DataTableSkeleton;
