'use client';

import * as React from 'react';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import {
  DELETE_INVESTMENT_HOLDING,
  DELETE_INVESTMENT_TRANSACTION,
} from '@/lib/graphql/operations/banking/mutations';
import { INVESTMENT_CASH_BALANCE, INVESTMENT_CASH_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import {
  InvestmentHoldingsDocument,
  InvestmentPortfolioSummaryDocument,
  type InvestmentHoldingsQuery,
  type InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import { i18n, useTranslation } from '@/shared/i18n';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import type { MaturityDrawerHolding } from '../MaturityConfirmDrawer';
import type { CouponDrawerHolding } from '../CouponPaymentConfirmDrawer';
import { personalFinanceTranslations } from '../../i18n';
import type { InvestmentHoldingDetailKind } from './InvestmentHoldingDetailDrawer';
import {
  FixedIncomeHoldingDetailDocument,
  type FixedIncomeHoldingDetailQuery,
} from './fixed-income/api/queries.generated';
import { estimateCouponGrossForPeriod } from './fixed-income/couponDomain';
import { FundHoldingDetailDocument, type FundHoldingDetailQuery } from './fund/api/queries.generated';
import type { DetailPerformancePeriod } from './performance/detailPerformancePeriods';
import { parseUnknownDate, resolveDetailPerformanceWindow } from './performance/detailPerformancePeriods';
import { getAssetGroupKey, type HoldingRow } from './table/investmentTableHelpers';

type FixedIncomeCouponScheduleEntry =
  NonNullable<FixedIncomeHoldingDetailQuery['investmentHolding']>['couponSchedule'][number];

type DeleteInvestmentTransactionMutation = {
  deleteInvestmentTransaction: boolean;
};

type DeleteInvestmentTransactionMutationVariables = {
  id: string;
};

type FixedIncomeDetailTransaction = FixedIncomeHoldingDetailQuery['investmentTransactions'][number];
type FundDetailTransaction = FundHoldingDetailQuery['investmentTransactions'][number];
type DetailTimelineTransaction = FixedIncomeDetailTransaction | FundDetailTransaction;

interface UseHoldingDetailDrawerOptions {
  open: boolean;
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
  onClose?: () => void;
}

interface UseHoldingDetailDrawerReturn {
  fixedIncomeData: FixedIncomeHoldingDetailQuery | undefined;
  fundData: FundHoldingDetailQuery | undefined;
  holdingRowData: HoldingRow | null;
  fixedIncomeLoading: boolean;
  fundLoading: boolean;
  fixedIncomeError: unknown;
  fundError: unknown;
  refetchFixedIncome: () => Promise<unknown>;
  refetchFund: () => Promise<unknown>;
  maturityOpen: boolean;
  maturityHolding: MaturityDrawerHolding | null;
  openMaturity: () => void;
  closeMaturity: () => void;
  couponOpen: boolean;
  couponHolding: CouponDrawerHolding | null;
  couponEditingTransactionId: string | null;
  couponDestinationAccountId: string | null;
  couponEditingTransaction: {
    id: string;
    tradeDate: string;
    grossValue: number;
    netValue?: number | null;
    incomeTax?: number | null;
    iof?: number | null;
  } | null;
  couponDestinationAccount: {
    id: string;
    name: string;
    issuerName?: string | null;
    issuer?: { name?: string | null; logoAsset?: { publicUrl?: string | null } | null } | null;
  } | null;
  openCoupon: (scheduledEntry: unknown) => void;
  editCouponPayment: (entry: FixedIncomeCouponScheduleEntry) => void;
  deleteCouponPayment: (transactionId: string) => Promise<void>;
  closeCoupon: () => void;
  deletingCouponPayment: boolean;
  deletingTransactionId: string | null;
  deleteConfirmOpen: boolean;
  deleteInProgress: boolean;
  deletingTransaction: DetailTimelineTransaction | null;
  openDeleteTransaction: (id: string) => void;
  closeDeleteConfirm: () => void;
  confirmDeleteTransaction: () => Promise<void>;
  moreActionsAnchorEl: HTMLElement | null;
  openMoreActions: (el: HTMLElement) => void;
  closeMoreActions: () => void;
  holdingRemoveConfirmOpen: boolean;
  openHoldingRemoveConfirm: () => void;
  closeHoldingRemoveConfirm: () => void;
  confirmRemoveHolding: () => Promise<void>;
  performancePeriod: DetailPerformancePeriod;
  setPerformancePeriod: (period: DetailPerformancePeriod) => void;
  locale: string;
  actionHoldingRow: HoldingRow | null;
  actionHoldingGroupKey: ReturnType<typeof getAssetGroupKey> | null;
  deletingHolding: boolean;
}

export function useHoldingDetailDrawer(options: UseHoldingDetailDrawerOptions): UseHoldingDetailDrawerReturn {
  const { open, holdingId, kind, onClose } = options;
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const apolloClient = useApolloClient();
  const locale = i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';

  const shouldFetchFixedIncome = open && kind === 'fixedIncome' && Boolean(holdingId);
  const shouldFetchFund = open && (kind === 'fund' || kind === 'equity') && Boolean(holdingId);
  const [fundPerformancePeriod, setFundPerformancePeriod] = React.useState<DetailPerformancePeriod>('ALL');
  const [fundFirstTradeDate, setFundFirstTradeDate] = React.useState<Date | null>(null);
  const fundPerformanceWindow = React.useMemo(
    () => resolveDetailPerformanceWindow(fundPerformancePeriod, fundFirstTradeDate),
    [fundFirstTradeDate, fundPerformancePeriod],
  );

  React.useEffect(() => {
    if (!open) return;
    setFundPerformancePeriod('ALL');
    setFundFirstTradeDate(null);
  }, [holdingId, kind, open]);

  const fixedIncomeQuery = useQuery(FixedIncomeHoldingDetailDocument, {
    variables: { holdingId: holdingId || '' },
    skip: !shouldFetchFixedIncome,
    errorPolicy: 'all',
    fetchPolicy: 'cache-and-network',
    nextFetchPolicy: 'cache-and-network',
    notifyOnNetworkStatusChange: true,
  });

  const fundQuery = useQuery(FundHoldingDetailDocument, {
    variables: {
      holdingId: holdingId || '',
      performanceResolution: fundPerformanceWindow.resolution,
      performanceFromDate: fundPerformanceWindow.fromDate ?? null,
      performanceToDate: fundPerformanceWindow.toDate ?? null,
    },
    skip: !shouldFetchFund,
    errorPolicy: 'all',
    fetchPolicy: 'cache-and-network',
    nextFetchPolicy: 'cache-and-network',
    notifyOnNetworkStatusChange: true,
  });

  const fixedIncomeData =
    fixedIncomeQuery.data ??
    (fixedIncomeQuery.previousData?.investmentHolding?.id === holdingId ? fixedIncomeQuery.previousData : undefined);
  const fundData =
    fundQuery.data ??
    (fundQuery.previousData?.investmentHolding?.id === holdingId ? fundQuery.previousData : undefined);

  React.useEffect(() => {
    const tradeDates = (fundData?.investmentTransactions ?? [])
      .map((transaction) => parseUnknownDate(transaction.tradeDate))
      .filter((value): value is Date => value != null)
      .sort((a, b) => a.getTime() - b.getTime());
    const nextFirstTradeDate = tradeDates[0] ?? null;
    setFundFirstTradeDate((current) => {
      const currentTime = current?.getTime() ?? null;
      const nextTime = nextFirstTradeDate?.getTime() ?? null;
      return currentTime === nextTime ? current : nextFirstTradeDate;
    });
  }, [fundData?.investmentTransactions]);

  const [maturityConfirmOpen, setMaturityConfirmOpen] = React.useState(false);
  const [couponConfirmOpen, setCouponConfirmOpen] = React.useState(false);
  const [couponConfirmHolding, setCouponConfirmHolding] = React.useState<CouponDrawerHolding | null>(null);
  const [couponEditingTransaction, setCouponEditingTransaction] = React.useState<{
    id: string;
    tradeDate: string;
    grossValue: number;
    netValue?: number | null;
    incomeTax?: number | null;
    iof?: number | null;
  } | null>(null);
  const [couponDestinationAccount, setCouponDestinationAccount] = React.useState<{
    id: string;
    name: string;
    issuerName?: string | null;
    issuer?: { name?: string | null; logoAsset?: { publicUrl?: string | null } | null } | null;
  } | null>(null);
  const [deletingTransactionId, setDeletingTransactionId] = React.useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = React.useState(false);
  const [deleteInProgress, setDeleteInProgress] = React.useState(false);
  const [moreActionsAnchorEl, setMoreActionsAnchorEl] = React.useState<HTMLElement | null>(null);
  const [holdingRemoveConfirmOpen, setHoldingRemoveConfirmOpen] = React.useState(false);

  const [deleteInvestmentTransaction, { loading: deleteMutationLoading }] = useMutation<
    DeleteInvestmentTransactionMutation,
    DeleteInvestmentTransactionMutationVariables
  >(DELETE_INVESTMENT_TRANSACTION);
  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);

  const timelineTransactions = React.useMemo<readonly DetailTimelineTransaction[]>(
    () =>
      kind === 'fixedIncome'
        ? (fixedIncomeData?.investmentTransactions ?? [])
        : (fundData?.investmentTransactions ?? []),
    [fixedIncomeData?.investmentTransactions, fundData?.investmentTransactions, kind],
  );

  const deletingTransaction = React.useMemo<DetailTimelineTransaction | null>(() => {
    if (!deletingTransactionId) return null;
    return timelineTransactions.find((tx) => tx.id === deletingTransactionId) ?? null;
  }, [deletingTransactionId, timelineTransactions]);

  const deletingCouponPayment = deleteMutationLoading && !deleteConfirmOpen;

  React.useEffect(() => {
    setDeletingTransactionId(null);
    setDeleteConfirmOpen(false);
    setDeleteInProgress(false);
    setMoreActionsAnchorEl(null);
    setHoldingRemoveConfirmOpen(false);
  }, [holdingId, kind, open]);

  const maturityHolding = React.useMemo<MaturityDrawerHolding | null>(() => {
    const h = fixedIncomeData?.investmentHolding;
    if (!h || !h.financialAccount?.id || !h.asset?.id) return null;
    return {
      id: h.id,
      financialAccountId: h.financialAccount.id,
      financialAccountName: h.financialAccount.name,
      financialAccountIssuerName: h.financialAccount.issuer?.name ?? h.financialAccount.issuerName ?? null,
      financialAccountIssuerLogoUrl: h.financialAccount.issuer?.logoAsset?.publicUrl ?? null,
      maturityDate: h.maturityDate ? String(h.maturityDate) : null,
      grossValue: h.grossValue,
      netValue: h.netValue,
      incomeTax: h.incomeTax,
      iof: h.iof,
      issuerName: h.asset.issuerName ?? null,
      issuerLogoUrl: h.asset.logoAsset?.publicUrl ?? h.financialAccount.issuer?.logoAsset?.publicUrl ?? null,
      asset: { id: h.asset.id, name: h.asset.name, assetType: h.asset.assetType ?? null },
    };
  }, [fixedIncomeData?.investmentHolding]);

  const actionHoldingFinancialAccountId =
    fixedIncomeData?.investmentHolding?.financialAccount?.id ?? fundData?.investmentHolding?.financialAccount?.id ?? null;

  const holdingRowQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(InvestmentHoldingsDocument, {
    variables: { financialAccountId: actionHoldingFinancialAccountId ?? undefined, assetClass: undefined },
    skip: !open || !actionHoldingFinancialAccountId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const actionHoldingRow: HoldingRow | null = React.useMemo(() => {
    if (!holdingId) return null;
    return holdingRowQuery.data?.investmentHoldings.find((h) => h.id === holdingId) ?? null;
  }, [holdingId, holdingRowQuery.data?.investmentHoldings]);

  const resolvedAssetType =
    (kind === 'fixedIncome'
      ? fixedIncomeData?.investmentHolding?.asset.assetType
      : kind === 'fund' || kind === 'equity'
        ? fundData?.investmentHolding?.asset.assetType
        : null) ?? null;
  const actionHoldingGroupKey = resolvedAssetType != null ? getAssetGroupKey(String(resolvedAssetType)) : null;

  const openCoupon = React.useCallback(
    (scheduledEntry: unknown) => {
      const entry = scheduledEntry as FixedIncomeCouponScheduleEntry;
      const h = fixedIncomeData?.investmentHolding;
      if (!h?.financialAccount?.id || !h.asset?.id) return;
      const scheduledRaw = entry.scheduledDate ? String(entry.scheduledDate) : null;
      const scheduledDateOnly = scheduledRaw?.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? scheduledRaw;

      const txs = fixedIncomeData?.investmentTransactions ?? [];
      const principal = txs
        .filter((tx) => (tx.type as string) === 'PURCHASE' || (tx.type as string) === 'SUBSCRIPTION')
        .reduce((sum, tx) => sum + tx.grossValue, 0);

      const periodStartStr = entry.periodStart ? String(entry.periodStart).match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? null : null;
      const couponGross = estimateCouponGrossForPeriod(principal, h.fixedAnnualRate, periodStartStr, scheduledDateOnly);

      setCouponEditingTransaction(null);
      setCouponConfirmHolding({
        id: h.id,
        financialAccountId: h.financialAccount.id,
        quantity: h.quantity,
        averageCost: h.averageCost,
        couponFrequency: h.couponFrequency ?? null,
        issueDate: h.issueDate ? String(h.issueDate) : null,
        isTaxExempt: h.isTaxExempt,
        grossValue: h.grossValue ?? null,
        accruedInterest: couponGross,
        couponDate: scheduledDateOnly ?? null,
        issuerName: h.asset.issuerName ?? null,
        asset: { id: h.asset.id, name: h.asset.name, assetType: h.asset.assetType ?? null },
      });
      setCouponDestinationAccount({
        id: h.financialAccount.id,
        name: h.financialAccount.name,
        issuerName: h.financialAccount.issuerName ?? null,
        issuer: h.financialAccount.issuer
          ? {
              name: h.financialAccount.issuer.name ?? null,
              logoAsset: h.financialAccount.issuer.logoAsset
                ? { publicUrl: h.financialAccount.issuer.logoAsset.publicUrl ?? null }
                : null,
            }
          : null,
      });
      setCouponConfirmOpen(true);
    },
    [fixedIncomeData?.investmentHolding, fixedIncomeData?.investmentTransactions],
  );

  const editCouponPayment = React.useCallback(
    (entry: FixedIncomeCouponScheduleEntry) => {
      const h = fixedIncomeData?.investmentHolding;
      const tx = entry.matchedTransaction;
      if (!h?.financialAccount?.id || !h.asset?.id || !tx?.id) return;
      const tradeRaw = tx.tradeDate ? String(tx.tradeDate) : null;
      const tradeDateOnly = tradeRaw?.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? tradeRaw;

      setCouponEditingTransaction({
        id: tx.id,
        tradeDate: tradeDateOnly ?? '',
        grossValue: tx.grossValue ?? 0,
        netValue: tx.netValue ?? null,
        incomeTax: tx.incomeTax ?? null,
        iof: tx.iof ?? null,
      });
      setCouponConfirmHolding({
        id: h.id,
        financialAccountId: h.financialAccount.id,
        quantity: h.quantity,
        averageCost: h.averageCost,
        couponFrequency: h.couponFrequency ?? null,
        issueDate: h.issueDate ? String(h.issueDate) : null,
        isTaxExempt: h.isTaxExempt,
        grossValue: h.grossValue ?? null,
        accruedInterest: h.accruedInterest ?? null,
        couponDate: tradeDateOnly ?? null,
        issuerName: h.asset.issuerName ?? null,
        asset: { id: h.asset.id, name: h.asset.name, assetType: h.asset.assetType ?? null },
      });
      setCouponDestinationAccount({
        id: h.financialAccount.id,
        name: h.financialAccount.name,
        issuerName: h.financialAccount.issuerName ?? null,
        issuer: h.financialAccount.issuer
          ? {
              name: h.financialAccount.issuer.name ?? null,
              logoAsset: h.financialAccount.issuer.logoAsset
                ? { publicUrl: h.financialAccount.issuer.logoAsset.publicUrl ?? null }
                : null,
            }
          : null,
      });
      setCouponConfirmOpen(true);
    },
    [fixedIncomeData?.investmentHolding],
  );

  const deleteCouponPayment = React.useCallback(
    async (transactionId: string) => {
      if (!transactionId) return;
      await deleteInvestmentTransaction({ variables: { id: transactionId } });
      toast.success(t('investments.detailDrawer.couponSchedule.deleteSuccess'));
      await fixedIncomeQuery.refetch();
    },
    [deleteInvestmentTransaction, fixedIncomeQuery, t, toast],
  );

  const openDeleteTransaction = React.useCallback((transactionId: string) => {
    setDeletingTransactionId(transactionId);
    setDeleteConfirmOpen(true);
  }, []);

  const closeDeleteConfirm = React.useCallback(() => {
    if (deleteInProgress) return;
    setDeleteConfirmOpen(false);
    setDeletingTransactionId(null);
  }, [deleteInProgress]);

  const confirmDeleteTransaction = React.useCallback(async () => {
    if (!deletingTransactionId) return;
    setDeleteInProgress(true);
    try {
      await deleteInvestmentTransaction({ variables: { id: deletingTransactionId } });
      toast.success(t('investments.detailDrawer.timeline.deleteSuccess'));
      if (kind === 'fixedIncome') {
        await fixedIncomeQuery.refetch();
      } else {
        await fundQuery.refetch();
      }
      setDeleteConfirmOpen(false);
      setDeletingTransactionId(null);
    } catch (error) {
      void error;
      toast.error(t('investments.detailDrawer.timeline.deleteError'));
      return;
    } finally {
      setDeleteInProgress(false);
    }
  }, [deleteInvestmentTransaction, deletingTransactionId, fixedIncomeQuery, fundQuery, kind, t, toast]);

  const closeCoupon = React.useCallback(() => {
    setCouponConfirmOpen(false);
    setCouponConfirmHolding(null);
    setCouponDestinationAccount(null);
    setCouponEditingTransaction(null);
  }, []);

  const openMaturity = React.useCallback(() => {
    if (!maturityHolding) return;
    setMaturityConfirmOpen(true);
  }, [maturityHolding]);

  const closeMaturity = React.useCallback(() => {
    setMaturityConfirmOpen(false);
  }, []);

  const openMoreActions = React.useCallback((el: HTMLElement) => {
    setMoreActionsAnchorEl(el);
  }, []);

  const closeMoreActions = React.useCallback(() => {
    setMoreActionsAnchorEl(null);
  }, []);

  const openHoldingRemoveConfirm = React.useCallback(() => {
    setHoldingRemoveConfirmOpen(true);
  }, []);

  const closeHoldingRemoveConfirm = React.useCallback(() => {
    if (deletingHolding) return;
    setHoldingRemoveConfirmOpen(false);
  }, [deletingHolding]);

  const confirmRemoveHolding = React.useCallback(async () => {
    if (!actionHoldingRow) return;

    try {
      await deleteInvestmentHolding({ variables: { id: actionHoldingRow.id } });
      setHoldingRemoveConfirmOpen(false);
      closeMoreActions();
      onClose?.();
      await apolloClient.refetchQueries({
        include: [
          InvestmentHoldingsDocument,
          InvestmentPortfolioSummaryDocument,
          INVESTMENT_CASH_BALANCE,
          INVESTMENT_CASH_TRANSACTIONS,
        ],
      });
    } catch (error) {
      toast.error(extractErrorMessage(error, t('errors.default', { ns: 'common' })));
      setHoldingRemoveConfirmOpen(false);
    }
  }, [actionHoldingRow, apolloClient, closeMoreActions, deleteInvestmentHolding, onClose, t, toast]);

  return {
    fixedIncomeData,
    fundData,
    holdingRowData: actionHoldingRow,
    fixedIncomeLoading: fixedIncomeQuery.loading,
    fundLoading: fundQuery.loading,
    fixedIncomeError: fixedIncomeQuery.error,
    fundError: fundQuery.error,
    refetchFixedIncome: fixedIncomeQuery.refetch,
    refetchFund: fundQuery.refetch,
    maturityOpen: maturityConfirmOpen,
    maturityHolding,
    openMaturity,
    closeMaturity,
    couponOpen: couponConfirmOpen,
    couponHolding: couponConfirmHolding,
    couponEditingTransactionId: couponEditingTransaction?.id ?? null,
    couponDestinationAccountId: couponDestinationAccount?.id ?? null,
    couponEditingTransaction,
    couponDestinationAccount,
    openCoupon,
    editCouponPayment,
    deleteCouponPayment,
    closeCoupon,
    deletingCouponPayment,
    deletingTransactionId,
    deleteConfirmOpen,
    deleteInProgress,
    deletingTransaction,
    openDeleteTransaction,
    closeDeleteConfirm,
    confirmDeleteTransaction,
    moreActionsAnchorEl,
    openMoreActions,
    closeMoreActions,
    holdingRemoveConfirmOpen,
    openHoldingRemoveConfirm,
    closeHoldingRemoveConfirm,
    confirmRemoveHolding,
    performancePeriod: fundPerformancePeriod,
    setPerformancePeriod: setFundPerformancePeriod,
    locale,
    actionHoldingRow,
    actionHoldingGroupKey,
    deletingHolding,
  };
}

export type { UseHoldingDetailDrawerOptions, UseHoldingDetailDrawerReturn };
