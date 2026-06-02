import { Box, Skeleton, Stack, Typography, useTheme } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionDirectionBadge, resolveTransactionDirectionVisuals } from '@/shared/components/ui/data-display';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { interactiveRowSx, makeKeyDownHandler } from './interactiveRowUtils';

interface SummaryMetricRowProps {
  creditDebitType: CreditDebitType;
  transactionType: TransactionType;
  label: string;
  value: number | undefined;
  percentage?: number;
  isLoading?: boolean;
  formatCurrency: (value: number) => string;
  dimmed?: boolean;
  onActivate?: () => void;
  testId?: string;
  /** E2E: amount-only test id (`tests/e2e/helpers/currency-dom.ts`). */
  amountTestId?: string;
}

export const SummaryMetricRow = memo(function SummaryMetricRow({
  creditDebitType,
  transactionType,
  label,
  value,
  percentage,
  isLoading = false,
  formatCurrency,
  dimmed = false,
  onActivate,
  testId,
  amountTestId,
}: SummaryMetricRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const totalSuffix = t('financeTransactions.monthSummary.total');
  const disabledFg = theme.palette.text.disabled;
  const visuals = resolveTransactionDirectionVisuals(theme, creditDebitType, transactionType);
  const accentColor = visuals?.color ?? theme.palette.text.primary;
  const interactive = Boolean(onActivate) && !dimmed && !isLoading;

  return (
    <Box
      data-testid={testId}
      onClick={interactive ? onActivate : undefined}
      onKeyDown={interactive ? makeKeyDownHandler(onActivate!) : undefined}
      role={interactive ? 'button' : dimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={dimmed || undefined}
      aria-label={
        interactive
          ? `${t('financeTransactions.monthSummary.a11y.filterByMetric')} ${label}`
          : undefined
      }
      sx={interactiveRowSx(theme, interactive, dimmed)}
    >
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <TransactionDirectionBadge
          creditDebitType={creditDebitType}
          transactionType={transactionType}
          dimmed={dimmed}
          sx={{ mr: 1.5 }}
        />
        <Box sx={{ flexGrow: 1 }}>
          <Stack spacing={0.3}>
            <Typography
              variant="subtitle2"
              sx={{ fontWeight: 600, lineHeight: 1.2, whiteSpace: 'pre-line', color: dimmed ? disabledFg : 'text.primary' }}
            >
              {label}
            </Typography>
            {percentage !== undefined && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
              >
                {isLoading ? <Skeleton width={60} /> : `${percentage}% ${totalSuffix}`}
              </Typography>
            )}
          </Stack>
        </Box>
        <Typography
          variant="subtitle2"
          data-testid={amountTestId}
          sx={{ fontWeight: 700, color: dimmed ? disabledFg : accentColor }}
        >
          {isLoading ? <Skeleton width={88} height={22} /> : formatCurrency(value ?? 0)}
        </Typography>
      </Box>
    </Box>
  );
});
