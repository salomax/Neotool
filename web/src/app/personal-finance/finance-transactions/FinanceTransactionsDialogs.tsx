'use client';

import { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { CreateFinancialAccountDrawer } from './account/CreateFinancialAccountDrawer';
import { ImportTransactionsDrawer } from './transaction/ImportTransactionsDrawer';
import { BillImportDrawer } from './bill/BillImportDrawer';
import { PayBillDialog } from './bill/PayBillDialog';
import { CreditCardBill } from './account/useAccountDisplayMode';

interface FinanceTransactionsDialogsProps {
  isDrawerOpen: boolean;
  onCloseDrawer: () => void;
  initialType: 'BANK' | 'CARD' | null;
  editingAccount: FinancialAccount | null;
  onBackFromPrefilledType?: () => void;
  onAccountCreated: () => void;
  isImportDrawerOpen: boolean;
  onCloseImportDrawer: () => void;
  selectedAccountId: string | null;
  selectedAccountType: FinancialAccount['type'] | null;
  onImportCompleted: () => void;
  onRequestCreateAccount: (type?: 'BANK' | 'CARD') => void;
  reviewJobId?: string;
  isImportBillDrawerOpen: boolean;
  onCloseImportBillDrawer: () => void;
  isPayBillDialogOpen: boolean;
  selectedBill: CreditCardBill | null;
  bankAccounts: FinancialAccount[];
  onClosePayBillDialog: () => void;
  onPayBillSuccess: () => void;
}

export function FinanceTransactionsDialogs({
  isDrawerOpen,
  onCloseDrawer,
  initialType,
  editingAccount,
  onBackFromPrefilledType,
  onAccountCreated,
  isImportDrawerOpen,
  onCloseImportDrawer,
  selectedAccountId,
  selectedAccountType,
  onImportCompleted,
  onRequestCreateAccount,
  reviewJobId,
  isImportBillDrawerOpen,
  onCloseImportBillDrawer,
  isPayBillDialogOpen,
  selectedBill,
  bankAccounts,
  onClosePayBillDialog,
  onPayBillSuccess,
}: FinanceTransactionsDialogsProps) {
  return (
    <>
      <CreateFinancialAccountDrawer
        open={isDrawerOpen}
        onClose={onCloseDrawer}
        initialType={initialType}
        accountToEdit={editingAccount}
        onBackFromPrefilledType={onBackFromPrefilledType}
        onCreated={onAccountCreated}
      />
      <ImportTransactionsDrawer
        open={isImportDrawerOpen}
        onClose={onCloseImportDrawer}
        selectedAccountId={selectedAccountId}
        selectedAccountType={selectedAccountType}
        onImportCompleted={onImportCompleted}
        onRequestCreateAccount={onRequestCreateAccount}
        reviewJobId={reviewJobId}
      />
      <BillImportDrawer
        open={isImportBillDrawerOpen}
        onClose={onCloseImportBillDrawer}
        onImportCompleted={onImportCompleted}
      />
      <PayBillDialog
        open={isPayBillDialogOpen}
        bill={selectedBill}
        bankAccounts={bankAccounts}
        onClose={onClosePayBillDialog}
        onSuccess={onPayBillSuccess}
      />
    </>
  );
}
