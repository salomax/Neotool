import {
  Alert,
  Avatar,
  Box,
  Chip,
  Link,
  Paper,
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
import type { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { AccountSelect } from '../../transaction/AccountSelect';
import { cardAccountOptionToFinancialAccount, getReviewCardLabel } from '../billImportUtils';
import type { CardAccountOption, CardResolution, ReviewCard } from '../billImportTypes';

interface CardBucketMatchingStepProps {
  reviewCards: ReviewCard[];
  cardResolutions: CardResolution[];
  cardAccountOptionsById: Map<string, CardAccountOption>;
  financialAccountsById: Map<string, FinancialAccount>;
  selectedIssuerKey: string | null;
  getAvailableOptionsForCard: (card: ReviewCard) => CardAccountOption[];
  onResolveCard: (ci: number, accountId: string | null) => void;
  onCreateCardForIndex: (ci: number) => void;
}

export const CardBucketMatchingStep = memo(function CardBucketMatchingStep({
  reviewCards,
  cardResolutions,
  cardAccountOptionsById,
  financialAccountsById,
  selectedIssuerKey,
  getAvailableOptionsForCard,
  onResolveCard,
  onCreateCardForIndex,
}: CardBucketMatchingStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const identifiedCardsCount = reviewCards.length;

  return (
    <Box sx={{ p: 1 }}>
      {identifiedCardsCount > 1 ? (
        <Stack spacing={0.5} sx={{ mb: 2 }}>
          <Typography variant="body1" color="text.secondary">
            {t('financeTransactions.billImport.cardMatchingStep.descriptionMultipleTitle', {
              count: identifiedCardsCount,
            })}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {t('financeTransactions.billImport.cardMatchingStep.descriptionMultipleBody')}
          </Typography>
        </Stack>
      ) : (
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.cardMatchingStep.descriptionSingle')}
        </Typography>
      )}

      {reviewCards.map((card, ci) => {
        const resolution = cardResolutions[ci];
        const isResolved = !!resolution?.financialAccountId;
        const needsUserInput = !isResolved;
        const availableOptions = getAvailableOptionsForCard(card);
        const availableAccounts = availableOptions.map((option) =>
          cardAccountOptionToFinancialAccount(option, financialAccountsById),
        );

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

            <AccountSelect
              value={resolution?.financialAccountId ?? ''}
              accounts={availableAccounts}
              isReadonly={false}
              allowEmpty
              searchable
              variant="field"
              label={t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}
              placeholder={t('financeTransactions.billImport.cardMatchingStep.cardAccountPlaceholder')}
              inputBottomBorderSx={{}}
              dataTestId={`card-bucket-account-select-${ci}`}
              onChange={(accountId) => {
                if (accountId && selectedIssuerKey) {
                  const selectedAccount = cardAccountOptionsById.get(accountId);
                  if (selectedAccount?.issuerKey !== selectedIssuerKey) return;
                }
                onResolveCard(ci, accountId || null);
              }}
            />

            {needsUserInput && !card.matchAmbiguous && (
              <Link
                component="button"
                type="button"
                variant="body1"
                underline="none"
                onClick={() => onCreateCardForIndex(ci)}
                data-testid={`card-bucket-create-card-link-${ci}`}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.5,
                  fontWeight: 'medium',
                  mt: 1,
                }}
              >
                <AddIcon fontSize="small" />
                {t('financeTransactions.billImport.cardMatchingStep.createNewCard')}
              </Link>
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
