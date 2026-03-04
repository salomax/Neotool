import React from 'react';
import { Box, Stack, Typography, Skeleton, useTheme, alpha, Avatar } from '@mui/material';
import { 
  ArrowUpward as IncomeIcon, 
  ArrowDownward as ExpenseIcon, 
  SwapVert as BalanceIcon 
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

export interface MonthSummaryData {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  closingBalance: number;
  openingBalance: number;
  transfersIn: number;
  transfersOut: number;
  projectedClosingBalance: number | null;
  isCurrentMonth: boolean;
}

interface MonthSummaryProps {
  summary: MonthSummaryData | undefined;
  loading: boolean;
}

export const MonthSummary: React.FC<MonthSummaryProps> = ({
  summary,
  loading
}) => {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL', // Assuming BRL for now, or could come from account/user prefs
    }).format(value);
  };

  const calculatePercentage = (value: number, total: number) => {
    if (!total || total === 0) return 0;
    return Math.round((value / total) * 100);
  };

  const renderItem = (
    icon: React.ReactNode,
    color: string,
    label: string,
    value: number | undefined,
    percentage?: number,
    isLoading: boolean = false
  ) => {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
        <Avatar
          variant="rounded"
          sx={{
            bgcolor: alpha(color, 0.1),
            color: color,
            mr: 2,
            width: 48,
            height: 48,
          }}
        >
          {icon}
        </Avatar>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            {label}
          </Typography>
          {percentage !== undefined && (
            <Typography variant="caption" color="text.secondary">
              {isLoading ? (
                <Skeleton width={60} />
              ) : (
                `${percentage}% ${t('financeTransactions.monthSummary.total')}`
              )}
            </Typography>
          )}
        </Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          {isLoading ? (
            <Skeleton width={100} />
          ) : (
            formatCurrency(value || 0)
          )}
        </Typography>
      </Box>
    );
  };

  if (loading && !summary) {
    return (
      <Box>
        {renderItem(<IncomeIcon />, theme.palette.success.main, t('financeTransactions.monthSummary.income'), 0, 0, true)}
        {renderItem(<ExpenseIcon />, theme.palette.error.main, t('financeTransactions.monthSummary.expenses'), 0, 0, true)}
        {renderItem(<BalanceIcon />, theme.palette.primary.main, t('financeTransactions.monthSummary.balance'), 0, undefined, true)}
      </Box>
    );
  }

  const income = summary?.totalIncome || 0;
  const expenses = summary?.totalExpenses || 0;
  const balance = summary?.closingBalance || 0;

  const absIncome = Math.abs(income);
  const absExpenses = Math.abs(expenses);
  const totalVolume = absIncome + absExpenses;

  const incomePercentage = totalVolume > 0 ? calculatePercentage(absIncome, totalVolume) : 0;
  const expensesPercentage = totalVolume > 0 ? calculatePercentage(absExpenses, totalVolume) : 0;

  return (
    <Box>
      {renderItem(
        <IncomeIcon />, 
        theme.palette.success.main, 
        t('financeTransactions.monthSummary.income'), 
        income, 
        incomePercentage
      )}
      {renderItem(
        <ExpenseIcon />, 
        theme.palette.error.main, 
        t('financeTransactions.monthSummary.expenses'), 
        expenses, 
        expensesPercentage
      )}

      {totalVolume > 0 && (
        <Box 
          sx={{ 
            mt: 2,
            mb: 2, 
            height: 4, 
            borderRadius: 2, 
            display: 'flex', 
            overflow: 'hidden', 
            width: '100%', 
            bgcolor: theme.palette.grey[100] 
          }}
        >
          <Box 
            sx={{ 
              flex: absIncome, 
              bgcolor: theme.palette.success.main,
              transition: 'flex 0.5s ease-in-out'
            }} 
          />
          <Box 
            sx={{ 
              flex: absExpenses, 
              bgcolor: theme.palette.error.main,
              transition: 'flex 0.5s ease-in-out'
            }} 
          />
        </Box>
      )}

      {renderItem(
        <BalanceIcon />, 
        theme.palette.primary.main, 
        t('financeTransactions.monthSummary.balance'), 
        balance
      )}
    </Box>
  );
};
