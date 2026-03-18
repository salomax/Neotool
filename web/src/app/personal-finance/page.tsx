'use client';

import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from './i18n';
import { Container } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { DashboardLayout } from '@/features/personal-finance/dashboard';

export default function PersonalFinancePage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('title'));

  return (
    <Page>
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <DashboardLayout />
      </Container>
    </Page>
  );
}
