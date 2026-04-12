'use client';

import { useCallback, useMemo, useRef, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import {
  Container,
  Box,
  CircularProgress,
  useTheme,
} from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { CreateFinancialAccountDrawer } from './account/CreateFinancialAccountDrawer';
import { EmptyFinancialAccountsState } from './account/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { CreditDebitType, FinancialAccount, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryFilterSelection, MonthSummaryData } from './summary/MonthSummary';
import { useAccountDisplayMode } from './account/useAccountDisplayMode';
import { useOnTransactionsImported } from './events';
import { FinanceTransactionsHeaderBar } from './FinanceTransactionsHeaderBar';
import { FinanceTransactionsActiveFilters } from './FinanceTransactionsActiveFilters';
import { FinanceTransactionsDialogs } from './FinanceTransactionsDialogs';
import { FinanceTransactionsContentPanel } from './FinanceTransactionsContentPanel';
import { FinanceTransactionsSidebar } from './FinanceTransactionsSidebar';
import { useScopedToggleFilter } from './hooks/useScopedToggleFilter';
import { usePaginatedTransactions } from './hooks/usePaginatedTransactions';

const TRANSACTION_ACCOUNT_TYPES: FinancialAccountType[] = [
  FinancialAccountType.Bank,
  FinancialAccountType.Credit,
  FinancialAccountType.Payment,
];

const categoryFilterEquals = (a: CategoryFilterSelection, b: CategoryFilterSelection) =>
  a.categoryId === b.categoryId && a.uncategorized === b.uncategorized;

export default function FinanceTransactionsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));

  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isImportDrawerOpen, setIsImportDrawerOpen] = useState(false);
  const [isImportBillDrawerOpen, setIsImportBillDrawerOpen] = useState(false);
  const [isNfceImportDrawerOpen, setIsNfceImportDrawerOpen] = useState(false);
  const [nfceDocumentTransactionId, setNfceDocumentTransactionId] = useState<string | null>(null);
  const [isCreateDrawerFromImport, setIsCreateDrawerFromImport] = useState(false);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [editingAccount, setEditingAccount] = useState<FinancialAccount | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);
  const [isPayBillDialogOpen, setIsPayBillDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const theme = useTheme();
  const initialTypeResetTimeoutRef = useRef<number | null>(null);

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
    if (selectedAccountId && transactionAccounts.some((account) => account.id === selectedAccountId)) {
      return selectedAccountId;
    }
    return transactionAccounts.length === 1 ? transactionAccounts[0]?.id ?? null : null;
  }, [selectedAccountId, transactionAccounts]);

  const selectedAccount = useMemo(
    () => transactionAccounts.find((a) => a.id === effectiveSelectedAccountId) || null,
    [effectiveSelectedAccountId, transactionAccounts],
  );

  const displayMode = useAccountDisplayMode(selectedAccount);

  const calendarMonth = displayMode.kind === 'calendar' ? displayMode.month : null;
  const calendarYear = displayMode.kind === 'calendar' ? displayMode.year : null;

  const filterScopeKey = useMemo(
    () => `${displayMode.kind}:${effectiveSelectedAccountId ?? ''}:${calendarYear ?? ''}:${calendarMonth ?? ''}`,
    [calendarMonth, calendarYear, displayMode.kind, effectiveSelectedAccountId],
  );

  const categoryFilter = useScopedToggleFilter<CategoryFilterSelection>(filterScopeKey, categoryFilterEquals);
  const tagFilter = useScopedToggleFilter<string>(filterScopeKey);
  const creditDebitFilter = useScopedToggleFilter<CreditDebitType>(filterScopeKey);

  const selectedBillId = displayMode.kind === 'bill' ? displayMode.selectedBill?.id ?? null : null;
  const canQueryTransactions = displayMode.kind === 'calendar' || Boolean(selectedBillId);
  const normalizedSearchQuery = searchQuery.trim();

  const {
    data: transactionsData,
    loading: transactionsLoading,
    error: transactionsError,
    fetchMore,
    refetch: refetchTransactions,
  } = useQuery<TransactionsQuery>(TRANSACTIONS, {
    variables: {
      financialAccountId: displayMode.kind === 'calendar' ? effectiveSelectedAccountId : undefined,
      billId: displayMode.kind === 'bill' ? selectedBillId : undefined,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
      search: normalizedSearchQuery.length > 0 ? normalizedSearchQuery : undefined,
      tags: displayMode.kind === 'calendar' && tagFilter.value ? [tagFilter.value] : undefined,
      categoryId:
        displayMode.kind === 'calendar' && categoryFilter.value?.categoryId && !categoryFilter.value?.uncategorized
          ? categoryFilter.value.categoryId
          : undefined,
      uncategorized:
        displayMode.kind === 'calendar' && categoryFilter.value?.uncategorized ? true : undefined,
      creditDebitType:
        displayMode.kind === 'calendar' && creditDebitFilter.value ? creditDebitFilter.value : undefined,
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

  const pagination = usePaginatedTransactions(transactionsData, transactionsLoading, fetchMore);

  // --- Active filter chip accents ---
  const summaryExpensesByCategory = monthSummaryData?.monthSummary?.expensesByCategory ?? null;
  const summaryIncomeByCategory = monthSummaryData?.monthSummary?.incomeByCategory ?? null;

  const categoryFilterMeta = useMemo(() => {
    if (!categoryFilter.value || (!summaryExpensesByCategory && !summaryIncomeByCategory)) return null;
    const lists = [...(summaryExpensesByCategory ?? []), ...(summaryIncomeByCategory ?? [])];
    const hit = lists.find((c) =>
      categoryFilter.value!.uncategorized ? c.uncategorized : c.category?.id === categoryFilter.value!.categoryId,
    );
    if (!hit) return null;
    return {
      name: hit.uncategorized ? t('financeTransactions.monthSummary.uncategorized') : hit.category?.name,
      icon: hit.uncategorized ? null : hit.category?.icon,
      color: hit.uncategorized ? theme.palette.grey[600] : hit.category?.color,
    };
  }, [categoryFilter.value, summaryExpensesByCategory, summaryIncomeByCategory, t, theme]);

  const categoryChipAccent =
    categoryFilter.value?.uncategorized
      ? theme.palette.grey[600]
      : categoryFilterMeta?.color ?? theme.palette.primary.main;

  const tagFilterMeta = useMemo(() => {
    const summaryTags = monthSummaryData?.monthSummary?.tagsSummary ?? null;
    if (!tagFilter.value || !summaryTags) return null;
    return summaryTags.find((item) => item.tag === tagFilter.value) ?? null;
  }, [tagFilter.value, monthSummaryData?.monthSummary?.tagsSummary]);

  const tagChipAccent = theme.palette.info.main;
  const creditDebitChipAccent =
    creditDebitFilter.value === CreditDebitType.Credit
      ? theme.palette.success.main
      : creditDebitFilter.value === CreditDebitType.Debit
        ? theme.palette.error.main
        : theme.palette.primary.main;

  const activeFiltersOpen =
    displayMode.kind === 'calendar' &&
    Boolean(categoryFilter.value || tagFilter.value || creditDebitFilter.value);

  // --- Refetch helpers ---
  const refetchBills = displayMode.kind === 'bill' ? displayMode.refetchBills : null;

  const refetchSummary = useCallback(async () => {
    if (displayMode.kind === 'calendar') {
      await refetchMonthSummary();
      return;
    }
    await Promise.resolve(refetchBills?.());
  }, [displayMode.kind, refetchBills, refetchMonthSummary]);

  const refreshAfterImport = useCallback(() => {
    void (async () => {
      if (canQueryTransactions) await refetchTransactions();
      if (effectiveSelectedAccountId) await refetchImports();
      await refetchSummary();
    })();
  }, [canQueryTransactions, effectiveSelectedAccountId, refetchImports, refetchSummary, refetchTransactions]);

  useOnTransactionsImported(refreshAfterImport);

  // --- Account drawer ---
  const handleOpenDrawer = useCallback((type?: 'BANK' | 'CARD') => {
    setEditingAccount(null);
    setInitialType(type ?? null);
    setIsDrawerOpen(true);
  }, []);

  const handleEditAccount = useCallback((account: FinancialAccount) => {
    setInitialType(null);
    setIsCreateDrawerFromImport(false);
    setEditingAccount(account);
    setIsDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setIsCreateDrawerFromImport(false);
    setEditingAccount(null);
    if (initialTypeResetTimeoutRef.current) {
      window.clearTimeout(initialTypeResetTimeoutRef.current);
    }
    initialTypeResetTimeoutRef.current = window.setTimeout(() => {
      setInitialType(null);
      initialTypeResetTimeoutRef.current = null;
    }, 300);
  }, []);

  const handleAccountCreated = useCallback(() => {
    void (async () => {
      await refetchFinancialAccounts();
      await refetchSummary();
    })();
  }, [refetchFinancialAccounts, refetchSummary]);

  // --- Import drawers ---
  const handleOpenReviewJob = useCallback((jobId: string) => {
    setReviewJobId(jobId);
    setIsImportDrawerOpen(true);
  }, []);

  const handleOpenImportTransactions = useCallback(() => {
    setReviewJobId(null);
    setIsImportDrawerOpen(true);
  }, []);

  const handleCloseImportDrawer = useCallback(() => {
    setIsImportDrawerOpen(false);
    setReviewJobId(null);
  }, []);

  const handleRequestCreateAccountFromImport = useCallback((type?: 'BANK' | 'CARD') => {
    setIsImportDrawerOpen(false);
    setReviewJobId(null);
    setIsCreateDrawerFromImport(true);
    handleOpenDrawer(type);
  }, [handleOpenDrawer]);

  const handleBackFromPrefilledType = useCallback(() => {
    setIsDrawerOpen(false);
    setIsCreateDrawerFromImport(false);
    setTimeout(() => setInitialType(null), 300);
    setIsImportDrawerOpen(true);
  }, []);

  // --- Navigation & content ---
  const handleSelectBill = useCallback((id: string) => {
    if (displayMode.kind !== 'bill') return;
    const bill = displayMode.bills.find((item) => item.id === id);
    if (bill) displayMode.setSelectedBill(bill);
  }, [displayMode]);

  const handleDateChange = useCallback((month: number, year: number) => {
    if (displayMode.kind === 'calendar') {
      displayMode.setMonth(month);
      displayMode.setYear(year);
    }
  }, [displayMode]);

  const handleOpenNfceImport = useCallback(() => setIsNfceImportDrawerOpen(true), []);
  const handleOpenImportBill = useCallback(() => setIsImportBillDrawerOpen(true), []);
  const handleCreateTransaction = useCallback(() => setIsCreatingTransaction(true), []);
  const handleCloseImportBillDrawer = useCallback(() => setIsImportBillDrawerOpen(false), []);
  const handleCloseNfceImportDrawer = useCallback(() => setIsNfceImportDrawerOpen(false), []);
  const handleCloseNfceDocumentDrawer = useCallback(() => setNfceDocumentTransactionId(null), []);
  const handleOpenPayBillDialog = useCallback(() => setIsPayBillDialogOpen(true), []);
  const handleClosePayBillDialog = useCallback(() => setIsPayBillDialogOpen(false), []);
  const handleCancelCreate = useCallback(() => setIsCreatingTransaction(false), []);

  const handleSuccessCreate = useCallback(async () => {
    await Promise.all([refetchTransactions(), refetchSummary()]);
  }, [refetchTransactions, refetchSummary]);

  const handlePayBillSuccess = useCallback(() => {
    if (displayMode.kind === 'bill') displayMode.refetchBills();
    refetchTransactions();
  }, [displayMode, refetchTransactions]);

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
          <QueryErrorState error={financialAccountsError} onRetry={refetchFinancialAccounts} />
        </Container>
      </Page>
    );
  }

  if (transactionAccounts.length === 0) {
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

  const headerNavMonth = displayMode.kind === 'calendar' ? displayMode.month : calendarMonth ?? 1;
  const headerNavYear = displayMode.kind === 'calendar' ? displayMode.year : calendarYear ?? new Date().getFullYear();
  const billSummaryData =
    displayMode.kind === 'bill' && displayMode.selectedBill
      ? {
          totalAmount: displayMode.selectedBill.totalAmount,
          closingDate: displayMode.selectedBill.closingDate,
          dueDate: displayMode.selectedBill.dueDate,
          status: displayMode.selectedBill.status,
          creditLimit: selectedAccount?.cardData?.creditLimit,
        }
      : undefined;

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      sidebar={
        <FinanceTransactionsSidebar
          accountSelector={{
            selectedAccountId: effectiveSelectedAccountId,
            onSelect: setSelectedAccountId,
            onNewAccount: handleOpenDrawer,
            onEditAccount: handleEditAccount,
          }}
          displayModeKind={displayMode.kind}
          calendarSummary={{
            data: monthSummaryData?.monthSummary,
            loading: monthSummaryLoading,
            error: monthSummaryError,
            onRetry: refetchMonthSummary,
            isConsolidated: !effectiveSelectedAccountId,
            categoryFilter: { value: categoryFilter.value, onToggle: categoryFilter.toggle },
            tagFilter: { value: tagFilter.value, onToggle: tagFilter.toggle },
            creditDebitFilter: { value: creditDebitFilter.value, onToggle: creditDebitFilter.toggle },
          }}
          billSummary={{
            bill: billSummaryData,
            loading: displayMode.kind === 'bill' ? displayMode.loading : false,
            onPayClick: handleOpenPayBillDialog,
          }}
        />
      }
    >
      <Page>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          <FinanceTransactionsHeaderBar
            importAlerts={{
              error: importsError,
              accountId: effectiveSelectedAccountId,
              onRetry: refetchImports,
              reviewRequiredJob: reviewRequiredJob ?? null,
              onOpenReviewJob: handleOpenReviewJob,
            }}
            navigation={{
              isCalendarMode: displayMode.kind === 'calendar',
              selectedMonth: headerNavMonth,
              selectedYear: headerNavYear,
              onDateChange: handleDateChange,
              bills: displayMode.kind === 'bill' ? displayMode.bills : [],
              selectedBillId: displayMode.kind === 'bill' ? (displayMode.selectedBill?.id ?? null) : null,
              onSelectBill: handleSelectBill,
            }}
            searchQuery={searchQuery}
            onSearchQueryChange={setSearchQuery}
            actions={{
              onOpenNfceImport: handleOpenNfceImport,
              onOpenImportTransactions: handleOpenImportTransactions,
              onOpenImportBill: handleOpenImportBill,
              onCreateTransaction: handleCreateTransaction,
              isCreating: isCreatingTransaction,
            }}
          />
          {displayMode.kind === 'calendar' && (
            <FinanceTransactionsActiveFilters
              open={activeFiltersOpen}
              summaryCreditDebitTypeFilter={creditDebitFilter.value}
              onClearCreditDebitTypeFilter={creditDebitFilter.clear}
              summaryTypeChipAccent={creditDebitChipAccent}
              summaryCategoryFilterSelection={categoryFilter.value}
              summaryCategoryFilterMeta={categoryFilterMeta}
              summaryCategoryChipAccent={categoryChipAccent}
              summaryUncategorizedFilterActive={categoryFilter.value?.uncategorized === true}
              summaryCategoryFilterId={categoryFilter.value?.categoryId ?? null}
              onClearCategoryFilter={categoryFilter.clear}
              summaryTagFilterValue={tagFilter.value}
              summaryTagFilterMeta={tagFilterMeta}
              summaryTagChipAccent={tagChipAccent}
              onClearTagFilter={tagFilter.clear}
            />
          )}
          <FinanceTransactionsContentPanel
            transactionsError={transactionsError}
            transactionsLoading={transactionsLoading}
            transactions={pagination.transactions}
            onRetryTransactions={refetchTransactions}
            financialAccounts={transactionAccounts}
            tableLoading={pagination.tableLoading}
            hasMore={pagination.hasMore}
            onLoadMore={pagination.onLoadMore}
            isCreatingTransaction={isCreatingTransaction}
            onCancelCreate={handleCancelCreate}
            onSuccessCreate={handleSuccessCreate}
            onOpenNfceDocument={setNfceDocumentTransactionId}
            selectedAccountId={effectiveSelectedAccountId}
            selectedMonth={displayMode.kind === 'calendar' ? displayMode.month : undefined}
            selectedYear={displayMode.kind === 'calendar' ? displayMode.year : undefined}
          />
        </Container>
      </Page>
      <FinanceTransactionsDialogs
        accountDrawer={{
          isOpen: isDrawerOpen,
          onClose: handleCloseDrawer,
          initialType,
          editingAccount,
          onBack: isCreateDrawerFromImport ? handleBackFromPrefilledType : undefined,
          onCreated: handleAccountCreated,
        }}
        importDrawer={{
          isOpen: isImportDrawerOpen,
          onClose: handleCloseImportDrawer,
          accountId: effectiveSelectedAccountId,
          accountType: selectedAccount?.type ?? null,
          onCompleted: refreshAfterImport,
          onRequestCreateAccount: handleRequestCreateAccountFromImport,
          reviewJobId: reviewJobId ?? undefined,
        }}
        importBillDrawer={{
          isOpen: isImportBillDrawerOpen,
          onClose: handleCloseImportBillDrawer,
        }}
        nfceDrawer={{
          isImportOpen: isNfceImportDrawerOpen,
          onCloseImport: handleCloseNfceImportDrawer,
          documentTransactionId: nfceDocumentTransactionId,
          onCloseDocument: handleCloseNfceDocumentDrawer,
          accountId: effectiveSelectedAccountId,
        }}
        payBillDialog={{
          isOpen: isPayBillDialogOpen,
          onClose: handleClosePayBillDialog,
          selectedBill: displayMode.kind === 'bill' ? displayMode.selectedBill : null,
          bankAccounts,
          onSuccess: handlePayBillSuccess,
        }}
      />
    </SidebarLayout>
  );
}
