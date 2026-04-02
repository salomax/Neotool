'use client';

import { Container } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';

export default function PricesConsumptionPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('dashboard.actions.pricesConsumption.title'));

  return (
    <Page>
      <Container maxWidth="xl" sx={{ py: 4 }} />
    </Page>
  );
}
