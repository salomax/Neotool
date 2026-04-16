import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  List,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { CreditCard as CardIcon } from '@mui/icons-material';
import React, { memo } from 'react';
import dayjs from 'dayjs';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { DateTimePicker } from '@/shared/components/ui/primitives/DateTimePicker';
import { getReviewCardLabel, txSelectionKey } from '../billImportUtils';
import { TxReviewItem } from './TxReviewItem';
import type {
  BillDateOverrides,
  CardAccountOption,
  FlatTxAssignments,
  FlatTxInclusions,
  ReviewCardForTxReview,
  TxCategorySelections,
  TxSelections,
} from '../billImportTypes';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

interface TxReviewStepProps {
  cards: ReviewCardForTxReview[];
  txSelections: TxSelections;
  flatTxInclusions: FlatTxInclusions;
  flatTxAssignments: FlatTxAssignments;
  txCategorySelections: TxCategorySelections;
  billDateOverrides: BillDateOverrides;
  isFlatAssignmentMode: boolean;
  allCardsResolved: boolean;
  allCategories: TransactionCategory[];
  cardAccountOptions: CardAccountOption[];
  confirming: boolean;
  includedCount: number;
  onSetTxSelections: React.Dispatch<React.SetStateAction<TxSelections>>;
  onSetFlatTxInclusions: React.Dispatch<React.SetStateAction<FlatTxInclusions>>;
  onSetBillDateOverrides: React.Dispatch<React.SetStateAction<BillDateOverrides>>;
  onUpdateCategory: (key: string, categoryId: string | null) => void;
  onConfirm: () => void;
  onBack: () => void;
}

export const TxReviewStep = memo(function TxReviewStep({
  cards,
  txSelections,
  flatTxInclusions,
  flatTxAssignments,
  txCategorySelections,
  billDateOverrides,
  isFlatAssignmentMode,
  allCardsResolved,
  allCategories,
  cardAccountOptions,
  confirming,
  includedCount,
  onSetTxSelections,
  onSetFlatTxInclusions,
  onSetBillDateOverrides,
  onUpdateCategory,
  onConfirm,
  onBack,
}: TxReviewStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const hasBillClosingDate = billDateOverrides.closingDate.trim().length > 0;
  const hasBillDueDate = billDateOverrides.dueDate.trim().length > 0;
  const closingDateValue = hasBillClosingDate ? dayjs(billDateOverrides.closingDate) : null;
  const dueDateValue = hasBillDueDate ? dayjs(billDateOverrides.dueDate) : null;
  const dueDateBeforeClosingDate =
    !!closingDateValue && !!dueDateValue && dueDateValue.isBefore(closingDateValue, 'day');

  return (
    <Box sx={{ p: 1 }}>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.txReviewStep.description')}
      </Typography>

      {isFlatAssignmentMode && !allCardsResolved && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.txReviewStep.setupRequired')}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2.5 }}>
        <Stack spacing={1.5}>
          <Box>
            <Typography variant="subtitle2">
              {t('financeTransactions.billImport.txReviewStep.billDatesTitle')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('financeTransactions.billImport.txReviewStep.billDatesDescription')}
            </Typography>
          </Box>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <DateTimePicker
              showTime={false}
              showDate
              size="medium"
              label={t('financeTransactions.billImport.txReviewStep.closingDateLabel')}
              value={billDateOverrides.closingDate ? dayjs(billDateOverrides.closingDate) : null}
              onChange={(v) =>
                onSetBillDateOverrides((prev) => ({
                  ...prev,
                  closingDate: v ? v.format('YYYY-MM-DD') : '',
                }))
              }
              required
              helperText={t('financeTransactions.billImport.txReviewStep.closingDateHint')}
            />
            <DateTimePicker
              showTime={false}
              showDate
              size="medium"
              label={t('financeTransactions.billImport.txReviewStep.dueDateLabel')}
              value={billDateOverrides.dueDate ? dayjs(billDateOverrides.dueDate) : null}
              onChange={(v) =>
                onSetBillDateOverrides((prev) => ({
                  ...prev,
                  dueDate: v ? v.format('YYYY-MM-DD') : '',
                }))
              }
              minDateTime={closingDateValue ?? undefined}
              error={dueDateBeforeClosingDate}
              errorMessage={
                dueDateBeforeClosingDate
                  ? t('financeTransactions.billImport.txReviewStep.dueDateHint')
                  : undefined
              }
              helperText={
                !dueDateBeforeClosingDate
                  ? t('financeTransactions.billImport.txReviewStep.dueDateHint')
                  : undefined
              }
            />
          </Stack>
        </Stack>
      </Paper>

      {cards.map((card, ci) => {
        const account = cardAccountOptions.find((a) => a.id === card.financialAccountId);
        return (
          <Box key={card.reviewCardId} sx={{ mb: 3 }}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
              <CardIcon fontSize="small" color="action" />
              <Typography variant="subtitle2">
                {getReviewCardLabel(card, ci, t)}
                {account && (
                  <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                    — {account.name}
                  </Typography>
                )}
              </Typography>
            </Stack>

            <Paper variant="outlined">
              <List disablePadding dense>
                {card.transactions.map((tx, ti) => {
                  const sourceTxIndex = tx.__sourceTxIndex;
                  const key = isFlatAssignmentMode && sourceTxIndex !== undefined
                    ? `flat-${sourceTxIndex}`
                    : txSelectionKey(card.reviewCardId, ti);

                  const included = isFlatAssignmentMode && sourceTxIndex !== undefined
                    ? (flatTxInclusions[sourceTxIndex] ?? true)
                    : (txSelections[key] ?? true);

                  const categoryState = txCategorySelections[key] ?? {
                    categoryId: tx.categoryId ?? null,
                    categorySource: tx.categorySource ?? null,
                  };

                  return (
                    <TxReviewItem
                      key={key}
                      tx={tx}
                      ti={ti}
                      selectionKey={key}
                      included={included}
                      allCategories={allCategories}
                      categoryState={categoryState}
                      onToggleInclude={(checked) => {
                        if (isFlatAssignmentMode && sourceTxIndex !== undefined) {
                          onSetFlatTxInclusions((prev) => ({ ...prev, [sourceTxIndex]: checked }));
                        } else {
                          onSetTxSelections((prev) => ({ ...prev, [key]: checked }));
                        }
                      }}
                      onChangeCategory={onUpdateCategory}
                    />
                  );
                })}
              </List>
            </Paper>
          </Box>
        );
      })}

      <Divider sx={{ my: 3 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined" onClick={onBack}>
          {t('financeTransactions.billImport.common.back')}
        </Button>
        <Button
          variant="contained"
          onClick={onConfirm}
          disabled={
            confirming ||
            includedCount === 0 ||
            !hasBillClosingDate ||
            dueDateBeforeClosingDate ||
            !allCardsResolved
          }
          startIcon={confirming ? <CircularProgress size={18} color="inherit" /> : null}
        >
          {confirming
            ? t('financeTransactions.billImport.txReviewStep.confirming')
            : t('financeTransactions.billImport.txReviewStep.confirmButton', { count: includedCount })}
        </Button>
      </Stack>
    </Box>
  );
});
