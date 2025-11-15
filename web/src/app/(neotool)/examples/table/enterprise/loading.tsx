import React from 'react';
import PageSkeleton from '@/shared/components/ui/primitives/PageSkeleton';

export default function EnterpriseLoading() {
  return <PageSkeleton data-testid="loading-enterprise-table" />;
}
