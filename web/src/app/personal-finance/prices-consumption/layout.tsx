'use client';

import React from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { useBreadcrumbLabel } from '@/shared/hooks/ui/useBreadcrumbLabel';

export default function PricesConsumptionLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  useBreadcrumbLabel('prices-consumption', t('dashboard.actions.pricesConsumption.title'));

  return <>{children}</>;
}
