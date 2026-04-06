'use client';

import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { Alert, alpha, Box, Chip, CircularProgress, Divider, Paper, Stack, Typography, useTheme } from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { Amount, InfoField } from '@/shared/components/ui/data-display';
import { Logo } from '@/shared/components/ui/primitives';
import { Link } from '@/shared/components/ui/navigation/Link';
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
  'id' | 'description' | 'amount' | 'currencyCode' | 'creditDebitType' | 'type' | 'date' | 'appliedRuleIds' | 'merchant'
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
};

interface AppliedTransactionRulesDrawerProps {
  open: boolean;
  transaction: AppliedTransaction | null;
  onClose: () => void;
}

function buildAppliedRuleItems(
  requestedIds: string[],
  rules: AppliedTransactionRule[],
): ResolvedAppliedRule[] {
  const rulesById = new Map(rules.map((rule) => [rule.id, rule]));
  let categoryApplied = false;
  let merchantApplied = false;
  let descriptionApplied = false;
  let tagsApplied = false;

  return requestedIds.map((id) => {
    const rule = rulesById.get(id) ?? null;
    if (!rule) {
      return { id, rule: null, contributedFields: [] };
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

    return { id, rule, contributedFields };
  });
}

export function AppliedTransactionRulesDrawer({
  open,
  transaction,
  onClose,
}: AppliedTransactionRulesDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const borderRadius = '.3rem';
  const patternCardRadius = `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`;
  const [editingRule, setEditingRule] = useState<TransactionRuleForDrawer | null>(null);
  const [isRuleDrawerOpen, setIsRuleDrawerOpen] = useState(false);

  const transactionAppliedRuleIds = transaction?.appliedRuleIds;
  const appliedRuleIds = useMemo(() => transactionAppliedRuleIds ?? [], [transactionAppliedRuleIds]);
  const { data, loading, error, refetch } = useQuery<
    AppliedTransactionRulesData,
    AppliedTransactionRulesVariables
  >(
    TRANSACTION_RULES_BY_IDS,
    {
      variables: { ids: appliedRuleIds },
      skip: !open || !transaction || appliedRuleIds.length === 0,
      fetchPolicy: 'cache-first',
    },
  );

  const resolvedRules = useMemo(
    () => buildAppliedRuleItems(appliedRuleIds, data?.transactionRulesByIds ?? []),
    [appliedRuleIds, data?.transactionRulesByIds],
  );

  const transactionDate = transaction?.date;
  const formattedDate = useMemo(() => {
    if (!transactionDate) return '';
    return new Intl.DateTimeFormat(i18n.language, {
      dateStyle: 'medium',
      timeZone: 'UTC',
    }).format(new Date(`${String(transactionDate)}T00:00:00Z`));
  }, [transactionDate]);

  const handleOpenRuleEditor = useCallback((rule: AppliedTransactionRule) => {
    const normalizedRule: TransactionRuleForDrawer = {
      id: rule.id,
      pattern: rule.pattern,
      patternType: rule.patternType,
      applicableTo: rule.applicableTo,
      priority: rule.priority,
      isSystem: rule.isSystem,
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

    setEditingRule(normalizedRule);
    setIsRuleDrawerOpen(true);
  }, []);

  if (!open || !transaction) return null;

  const outlinedChipSx = (color: string) => ({
    borderColor: alpha(color, 0.35),
    bgcolor: alpha(color, 0.06),
    color,
    fontWeight: 700,
  });

  const applicableToColor = (applicableTo: CategoryApplicableTo) => {
    if (applicableTo === CategoryApplicableTo.Income) return theme.palette.success.main;
    if (applicableTo === CategoryApplicableTo.Expense) return theme.palette.error.main;
    if (applicableTo === CategoryApplicableTo.Transfer) return theme.palette.info.main;
    return theme.palette.text.secondary;
  };

  const getTypeIconProps = (creditDebitType: CreditDebitType, transactionType: TransactionType) => {
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
  };

  const renderTransactionIcon = (
    creditDebitType: CreditDebitType,
    transactionType: TransactionType,
    merchant: AppliedTransaction['merchant'],
  ) => {
    const typeProps = getTypeIconProps(creditDebitType, transactionType);
    const logoUrl = merchant?.logoAsset?.publicUrl;

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
                borderRadius: borderRadius,
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
          borderRadius: borderRadius,
          bgcolor: alpha(typeProps.color, 0.05),
          color: typeProps.color,
          fontSize: 20,
          flexShrink: 0,
        }}
      >
        {typeProps.icon}
      </Box>
    );
  };

  const fieldLabels: Record<ContributedField, string> = {
    category: t('financeTransactions.transactionTable.appliedRules.fields.category'),
    merchant: t('financeTransactions.transactionTable.appliedRules.fields.merchant'),
    description: t('financeTransactions.transactionTable.appliedRules.fields.description'),
    tags: t('financeTransactions.transactionTable.appliedRules.fields.tags'),
  };

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
          <Stack spacing={3}>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
            {renderTransactionIcon(transaction.creditDebitType, transaction.type, transaction.merchant)}

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
              locale={i18n.language}
              tone="neutral"
              variant="subtitle1"
              fontWeight={800}
              noWrap
            />
          </Box>

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
            <Stack spacing={1.5}>
              {resolvedRules.map((item, index) => {
                if (!item.rule) {
                  return (
                    <Paper key={item.id} variant="outlined" sx={{ p: 2 }}>
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
                  );
                }

                return (
                  <Paper key={item.rule.id} variant="outlined" sx={{ p: 2 }}>
                    <Stack spacing={1.25}>
                      <Stack
                        direction={{ xs: 'column', sm: 'row' }}
                        spacing={1}
                        justifyContent="space-between"
                        alignItems={{ xs: 'flex-start', sm: 'center' }}
                      >
                        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                          {t('financeTransactions.transactionTable.appliedRules.ruleNumber', {
                            index: index + 1,
                          })}
                        </Typography>
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
                          <Link
                            component="button"
                            underline="hover"
                            onClick={() => handleOpenRuleEditor(item.rule!)}
                            sx={{
                              fontWeight: 700,
                              fontSize: '0.8125rem',
                              whiteSpace: 'nowrap',
                              border: 'none',
                              padding: 0,
                              background: 'none',
                              cursor: 'pointer',
                              fontFamily: 'inherit',
                            }}
                          >
                            {t('financeTransactions.transactionTable.appliedRules.editRule')}
                          </Link>
                        </Stack>
                      </Stack>

                      <Box
                        sx={{
                          p: 2,
                          borderRadius: patternCardRadius,
                          boxShadow: theme.shadows[3],
                          bgcolor: 'background.default',
                          color: 'text.primary',
                        }}
                      >
                        <Stack spacing={0.75}>
                          <Typography
                            variant="overline"
                            color="text.secondary"
                            sx={{ fontWeight: 800, letterSpacing: '0.08em' }}
                          >
                            {t(`financeTransactions.transactionTable.appliedRules.patternType.${item.rule.patternType}`)}
                          </Typography>
                          <Typography variant="body1" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
                            &quot;{item.rule.pattern}&quot;
                          </Typography>
                        </Stack>
                      </Box>

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
                          <Typography variant="body2" color="text.secondary">
                            {fieldLabels.description}: {item.rule.descriptionOverride}
                          </Typography>
                        )}
                        {item.contributedFields.includes('tags') && (
                          <Stack spacing={0.5}>
                            <Typography variant="body2" color="text.secondary">
                              {fieldLabels.tags}
                            </Typography>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              {item.rule.tagsOverride?.map((tag) => (
                                <Chip key={tag} size="small" variant="outlined" label={`#${tag}`} />
                              ))}
                            </Stack>
                          </Stack>
                        )}
                      </Stack>
                    </Stack>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Stack>
        </Drawer.Body>
      </Drawer>

      <TransactionRuleDrawer
        open={isRuleDrawerOpen}
        rule={editingRule}
        onClose={() => setIsRuleDrawerOpen(false)}
        onSaved={() => {
          setIsRuleDrawerOpen(false);
          refetch();
        }}
      />
    </>
  );
}
