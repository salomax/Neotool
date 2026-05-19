import { TransactionDirectionBadge } from '@/shared/components/ui/data-display';
import { CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import type { RecurringPaymentRow } from './recurringPaymentRowModel';

type RecurringPaymentNameIconRow = Pick<RecurringPaymentRow, 'creditDebitType' | 'type' | 'merchant'>;

function recurringPaymentTransactionType(row: RecurringPaymentNameIconRow): TransactionType {
  const type = row.type as TransactionType | undefined;
  if (type && Object.values(TransactionType).includes(type)) return type;
  return row.creditDebitType === 'CREDIT' ? TransactionType.OtherIncome : TransactionType.OtherExpense;
}

export function RecurringPaymentNameIcon({ row, size }: { row: RecurringPaymentNameIconRow; size: number }) {
  return (
    <TransactionDirectionBadge
      creditDebitType={row.creditDebitType === 'CREDIT' ? CreditDebitType.Credit : CreditDebitType.Debit}
      transactionType={recurringPaymentTransactionType(row)}
      merchantLogoUrl={row.merchant?.logoAsset?.publicUrl}
      merchantName={row.merchant?.name}
      size={size}
    />
  );
}
