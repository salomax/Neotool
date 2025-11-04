"use client";

import React, { Suspense } from 'react';
import { CircularProgress, Box } from '@/shared/ui/mui-imports';

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
    <CircularProgress />
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

export const LazyEnterpriseTable = React.lazy(() => 
  import('@/shared/components/ui/data-table/components/enterprise/EnterpriseTable')
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
