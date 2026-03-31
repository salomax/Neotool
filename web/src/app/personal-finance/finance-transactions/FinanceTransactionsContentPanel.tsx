'use client';

import { Alert, Box, Button } from '@mui/material';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { TransactionTable } from './transaction/TransactionTable';
import { FinancialAccount, Transaction } from '@/lib/graphql/types/__generated__/graphql';

interface FinanceTransactionsContentPanelProps {
  transactionsError?: unknown;
  transactionsLoading: boolean;
  transactions: Transaction[];
  onRetryTransactions: () => void;
  financialAccounts: FinancialAccount[];
  tableLoading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  isCreatingTransaction: boolean;
  onCancelCreate: () => void;
  onSuccessCreate: () => void;
  selectedAccountId: string | null;
  selectedMonth?: number;
  selectedYear?: number;
}

export function FinanceTransactionsContentPanel({
  transactionsError,
  transactionsLoading,
  transactions,
  onRetryTransactions,
  financialAccounts,
  tableLoading,
  hasMore,
  onLoadMore,
  isCreatingTransaction,
  onCancelCreate,
  onSuccessCreate,
  selectedAccountId,
  selectedMonth,
  selectedYear,
}: FinanceTransactionsContentPanelProps) {
  const { tCommon } = useTranslation(personalFinanceTranslations);

  if (transactionsError && !transactionsLoading && transactions.length === 0) {
    return (
      <Box sx={{ width: '100%' }}>
        <QueryErrorState error={transactionsError} onRetry={onRetryTransactions} />
      </Box>
    );
  }

  return (
    <>
      {transactionsError && (
        <Alert
          severity="warning"
          sx={{ width: '100%', mb: 2 }}
          action={
            <Button size="small" onClick={onRetryTransactions}>
              {tCommon('cardGrid.retry')}
            </Button>
          }
        >
          {extractErrorMessage(transactionsError, tCommon('errors.default'))}
        </Alert>
      )}
      <TransactionTable
        transactions={transactions}
        financialAccounts={financialAccounts}
        loading={tableLoading}
        hasMore={hasMore}
        onLoadMore={onLoadMore}
        isCreating={isCreatingTransaction}
        onCancelCreate={onCancelCreate}
        onSuccessCreate={onSuccessCreate}
        selectedAccountId={selectedAccountId}
        selectedMonth={selectedMonth}
        selectedYear={selectedYear}
      />
    </>
  );
}
