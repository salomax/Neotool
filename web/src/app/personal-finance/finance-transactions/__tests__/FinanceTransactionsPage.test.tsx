
import React from 'react';
import { render, screen, cleanup } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, Mock, afterEach } from 'vitest';
import FinanceTransactionsPage from '../page';
import { useAccountDisplayMode } from '../hooks/useAccountDisplayMode';
import { useQuery } from '@apollo/client/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('@/shared/hooks/ui/usePageTitle', () => ({
  usePageTitle: vi.fn(),
}));

vi.mock('@/shared/components/ui/layout/SidebarLayout', () => ({
  SidebarLayout: ({ sidebar }: { sidebar: React.ReactNode }) => (
    <div data-testid="sidebar-layout">
      <div data-testid="sidebar-content">{sidebar}</div>
    </div>
  ),
}));

vi.mock('../components/FinancialAccountSelector', () => ({
  FinancialAccountSelector: () => <div data-testid="financial-account-selector" />,
}));

vi.mock('../components/MonthYearCarousel', () => ({
  MonthYearCarousel: () => <div data-testid="month-year-carousel" />,
}));

vi.mock('../components/BillCarousel', () => ({
  BillCarousel: () => <div data-testid="bill-carousel" />,
}));

vi.mock('../components/BillSummaryHeader', () => ({
  BillSummaryHeader: () => <div data-testid="bill-summary-header" />,
}));

vi.mock('../components/MonthSummary', () => ({
  MonthSummary: () => <div data-testid="month-summary" />,
}));

vi.mock('../components/TransactionTable', () => ({
  TransactionTable: () => <div data-testid="transaction-table" />,
}));

vi.mock('../components/CreateFinancialAccountDrawer', () => ({
  CreateFinancialAccountDrawer: () => <div data-testid="create-account-drawer" />,
}));

vi.mock('../components/ImportTransactionsDrawer', () => ({
  ImportTransactionsDrawer: () => <div data-testid="import-transactions-drawer" />,
}));

vi.mock('../components/EmptyFinancialAccountsState', () => ({
  EmptyFinancialAccountsState: () => <div data-testid="empty-state" />,
}));

// Mock useAccountDisplayMode
vi.mock('../hooks/useAccountDisplayMode');

// Mock Apollo useQuery
vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

describe.sequential('FinanceTransactionsPage', () => {
  const theme = createTheme();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default useQuery mock implementation
    (useQuery as unknown as Mock).mockReturnValue({
      data: { financialAccounts: [{ id: 'acc-1', name: 'Account 1', type: FinancialAccountType.Bank }] },
      loading: false,
      refetch: vi.fn(),
      fetchMore: vi.fn(),
    });

    // Default useAccountDisplayMode mock implementation (calendar)
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
    (useQuery as any).mockReturnValue({
      data: undefined,
      loading: true,
    });

    renderPage();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders empty state when no accounts exist', () => {
    (useQuery as any).mockReturnValue({
      data: { financialAccounts: [] },
      loading: false,
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

    // Mock useQuery for FINANCIAL_ACCOUNTS to return some accounts
    // We need to handle multiple useQuery calls.
    // The first one is FINANCIAL_ACCOUNTS.
    // The others are TRANSACTIONS, MONTH_SUMMARY, etc.
    // simpler way: mockReturnValueOnce for sequential calls if order is deterministic, 
    // or use a smarter mock that checks arguments. 
    // For now, let's just make the default mock return data that satisfies all.
    (useQuery as any).mockReturnValue({
      data: { 
        financialAccounts: [{ id: 'acc-1', name: 'Bank Account', type: FinancialAccountType.Bank }],
        transactions: { edges: [] },
        monthSummary: {},
        transactionImports: []
      },
      loading: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByTestId('sidebar-layout')).toBeInTheDocument();
    expect(screen.getByTestId('month-summary')).toBeInTheDocument();
    expect(screen.queryByTestId('bill-summary-header')).not.toBeInTheDocument();
    // MonthYearCarousel is likely inside SidebarLayout or Page, let's check where it is in page.tsx
    // It's not in the sidebar prop in the snippet I read. Let's assume it's in the main content.
    // Wait, in page.tsx I saw SidebarLayout sidebar={...}.
    // I didn't see where MonthYearCarousel is used in the snippet I read (lines 101-200).
    // Let me check lines 1-100 or 200+ to find MonthYearCarousel usage.
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

    (useQuery as any).mockReturnValue({
      data: { 
        financialAccounts: [{ id: 'card-1', name: 'Credit Card', type: FinancialAccountType.Credit }],
        transactions: { edges: [] },
        transactionImports: []
      },
      loading: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByTestId('bill-summary-header')).toBeInTheDocument();
    expect(screen.queryByTestId('month-summary')).not.toBeInTheDocument();
  });
});
