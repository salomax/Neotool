import { CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';

export function resolveRegularTransactionType(
  creditDebitType: CreditDebitType | 'TRANSFER_IN' | 'TRANSFER_OUT',
): TransactionType {
  return creditDebitType === CreditDebitType.Credit
    ? TransactionType.OtherIncome
    : TransactionType.OtherExpense;
}
