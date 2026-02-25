'use client';

import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { Container, Typography, Box, Paper } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import ConstructionIcon from '@mui/icons-material/Construction';

export default function FinanceTransactionsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      sidebar={
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ p: 3, pb: 2 }}>
            <Typography variant="h6">{t('financeTransactions.title')}</Typography>
          </Box>
        </Box>
      }
    >
      <Page>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          <Paper 
            variant="outlined" 
            sx={{ 
              p: 8, 
              display: 'flex', 
              flexDirection: 'column', 
              alignItems: 'center', 
              justifyContent: 'center',
              textAlign: 'center',
              gap: 2,
              backgroundColor: 'background.default',
              borderStyle: 'dashed'
            }}
          >
            <Box sx={{ color: 'text.secondary', opacity: 0.5 }}>
              <ConstructionIcon sx={{ fontSize: 64 }} />
            </Box>
            <Typography variant="h5" color="text.secondary">
              {t('financeTransactions.title')}
            </Typography>
            <Typography variant="body1" color="text.secondary">
              Work in progress
            </Typography>
          </Paper>
        </Container>
      </Page>
    </SidebarLayout>
  );
}
