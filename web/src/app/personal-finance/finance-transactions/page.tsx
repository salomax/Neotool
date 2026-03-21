'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { Container, Box, CircularProgress, Stack, Button, Alert, Divider, alpha, useTheme, Chip } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { QueryErrorState } from '@/shared/components/ui/feedback';
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
import { CreateFinancialAccountDrawer } from './components/CreateFinancialAccountDrawer';
import { ImportTransactionsDrawer } from './components/ImportTransactionsDrawer';
import { BillImportDrawer } from './components/BillImportDrawer';
import { FinancialAccountSelector } from './components/FinancialAccountSelector';
import { EmptyFinancialAccountsState } from './components/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { CreditDebitType, FinancialAccount, FinancialAccountType, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { MonthYearCarousel } from './components/MonthYearCarousel';
import { TransactionTable } from './components/TransactionTable';
import { MonthSummary, MonthSummaryData } from './components/MonthSummary';
import { CategoryIcon } from './components/category/CategoryIcon';
import { useAccountDisplayMode } from './hooks/useAccountDisplayMode';
import { BillCarousel } from './components/BillCarousel';
import { BillSummaryHeader } from './components/BillSummaryHeader';

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
  const [importMenuAnchor, setImportMenuAnchor] = useState<null | HTMLElement>(null);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);

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

  const [summaryCategoryFilter, setSummaryCategoryFilter] = useState<{ scopeKey: string; categoryId: string | null }>(() => ({
    scopeKey: summaryCategoryFilterScopeKey,
    categoryId: null,
  }));

  const summaryCategoryFilterId =
    summaryCategoryFilter.scopeKey === summaryCategoryFilterScopeKey ? summaryCategoryFilter.categoryId : null;

  const setSummaryCategoryFilterId = useCallback((categoryId: string | null) => {
    setSummaryCategoryFilter({ scopeKey: summaryCategoryFilterScopeKey, categoryId });
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

  const { 
    data: transactionsData, 
    loading: transactionsLoading, 
    fetchMore, 
    refetch: refetchTransactions 
  } = useQuery<TransactionsQuery>(TRANSACTIONS, {
    variables: {
      financialAccountId: displayMode.kind === 'calendar' ? effectiveSelectedAccountId : undefined,
      billId: displayMode.kind === 'bill' ? displayMode.selectedBill?.id : undefined,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
      categoryId: displayMode.kind === 'calendar' && summaryCategoryFilterId ? summaryCategoryFilterId : undefined,
      creditDebitType:
        displayMode.kind === 'calendar' && summaryCreditDebitTypeFilterEffective
          ? summaryCreditDebitTypeFilterEffective
          : undefined,
      first: 20,
    },
    skip: displayMode.kind === 'bill' && !displayMode.selectedBill?.id,
    notifyOnNetworkStatusChange: true,
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

  const { data: importsData, refetch: refetchImports } = useQuery<{ transactionImports: Array<{ id: string; status: string; duplicateReviewCount: number }> }>(TRANSACTION_IMPORTS, {
    variables: { financialAccountId: effectiveSelectedAccountId },
    skip: !effectiveSelectedAccountId,
  });
  const reviewRequiredJob = importsData?.transactionImports?.find((j) => j.status === 'REVIEW_REQUIRED');

  const summaryExpensesByCategory = monthSummaryData?.monthSummary?.expensesByCategory ?? null;
  const summaryIncomeByCategory = monthSummaryData?.monthSummary?.incomeByCategory ?? null;

  const summaryCategoryFilterMeta = useMemo(() => {
    if (!summaryCategoryFilterId || (!summaryExpensesByCategory && !summaryIncomeByCategory)) return null;
    const lists = [
      ...(summaryExpensesByCategory ?? []),
      ...(summaryIncomeByCategory ?? []),
    ];
    const hit = lists.find((c) => c.category.id === summaryCategoryFilterId);
    if (!hit) return null;
    return {
      name: hit.category.name,
      icon: hit.category.icon,
      color: hit.category.color,
    };
  }, [summaryCategoryFilterId, summaryExpensesByCategory, summaryIncomeByCategory]);

  const summaryCategoryChipAccent = summaryCategoryFilterMeta?.color ?? theme.palette.primary.main;

  const summaryTypeChipAccent =
    summaryCreditDebitTypeFilterEffective === CreditDebitType.Credit
      ? theme.palette.success.main
      : summaryCreditDebitTypeFilterEffective === CreditDebitType.Debit
        ? theme.palette.error.main
        : theme.palette.primary.main;

  const refetchSummary = () => {
    if (displayMode.kind === 'calendar') {
      refetchMonthSummary();
    } else {
      displayMode.refetchBills();
    }
  };

  const handleImportCompleted = () => {
    const canRefetchTransactions = !(displayMode.kind === 'bill' && !displayMode.selectedBill?.id);
    if (canRefetchTransactions) {
      refetchTransactions();
    }
    if (effectiveSelectedAccountId) {
      refetchImports();
    }
    refetchSummary();
  };

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
    if (type) {
      setInitialType(type);
    } else {
      setInitialType(null);
    }
    setIsDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setIsDrawerOpen(false);
    setIsCreateDrawerFromImport(false);
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
                  onCategoryClick={(categoryId) => setSummaryCategoryFilterId(categoryId)}
                  activeCategoryFilterId={summaryCategoryFilterId}
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
                onPayClick={() => {
                  // TODO: Implement pay bill
                }}
              />
            )}
            <Divider sx={{ my: 3 }} />           
          </Box>
        </Box>
      }
    >
      <Page>
        <Container maxWidth="xl" sx={{ py: 4 }}>
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
            >
              {t('financeTransactions.import.button')}
            </Button>
            <Menu
              anchorEl={importMenuAnchor}
              open={Boolean(importMenuAnchor)}
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

          {displayMode.kind === 'calendar' &&
            (summaryCategoryFilterId || summaryCreditDebitTypeFilterEffective) && (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ width: '100%', mb: 2 }}>
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
                {summaryCategoryFilterId && (
                  <Chip
                    icon={
                      <CategoryIcon
                        iconName={summaryCategoryFilterMeta?.icon ?? null}
                        color={summaryCategoryChipAccent}
                        size={18}
                      />
                    }
                    label={t('financeTransactions.monthSummary.filteringByCategory', {
                      name: summaryCategoryFilterMeta?.name ?? summaryCategoryFilterId,
                    })}
                    onDelete={() => setSummaryCategoryFilterId(null)}
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
              </Stack>
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
        </Container>
      </Page>
      
      <CreateFinancialAccountDrawer 
        open={isDrawerOpen} 
        onClose={handleCloseDrawer}
        initialType={initialType}
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
    </SidebarLayout>
  );
}
