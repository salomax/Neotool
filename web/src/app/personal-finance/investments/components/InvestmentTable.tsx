'use client';

import React from 'react';
import { useQuery } from '@apollo/client/react';
import i18n from 'i18next';
import {
  Box,
  Button,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SavingsIcon from '@mui/icons-material/Savings';
import { useTranslation } from '@/shared/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { personalFinanceTranslations } from '../../i18n';
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS } from '@/lib/graphql/operations/banking/queries';
import {
  AllInvestmentHoldingsQuery,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import { Indexer } from '@/lib/graphql/types/__generated__/graphql';
import { AssetGroupCard } from './AssetGroupCard';

interface InvestmentTableProps {
  selectedAccountId: string | null;
  onRecordHolding?: () => void;
}

type HoldingRow =
  | InvestmentHoldingsQuery['investmentHoldings'][number]
  | AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];

const INDEXER_LABELS: Partial<Record<Indexer, string>> = {
  [Indexer.Cdi]: 'CDI',
  [Indexer.Di]: 'DI',
  [Indexer.Ipca]: 'IPCA',
  [Indexer.IgpM]: 'IGP-M',
  [Indexer.IgpDi]: 'IGP-DI',
  [Indexer.Inpc]: 'INPC',
  [Indexer.Tr]: 'TR',
  [Indexer.Selic]: 'SELIC',
  [Indexer.PreFixado]: 'Prefixado',
};

const EMPTY_HOLDINGS: HoldingRow[] = [];

type AssetGroupKey =
  | 'variableIncomeB3'
  | 'bankFixedIncome'
  | 'creditFixedIncome'
  | 'treasuryDirect'
  | 'mutualFunds'
  | 'pension'
  | 'structured'
  | 'others';

const ASSET_GROUP_ORDER: readonly AssetGroupKey[] = [
  'bankFixedIncome',
  'creditFixedIncome',
  'treasuryDirect',
  'variableIncomeB3',
  'mutualFunds',
  'pension',
  'structured',
  'others',
] as const;

const ASSET_TYPES_BY_GROUP: Record<AssetGroupKey, readonly string[]> = {
  variableIncomeB3: [
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
  mutualFunds: ['MUTUAL_FUND'],
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

export function InvestmentTable({ selectedAccountId, onRecordHolding }: InvestmentTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const [expandedGroup, setExpandedGroup] = React.useState<AssetGroupKey | false>(false);
  const didInitExpansionRef = React.useRef(false);

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

  const loading = selectedAccountId ? accountHoldingsQuery.loading : consolidatedHoldingsQuery.loading;
  const error = selectedAccountId ? accountHoldingsQuery.error : consolidatedHoldingsQuery.error;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((acc, groupKey) => {
      acc[groupKey] = [];
      return acc;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    base.others = [];

    holdings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [holdings]);

  const groups = React.useMemo(() => {
    const computed = ASSET_GROUP_ORDER.map((groupKey) => {
      const groupHoldings = groupedHoldings[groupKey] || [];
      const currencyCodes = new Set(groupHoldings.map((holding) => holding.asset?.currencyCode).filter(Boolean));

      let sum = 0;
      let hasValue = false;

      groupHoldings.forEach((holding) => {
        if (typeof holding.grossValue === 'number') {
          sum += holding.grossValue;
          hasValue = true;
        }
      });

      const currencyCode = currencyCodes.size === 1 ? Array.from(currencyCodes)[0] : null;
      const total = hasValue && currencyCode ? formatCurrency(sum, currencyCode, locale) : '—';

      return {
        key: groupKey,
        title: t(`investments.table.groups.${groupKey}`),
        count: groupHoldings.length,
        total,
        summary: t('investments.table.groupSummary', {
          count: groupHoldings.length,
          total,
        }),
        holdings: groupHoldings,
      };
    });
    return computed.filter((group) => group.count > 0);
  }, [groupedHoldings, locale, t]);

  React.useEffect(() => {
    if (loading || error) return;

    if (groups.length === 0) {
      if (expandedGroup) setExpandedGroup(false);
      didInitExpansionRef.current = false;
      return;
    }

    const firstNonEmpty = groups[0]?.key ?? false;

    if (!didInitExpansionRef.current) {
      setExpandedGroup(firstNonEmpty);
      didInitExpansionRef.current = true;
      return;
    }

    if (expandedGroup && !groups.some((group) => group.key === expandedGroup)) setExpandedGroup(firstNonEmpty);
  }, [expandedGroup, error, groups, loading]);

  React.useEffect(() => {
    setExpandedGroup(false);
    didInitExpansionRef.current = false;
  }, [selectedAccountId]);

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

      {!loading && !error && holdings.length === 0 && (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('investments.table.emptyState')}
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

      {!loading && !error && holdings.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {groups.map((group) => {
            const isExpanded = expandedGroup === group.key;
            const leadingIcon = group.key === 'bankFixedIncome' ? <SavingsIcon sx={{ fontSize: 18 }} /> : null;

            return (
              <AssetGroupCard
                key={group.key}
                expanded={isExpanded}
                onToggle={(expanded) => setExpandedGroup(expanded ? group.key : false)}
                title={group.title}
                summary={group.summary}
                leadingIcon={leadingIcon}
              >
                <Table sx={{ minWidth: 650, borderCollapse: 'separate', borderSpacing: '0' }} aria-label="investments table">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ borderBottom: 'none', bgcolor: 'transparent' }}>
                        <Label>{t('investments.table.columns.asset')}</Label>
                      </TableCell>
                      <TableCell sx={{ borderBottom: 'none', bgcolor: 'transparent' }}>
                        <Label>{t('investments.table.columns.type')}</Label>
                      </TableCell>
                      <TableCell sx={{ borderBottom: 'none', bgcolor: 'transparent' }}>
                        <Label>{t('investments.table.columns.indexer')}</Label>
                      </TableCell>
                      <TableCell align="right" sx={{ borderBottom: 'none', bgcolor: 'transparent' }}>
                        <Label>{t('investments.table.columns.grossValue')}</Label>
                      </TableCell>
                      <TableCell sx={{ borderBottom: 'none', bgcolor: 'transparent' }}>
                        <Label>{t('investments.table.columns.maturityDate')}</Label>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {group.holdings.map((holding) => (
                      <TableRow key={holding.id}>
                        <TableCell>
                          <Typography variant="body2" fontWeight={700}>
                            {holding.asset.name}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{formatAssetType(holding.asset.assetType, t)}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{formatIndexer(holding, locale)}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">
                            {formatCurrency(holding.grossValue, holding.asset.currencyCode, locale)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{formatDate(holding.maturityDate, locale)}</Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </AssetGroupCard>
            );
          })}
        </Box>
      )}
    </TableContainer>
  );
}
