'use client';

import { Box, Button, Dialog, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

export type ApplyRuleToHistoricalDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  matchCount: number;
  loading?: boolean;
};

export function ApplyRuleToHistoricalDialog({
  open,
  onClose,
  onConfirm,
  matchCount,
  loading = false,
}: ApplyRuleToHistoricalDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      PaperProps={{ sx: { p: 2 } }}
      data-testid="apply-rule-to-history-dialog"
    >
      <Box sx={{ px: 2, pt: 2 }}>
        <Typography variant="h6" sx={{ mb: 0.5 }}>
          {t('financeTransactions.applyRuleToHistoryPrompt.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {t('financeTransactions.applyRuleToHistoryPrompt.description', { count: matchCount })}
        </Typography>

        <Box sx={{ mt: 5 }}>
          <Button
            onClick={onConfirm}
            color="primary"
            variant="contained"
            loading={loading}
            fullWidth
            data-testid="apply-rule-to-history-confirm"
            sx={{ mb: 1 }}
          >
            {t('financeTransactions.applyRuleToHistoryPrompt.confirm')}
          </Button>
          <Button
            onClick={onClose}
            variant="text"
            color="primary"
            disabled={loading}
            fullWidth
            data-testid="apply-rule-to-history-cancel"
          >
            {t('financeTransactions.applyRuleToHistoryPrompt.cancel')}
          </Button>
        </Box>
      </Box>
    </Dialog>
  );
}
