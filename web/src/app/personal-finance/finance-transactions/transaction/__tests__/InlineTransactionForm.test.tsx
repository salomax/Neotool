import type { ReactElement } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme, Table, TableBody } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { InlineTransactionForm } from '../InlineTransactionForm';
import { TransactionActionMenu } from '../TransactionActionMenu';
import { TransactionRow } from '../TransactionRow';
import { resolveSubmittedCategoryId } from '../hooks/useTransactionSave';
import { resolveRegularTransactionType } from '../transactionTypeUtils';
import {
  APPLY_TRANSACTION_EDIT_TO_SIMILAR,
  CREATE_TRANSACTION,
  UPDATE_TRANSACTION,
} from '@/lib/graphql/operations/banking/mutations';
import { CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';
import {
  useMutation as mockUseMutation,
  useQuery as mockUseQuery,
  useLazyQuery as mockUseLazyQuery,
} from '@apollo/client/react';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
  useLazyQuery: vi.fn(() => [vi.fn(), { loading: false, data: null }]),
  useMutation: vi.fn(),
  useApolloClient: vi.fn(),
}));

vi.mock('@/shared/providers', () => ({
  usePreferences: () => ({
    bankingUserPreferences: {
      ruleCreationMode: 'ALWAYS_PROMPT',
    },
  }),
  useToast: () => ({
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  }),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('../SimilarEditDialog', () => ({
  SimilarEditDialog: () => null,
}));

vi.mock('../hooks/useRecurringSuggestion', () => ({
  useRecurringSuggestion: () => ({
    recurringPaymentSuggestion: null,
    handleLinkRecurringPayment: vi.fn(),
  }),
}));

vi.mock('../../../components/AttachmentSection', () => ({
  AttachmentSection: () => <div data-testid="attachment-section-mock" />,
}));

vi.mock('../MerchantSelect', () => ({
  MerchantSelect: () => <div data-testid="merchant-select-mock" />,
}));

const theme = createTheme();

function renderInTable(ui: ReactElement) {
  return render(
    <ThemeProvider theme={theme}>
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <Table>
          <TableBody>{ui}</TableBody>
        </Table>
      </LocalizationProvider>
    </ThemeProvider>,
  );
}

const baseTransaction = {
  id: 'tx-1',
  financialAccountId: 'acc-1',
  description: 'Transaction',
  date: '2026-03-15',
  amount: 100,
  currencyCode: 'BRL',
  merchant: null,
  appliedRuleIds: [],
  tags: [],
  attachments: [],
} as const;

afterEach(() => {
  cleanup();
});

describe('InlineTransactionForm — BALANCE_ADJUSTMENT edit', () => {
  const updateTransaction = vi.fn().mockResolvedValue({});

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(mockUseQuery).mockReturnValue({
      data: {
        financialAccounts: [{ id: 'acc-1', name: 'Conta', issuer: null, themeColor: null }],
        transactionCategories: [],
        transactionTags: [],
      },
    } as any);
    vi.mocked(mockUseMutation).mockImplementation((doc: unknown) => {
      if (doc === UPDATE_TRANSACTION) {
        return [updateTransaction, { loading: false }] as any;
      }
      if (doc === APPLY_TRANSACTION_EDIT_TO_SIMILAR) {
        return [vi.fn(), { loading: false }] as any;
      }
      return [vi.fn(), { loading: false }] as any;
    });
  });

  it('marks form as balance-adjustment edit and sends only amount on save', async () => {
    const user = userEvent.setup();
    const initialValues = {
      id: 'tx-1',
      financialAccountId: 'acc-1',
      description: 'Saldo inicial',
      date: '2026-03-15',
      creditDebitType: CreditDebitType.Credit,
      type: TransactionType.BalanceAdjustment,
      amount: 1000,
      currencyCode: 'BRL',
      category: null,
      tags: [],
    } as unknown as Transaction;

    renderInTable(
      <InlineTransactionForm
        isOpen
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        selectedAccountId="acc-1"
        initialValues={initialValues}
      />,
    );

    const form = screen.getByTestId('inline-transaction-form');
    expect(form).toHaveAttribute('data-balance-adjustment-edit', 'true');

    expect(screen.queryByPlaceholderText(/tags/i)).not.toBeInTheDocument();
    expect(screen.getByTestId('tx-credit-debit-type-readonly')).toBeInTheDocument();

    const desc = screen.getByTestId('tx-description-input');
    expect(desc).toHaveAttribute('readonly');

    await user.clear(screen.getByTestId('tx-amount-input'));
    await user.type(screen.getByTestId('tx-amount-input'), '1500');
    await user.click(screen.getByTestId('tx-save-btn'));

    expect(updateTransaction).toHaveBeenCalledWith({
      variables: {
        id: 'tx-1',
        input: { amount: 1500 },
      },
    });
  });
});

describe('InlineTransactionForm — create validation', () => {
  const createTransaction = vi.fn().mockResolvedValue({});

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(mockUseQuery).mockReturnValue({
      data: {
        financialAccounts: [{ id: 'acc-1', name: 'Conta', issuer: null, themeColor: null }],
        transactionCategories: [],
        transactionTags: [],
      },
    } as any);
    vi.mocked(mockUseMutation).mockImplementation((doc: unknown) => {
      if (doc === CREATE_TRANSACTION) {
        return [createTransaction, { loading: false }] as any;
      }
      return [vi.fn(), { loading: false }] as any;
    });
  });

  it('keeps save enabled and shows required errors when description is missing', async () => {
    const user = userEvent.setup();

    renderInTable(
      <InlineTransactionForm
        isOpen
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        selectedAccountId="acc-1"
      />,
    );

    await user.type(screen.getByTestId('tx-amount-input'), '100');
    expect(screen.getByTestId('tx-save-btn')).not.toBeDisabled();

    await user.click(screen.getByTestId('tx-save-btn'));

    expect(createTransaction).not.toHaveBeenCalled();
    expect(screen.getByTestId('tx-description-input')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('financeTransactions.validation.required')).toBeInTheDocument();
  });
});

describe('InlineTransactionForm — BILL_PAYMENT edit', () => {
  const updateTransaction = vi.fn().mockResolvedValue({});

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(mockUseQuery).mockReturnValue({
      data: {
        financialAccounts: [{ id: 'acc-1', name: 'Conta', issuer: null, themeColor: null }],
        transactionCategories: [],
        transactionTags: [],
      },
    } as any);
    vi.mocked(mockUseMutation).mockImplementation((doc: unknown) => {
      if (doc === UPDATE_TRANSACTION) {
        return [updateTransaction, { loading: false }] as any;
      }
      if (doc === APPLY_TRANSACTION_EDIT_TO_SIMILAR) {
        return [vi.fn(), { loading: false }] as any;
      }
      return [vi.fn(), { loading: false }] as any;
    });
  });

  it('disables CategorySelect for bill payment edits and keeps it enabled otherwise', () => {
    const billPaymentValues = {
      id: 'tx-bill',
      financialAccountId: 'acc-1',
      description: 'Fatura cartão',
      date: '2026-03-15',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.BillPayment,
      amount: 500,
      currencyCode: 'BRL',
      category: null,
      tags: [],
    } as unknown as Transaction;

    const { unmount: unmountBillPaymentForm } = renderInTable(
      <InlineTransactionForm
        isOpen
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        selectedAccountId="acc-1"
        initialValues={billPaymentValues}
      />,
    );

    expect(screen.getByTestId('tx-category-select')).toHaveAttribute('data-disabled', 'true');
    expect(screen.getByText('financeTransactions.category.disabledBillPayment')).toBeInTheDocument();
    unmountBillPaymentForm();
    cleanup();

    const regularValues = {
      id: 'tx-regular',
      financialAccountId: 'acc-1',
      description: 'Coffee',
      date: '2026-03-15',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.Card,
      amount: 12.34,
      currencyCode: 'BRL',
      category: null,
      tags: [],
    } as unknown as Transaction;

    renderInTable(
      <InlineTransactionForm
        isOpen
        onClose={vi.fn()}
        onSuccess={vi.fn()}
        selectedAccountId="acc-1"
        initialValues={regularValues}
      />,
    );

    expect(screen.getByTestId('tx-category-select')).not.toHaveAttribute('data-disabled', 'true');
    expect(screen.queryByText('financeTransactions.category.disabledBillPayment')).not.toBeInTheDocument();
  });

  it('clears any legacy category when saving a bill payment edit', async () => {
    const billPaymentValues = {
      ...baseTransaction,
      id: 'tx-bill',
      description: 'Fatura cartão',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.BillPayment,
      category: { id: 'cat-legacy', name: 'Legacy category' },
    } as unknown as Transaction;

    expect(resolveSubmittedCategoryId(billPaymentValues, null)).toBeNull();
    expect(resolveSubmittedCategoryId(billPaymentValues, 'cat-legacy')).toBeNull();
  });
});

describe('TransactionActionMenu — bill payment undo', () => {
  afterEach(() => {
    cleanup();
  });

  const billPaymentTransaction = {
    id: 'tx-bill',
    description: 'Fatura',
    date: '2026-03-01',
    creditDebitType: CreditDebitType.Debit,
    type: TransactionType.BillPayment,
    amount: 100,
    currencyCode: 'BRL',
    financialAccountId: 'acc-1',
    merchant: null,
    category: null,
    appliedRuleIds: [],
    tags: [],
    attachments: [],
  } as unknown as Transaction;

  const regularTransaction = {
    ...billPaymentTransaction,
    id: 'tx-regular',
    type: TransactionType.Card,
  } as unknown as Transaction;

  it('shows undo bill payment option only for bill payment transactions', () => {
    const anchorEl = document.createElement('button');
    anchorEl.style.position = 'absolute';
    anchorEl.style.top = '0';
    anchorEl.style.left = '0';
    anchorEl.style.width = '20px';
    anchorEl.style.height = '20px';
    anchorEl.getBoundingClientRect = () =>
      ({
        x: 0,
        y: 0,
        top: 0,
        left: 0,
        right: 20,
        bottom: 20,
        width: 20,
        height: 20,
        toJSON: () => ({}),
      }) as DOMRect;
    document.body.appendChild(anchorEl);

    const { unmount: unmountBillPaymentMenu } = render(
      <ThemeProvider theme={theme}>
        <TransactionActionMenu
          anchorEl={anchorEl}
          transaction={billPaymentTransaction}
          markingTransferTransactionId={null}
          markingBillPaymentTransactionId={null}
          onClose={vi.fn()}
          onEdit={vi.fn()}
          onViewAppliedRules={vi.fn()}
          onCreateRule={vi.fn()}
          onCreateRecurringPayment={vi.fn()}
          onMarkAsTransfer={vi.fn()}
          onMarkAsBillPayment={vi.fn()}
          onUnmarkBillPayment={vi.fn()}
          onDelete={vi.fn()}
        />
      </ThemeProvider>,
    );

    expect(screen.getByTestId('transaction-action-unmark-bill-payment')).toBeInTheDocument();
    unmountBillPaymentMenu();
    cleanup();

    render(
      <ThemeProvider theme={theme}>
        <TransactionActionMenu
          anchorEl={anchorEl}
          transaction={regularTransaction}
          markingTransferTransactionId={null}
          markingBillPaymentTransactionId={null}
          onClose={vi.fn()}
          onEdit={vi.fn()}
          onViewAppliedRules={vi.fn()}
          onCreateRule={vi.fn()}
          onCreateRecurringPayment={vi.fn()}
          onMarkAsTransfer={vi.fn()}
          onMarkAsBillPayment={vi.fn()}
          onUnmarkBillPayment={vi.fn()}
          onDelete={vi.fn()}
        />
      </ThemeProvider>,
    );

    expect(screen.queryByTestId('transaction-action-unmark-bill-payment')).not.toBeInTheDocument();
    anchorEl.remove();
  });
});

describe('TransactionRow — bill payment category lock', () => {
  it('does not open the category picker for bill payment rows', async () => {
    const user = userEvent.setup();
    const onOpenCategoryPicker = vi.fn();
    const billPaymentTransaction = {
      ...baseTransaction,
      id: 'tx-bill-row',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.BillPayment,
      category: { id: 'cat-1', name: 'Moradia', icon: 'home', color: '#ccc' },
    } as unknown as Transaction;

    renderInTable(
      <TransactionRow
        transaction={billPaymentTransaction}
        account={null}
        isFirst
        isLast
        isEditing={false}
        isEditFormOpen={false}
        isDeleting={false}
        updatingCategoryTransactionId={null}
        displayedCategory={billPaymentTransaction.category}
        selectedAccountId="acc-1"
        activeActionMenuTransactionId={null}
        activeActionMenuAnchorEl={null}
        callbacks={{
          onOpenCategoryPicker,
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

    expect(screen.getByLabelText('financeTransactions.category.disabledBillPayment')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Moradia'));
    expect(onOpenCategoryPicker).not.toHaveBeenCalled();
  });
});

describe('transactionTypeUtils', () => {
  it.each([
    [CreditDebitType.Debit, TransactionType.OtherExpense],
    [CreditDebitType.Credit, TransactionType.OtherIncome],
  ])('maps %s to %s', (creditDebitType, expectedType) => {
    expect(resolveRegularTransactionType(creditDebitType)).toBe(expectedType);
  });
});
