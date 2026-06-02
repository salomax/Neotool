import type { ReactElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme, Table, TableBody } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { InlineTransactionForm } from '../InlineTransactionForm';
import {
  APPLY_TRANSACTION_EDIT_TO_SIMILAR,
  CREATE_TRANSACTION,
  UPDATE_TRANSACTION,
} from '@/lib/graphql/operations/banking/mutations';
import { CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useMutation as mockUseMutation, useQuery as mockUseQuery, useLazyQuery as mockUseLazyQuery } from '@apollo/client/react';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
  useLazyQuery: vi.fn(() => [vi.fn(), { loading: false, data: null }]),
  useMutation: vi.fn(),
}));

vi.mock('@/shared/providers', () => ({
  usePreferences: () => ({
    bankingUserPreferences: {
      ruleApplicationMode: 'ALWAYS_PROMPT',
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

vi.mock('../../components/AttachmentSection', () => ({
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
