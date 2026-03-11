import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, afterEach } from 'vitest';
import { MonthSummary, MonthSummaryData } from '../MonthSummary';
import { ThemeProvider, createTheme } from '@mui/material/styles';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
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
    expect(screen.getByText(/63% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Income % (5000/8000)
    expect(screen.getByText(/38% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Expenses % (3000/8000)
  });

  it('renders with isConsolidated prop (component accepts but does not yet render tooltip)', () => {
    // MonthSummary accepts isConsolidated but does not currently render a tooltip.
    // This test verifies the component still renders correctly with the prop.
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} isConsolidated={true} />);
    expect(screen.getByText(/5\.000,00/)).toBeInTheDocument();
  });

  it('renders unavailable state with contextual message and retry action', () => {
    renderWithTheme(<MonthSummary summary={undefined} loading={false} unavailable={true} onRetry={vi.fn()} />);

    expect(screen.getByText('financeTransactions.monthSummary.unavailableTitle')).toBeInTheDocument();
    expect(screen.getByText('financeTransactions.monthSummary.unavailableDescription')).toBeInTheDocument();
    expect(screen.getByText('financeTransactions.monthSummary.retry')).toBeInTheDocument();

    const bodyText = document.body.textContent ?? '';
    expect(bodyText).not.toMatch(/[0-9][,.][0-9]{2}/);
  });

  it('calls retry callback when clicking retry button in unavailable state', () => {
    const onRetry = vi.fn();
    renderWithTheme(<MonthSummary summary={undefined} loading={false} unavailable={true} onRetry={onRetry} />);

    fireEvent.click(screen.getByText('financeTransactions.monthSummary.retry'));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders zero values when summary exists and all totals are zero', () => {
    renderWithTheme(<MonthSummary summary={zeroSummary} loading={false} />);

    const bodyText = document.body.textContent ?? '';
    expect(bodyText).toMatch(/0[,.]00/);
  });
});
