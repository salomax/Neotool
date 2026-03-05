
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { BillSummaryHeader, BillSummaryData } from '../BillSummaryHeader';
import { ThemeProvider, createTheme } from '@mui/material/styles';

const theme = createTheme();

describe('BillSummaryHeader', () => {
  let container: HTMLElement;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
  });

  afterEach(() => {
    cleanup();
    if (container && container.parentNode) {
      container.parentNode.removeChild(container);
    }
    container = null as any;
  });

  const renderWithTheme = (component: React.ReactNode) => {
    return render(
      <ThemeProvider theme={theme}>
        {component}
      </ThemeProvider>,
      { container }
    );
  };

  const mockBill: BillSummaryData = {
    totalAmount: 1500.50,
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

    // Check Total Amount (formatted)
    expect(screen.getByText('R$ 1.500,50')).toBeInTheDocument();

    // Check Due Date (formatted)
    expect(screen.getByText(/25 de mar\. de 2024/i)).toBeInTheDocument();

    // Check Status Chip
    expect(screen.getByText('ABERTA')).toBeInTheDocument();
  });

  it('hides "Pagar fatura" when status = PAID', () => {
    const paidBill: BillSummaryData = { ...mockBill, status: 'PAID' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={paidBill}
        loading={false}
      />
    );

    expect(screen.queryByText('Pagar fatura')).not.toBeInTheDocument();
    expect(screen.getByText('PAGA')).toBeInTheDocument();
  });

  it('shows "Pagar fatura" when status = CLOSED', () => {
    const closedBill: BillSummaryData = { ...mockBill, status: 'CLOSED' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={closedBill}
        loading={false}
      />
    );

    expect(screen.getByText('Pagar fatura')).toBeInTheDocument();
    expect(screen.getByText('FECHADA')).toBeInTheDocument();
  });

  it('shows OVERDUE visual warning', () => {
    const overdueBill: BillSummaryData = { ...mockBill, status: 'OVERDUE' };
    
    renderWithTheme(
      <BillSummaryHeader
        bill={overdueBill}
        loading={false}
      />
    );

    expect(screen.getByText('EM ATRASO')).toBeInTheDocument();
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

    fireEvent.click(screen.getByText('Pagar fatura'));
    expect(onPayClick).toHaveBeenCalledTimes(1);
  });

  it('renders skeletons when loading is true', () => {
    renderWithTheme(
      <BillSummaryHeader
        bill={undefined}
        loading={true}
      />
    );

    // Check for skeletons (MUI Skeleton usually has class MuiSkeleton-root)
    // Or just check that text is not present
    expect(screen.queryAllByText('R$ 1.500,50')).toHaveLength(0);
    
    // In this specific component implementation, if loading && !bill, it renders a different structure
    // We can check for the absence of specific text or presence of skeletons if we add data-testid
  });
});
