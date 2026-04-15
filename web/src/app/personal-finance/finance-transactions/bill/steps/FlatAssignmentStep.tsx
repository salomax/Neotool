import {
  Alert,
  Autocomplete,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  InputAdornment,
  InputLabel,
  LinearProgress,
  List,
  ListItem,
  MenuItem as SelectMenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
  alpha,
} from '@mui/material';
import {
  AccountBalance as BankIcon,
  CheckCircle as CheckIcon,
  CreditCard as CardIcon,
  HelpOutline as UnmatchedIcon,
} from '@mui/icons-material';
import { memo } from 'react';
import { NumericFormat } from 'react-number-format';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { FormStack } from '@/shared/components/ui/forms/components';
import { formatTxDisplayDate } from '../billImportUtils';
import type {
  CardAccountOption,
  DraftBucketSummary,
  DraftCardResolution,
  FlatTxAssignments,
  FlatTxSelections,
  ReviewCard,
  SharedCardTemplate,
} from '../billImportTypes';

interface FlatAssignmentStepProps {
  flatSourceCard: ReviewCard;
  flatTxAssignments: FlatTxAssignments;
  flatTxSelections: FlatTxSelections;
  draftCards: DraftCardResolution[];
  draftBucketSummaries: DraftBucketSummary[];
  assignmentLastFour: string;
  sharedCardTemplate: SharedCardTemplate;
  templateError: string | null;
  templateHasIssuer: boolean;
  templateRequiresBillingCycle: boolean;
  templatePreviewName: string;
  allIssuerOptions: Array<{ label: string; value: string; logo: string | null; compe: string | null }>;
  issuerOptions: Array<{ id: string; name: string }>;
  cardSubtypeOptions: Array<{ value: string; label: string }>;
  cardNetworkOptions: Array<{ value: string; label: string }>;
  cardLevelOptions: Array<{ value: string; label: string }>;
  issuerHasExistingCards: boolean;
  issuerCardLastFourOptions: string[];
  issuerCardsForSelection: CardAccountOption[];
  canReviewTransactions: boolean;
  creatingTemplateCards: boolean;
  accountsLoading: boolean;
  issuersLoading: boolean;
  moneyLocale: string;
  onTemplateFieldChange: (field: keyof SharedCardTemplate, value: string) => void;
  onAssignSelectedTransactions: (lastFourDigits: string | null) => void;
  onSelectAllTx: () => void;
  onAssignmentLastFourChange: (value: string) => void;
  onSetFlatTxSelections: React.Dispatch<React.SetStateAction<FlatTxSelections>>;
  onResetDraftCards: () => void;
  onContinue: () => void;
}

export const FlatAssignmentStep = memo(function FlatAssignmentStep({
  flatSourceCard,
  flatTxAssignments,
  flatTxSelections,
  draftCards,
  assignmentLastFour,
  sharedCardTemplate,
  templateError,
  templateHasIssuer,
  templateRequiresBillingCycle,
  templatePreviewName,
  allIssuerOptions,
  cardSubtypeOptions,
  cardNetworkOptions,
  cardLevelOptions,
  issuerHasExistingCards,
  issuerCardLastFourOptions,
  issuerCardsForSelection,
  canReviewTransactions,
  creatingTemplateCards,
  accountsLoading,
  issuersLoading,
  moneyLocale,
  onTemplateFieldChange,
  onAssignSelectedTransactions,
  onSelectAllTx,
  onAssignmentLastFourChange,
  onSetFlatTxSelections,
  onResetDraftCards,
  onContinue,
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

  const issuerSelected = templateHasIssuer;

  const selectedIssuerOption = allIssuerOptions.find((o) => o.value === sharedCardTemplate.issuerId) ?? null;

  return (
    <Box sx={{ p: 1 }}>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.cardMatchingStep.flatAssignmentBanner')}
      </Alert>

      <FormStack sx={{ pb: selectedTransactionIndexes.length > 0 ? 11 : 0 }}>
        {/* ── Step 1: Issuer ─────────────────────────────────────────── */}
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1.25} alignItems="center">
            <StepCircle number={1} active />
            <Typography variant="subtitle2" fontWeight={700}>
              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerTitle')}
            </Typography>
          </Stack>

          <Paper variant="outlined" sx={{ p: 2.5 }}>
            <Stack spacing={2}>
              <Autocomplete
                options={allIssuerOptions}
                loading={issuersLoading}
                disabled={issuersLoading}
                value={selectedIssuerOption}
                onChange={(_, newValue) => {
                  const nextIssuerId = newValue ? newValue.value : '';
                  onTemplateFieldChange('issuerId', nextIssuerId);
                  if (nextIssuerId !== 'custom') onTemplateFieldChange('issuerName', '');
                  onResetDraftCards();
                }}
                isOptionEqualToValue={(option, value) => option.value === value.value}
                noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                getOptionLabel={(option) => option.label || ''}
                renderOption={(props, option) => {
                  const { key, ...otherProps } = props;
                  return (
                    <Box
                      component="li"
                      key={key}
                      {...otherProps}
                      sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}
                    >
                      {option.logo ? (
                        <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
                      ) : (
                        <Avatar sx={{ width: 40, height: 40, borderRadius: 1.5, bgcolor: 'action.disabledBackground' }}>
                          <BankIcon sx={{ fontSize: 24 }} />
                        </Avatar>
                      )}
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body1" fontWeight="600">{option.label}</Typography>
                        {option.compe && (
                          <Typography variant="body2" color="text.secondary">({option.compe})</Typography>
                        )}
                      </Box>
                    </Box>
                  );
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    size="medium"
                    label={t('financeTransactions.createAccount.step2.fields.issuer')}
                    helperText={
                      !templateHasIssuer
                        ? t('financeTransactions.billImport.cardMatchingStep.templateIssuerHint')
                        : undefined
                    }
                    InputProps={{
                      ...params.InputProps,
                      startAdornment: (
                        <>
                          {templateHasIssuer && selectedIssuerOption && (
                            <InputAdornment position="start" sx={{ mr: 0.25 }}>
                              {selectedIssuerOption.logo ? (
                                <Avatar src={selectedIssuerOption.logo} sx={{ width: 24, height: 24, borderRadius: 1 }} />
                              ) : (
                                <Avatar sx={{ width: 24, height: 24, borderRadius: 1, bgcolor: 'action.disabledBackground' }}>
                                  <BankIcon sx={{ fontSize: 16 }} />
                                </Avatar>
                              )}
                            </InputAdornment>
                          )}
                          {params.InputProps.startAdornment}
                        </>
                      ),
                      endAdornment: (
                        <>
                          {issuersLoading ? <CircularProgress color="inherit" size={20} /> : null}
                          {params.InputProps.endAdornment}
                        </>
                      ),
                    }}
                  />
                )}
              />

              {sharedCardTemplate.issuerId === 'custom' && (
                <TextField
                  size="medium"
                  label={t('financeTransactions.createAccount.step2.fields.institutionName')}
                  value={sharedCardTemplate.issuerName}
                  onChange={(e) => {
                    onTemplateFieldChange('issuerName', e.target.value);
                    onResetDraftCards();
                  }}
                />
              )}

              {issuerSelected ? (
                <IssuerSelectedBanner
                  issuerLabel={
                    sharedCardTemplate.issuerId === 'custom'
                      ? sharedCardTemplate.issuerName.trim()
                      : allIssuerOptions.find((o) => o.value === sharedCardTemplate.issuerId)?.label ?? ''
                  }
                  accountsLoading={accountsLoading}
                  issuerHasExistingCards={issuerHasExistingCards}
                  issuerCardsCount={issuerCardsForSelection.length}
                  t={t}
                />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {t('financeTransactions.billImport.cardMatchingStep.flatSteps.selectIssuerFirst')}
                </Typography>
              )}
            </Stack>
          </Paper>
        </Stack>

        {/* ── Step 2: New card details (only if no existing cards) ────── */}
        {!issuerHasExistingCards && (
          <Box sx={{ opacity: issuerSelected ? 1 : 0.35, pointerEvents: issuerSelected ? 'auto' : 'none' }}>
            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1.25} alignItems="center">
                <StepCircle number={2} active={issuerSelected} />
                <Typography variant="subtitle2" fontWeight={700} color={issuerSelected ? 'text.primary' : 'text.disabled'}>
                  {t('financeTransactions.billImport.cardMatchingStep.flatSteps.newCardDetailsTitle')}
                </Typography>
              </Stack>

              <Paper variant="outlined" sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  {templateError && <Alert severity="warning">{templateError}</Alert>}

                  <FormControl fullWidth size="medium">
                    <InputLabel>{t('financeTransactions.createAccount.step2.fields.subtype')}</InputLabel>
                    <Select
                      label={t('financeTransactions.createAccount.step2.fields.subtype')}
                      value={sharedCardTemplate.subtype}
                      onChange={(e) => onTemplateFieldChange('subtype', e.target.value as string)}
                    >
                      {cardSubtypeOptions.map((option) => (
                        <SelectMenuItem key={option.value} value={option.value}>{option.label}</SelectMenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                    <FormControl fullWidth size="medium">
                      <InputLabel>{t('financeTransactions.createAccount.step2.fields.network')}</InputLabel>
                      <Select
                        label={t('financeTransactions.createAccount.step2.fields.network')}
                        value={sharedCardTemplate.cardNetwork}
                        onChange={(e) => onTemplateFieldChange('cardNetwork', e.target.value as string)}
                      >
                        <SelectMenuItem value="">
                          <em>{t('financeTransactions.billImport.cardMatchingStep.optionalField')}</em>
                        </SelectMenuItem>
                        {cardNetworkOptions.map((option) => (
                          <SelectMenuItem key={option.value} value={option.value}>{option.label}</SelectMenuItem>
                        ))}
                      </Select>
                    </FormControl>

                    <FormControl fullWidth size="medium" disabled={sharedCardTemplate.subtype !== 'CREDIT_CARD'}>
                      <InputLabel>{t('financeTransactions.createAccount.step2.fields.level')}</InputLabel>
                      <Select
                        label={t('financeTransactions.createAccount.step2.fields.level')}
                        value={sharedCardTemplate.cardLevel}
                        onChange={(e) => onTemplateFieldChange('cardLevel', e.target.value as string)}
                      >
                        <SelectMenuItem value="">
                          <em>{t('financeTransactions.billImport.cardMatchingStep.optionalField')}</em>
                        </SelectMenuItem>
                        {cardLevelOptions.map((option) => (
                          <SelectMenuItem key={option.value} value={option.value}>{option.label}</SelectMenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Stack>

                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                    <TextField
                      fullWidth
                      size="medium"
                      type="number"
                      label={t('financeTransactions.createAccount.step2.fields.closingDay')}
                      value={sharedCardTemplate.closingDay}
                      onChange={(e) => onTemplateFieldChange('closingDay', e.target.value.replace(/\D/g, '').slice(0, 2))}
                      inputProps={{ min: 1, max: 31 }}
                      disabled={!templateRequiresBillingCycle}
                    />
                    <TextField
                      fullWidth
                      size="medium"
                      type="number"
                      label={t('financeTransactions.createAccount.step2.fields.dueDay')}
                      value={sharedCardTemplate.dueDay}
                      onChange={(e) => onTemplateFieldChange('dueDay', e.target.value.replace(/\D/g, '').slice(0, 2))}
                      inputProps={{ min: 1, max: 31 }}
                      disabled={!templateRequiresBillingCycle}
                    />
                    <NumericFormat
                      customInput={TextField}
                      fullWidth
                      size="medium"
                      label={t('financeTransactions.createAccount.step2.fields.creditLimit')}
                      value={sharedCardTemplate.creditLimit}
                      thousandSeparator={moneyLocale === 'pt-BR' ? '.' : ','}
                      decimalSeparator={moneyLocale === 'pt-BR' ? ',' : '.'}
                      decimalScale={2}
                      fixedDecimalScale
                      prefix={moneyLocale === 'pt-BR' ? 'R$ ' : '$ '}
                      allowNegative={false}
                      valueIsNumericString
                      onValueChange={(values) => onTemplateFieldChange('creditLimit', values.value)}
                    />
                  </Stack>

                  <TextField
                    size="medium"
                    label={t('financeTransactions.billImport.cardMatchingStep.descriptionBase')}
                    value={sharedCardTemplate.descriptionBase}
                    onChange={(e) => onTemplateFieldChange('descriptionBase', e.target.value)}
                    helperText={t('financeTransactions.billImport.cardMatchingStep.descriptionBaseHint')}
                  />

                  <Alert severity="success" variant="outlined">
                    {t('financeTransactions.billImport.cardMatchingStep.previewLabel', { name: templatePreviewName })}
                  </Alert>
                </Stack>
              </Paper>
            </Stack>
          </Box>
        )}

        {/* ── Step 3 (or 2 if existing cards): Assign transactions ───── */}
        <Box sx={{ opacity: issuerSelected ? 1 : 0.35, pointerEvents: issuerSelected ? 'auto' : 'none' }}>
          <Stack spacing={1.25}>
            <Stack direction="row" spacing={1.25} alignItems="center">
              <StepCircle number={issuerHasExistingCards ? 2 : 3} active={issuerSelected} />
              <Typography variant="subtitle2" fontWeight={700} color={issuerSelected ? 'text.primary' : 'text.disabled'}>
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
            <Stack direction="row" spacing={1.25} alignItems="center">
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
              <Box sx={{ flex: 1 }} />
              {issuerHasExistingCards ? (
                <Autocomplete
                  freeSolo
                  clearOnBlur={false}
                  options={issuerCardLastFourOptions}
                  value={assignmentLastFour.trim() ? assignmentLastFour.trim() : null}
                  inputValue={assignmentLastFour}
                  onChange={(_, next) => {
                    const normalized = String(next ?? '').replace(/\D/g, '').slice(0, 4);
                    onAssignmentLastFourChange(normalized);
                  }}
                  onInputChange={(_, nextInputValue) => {
                    onAssignmentLastFourChange(nextInputValue.replace(/\D/g, '').slice(0, 4));
                  }}
                  sx={{ width: 210 }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      size="medium"
                      placeholder={t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel')}
                      inputProps={{
                        ...params.inputProps,
                        inputMode: 'numeric',
                        maxLength: 4,
                        'aria-label': t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel'),
                      }}
                      sx={{ '& .MuiOutlinedInput-root': { bgcolor: 'background.default' } }}
                    />
                  )}
                />
              ) : (
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
                  sx={{ width: 170, '& .MuiOutlinedInput-root': { bgcolor: 'background.default' } }}
                />
              )}
              <Button
                variant="contained"
                disabled={assignmentLastFour.trim().length > 0 && assignmentLastFour.trim().length < 4}
                onClick={() =>
                  onAssignSelectedTransactions(
                    assignmentLastFour.trim().length === 4 ? assignmentLastFour : null,
                  )
                }
                sx={{ borderRadius: 2, px: 2.25 }}
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

function IssuerSelectedBanner({
  issuerLabel,
  accountsLoading,
  issuerHasExistingCards,
  issuerCardsCount,
  t,
}: {
  issuerLabel: string;
  accountsLoading: boolean;
  issuerHasExistingCards: boolean;
  issuerCardsCount: number;
  t: (key: string, params?: Record<string, unknown>) => string;
}) {
  return (
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
        <Avatar sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12), color: 'primary.main', width: 36, height: 36 }}>
          <CardIcon fontSize="small" />
        </Avatar>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="body2" fontWeight={700}>
            {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerSelectedTitle', { issuer: issuerLabel })}
          </Typography>
          {accountsLoading ? (
            <Typography variant="body2" color="text.secondary">
              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.loadingCards')}
            </Typography>
          ) : issuerHasExistingCards ? (
            <Typography variant="body2" color="text.secondary">
              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerHasCardsMessage', { count: issuerCardsCount })}
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerNoCardsMessage')}
            </Typography>
          )}
        </Box>
        <Box
          sx={{
            width: 28,
            height: 28,
            borderRadius: '50%',
            bgcolor: 'success.main',
            color: 'common.white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mt: 0.25,
          }}
        >
          <CheckIcon fontSize="small" />
        </Box>
      </Stack>
    </Paper>
  );
}
