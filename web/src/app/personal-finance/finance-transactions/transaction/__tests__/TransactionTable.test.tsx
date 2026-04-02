import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransactionTable } from '../TransactionTable';
import { CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';

const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
};

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(() => [vi.fn(), { loading: false, error: null }]),
  useLazyQuery: vi.fn(() => [vi.fn(), { loading: false, data: null, error: null }]),
}));

vi.mock('@/shared/providers', () => ({
  useToast: () => mockToast,
  usePreferences: () => ({
    bankingUserPreferences: {
      ruleApplicationMode: 'ALWAYS_PROMPT',
    },
  }),
}));

vi.mock('../../category/CategoryPicker', () => ({
  CategoryPicker: () => null,
}));

vi.mock('../../../components/FilePreviewDialog', () => ({
  FilePreviewDialog: () => null,
}));

vi.mock('@/shared/components/ui/feedback', () => ({
  ConfirmationDialog: () => null,
}));

vi.mock('../SimilarEditDialog', () => ({
  SimilarEditDialog: () => null,
}));

describe.sequential('TransactionTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders table with accessible name and makes +N tags control focusable', () => {
    const transaction = {
      id: 'tx-1',
      description: 'Coffee',
      date: '2026-03-01',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.Card,
      amount: 12.34,
      currencyCode: 'BRL',
      financialAccountId: 'acc-1',
      merchant: null,
      category: null,
      tags: ['food', 'coffee', 'morning'],
      attachments: [],
    } as unknown as Transaction;

    render(
      <TransactionTable
        transactions={[transaction]}
        financialAccounts={[]}
        loading={false}
        hasMore={false}
        onLoadMore={vi.fn()}
        selectedAccountId="acc-1"
      />,
    );

    expect(screen.getByRole('table', { name: /Transactions table|Tabela de transações/i })).toBeInTheDocument();
    const moreTagsButton = screen.getByRole('button', { name: /View .* more tags|Ver mais .* tags/i });
    expect(moreTagsButton).toHaveTextContent('+1');
  });

  it('wires the row actions button to the menu via ARIA attributes', async () => {
    const user = userEvent.setup();
    const transaction = {
      id: 'tx-1',
      description: 'Coffee',
      date: '2026-03-01',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.Card,
      amount: 12.34,
      currencyCode: 'BRL',
      financialAccountId: 'acc-1',
      merchant: null,
      category: null,
      tags: [],
      attachments: [
        {
          id: 'att-1',
          assetId: 'asset-1',
          filename: 'receipt.pdf',
          mimeType: 'application/pdf',
          downloadUrl: 'https://example.com/receipt.pdf',
        },
      ],
    } as unknown as Transaction;

    render(
      <TransactionTable
        transactions={[transaction]}
        financialAccounts={[]}
        loading={false}
        hasMore={false}
        onLoadMore={vi.fn()}
        selectedAccountId="acc-1"
      />,
    );

    const actionsButton = screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!;
    expect(actionsButton).toHaveAttribute('aria-haspopup', 'menu');
    expect(actionsButton.id).toBe('transaction-actions-button-tx-1');

    await user.click(actionsButton);

    const menu = screen.getByRole('menu');
    expect(menu).toBeInTheDocument();
    expect(actionsButton).toHaveAttribute('aria-controls', 'finance-transactions-transaction-actions-menu');

    expect(
      screen.getByRole('menuitem', {
        name: /Mark as internal transfer|Marcar como transferência interna/i,
      }),
    ).toBeInTheDocument();
  });
});
