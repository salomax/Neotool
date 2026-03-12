'use client';

import React from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { useBreadcrumbLabel } from '@/shared/hooks/ui/useBreadcrumbLabel';

export default function CategoriesLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  useBreadcrumbLabel('categories', t('categories.title'));

  return <>{children}</>;
}
