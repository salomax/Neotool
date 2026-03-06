import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { BillSummaryHeader, BillSummaryData } from '../BillSummaryHeader';
import { ThemeProvider, createTheme } from '@mui/material/styles';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'financeTransactions.billSummary.billValue': 'Valor da Fatura',
        'financeTransactions.billSummary.closingDate': 'Fechamento',
        'financeTransactions.billSummary.dueDate': 'Vencimento',
        'financeTransactions.billSummary.availableLimit': 'Limite Disp.',
        'financeTransactions.billSummary.payBill': 'Pagar Fatura',
        'financeTransactions.billSummary.overdueMessage': 'Esta fatura está em atraso.',
        'financeTransactions.billSummary.billStatus.OPEN': 'Aberta',
        'financeTransactions.billSummary.billStatus.CLOSED': 'Fechada',
        'financeTransactions.billSummary.billStatus.PAID': 'Paga',
        'financeTransactions.billSummary.billStatus.OVERDUE': 'Em atraso',
      };
      return translations[key] ?? key;
    },
    i18n: { language: 'pt' },
  }),
}));

const theme = createTheme();

describe.sequential('BillSummaryHeader', () => {
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

  const mockBill: BillSummaryData = {
    totalAmount: 1500.50,
    closingDate: '2024-03-10',
    dueDate: '2024-03-25',
    status: 'OPEN',
    creditLimit: 5000,
  };

  it('renders totalAmount, dueDate, and status chip correctly', () => {
    renderWithTheme(
      <BillSummaryHeader
        bill={mockBill}
        loading={false}
      />
    );

    // Check Total Amount (formatted - split across spans via formatToParts)
    const amountElements = screen.getAllByText((_, element) =>
      (element?.textContent ?? '').includes('1.500,50')
    );
    expect(amountElements.length).toBeGreaterThan(0);

    // Check Due Date (formatted - pt-BR short format)
    expect(screen.getByText(/25\/03\/2024/)).toBeInTheDocument();

    // Check Status Chip
    expect(screen.getByText('Aberta')).toBeInTheDocument();
  });

  it('hides "Pagar fatura" when status = PAID', () => {
    const paidBill: BillSummaryData = { ...mockBill, status: 'PAID' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={paidBill}
        loading={false}
      />
    );

    expect(screen.queryByText('Pagar Fatura')).not.toBeInTheDocument();
    expect(screen.getByText('Paga')).toBeInTheDocument();
  });

  it('shows "Pagar fatura" when status = CLOSED', () => {
    const closedBill: BillSummaryData = { ...mockBill, status: 'CLOSED' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={closedBill}
        loading={false}
      />
    );

    expect(screen.getByText('Pagar Fatura')).toBeInTheDocument();
    expect(screen.getByText('Fechada')).toBeInTheDocument();
  });

  it('shows OVERDUE visual warning', () => {
    const overdueBill: BillSummaryData = { ...mockBill, status: 'OVERDUE' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={overdueBill}
        loading={false}
      />
    );

    expect(screen.getByText('Em atraso')).toBeInTheDocument();
    expect(screen.getByText('Esta fatura está em atraso.')).toBeInTheDocument();
  });

  it('calls onPayClick when "Pagar fatura" is clicked', () => {
    const onPayClick = vi.fn();
    const closedBill: BillSummaryData = { ...mockBill, status: 'CLOSED' };

    renderWithTheme(
      <BillSummaryHeader
        bill={closedBill}
        loading={false}
        onPayClick={onPayClick}
      />
    );

    fireEvent.click(screen.getByText('Pagar Fatura'));
    expect(onPayClick).toHaveBeenCalledTimes(1);
  });

  it('renders skeletons when loading is true', () => {
    renderWithTheme(
      <BillSummaryHeader
        bill={undefined}
        loading={true}
      />
    );

    // Check for skeletons - formatted amount should not be present when loading
    expect(screen.queryByText((_, el) => (el?.textContent ?? '').includes('1.500'))).toBeNull();
    
    // In this specific component implementation, if loading && !bill, it renders a different structure
    // We can check for the absence of specific text or presence of skeletons if we add data-testid
  });
});
