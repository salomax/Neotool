import React from 'react';
import { Box, Stack, Typography, Skeleton, useTheme, alpha, Avatar, Divider, Button, LinearProgress } from '@mui/material';
import {
  ArrowUpward as IncomeIcon,
  ArrowDownward as ExpenseIcon,
  ErrorOutline as UnavailableIcon,
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { extractErrorMessage } from '@/shared/utils/error';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { CategoryIcon } from './category/CategoryIcon';

export interface CategoryTotalItem {
  category: { id: string; name: string; icon: string | null; color: string | null };
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

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
  isFutureMonth?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
}

interface MonthSummaryProps {
  summary: MonthSummaryData | undefined;
  loading: boolean;
  error?: unknown;
  onRetry?: () => void;
  isConsolidated?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  /** Called when user clicks a category row to filter transactions by category. */
  onCategoryClick?: (categoryId: string) => void;
}

export const MonthSummary: React.FC<MonthSummaryProps> = ({
  summary,
  loading,
  error,
  onRetry,
  expensesByCategory,
  incomeByCategory,
  onCategoryClick,
}) => {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [drawerState, setDrawerState] = React.useState<{ open: boolean; kind: 'expenses' | 'income' | null }>({
    open: false,
    kind: null,
  });

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

  const renderCategorySummary = ({
    title,
    emptyText,
    items,
    kind,
  }: {
    title: string;
    emptyText: string;
    items: CategoryTotalItem[];
    kind: 'expenses' | 'income';
  }) => {
    const topItems = items.slice(0, 5);
    const canSeeAll = items.length > 5;

    return (
      <Box>
        <Divider sx={{ mb: 3 }}/>
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            {title}
          </Typography>
          {canSeeAll && (
            <Button
              variant="text"
              size="small"
              onClick={() => setDrawerState({ open: true, kind })}
              sx={{
                p: 0,
                minWidth: 'auto',
                textTransform: 'uppercase',
                fontWeight: 500,
                letterSpacing: 0.6,
                fontSize: '0.75rem',
              }}
            >
              {t('financeTransactions.monthSummary.seeAll')}
            </Button>
          )}
        </Box>

        {items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {emptyText}
          </Typography>
        ) : (
          <Stack spacing={2}>
            {topItems.map((item) => (
              <Box
                key={item.category.id}
                onClick={() => onCategoryClick?.(item.category.id)}
                sx={{
                  cursor: onCategoryClick ? 'pointer' : 'default',
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                    <CategoryIcon
                      iconName={item.category.icon}
                      color={item.category.color ?? undefined}
                      size={18}
                      sx={{ flexShrink: 0 }}
                    />
                    <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                      {item.category.name}
                    </Typography>
                  </Box>
                  <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                    {Math.round(item.percentage)}%
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(item.percentage, 100)}
                  sx={{
                    mt: 1,
                    height: 6,
                    borderRadius: 2,
                    bgcolor: alpha(theme.palette.text.primary, 0.08),
                    '& .MuiLinearProgress-bar': {
                      bgcolor: item.category.color ?? theme.palette.primary.main,
                      borderRadius: 2,
                    },
                  }}
                />
              </Box>
            ))}
          </Stack>
        )}
      </Box>
    );
  };

  if (error && !loading && !summary) {
    const defaultErrorMessage = tCommon('errors.default');
    const extracted = extractErrorMessage(error, defaultErrorMessage);
    const description = extracted === defaultErrorMessage ? t('financeTransactions.monthSummary.unavailableDescription') : extracted;

    return (
      <Box
        sx={{
          minHeight: 280,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          px: 2,
        }}
      >
        <Stack spacing={1.5} alignItems="center" textAlign="center" maxWidth={320}>
          <Avatar
            variant="rounded"
            sx={{
              bgcolor: alpha(theme.palette.warning.main, 0.12),
              color: theme.palette.warning.main,
              width: 52,
              height: 52,
            }}
          >
            <UnavailableIcon />
          </Avatar>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            {t('financeTransactions.monthSummary.unavailableTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
          {onRetry && (
            <Button variant="outlined" size="small" onClick={onRetry}>
              {t('financeTransactions.monthSummary.retry')}
            </Button>
          )}
        </Stack>
      </Box>
    );
  }

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
            bgcolor: theme.palette.grey[100],
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
                  transition: 'flex 0.5s ease-in-out',
                }}
              />
              <Box
                sx={{
                  flex: absExpenses,
                  bgcolor: theme.palette.error.main,
                  transition: 'flex 0.5s ease-in-out',
                }}
              />
            </>
          )}
        </Box>
      )}

      {summary && !loading && expensesByCategory !== undefined && incomeByCategory !== undefined && (
        <Stack spacing={3} sx={{ mt: 3 }}>
          {renderCategorySummary({
            title: t('financeTransactions.monthSummary.expensesByCategory'),
            emptyText: t('financeTransactions.monthSummary.noCategorizedExpenses'),
            items: expensesByCategory,
            kind: 'expenses',
          })}
          {renderCategorySummary({
            title: t('financeTransactions.monthSummary.incomeByCategory'),
            emptyText: t('financeTransactions.monthSummary.noCategorizedIncome'),
            items: incomeByCategory,
            kind: 'income',
          })}
        </Stack>
      )}

      <Drawer
        open={drawerState.open}
        onClose={() => setDrawerState({ open: false, kind: null })}
        anchor="right"
        size="sm"
      >
        <Drawer.Header
          title={
            drawerState.kind === 'expenses'
              ? t('financeTransactions.monthSummary.expensesByCategory')
              : t('financeTransactions.monthSummary.incomeByCategory')
          }
          showCloseButton
        />
        <Drawer.Body>
          <Stack spacing={2}>
            {(drawerState.kind === 'expenses' ? expensesByCategory : incomeByCategory)?.map((item) => (
              <Box
                key={item.category.id}
                onClick={() => onCategoryClick?.(item.category.id)}
                sx={{
                  cursor: onCategoryClick ? 'pointer' : 'default',
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                    <CategoryIcon
                      iconName={item.category.icon}
                      color={item.category.color ?? undefined}
                      size={18}
                      sx={{ flexShrink: 0 }}
                    />
                    <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                      {item.category.name}
                    </Typography>
                  </Box>
                  <Typography variant="body2" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                    {Math.round(item.percentage)}%
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(item.percentage, 100)}
                  sx={{
                    mt: 1,
                    height: 6,
                    borderRadius: 2,
                    bgcolor: alpha(theme.palette.text.primary, 0.08),
                    '& .MuiLinearProgress-bar': {
                      bgcolor: item.category.color ?? theme.palette.primary.main,
                      borderRadius: 2,
                    },
                  }}
                />
              </Box>
            ))}
          </Stack>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};
