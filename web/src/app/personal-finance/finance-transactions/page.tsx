'use client';

import { useCallback, useRef, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { Container, Box, CircularProgress, Stack, Button, Alert, Divider, alpha, useTheme } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import AddIcon from '@mui/icons-material/Add';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CreditCardIcon from '@mui/icons-material/CreditCard';
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
import { FinancialAccount, FinancialAccountType, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { MonthYearCarousel } from './components/MonthYearCarousel';
import { TransactionTable } from './components/TransactionTable';
import { MonthSummary, MonthSummaryData } from './components/MonthSummary';
import { useAccountDisplayMode } from './hooks/useAccountDisplayMode';
import { BillCarousel } from './components/BillCarousel';
import { BillSummaryHeader } from './components/BillSummaryHeader';

export default function FinanceTransactionsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isImportDrawerOpen, setIsImportDrawerOpen] = useState(false);
  const [isImportBillDrawerOpen, setIsImportBillDrawerOpen] = useState(false);
  const [importMenuAnchor, setImportMenuAnchor] = useState<null | HTMLElement>(null);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);
  
  const theme = useTheme();

  const { data, loading, error: financialAccountsError, refetch: refetchFinancialAccounts } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    errorPolicy: 'all',
  });
  
  const transactionAccountTypes: FinancialAccountType[] = [
    FinancialAccountType.Bank,
    FinancialAccountType.Credit,
    FinancialAccountType.Payment,
  ];
  const transactionAccounts = (data?.financialAccounts ?? []).filter((account) => transactionAccountTypes.includes(account.type));
  const effectiveSelectedAccountId =
    selectedAccountId && transactionAccounts.some((account) => account.id === selectedAccountId) ? selectedAccountId : null;
  const selectedAccount = transactionAccounts.find((a) => a.id === effectiveSelectedAccountId) || null;
  const displayMode = useAccountDisplayMode(selectedAccount);

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

  const refetchSummary = () => {
    if (displayMode.kind === 'calendar') {
      refetchMonthSummary();
    } else {
      displayMode.refetchBills();
    }
  };

  const handleImportCompleted = () => {
    refetchTransactions();
    refetchImports();
    refetchSummary();
  };

  const handleAccountCreated = () => {
    refetchSummary();
  };

  const transactions = (transactionsData?.transactions?.edges?.map(edge => edge.node) || []) as Transaction[];
  const hasMore = transactionsData?.transactions?.pageInfo?.hasNextPage || false;
  const endCursor = transactionsData?.transactions?.pageInfo?.endCursor ?? null;
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const isFetchingMoreRef = useRef(false);

  const handleLoadMore = useCallback(() => {
    if (!hasMore || !endCursor) return;
    if (transactionsLoading || isFetchingMoreRef.current) return;

    isFetchingMoreRef.current = true;
    setIsFetchingMore(true);

    fetchMore({
      variables: {
        after: endCursor,
      },
      updateQuery: (previousQueryResult, { fetchMoreResult }) => {
        if (!fetchMoreResult?.transactions) return previousQueryResult;

        const previousEdges = previousQueryResult.transactions?.edges ?? [];
        const incomingEdges = fetchMoreResult.transactions.edges ?? [];

        const mergedEdges = [...previousEdges, ...incomingEdges].filter((edge, index, allEdges) => {
          const id = edge?.node?.id;
          if (!id) return false;
          return allEdges.findIndex((e) => e?.node?.id === id) === index;
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
      setIsFetchingMore(false);
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
    // Reset initial type after drawer closes (timeout to avoid flicker)
    setTimeout(() => setInitialType(null), 300);
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
              accountTypes={[FinancialAccountType.Bank, FinancialAccountType.Credit, FinancialAccountType.Payment]}
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
          />
        </Container>
      </Page>
      
      <CreateFinancialAccountDrawer 
        open={isDrawerOpen} 
        onClose={handleCloseDrawer}
        initialType={initialType}
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
        onRequestCreateAccount={() => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
          handleOpenDrawer();
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
