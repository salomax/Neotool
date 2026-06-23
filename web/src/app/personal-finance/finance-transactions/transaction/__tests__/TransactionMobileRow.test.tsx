import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import {
  CreditDebitType,
  TransactionType,
  type Transaction,
} from '@/lib/graphql/types/__generated__/graphql';
import { TransactionMobileRow } from '../TransactionMobileRow';
import { TransactionActionMenu } from '../TransactionActionMenu';
import type { TransactionRowCallbacks } from '../TransactionRow';

const mockUseMediaQuery = vi.fn((_query?: unknown) => true);

vi.mock('@mui/material/useMediaQuery', () => ({
  default: (query: unknown) => mockUseMediaQuery(query),
}));

const baseTransaction = {
  id: 'tx-1',
  description: 'Coffee shop',
  date: '2026-03-01',
  creditDebitType: CreditDebitType.Debit,
  type: TransactionType.Card,
  amount: 12.34,
  currencyCode: 'BRL',
  financialAccountId: 'acc-1',
  merchant: null,
  category: {
    id: 'cat-1',
    name: 'Food',
    icon: 'restaurant',
    color: '#00897b',
  },
  appliedRuleIds: [],
  tags: ['food'],
  attachments: [],
} as unknown as Transaction;

function renderRow(transaction: Transaction, onEditTransaction = vi.fn()) {
  function Harness() {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [menuTransaction, setMenuTransaction] = useState<Transaction | null>(null);

    const callbacks: TransactionRowCallbacks = {
      onOpenCategoryPicker: vi.fn(),
      onOpenActionMenu: (el, tx) => {
        setAnchorEl(el);
        setMenuTransaction(tx);
      },
      onOpenAttachmentPreview: vi.fn(),
      onEditClose: vi.fn(),
      onEditExited: vi.fn(),
      onEditSuccess: vi.fn(),
      onEditRefresh: vi.fn(),
      onBusyStart: vi.fn(),
      onBusyEnd: vi.fn(),
    };

    return (
      <>
        <TransactionMobileRow
          transaction={transaction}
          account={null}
          isEditing={false}
          isEditFormOpen={false}
          isDeleting={false}
          updatingCategoryTransactionId={null}
          displayedCategory={transaction.category}
          selectedAccountId="acc-1"
          activeActionMenuTransactionId={menuTransaction?.id ?? null}
          activeActionMenuAnchorEl={anchorEl}
          showDivider={false}
          callbacks={callbacks}
        />
        <TransactionActionMenu
          anchorEl={anchorEl}
          transaction={menuTransaction}
          markingTransferTransactionId={null}
          markingBillPaymentTransactionId={null}
          onClose={() => {
            setAnchorEl(null);
            setMenuTransaction(null);
          }}
          onEdit={onEditTransaction}
          onViewAppliedRules={vi.fn()}
          onCreateRule={vi.fn()}
          onCreateRecurringPayment={vi.fn()}
          onMarkAsTransfer={vi.fn()}
          onMarkAsBillPayment={vi.fn()}
          onUnmarkBillPayment={vi.fn()}
          onDelete={vi.fn()}
        />
      </>
    );
  }

  return render(<Harness />);
}

describe.sequential('TransactionMobileRow', () => {
  it('renders description, amount, category chip, and direction badge', () => {
    renderRow(baseTransaction);

    expect(screen.getByText('Coffee shop')).toBeInTheDocument();
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByTestId('transaction-mobile-row-direction-tx-1')).toBeInTheDocument();
    expect(screen.getByTestId('transaction-mobile-row-amount-tx-1')).toHaveTextContent('12');
  });

  it('invokes edit through the shared action drawer when the row is clicked', async () => {
    const user = userEvent.setup();
    const onEditTransaction = vi.fn();

    renderRow(baseTransaction, onEditTransaction);

    await user.click(
      screen.getByTestId('transaction-mobile-row-tx-1'),
    );
    await user.click(
      screen.getByTestId('transaction-action-edit'),
    );

    expect(onEditTransaction).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'tx-1' }),
    );
  });

  it('renders installment progress only when installment data is present', () => {
    const withInstallment = {
      ...baseTransaction,
      installmentNumber: 2,
      totalInstallments: 4,
    } as Transaction;

    const { rerender } = renderRow(withInstallment);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();

    rerender(
      <TransactionMobileRow
        transaction={baseTransaction}
        account={null}
        isEditing={false}
        isEditFormOpen={false}
        isDeleting={false}
        updatingCategoryTransactionId={null}
        displayedCategory={baseTransaction.category}
        selectedAccountId="acc-1"
        activeActionMenuTransactionId={null}
        activeActionMenuAnchorEl={null}
        showDivider={false}
        callbacks={{
          onOpenCategoryPicker: vi.fn(),
          onOpenActionMenu: vi.fn(),
          onOpenAttachmentPreview: vi.fn(),
          onEditClose: vi.fn(),
          onEditExited: vi.fn(),
          onEditSuccess: vi.fn(),
          onEditRefresh: vi.fn(),
          onBusyStart: vi.fn(),
          onBusyEnd: vi.fn(),
        }}
      />,
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });
});
