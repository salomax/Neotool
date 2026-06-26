'use client';

import { Fragment } from 'react';
import {
  Alert,
  alpha,
  Box,
  Button,
  Stack,
  Typography,
  useTheme,
} from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../i18n';
import { useTranslation } from '@/shared/i18n';

interface ImportAlertState {
  error?: unknown;
  accountId: string | null;
  onRetry: () => void;
  reviewRequiredJobs: Array<{ id: string; duplicateReviewCount: number | null }> | null;
  onOpenReviewJobs: (jobIds: string[]) => void;
}

interface FinanceTransactionsHeaderBarProps {
  importAlerts: ImportAlertState;
}

export function FinanceTransactionsHeaderBar({
  importAlerts: {
    error: importsError,
    accountId: effectiveSelectedAccountId,
    onRetry: onRetryImports,
    reviewRequiredJobs,
    onOpenReviewJobs,
  },
}: FinanceTransactionsHeaderBarProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  return (
    <Fragment>
      {Boolean(importsError) && effectiveSelectedAccountId && (
        <Alert
          severity="warning"
          sx={{ width: '100%', mb: 2 }}
          action={
            <Button size="small" onClick={onRetryImports}>
              {tCommon('cardGrid.retry')}
            </Button>
          }
        >
          {String(extractErrorMessage(importsError, tCommon('errors.default')))}
        </Alert>
      )}
      {reviewRequiredJobs && reviewRequiredJobs.length > 0 && (
        <Box
          sx={{
            width: '100%',
            mb: 4,
            borderRadius: 1.5,
            border: '1px solid',
            borderColor: alpha(theme.palette.warning.main, 0.35),
            bgcolor: 'background.paper',
            px: { xs: 1.5, sm: 2 },
            py: { xs: 1.25, sm: 1.5 },
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 2,
          }}
        >
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: alpha(theme.palette.warning.main, theme.palette.mode === 'dark' ? 0.22 : 0.12),
                color: 'warning.main',
                flexShrink: 0,
              }}
            >
              <WarningAmberIcon fontSize="small" />
            </Box>
            <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
              {t('financeTransactions.duplicateReview.header', {
                count: reviewRequiredJobs.reduce((sum, j) => sum + (j.duplicateReviewCount ?? 1), 0),
              })}
            </Typography>
          </Stack>

          <Button
            size="medium"
            variant="text"
            onClick={() => onOpenReviewJobs(reviewRequiredJobs.map((j) => j.id))}
            endIcon={<ChevronRightIcon fontSize="small" />}
            sx={{
              fontWeight: 600,
              color: 'warning.main',
              flexShrink: 0,
              '&:hover': { bgcolor: alpha(theme.palette.warning.main, theme.palette.mode === 'dark' ? 0.18 : 0.08) },
            }}
          >
            {t('financeTransactions.duplicateReview.bannerAction')}
          </Button>
        </Box>
      )}
    </Fragment>
  );
}
