"use client";

import React, { Suspense } from 'react';
import { Box } from '@/shared/ui/mui-imports';
import { LoadingSpinner } from '@/shared/components/ui/primitives/LoadingSpinner';

interface LazyWrapperProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
  minHeight?: string | number;
}

const DefaultFallback = ({ minHeight = 200 }: { minHeight?: string | number }) => (
  <Box 
    display="flex" 
    justifyContent="center" 
    alignItems="center" 
    minHeight={minHeight}
  >
    <LoadingSpinner />
  </Box>
);

export function LazyWrapper({ 
  children, 
  fallback, 
  minHeight = 200 
}: LazyWrapperProps) {
  return (
    <Suspense fallback={fallback || <DefaultFallback minHeight={minHeight} />}>
      {children}
    </Suspense>
  );
}

// Pre-configured lazy loaders for common heavy components
export const LazyDataTable = React.lazy(() => 
  import('@/shared/components/ui/data-display/DataTable')
);

// Higher-order component for lazy loading
export function withLazyLoading<T extends object>(
  Component: React.ComponentType<T>,
  fallback?: React.ReactNode
) {
  return function LazyLoadedComponent(props: T) {
    return (
      <LazyWrapper fallback={fallback}>
        <Component {...props} />
      </LazyWrapper>
    );
  };
}
