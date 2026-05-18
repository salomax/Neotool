'use client';

import * as React from 'react';
import { useQuery } from '@apollo/client/react';
import { Box, CircularProgress } from '@mui/material';
import { INVESTMENT_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { ErrorAlert } from '@/shared/components/ui/feedback';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import type { IssuerSelectionAccount } from '../IssuerSelection';
import { RecordFixedIncomeDrawer } from '../RecordFixedIncomeDrawer';
import { UpdateHoldingDrawer } from '../update/UpdateHoldingDrawer';
import type { UpdateHoldingEditTransaction } from '../update/types';
import type { TransactionEditorExistingTransaction } from './config.types';
import type { TransactionEditorIntent } from './intent';
import { NewHoldingFlowShell } from './new-holding/NewHoldingFlowShell';

interface InvestmentTransactionsQueryResult {
  investmentTransactions: (TransactionEditorExistingTransaction & {
    holdingId: string;
    financialAccountId: string;
  })[];
}

interface InvestmentTransactionsQueryVariables {
  holdingId: string;
}

export interface TransactionEditorProps {
  open: boolean;
  intent: TransactionEditorIntent | null;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  onClose: () => void;
  onRecorded?: () => void | Promise<void>;
}

function mapInitialDirection(intent: TransactionEditorIntent): InvestmentTransactionDirection {
  if (intent.kind === 'new-sell' || intent.kind === 'row-sell') return InvestmentTransactionDirection.Outflow;
  return InvestmentTransactionDirection.Inflow;
}

export function TransactionEditorRouter({
  open,
  intent,
  financialAccountId,
  financialAccounts = [],
  onClose,
  onRecorded,
}: TransactionEditorProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const editIntent = intent?.kind === 'edit' ? intent : null;
  const editTransactionsQuery = useQuery<InvestmentTransactionsQueryResult, InvestmentTransactionsQueryVariables>(
    INVESTMENT_TRANSACTIONS,
    {
      variables: { holdingId: editIntent?.holdingId ?? '' },
      skip: !open || !editIntent?.holdingId,
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
    },
  );

  const editTransaction = React.useMemo(() => {
    if (!editIntent) return null;
    return editTransactionsQuery.data?.investmentTransactions.find((item) => item.id === editIntent.transactionId) ?? null;
  }, [editIntent, editTransactionsQuery.data?.investmentTransactions]);

  if (!open || !intent) return null;

  if (intent.kind === 'row-buy' || intent.kind === 'row-sell') {
    return (
      <UpdateHoldingDrawer
        open={open}
        onClose={onClose}
        holdingId={intent.holdingId}
        initialDirection={mapInitialDirection(intent)}
        onRecorded={onRecorded as (() => void) | undefined}
      />
    );
  }

  if (intent.kind === 'edit') {
    if (editTransactionsQuery.loading && !editTransaction) {
      return (
        <Drawer open={open} onClose={onClose} anchor="right" size="md">
          <Drawer.Header title={t('investments.updateHolding.loadingTitle')} />
          <Drawer.Body>
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={24} />
            </Box>
          </Drawer.Body>
        </Drawer>
      );
    }

    if (editTransactionsQuery.error || !editTransaction) {
      return (
        <Drawer open={open} onClose={onClose} anchor="right" size="md">
          <Drawer.Header title={t('investments.updateHolding.errorTitle')} />
          <Drawer.Body>
            <ErrorAlert error={editTransactionsQuery.error ?? t('investments.updateHolding.errorHoldingNotFound')} />
          </Drawer.Body>
        </Drawer>
      );
    }

    return (
      <UpdateHoldingDrawer
        open={open}
        onClose={onClose}
        holdingId={intent.holdingId}
        editTransaction={editTransaction as unknown as UpdateHoldingEditTransaction}
        onRecorded={onRecorded as (() => void) | undefined}
      />
    );
  }

  if (intent.kind === 'new-buy' || intent.kind === 'new-sell') {
    if (intent.assetClass === 'fixed-income') {
      return (
        <RecordFixedIncomeDrawer
          open={open}
          onClose={onClose}
          financialAccountId={financialAccountId}
          financialAccounts={financialAccounts}
          onRecorded={onRecorded as (() => void) | undefined}
        />
      );
    }

    return (
      <NewHoldingFlowShell
        open={open}
        onClose={onClose}
        intent={intent}
        financialAccountId={financialAccountId}
        financialAccounts={financialAccounts}
        onRecorded={onRecorded}
      />
    );
  }

  return null;
}
