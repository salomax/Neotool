'use client';

import * as React from 'react';
import { NetworkStatus, type ErrorLike as ApolloError } from '@apollo/client/core';
import { useApolloClient, useQuery } from '@apollo/client/react';
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_GRID_DATA,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from '@/lib/graphql/operations/banking/queries';
import type {
  InvestmentCashBalanceQuery,
  InvestmentCashBalanceQueryVariables,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import type { IssuerSelectionAccount } from '../IssuerSelection';
import {
  ASSET_GROUP_ORDER,
  formatCurrency,
  getAssetGroupKey,
  getCurrentLocale,
  pickGroupValueOrigin,
  type AssetGroupKey,
  type HoldingRow,
  type InvestmentHoldingGroup,
} from './investmentTableHelpers';

interface UseInvestmentTableDataOptions {
  selectedAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  visibleGroupKeys?: AssetGroupKey[];
  visibleAssetTypes?: string[];
}

interface UseInvestmentTableDataReturn {
  holdings: HoldingRow[];
  scopedHoldings: HoldingRow[];
  groups: InvestmentHoldingGroup[];
  cashFormatted: string | null;
  showCashCard: boolean;
  holdingsFetching: boolean;
  holdingsLoading: boolean;
  holdingsError: ApolloError | undefined;
  displayCurrency: string;
  locale: string;
  refreshInvestmentData: () => Promise<void>;
}

const EMPTY_HOLDINGS: HoldingRow[] = [];

export function useInvestmentTableData(options: UseInvestmentTableDataOptions): UseInvestmentTableDataReturn {
  const { selectedAccountId, investmentAccounts, searchQuery, visibleGroupKeys, visibleAssetTypes } = options;
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();

  const cashQuery = useQuery<InvestmentCashBalanceQuery, InvestmentCashBalanceQueryVariables>(INVESTMENT_CASH_BALANCE, {
    variables: { financialAccountId: selectedAccountId ?? undefined },
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const holdingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId ?? null },
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const refreshInvestmentData = React.useCallback(async () => {
    await Promise.all([
      holdingsQuery.refetch(),
      cashQuery.refetch(),
      apolloClient.refetchQueries({
        include: [INVESTMENT_CASH_TRANSACTIONS, INVESTMENT_PORTFOLIO_SUMMARY, INVESTMENT_GRID_DATA],
      }),
    ]);
  }, [apolloClient, cashQuery, holdingsQuery]);

  const displayCurrency = React.useMemo(() => {
    if (selectedAccountId) {
      return investmentAccounts.find((account) => account.id === selectedAccountId)?.currencyCode ?? 'BRL';
    }

    const codes = [...new Set(investmentAccounts.map((account) => account.currencyCode).filter(Boolean))];
    return codes.length === 1 ? codes[0]! : 'BRL';
  }, [investmentAccounts, selectedAccountId]);

  const holdings = holdingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS;

  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? '').trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);
  const cashMatchesSearch = React.useMemo(() => {
    if (!hasSearchQuery) return true;

    const cashSearchText = [
      t('investments.cash.cardTitle'),
      t('investments.summary.cashBalance'),
      'cash',
    ]
      .join(' ')
      .toLowerCase();

    return cashSearchText.includes(normalizedSearchQuery);
  }, [hasSearchQuery, normalizedSearchQuery, t]);

  const displayedHoldings = React.useMemo(() => {
    const scopedByAssetType =
      visibleAssetTypes && visibleAssetTypes.length > 0
        ? holdings.filter((holding) => {
            const assetType = holding.asset?.assetType;
            return assetType ? visibleAssetTypes.includes(assetType) : false;
          })
        : holdings;

    if (!hasSearchQuery) return scopedByAssetType;

    return scopedByAssetType.filter((holding) => {
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
  }, [hasSearchQuery, holdings, normalizedSearchQuery, visibleAssetTypes]);

  const scopedHoldings = React.useMemo(() => {
    if (visibleGroupKeys === undefined) return displayedHoldings;
    if (visibleGroupKeys.length === 0) return EMPTY_HOLDINGS;
    return displayedHoldings.filter((holding) => visibleGroupKeys.includes(getAssetGroupKey(holding.asset?.assetType)));
  }, [displayedHoldings, visibleGroupKeys]);

  const holdingsLoading = holdingsQuery.loading;
  const holdingsError = holdingsQuery.error ?? undefined;
  const holdingsFetching =
    !holdingsError &&
    (holdingsLoading ||
      holdingsQuery.networkStatus === NetworkStatus.refetch ||
      holdingsQuery.networkStatus === NetworkStatus.setVariables);

  const cashBalance = cashQuery.data?.investmentCashBalance;
  const cashFormatted =
    cashBalance != null && Number.isFinite(cashBalance) ? formatCurrency(cashBalance, displayCurrency, locale) : null;
  const showCashCard =
    !cashQuery.loading &&
    !cashQuery.error &&
    cashBalance != null &&
    Number.isFinite(cashBalance) &&
    cashBalance !== 0 &&
    cashFormatted != null &&
    cashMatchesSearch;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((accumulator, groupKey) => {
      accumulator[groupKey] = [];
      return accumulator;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    scopedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [scopedHoldings]);

  const groups = React.useMemo(() => {
    let holdingsGrossTotal = 0;
    displayedHoldings.forEach((holding) => {
      if (typeof holding.grossValue === 'number') holdingsGrossTotal += holding.grossValue;
    });

    const portfolioDenominator =
      showCashCard && cashBalance != null && Number.isFinite(cashBalance)
        ? holdingsGrossTotal + cashBalance
        : holdingsGrossTotal;

    return ASSET_GROUP_ORDER.map((groupKey) => {
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
    })
      .filter((group) => group.count > 0)
      .filter((group) => (visibleGroupKeys === undefined ? true : visibleGroupKeys.includes(group.key)));
  }, [cashBalance, displayedHoldings, groupedHoldings, locale, showCashCard, t, visibleGroupKeys]);

  return {
    holdings,
    scopedHoldings,
    groups,
    cashFormatted,
    showCashCard,
    holdingsFetching,
    holdingsLoading,
    holdingsError,
    displayCurrency,
    locale,
    refreshInvestmentData,
  };
}

export type { UseInvestmentTableDataOptions, UseInvestmentTableDataReturn };
