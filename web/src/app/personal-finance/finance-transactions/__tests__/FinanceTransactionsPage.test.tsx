
import React from 'react';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach, Mock, afterEach } from 'vitest';
import FinanceTransactionsPage from '../page';
import { useAccountDisplayMode } from '../account/useAccountDisplayMode';
import { useQuery } from '@apollo/client/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { FINANCIAL_ACCOUNTS, MONTH_SUMMARY, TRANSACTIONS, TRANSACTION_IMPORTS } from '@/lib/graphql/operations/banking/queries';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('@/shared/hooks/ui/usePageTitle', () => ({
  usePageTitle: vi.fn(),
}));

vi.mock('@/shared/components/ui/layout', () => ({
  Page: ({ children }: { children: React.ReactNode }) => <div data-testid="page">{children}</div>,
}));

vi.mock('@/shared/components/ui/layout/SidebarLayout', () => ({
  SidebarLayout: ({ sidebar, children }: { sidebar: React.ReactNode; children: React.ReactNode }) => (
    <div data-testid="sidebar-layout">
      <div data-testid="sidebar-content">{sidebar}</div>
      <div data-testid="main-content">{children}</div>
    </div>
  ),
}));

vi.mock('@/shared/components/ui/feedback', () => ({
  QueryErrorState: ({ error, onRetry }: { error: any; onRetry?: () => void }) => (
    <div data-testid="query-error">
      <div>{String(error?.message ?? error)}</div>
      {onRetry && <button onClick={onRetry}>retry</button>}
    </div>
  ),
}));

vi.mock('../account/FinancialAccountSelector', () => ({
  FinancialAccountSelector: () => <div data-testid="financial-account-selector" />,
}));

vi.mock('../summary/MonthYearCarousel', () => ({
  MonthYearCarousel: () => <div data-testid="month-year-carousel" />,
}));

vi.mock('../bill/BillCarousel', () => ({
  BillCarousel: () => <div data-testid="bill-carousel" />,
}));

vi.mock('../bill/BillSummaryHeader', () => ({
  BillSummaryHeader: () => <div data-testid="bill-summary-header" />,
}));

vi.mock('../summary/MonthSummary', () => ({
  MonthSummary: () => <div data-testid="month-summary" />,
}));

vi.mock('../transaction/TransactionTable', () => ({
  TransactionTable: () => <div data-testid="transaction-table" />,
}));

vi.mock('../account/CreateFinancialAccountDrawer', () => ({
  CreateFinancialAccountDrawer: () => <div data-testid="create-account-drawer" />,
}));

vi.mock('../transaction/ImportTransactionsDrawer', () => ({
  ImportTransactionsDrawer: () => <div data-testid="import-transactions-drawer" />,
}));

vi.mock('../bill/BillImportDrawer', () => ({
  BillImportDrawer: () => <div data-testid="import-bill-drawer" />,
}));

vi.mock('../bill/PayBillDialog', () => ({
  PayBillDialog: () => <div data-testid="pay-bill-dialog" />,
}));

vi.mock('../account/EmptyFinancialAccountsState', () => ({
  EmptyFinancialAccountsState: () => <div data-testid="empty-state" />,
}));

// Mock useAccountDisplayMode
vi.mock('../account/useAccountDisplayMode', () => ({
  useAccountDisplayMode: vi.fn(),
}));

// Mock Apollo useQuery
vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

describe.sequential('FinanceTransactionsPage', () => {
  const theme = createTheme();

  const mockRefetchFinancialAccounts = vi.fn();
  const mockRefetchTransactions = vi.fn();
  const mockRefetchMonthSummary = vi.fn();
  const mockRefetchImports = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    (useQuery as unknown as Mock).mockImplementation((query: any) => {
      if (query === FINANCIAL_ACCOUNTS) {
        return {
          data: { financialAccounts: [{ id: 'acc-1', name: 'Account 1', type: FinancialAccountType.Bank }] },
          loading: false,
          error: undefined,
          refetch: mockRefetchFinancialAccounts,
        };
      }

      if (query === TRANSACTIONS) {
        return {
          data: { transactions: { edges: [], pageInfo: { hasNextPage: false, endCursor: null } } },
          loading: false,
          error: undefined,
          refetch: mockRefetchTransactions,
          fetchMore: vi.fn(),
        };
      }

      if (query === MONTH_SUMMARY) {
        return {
          data: { monthSummary: {} },
          loading: false,
          error: undefined,
          refetch: mockRefetchMonthSummary,
        };
      }

      if (query === TRANSACTION_IMPORTS) {
        return {
          data: { transactionImports: [] },
          loading: false,
          error: undefined,
          refetch: mockRefetchImports,
        };
      }

      return {
        data: undefined,
        loading: false,
        error: undefined,
        refetch: vi.fn(),
      };
    });

    // Default useAccountDisplayMode mock implementation
    (useAccountDisplayMode as unknown as Mock).mockReturnValue({
      kind: 'bill',
      bills: [],
      selectedBill: null,
      loading: false,
      refetchBills: vi.fn(),
      setSelectedBill: vi.fn(),
    });
  });

  afterEach(() => {
    cleanup();
  });

  const renderPage = () => {
    return render(
      <ThemeProvider theme={theme}>
        <FinanceTransactionsPage />
      </ThemeProvider>,
    );
  };

  it('renders loading state initially', () => {
    (useQuery as unknown as Mock).mockImplementation((query: any) => {
      if (query === FINANCIAL_ACCOUNTS) {
        return { data: undefined, loading: true, error: undefined, refetch: mockRefetchFinancialAccounts };
      }
      return {
        data: undefined,
        loading: false,
        error: undefined,
        refetch: vi.fn(),
        fetchMore: vi.fn(),
      };
    });

    renderPage();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders empty state when no accounts exist', () => {
    (useQuery as unknown as Mock).mockImplementation((query: any) => {
      if (query === FINANCIAL_ACCOUNTS) {
        return {
          data: { financialAccounts: [] },
          loading: false,
          error: undefined,
          refetch: mockRefetchFinancialAccounts,
        };
      }

      if (query === TRANSACTIONS) {
        return {
          data: { transactions: { edges: [], pageInfo: { hasNextPage: false, endCursor: null } } },
          loading: false,
          error: undefined,
          refetch: mockRefetchTransactions,
          fetchMore: vi.fn(),
        };
      }

      if (query === MONTH_SUMMARY) {
        return { data: { monthSummary: {} }, loading: false, error: undefined, refetch: mockRefetchMonthSummary };
      }

      if (query === TRANSACTION_IMPORTS) {
        return { data: { transactionImports: [] }, loading: false, error: undefined, refetch: mockRefetchImports };
      }

      return { data: undefined, loading: false, error: undefined, refetch: vi.fn() };
    });

    renderPage();
    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
  });

  it('renders calendar mode components (MonthSummary) when in calendar mode', () => {
    // Mock useAccountDisplayMode to return calendar mode
    (useAccountDisplayMode as any).mockReturnValue({
      kind: 'calendar',
      month: 3,
      year: 2024,
      setMonth: vi.fn(),
      setYear: vi.fn(),
    });

    renderPage();

    expect(screen.getByTestId('sidebar-layout')).toBeInTheDocument();
    expect(screen.getByTestId('month-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('bill-summary-header')).not.toBeInTheDocument();
    expect(screen.getByTestId('month-year-carousel')).toBeInTheDocument();
  });

  it('renders bill mode components (BillSummaryHeader) when in bill mode', () => {
    // Mock useAccountDisplayMode to return bill mode
    (useAccountDisplayMode as any).mockReturnValue({
      kind: 'bill',
      selectedBill: { id: 'bill-1', totalAmount: 100, status: 'OPEN', dueDate: '2024-03-10' },
      bills: [],
      loading: false,
      refetchBills: vi.fn(),
    });

    renderPage();

    expect(screen.getByTestId('bill-summary-header')).toBeInTheDocument();
    expect(screen.getByTestId('bill-carousel')).toBeInTheDocument();
    expect(screen.queryByTestId('month-summary')).not.toBeInTheDocument();
  });

  it('shows a retryable error state when transactions query fails with no data', async () => {
    const user = userEvent.setup();

    (useAccountDisplayMode as any).mockReturnValue({
      kind: 'calendar',
      month: 3,
      year: 2024,
      setMonth: vi.fn(),
      setYear: vi.fn(),
    });

    (useQuery as unknown as Mock).mockImplementation((query: any) => {
      if (query === FINANCIAL_ACCOUNTS) {
        return {
          data: { financialAccounts: [{ id: 'acc-1', name: 'Account 1', type: FinancialAccountType.Bank }] },
          loading: false,
          error: undefined,
          refetch: mockRefetchFinancialAccounts,
        };
      }

      if (query === TRANSACTIONS) {
        return {
          data: undefined,
          loading: false,
          error: new Error('Failed to load transactions'),
          refetch: mockRefetchTransactions,
          fetchMore: vi.fn(),
        };
      }

      if (query === MONTH_SUMMARY) {
        return { data: { monthSummary: {} }, loading: false, error: undefined, refetch: mockRefetchMonthSummary };
      }

      if (query === TRANSACTION_IMPORTS) {
        return { data: { transactionImports: [] }, loading: false, error: undefined, refetch: mockRefetchImports };
      }

      return { data: undefined, loading: false, error: undefined, refetch: vi.fn() };
    });

    renderPage();

    expect(screen.getByTestId('query-error')).toBeInTheDocument();
    expect(screen.getByText('Failed to load transactions')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'retry' }));
    expect(mockRefetchTransactions).toHaveBeenCalled();
  });
});
