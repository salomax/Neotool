import { Box, Button, Chip, Divider, LinearProgress, List, ListItem, Paper, Stack, TextField, Typography, alpha } from '@mui/material';
import { CheckCircle as CheckIcon, HelpOutline as UnmatchedIcon } from '@mui/icons-material';
import React, { memo } from 'react';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { FinancialAccountSelector } from '../../account/FinancialAccountSelector';
import { FormStack } from '@/shared/components/ui/forms/components';
import { formatTxDisplayDate } from '../billImportUtils';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import type { DraftCardResolution, FlatTxAssignments, FlatTxSelections, ReviewCard } from '../billImportTypes';

interface FlatAssignmentStepProps {
  flatSourceCard: ReviewCard;
  flatTxAssignments: FlatTxAssignments;
  flatTxSelections: FlatTxSelections;
  draftCards: DraftCardResolution[];
  assignmentLastFour: string;
  selectedCardAccountId: string | null;
  selectedCardIsMultiCard: boolean;
  onSelectCardAccount: (accountId: string | null) => void;
  onCreateCardAccount: () => void;
  onAssignSelectedTransactionsToExistingAccount: (financialAccountId: string, lastFourDigitsOverride?: string | null) => void;
  onSelectAllTx: () => void;
  onAssignmentLastFourChange: (value: string) => void;
  onSetFlatTxSelections: React.Dispatch<React.SetStateAction<FlatTxSelections>>;
}

export const FlatAssignmentStep = memo(function FlatAssignmentStep({
  flatSourceCard,
  flatTxAssignments,
  flatTxSelections,
  draftCards,
  assignmentLastFour,
  selectedCardAccountId,
  selectedCardIsMultiCard,
  onSelectCardAccount,
  onCreateCardAccount,
  onAssignSelectedTransactionsToExistingAccount,
  onSelectAllTx,
  onAssignmentLastFourChange,
  onSetFlatTxSelections,
}: FlatAssignmentStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const selectedTransactionIndexes = flatSourceCard.transactions
    .map((_, txIndex) => txIndex)
    .filter((txIndex) => flatTxSelections[txIndex]);

  const assignedCount = Object.values(flatTxAssignments).filter(Boolean).length;
  const flatAssignmentProgress =
    flatSourceCard.transactions.length > 0
      ? Math.round((assignedCount / flatSourceCard.transactions.length) * 100)
      : 0;
  const cardSelected = !!selectedCardAccountId;

  return (
    <Box sx={{ p: 1 }}>
      <FormStack sx={{ pb: selectedTransactionIndexes.length > 0 ? 11 : 0 }}>
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1.25} alignItems="center">
            <StepCircle number={1} active />
            <Typography variant="subtitle2" fontWeight={700}>
              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerTitle')}
            </Typography>
          </Stack>

          <Paper variant="outlined" sx={{ p: 2.5 }}>
            <FinancialAccountSelector
              onNewAccount={onCreateCardAccount}
              selectedAccountId={selectedCardAccountId}
              onSelectAccount={onSelectCardAccount}
              accountTypes={[FinancialAccountType.Credit, FinancialAccountType.Payment]}
              showAllAccountsOption={false}
              showNewAccountAction
              autoSelectSingleAccount={false}
            />
          </Paper>
        </Stack>

        <Box sx={{ opacity: cardSelected ? 1 : 0.35, pointerEvents: cardSelected ? 'auto' : 'none' }}>
          <Stack spacing={1.25}>
            <Stack direction="row" spacing={1.25} alignItems="center">
              <StepCircle number={2} active={cardSelected} />
              <Typography variant="subtitle2" fontWeight={700} color={cardSelected ? 'text.primary' : 'text.disabled'}>
                {t('financeTransactions.billImport.cardMatchingStep.flatSteps.assignTransactionsTitle')}
              </Typography>
            </Stack>

            <Paper
              variant="outlined"
              sx={(theme) => ({
                p: 1.5,
                borderRadius: 2,
                bgcolor: alpha(theme.palette.primary.main, 0.06),
                borderColor: alpha(theme.palette.primary.main, 0.18),
              })}
            >
              <Stack direction="row" spacing={1.5} alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={700}>
                    {t('financeTransactions.billImport.cardMatchingStep.flatSteps.assignTransactionsInfoTitle')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.billImport.cardMatchingStep.flatSteps.assignTransactionsHint')}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.billImport.cardMatchingStep.flatSteps.assignTransactionsRequirementHint')}
                  </Typography>
                </Box>
              </Stack>
            </Paper>

            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 0.75 }}>
              <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0 }}>
                <LinearProgress
                  variant="determinate"
                  value={flatAssignmentProgress}
                  sx={(theme) => ({
                    width: 180,
                    height: 6,
                    borderRadius: 999,
                    bgcolor: theme.palette.action.hover,
                    '& .MuiLinearProgress-bar': { borderRadius: 999 },
                  })}
                />
                <Typography variant="body2" fontWeight={600} color="primary.main" noWrap>
                  {t('financeTransactions.billImport.cardMatchingStep.assignmentProgressShort', {
                    assigned: assignedCount,
                    total: flatSourceCard.transactions.length,
                  })}
                </Typography>
              </Stack>
              <Button size="medium" variant="text" onClick={onSelectAllTx} sx={{ fontWeight: 700 }}>
                {t('financeTransactions.billImport.cardMatchingStep.selectAll')}
              </Button>
            </Stack>

            <Paper variant="outlined">
              <List disablePadding dense>
                {flatSourceCard.transactions.map((tx, txIndex) => {
                  const assignedDraftId = flatTxAssignments[txIndex];
                  const assignedDraft = draftCards.find((draft) => draft.draftCardId === assignedDraftId);
                  const selected = flatTxSelections[txIndex] ?? false;
                  const isDebit = tx.creditDebitType === 'DEBIT';
                  const trimmedLastFour = assignedDraft?.lastFourDigits.trim() ?? '';
                  const hasLastFour = trimmedLastFour.length === 4;
                  const isSetup = !!assignedDraft?.financialAccountId;
                  const needsSetup = !!assignedDraft && !isSetup;

                  return (
                    <Box key={`flat-tx-${txIndex}`}>
                      {txIndex > 0 && <Divider />}
                      <ListItem
                        disableGutters
                        sx={{ py: 1, px: 1.25, alignItems: 'flex-start' }}
                      >
                        <Box
                          component="span"
                          sx={{ display: 'flex', alignItems: 'flex-start', mt: 0.5, mr: 0.75, cursor: 'pointer' }}
                          onClick={() => onSetFlatTxSelections((prev) => ({ ...prev, [txIndex]: !prev[txIndex] }))}
                        >
                          <input
                            type="checkbox"
                            checked={selected}
                            onChange={(e) => onSetFlatTxSelections((prev) => ({ ...prev, [txIndex]: e.target.checked }))}
                          />
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 0, pt: 0.25 }}>
                          <Typography variant="body2" fontWeight="medium" noWrap>
                            {tx.description}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {formatTxDisplayDate(tx.date, i18n.language)}
                          </Typography>
                        </Box>
                        <Box sx={{ pl: 1, minWidth: 120, textAlign: 'right' }}>
                          <Typography variant="body2" fontWeight="medium" color={isDebit ? 'error.main' : 'text.primary'}>
                            {tx.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                          </Typography>
                          <Chip
                            size="medium"
                            icon={
                              isSetup
                                ? <CheckIcon sx={{ fontSize: 18 }} />
                                : needsSetup
                                  ? <UnmatchedIcon sx={{ fontSize: 18 }} />
                                  : undefined
                            }
                            label={
                              assignedDraft
                                ? hasLastFour
                                  ? t('financeTransactions.billImport.cardMatchingStep.lastFourChip', { lastFour: trimmedLastFour })
                                  : t('financeTransactions.billImport.cardMatchingStep.unassignedLastFour')
                                : t('financeTransactions.billImport.cardMatchingStep.status.pending')
                            }
                            color={isSetup ? 'success' : needsSetup ? 'warning' : 'default'}
                            variant={assignedDraft ? 'outlined' : 'filled'}
                            sx={{ mt: 0.75, fontSize: 11 }}
                          />
                        </Box>
                      </ListItem>
                    </Box>
                  );
                })}
              </List>
            </Paper>
          </Stack>
        </Box>
      </FormStack>

      {/* ── Floating assignment bar ─────────────────────────────────────── */}
      {selectedTransactionIndexes.length > 0 && (
        <Box sx={{ position: 'sticky', bottom: 16, mt: 1, zIndex: 1 }}>
          <Paper
            elevation={10}
            sx={{ p: 1.25, borderRadius: 2, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}
          >
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={1.25}
              alignItems={{ xs: 'stretch', md: 'center' }}
            >
              <Chip
                size="medium"
                label={t('financeTransactions.billImport.cardMatchingStep.selectedCount', {
                  count: selectedTransactionIndexes.length,
                })}
                sx={(theme) => ({
                  fontWeight: 600,
                  bgcolor: alpha(theme.palette.primary.main, 0.12),
                  color: theme.palette.primary.main,
                  px: 0.75,
                  height: 28,
                  '& .MuiChip-label': { px: 1.25 },
                })}
              />

              <Box sx={{ flex: 1, display: { xs: 'none', md: 'block' } }} />

              {!selectedCardIsMultiCard && (
                <TextField
                  size="medium"
                  placeholder={t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel')}
                  value={assignmentLastFour}
                  onChange={(e) => onAssignmentLastFourChange(e.target.value.replace(/\D/g, '').slice(0, 4))}
                  inputProps={{
                    maxLength: 4,
                    inputMode: 'numeric',
                    'aria-label': t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel'),
                  }}
                  sx={{ width: { xs: '100%', md: 170 }, '& .MuiOutlinedInput-root': { bgcolor: 'background.default' } }}
                />
              )}

              <Button
                variant="contained"
                disabled={
                  !selectedCardAccountId ||
                  (!selectedCardIsMultiCard &&
                    assignmentLastFour.trim().length > 0 &&
                    assignmentLastFour.trim().length < 4)
                }
                onClick={() => {
                  if (!selectedCardAccountId) return;
                  onAssignSelectedTransactionsToExistingAccount(
                    selectedCardAccountId,
                    assignmentLastFour.trim().length === 4 ? assignmentLastFour : null,
                  );
                }}
                sx={{ borderRadius: 1, px: 2.25, alignSelf: { xs: 'stretch', md: 'auto' } }}
              >
                {t('financeTransactions.billImport.cardMatchingStep.floatingApply')}
              </Button>
            </Stack>
          </Paper>
        </Box>
      )}
    </Box>
  );
});

// ── Small helper components ────────────────────────────────────────────────

function StepCircle({ number, active }: { number: number; active: boolean }) {
  return (
    <Box
      sx={{
        width: 28,
        height: 28,
        borderRadius: '50%',
        bgcolor: active ? 'primary.main' : 'grey.200',
        color: active ? 'common.white' : 'text.disabled',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: 700,
      }}
    >
      <Typography variant="caption" fontWeight={700}>{number}</Typography>
    </Box>
  );
}
