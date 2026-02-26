'use client';

import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from './i18n';
import { Container, Grid } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { ModuleCard } from '@/shared/components/ui/navigation/ModuleCard';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';

export default function PersonalFinancePage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('title'));

  return (
    <Page>
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 6, lg: 4 }}>
            <ModuleCard
              title={t('financeTransactions.title')}
              description={t('financeTransactions.description')}
              icon={<ReceiptLongIcon fontSize="large" />}
              href="/personal-finance/finance-transactions"
              actionLabel={t('actions.accessModule')}
            />
          </Grid>
        </Grid>
      </Container>
    </Page>
  );
}
