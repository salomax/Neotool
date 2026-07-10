import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringPaymentsSummaryPanel } from '../RecurringPaymentsSummaryPanel';
import type { RecurringPaymentRow } from '../recurringPaymentRowModel';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) =>
      values ? `${key}:${Object.values(values).join(',')}` : key,
  }),
}));

describe.sequential('RecurringPaymentsSummaryPanel', () => {
  const theme = createTheme();

  const rows: RecurringPaymentRow[] = [
    {
      id: 'row-1',
      financialAccountId: 'acc-1',
      name: 'Rent',
      creditDebitType: 'DEBIT',
      expectedAmount: 2500,
      currencyCode: 'BRL',
      frequency: RecurringFrequency.Monthly,
      remindDaysBefore: 3,
      status: RecurringPaymentStatus.Active,
      nextDueDate: '2026-07-05',
      category: { id: 'housing', name: 'Housing', color: '#123456', icon: 'home' },
    },
    {
      id: 'row-2',
      financialAccountId: 'acc-1',
      name: 'Gym',
      creditDebitType: 'DEBIT',
      expectedAmount: 150,
      currencyCode: 'BRL',
      frequency: RecurringFrequency.Weekly,
      remindDaysBefore: 3,
      status: RecurringPaymentStatus.Active,
      nextDueDate: '2026-07-11',
      category: { id: 'health', name: 'Health', color: '#654321', icon: 'favorite' },
    },
  ];

  afterEach(() => cleanup());

  function renderWithTheme(component: React.ReactNode) {
    return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
  }

  it('forwards due bucket, category, and frequency clicks from the summary', () => {
    const onDueBucketClick = vi.fn();
    const onCategoryClick = vi.fn();
    const onFrequencyClick = vi.fn();

    renderWithTheme(
      <RecurringPaymentsSummaryPanel
        rows={rows}
        onDueBucketClick={onDueBucketClick}
        onCategoryClick={onCategoryClick}
        onFrequencyClick={onFrequencyClick}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /recurringPayments\.summary\.a11y\.filterByDueBucket:recurringPayments\.summary\.overdue/ }));
    fireEvent.click(screen.getByRole('button', { name: /financeTransactions\.monthSummary\.a11y\.filterByCategory: Housing/ }));
    fireEvent.click(screen.getByRole('button', { name: /recurringPayments\.summary\.a11y\.filterByFrequency: recurringPayments\.frequency\.MONTHLY/ }));

    expect(onDueBucketClick).toHaveBeenCalledWith('overdue');
    expect(onCategoryClick).toHaveBeenCalledWith({ categoryId: 'housing', uncategorized: false });
    expect(onFrequencyClick).toHaveBeenCalledWith(RecurringFrequency.Monthly);
  });
});
