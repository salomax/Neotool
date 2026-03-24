'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import {
  Container,
  Box,
  CircularProgress,
  Stack,
  Button,
  Alert,
  Divider,
  alpha,
  useTheme,
  Chip,
  Collapse,
  Fade,
} from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { extractErrorMessage } from '@/shared/utils/error';
import AddIcon from '@mui/icons-material/Add';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import CloseIcon from '@mui/icons-material/Close';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import { CreateFinancialAccountDrawer } from './account/CreateFinancialAccountDrawer';
import { ImportTransactionsDrawer } from './transaction/ImportTransactionsDrawer';
import { BillImportDrawer } from './bill/BillImportDrawer';
import { FinancialAccountSelector } from './account/FinancialAccountSelector';
import { EmptyFinancialAccountsState } from './account/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { CreditDebitType, FinancialAccount, FinancialAccountType, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { MonthYearCarousel } from './summary/MonthYearCarousel';
import { TransactionTable } from './transaction/TransactionTable';
import { CategoryFilterSelection, MonthSummary, MonthSummaryData } from './summary/MonthSummary';
import { CategoryIcon } from './category/CategoryIcon';
import { useAccountDisplayMode } from './account/useAccountDisplayMode';
import { BillCarousel } from './bill/BillCarousel';
import { BillSummaryHeader } from './bill/BillSummaryHeader';
import { PayBillDialog } from './bill/PayBillDialog';
import { useOnTransactionsImported } from './events';

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
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isImportDrawerOpen, setIsImportDrawerOpen] = useState(false);
  const [isImportBillDrawerOpen, setIsImportBillDrawerOpen] = useState(false);
  const [isCreateDrawerFromImport, setIsCreateDrawerFromImport] = useState(false);
  const [importMenuAnchor, setImportMenuAnchor] = useState<null | HTMLElement>(null);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [editingAccount, setEditingAccount] = useState<FinancialAccount | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);
  const [isPayBillDialogOpen, setIsPayBillDialogOpen] = useState(false);

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
      sidebar={
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
                bill={displayMode.selectedBill ? {
                  totalAmount: displayMode.selectedBill.totalAmount,
                  closingDate: displayMode.selectedBill.closingDate,
                  dueDate: displayMode.selectedBill.dueDate,
                  status: displayMode.selectedBill.status,
                  creditLimit: selectedAccount?.cardData?.creditLimit
                } : undefined}
                loading={displayMode.loading}
                onPayClick={() => setIsPayBillDialogOpen(true)}
              />
            )}
            <Divider sx={{ my: 3 }} />           
          </Box>
        </Box>
      }
    >
      <Page>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          {importsError && effectiveSelectedAccountId && (
            <Alert
              severity="warning"
              sx={{ width: '100%', mb: 2 }}
              action={
                <Button size="small" onClick={() => refetchImports()}>
                  {tCommon('cardGrid.retry')}
                </Button>
              }
            >
              {extractErrorMessage(importsError, tCommon('errors.default'))}
            </Alert>
          )}
          {reviewRequiredJob && (
            <Alert
              severity="info"
              sx={{ width: '100%', mb: 2 }}
              action={
                <Button
                  size="small"
                  onClick={() => {
                    setReviewJobId(reviewRequiredJob.id);
                    setIsImportDrawerOpen(true);
                  }}
                >
                  {t('financeTransactions.duplicateReview.bannerAction')}
                </Button>
              }
            >
              {t('financeTransactions.duplicateReview.header', {
                count: reviewRequiredJob.duplicateReviewCount ?? 1,
              })}
            </Alert>
          )}
          <Stack direction="row" spacing={2} sx={{ width: '100%', mb: 2 }} alignItems="flex-end" flexWrap="wrap">
            <Box sx={{ flex: 1, minWidth: 200 }}>
              {displayMode.kind === 'calendar' ? (
                <MonthYearCarousel 
                  selectedMonth={displayMode.month} 
                  selectedYear={displayMode.year}
                  onChange={handleDateChange}
                />
              ) : (
                <BillCarousel 
                  bills={displayMode.bills}
                  selectedBillId={displayMode.selectedBill?.id ?? null}
                  onSelectBill={(id) => {
                    const bill = displayMode.bills.find(b => b.id === id);
                    if (bill && displayMode.kind === 'bill') {
                      displayMode.setSelectedBill(bill);
                    }
                  }}
                />
              )}
            </Box>
            <Button
              variant="outlined"
              endIcon={<ArrowDropDownIcon />}
              onClick={(e) => setImportMenuAnchor(e.currentTarget)}
              aria-haspopup="menu"
              aria-expanded={!!importMenuAnchor}
              aria-controls={importMenuAnchor ? 'finance-transactions-import-menu' : undefined}
            >
              {t('financeTransactions.import.button')}
            </Button>
            <Menu
              id="finance-transactions-import-menu"
              anchorEl={importMenuAnchor}
              open={!!importMenuAnchor}
              onClose={() => setImportMenuAnchor(null)}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
            >
              <MenuItem
                onClick={() => {
                  setImportMenuAnchor(null);
                  setReviewJobId(null);
                  setIsImportDrawerOpen(true);
                }}
              >
                <ListItemIcon><AccountBalanceIcon fontSize="small" /></ListItemIcon>
                {t('financeTransactions.import.menu.file')}
              </MenuItem>
              <MenuItem
                onClick={() => {
                  setImportMenuAnchor(null);
                  setIsImportBillDrawerOpen(true);
                }}
              >
                <ListItemIcon><CreditCardIcon fontSize="small" /></ListItemIcon>
                {t('financeTransactions.import.menu.cardBill')}
              </MenuItem>
            </Menu>
            {/* <ScanNfceButton
              selectedAccountId={selectedAccountId}
              onImportCompleted={handleImportCompleted}
            /> */}
            <Button
              sx={{
                boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.28)}`,
                '&:hover': {
                  boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.36)}`,
                },
              }}
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setIsCreatingTransaction(true)}
              disabled={isCreatingTransaction}
            >
              {t('financeTransactions.quickAdd')}
            </Button>
          </Stack>

          {displayMode.kind === 'calendar' && (
            <Collapse
              in={summaryTableFiltersOpen}
              timeout={{ enter: 320, exit: 240 }}
              unmountOnExit
              easing={{
                enter: theme.transitions.easing.easeOut,
                exit: theme.transitions.easing.sharp,
              }}
              sx={{ width: '100%' }}
            >
              <Fade
                in={summaryTableFiltersOpen}
                timeout={{ enter: 260, exit: 180 }}
                easing={{
                  enter: theme.transitions.easing.easeOut,
                  exit: theme.transitions.easing.sharp,
                }}
              >
                <Box sx={{ width: '100%', mb: 2, pt: 0.25 }}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ width: '100%' }}>
                    {summaryCreditDebitTypeFilterEffective && (
                      <Chip
                        icon={
                          summaryCreditDebitTypeFilterEffective === CreditDebitType.Credit ? (
                            <ArrowUpwardIcon sx={{ fontSize: 18 }} />
                          ) : (
                            <ArrowDownwardIcon sx={{ fontSize: 18 }} />
                          )
                        }
                        label={
                          summaryCreditDebitTypeFilterEffective === CreditDebitType.Credit
                            ? t('financeTransactions.monthSummary.filteringByCredit')
                            : t('financeTransactions.monthSummary.filteringByDebit')
                        }
                        onDelete={clearCreditDebitTypeFilter}
                        deleteIcon={
                          <CloseIcon
                            fontSize="small"
                            aria-label={t('financeTransactions.monthSummary.clearCreditDebitTypeFilter')}
                          />
                        }
                        variant="outlined"
                        sx={{
                          pl: 1.5,
                          pr: 1.25,
                          py: 0.75,
                          bgcolor: alpha(summaryTypeChipAccent, 0.12),
                          borderColor: alpha(summaryTypeChipAccent, 0.4),
                          color: summaryTypeChipAccent,
                          fontWeight: 600,
                          '& .MuiChip-icon': {
                            color: summaryTypeChipAccent,
                            ml: 0.25,
                            mr: 0.5,
                          },
                          '& .MuiChip-label': { px: 1.25 },
                          '& .MuiChip-deleteIcon': {
                            color: alpha(summaryTypeChipAccent, 0.75),
                            '&:hover': { color: summaryTypeChipAccent },
                          },
                        }}
                      />
                    )}
                    {summaryCategoryFilterSelection && (
                      <Chip
                        icon={
                          <CategoryIcon
                            iconName={summaryCategoryFilterMeta?.icon ?? null}
                            color={summaryCategoryChipAccent}
                            size={18}
                          />
                        }
                        label={t('financeTransactions.monthSummary.filteringByCategory', {
                          name:
                            summaryCategoryFilterMeta?.name ??
                            (summaryUncategorizedFilterActive
                              ? t('financeTransactions.monthSummary.uncategorized')
                              : summaryCategoryFilterId),
                        })}
                        onDelete={() => setSummaryCategoryFilterSelection(null)}
                        deleteIcon={
                          <CloseIcon
                            fontSize="small"
                            aria-label={t('financeTransactions.monthSummary.clearCategoryFilter')}
                          />
                        }
                        variant="outlined"
                        sx={{
                          pl: 1.5,
                          pr: 1.25,
                          py: 0.75,
                          bgcolor: alpha(summaryCategoryChipAccent, 0.12),
                          borderColor: alpha(summaryCategoryChipAccent, 0.4),
                          color: summaryCategoryChipAccent,
                          fontWeight: 600,
                          '& .MuiChip-icon': {
                            color: summaryCategoryChipAccent,
                            ml: 0.25,
                            mr: 0.5,
                          },
                          '& .MuiChip-label': { px: 1.25 },
                          '& .MuiChip-deleteIcon': {
                            color: alpha(summaryCategoryChipAccent, 0.75),
                            '&:hover': { color: summaryCategoryChipAccent },
                          },
                        }}
                      />
                    )}
                    {summaryTagFilterValue && (
                      <Chip
                        label={t('financeTransactions.monthSummary.filteringByTag', {
                          name: summaryTagFilterMeta?.tag ?? summaryTagFilterValue,
                        })}
                        onDelete={() => setSummaryTagFilterValue(null)}
                        deleteIcon={
                          <CloseIcon
                            fontSize="small"
                            aria-label={t('financeTransactions.monthSummary.clearTagFilter')}
                          />
                        }
                        variant="outlined"
                        sx={{
                          pl: 1.5,
                          pr: 1.25,
                          py: 0.75,
                          bgcolor: alpha(summaryTagChipAccent, 0.12),
                          borderColor: alpha(summaryTagChipAccent, 0.4),
                          color: summaryTagChipAccent,
                          fontWeight: 600,
                          '& .MuiChip-label': { px: 1.25 },
                          '& .MuiChip-deleteIcon': {
                            color: alpha(summaryTagChipAccent, 0.75),
                            '&:hover': { color: summaryTagChipAccent },
                          },
                        }}
                      />
                    )}
                  </Stack>
                </Box>
              </Fade>
            </Collapse>
          )}

          {transactionsError && !transactionsLoading && transactions.length === 0 ? (
            <Box sx={{ width: '100%' }}>
              <QueryErrorState error={transactionsError} onRetry={() => refetchTransactions()} />
            </Box>
          ) : (
            <>
              {transactionsError && (
                <Alert
                  severity="warning"
                  sx={{ width: '100%', mb: 2 }}
                  action={
                    <Button size="small" onClick={() => refetchTransactions()}>
                      {tCommon('cardGrid.retry')}
                    </Button>
                  }
                >
                  {extractErrorMessage(transactionsError, tCommon('errors.default'))}
                </Alert>
              )}
              <TransactionTable 
                transactions={transactions} 
                financialAccounts={transactionAccounts}
                loading={transactionsLoading || isFetchingMore} 
                hasMore={hasMore} 
                onLoadMore={handleLoadMore} 
                isCreating={isCreatingTransaction}
                onCancelCreate={() => setIsCreatingTransaction(false)}
                onSuccessCreate={() => {
                  refetchTransactions();
                  refetchSummary();
                }}
                selectedAccountId={effectiveSelectedAccountId}
                selectedMonth={displayMode.kind === 'calendar' ? displayMode.month : undefined}
                selectedYear={displayMode.kind === 'calendar' ? displayMode.year : undefined}
              />
            </>
          )}
        </Container>
      </Page>
      
      <CreateFinancialAccountDrawer 
        open={isDrawerOpen} 
        onClose={handleCloseDrawer}
        initialType={initialType}
        accountToEdit={editingAccount}
        onBackFromPrefilledType={
          isCreateDrawerFromImport
            ? () => {
                setIsDrawerOpen(false);
                setIsCreateDrawerFromImport(false);
                setTimeout(() => setInitialType(null), 300);
                setIsImportDrawerOpen(true);
              }
            : undefined
        }
        onCreated={handleAccountCreated}
      />
      <ImportTransactionsDrawer
        open={isImportDrawerOpen}
        onClose={() => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
        }}
        selectedAccountId={effectiveSelectedAccountId}
        selectedAccountType={selectedAccount?.type ?? null}
        onImportCompleted={handleImportCompleted}
        onRequestCreateAccount={(type) => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
          setIsCreateDrawerFromImport(true);
          handleOpenDrawer(type);
        }}
        reviewJobId={reviewJobId ?? undefined}
      />
      <BillImportDrawer
        open={isImportBillDrawerOpen}
        onClose={() => setIsImportBillDrawerOpen(false)}
        onImportCompleted={handleImportCompleted}
      />
      <PayBillDialog
        open={isPayBillDialogOpen}
        bill={displayMode.kind === 'bill' ? displayMode.selectedBill : null}
        bankAccounts={bankAccounts}
        onClose={() => setIsPayBillDialogOpen(false)}
        onSuccess={() => {
          if (displayMode.kind === 'bill') displayMode.refetchBills();
          refetchTransactions();
        }}
      />
    </SidebarLayout>
  );
}
