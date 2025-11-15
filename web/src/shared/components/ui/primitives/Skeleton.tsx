"use client";

import React from 'react';
import { Skeleton as MUISkeleton, Box, Stack } from '@mui/material';

export type SkeletonVariant = 'text' | 'rectangular' | 'circular' | 'table' | 'form' | 'card' | 'list';

export interface SkeletonProps {
  variant?: SkeletonVariant;
  width?: number | string;
  height?: number | string;
  rows?: number;
  columns?: number;
  spacing?: number;
  animation?: 'pulse' | 'wave' | false;
  className?: string;
  sx?: any;
}

const SkeletonVariants = {
  text: {
    component: MUISkeleton,
    props: { variant: 'text' as const }
  },
  rectangular: {
    component: MUISkeleton,
    props: { variant: 'rectangular' as const }
  },
  circular: {
    component: MUISkeleton,
    props: { variant: 'circular' as const }
  },
  table: {
    component: TableSkeleton,
    props: {}
  },
  form: {
    component: FormSkeleton,
    props: {}
  },
  card: {
    component: CardSkeleton,
    props: {}
  },
  list: {
    component: ListSkeleton,
    props: {}
  }
} as const;

// Table Skeleton Component
function TableSkeleton({ 
  rows = 5, 
  columns = 4, 
  spacing = 1, 
  height = 400,
  ...props 
}: Omit<SkeletonProps, 'variant'>) {
  return (
    <Box sx={{ p: 2, height, ...props.sx }}>
      {/* Table Header */}
      <Stack direction="row" spacing={spacing} sx={{ mb: 2 }}>
        {Array.from({ length: columns }).map((_, index) => (
          <MUISkeleton
            key={`header-${index}`}
            variant="rectangular"
            height={40}
            sx={{ flex: index === 0 ? 2 : 1 }}
          />
        ))}
      </Stack>
      
      {/* Table Rows */}
      <Stack spacing={spacing}>
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <Stack key={`row-${rowIndex}`} direction="row" spacing={spacing}>
            {Array.from({ length: columns }).map((_, colIndex) => (
              <MUISkeleton
                key={`cell-${rowIndex}-${colIndex}`}
                variant="rectangular"
                height={32}
                sx={{ flex: colIndex === 0 ? 2 : 1 }}
              />
            ))}
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}

// Form Skeleton Component
function FormSkeleton({ 
  fields = 4, 
  spacing = 2, 
  height = 300,
  ...props 
}: Omit<SkeletonProps, 'variant'>) {
  return (
    <Box sx={{ p: 2, height, ...props.sx }}>
      <Stack spacing={spacing}>
        {Array.from({ length: fields }).map((_, index) => (
          <Box key={`field-${index}`}>
            <MUISkeleton variant="text" width="30%" height={20} sx={{ mb: 1 }} />
            <MUISkeleton variant="rectangular" height={40} />
          </Box>
        ))}
        {/* Action buttons */}
        <Stack direction="row" spacing={2} sx={{ mt: 3, justifyContent: 'flex-end' }}>
          <MUISkeleton variant="rectangular" width={80} height={36} />
          <MUISkeleton variant="rectangular" width={100} height={36} />
        </Stack>
      </Stack>
    </Box>
  );
}

// Card Skeleton Component
function CardSkeleton({ 
  cards = 3, 
  spacing = 2, 
  height = 200,
  ...props 
}: Omit<SkeletonProps, 'variant'>) {
  return (
    <Box sx={{ p: 2, height, ...props.sx }}>
      <Stack direction="row" spacing={spacing}>
        {Array.from({ length: cards }).map((_, index) => (
          <Box key={`card-${index}`} sx={{ flex: 1 }}>
            <MUISkeleton variant="rectangular" height={height} sx={{ borderRadius: 1 }} />
            <Box sx={{ p: 2 }}>
              <MUISkeleton variant="text" width="80%" height={24} sx={{ mb: 1 }} />
              <MUISkeleton variant="text" width="60%" height={20} />
            </Box>
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

// List Skeleton Component
function ListSkeleton({ 
  items = 5, 
  spacing = 1, 
  height = 300,
  ...props 
}: Omit<SkeletonProps, 'variant'>) {
  return (
    <Box sx={{ p: 2, height, ...props.sx }}>
      <Stack spacing={spacing}>
        {Array.from({ length: items }).map((_, index) => (
          <Box key={`item-${index}`} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <MUISkeleton variant="circular" width={40} height={40} />
            <Box sx={{ flex: 1 }}>
              <MUISkeleton variant="text" width="70%" height={20} sx={{ mb: 0.5 }} />
              <MUISkeleton variant="text" width="50%" height={16} />
            </Box>
            <MUISkeleton variant="rectangular" width={60} height={24} />
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

// Main Skeleton Component
export const Skeleton: React.FC<SkeletonProps> = ({
  variant = 'rectangular',
  width,
  height,
  rows,
  columns,
  spacing = 1,
  animation = 'pulse',
  className,
  sx,
  ...props
}) => {
  const skeletonConfig = SkeletonVariants[variant];
  
  if (variant === 'table' || variant === 'form' || variant === 'card' || variant === 'list') {
    const SkeletonComponent = skeletonConfig.component as React.ComponentType<any>;
    return (
      <SkeletonComponent
        rows={rows}
        columns={columns}
        spacing={spacing}
        height={height}
        width={width}
        className={className}
        sx={sx}
        {...props}
      />
    );
  }

  const SkeletonComponent = skeletonConfig.component;
  return (
    <SkeletonComponent
      {...skeletonConfig.props}
      width={width}
      height={height}
      animation={animation}
      className={className}
      sx={sx}
      {...props}
    />
  );
};

export default Skeleton;
