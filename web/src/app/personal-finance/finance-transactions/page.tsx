'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import {
  Container,
  Box,
  CircularProgress,
  Divider,
  useTheme,
} from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { CreateFinancialAccountDrawer } from './account/CreateFinancialAccountDrawer';
import { FinancialAccountSelector } from './account/FinancialAccountSelector';
import { EmptyFinancialAccountsState } from './account/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { CreditDebitType, FinancialAccount, FinancialAccountType, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryFilterSelection, MonthSummary, MonthSummaryData } from './summary/MonthSummary';
import { useAccountDisplayMode } from './account/useAccountDisplayMode';
import { BillSummaryHeader } from './bill/BillSummaryHeader';
import { useOnTransactionsImported } from './events';
import { FinanceTransactionsHeaderBar } from './FinanceTransactionsHeaderBar';
import { FinanceTransactionsActiveFilters } from './FinanceTransactionsActiveFilters';
import { FinanceTransactionsDialogs } from './FinanceTransactionsDialogs';
import { FinanceTransactionsContentPanel } from './FinanceTransactionsContentPanel';

const TRANSACTION_ACCOUNT_TYPES: FinancialAccountType[] = [
  FinancialAccountType.Bank,
  FinancialAccountType.Credit,
  FinancialAccountType.Payment,
];

const ACCOUNT_SELECTOR_TYPES: FinancialAccountType[] = [
  FinancialAccountType.Bank,
  FinancialAccountType.Credit,
  FinancialAccountType.Payment,
];

export default function FinanceTransactionsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isImportDrawerOpen, setIsImportDrawerOpen] = useState(false);
  const [isImportBillDrawerOpen, setIsImportBillDrawerOpen] = useState(false);
  const [isCreateDrawerFromImport, setIsCreateDrawerFromImport] = useState(false);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [editingAccount, setEditingAccount] = useState<FinancialAccount | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);
  const [isPayBillDialogOpen, setIsPayBillDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const theme = useTheme();
  const mountedRef = useRef(true);
  const initialTypeResetTimeoutRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      if (initialTypeResetTimeoutRef.current) {
        window.clearTimeout(initialTypeResetTimeoutRef.current);
        initialTypeResetTimeoutRef.current = null;
      }
    };
  }, []);

  const { data, loading, error: financialAccountsError, refetch: refetchFinancialAccounts } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    errorPolicy: 'all',
  });

  const transactionAccounts = useMemo(
    () => (data?.financialAccounts ?? []).filter((account) => TRANSACTION_ACCOUNT_TYPES.includes(account.type)),
    [data?.financialAccounts],
  );

  const bankAccounts = useMemo(
    () => (data?.financialAccounts ?? []).filter((account) => account.type === FinancialAccountType.Bank),
    [data?.financialAccounts],
  );

  const effectiveSelectedAccountId = useMemo(() => {
    return selectedAccountId && transactionAccounts.some((account) => account.id === selectedAccountId) ? selectedAccountId : null;
  }, [selectedAccountId, transactionAccounts]);

  const selectedAccount = useMemo(() => {
    return transactionAccounts.find((a) => a.id === effectiveSelectedAccountId) || null;
  }, [effectiveSelectedAccountId, transactionAccounts]);

  const displayMode = useAccountDisplayMode(selectedAccount);

  const calendarMonth = displayMode.kind === 'calendar' ? displayMode.month : null;
  const calendarYear = displayMode.kind === 'calendar' ? displayMode.year : null;

  const summaryCategoryFilterScopeKey = useMemo(() => {
    return `${displayMode.kind}:${effectiveSelectedAccountId ?? ''}:${calendarYear ?? ''}:${calendarMonth ?? ''}`;
  }, [calendarMonth, calendarYear, displayMode.kind, effectiveSelectedAccountId]);

  const [summaryCategoryFilter, setSummaryCategoryFilter] = useState<{ scopeKey: string; selection: CategoryFilterSelection | null }>(() => ({
    scopeKey: summaryCategoryFilterScopeKey,
    selection: null,
  }));

  const summaryCategoryFilterSelection =
    summaryCategoryFilter.scopeKey === summaryCategoryFilterScopeKey ? summaryCategoryFilter.selection : null;
  const summaryCategoryFilterId = summaryCategoryFilterSelection?.categoryId ?? null;
  const summaryUncategorizedFilterActive = summaryCategoryFilterSelection?.uncategorized === true;

  const setSummaryCategoryFilterSelection = useCallback((selection: CategoryFilterSelection | null) => {
    setSummaryCategoryFilter((prev) => {
      const scopeKey = summaryCategoryFilterScopeKey;
      const current = prev.scopeKey === scopeKey ? prev.selection : null;
      const isSameSelection =
        current?.categoryId === selection?.categoryId &&
        current?.uncategorized === selection?.uncategorized;

      return {
        scopeKey,
        selection: selection && !isSameSelection ? selection : null,
      };
    });
  }, [summaryCategoryFilterScopeKey]);

  const [summaryTagFilter, setSummaryTagFilter] = useState<{ scopeKey: string; tag: string | null }>(() => ({
    scopeKey: summaryCategoryFilterScopeKey,
    tag: null,
  }));

  const summaryTagFilterValue =
    summaryTagFilter.scopeKey === summaryCategoryFilterScopeKey ? summaryTagFilter.tag : null;

  const setSummaryTagFilterValue = useCallback((tag: string | null) => {
    setSummaryTagFilter((prev) => {
      const scopeKey = summaryCategoryFilterScopeKey;
      const current = prev.scopeKey === scopeKey ? prev.tag : null;

      return {
        scopeKey,
        tag: tag && current === tag ? null : tag,
      };
    });
  }, [summaryCategoryFilterScopeKey]);

  const [summaryCreditDebitFilter, setSummaryCreditDebitFilter] = useState<{
    scopeKey: string;
    creditDebitType: CreditDebitType | null;
  }>(() => ({
    scopeKey: summaryCategoryFilterScopeKey,
    creditDebitType: null,
  }));

  const summaryCreditDebitTypeFilterEffective =
    summaryCreditDebitFilter.scopeKey === summaryCategoryFilterScopeKey
      ? summaryCreditDebitFilter.creditDebitType
      : null;

  const handleCreditDebitTypeFilter = useCallback((type: CreditDebitType) => {
    setSummaryCreditDebitFilter((prev) => {
      const scopeKey = summaryCategoryFilterScopeKey;
      const current = prev.scopeKey === scopeKey ? prev.creditDebitType : null;
      return {
        scopeKey,
        creditDebitType: current === type ? null : type,
      };
    });
  }, [summaryCategoryFilterScopeKey]);

  const clearCreditDebitTypeFilter = useCallback(() => {
    setSummaryCreditDebitFilter({ scopeKey: summaryCategoryFilterScopeKey, creditDebitType: null });
  }, [summaryCategoryFilterScopeKey]);

  const selectedBillId = displayMode.kind === 'bill' ? displayMode.selectedBill?.id ?? null : null;
  const canQueryTransactions = displayMode.kind === 'calendar' || Boolean(selectedBillId);
  const normalizedSearchQuery = searchQuery.trim();

  const { 
    data: transactionsData, 
    loading: transactionsLoading, 
    error: transactionsError,
    fetchMore, 
    refetch: refetchTransactions 
  } = useQuery<TransactionsQuery>(TRANSACTIONS, {
    variables: {
      financialAccountId: displayMode.kind === 'calendar' ? effectiveSelectedAccountId : undefined,
      billId: displayMode.kind === 'bill' ? selectedBillId : undefined,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
      search: normalizedSearchQuery.length > 0 ? normalizedSearchQuery : undefined,
      tags: displayMode.kind === 'calendar' && summaryTagFilterValue ? [summaryTagFilterValue] : undefined,
      categoryId:
        displayMode.kind === 'calendar' && summaryCategoryFilterId && !summaryUncategorizedFilterActive
          ? summaryCategoryFilterId
          : undefined,
      uncategorized:
        displayMode.kind === 'calendar' && summaryUncategorizedFilterActive
          ? true
          : undefined,
      creditDebitType:
        displayMode.kind === 'calendar' && summaryCreditDebitTypeFilterEffective
          ? summaryCreditDebitTypeFilterEffective
          : undefined,
      first: 20,
    },
    skip: !canQueryTransactions,
    notifyOnNetworkStatusChange: true,
    errorPolicy: 'all',
  });

  const {
    data: monthSummaryData,
    loading: monthSummaryLoading,
    error: monthSummaryError,
    refetch: refetchMonthSummary,
  } = useQuery<{ monthSummary: MonthSummaryData }>(MONTH_SUMMARY, {
    variables: {
      financialAccountId: effectiveSelectedAccountId,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
    },
    fetchPolicy: 'cache-and-network',
    skip: displayMode.kind !== 'calendar',
  });

  const { data: importsData, error: importsError, refetch: refetchImports } = useQuery<{ transactionImports: Array<{ id: string; status: string; duplicateReviewCount: number }> }>(TRANSACTION_IMPORTS, {
    variables: { financialAccountId: effectiveSelectedAccountId },
    skip: !effectiveSelectedAccountId,
    errorPolicy: 'all',
  });
  const reviewRequiredJob = importsData?.transactionImports?.find((j) => j.status === 'REVIEW_REQUIRED');

  const summaryExpensesByCategory = monthSummaryData?.monthSummary?.expensesByCategory ?? null;
  const summaryIncomeByCategory = monthSummaryData?.monthSummary?.incomeByCategory ?? null;
  const summaryTags = monthSummaryData?.monthSummary?.tagsSummary ?? null;

  const summaryCategoryFilterMeta = useMemo(() => {
    if (!summaryCategoryFilterSelection || (!summaryExpensesByCategory && !summaryIncomeByCategory)) return null;
    const lists = [
      ...(summaryExpensesByCategory ?? []),
      ...(summaryIncomeByCategory ?? []),
    ];
    const hit = lists.find((c) =>
      summaryCategoryFilterSelection.uncategorized
        ? c.uncategorized
        : c.category?.id === summaryCategoryFilterSelection.categoryId,
    );
    if (!hit) return null;
    return {
      name: hit.uncategorized ? t('financeTransactions.monthSummary.uncategorized') : hit.category?.name,
      icon: hit.uncategorized ? null : hit.category?.icon,
      color: hit.uncategorized ? theme.palette.grey[600] : hit.category?.color,
    };
  }, [summaryCategoryFilterSelection, summaryExpensesByCategory, summaryIncomeByCategory, t, theme]);

  const summaryCategoryChipAccent =
    summaryUncategorizedFilterActive
      ? theme.palette.grey[600]
      : summaryCategoryFilterMeta?.color ?? theme.palette.primary.main;

  const summaryTagFilterMeta = useMemo(() => {
    if (!summaryTagFilterValue || !summaryTags) return null;
    return summaryTags.find((item) => item.tag === summaryTagFilterValue) ?? null;
  }, [summaryTagFilterValue, summaryTags]);

  const summaryTagChipAccent = theme.palette.info.main;
  const summaryTypeChipAccent =
    summaryCreditDebitTypeFilterEffective === CreditDebitType.Credit
      ? theme.palette.success.main
      : summaryCreditDebitTypeFilterEffective === CreditDebitType.Debit
        ? theme.palette.error.main
        : theme.palette.primary.main;

  const summaryTableFiltersOpen =
    displayMode.kind === 'calendar' &&
    Boolean(summaryCategoryFilterSelection || summaryTagFilterValue || summaryCreditDebitTypeFilterEffective);

  const refetchBills = displayMode.kind === 'bill' ? displayMode.refetchBills : null;

  const refetchSummary = useCallback(() => {
    if (displayMode.kind === 'calendar') {
      refetchMonthSummary();
      return;
    }
    refetchBills?.();
  }, [displayMode.kind, refetchBills, refetchMonthSummary]);

  const refreshAfterImport = useCallback(() => {
    if (canQueryTransactions) {
      refetchTransactions();
    }
    if (effectiveSelectedAccountId) {
      refetchImports();
    }
    refetchSummary();
  }, [canQueryTransactions, effectiveSelectedAccountId, refetchImports, refetchSummary, refetchTransactions]);

  const handleTransactionsImported = useCallback(() => {
    refreshAfterImport();
  }, [refreshAfterImport]);

  useOnTransactionsImported(handleTransactionsImported);

  const handleImportCompleted = useCallback(() => {
    refreshAfterImport();
  }, [refreshAfterImport]);

  const handleAccountCreated = () => {
    refetchFinancialAccounts();
    refetchSummary();
  };

  const transactions = useMemo(() => {
    return (transactionsData?.transactions?.edges?.map((edge) => edge.node) || []) as Transaction[];
  }, [transactionsData?.transactions?.edges]);

  const hasMore = transactionsData?.transactions?.pageInfo?.hasNextPage || false;
  const endCursor = transactionsData?.transactions?.pageInfo?.endCursor ?? null;
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const isFetchingMoreRef = useRef(false);

  const handleLoadMore = useCallback(() => {
    if (!hasMore || !endCursor) return;
    if (transactionsLoading || isFetchingMoreRef.current) return;

    isFetchingMoreRef.current = true;
    if (mountedRef.current) {
      setIsFetchingMore(true);
    }

    fetchMore({
      variables: {
        after: endCursor,
      },
      updateQuery: (previousQueryResult, { fetchMoreResult }) => {
        if (!fetchMoreResult?.transactions) return previousQueryResult;

        const previousEdges = previousQueryResult.transactions?.edges ?? [];
        const incomingEdges = fetchMoreResult.transactions.edges ?? [];

        const seenIds = new Set<string>();
        const mergedEdges = [...previousEdges, ...incomingEdges].filter((edge) => {
          const id = edge?.node?.id;
          if (!id) return false;
          if (seenIds.has(id)) return false;
          seenIds.add(id);
          return true;
        });

        return {
          ...previousQueryResult,
          transactions: {
            ...previousQueryResult.transactions,
            ...fetchMoreResult.transactions,
            edges: mergedEdges,
            pageInfo: fetchMoreResult.transactions.pageInfo,
          },
        };
      },
    }).finally(() => {
      isFetchingMoreRef.current = false;
      if (mountedRef.current) {
        setIsFetchingMore(false);
      }
    });
  }, [endCursor, fetchMore, hasMore, transactionsLoading]);

  const handleDateChange = (month: number, year: number) => {
    if (displayMode.kind === 'calendar') {
      displayMode.setMonth(month);
      displayMode.setYear(year);
    }
  };

  const handleOpenDrawer = (type?: 'BANK' | 'CARD') => {
    setEditingAccount(null);
    if (type) {
      setInitialType(type);
    } else {
      setInitialType(null);
    }
    setIsDrawerOpen(true);
  };

  const handleEditAccount = (account: FinancialAccount) => {
    setInitialType(null);
    setIsCreateDrawerFromImport(false);
    setEditingAccount(account);
    setIsDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setIsDrawerOpen(false);
    setIsCreateDrawerFromImport(false);
    setEditingAccount(null);
    if (initialTypeResetTimeoutRef.current) {
      window.clearTimeout(initialTypeResetTimeoutRef.current);
    }
    initialTypeResetTimeoutRef.current = window.setTimeout(() => {
      if (mountedRef.current) {
        setInitialType(null);
      }
      initialTypeResetTimeoutRef.current = null;
    }, 300);
  };

  const renderSidebarContent = () => (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ p: 3, pb: 2 }}>
        <FinancialAccountSelector
          onNewAccount={() => handleOpenDrawer()}
          onEditAccount={handleEditAccount}
          selectedAccountId={effectiveSelectedAccountId}
          onSelectAccount={setSelectedAccountId}
          accountTypes={ACCOUNT_SELECTOR_TYPES}
        />
        <Divider sx={{ my: 3 }} />
        {displayMode.kind === 'calendar' ? (
          <MonthSummary
            summary={monthSummaryData?.monthSummary}
            loading={monthSummaryLoading}
            error={monthSummaryError}
            onRetry={() => refetchMonthSummary()}
            isConsolidated={!effectiveSelectedAccountId}
            expensesByCategory={monthSummaryData?.monthSummary?.expensesByCategory ?? []}
            incomeByCategory={monthSummaryData?.monthSummary?.incomeByCategory ?? []}
            tagsSummary={monthSummaryData?.monthSummary?.tagsSummary ?? []}
            onCategoryClick={(selection) => setSummaryCategoryFilterSelection(selection)}
            activeCategoryFilter={summaryCategoryFilterSelection}
            onTagClick={(tag) => setSummaryTagFilterValue(tag)}
            activeTagFilter={summaryTagFilterValue}
            onCreditDebitTypeClick={handleCreditDebitTypeFilter}
            activeCreditDebitTypeFilter={summaryCreditDebitTypeFilterEffective}
          />
        ) : (
          <BillSummaryHeader
            bill={
              displayMode.selectedBill
                ? {
                    totalAmount: displayMode.selectedBill.totalAmount,
                    closingDate: displayMode.selectedBill.closingDate,
                    dueDate: displayMode.selectedBill.dueDate,
                    status: displayMode.selectedBill.status,
                    creditLimit: selectedAccount?.cardData?.creditLimit,
                  }
                : undefined
            }
            loading={displayMode.loading}
            onPayClick={() => setIsPayBillDialogOpen(true)}
          />
        )}
        <Divider sx={{ my: 3 }} />
      </Box>
    </Box>
  );

  const handleSelectBill = (id: string) => {
    if (displayMode.kind !== 'bill') return;
    const bill = displayMode.bills.find((item) => item.id === id);
    if (bill) {
      displayMode.setSelectedBill(bill);
    }
  };

  const handleOpenReviewJob = (jobId: string) => {
    setReviewJobId(jobId);
    setIsImportDrawerOpen(true);
  };

  const handleOpenImportTransactions = () => {
    setReviewJobId(null);
    setIsImportDrawerOpen(true);
  };

  const handleBackFromPrefilledType = isCreateDrawerFromImport
    ? () => {
        setIsDrawerOpen(false);
        setIsCreateDrawerFromImport(false);
        setTimeout(() => setInitialType(null), 300);
        setIsImportDrawerOpen(true);
      }
    : undefined;

  const handleCloseImportDrawer = () => {
    setIsImportDrawerOpen(false);
    setReviewJobId(null);
  };

  const handleRequestCreateAccountFromImport = (type?: 'BANK' | 'CARD') => {
    setIsImportDrawerOpen(false);
    setReviewJobId(null);
    setIsCreateDrawerFromImport(true);
    handleOpenDrawer(type);
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (financialAccountsError) {
    return (
      <Page>
        <Container maxWidth="md" sx={{ py: 6 }}>
          <QueryErrorState error={financialAccountsError} onRetry={() => refetchFinancialAccounts()} />
        </Container>
      </Page>
    );
  }

  const hasAccounts = transactionAccounts.length > 0;

  if (!hasAccounts) {
    return (
      <>
        <EmptyFinancialAccountsState onSelectType={handleOpenDrawer} />
        <CreateFinancialAccountDrawer 
          open={isDrawerOpen} 
          onClose={handleCloseDrawer}
          initialType={initialType}
          accountToEdit={editingAccount}
          onCreated={handleAccountCreated}
        />
      </>
    );
  }

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      sidebar={renderSidebarContent()}
    >
      <Page>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          <FinanceTransactionsHeaderBar
            importsError={importsError}
            effectiveSelectedAccountId={effectiveSelectedAccountId}
            onRetryImports={refetchImports}
            reviewRequiredJob={reviewRequiredJob ?? null}
            onOpenReviewJob={handleOpenReviewJob}
            isCalendarMode={displayMode.kind === 'calendar'}
            selectedMonth={displayMode.kind === 'calendar' ? displayMode.month : calendarMonth ?? 1}
            selectedYear={displayMode.kind === 'calendar' ? displayMode.year : calendarYear ?? new Date().getFullYear()}
            onDateChange={handleDateChange}
            bills={displayMode.kind === 'bill' ? displayMode.bills : []}
            selectedBillId={displayMode.kind === 'bill' ? displayMode.selectedBill?.id ?? null : null}
            onSelectBill={handleSelectBill}
            searchQuery={searchQuery}
            onSearchQueryChange={setSearchQuery}
            onOpenImportTransactions={handleOpenImportTransactions}
            onOpenImportBill={() => setIsImportBillDrawerOpen(true)}
            onCreateTransaction={() => setIsCreatingTransaction(true)}
            isCreatingTransaction={isCreatingTransaction}
          />
          {displayMode.kind === 'calendar' && (
            <FinanceTransactionsActiveFilters
              open={summaryTableFiltersOpen}
              summaryCreditDebitTypeFilter={summaryCreditDebitTypeFilterEffective}
              onClearCreditDebitTypeFilter={clearCreditDebitTypeFilter}
              summaryTypeChipAccent={summaryTypeChipAccent}
              summaryCategoryFilterSelection={summaryCategoryFilterSelection}
              summaryCategoryFilterMeta={summaryCategoryFilterMeta}
              summaryCategoryChipAccent={summaryCategoryChipAccent}
              summaryUncategorizedFilterActive={summaryUncategorizedFilterActive}
              summaryCategoryFilterId={summaryCategoryFilterId}
              onClearCategoryFilter={() => setSummaryCategoryFilterSelection(null)}
              summaryTagFilterValue={summaryTagFilterValue}
              summaryTagFilterMeta={summaryTagFilterMeta}
              summaryTagChipAccent={summaryTagChipAccent}
              onClearTagFilter={() => setSummaryTagFilterValue(null)}
            />
          )}
          <FinanceTransactionsContentPanel
            transactionsError={transactionsError}
            transactionsLoading={transactionsLoading}
            transactions={transactions}
            onRetryTransactions={refetchTransactions}
            financialAccounts={transactionAccounts}
            tableLoading={transactionsLoading || isFetchingMore}
            hasMore={hasMore}
            onLoadMore={handleLoadMore}
            isCreatingTransaction={isCreatingTransaction}
            onCancelCreate={() => setIsCreatingTransaction(false)}
            onSuccessCreate={() => {
              refetchTransactions();
              refetchSummary();
            }}
            selectedAccountId={effectiveSelectedAccountId}
            selectedMonth={displayMode.kind === 'calendar' ? displayMode.month : undefined}
            selectedYear={displayMode.kind === 'calendar' ? displayMode.year : undefined}
          />
        </Container>
      </Page>
      <FinanceTransactionsDialogs
        isDrawerOpen={isDrawerOpen}
        onCloseDrawer={handleCloseDrawer}
        initialType={initialType}
        editingAccount={editingAccount}
        onBackFromPrefilledType={handleBackFromPrefilledType}
        onAccountCreated={handleAccountCreated}
        isImportDrawerOpen={isImportDrawerOpen}
        onCloseImportDrawer={handleCloseImportDrawer}
        selectedAccountId={effectiveSelectedAccountId}
        selectedAccountType={selectedAccount?.type ?? null}
        onImportCompleted={handleImportCompleted}
        onRequestCreateAccount={handleRequestCreateAccountFromImport}
        reviewJobId={reviewJobId ?? undefined}
        isImportBillDrawerOpen={isImportBillDrawerOpen}
        onCloseImportBillDrawer={() => setIsImportBillDrawerOpen(false)}
        isPayBillDialogOpen={isPayBillDialogOpen}
        selectedBill={displayMode.kind === 'bill' ? displayMode.selectedBill : null}
        bankAccounts={bankAccounts}
        onClosePayBillDialog={() => setIsPayBillDialogOpen(false)}
        onPayBillSuccess={() => {
          if (displayMode.kind === 'bill') displayMode.refetchBills();
          refetchTransactions();
        }}
      />
    </SidebarLayout>
  );
}
