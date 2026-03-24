import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { BillCarousel, CreditCardBillItem } from '../BillCarousel';
import { ThemeProvider, createTheme } from '@mui/material/styles';

const theme = createTheme();

// Mock i18n
vi.mock('@/shared/i18n', () => ({
  i18n: {
    language: 'pt-BR',
  },
  useTranslation: () => ({
    t: (key: string, options?: { month?: string }) =>
      key === 'financeTransactions.billCarousel.title' && options?.month
        ? `${options.month}`
        : key,
  }),
}));

describe.sequential('BillCarousel', () => {
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

  const mockBills: CreditCardBillItem[] = [
    {
      id: 'bill-1', // Newest (April)
      closingDate: '2024-04-10',
      dueDate: '2024-04-20',
      totalAmount: 100,
      status: 'OPEN',
    },
    {
      id: 'bill-2', // Middle (March)
      closingDate: '2024-03-10',
      dueDate: '2024-03-20',
      totalAmount: 200,
      status: 'CLOSED',
    },
    {
      id: 'bill-3', // Oldest (February)
      closingDate: '2024-02-10',
      dueDate: '2024-02-20',
      totalAmount: 300,
      status: 'PAID',
    },
  ];

  it('selects OPEN bill by default when selectedBillId is null', () => {
    renderWithTheme(
      <BillCarousel
        bills={mockBills}
        selectedBillId={null}
        onSelectBill={vi.fn()}
      />
    );

    // Bill 1 (OPEN) should be selected.
    // April 2024
    expect(screen.getByText(/abr\/2024/i)).toBeInTheDocument();
  });

  it('navigates to previous bill (older) when clicking left arrow', () => {
    const onSelectBill = vi.fn();
    // Start at bill-2 (March)
    renderWithTheme(
      <BillCarousel
        bills={mockBills}
        selectedBillId="bill-2"
        onSelectBill={onSelectBill}
      />
    );

    // Left arrow -> Older -> February (bill-3)
    fireEvent.click(screen.getByRole('button', { name: 'financeTransactions.billCarousel.prevAriaLabel' }));
    
    expect(onSelectBill).toHaveBeenCalledWith('bill-3');
  });

  it('navigates to next bill (newer) when clicking right arrow', () => {
    const onSelectBill = vi.fn();
    // Start at bill-2 (March)
    renderWithTheme(
      <BillCarousel
        bills={mockBills}
        selectedBillId="bill-2"
        onSelectBill={onSelectBill}
      />
    );

    // Right arrow -> Newer -> April (bill-1)
    fireEvent.click(screen.getByRole('button', { name: 'financeTransactions.billCarousel.nextAriaLabel' }));
    
    expect(onSelectBill).toHaveBeenCalledWith('bill-1');
  });

  it('disables left arrow at oldest bill', () => {
    // Start at bill-3 (February, oldest)
    renderWithTheme(
      <BillCarousel
        bills={mockBills}
        selectedBillId="bill-3"
        onSelectBill={vi.fn()}
      />
    );

    const leftArrow = screen.getByRole('button', { name: 'financeTransactions.billCarousel.prevAriaLabel' });
    expect(leftArrow).toBeDisabled();
  });

  it('disables right arrow at newest bill', () => {
    // Start at bill-1 (April, newest)
    renderWithTheme(
      <BillCarousel
        bills={mockBills}
        selectedBillId="bill-1"
        onSelectBill={vi.fn()}
      />
    );

    const rightArrow = screen.getByRole('button', { name: 'financeTransactions.billCarousel.nextAriaLabel' });
    expect(rightArrow).toBeDisabled();
  });
});
