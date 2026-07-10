'use client';

import React, { type ReactNode } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { useBreadcrumbLabel } from '@/shared/hooks/ui/useBreadcrumbLabel';

export default function PurchaseWishlistLayout({ children }: { children: ReactNode }) {
  const { tCommon } = useTranslation(personalFinanceTranslations);
  useBreadcrumbLabel('purchase-wishlist', tCommon('routes.purchaseWishlist'));

  return children;
}
