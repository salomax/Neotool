import { Box, Checkbox, Chip, Divider, ListItem, ListItemAvatar, ListItemText, Stack, Typography } from '@mui/material';
import { SwapHoriz as TransferIcon } from '@mui/icons-material';
import { memo } from 'react';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { CategorySelect } from '../../category/CategorySelect';
import { formatTxDisplayDate } from '../billImportUtils';
import type { ReviewTransaction, TxCategorySource } from '../billImportTypes';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

interface TxReviewItemProps {
  tx: ReviewTransaction & { __sourceTxIndex?: number };
  ti: number;
  selectionKey: string;
  included: boolean;
  allCategories: TransactionCategory[];
  categoryState: { categoryId: string | null; categorySource: TxCategorySource };
  onToggleInclude: (checked: boolean) => void;
  onChangeCategory: (key: string, categoryId: string | null) => void;
}

export const TxReviewItem = memo(function TxReviewItem({
  tx,
  ti,
  selectionKey,
  included,
  allCategories,
  categoryState,
  onToggleInclude,
  onChangeCategory,
}: TxReviewItemProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const isDebit = tx.creditDebitType === 'DEBIT';
  const isBillPayment = !!tx.isBillPayment;

  return (
    <>
      {ti > 0 && <Divider />}
      <ListItem
        sx={{ opacity: included ? 1 : 0.4, py: 0.75 }}
        secondaryAction={
          <Checkbox
            edge="end"
            checked={included}
            onChange={(e) => onToggleInclude(e.target.checked)}
            size="medium"
          />
        }
      >
        <ListItemAvatar sx={{ minWidth: 36 }}>
          {isBillPayment ? (
            <TransferIcon fontSize="small" color="info" />
          ) : (
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: isDebit ? 'error.main' : 'success.main',
                mt: 0.5,
              }}
            />
          )}
        </ListItemAvatar>
        <ListItemText
          primary={
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Typography variant="body2" noWrap sx={{ flex: 1 }}>
                {tx.description}
              </Typography>
              {isBillPayment && (
                <Chip
                  label={t('financeTransactions.billImport.txReviewStep.paymentChip')}
                  size="medium"
                  color="info"
                  variant="outlined"
                  sx={{ fontSize: 10 }}
                />
              )}
              {!isBillPayment && (
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <CategorySelect
                    value={categoryState.categoryId}
                    onChange={(categoryId) => onChangeCategory(selectionKey, categoryId)}
                    categories={allCategories}
                    creditDebitType={
                      tx.creditDebitType === 'CREDIT' || tx.creditDebitType === 'DEBIT' || tx.creditDebitType === 'TRANSFER'
                        ? tx.creditDebitType
                        : 'DEBIT'
                    }
                  />
                </Stack>
              )}
              {tx.installment && (
                <Chip
                  label={`${tx.installment.current}/${tx.installment.total}`}
                  size="medium"
                  variant="outlined"
                  sx={{ fontSize: 10 }}
                />
              )}
            </Stack>
          }
          secondary={
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="caption" color="text.secondary">
                {formatTxDisplayDate(tx.date, i18n.language)}
              </Typography>
              <Typography
                variant="caption"
                fontWeight="medium"
                color={isDebit ? 'error.main' : 'success.main'}
              >
                {isDebit ? '- ' : '+ '}
                {tx.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
              </Typography>
            </Stack>
          }
        />
      </ListItem>
    </>
  );
});
