import React from 'react';
import { Box, Stack, Typography, Skeleton, useTheme, alpha, Avatar, Divider } from '@mui/material';
import { 
  ArrowUpward as IncomeIcon, 
  ArrowDownward as ExpenseIcon, 
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';

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
  isConsolidated?: boolean;
}

export const MonthSummary: React.FC<MonthSummaryProps> = ({
  summary,
  loading,
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

  const renderItem = ({
    icon,
    color,
    label,
    value,
    percentage,
    isLoading = false,
  }: {
    icon: React.ReactNode;
    color: string;
    label: string;
    value: number | undefined;
    percentage?: number;
    isLoading?: boolean;
  }) => {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
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
          <Stack spacing={0.5}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
              {label}
            </Typography>
            {percentage !== undefined && (
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
                {isLoading ? (
                  <Skeleton width={60} />
                ) : (
                  `${percentage}% ${t('financeTransactions.monthSummary.total')}`
                )}
              </Typography>
            )}
          </Stack>
        </Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, color: color }}>
          {isLoading ? (
            <Skeleton width={100} />
          ) : (
            formatCurrency(value || 0)
          )}
        </Typography>
      </Box>
    );
  };

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
      <Box sx={{ mb: 2 }}>
        <Label>
          {t('financeTransactions.monthSummary.balance')}
        </Label>
        <Typography 
          variant="h3" 
          component="div"
          sx={{ 
            fontWeight: 700, 
            mt: 0.5,
            fontSize: '2.3rem',
            color: theme.palette.text.primary
          }}
        >
          {loading && !summary ? (
            <Skeleton width={200} />
          ) : (
            new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
              .formatToParts(balance)
              .map((part, index) => {
                const isMain = part.type === 'integer' || part.type === 'group';
                return (
                  <Box
                    key={index}
                    component="span"
                    sx={{
                      fontSize: isMain ? 'inherit' : '0.6em',
                      opacity: isMain ? 1 : 0.7,
                      mr: part.type === 'currency' ? 0.5 : 0,
                    }}
                  >
                    {part.value}
                  </Box>
                );
              })
          )}
        </Typography>
      </Box>

      <Divider sx={{ mb: 3 }} />

      <Stack spacing={3}>
        {renderItem({ 
          icon: <IncomeIcon />, 
          color: theme.palette.success.main, 
          label: t('financeTransactions.monthSummary.income'), 
          value: income, 
          percentage: incomePercentage,
          isLoading: loading && !summary
        })}
        
        {renderItem({ 
          icon: <ExpenseIcon />, 
          color: theme.palette.error.main, 
          label: t('financeTransactions.monthSummary.expenses'), 
          value: expenses, 
          percentage: expensesPercentage,
          isLoading: loading && !summary
        })}
      </Stack>

      {(totalVolume > 0 || (loading && !summary)) && (
        <Box 
          sx={{ 
            mt: 3,
            height: 6, 
            borderRadius: 3, 
            display: 'flex', 
            overflow: 'hidden', 
            width: '100%', 
            bgcolor: theme.palette.grey[100] 
          }}
        >
           {loading && !summary ? (
              <Skeleton variant="rectangular" width="100%" height="100%" />
           ) : (
             <>
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
             </>
           )}
        </Box>
      )}
    </Box>
  );
};
