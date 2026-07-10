'use client';

import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { PurchaseWishlistList } from './PurchaseWishlistList';

export default function PurchaseWishlistPage() {
  const { tCommon } = useTranslation(personalFinanceTranslations);
  usePageTitle(tCommon('routes.purchaseWishlist'));

  return <PurchaseWishlistList />;
}
