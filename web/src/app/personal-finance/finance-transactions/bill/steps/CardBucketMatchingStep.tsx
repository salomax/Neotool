import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem as SelectMenuItem,
  Paper,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  CheckCircle as CheckIcon,
  CreditCard as CardIcon,
  HelpOutline as UnmatchedIcon,
} from '@mui/icons-material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { getReviewCardLabel } from '../billImportUtils';
import type { CardAccountOption, CardResolution, ReviewCard } from '../billImportTypes';

interface CardBucketMatchingStepProps {
  reviewCards: ReviewCard[];
  cardResolutions: CardResolution[];
  cardAccountOptionsById: Map<string, CardAccountOption>;
  selectedIssuerKey: string | null;
  getAvailableOptionsForCard: (card: ReviewCard) => CardAccountOption[];
  onResolveCard: (ci: number, accountId: string | null) => void;
  onCreateCardForIndex: (ci: number) => void;
}

export const CardBucketMatchingStep = memo(function CardBucketMatchingStep({
  reviewCards,
  cardResolutions,
  cardAccountOptionsById,
  selectedIssuerKey,
  getAvailableOptionsForCard,
  onResolveCard,
  onCreateCardForIndex,
}: CardBucketMatchingStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box sx={{ p: 1 }}>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.cardMatchingStep.description')}
      </Typography>

      {reviewCards.map((card, ci) => {
        const resolution = cardResolutions[ci];
        const isResolved = !!resolution?.financialAccountId;
        const needsUserInput = !isResolved;
        const availableOptions = getAvailableOptionsForCard(card);

        return (
          <Paper variant="outlined" key={card.reviewCardId} sx={{ mb: 2, p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 1.5 }}>
              <Avatar sx={{ bgcolor: 'primary.light', width: 36, height: 36 }}>
                <CardIcon fontSize="small" />
              </Avatar>
              <Box sx={{ flex: 1 }}>
                <Typography variant="body1" fontWeight="medium">
                  {getReviewCardLabel(card, ci, t)}
                </Typography>
                {card.holderName && (
                  <Typography variant="caption" color="text.secondary">{card.holderName}</Typography>
                )}
              </Box>
              <Chip
                size="medium"
                icon={isResolved ? <CheckIcon /> : <UnmatchedIcon />}
                label={
                  isResolved
                    ? t('financeTransactions.billImport.cardMatchingStep.status.associated')
                    : t('financeTransactions.billImport.cardMatchingStep.status.pending')
                }
                color={isResolved ? 'success' : 'warning'}
                variant="outlined"
              />
            </Stack>

            {card.matchAmbiguous && (
              <Alert severity="warning" sx={{ mb: 1.5 }}>
                {t('financeTransactions.billImport.cardMatchingStep.ambiguousWarning', {
                  lastFourDigits: card.lastFourDigits ?? '----',
                })}
              </Alert>
            )}

            <FormControl fullWidth size="medium">
              <InputLabel>{t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}</InputLabel>
              <Select
                label={t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}
                value={resolution?.financialAccountId ?? ''}
                onChange={(e) => {
                  const val = e.target.value as string;
                  if (val && selectedIssuerKey) {
                    const selectedAccount = cardAccountOptionsById.get(val);
                    if (selectedAccount?.issuerKey !== selectedIssuerKey) return;
                  }
                  onResolveCard(ci, val || null);
                }}
              >
                <SelectMenuItem value="" disabled>
                  <em>{t('financeTransactions.billImport.cardMatchingStep.cardAccountPlaceholder')}</em>
                </SelectMenuItem>
                {availableOptions.map((opt) => (
                  <SelectMenuItem key={opt.id} value={opt.id}>{opt.name}</SelectMenuItem>
                ))}
              </Select>
            </FormControl>

            {needsUserInput && !card.matchAmbiguous && (
              <Button
                size="medium"
                startIcon={<AddIcon />}
                sx={{ mt: 1 }}
                onClick={() => onCreateCardForIndex(ci)}
              >
                {t('financeTransactions.billImport.cardMatchingStep.createNewCard')}
              </Button>
            )}

            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
              {t('financeTransactions.billImport.cardMatchingStep.txSummary', {
                count: card.transactions.length,
                total: card.computedTotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
              })}
            </Typography>
          </Paper>
        );
      })}
    </Box>
  );
});
