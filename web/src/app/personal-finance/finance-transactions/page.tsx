'use client';

import { useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { Container, Typography, Box, CircularProgress, Stack, Button, Alert, Divider } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { usePageTitle } from '@/shared/hooks/ui/usePageTitle';
import AddIcon from '@mui/icons-material/Add';
import { CreateFinancialAccountDrawer } from './components/CreateFinancialAccountDrawer';
import { ImportTransactionsDrawer } from './components/ImportTransactionsDrawer';
import { ScanNfceButton } from './components/ScanNfceButton';
import { FinancialAccountSelector } from './components/FinancialAccountSelector';
import { EmptyFinancialAccountsState } from './components/EmptyFinancialAccountsState';
import { FINANCIAL_ACCOUNTS, TRANSACTIONS, TRANSACTION_IMPORTS, MONTH_SUMMARY } from '@/lib/graphql/operations/banking/queries';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { FinancialAccount, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { MonthYearCarousel } from './components/MonthYearCarousel';
import { TransactionTable } from './components/TransactionTable';
import { MonthSummary, MonthSummaryData } from './components/MonthSummary';
import dayjs from 'dayjs';

export default function FinanceTransactionsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isImportDrawerOpen, setIsImportDrawerOpen] = useState(false);
  const [reviewJobId, setReviewJobId] = useState<string | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [initialType, setInitialType] = useState<'BANK' | 'CARD' | null>(null);
  const [isCreatingTransaction, setIsCreatingTransaction] = useState(false);

  const { data, loading } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    errorPolicy: 'all',
  });
  
  const [currentDate, setCurrentDate] = useState({ month: dayjs().month() + 1, year: dayjs().year() });

  const { data: transactionsData, loading: transactionsLoading, fetchMore, refetch: refetchTransactions } = useQuery<TransactionsQuery>(TRANSACTIONS, {
    variables: {
      financialAccountId: selectedAccountId,
      month: currentDate.month,
      year: currentDate.year,
      first: 20,
    },
    notifyOnNetworkStatusChange: true,
  });

  const { data: monthSummaryData, loading: monthSummaryLoading, refetch: refetchMonthSummary } = useQuery<{ monthSummary: MonthSummaryData }>(MONTH_SUMMARY, {
    variables: {
      financialAccountId: selectedAccountId,
      month: currentDate.month,
      year: currentDate.year,
    },
    fetchPolicy: 'cache-and-network',
  });

  const { data: importsData, refetch: refetchImports } = useQuery<{ transactionImports: Array<{ id: string; status: string; duplicateReviewCount: number }> }>(TRANSACTION_IMPORTS, {
    variables: { financialAccountId: selectedAccountId },
    skip: !selectedAccountId,
  });
  const reviewRequiredJob = importsData?.transactionImports?.find((j) => j.status === 'REVIEW_REQUIRED');

  const handleImportCompleted = () => {
    refetchTransactions();
    refetchImports();
    refetchMonthSummary();
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
    setCurrentDate({ month, year });
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

  const hasAccounts = data?.financialAccounts && data.financialAccounts.length > 0;

  if (!hasAccounts) {
    return (
      <>
        <EmptyFinancialAccountsState onSelectType={handleOpenDrawer} />
        <CreateFinancialAccountDrawer 
          open={isDrawerOpen} 
          onClose={handleCloseDrawer}
          initialType={initialType}
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
            <MonthSummary 
              summary={monthSummaryData?.monthSummary}
              loading={monthSummaryLoading}
            />
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
              <MonthYearCarousel 
                selectedMonth={currentDate.month} 
                selectedYear={currentDate.year}
                onChange={handleDateChange}
              />
            </Box>
            <Button
              variant="outlined"
              onClick={() => {
                setReviewJobId(null);
                setIsImportDrawerOpen(true);
              }}
            >
              {t('financeTransactions.import.button')}
            </Button>
            <ScanNfceButton
              selectedAccountId={selectedAccountId}
              onImportCompleted={handleImportCompleted}
            />
            <Button
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
              refetchMonthSummary();
            }}
            selectedAccountId={selectedAccountId}
          />
        </Container>
      </Page>
      
      <CreateFinancialAccountDrawer 
        open={isDrawerOpen} 
        onClose={handleCloseDrawer}
        initialType={initialType}
      />
      <ImportTransactionsDrawer
        open={isImportDrawerOpen}
        onClose={() => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
        }}
        selectedAccountId={selectedAccountId}
        onImportCompleted={handleImportCompleted}
        onRequestCreateAccount={() => {
          setIsImportDrawerOpen(false);
          setReviewJobId(null);
          handleOpenDrawer('BANK');
        }}
        reviewJobId={reviewJobId ?? undefined}
      />
    </SidebarLayout>
  );
}
