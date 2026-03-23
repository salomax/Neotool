'use client';

import React from 'react';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import i18n from 'i18next';
import {
  alpha,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
  useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import InfoIcon from '@mui/icons-material/Info';
import { useTranslation } from '@/shared/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { GainPercent } from '@/shared/components/ui/data-display';
import { FINANCE_TABLE_BODY_FONT_SIZE, FINANCE_TABLE_BODY_LINE_HEIGHT, FINANCE_TABLE_TYPOGRAPHY_SX } from '@/shared/components/ui/data-display/Table';
import { personalFinanceTranslations } from '../../i18n';
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS, INVESTMENT_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import {
  DELETE_INVESTMENT_HOLDING,
  DELETE_INVESTMENT_TRANSACTION,
  RECORD_INVESTMENT_TRANSACTION,
} from '@/lib/graphql/operations/banking/mutations';
import {
  AllInvestmentHoldingsQuery,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  HoldingPricingMode,
  Indexer,
  InvestmentSource,
  InvestmentTransaction,
  ValuationSource,
} from '@/lib/graphql/types/__generated__/graphql';
import { AssetGroupCard } from './AssetGroupCard';
import { AttachmentSection } from '../../components/AttachmentSection';
import { formatFundAssetType } from '@/lib/utils/mapCvmFundAssetType';
import { AssetTypeIcon, type AssetTypeIconKind } from '@/shared/components/ui/icons/AssetTypeIcon';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';

interface InvestmentTableProps {
  selectedAccountId: string | null;
  onRecordHolding?: () => void;
  searchQuery?: string;
}

type HoldingRow =
  | InvestmentHoldingsQuery['investmentHoldings'][number]
  | AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];

const INDEXER_LABELS: Partial<Record<Indexer, string>> = {
  [Indexer.Cdi]: 'CDI',
  [Indexer.Di]: 'DI',
  [Indexer.Ipca]: 'IPCA+',
  [Indexer.IgpM]: 'IGP-M',
  [Indexer.IgpDi]: 'IGP-DI',
  [Indexer.Inpc]: 'INPC',
  [Indexer.Tr]: 'TR',
  [Indexer.Selic]: 'SELIC',
  [Indexer.PreFixado]: 'Prefixado',
};

const EMPTY_HOLDINGS: HoldingRow[] = [];
const FUND_GROUP_KEYS = new Set<AssetGroupKey>(['mutualFunds', 'pension']);
const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(['bankFixedIncome', 'creditFixedIncome', 'treasuryDirect']);

type AssetGroupKey =
  | 'variableIncome'
  | 'bankFixedIncome'
  | 'creditFixedIncome'
  | 'treasuryDirect'
  | 'mutualFunds'
  | 'pension'
  | 'structured'
  | 'others';

const ICON_KIND_BY_GROUP: Record<AssetGroupKey, AssetTypeIconKind> = {
  bankFixedIncome: 'FIXED_INCOME',
  creditFixedIncome: 'FIXED_INCOME',
  treasuryDirect: 'FIXED_INCOME',
  variableIncome: 'STOCK',
  mutualFunds: 'FUND',
  pension: 'FUND',
  structured: 'STRUCTURED',
  others: 'OTHER',
};

function AssetGroupIcon({
  groupKey,
  ...props
}: { groupKey: AssetGroupKey } & Omit<React.ComponentProps<typeof AssetTypeIcon>, 'kind' | 'assetType'>) {
  return <AssetTypeIcon kind={ICON_KIND_BY_GROUP[groupKey]} {...props} />;
}

const ASSET_GROUP_ORDER: readonly AssetGroupKey[] = [
  'bankFixedIncome',
  'creditFixedIncome',
  'treasuryDirect',
  'variableIncome',
  'mutualFunds',
  'pension',
  'structured',
  'others',
] as const;

const ASSET_TYPES_BY_GROUP: Record<AssetGroupKey, readonly string[]> = {
  variableIncome: [
    'STOCK',
    'STOCK_RIGHTS',
    'BDR',
    'REAL_ESTATE_FUND',
    'AGRIBUSINESS_FUND',
    'ETF',
    'ETF_FIXED_INCOME',
    'ETF_INTERNATIONAL',
  ],
  bankFixedIncome: ['CDB', 'RDB', 'LCI', 'LCA', 'LC', 'LF', 'LIG'],
  creditFixedIncome: ['DEBENTURES', 'CRI', 'CRA'],
  treasuryDirect: ['TREASURY'],
  mutualFunds: ['MUTUAL_FUND', 'FIXED_INCOME_FUND', 'EQUITY_FUND', 'MULTI_MARKET_FUND', 'FOREX_FUND'],
  pension: ['PGBL', 'VGBL'],
  structured: ['COE'],
  others: ['CRYPTO', 'OPTION', 'DERIVATIVES', 'OTHER'],
};

function getAssetGroupKey(assetType: string | null | undefined): AssetGroupKey {
  if (!assetType) return 'others';

  const normalized = String(assetType);
  const match = ASSET_GROUP_ORDER.find((groupKey) => ASSET_TYPES_BY_GROUP[groupKey].includes(normalized));
  return match || 'others';
}

function getCurrentLocale(): string {
  return i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';
}

function formatCurrency(value: number | null | undefined, currencyCode = 'BRL', locale = getCurrentLocale()): string {
  if (value == null) return '—';
  return new Intl.NumberFormat(locale, { style: 'currency', currency: currencyCode }).format(value);
}

function formatDate(value: unknown, locale = getCurrentLocale()): string {
  if (!value) return '—';

  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale);
  }

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(locale);
}

function formatAssetType(assetType: string, t: (key: string, options?: Record<string, unknown>) => string): string {
  return t(`investments.assetTypes.${assetType}` as string, {
    defaultValue: assetType.replaceAll('_', ' '),
  });
}

function formatIndexer(holding: HoldingRow, locale = getCurrentLocale()): string {
  if (!holding.indexer) return '—';

  if (holding.indexer === Indexer.PreFixado) {
    if (holding.preFixedRate == null) return INDEXER_LABELS[Indexer.PreFixado] || 'Prefixado';
    const annualRate = holding.preFixedRate * 100;
    return `${annualRate.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}% a.a.`;
  }

  const label = INDEXER_LABELS[holding.indexer] || holding.indexer;
  if (holding.indexerPercentage == null) return label;

  return `${label} ${(holding.indexerPercentage * 100).toFixed(0)}%`;
}

function formatFinancialAccountLabel(holding: HoldingRow): string {
  const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
  return consolidated.financialAccount?.name ?? '—';
}

function getFinancialAccountAvatarProps(holding: HoldingRow) {
  const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
  const financialAccount = consolidated.financialAccount;
  const name = financialAccount?.name ?? '';

  return {
    src: financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
    alt: name || undefined,
    label: name ? name.slice(0, 1).toUpperCase() : '—',
    backgroundColor: financialAccount?.themeColor ?? 'grey.300',
  };
}

export function InvestmentTable({ selectedAccountId, onRecordHolding, searchQuery }: InvestmentTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();
  const [expandedGroups, setExpandedGroups] = React.useState<Set<AssetGroupKey>>(() => new Set());
  const didInitExpansionRef = React.useRef(false);
  const [actionMenuAnchorEl, setActionMenuAnchorEl] = React.useState<HTMLElement | null>(null);
  const [actionMenuHolding, setActionMenuHolding] = React.useState<HoldingRow | null>(null);
  const [holdingToRemove, setHoldingToRemove] = React.useState<HoldingRow | null>(null);
  const [holdingToEdit, setHoldingToEdit] = React.useState<HoldingRow | null>(null);
  const [editingTx, setEditingTx] = React.useState<InvestmentTransaction | null>(null);
  const [editDraft, setEditDraft] = React.useState<{ tradeDate: string; quantity: string; unitPrice: string; grossValue: string }>({
    tradeDate: '',
    quantity: '',
    unitPrice: '',
    grossValue: '',
  });

  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);
  const [deleteInvestmentTransaction, { loading: deletingTransaction }] = useMutation(DELETE_INVESTMENT_TRANSACTION);
  const [recordInvestmentTransaction, { loading: recordingEdit }] = useMutation(RECORD_INVESTMENT_TRANSACTION);
  const deleting = deletingHolding || deletingTransaction;

  const closeActionMenu = () => {
    setActionMenuAnchorEl(null);
    setActionMenuHolding(null);
  };

  const toDateInputValue = (value: unknown) => {
    if (!value) return '';
    const raw = String(value);
    return raw.split('T')[0] ?? '';
  };

  const resolveManualTransactions = async (holdingId: string) => {
    const result = await apolloClient.query<{
      investmentTransactions: InvestmentTransaction[];
    }>({
      query: INVESTMENT_TRANSACTIONS,
      variables: { holdingId },
      fetchPolicy: 'network-only',
      errorPolicy: 'all',
    });

    const list = result.data?.investmentTransactions ?? [];
    return list.filter((tx) => tx.source === InvestmentSource.Manual);
  };

  const resolveLatestManualTransaction = async (holdingId: string) => {
    const list = await resolveManualTransactions(holdingId);
    return list[0] ?? null;
  };

  const resolveFinancialAccountId = (holding: HoldingRow) => {
    if (selectedAccountId) return selectedAccountId;
    const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
    return consolidated.financialAccount?.id ?? null;
  };

  const handleConfirmRemove = async () => {
    const holding = holdingToRemove;
    if (!holding) return;

    try {
      await deleteInvestmentHolding({ variables: { id: holding.id } });
      setHoldingToRemove(null);

      if (selectedAccountId) await accountHoldingsQuery.refetch();
      else await consolidatedHoldingsQuery.refetch();
    } catch {
      setHoldingToRemove(null);
    }
  };

  const handleOpenEdit = async (holding: HoldingRow) => {
    const tx = await resolveLatestManualTransaction(holding.id);
    if (!tx) return;

    setHoldingToEdit(holding);
    setEditingTx(tx);
    setEditDraft({
      tradeDate: toDateInputValue(tx.tradeDate),
      quantity: tx.quantity != null ? String(tx.quantity) : '',
      unitPrice: tx.unitPrice != null ? String(tx.unitPrice) : '',
      grossValue: String(tx.grossValue ?? ''),
    });
  };

  const closeEditDialog = () => {
    setHoldingToEdit(null);
    setEditingTx(null);
  };

  const handleSaveEdit = async () => {
    const holding = holdingToEdit;
    const tx = editingTx;
    if (!holding || !tx) return;

    const financialAccountIdResolved = resolveFinancialAccountId(holding);
    const assetId = holding.asset?.id ?? null;
    if (!financialAccountIdResolved || !assetId) return;

    const tradeDate = editDraft.tradeDate;
    const grossValue = Number(editDraft.grossValue);
    const quantity = tx.quantity != null ? Number(editDraft.quantity) : null;
    const unitPrice = tx.unitPrice != null ? Number(editDraft.unitPrice) : null;

    if (!tradeDate) return;
    if (!Number.isFinite(grossValue) || grossValue <= 0) return;
    if (quantity != null && (!Number.isFinite(quantity) || quantity <= 0)) return;
    if (unitPrice != null && (!Number.isFinite(unitPrice) || unitPrice <= 0)) return;

    try {
      await deleteInvestmentTransaction({ variables: { id: tx.id } });

      const isFixedIncome =
        holding.asset?.assetType &&
        (getAssetGroupKey(holding.asset.assetType) === 'bankFixedIncome' ||
          getAssetGroupKey(holding.asset.assetType) === 'creditFixedIncome' ||
          getAssetGroupKey(holding.asset.assetType) === 'treasuryDirect');

      await recordInvestmentTransaction({
        variables: {
          input: {
            financialAccountId: financialAccountIdResolved,
            assetId,
            direction: tx.direction,
            type: tx.type,
            quantity: quantity ?? undefined,
            unitPrice: unitPrice ?? undefined,
            grossValue,
            tradeDate,
            settlementDate: tradeDate,
            indexer: isFixedIncome ? holding.indexer ?? undefined : undefined,
            indexerPercentage: isFixedIncome ? holding.indexerPercentage ?? undefined : undefined,
            preFixedRate: isFixedIncome ? holding.preFixedRate ?? undefined : undefined,
            issueDate: isFixedIncome ? toDateInputValue(holding.issueDate) || undefined : undefined,
            maturityDate: isFixedIncome ? toDateInputValue(holding.maturityDate) || undefined : undefined,
            isTaxExempt: isFixedIncome ? holding.isTaxExempt ?? undefined : undefined,
          },
        },
      });

      closeEditDialog();
      if (selectedAccountId) await accountHoldingsQuery.refetch();
      else await consolidatedHoldingsQuery.refetch();
    } catch {
      closeEditDialog();
    }
  };

  const EstimatedInfoBadge = React.forwardRef<HTMLSpanElement, { ml?: number; mr?: number }>(({ ml = 0, mr = 0 }, ref) => (
    <Box
      ref={ref}
      component="span"
      sx={{
        width: 18,
        height: 18,
        border: 'none',
        color: 'info.main',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        ml,
        mr,
        flex: '0 0 auto',
      }}
      aria-hidden
    >
      <InfoIcon sx={{ fontSize: 18, color: alpha(theme.palette.primary.main, 0.56), }} />
    </Box>
  ));
  EstimatedInfoBadge.displayName = 'EstimatedInfoBadge';

  const accountHoldingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId || '' },
    skip: !selectedAccountId,
    errorPolicy: 'all',
  });

  const consolidatedHoldingsQuery = useQuery<AllInvestmentHoldingsQuery>(ALL_INVESTMENT_HOLDINGS, {
    skip: Boolean(selectedAccountId),
    errorPolicy: 'all',
  });

  const holdings = React.useMemo(() => {
    if (selectedAccountId) return accountHoldingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS;
    return consolidatedHoldingsQuery.data?.allInvestmentHoldings ?? EMPTY_HOLDINGS;
  }, [accountHoldingsQuery.data?.investmentHoldings, consolidatedHoldingsQuery.data?.allInvestmentHoldings, selectedAccountId]);

  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? '').trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);

  const displayedHoldings = React.useMemo(() => {
    if (!hasSearchQuery) return holdings;

    return holdings.filter((holding) => {
      const assetName = holding.asset?.name ?? '';
      const ticker = holding.asset?.ticker ?? '';
      const issuerName = holding.asset?.issuerName ?? '';
      const assetType = holding.asset?.assetType ?? '';
      const financialAccountName = (holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number]).financialAccount?.name ?? '';

      const haystack = `${assetName} ${ticker} ${issuerName} ${assetType} ${financialAccountName}`.toLowerCase();
      return haystack.includes(normalizedSearchQuery);
    });
  }, [hasSearchQuery, holdings, normalizedSearchQuery]);

  const loading = selectedAccountId ? accountHoldingsQuery.loading : consolidatedHoldingsQuery.loading;
  const error = selectedAccountId ? accountHoldingsQuery.error : consolidatedHoldingsQuery.error;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((acc, groupKey) => {
      acc[groupKey] = [];
      return acc;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    base.others = [];

    displayedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [displayedHoldings]);

  const groups = React.useMemo(() => {
    let portfolioTotal = 0;
    displayedHoldings.forEach((holding) => {
      if (typeof holding.grossValue === 'number') portfolioTotal += holding.grossValue;
    });

    const computed = ASSET_GROUP_ORDER.map((groupKey) => {
      const groupHoldings = groupedHoldings[groupKey] || [];
      const currencyCodes = new Set(groupHoldings.map((holding) => holding.asset?.currencyCode).filter(Boolean));

      let sum = 0;
      let principalSum = 0;
      let hasValue = false;
      let hasEstimated = false;

      groupHoldings.forEach((holding) => {
        if (typeof holding.grossValue === 'number') {
          sum += holding.grossValue;
          hasValue = true;
        }
        if (typeof holding.averageCost === 'number' && typeof holding.quantity === 'number') {
          principalSum += holding.averageCost * holding.quantity;
        }
        if (holding.valuationSource === ValuationSource.Estimated || holding.valuationSource === ValuationSource.MarketData) {
          hasEstimated = true;
        }
      });

      const currencyCode = currencyCodes.size === 1 ? Array.from(currencyCodes)[0] : null;
      const total = hasValue && currencyCode ? formatCurrency(sum, currencyCode, locale) : '—';
      const allocationPercent = portfolioTotal > 0 && hasValue ? sum / portfolioTotal : null;

      return {
        key: groupKey,
        title: t(`investments.table.groups.${groupKey}`),
        count: groupHoldings.length,
        total,
        hasEstimatedInGroup: hasEstimated,
        gainPercent: principalSum > 0 && hasValue ? (sum - principalSum) / principalSum : null,
        allocationPercent,
        summary: t('investments.table.groupSummary', {
          count: groupHoldings.length,
          total,
        }),
        holdings: groupHoldings,
      };
    });
    return computed.filter((group) => group.count > 0);
  }, [displayedHoldings, groupedHoldings, locale, t]);

  React.useEffect(() => {
    if (loading || error) return;

    if (groups.length === 0) {
      if (expandedGroups.size > 0) setExpandedGroups(new Set());
      didInitExpansionRef.current = false;
      return;
    }

    if (hasSearchQuery) {
      const nextExpandedGroups = new Set(groups.map((group) => group.key));
      const isSame =
        nextExpandedGroups.size === expandedGroups.size &&
        Array.from(nextExpandedGroups).every((key) => expandedGroups.has(key));

      if (!isSame) setExpandedGroups(nextExpandedGroups);
      didInitExpansionRef.current = true;
      return;
    }

    const firstNonEmpty = groups[0]?.key;

    if (!didInitExpansionRef.current) {
      if (firstNonEmpty) setExpandedGroups(new Set([firstNonEmpty]));
      didInitExpansionRef.current = true;
      return;
    }

    if (expandedGroups.size === 0) return;

    const availableKeys = new Set(groups.map((group) => group.key));
    const nextExpandedGroups = new Set(Array.from(expandedGroups).filter((key) => availableKeys.has(key)));

    if (nextExpandedGroups.size !== expandedGroups.size) {
      if (nextExpandedGroups.size === 0 && firstNonEmpty) {
        setExpandedGroups(new Set([firstNonEmpty]));
      } else {
        setExpandedGroups(nextExpandedGroups);
      }
    }
  }, [error, expandedGroups, groups, hasSearchQuery, loading]);

  React.useEffect(() => {
    setExpandedGroups(new Set());
    didInitExpansionRef.current = false;
  }, [selectedAccountId]);

  React.useEffect(() => {
    if (hasSearchQuery) return;
    setExpandedGroups(new Set());
    didInitExpansionRef.current = false;
  }, [hasSearchQuery]);

  const isEditOpen = Boolean(holdingToEdit && editingTx);
  const isEditBusy = deleting || recordingEdit;
  const canSaveEdit = (() => {
    if (!isEditOpen) return false;
    if (!editDraft.tradeDate) return false;
    const grossValue = Number(editDraft.grossValue);
    if (!Number.isFinite(grossValue) || grossValue <= 0) return false;
    if (editingTx?.quantity != null) {
      const q = Number(editDraft.quantity);
      if (!Number.isFinite(q) || q <= 0) return false;
    }
    if (editingTx?.unitPrice != null) {
      const u = Number(editDraft.unitPrice);
      if (!Number.isFinite(u) || u <= 0) return false;
    }
    return true;
  })();

  const isActionMenuFixedIncome = actionMenuHolding?.asset?.assetType
    ? FIXED_INCOME_GROUP_KEYS.has(getAssetGroupKey(actionMenuHolding.asset.assetType))
    : false;

  const emptyStateMessage = hasSearchQuery ? t('investments.table.noMatches') : t('investments.table.emptyState');

  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
      }}
      data-testid="investment-table"
    >
      {loading && (
        <Box sx={{ py: 6, display: 'flex', justifyContent: 'center' }}>
          <CircularProgress size={20} />
        </Box>
      )}

      {!loading && error && (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="error.main">
            {t('investments.table.error')}
          </Typography>
        </Box>
      )}

      {!loading && !error && displayedHoldings.length === 0 && (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {emptyStateMessage}
          </Typography>
          {onRecordHolding && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<AddIcon />}
              onClick={onRecordHolding}
              data-testid="record-holding-inline-btn"
            >
              {t('investments.actions.recordHolding')}
            </Button>
          )}
        </Box>
      )}

      {!loading && !error && displayedHoldings.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {groups.map((group) => {
            const isExpanded = expandedGroups.has(group.key);
            const leadingIcon = (
              <AssetGroupIcon groupKey={group.key} sx={{ fontSize: 16 }} />
            );
            const divider = alpha(theme.palette.divider, 0.7);
            const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;
            const isFundGroup = FUND_GROUP_KEYS.has(group.key);
            const isFixedIncomeGroup = FIXED_INCOME_GROUP_KEYS.has(group.key);
            const showFinancialAccountColumn = !selectedAccountId;
            const dataColumnCount = isFundGroup ? 4 : isFixedIncomeGroup ? 4 : 5;
            const colWidth = `calc((100% - 52px) / ${dataColumnCount})`;

            const summaryAdornment =
              group.hasEstimatedInGroup ? (
                <Tooltip title={t('investments.table.groupTotalEstimated')}>
                  <EstimatedInfoBadge ml={0.25} />
                </Tooltip>
              ) : null;

            return (
              <AssetGroupCard
                key={group.key}
                expanded={isExpanded}
                onToggle={(expanded) =>
                  setExpandedGroups((prev) => {
                    const next = new Set(prev);
                    if (expanded) next.add(group.key);
                    else next.delete(group.key);
                    return next;
                  })
                }
                title={group.title}
                summary={group.summary}
                leadingIcon={leadingIcon}
                summaryAdornment={summaryAdornment}
                gainPercent={group.gainPercent}
                allocationPercent={group.allocationPercent}
              >
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                  <Table
                    sx={{
                      width: '100%',
                      tableLayout: 'fixed',
                      borderCollapse: 'collapse',
                      ...FINANCE_TABLE_TYPOGRAPHY_SX,
                      '& .MuiTableCell-root': {
                        px: 1.5,
                        pb: 0.75,
                        borderBottom: 'none',
                        bgcolor: 'transparent',
                      },
                    }}
                    aria-label="investments table header"
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ width: colWidth }}>
                          <Label>{t('investments.table.columns.asset')}</Label>
                        </TableCell>
                        {!isFixedIncomeGroup && (
                          <TableCell sx={{ width: colWidth }}>
                            <Label>{t('investments.table.columns.type')}</Label>
                          </TableCell>
                        )}
                        {isFundGroup ? (
                          <>
                            <TableCell align="right" sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.grossValue')}</Label>
                            </TableCell>
                            <TableCell align="right" sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.gain')}</Label>
                            </TableCell>
                          </>
                        ) : (
                          <>
                            <TableCell sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.maturityDate')}</Label>
                            </TableCell>
                            <TableCell align="right" sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.grossValue')}</Label>
                            </TableCell>
                          <TableCell align="right" sx={{ width: colWidth }}>
                            <Label>{t('investments.table.columns.gain')}</Label>
                          </TableCell>
                          </>
                        )}
                        <TableCell align="center" sx={{ width: 52 }} />
                      </TableRow>
                    </TableHead>
                  </Table>

                  <Box
                    sx={{
                      borderRadius: `${tableRadius}px`,
                      overflow: 'hidden',
                      bgcolor: 'background.paper',
                    }}
                  >
                    <Table
                      sx={{
                        width: '100%',
                        tableLayout: 'fixed',
                        borderCollapse: 'collapse',
                        ...FINANCE_TABLE_TYPOGRAPHY_SX,
                        '& .MuiTableCell-root': {
                          px: 1.5,
                        },
                      }}
                      aria-label="investments table"
                    >
                      <TableBody>
                    {group.holdings.map((holding) => {
                      const source = holding.valuationSource;
                      const isExpired = holding.isExpired;
                      const refDate = typeof holding.valuationReferenceDate === 'string' ? holding.valuationReferenceDate : null;
                      const refDateFormatted = refDate ? formatDate(refDate, locale) : '';
                      const currencyCode = holding.asset?.currencyCode ?? 'BRL';
                      const manualPricing = holding.pricingMode === HoldingPricingMode.Manual;
                      const marketDataPricing = holding.pricingMode === HoldingPricingMode.MarketData;

                      const valueUnavailable = source === ValuationSource.Unavailable || typeof holding.grossValue !== 'number';
                      const valueContent = valueUnavailable ? (
                        '—'
                      ) : (
                        formatCurrency(holding.grossValue, currencyCode, locale)
                      );
                      const valueSourceKey =
                        source === ValuationSource.Manual || source === ValuationSource.MarketData || source === ValuationSource.Provider
                          ? source
                          : manualPricing
                            ? ValuationSource.Manual
                            : marketDataPricing
                              ? ValuationSource.MarketData
                          : null;
                      const valueSourceLabel = valueSourceKey
                        ? t(`investments.table.valueSource.${valueSourceKey}`)
                        : null;
                      const valueIcon =
                        source === ValuationSource.Estimated ||
                        source === ValuationSource.Manual ||
                        source === ValuationSource.MarketData ? (
                          <Tooltip
                            title={t(`investments.table.valuationTooltip.${source}`, {
                              date: refDateFormatted || '—',
                            })}
                          >
                            <EstimatedInfoBadge mr={0.25} />
                          </Tooltip>
                        ) : null;

                      const principal = holding.averageCost * holding.quantity;
                      const grossVal = typeof holding.grossValue === 'number' ? holding.grossValue : null;
                      const absolute = principal > 0 && grossVal != null ? grossVal - principal : null;
                      const growthRatio = absolute != null && principal > 0 ? absolute / principal : null;
                      const gainUnavailable = grossVal == null || source === ValuationSource.Unavailable;
                      const gainEstimated = source === ValuationSource.Estimated || source === ValuationSource.MarketData;
                      const canMutate = holding.source === InvestmentSource.Manual;

                      return (
                        <TableRow
                          key={holding.id}
                          sx={{
                            '& td': { borderBottom: `1px solid ${divider}`, py: 1.5 },
                            '&:last-of-type td': { borderBottom: 'none' },
                          }}
                        >
                          <TableCell sx={{ width: colWidth }}>
                            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                              {(() => {
                                const avatar = getFinancialAccountAvatarProps(holding);
                                return (
                                  <Avatar
                                    src={avatar.src}
                                    alt={avatar.alt}
                                    variant="rounded"
                                    sx={{
                                      width: 32,
                                      height: 32,
                                      bgcolor: avatar.src ? 'transparent' : avatar.backgroundColor,
                                      borderRadius: '.3rem',
                                      fontSize: '0.75rem',
                                      flex: '0 0 auto',
                                      '& img': { objectFit: 'contain' },
                                    }}
                                  >
                                    {!avatar.src && avatar.label}
                                  </Avatar>
                                );
                              })()}
                              <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                                <Typography variant="body2" noWrap>
                                  {(() => {
                                    const name = holding.asset?.name ?? '—';
                                    if (!isFixedIncomeGroup) return name;
                                    const typeLabel = holding.asset ? formatAssetType(holding.asset.assetType, t) : '—';
                                    const idx = formatIndexer(holding, locale);
                                    return idx ? `${name} • ${typeLabel} ${idx}` : `${name} • ${typeLabel}`;
                                  })()}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" noWrap sx={{ mt: 0.25 }}>
                                  {formatFinancialAccountLabel(holding)}
                                </Typography>
                              </Box>
                            </Stack>
                            {isExpired && source !== 'PROVIDER' && (() => {
                              const maturityDateStr = holding.maturityDate ? formatDate(holding.maturityDate, locale) : '';
                              return maturityDateStr ? (
                                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                                  {t('investments.table.expiredNudge', { maturityDate: maturityDateStr }) as string}
                                </Typography>
                              ) : null;
                            })()}
                          </TableCell>
                          {isFundGroup ? (
                            <TableCell sx={{ width: colWidth }}>
                              <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                                <Typography variant="body2" noWrap>
                                  {holding.asset ? formatFundAssetType(holding.asset.assetType, t) : '—'}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" noWrap sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                  {holding.quantity.toLocaleString(locale, { maximumFractionDigits: 6 })}
                                </Typography>
                              </Box>
                            </TableCell>
                          ) : (
                            !isFixedIncomeGroup && (
                              <TableCell sx={{ width: colWidth }}>
                                <Typography variant="body2">
                                  {holding.asset ? formatAssetType(holding.asset.assetType, t) : '—'}
                                </Typography>
                              </TableCell>
                            )
                          )}
                          {isFundGroup ? (
                            <>
                              <TableCell align="right" sx={{ width: colWidth }}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                    {valueIcon}
                                    <Typography variant="body2" component="span" fontWeight="medium">
                                      {valueContent}
                                    </Typography>
                                  </Box>
                                  {valueSourceLabel ? (
                                    <Typography variant="caption" color="text.secondary">
                                      {valueSourceLabel}
                                    </Typography>
                                  ) : null}
                                </Box>
                              </TableCell>
                              <TableCell align="right" sx={{ width: colWidth }}>
                                <Box
                                  sx={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'flex-end',
                                    justifyContent: 'center',
                                    gap: 0.5,
                                  }}
                                >
                                  {gainUnavailable ? (
                                    <Typography variant="body2">—</Typography>
                                  ) : (
                                    <>
                                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                        {gainEstimated && (
                                          <Tooltip title={t('investments.table.valuationTooltip.ESTIMATED', { date: refDateFormatted })}>
                                            <EstimatedInfoBadge mr={0.25} />
                                          </Tooltip>
                                        )}
                                        <Typography
                                          variant="body2"
                                          fontWeight="medium"
                                          color={
                                            absolute != null && absolute >= 0
                                              ? 'success.main'
                                              : absolute != null
                                                ? 'error.main'
                                                : 'text.primary'
                                          }
                                        >
                                          {absolute != null ? formatCurrency(absolute, currencyCode, locale) : '—'}
                                        </Typography>
                                      </Box>
                                      {growthRatio != null && principal > 0 && absolute != null && (
                                        <GainPercent value={growthRatio} locale={locale} />
                                      )}
                                    </>
                                  )}
                                </Box>
                              </TableCell>
                            </>
                          ) : (
                            <>
                              <TableCell sx={{ width: colWidth }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
                                  <Typography variant="body2">{formatDate(holding.maturityDate, locale)}</Typography>
                                  {isExpired && (
                                    <Chip
                                      size="small"
                                      label={
                                        <Typography
                                          variant="body2"
                                          sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, lineHeight: FINANCE_TABLE_BODY_LINE_HEIGHT, opacity: 0.7 }}
                                        >
                                          {t('investments.table.expiredBadge')}
                                        </Typography>
                                      }
                                      sx={{
                                        height: 28,
                                        bgcolor: 'action.selected',
                                        borderColor: 'divider',
                                        '& .MuiChip-label': { px: 1 },
                                      }}
                                      color="default"
                                      variant="outlined"
                                    />
                                  )}
                                </Box>
                              </TableCell>                              
                              <TableCell align="right" sx={{ width: colWidth }}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                    {valueIcon}
                                    <Typography variant="body2" component="span" fontWeight="medium">
                                      {valueContent}
                                    </Typography>
                                  </Box>
                                  {isFundGroup && valueSourceLabel ? (
                                    <Typography variant="caption" color="text.secondary">
                                      {valueSourceLabel}
                                    </Typography>
                                  ) : null}
                                </Box>
                              </TableCell>

                              {isFixedIncomeGroup ? (
                                <TableCell align="right" sx={{ width: colWidth }}>
                                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'center', gap: 0.5 }}>
                                    {gainUnavailable ? (
                                      <Typography variant="body2">—</Typography>
                                    ) : (
                                      <>
                                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                          {gainEstimated && (
                                            <Tooltip title={t('investments.table.valuationTooltip.ESTIMATED', { date: refDateFormatted })}>
                                              <EstimatedInfoBadge mr={0.25} />
                                            </Tooltip>
                                          )}
                                          <Typography
                                            variant="body2"
                                            fontWeight="medium"
                                            color={absolute != null && absolute >= 0 ? 'success.main' : absolute != null ? 'error.main' : 'text.primary'}
                                          >
                                            {absolute != null ? formatCurrency(absolute, currencyCode, locale) : '—'}
                                          </Typography>
                                        </Box>
                                        {growthRatio != null && principal > 0 && absolute != null && (
                                          <GainPercent value={growthRatio} locale={locale} />
                                        )}
                                      </>
                                    )}
                                  </Box>
                                </TableCell>
                              ) : (
                                <TableCell align="right" sx={{ width: colWidth }}>
                                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'center', gap: 0.5 }}>
                                    {gainUnavailable ? (
                                      <Typography variant="body2">—</Typography>
                                    ) : (
                                      <>
                                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                          {gainEstimated && (
                                            <Tooltip title={t('investments.table.valuationTooltip.ESTIMATED', { date: refDateFormatted })}>
                                              <EstimatedInfoBadge mr={0.25} />
                                            </Tooltip>
                                          )}
                                          <Typography
                                            variant="body2"
                                            fontWeight="medium"
                                            color={absolute != null && absolute >= 0 ? 'success.main' : absolute != null ? 'error.main' : 'text.primary'}
                                          >
                                            {absolute != null ? formatCurrency(absolute, currencyCode, locale) : '—'}
                                          </Typography>
                                        </Box>
                                        {growthRatio != null && principal > 0 && absolute != null && (
                                          <GainPercent value={growthRatio} locale={locale} />
                                        )}
                                      </>
                                    )}
                                  </Box>
                                </TableCell>
                              )}
                            </>
                          )}
                          <TableCell align="center" sx={{ width: 52 }}>
                            <IconButton
                              size="small"
                              onClick={(e) => {
                                e.stopPropagation();
                                if (!canMutate) return;
                                setActionMenuAnchorEl(e.currentTarget);
                                setActionMenuHolding(holding);
                              }}
                              aria-label={t('investments.table.rowActions.ariaLabel')}
                              disabled={!canMutate}
                            >
                              <MoreVertIcon fontSize="small" />
                            </IconButton>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                      </TableBody>
                    </Table>
                  </Box>
                </Box>
              </AssetGroupCard>
            );
          })}
        </Box>
      )}

      <Menu
        open={Boolean(actionMenuAnchorEl)}
        anchorEl={actionMenuAnchorEl}
        onClose={closeActionMenu}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem
          onClick={async () => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            await handleOpenEdit(holding);
          }}
          disabled={deleting || recordingEdit}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.edit')}</ListItemText>
        </MenuItem>
        {isActionMenuFixedIncome && <Divider />}
        <MenuItem
          onClick={() => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            setHoldingToRemove(holding);
          }}
          sx={{
            color: 'error.main',
            ...(isActionMenuFixedIncome
              ? {
                  '&:hover': { bgcolor: alpha(theme.palette.error.main, 0.08) },
                  '&.Mui-focusVisible': { bgcolor: alpha(theme.palette.error.main, 0.12) },
                }
              : {}),
          }}
          disabled={deleting || recordingEdit}
        >
          <ListItemIcon sx={{ color: 'inherit' }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.remove')}</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmationDialog
        open={Boolean(holdingToRemove)}
        onClose={() => setHoldingToRemove(null)}
        onConfirm={handleConfirmRemove}
        title={t('investments.deleteConfirmation.title')}
        message={t('investments.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={deleting}
      />

      <Dialog open={isEditOpen} onClose={closeEditDialog} fullWidth maxWidth="xs">
        <DialogTitle>{t('investments.editDialog.title')}</DialogTitle>
        <DialogContent sx={{ pt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('investments.editDialog.fields.tradeDate')}
            type="date"
            value={editDraft.tradeDate}
            onChange={(e) => setEditDraft((prev) => ({ ...prev, tradeDate: e.target.value }))}
            fullWidth
            InputLabelProps={{ shrink: true }}
          />

          {editingTx?.quantity != null && (
            <TextField
              label={t('investments.editDialog.fields.quantity')}
              type="number"
              value={editDraft.quantity}
              onChange={(e) => setEditDraft((prev) => ({ ...prev, quantity: e.target.value }))}
              fullWidth
              inputProps={{ step: 'any', min: 0 }}
            />
          )}

          {editingTx?.unitPrice != null && (
            <TextField
              label={t('investments.editDialog.fields.unitPrice')}
              type="number"
              value={editDraft.unitPrice}
              onChange={(e) => setEditDraft((prev) => ({ ...prev, unitPrice: e.target.value }))}
              fullWidth
              inputProps={{ step: 'any', min: 0 }}
            />
          )}

          <TextField
            label={t('investments.editDialog.fields.grossValue')}
            type="number"
            value={editDraft.grossValue}
            onChange={(e) => setEditDraft((prev) => ({ ...prev, grossValue: e.target.value }))}
            fullWidth
            inputProps={{ step: 'any', min: 0 }}
          />

          {holdingToEdit && (
            <AttachmentSection
              holdingId={holdingToEdit.id}
              attachments={'attachments' in holdingToEdit ? holdingToEdit.attachments ?? [] : []}
              onChanged={() => {
                if (selectedAccountId) accountHoldingsQuery.refetch();
                else consolidatedHoldingsQuery.refetch();
              }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeEditDialog} disabled={isEditBusy}>
            {t('actions.cancel')}
          </Button>
          <Button variant="contained" onClick={handleSaveEdit} disabled={!canSaveEdit || isEditBusy}>
            {t('actions.save')}
          </Button>
        </DialogActions>
      </Dialog>
    </TableContainer>
  );
}
