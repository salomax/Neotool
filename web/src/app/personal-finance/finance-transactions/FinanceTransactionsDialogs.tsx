'use client';

import { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { CreateFinancialAccountDrawer } from './account/CreateFinancialAccountDrawer';
import { ImportTransactionsDrawer } from './transaction/ImportTransactionsDrawer';
import { BillImportDrawer } from './bill/BillImportDrawer';
import { PayBillDialog } from './bill/PayBillDialog';
import { NfceImportDrawer } from './transaction/NfceImportDrawer';
import { NfceDocumentDrawer } from './transaction/NfceDocumentDrawer';
import { CreditCardBill } from './account/useAccountDisplayMode';
interface AccountDrawerState {
  isOpen: boolean;
  onClose: () => void;
  initialType: 'BANK' | 'CARD' | null;
  editingAccount: FinancialAccount | null;
  onBack?: () => void;
  onCreated: () => void;
}

interface ImportDrawerState {
  isOpen: boolean;
  onClose: () => void;
  accountId: string | null;
  accountType: FinancialAccount['type'] | null;
  onCompleted: () => void;
  onRequestCreateAccount: (type?: 'BANK' | 'CARD') => void;
  reviewJobId?: string;
}

interface ImportBillDrawerState {
  isOpen: boolean;
  onClose: () => void;
}

interface NfceDrawerState {
  isImportOpen: boolean;
  onCloseImport: () => void;
  documentTransactionId: string | null;
  onCloseDocument: () => void;
  accountId: string | null;
}

interface PayBillDialogState {
  isOpen: boolean;
  onClose: () => void;
  selectedBill: CreditCardBill | null;
  bankAccounts: FinancialAccount[];
  onSuccess: () => void;
}

interface FinanceTransactionsDialogsProps {
  accountDrawer: AccountDrawerState;
  importDrawer: ImportDrawerState;
  importBillDrawer: ImportBillDrawerState;
  nfceDrawer: NfceDrawerState;
  payBillDialog: PayBillDialogState;
}

export function FinanceTransactionsDialogs({
  accountDrawer,
  importDrawer,
  importBillDrawer,
  nfceDrawer,
  payBillDialog,
}: FinanceTransactionsDialogsProps) {
  return (
    <>
      <CreateFinancialAccountDrawer
        open={accountDrawer.isOpen}
        onClose={accountDrawer.onClose}
        initialType={accountDrawer.initialType}
        accountToEdit={accountDrawer.editingAccount}
        onBackFromPrefilledType={accountDrawer.onBack}
        onCreated={accountDrawer.onCreated}
      />
      <ImportTransactionsDrawer
        open={importDrawer.isOpen}
        onClose={importDrawer.onClose}
        selectedAccountId={importDrawer.accountId}
        selectedAccountType={importDrawer.accountType}
        onImportCompleted={importDrawer.onCompleted}
        onRequestCreateAccount={importDrawer.onRequestCreateAccount}
        reviewJobId={importDrawer.reviewJobId}
      />
      <BillImportDrawer
        open={importBillDrawer.isOpen}
        onClose={importBillDrawer.onClose}
        onImportCompleted={importDrawer.onCompleted}
      />
      <NfceImportDrawer
        open={nfceDrawer.isImportOpen}
        onClose={nfceDrawer.onCloseImport}
        selectedAccountId={nfceDrawer.accountId}
        onImportCompleted={importDrawer.onCompleted}
      />
      <NfceDocumentDrawer
        open={Boolean(nfceDrawer.documentTransactionId)}
        onClose={nfceDrawer.onCloseDocument}
        transactionId={nfceDrawer.documentTransactionId}
      />
      <PayBillDialog
        open={payBillDialog.isOpen}
        bill={payBillDialog.selectedBill}
        bankAccounts={payBillDialog.bankAccounts}
        onClose={payBillDialog.onClose}
        onSuccess={payBillDialog.onSuccess}
      />
    </>
  );
}
