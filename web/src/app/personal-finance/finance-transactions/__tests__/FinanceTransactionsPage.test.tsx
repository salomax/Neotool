import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import FinanceTransactionsPage from '../page';
import { useQuery } from '@apollo/client/react';
import { extractErrorMessage } from '@/shared/utils/error';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => `pf:${key}`,
    tCommon: (key: string) => `common:${key}`,
  }),
  i18n: {
    language: 'pt-BR',
  },
}));

vi.mock('@/shared/utils/error', () => ({
  extractErrorMessage: vi.fn((_: unknown, defaultMessage: string) => defaultMessage),
}));

vi.mock('@/shared/hooks/ui/usePageTitle', () => ({
  usePageTitle: () => undefined,
}));

vi.mock('@/shared/components/ui/layout/SidebarLayout', () => ({
  SidebarLayout: ({ sidebar, children }: { sidebar: React.ReactNode; children: React.ReactNode }) => (
    <div>
      <div>{sidebar}</div>
      <div>{children}</div>
    </div>
  ),
}));

vi.mock('@/shared/components/ui/layout', () => ({
  Page: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('../account/FinancialAccountSelector', () => ({
  FinancialAccountSelector: ({ onSelectAccount }: { onSelectAccount: (id: string) => void }) => {
    React.useEffect(() => {
      onSelectAccount('acc-1');
    }, [onSelectAccount]);
    return <div>selector</div>;
  },
}));

vi.mock('../account/EmptyFinancialAccountsState', () => ({
  EmptyFinancialAccountsState: () => <div>empty</div>,
}));

vi.mock('../account/CreateFinancialAccountDrawer', () => ({
  CreateFinancialAccountDrawer: () => null,
}));

vi.mock('../transaction/ImportTransactionsDrawer', () => ({
  ImportTransactionsDrawer: () => null,
}));

vi.mock('../bill/BillImportDrawer', () => ({
  BillImportDrawer: () => null,
}));

vi.mock('../bill/PayBillDialog', () => ({
  PayBillDialog: () => null,
}));

vi.mock('../transaction/TransactionTable', () => ({
  TransactionTable: () => <div>table</div>,
}));

vi.mock('../summary/MonthYearCarousel', () => ({
  MonthYearCarousel: () => <div>carousel</div>,
}));

vi.mock('../summary/MonthSummary', () => ({
  MonthSummary: () => <div>month-summary</div>,
}));

vi.mock('../account/useAccountDisplayMode', () => ({
  useAccountDisplayMode: () => ({
    kind: 'calendar',
    month: 3,
    year: 2024,
    setMonth: vi.fn(),
    setYear: vi.fn(),
  }),
}));

describe.sequential('FinanceTransactionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useQuery as unknown as { mockImplementation: (fn: any) => void }).mockImplementation((_doc: unknown, opts?: any) => {
      const vars = opts?.variables as Record<string, unknown> | undefined;
      const skip = opts?.skip === true;

      if (!vars) {
        return {
          data: {
            financialAccounts: [
              {
                id: 'acc-1',
                type: FinancialAccountType.Bank,
              },
            ],
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        };
      }

      if (skip) {
        return {
          data: null,
          loading: false,
          error: undefined,
          refetch: vi.fn(),
          fetchMore: vi.fn(),
        };
      }

      if ('first' in vars) {
        return {
          data: {
            transactions: {
              edges: [],
              pageInfo: { hasNextPage: false, endCursor: null },
            },
          },
          loading: false,
          error: undefined,
          fetchMore: vi.fn(),
          refetch: vi.fn(),
        };
      }

      if ('month' in vars || 'year' in vars) {
        return {
          data: {
            monthSummary: {
              year: 2024,
              month: 3,
              totalIncome: 0,
              totalExpenses: 0,
              closingBalance: 0,
              openingBalance: 0,
              transfersIn: 0,
              transfersOut: 0,
              projectedClosingBalance: 0,
              isCurrentMonth: true,
              expensesByCategory: [],
              incomeByCategory: [],
              tagsSummary: [],
            },
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        };
      }

      return {
        data: null,
        loading: false,
        error: new Error('boom'),
        refetch: vi.fn(),
      };
    });
  });

  it('uses common translations for retry and default error message', async () => {
    render(<FinanceTransactionsPage />);

    await waitFor(() => {
      expect(screen.getByText('common:errors.default')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: 'common:cardGrid.retry' })).toBeInTheDocument();
    expect(extractErrorMessage).toHaveBeenCalledWith(expect.any(Error), 'common:errors.default');
  });

  it('passes the search text to the transactions query', async () => {
    render(<FinanceTransactionsPage />);

    fireEvent.change(
      screen.getByPlaceholderText('pf:financeTransactions.transactionRules.searchPlaceholder'),
      { target: { value: 'rent' } },
    );

    await waitFor(() => {
      expect(useQuery).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({
          variables: expect.objectContaining({
            search: 'rent',
          }),
        }),
      );
    });
  });
});
