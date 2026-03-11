'use client';

import { useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { Container, Typography, Box, CircularProgress, Stack, Button, Alert, Divider, useTheme } from '@mui/material';
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
import { ImportBillDrawer } from './components/ImportBillDrawer';
import { ScanNfceButton } from './components/ScanNfceButton';
import { FinancialAccountSelector } from './components/FinancialAccountSelector';
import { EmptyFinancialAccountsState } from './components/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY, TRANSACTIONS_BY_BILL } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { FinancialAccount, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { MonthYearCarousel } from './components/MonthYearCarousel';
import { TransactionTable } from './components/TransactionTable';
import { MonthSummary, MonthSummaryData } from './components/MonthSummary';
import { useAccountDisplayMode } from './hooks/useAccountDisplayMode';
import { BillCarousel } from './components/BillCarousel';
import { BillSummaryHeader } from './components/BillSummaryHeader';
import dayjs from 'dayjs';

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
  
  const selectedAccount = data?.financialAccounts.find(a => a.id === selectedAccountId) || null;
  const displayMode = useAccountDisplayMode(selectedAccount);

  const { 
    data: calendarTransactionsData, 
    loading: calendarTransactionsLoading, 
    fetchMore: fetchMoreCalendar, 
    refetch: refetchCalendarTransactions 
  } = useQuery<TransactionsQuery>(TRANSACTIONS, {
    variables: {
      financialAccountId: selectedAccountId,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
      first: 20,
    },
    skip: displayMode.kind !== 'calendar',
    notifyOnNetworkStatusChange: true,
  });

  const { 
    data: billTransactionsData, 
    loading: billTransactionsLoading, 
    fetchMore: fetchMoreBill, 
    refetch: refetchBillTransactions 
  } = useQuery<TransactionsQuery>(TRANSACTIONS_BY_BILL, {
    variables: {
      billId: displayMode.kind === 'bill' ? displayMode.selectedBill?.id : undefined,
      first: 20,
    },
    skip: displayMode.kind !== 'bill' || !displayMode.selectedBill?.id,
    notifyOnNetworkStatusChange: true,
  });

  const {
    data: monthSummaryData,
    loading: monthSummaryLoading,
    error: monthSummaryError,
    refetch: refetchMonthSummary,
  } = useQuery<{ monthSummary: MonthSummaryData }>(MONTH_SUMMARY, {
    variables: {
      financialAccountId: selectedAccountId,
      month: displayMode.kind === 'calendar' ? displayMode.month : undefined,
      year: displayMode.kind === 'calendar' ? displayMode.year : undefined,
    },
    fetchPolicy: 'cache-and-network',
    skip: displayMode.kind !== 'calendar',
  });

  const transactionsData = displayMode.kind === 'calendar' ? calendarTransactionsData : billTransactionsData;
  const transactionsLoading = displayMode.kind === 'calendar' ? calendarTransactionsLoading : billTransactionsLoading;
  const fetchMore = displayMode.kind === 'calendar' ? fetchMoreCalendar : fetchMoreBill;
  const refetchTransactions = displayMode.kind === 'calendar' ? refetchCalendarTransactions : refetchBillTransactions;

  const { data: importsData, refetch: refetchImports } = useQuery<{ transactionImports: Array<{ id: string; status: string; duplicateReviewCount: number }> }>(TRANSACTION_IMPORTS, {
    variables: { financialAccountId: selectedAccountId },
    skip: !selectedAccountId,
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

  const handleLoadMore = () => {
    if (hasMore && !transactionsLoading) {
      fetchMore({
        variables: {
          after: transactionsData?.transactions.pageInfo.endCursor,
        },
      });
    }
  };

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

  const hasAccounts = data?.financialAccounts && data.financialAccounts.length > 0;

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
              selectedAccountId={selectedAccountId}
              onSelectAccount={setSelectedAccountId}
            />
            <Divider sx={{ my: 3 }} />
            {displayMode.kind === 'calendar' ? (
              <MonthSummary 
                  summary={monthSummaryData?.monthSummary}
                  loading={monthSummaryLoading}
                  error={monthSummaryError}
                  onRetry={() => refetchMonthSummary()}
                  isConsolidated={!selectedAccountId}
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
                Extrato bancário
              </MenuItem>
              <MenuItem
                onClick={() => {
                  setImportMenuAnchor(null);
                  setIsImportBillDrawerOpen(true);
                }}
              >
                <ListItemIcon><CreditCardIcon fontSize="small" /></ListItemIcon>
                Fatura do cartão
              </MenuItem>
            </Menu>
            <ScanNfceButton
              selectedAccountId={selectedAccountId}
              onImportCompleted={handleImportCompleted}
            />
            <Button
              sx={{ boxShadow: theme.shadows[4] }}
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
            financialAccounts={data?.financialAccounts}
            loading={transactionsLoading} 
            hasMore={hasMore} 
            onLoadMore={handleLoadMore} 
            isCreating={isCreatingTransaction}
            onCancelCreate={() => setIsCreatingTransaction(false)}
            onSuccessCreate={() => {
              refetchTransactions();
              refetchSummary();
            }}
            selectedAccountId={selectedAccountId}
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
        selectedAccountId={selectedAccountId}
        selectedAccountType={selectedAccount?.type ?? null}
        onImportCompleted={handleImportCompleted}
        onRequestCreateAccount={() => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
          handleOpenDrawer('BANK');
        }}
        reviewJobId={reviewJobId ?? undefined}
      />
      <ImportBillDrawer
        open={isImportBillDrawerOpen}
        onClose={() => setIsImportBillDrawerOpen(false)}
        onImportCompleted={handleImportCompleted}
      />
    </SidebarLayout>
  );
}
