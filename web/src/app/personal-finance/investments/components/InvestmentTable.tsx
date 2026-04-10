'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import { alpha, Box, CircularProgress, Paper, TableContainer, Typography, useTheme } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { useToast } from '@/shared/providers';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { personalFinanceTranslations } from '../../i18n';
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
  INVESTMENT_TRANSACTIONS,
} from '@/lib/graphql/operations/banking/queries';
import { DELETE_INVESTMENT_HOLDING } from '@/lib/graphql/operations/banking/mutations';
import {
  FinancialAccountsQuery,
  InvestmentCashBalanceQuery,
  InvestmentCashBalanceQueryVariables,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import { InvestmentSource, InvestmentTransaction } from '@/lib/graphql/types/__generated__/graphql';
import type { MaturityDrawerHolding } from '../MaturityConfirmDrawer';
import type { InvestmentHoldingDetailKind } from './InvestmentHoldingDetailDrawer';
import { CashTransactionsInlineTable } from './CashTransactionsInlineTable';
import { InvestmentEditDrawers } from './InvestmentEditDrawers';
import { InvestmentGroupTable } from './table/InvestmentGroupTable';
import { InvestmentHoldingActionsMenu } from './table/InvestmentHoldingActionsMenu';
import type { IssuerSelectionAccount } from './IssuerSelection';
import { useInvestmentCardExpansion } from './table/useInvestmentCardExpansion';
import {
  ASSET_GROUP_ORDER,
  formatCurrency,
  getAssetGroupKey,
  getCurrentLocale,
  getHoldingKind,
  pickGroupValueOrigin,
  type HoldingKind,
  type AssetGroupKey,
  type HoldingRow,
  type InvestmentHoldingGroup,
} from './table/investmentTableHelpers';

const InvestmentHoldingDetailDrawer = dynamic(
  () => import('./InvestmentHoldingDetailDrawer').then((mod) => ({ default: mod.InvestmentHoldingDetailDrawer })),
  { loading: () => null },
);

const MaturityConfirmDrawer = dynamic(
  () => import('../MaturityConfirmDrawer').then((mod) => ({ default: mod.MaturityConfirmDrawer })),
  { loading: () => null },
);

type BankAccountRow = NonNullable<FinancialAccountsQuery['financialAccounts']>[number];

interface InvestmentTableProps {
  selectedAccountId: string | null;
  bankAccounts: BankAccountRow[];
  investmentAccounts?: IssuerSelectionAccount[];
  onRecordHolding?: () => void;
  searchQuery?: string;
}

interface ActionMenuState {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
}

interface DetailDrawerState {
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
}

interface EditDrawerState {
  holding: HoldingRow | null;
  transaction: InvestmentTransaction | null;
  kind: HoldingKind;
  financialAccountId: string | null;
}

const EMPTY_HOLDINGS: HoldingRow[] = [];

function getTransactionTimestamp(transaction: InvestmentTransaction): number {
  const rawValue = transaction.settlementDate ?? transaction.tradeDate;
  if (!rawValue) return Number.NEGATIVE_INFINITY;

  const timestamp = new Date(String(rawValue)).getTime();
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp;
}

function sortTransactionsByMostRecent(transactions: InvestmentTransaction[]): InvestmentTransaction[] {
  return [...transactions].sort((left, right) => {
    const timestampDelta = getTransactionTimestamp(right) - getTransactionTimestamp(left);
    if (timestampDelta !== 0) return timestampDelta;
    return right.id.localeCompare(left.id);
  });
}

export function InvestmentTable({
  selectedAccountId,
  bankAccounts,
  investmentAccounts = [],
  searchQuery,
}: InvestmentTableProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();
  const [actionMenu, setActionMenu] = React.useState<ActionMenuState>({ anchorEl: null, holding: null });
  const [holdingToRemove, setHoldingToRemove] = React.useState<HoldingRow | null>(null);
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({ holdingId: null, kind: null });
  const [editDrawer, setEditDrawer] = React.useState<EditDrawerState>({
    holding: null,
    transaction: null,
    kind: 'equity',
    financialAccountId: null,
  });
  const [maturityConfirmOpen, setMaturityConfirmOpen] = React.useState(false);
  const [maturityConfirmHolding, setMaturityConfirmHolding] = React.useState<MaturityDrawerHolding | null>(null);

  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);

  const cashQuery = useQuery<InvestmentCashBalanceQuery, InvestmentCashBalanceQueryVariables>(INVESTMENT_CASH_BALANCE, {
    variables: { financialAccountId: selectedAccountId ?? undefined },
    errorPolicy: 'all',
  });

  const holdingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId ?? null },
    errorPolicy: 'all',
  });

  const refreshInvestmentData = React.useCallback(async () => {
    await Promise.all([
      holdingsQuery.refetch(),
      cashQuery.refetch(),
      apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY] }),
    ]);
  }, [apolloClient, cashQuery, holdingsQuery]);

  const displayCurrency = React.useMemo(() => {
    if (selectedAccountId) {
      return investmentAccounts.find((account) => account.id === selectedAccountId)?.currencyCode ?? 'BRL';
    }

    const codes = [...new Set(investmentAccounts.map((account) => account.currencyCode).filter(Boolean))];
    return codes.length === 1 ? codes[0]! : 'BRL';
  }, [investmentAccounts, selectedAccountId]);

  const resolveFinancialAccountId = React.useCallback(
    (holding: HoldingRow) => selectedAccountId ?? holding.financialAccount?.id ?? null,
    [selectedAccountId],
  );

  const resolveManualTransactions = React.useCallback(
    async (holdingId: string) => {
      const result = await apolloClient.query<{ investmentTransactions: InvestmentTransaction[] }>({
        query: INVESTMENT_TRANSACTIONS,
        variables: { holdingId },
        fetchPolicy: 'network-only',
        errorPolicy: 'all',
      });

      const manualTransactions = (result.data?.investmentTransactions ?? []).filter(
        (transaction) => transaction.source === InvestmentSource.Manual,
      );

      return sortTransactionsByMostRecent(manualTransactions);
    },
    [apolloClient],
  );

  const resolveLatestManualTransaction = React.useCallback(
    async (holdingId: string) => {
      const transactions = await resolveManualTransactions(holdingId);
      return transactions[0] ?? null;
    },
    [resolveManualTransactions],
  );

  const closeActionMenu = React.useCallback(() => {
    setActionMenu({ anchorEl: null, holding: null });
  }, []);

  const handleOpenActionMenu = React.useCallback((anchorEl: HTMLElement, holding: HoldingRow) => {
    setActionMenu({ anchorEl, holding });
  }, []);

  const closeDetailDrawer = React.useCallback(() => {
    setDetailDrawer({ holdingId: null, kind: null });
  }, []);

  const handleOpenDetail = React.useCallback((holding: HoldingRow) => {
    setDetailDrawer({
      holdingId: holding.id,
      kind: getHoldingKind(holding.asset?.assetType),
    });
  }, []);

  const closeEditDrawer = React.useCallback(() => {
    setEditDrawer({
      holding: null,
      transaction: null,
      kind: 'equity',
      financialAccountId: null,
    });
  }, []);

  const handleOpenEdit = React.useCallback(
    async (holding: HoldingRow) => {
      try {
        const transaction = await resolveLatestManualTransaction(holding.id);

        if (!transaction) {
          toast.info('Esta posição não possui lançamento manual editável.');
          return;
        }

        setEditDrawer({
          holding,
          transaction,
          kind: getHoldingKind(holding.asset?.assetType),
          financialAccountId: resolveFinancialAccountId(holding),
        });
      } catch {
        toast.error('Não foi possível carregar os dados para edição.');
      }
    },
    [resolveFinancialAccountId, resolveLatestManualTransaction, toast],
  );

  const closeMaturityConfirmDrawer = React.useCallback(() => {
    setMaturityConfirmOpen(false);
    setMaturityConfirmHolding(null);
  }, []);

  const handleOpenMaturityConfirmDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;

    setMaturityConfirmHolding({
      id: holding.id,
      maturityDate: holding.maturityDate ? String(holding.maturityDate) : null,
      grossValue: holding.grossValue ?? null,
      netValue: holding.netValue ?? null,
      incomeTax: holding.incomeTax ?? null,
      iof: holding.iof ?? null,
      issuerName: holding.asset.issuerName ?? null,
      issuerLogoUrl: holding.asset.logoAsset?.publicUrl ?? holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
      asset: { id: holding.asset.id, name: holding.asset.name },
    });
    setMaturityConfirmOpen(true);
  }, []);

  const handleConfirmRemove = async () => {
    if (!holdingToRemove) return;

    try {
      await deleteInvestmentHolding({ variables: { id: holdingToRemove.id } });
      setHoldingToRemove(null);
      await refreshInvestmentData();
    } catch {
      setHoldingToRemove(null);
    }
  };

  const holdings = holdingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS;

  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? '').trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);

  const displayedHoldings = React.useMemo(() => {
    if (!hasSearchQuery) return holdings;

    return holdings.filter((holding) => {
      const haystack = [
        holding.asset?.name ?? '',
        holding.asset?.ticker ?? '',
        holding.asset?.issuerName ?? '',
        holding.asset?.assetType ?? '',
        holding.financialAccount?.name ?? '',
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(normalizedSearchQuery);
    });
  }, [hasSearchQuery, holdings, normalizedSearchQuery]);

  const detailHolding = React.useMemo(
    () => holdings.find((holding) => holding.id === detailDrawer.holdingId) ?? null,
    [detailDrawer.holdingId, holdings],
  );

  const detailHoldingIssuerLogoUrl = React.useMemo(
    () => detailHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
    [detailHolding],
  );

  const loading = holdingsQuery.loading;
  const error = holdingsQuery.error;
  const cashBalance = cashQuery.data?.investmentCashBalance;
  const cashFormatted =
    cashBalance != null && Number.isFinite(cashBalance) ? formatCurrency(cashBalance, displayCurrency, locale) : null;
  const showCashCard =
    !cashQuery.loading && !cashQuery.error && cashBalance != null && Number.isFinite(cashBalance) && cashFormatted != null;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((accumulator, groupKey) => {
      accumulator[groupKey] = [];
      return accumulator;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    displayedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [displayedHoldings]);

  const { groups } = React.useMemo(() => {
    let holdingsGrossTotal = 0;
    displayedHoldings.forEach((holding) => {
      if (typeof holding.grossValue === 'number') holdingsGrossTotal += holding.grossValue;
    });

    const portfolioDenominator =
      showCashCard && cashBalance != null && Number.isFinite(cashBalance)
        ? holdingsGrossTotal + cashBalance
        : holdingsGrossTotal;

    const computedGroups: InvestmentHoldingGroup[] = ASSET_GROUP_ORDER.map((groupKey) => {
      const groupHoldings = groupedHoldings[groupKey] ?? [];
      const currencyCodes = new Set(groupHoldings.map((holding) => holding.asset?.currencyCode).filter(Boolean));

      let totalGrossValue = 0;
      let totalPrincipal = 0;
      let hasGrossValue = false;
      let hasPrincipal = false;

      groupHoldings.forEach((holding) => {
        if (typeof holding.grossValue === 'number') {
          totalGrossValue += holding.grossValue;
          hasGrossValue = true;
        }

        if (typeof holding.averageCost === 'number' && typeof holding.quantity === 'number') {
          totalPrincipal += holding.averageCost * holding.quantity;
          hasPrincipal = true;
        }
      });

      const currencyCode = currencyCodes.size === 1 ? Array.from(currencyCodes)[0] : null;
      const total = hasGrossValue && currencyCode ? formatCurrency(totalGrossValue, currencyCode, locale) : '—';
      const appliedTotal = hasPrincipal && currencyCode ? formatCurrency(totalPrincipal, currencyCode, locale) : '—';
      const allocationPercent = portfolioDenominator > 0 && hasGrossValue ? totalGrossValue / portfolioDenominator : null;

      return {
        key: groupKey,
        title: t(`investments.table.groups.${groupKey}`),
        count: groupHoldings.length,
        total,
        appliedTotal,
        valueOriginKey: pickGroupValueOrigin(groupHoldings),
        gainPercent: totalPrincipal > 0 && hasGrossValue ? (totalGrossValue - totalPrincipal) / totalPrincipal : null,
        allocationPercent,
        summary: t('investments.table.groupSummary', {
          count: groupHoldings.length,
          total,
        }),
        holdings: groupHoldings,
      };
    }).filter((group) => group.count > 0);

    return {
      groups: computedGroups,
    };
  }, [cashBalance, displayedHoldings, groupedHoldings, locale, showCashCard, t]);

  const { expandedGroups, toggleGroup } = useInvestmentCardExpansion({
    selectedAccountId,
    groups,
    hasSearchQuery,
    loading,
    hasError: Boolean(error),
  });

  const emptyStateMessage = hasSearchQuery ? t('investments.table.noMatches') : t('investments.table.emptyState');

  const tableRadius = (theme as { custom?: { radius?: { table?: number } } }).custom?.radius?.table ?? theme.shape.borderRadius;
  const hasHoldingsList = !loading && !error && displayedHoldings.length > 0;

  const cashSection = React.useMemo(() => {
    if (!showCashCard) return null;

    return (
      <Box
        sx={{
          border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
          borderRadius: `${tableRadius}px`,
          overflow: 'hidden',
          bgcolor: 'background.paper',
          mb: hasHoldingsList ? 2 : 0,
        }}
      >
        <CashTransactionsInlineTable
          financialAccountId={selectedAccountId}
          investmentAccounts={investmentAccounts}
        />
      </Box>
    );
  }, [hasHoldingsList, investmentAccounts, selectedAccountId, showCashCard, tableRadius, theme.palette.divider]);

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
      {showCashCard && (loading || error || displayedHoldings.length === 0) ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>{cashSection}</Box>
      ) : null}

      {loading ? (
        <Box sx={{ py: 6, display: 'flex', justifyContent: 'center' }}>
          <CircularProgress size={20} />
        </Box>
      ) : null}

      {!loading && error ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="error.main">
            {t('investments.table.error')}
          </Typography>
        </Box>
      ) : null}

      {!loading && !error && displayedHoldings.length === 0 && !showCashCard ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {emptyStateMessage}
          </Typography>
        </Box>
      ) : null}

      {!loading && !error && displayedHoldings.length > 0 ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {cashSection}
          {groups.map((group) => (
            <InvestmentGroupTable
              key={group.key}
              group={group}
              expanded={expandedGroups.has(group.key)}
              locale={locale}
              onToggleGroup={toggleGroup}
              onOpenDetail={handleOpenDetail}
              onOpenMaturityConfirm={handleOpenMaturityConfirmDrawer}
              onOpenActions={handleOpenActionMenu}
            />
          ))}
        </Box>
      ) : null}

      <InvestmentHoldingActionsMenu
        anchorEl={actionMenu.anchorEl}
        holding={actionMenu.holding}
        deleting={deletingHolding}
        onClose={closeActionMenu}
        onViewDetails={handleOpenDetail}
        onEdit={handleOpenEdit}
        onRemove={setHoldingToRemove}
      />

      <ConfirmationDialog
        open={Boolean(holdingToRemove)}
        onClose={() => setHoldingToRemove(null)}
        onConfirm={handleConfirmRemove}
        title={t('investments.deleteConfirmation.title')}
        message={t('investments.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={deletingHolding}
      />

      <InvestmentHoldingDetailDrawer
        open={Boolean(detailDrawer.holdingId && detailDrawer.kind)}
        holdingId={detailDrawer.holdingId}
        kind={detailDrawer.kind}
        initialTitle={detailHolding?.asset?.name ?? null}
        initialAssetType={detailHolding?.asset?.assetType ?? null}
        initialLogoUrl={detailHolding?.asset?.logoAsset?.publicUrl ?? null}
        initialIssuerLogoUrl={detailHoldingIssuerLogoUrl}
        onClose={closeDetailDrawer}
      />

      <MaturityConfirmDrawer
        open={maturityConfirmOpen}
        onClose={closeMaturityConfirmDrawer}
        holding={maturityConfirmHolding}
        bankAccounts={bankAccounts}
        onSuccess={refreshInvestmentData}
      />

      <InvestmentEditDrawers
        holding={editDrawer.holding}
        transaction={editDrawer.transaction}
        kind={editDrawer.kind}
        financialAccountId={editDrawer.financialAccountId}
        financialAccounts={investmentAccounts}
        onClose={closeEditDrawer}
        onRecorded={refreshInvestmentData}
      />
    </TableContainer>
  );
}
