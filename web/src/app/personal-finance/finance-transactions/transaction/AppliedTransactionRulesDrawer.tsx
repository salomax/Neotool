'use client';

import { memo, useCallback, useMemo, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { Alert, alpha, Box, Chip, CircularProgress, Divider, Link as MuiLink, Paper, Stack, Typography, useTheme } from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { FormStack } from '@/shared/components/ui/forms/components';
import { Amount, InfoField, TagChips } from '@/shared/components/ui/data-display';
import { Label, Logo } from '@/shared/components/ui/primitives';
import { parseDateOnly } from '@/shared/utils/date/dateOnly';
import { extractErrorMessage } from '@/shared/utils/error';
import { TRANSACTION_RULES_BY_IDS } from '@/lib/graphql/operations/banking/queries';
import {
  CategoryApplicableTo,
  CreditDebitType,
  TransactionType,
  type Transaction,
  type TransactionRule,
} from '@/lib/graphql/types/__generated__/graphql';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CategoryChip } from '../category/CategoryChip';
import { TransactionRuleDrawer } from '../transaction-rules/TransactionRuleDrawer';
import type { TransactionRule as TransactionRuleForDrawer } from '../transaction-rules/types';

type AppliedTransaction = Pick<
  Transaction,
  | 'id'
  | 'description'
  | 'amount'
  | 'currencyCode'
  | 'creditDebitType'
  | 'type'
  | 'date'
  | 'appliedRuleIds'
  | 'appliedRuleHistory'
  | 'merchant'
>;

type AppliedTransactionRule = Pick<
  TransactionRule,
  | 'id'
  | 'pattern'
  | 'patternType'
  | 'applicableTo'
  | 'position'
  | 'priority'
  | 'isSystem'
  | 'isImplicit'
  | 'matchCount'
  | 'createdAt'
  | 'descriptionOverride'
  | 'tagsOverride'
  | 'categoryId'
  | 'merchantId'
> & {
  category: TransactionRule['category'];
  merchant: TransactionRule['merchant'];
};

type AppliedTransactionRulesData = {
  transactionRulesByIds: AppliedTransactionRule[];
};

type AppliedTransactionRulesVariables = {
  ids: string[];
};

type ContributedField = 'category' | 'merchant' | 'description' | 'tags';

type ResolvedAppliedRule = {
  id: string;
  rule: AppliedTransactionRule | null;
  contributedFields: ContributedField[];
  appliedAt: string | null;
};

interface AppliedTransactionRulesDrawerProps {
  open: boolean;
  transaction: AppliedTransaction | null;
  onClose: () => void;
}

function buildAppliedRuleItems(
  requestedIds: string[],
  rules: AppliedTransactionRule[],
  history: AppliedTransaction['appliedRuleHistory'],
): ResolvedAppliedRule[] {
  const rulesById = new Map(rules.map((rule) => [rule.id, rule]));
  const historyByRuleId = new Map<string, string>();
  history?.forEach((entry) => {
    const appliedAt = typeof entry.appliedAt === 'string' ? entry.appliedAt : String(entry.appliedAt);
    const previous = historyByRuleId.get(entry.ruleId);
    if (!previous || appliedAt > previous) {
      historyByRuleId.set(entry.ruleId, appliedAt);
    }
  });
  let categoryApplied = false;
  let merchantApplied = false;
  let descriptionApplied = false;
  let tagsApplied = false;

  return requestedIds.map((id) => {
    const rule = rulesById.get(id) ?? null;
    if (!rule) {
      return { id, rule: null, contributedFields: [], appliedAt: historyByRuleId.get(id) ?? null };
    }

    const contributedFields: ContributedField[] = [];

    if (!categoryApplied && rule.categoryId) {
      contributedFields.push('category');
      categoryApplied = true;
    }
    if (!merchantApplied && rule.merchantId) {
      contributedFields.push('merchant');
      merchantApplied = true;
    }
    if (!descriptionApplied && rule.descriptionOverride) {
      contributedFields.push('description');
      descriptionApplied = true;
    }
    if (!tagsApplied && (rule.tagsOverride?.length ?? 0) > 0) {
      contributedFields.push('tags');
      tagsApplied = true;
    }

    return { id, rule, contributedFields, appliedAt: historyByRuleId.get(id) ?? null };
  });
}

function toTransactionRuleForDrawer(rule: AppliedTransactionRule): TransactionRuleForDrawer {
  return {
    id: rule.id,
    pattern: rule.pattern,
    patternType: rule.patternType,
    applicableTo: rule.applicableTo,
    priority: rule.priority,
    isSystem: rule.isSystem,
    isDisabledForAccount: false,
    isImplicit: rule.isImplicit,
    matchCount: rule.matchCount,
    createdAt: String(rule.createdAt),
    descriptionOverride: rule.descriptionOverride,
    tagsOverride: rule.tagsOverride,
    categoryId: rule.categoryId,
    category: rule.category
      ? {
          id: rule.category.id,
          name: rule.category.name,
          icon: rule.category.icon,
          color: rule.category.color,
          applicableTo: rule.category.applicableTo,
        }
      : null,
    merchantId: rule.merchantId,
    merchant: rule.merchant
      ? {
          id: rule.merchant.id,
          name: rule.merchant.name,
          domain: rule.merchant.domain,
          logoAsset: rule.merchant.logoAsset
            ? { id: rule.merchant.logoAsset.id, publicUrl: rule.merchant.logoAsset.publicUrl }
            : null,
        }
      : null,
  };
}

const TransactionIcon = memo(function TransactionIcon({
  creditDebitType,
  transactionType,
  merchant,
}: {
  creditDebitType: CreditDebitType;
  transactionType: TransactionType;
  merchant: AppliedTransaction['merchant'];
}) {
  const theme = useTheme();
  const logoUrl = merchant?.logoAsset?.publicUrl;

  const typeProps = useMemo(() => {
    if (transactionType === TransactionType.Transfer) {
      return { icon: <SwapHorizIcon fontSize="inherit" />, color: theme.palette.info.main };
    }
    if (creditDebitType === CreditDebitType.Credit) {
      return { icon: <ArrowUpwardIcon fontSize="inherit" />, color: theme.palette.success.main };
    }
    if (creditDebitType === CreditDebitType.Debit) {
      return { icon: <ArrowDownwardIcon fontSize="inherit" />, color: theme.palette.error.main };
    }
    return null;
  }, [creditDebitType, theme, transactionType]);

  const radius = `${theme.shape.borderRadius}px`;

  if (logoUrl) {
    return (
      <Box sx={{ position: 'relative', width: 32, height: 32, flexShrink: 0 }}>
        <Logo src={logoUrl} alt={merchant?.name ?? ''} size="lg" />
        {typeProps && (
          <Box
            sx={{
              position: 'absolute',
              bottom: -8,
              right: -8,
              width: 18,
              height: 18,
              border: 'none',
              borderRadius: radius,
              display: 'flex',
              bgcolor: `color-mix(in srgb, ${typeProps.color} 5%, ${theme.palette.background.paper})`,
              alignItems: 'center',
              justifyContent: 'center',
              color: typeProps.color,
              fontSize: 16,
            }}
          >
            {typeProps.icon}
          </Box>
        )}
      </Box>
    );
  }

  if (!typeProps) return null;
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 32,
        height: 32,
        borderRadius: radius,
        bgcolor: alpha(typeProps.color, 0.05),
        color: typeProps.color,
        fontSize: 20,
        flexShrink: 0,
      }}
    >
      {typeProps.icon}
    </Box>
  );
});

const TransactionHeader = memo(function TransactionHeader({
  transaction,
  formattedDate,
  locale,
}: {
  transaction: AppliedTransaction;
  formattedDate: string;
  locale: string;
}) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
      <TransactionIcon
        creditDebitType={transaction.creditDebitType}
        transactionType={transaction.type}
        merchant={transaction.merchant}
      />

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap title={transaction.description}>
          {transaction.description}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }} noWrap title={formattedDate}>
          {formattedDate}
        </Typography>
      </Box>

      <Amount
        value={Math.abs(transaction.amount)}
        currency={transaction.currencyCode}
        locale={locale}
        tone="neutral"
        variant="subtitle1"
        fontWeight={800}
        noWrap
      />
    </Box>
  );
});

const TimelineMarker = memo(function TimelineMarker({ label }: { label: string }) {
  const theme = useTheme();
  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ pl: 0, pt: 1.5 }}>
      <Box
        sx={{
          width: 14,
          height: 14,
          boxSizing: 'border-box',
          borderRadius: '50%',
          bgcolor: theme.palette.primary.main,
          border: `2px solid ${theme.palette.background.paper}`,
          zIndex: 1,
        }}
      />
      <Label sx={{ fontSize: '0.7rem', color: 'text.secondary', lineHeight: 1.2 }}>
        {label}
      </Label>
    </Stack>
  );
});

export function AppliedTransactionRulesDrawer({
  open,
  transaction,
  onClose,
}: AppliedTransactionRulesDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [editingRule, setEditingRule] = useState<TransactionRuleForDrawer | null>(null);
  const [isRuleDrawerOpen, setIsRuleDrawerOpen] = useState(false);

  const transactionAppliedRuleIds = transaction?.appliedRuleIds;
  const transactionAppliedRuleHistory = transaction?.appliedRuleHistory;
  const appliedRuleIds = useMemo(() => transactionAppliedRuleIds ?? [], [transactionAppliedRuleIds]);

  const queryVariables = useMemo<AppliedTransactionRulesVariables>(() => ({ ids: appliedRuleIds }), [appliedRuleIds]);
  const { data, loading, error, refetch } = useQuery<
    AppliedTransactionRulesData,
    AppliedTransactionRulesVariables
  >(
    TRANSACTION_RULES_BY_IDS,
    {
      variables: queryVariables,
      skip: !open || !transaction || appliedRuleIds.length === 0,
      fetchPolicy: 'cache-first',
    },
  );

  const resolvedRules = useMemo(
    () => buildAppliedRuleItems(appliedRuleIds, data?.transactionRulesByIds ?? [], transactionAppliedRuleHistory ?? []),
    [appliedRuleIds, data?.transactionRulesByIds, transactionAppliedRuleHistory],
  );

  const locale = i18n.language;
  const transactionDate = transaction?.date;
  const transactionDateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        dateStyle: 'medium',
        timeZone: 'UTC',
      }),
    [locale],
  );
  const formattedDate = useMemo(() => {
    if (!transactionDate) return '';
    const parsedDate = parseDateOnly(String(transactionDate));
    if (!parsedDate) return '';
    return transactionDateFormatter.format(parsedDate);
  }, [transactionDate, transactionDateFormatter]);

  const appliedAtFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }),
    [locale],
  );
  const formatAppliedAt = useCallback(
    (value: string | null) => {
      if (!value) return null;
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return null;
      return appliedAtFormatter.format(date);
    },
    [appliedAtFormatter],
  );

  const handleOpenRuleEditor = useCallback((rule: AppliedTransactionRule) => {
    setEditingRule(toTransactionRuleForDrawer(rule));
    setIsRuleDrawerOpen(true);
  }, []);

  const handleCloseRuleDrawer = useCallback(() => setIsRuleDrawerOpen(false), []);
  const handleSavedRule = useCallback(() => {
    setIsRuleDrawerOpen(false);
    refetch();
  }, [refetch]);

  const outlinedChipSx = useCallback(
    (color: string) => ({
      borderColor: alpha(color, 0.35),
      bgcolor: alpha(color, 0.06),
      color,
      fontWeight: 700,
    }),
    [],
  );
  const applicableToColor = useCallback(
    (applicableTo: CategoryApplicableTo) => {
      if (applicableTo === CategoryApplicableTo.Income) return theme.palette.success.main;
      if (applicableTo === CategoryApplicableTo.Expense) return theme.palette.error.main;
      if (applicableTo === CategoryApplicableTo.Transfer) return theme.palette.info.main;
      return theme.palette.text.secondary;
    },
    [theme],
  );

  const fieldLabels = useMemo<Record<ContributedField, string>>(
    () => ({
      category: t('financeTransactions.transactionTable.appliedRules.fields.category'),
      merchant: t('financeTransactions.transactionTable.appliedRules.fields.merchant'),
      description: t('financeTransactions.transactionTable.appliedRules.fields.description'),
      tags: t('financeTransactions.transactionTable.appliedRules.fields.tags'),
    }),
    [t],
  );
  const timelineAxisOffset = 7;
  const timelineContentOffset = 20;

  if (!open || !transaction) return null;

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        anchor="right"
        size="md"
        ariaLabel={t('financeTransactions.transactionTable.appliedRules.title')}
      >
        <Drawer.Header
          title={t('financeTransactions.transactionTable.appliedRules.title')}
          showCloseButton
          closeButtonAriaLabel={t('financeTransactions.actions.close')}
        />
        <Drawer.Body>
          <FormStack>
            <TransactionHeader transaction={transaction} formattedDate={formattedDate} locale={locale} />

          <Divider />

          <Typography variant="body2" color="text.secondary">
            {t('financeTransactions.transactionTable.appliedRules.description')}
          </Typography>

          {loading && (
            <Stack alignItems="center" justifyContent="center" sx={{ py: 6 }}>
              <CircularProgress size={28} />
            </Stack>
          )}

          {!loading && error && (
            <Alert severity="error">
              {extractErrorMessage(error, t('financeTransactions.transactionTable.appliedRules.loadError'))}
            </Alert>
          )}

          {!loading && !error && resolvedRules.length === 0 && (
            <Alert severity="info">{t('financeTransactions.transactionTable.appliedRules.empty')}</Alert>
          )}

          {!loading && !error && resolvedRules.length > 0 && (
            <Stack
              spacing={1.5}
              sx={{
                position: 'relative',
                '&::before': {
                  content: '""',
                  position: 'absolute',
                  left: timelineAxisOffset,
                  top: 12,
                  bottom: 12,
                  width: 3,
                  borderRadius: 999,
                  bgcolor: alpha(theme.palette.primary.main, 0.2),
                },
              }}
            >
              {resolvedRules.map((item, index) => {
                if (!item.rule) {
                  return (
                    <Stack
                      key={item.id}
                      spacing={0.75}
                    >
                      <TimelineMarker label={`#${index + 1}`} />
                      <Stack direction="row" spacing={1.5} alignItems="flex-start">
                        <Box sx={{ width: timelineContentOffset, flexShrink: 0 }} />
                        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
                          <Stack spacing={0.75}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                              {t('financeTransactions.transactionTable.appliedRules.ruleNumber', {
                                index: index + 1,
                              })}
                            </Typography>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              {t('financeTransactions.transactionTable.appliedRules.removed')}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {t('financeTransactions.transactionTable.appliedRules.removedDescription')}
                            </Typography>
                          </Stack>
                        </Paper>
                      </Stack>
                    </Stack>
                  );
                }

                const appliedAtLabel = item.appliedAt
                  ? t('financeTransactions.transactionTable.appliedRules.appliedAt', {
                      datetime: formatAppliedAt(item.appliedAt) ?? item.appliedAt,
                    })
                  : `#${index + 1}`;

                return (
                  <Stack
                    key={item.rule.id}
                    spacing={0.75}
                  >
                    <TimelineMarker label={appliedAtLabel} />
                    <Stack direction="row" spacing={1.5} alignItems="flex-start">
                      <Box sx={{ width: timelineContentOffset, flexShrink: 0 }} />
                      <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
                        <Stack spacing={1.25}>
                          <Stack
                            direction={{ xs: 'column', sm: 'row' }}
                            spacing={1}
                            justifyContent="space-between"
                            alignItems={{ xs: 'flex-start', sm: 'flex-start' }}
                          >
                            <Stack spacing={0.75}>
                              <Label
                                sx={{
                                  fontWeight: 600,
                                  fontSize: (theme) => theme.typography.subtitle2.fontSize,
                                }}
                              >
                                {t('financeTransactions.transactionTable.appliedRules.ruleNumber', {
                                  index: index + 1,
                                })}
                              </Label>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={
                                    item.rule.isSystem
                                      ? t('financeTransactions.transactionTable.appliedRules.ownership.automatic')
                                      : t('financeTransactions.transactionTable.appliedRules.ownership.manual')
                                  }
                                  sx={outlinedChipSx(
                                    item.rule.isSystem ? theme.palette.info.main : theme.palette.text.secondary,
                                  )}
                                />
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={t(`financeTransactions.transactionTable.appliedRules.applicableTo.${item.rule.applicableTo}`)}
                                  sx={outlinedChipSx(applicableToColor(item.rule.applicableTo))}
                                />
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={t('financeTransactions.transactionTable.appliedRules.priority', {
                                    priority: item.rule.priority,
                                  })}
                                />
                              </Stack>
                            </Stack>
                            {!item.rule.isSystem && (
                              <MuiLink
                                component="button"
                                underline="hover"
                                onClick={() => handleOpenRuleEditor(item.rule!)}
                                sx={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 0.5,
                                  fontWeight: 700,
                                  fontSize: '0.8125rem',
                                  whiteSpace: 'nowrap',
                                  border: 'none',
                                  padding: 0,
                                  background: 'none',
                                  cursor: 'pointer',
                                  fontFamily: 'inherit',
                                  alignSelf: { xs: 'flex-start', sm: 'flex-start' },
                                }}
                              >
                                {t('financeTransactions.transactionTable.appliedRules.editRule')}
                                <OpenInNewIcon sx={{ fontSize: '0.95rem' }} />
                              </MuiLink>
                            )}
                          </Stack>
                          <Stack spacing={0.75}>
                            <Label>
                              {t(`financeTransactions.transactionTable.appliedRules.patternType.${item.rule.patternType}`)}
                            </Label>
                            <Typography variant="body1" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
                              &quot;{item.rule.pattern}&quot;
                            </Typography>
                          </Stack>

                          <Stack direction="row" spacing={3}>
                            {item.contributedFields.includes('category') && (
                              <InfoField
                                label={fieldLabels.category}
                                value={
                                  <CategoryChip
                                    category={item.rule.category}
                                    placeholder={t('financeTransactions.transactionTable.appliedRules.missingValue')}
                                  />
                                }
                              />
                            )}
                            {item.contributedFields.includes('merchant') && (
                              <InfoField
                                label={fieldLabels.merchant}
                                value={
                                  item.rule.merchant?.logoAsset?.publicUrl ? (
                                    <Box
                                      component="span"
                                      sx={{
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        gap: 1,
                                        minWidth: 0,
                                      }}
                                    >
                                      <Logo
                                        src={item.rule.merchant.logoAsset.publicUrl ?? undefined}
                                        alt={item.rule.merchant.name}
                                        size="xs"
                                        sx={{ flexShrink: 0 }}
                                      />
                                      <Box
                                        component="span"
                                        sx={{
                                          minWidth: 0,
                                          overflow: 'hidden',
                                          textOverflow: 'ellipsis',
                                          whiteSpace: 'nowrap',
                                        }}
                                      >
                                        {item.rule.merchant.name}
                                      </Box>
                                    </Box>
                                  ) : (
                                    item.rule.merchant?.name ?? t('financeTransactions.transactionTable.appliedRules.missingValue')
                                  )
                                }
                                valueColor="text.secondary"
                              />
                            )}
                            {item.contributedFields.includes('description') && (
                              <InfoField
                                label={fieldLabels.description}
                                value={item.rule.descriptionOverride ?? t('financeTransactions.transactionTable.appliedRules.missingValue')}
                                valueColor="text.secondary"
                              />
                            )}
                            {item.contributedFields.includes('tags') && (
                              <Stack spacing={0.5}>
                                <Label>
                                  {fieldLabels.tags}
                                </Label>
                                <TagChips tags={item.rule.tagsOverride ?? []} size="small" />
                              </Stack>
                            )}
                          </Stack>
                        </Stack>
                      </Paper>
                    </Stack>
                  </Stack>
                );
              })}
            </Stack>
          )}
        </FormStack>
        </Drawer.Body>
      </Drawer>

      <TransactionRuleDrawer
        open={isRuleDrawerOpen}
        rule={editingRule}
        onClose={handleCloseRuleDrawer}
        onSaved={handleSavedRule}
      />
    </>
  );
}
