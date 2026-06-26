import {
  Alert,
  Box,
  List,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { CreditCard as CardIcon } from '@mui/icons-material';
import React, { memo, type Dispatch, type SetStateAction } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslation } from '@/shared/i18n';
import { formatDateOnly } from '@/shared/utils/date/dateOnly';
import { personalFinanceTranslations } from '../../../i18n';
import { DateTimePicker } from '@/shared/components/ui/primitives/DateTimePicker';
import { getReviewCardLabel, txSelectionKey, getBillDateValidation } from '../billImportUtils';
import { TxReviewItem } from './TxReviewItem';
import type {
  BillDateOverrides,
  BillDateValidation,
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
  onSetTxSelections: Dispatch<SetStateAction<TxSelections>>;
  onSetFlatTxInclusions: Dispatch<SetStateAction<FlatTxInclusions>>;
  onSetBillDateOverrides: Dispatch<SetStateAction<BillDateOverrides>>;
  onUpdateCategory: (key: string, categoryId: string | null) => void;
  onBillDateValidationChange: (validation: BillDateValidation) => void;
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
  onSetTxSelections,
  onSetFlatTxInclusions,
  onSetBillDateOverrides,
  onUpdateCategory,
  onBillDateValidationChange,
}: TxReviewStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const invalidDateMessage = t('datePicker.errors.invalidDate', { ns: 'common' });

  const [closingDateInput, setClosingDateInput] = React.useState<Dayjs | null>(
    () => (billDateOverrides.closingDate ? dayjs(billDateOverrides.closingDate) : null),
  );
  const [dueDateInput, setDueDateInput] = React.useState<Dayjs | null>(
    () => (billDateOverrides.dueDate ? dayjs(billDateOverrides.dueDate) : null),
  );
  const serializedClosingDateInput =
    closingDateInput && closingDateInput.isValid() ? formatDateOnly(closingDateInput.toDate()) : '';
  const serializedDueDateInput =
    dueDateInput && dueDateInput.isValid() ? formatDateOnly(dueDateInput.toDate()) : '';

  React.useEffect(() => {
    if (billDateOverrides.closingDate === serializedClosingDateInput) return;
    setClosingDateInput(billDateOverrides.closingDate ? dayjs(billDateOverrides.closingDate) : null);
  }, [billDateOverrides.closingDate, serializedClosingDateInput]);

  React.useEffect(() => {
    if (billDateOverrides.dueDate === serializedDueDateInput) return;
    setDueDateInput(billDateOverrides.dueDate ? dayjs(billDateOverrides.dueDate) : null);
  }, [billDateOverrides.dueDate, serializedDueDateInput]);

  const handleBillDateChange = React.useCallback(
    (
      field: keyof BillDateOverrides,
      setInput: Dispatch<SetStateAction<Dayjs | null>>,
      nextValue: Dayjs | null,
    ) => {
      setInput(nextValue);
      onSetBillDateOverrides((prev) => ({
        ...prev,
        [field]: nextValue && nextValue.isValid() ? formatDateOnly(nextValue.toDate()) : '',
      }));
    },
    [onSetBillDateOverrides],
  );

  const billDateValidation = React.useMemo(
    () => getBillDateValidation(closingDateInput, dueDateInput),
    [closingDateInput, dueDateInput],
  );
  const {
    hasBillClosingDate,
    closingDateInvalid,
    dueDateInvalid,
    dueDateBeforeClosingDate,
  } = billDateValidation;
  const closingDateValue = closingDateInput && closingDateInput.isValid() ? closingDateInput : null;
  const dueDateValue = dueDateInput && dueDateInput.isValid() ? dueDateInput : null;

  React.useEffect(() => {
    onBillDateValidationChange(billDateValidation);
  }, [billDateValidation, onBillDateValidationChange]);

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
              value={closingDateInput}
              onChange={(v) => handleBillDateChange('closingDate', setClosingDateInput, v)}
              required
              error={closingDateInvalid}
              errorMessage={closingDateInvalid ? invalidDateMessage : undefined}
              helperText={
                !closingDateInvalid
                  ? t('financeTransactions.billImport.txReviewStep.closingDateHint')
                  : undefined
              }
            />
            <DateTimePicker
              showTime={false}
              showDate
              size="medium"
              label={t('financeTransactions.billImport.txReviewStep.dueDateLabel')}
              value={dueDateInput}
              onChange={(v) => handleBillDateChange('dueDate', setDueDateInput, v)}
              minDateTime={closingDateValue ?? undefined}
              error={dueDateInvalid || dueDateBeforeClosingDate}
              errorMessage={
                dueDateInvalid
                  ? invalidDateMessage
                  : dueDateBeforeClosingDate
                  ? t('financeTransactions.billImport.txReviewStep.dueDateHint')
                  : undefined
              }
              helperText={
                !dueDateInvalid && !dueDateBeforeClosingDate
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
    </Box>
  );
});
