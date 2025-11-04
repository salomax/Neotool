import React from 'react';
import PageSkeleton from '@/shared/components/ui/primitives/PageSkeleton';

export default function DashboardLoading() {
  return <PageSkeleton data-testid="loading-dashboard" />;
}
