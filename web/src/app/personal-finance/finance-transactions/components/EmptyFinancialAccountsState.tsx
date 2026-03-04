import React from 'react';
import { Box, Typography, Container } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { FinancialAccountTypeSelection } from './FinancialAccountTypeSelection';

interface EmptyFinancialAccountsStateProps {
  onSelectType: (type: 'BANK' | 'CARD') => void;
}

export function EmptyFinancialAccountsState({ onSelectType }: EmptyFinancialAccountsStateProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Container maxWidth="md" sx={{ py: 8, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
      <Box sx={{ mb: 6, maxWidth: 'sm', mx: 'auto' }}>
        <Typography 
            variant="h4" 
            component="h2" 
            color="primary.main" 
            fontWeight="bold" 
            sx={{ mb: 2, letterSpacing: '-0.025em' }}
        >
          {t('financeTransactions.emptyState.title')}
        </Typography>
        <Typography 
            variant="body1" 
            color="text.secondary" 
            sx={{ fontSize: '1.125rem', lineHeight: 1.6 }}
        >
          {t('financeTransactions.emptyState.description')}
        </Typography>
      </Box>

      <Box sx={{ width: '100%', maxWidth: 800 }}>
        <FinancialAccountTypeSelection onSelect={onSelectType} />
      </Box>
    </Container>
  );
}
