import React from 'react';
import { Box, Card, Typography, useTheme } from '@mui/material';
import { 
  AccountBalance as BankIcon, 
  CreditCard as CardIcon,
  ArrowBack as BackIcon
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface FinancialAccountTypeSelectionProps {
  onSelect: (type: 'BANK' | 'CARD') => void;
}

export function FinancialAccountTypeSelection({ onSelect }: FinancialAccountTypeSelectionProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3, width: '100%' }}>
      {/* Bank Account Card */}
      <Card 
        elevation={0}
        sx={{ 
          cursor: 'pointer', 
          border: 1, 
          borderColor: 'divider',
          borderRadius: 3,
          p: 3,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          gap: 2,
          transition: 'all 0.2s',
          boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
          '&:hover': { 
            bgcolor: 'action.hover',
            transform: 'translateY(-2px)',
            boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)'
          }
        }}
        onClick={() => onSelect('BANK')}
      >
        <Box sx={{ 
          width: 56, 
          height: 56, 
          borderRadius: 3, 
          bgcolor: 'action.selected',
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          color: 'text.secondary',
          transition: 'background-color 0.3s',
          '.MuiCard-root:hover &': {
            bgcolor: 'action.focus'
          }
        }}>
          <BankIcon sx={{ fontSize: 30 }} />
        </Box>
        <Box>
          <Typography variant="h6" color="text.secondary" fontWeight="bold" gutterBottom>
            {t('financeTransactions.createAccount.step1.bankAccount.label')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
            {t('financeTransactions.createAccount.step1.bankAccount.subtitle')}
          </Typography>
        </Box>
        <Box sx={{ mt: 'auto', display: 'flex', alignItems: 'center', color: 'primary.main', fontWeight: 'bold', fontSize: '0.875rem', gap: 1 }}>
          <span>{t('financeTransactions.createAccount.step1.action')}</span>
          <BackIcon sx={{ transform: 'rotate(180deg)', fontSize: '1rem' }} />
        </Box>
      </Card>

      {/* Credit Card Card */}
      <Card 
        elevation={0}
        sx={{ 
          cursor: 'pointer', 
          border: 1, 
          borderColor: 'divider',
          borderRadius: 3,
          p: 3,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          gap: 2,
          transition: 'all 0.2s',
          boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
          '&:hover': { 
            bgcolor: 'action.hover',
            transform: 'translateY(-2px)',
            boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)'
          }
        }}
        onClick={() => onSelect('CARD')}
      >
        <Box sx={{ 
          width: 56, 
          height: 56, 
          borderRadius: 3, 
          bgcolor: 'action.selected',
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          color: 'text.secondary',
          transition: 'background-color 0.3s',
          '.MuiCard-root:hover &': {
            bgcolor: 'action.focus'
          }
        }}>
          <CardIcon sx={{ fontSize: 30 }} />
        </Box>
        <Box>
          <Typography variant="h6" color="text.secondary" fontWeight="bold" gutterBottom>
            {t('financeTransactions.createAccount.step1.card.label')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
            {t('financeTransactions.createAccount.step1.card.subtitle')}
          </Typography>
        </Box>
        <Box sx={{ mt: 'auto', display: 'flex', alignItems: 'center', color: 'primary.main', fontWeight: 'bold', fontSize: '0.875rem', gap: 1 }}>
          <span>{t('financeTransactions.createAccount.step1.action')}</span>
          <BackIcon sx={{ transform: 'rotate(180deg)', fontSize: '1rem' }} />
        </Box>
      </Card>
    </Box>
  );
}
