import { Theme } from '@mui/material';

export type BillStatus = 'OPEN' | 'CLOSED' | 'PAID' | 'OVERDUE';

export interface CreditCardBill {
  id: string;
  financialAccountId: string;
  closingDate: string;
  dueDate: string;
  referenceMonth: string;
  totalAmount: number;
  minimumPayment?: number | null;
  status: BillStatus;
  createdAt: string;
  updatedAt: string;
}

const BILL_STATUS_LABELS: Record<BillStatus, string> = {
  OPEN: 'ABERTA',
  CLOSED: 'FECHADA',
  PAID: 'PAGA',
  OVERDUE: 'EM ATRASO',
};

export function getBillStatusLabel(status: BillStatus): string {
  return BILL_STATUS_LABELS[status] ?? '-';
}

export function getBillStatusColor(status: BillStatus, theme: Theme) {
  switch (status) {
    case 'OPEN':    return theme.palette.success;
    case 'CLOSED':  return theme.palette.warning;
    case 'PAID':    return theme.palette.info;
    case 'OVERDUE': return theme.palette.error;
    default:        return theme.palette.grey as unknown as typeof theme.palette.success;
  }
}
