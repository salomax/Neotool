import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransactionTable } from '../TransactionTable';
import { CategoryApplicableTo, CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useLazyQuery as mockUseLazyQuery } from '@apollo/client/react';

let lastRuleDrawerProps: any = null;
let lastApplyDialogProps: any = null;

const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
};

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(() => [vi.fn(), { loading: false, error: null }]),
  useLazyQuery: vi.fn(() => [vi.fn(), { loading: false, data: null, error: null }]),
  useApolloClient: vi.fn(() => ({
    readQuery: vi.fn(() => {
      throw new Error('no cache');
    }),
    query: vi.fn(async () => ({
      data: {
        transactionCategories: [
          {
            id: 'cat-transfer-own-accounts',
            parentId: 'parent-transfer-sent',
            name: 'Entre contas próprias',
            applicableTo: 'TRANSFER',
            isSystem: true,
            sortOrder: 30,
            color: null,
            icon: null,
          },
        ],
      },
    })),
  })),
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

vi.mock('../AppliedTransactionRulesDrawer', () => ({
  AppliedTransactionRulesDrawer: ({ open }: { open: boolean }) => (open ? <div>applied-rules-drawer</div> : null),
}));

vi.mock('../ApplyRuleToHistoricalDialog', () => ({
  ApplyRuleToHistoricalDialog: (props: any) => {
    lastApplyDialogProps = props;
    return props.open ? <div>apply-rule-to-history-dialog</div> : null;
  },
}));

vi.mock('../../transaction-rules/TransactionRuleDrawer', () => ({
  TransactionRuleDrawer: (props: any) => {
    lastRuleDrawerProps = props;
    return props.open ? (
      <div>
        transaction-rule-drawer
        <button type="button" onClick={() => props.onSaved({ id: 'rule-1' })}>
          save-rule
        </button>
      </div>
    ) : null;
  },
}));

describe.sequential('TransactionTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lastRuleDrawerProps = null;
    lastApplyDialogProps = null;
    vi.mocked(mockUseLazyQuery).mockImplementation(() => [vi.fn(), { loading: false, data: null, error: null }] as any);
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
      appliedRuleIds: [],
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

  it('hides category/tags controls for BALANCE_ADJUSTMENT rows', () => {
    const transaction = {
      id: 'tx-balance-adj',
      description: 'Saldo inicial',
      date: '2026-03-01',
      creditDebitType: CreditDebitType.Credit,
      type: TransactionType.BalanceAdjustment,
      amount: 1000,
      currencyCode: 'BRL',
      financialAccountId: 'acc-1',
      merchant: null,
      category: { id: 'cat-x', name: 'Should not render chip' },
      appliedRuleIds: ['rule-1'],
      tags: ['a', 'b'],
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

    expect(screen.getByTestId('transaction-row-balance-adjustment-no-category')).toHaveTextContent('—');
    expect(screen.getByTestId('transaction-row-balance-adjustment-no-tags')).toHaveTextContent('—');
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
      appliedRuleIds: [],
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

  it('row actions for BALANCE_ADJUSTMENT only lists Edit and Remove', async () => {
    const user = userEvent.setup();
    const transaction = {
      id: 'tx-ba',
      description: 'Ajuste',
      date: '2026-03-01',
      creditDebitType: CreditDebitType.Credit,
      type: TransactionType.BalanceAdjustment,
      amount: 500,
      currencyCode: 'BRL',
      financialAccountId: 'acc-1',
      merchant: null,
      category: null,
      appliedRuleIds: ['rule-1'],
      tags: [],
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

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);

    const menu = screen.getByRole('menu');
    const items = within(menu).getAllByRole('menuitem');
    expect(items).toHaveLength(2);
    expect(screen.getByTestId('transaction-action-edit')).toBeInTheDocument();
    expect(screen.getByTestId('transaction-action-delete')).toBeInTheDocument();
    expect(screen.queryByTestId('transaction-action-create-rule-from-description')).not.toBeInTheDocument();
    expect(screen.queryByTestId('transaction-action-view-applied-rules')).not.toBeInTheDocument();
  });

  it('shows the applied rules action only when the transaction has applied rules', async () => {
    const user = userEvent.setup();
    const transactionWithRules = {
      id: 'tx-with-rules',
      description: 'Coffee',
      date: '2026-03-01',
      creditDebitType: CreditDebitType.Debit,
      type: TransactionType.Card,
      amount: 12.34,
      currencyCode: 'BRL',
      financialAccountId: 'acc-1',
      merchant: null,
      category: null,
      appliedRuleIds: ['rule-1'],
      tags: [],
      attachments: [],
    } as unknown as Transaction;
    const transactionWithoutRules = {
      ...transactionWithRules,
      id: 'tx-without-rules',
      appliedRuleIds: [],
    } as Transaction;

    const { unmount } = render(
      <TransactionTable
        transactions={[transactionWithRules]}
        financialAccounts={[]}
        loading={false}
        hasMore={false}
        onLoadMore={vi.fn()}
        selectedAccountId="acc-1"
      />,
    );

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);

    expect(
      screen.getByRole('menuitem', {
        name: /View applied rules|Ver regras aplicadas/i,
      }),
    ).toBeInTheDocument();

    unmount();

    render(
      <TransactionTable
        transactions={[transactionWithoutRules]}
        financialAccounts={[]}
        loading={false}
        hasMore={false}
        onLoadMore={vi.fn()}
        selectedAccountId="acc-1"
      />,
    );

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);

    expect(
      screen.queryByRole('menuitem', {
        name: /View applied rules|Ver regras aplicadas/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('opens the applied rules drawer from the row actions menu', async () => {
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
      appliedRuleIds: ['rule-1'],
      tags: [],
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

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);
    await user.click(
      screen.getByRole('menuitem', {
        name: /View applied rules|Ver regras aplicadas/i,
      }),
    );

    expect(screen.getByText('applied-rules-drawer')).toBeInTheDocument();
  });

  it('opens the create rule drawer with initial values from the transaction description and type', async () => {
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
      appliedRuleIds: [],
      tags: [],
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

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);
    await user.click(
      screen.getByRole('menuitem', {
        name: /Criar regra a partir da descrição|Create rule from description/i,
      }),
    );

    expect(screen.getByText('transaction-rule-drawer')).toBeInTheDocument();
    expect(lastRuleDrawerProps?.initialPattern).toBe('Coffee');
    expect(lastRuleDrawerProps?.initialApplicableTo).toBe(CategoryApplicableTo.Expense);
  });

  it('does not show the apply-to-history dialog when there are no matched transactions', async () => {
    const user = userEvent.setup();
    vi.mocked(mockUseLazyQuery).mockImplementation(
      () =>
        [
          vi.fn(async () => ({ data: { transactionRuleHistoricalMatchCount: 0 } })),
          { loading: false, data: null, error: null },
        ] as any,
    );

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
      appliedRuleIds: [],
      tags: [],
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

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);
    await user.click(
      screen.getByRole('menuitem', {
        name: /Criar regra a partir da descrição|Create rule from description/i,
      }),
    );
    await user.click(screen.getByRole('button', { name: 'save-rule' }));

    await waitFor(() => expect(screen.queryByText('transaction-rule-drawer')).not.toBeInTheDocument());
    expect(screen.queryByText('apply-rule-to-history-dialog')).not.toBeInTheDocument();
  });

  it('shows the apply-to-history dialog when there are matched transactions', async () => {
    const user = userEvent.setup();
    vi.mocked(mockUseLazyQuery).mockImplementation(
      () =>
        [
          vi.fn(async () => ({ data: { transactionRuleHistoricalMatchCount: 3 } })),
          { loading: false, data: null, error: null },
        ] as any,
    );

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
      appliedRuleIds: [],
      tags: [],
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

    await user.click(screen.getAllByRole('button', { name: /Transaction actions|Ações da transação/i })[0]!);
    await user.click(
      screen.getByRole('menuitem', {
        name: /Criar regra a partir da descrição|Create rule from description/i,
      }),
    );
    await user.click(screen.getByRole('button', { name: 'save-rule' }));

    await waitFor(() => expect(lastApplyDialogProps?.open).toBe(true));
    expect(lastApplyDialogProps?.matchCount).toBe(3);
  });
});
