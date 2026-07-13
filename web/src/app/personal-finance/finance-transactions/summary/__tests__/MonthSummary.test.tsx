import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, afterEach } from 'vitest';
import { MonthSummary, MonthSummaryData } from '../MonthSummary';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    tCommon: (key: string) => key,
  }),
}));

vi.mock('@/shared/utils/error', () => ({
  extractErrorMessage: vi.fn(() => 'friendly error'),
}));

describe.sequential('MonthSummary', () => {
  const theme = createTheme();

  const mockSummary: MonthSummaryData = {
    year: 2024,
    month: 3,
    totalIncome: 5000,
    totalExpenses: 3000,
    closingBalance: 2000,
    openingBalance: 0,
    transfersIn: 0,
    transfersOut: 0,
    projectedClosingBalance: 2000,
    isCurrentMonth: true,
  };
  const zeroSummary: MonthSummaryData = {
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
  };

  afterEach(() => {
    cleanup();
  });

  const renderWithTheme = (component: React.ReactNode) => {
    return render(
      <ThemeProvider theme={theme}>
        {component}
      </ThemeProvider>,
    );
  };

  it('renders loading state', () => {
    renderWithTheme(<MonthSummary summary={undefined} loading={true} />);
    // MUI Skeleton has no semantic role; verify loading UI shows the section label
    expect(screen.getByText('financeTransactions.monthSummary.balance')).toBeInTheDocument();
  });

  it('renders summary data correctly', () => {
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} />);
    
    // Check for formatted currency values - Intl format varies by locale (pt-BR: 5.000,00 vs en: 5,000.00)
    const bodyText = document.body.textContent ?? '';
    expect(bodyText).toMatch(/5[,.]000[,.]00/); // Income
    expect(bodyText).toMatch(/3[,.]000[,.]00/); // Expenses
    expect(bodyText).toMatch(/2[,.]000[,.]00/); // Balance
    
    // Check for percentage text (mock returns key as-is)
    expect(screen.getByText(/63% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Income % (rounded)
    expect(screen.getByText(/37% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Expenses % (100 − income %)
  });

  it('renders consolidated hint when isConsolidated is true', () => {
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} isConsolidated={true} />);
    expect(screen.getByText(/5\.000,00/)).toBeInTheDocument();
  });

  it('renders unavailable state with contextual message and retry action', () => {
    renderWithTheme(<MonthSummary summary={undefined} loading={false} error={new Error('test')} onRetry={vi.fn()} />);

    expect(screen.getByText('financeTransactions.monthSummary.unavailableTitle')).toBeInTheDocument();
    expect(screen.getByText('friendly error')).toBeInTheDocument();
    expect(screen.getByText('financeTransactions.monthSummary.retry')).toBeInTheDocument();

    const bodyText = document.body.textContent ?? '';
    expect(bodyText).not.toMatch(/[0-9][,.][0-9]{2}/);
  });

  it('calls retry callback when clicking retry button in unavailable state', () => {
    const onRetry = vi.fn();
    renderWithTheme(<MonthSummary summary={undefined} loading={false} error={new Error('test')} onRetry={onRetry} />);

    fireEvent.click(screen.getByText('financeTransactions.monthSummary.retry'));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders zero values when summary exists and all totals are zero', () => {
    renderWithTheme(<MonthSummary summary={zeroSummary} loading={false} />);

    const bodyText = document.body.textContent ?? '';
    expect(bodyText).toMatch(/0[,.]00/);
  });

  it('renders negative closing balance with error color', () => {
    renderWithTheme(
      <MonthSummary summary={{ ...mockSummary, closingBalance: -1500 }} loading={false} />,
    );

    const balanceEl = screen.getByTestId('month-summary-closing-balance');
    expect(balanceEl).toHaveStyle({ color: theme.palette.error.main });
  });

  it('renders positive closing balance with primary text color', () => {
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} />);

    const balanceEl = screen.getByTestId('month-summary-closing-balance');
    expect(balanceEl).toHaveStyle({ color: theme.palette.text.primary });
  });

  it('renders tag summaries and forwards tag clicks', () => {
    const onTagClick = vi.fn();
    renderWithTheme(
      <MonthSummary
        summary={{
          ...mockSummary,
          tagsSummary: [{ tag: 'Weekend', totalAmount: 300, transactionCount: 2, percentage: 75 }],
        }}
        loading={false}
        expensesByCategory={[]}
        incomeByCategory={[]}
        tagsSummary={[{ tag: 'Weekend', totalAmount: 300, transactionCount: 2, percentage: 75 }]}
        onTagClick={onTagClick}
      />,
    );

    expect(screen.getByText('financeTransactions.monthSummary.tagsSummary')).toBeInTheDocument();
    expect(screen.getByText('#Weekend')).toBeInTheDocument();

    fireEvent.click(screen.getByText('#Weekend'));
    expect(onTagClick).toHaveBeenCalledWith('Weekend');
  });

  it('exposes accessible names for interactive summary rows', () => {
    const onCategoryClick = vi.fn();
    const onTagClick = vi.fn();
    const onCreditDebitTypeClick = vi.fn();
    renderWithTheme(
      <MonthSummary
        summary={mockSummary}
        loading={false}
        expensesByCategory={[
          {
            category: { id: 'cat-1', name: 'Mercado', icon: 'shopping_cart', color: '#2E7D32' },
            uncategorized: false,
            totalAmount: 200,
            transactionCount: 3,
            percentage: 40,
          },
        ]}
        incomeByCategory={[]}
        tagsSummary={[{ tag: 'Weekend', totalAmount: 300, transactionCount: 2, percentage: 75 }]}
        onCategoryClick={onCategoryClick}
        onTagClick={onTagClick}
        onCreditDebitTypeClick={onCreditDebitTypeClick}
      />,
    );

    expect(
      screen.getByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.income/,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByCategory: Mercado/,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByTag: #Weekend/,
      }),
    ).toBeInTheDocument();
  });

  it('renders uncategorized as a separate category block and forwards the explicit uncategorized filter', () => {
    const onCategoryClick = vi.fn();
    renderWithTheme(
      <MonthSummary
        summary={mockSummary}
        loading={false}
        expensesByCategory={[
          {
            category: { id: 'cat-1', name: 'Mercado', icon: 'shopping_cart', color: '#2E7D32' },
            uncategorized: false,
            totalAmount: 200,
            transactionCount: 3,
            percentage: 40,
          },
          {
            category: null,
            uncategorized: true,
            totalAmount: 300,
            transactionCount: 2,
            percentage: 60,
          },
        ]}
        incomeByCategory={[]}
        tagsSummary={[]}
        onCategoryClick={onCategoryClick}
      />,
    );

    expect(screen.getByText('financeTransactions.monthSummary.uncategorized')).toBeInTheDocument();

    fireEvent.click(screen.getByText('financeTransactions.monthSummary.uncategorized'));
    expect(onCategoryClick).toHaveBeenCalledWith({ categoryId: null, uncategorized: true });
  });

  it('locks income and expenses when transfers filter is active', () => {
    const onCreditDebitTypeClick = vi.fn();
    const onTransferClick = vi.fn();
    renderWithTheme(
      <MonthSummary
        summary={{ ...mockSummary, transfersIn: 100, transfersOut: 50 }}
        loading={false}
        onCreditDebitTypeClick={onCreditDebitTypeClick}
        onTransferClick={onTransferClick}
        activeTransferFilter={true}
      />,
    );

    expect(
      screen.queryByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.income/,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.expenses/,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.transfersIn/,
      }),
    ).toBeInTheDocument();
  });

  it('locks transfers when income filter is active', () => {
    const onCreditDebitTypeClick = vi.fn();
    const onTransferClick = vi.fn();
    renderWithTheme(
      <MonthSummary
        summary={{ ...mockSummary, transfersIn: 100, transfersOut: 50 }}
        loading={false}
        onCreditDebitTypeClick={onCreditDebitTypeClick}
        activeCreditDebitTypeFilter={CreditDebitType.Credit}
        onTransferClick={onTransferClick}
      />,
    );

    expect(
      screen.getByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.income/,
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /financeTransactions\.monthSummary\.a11y\.filterByMetric financeTransactions\.monthSummary\.transfersIn/,
      }),
    ).not.toBeInTheDocument();
  });
});
