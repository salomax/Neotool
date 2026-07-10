'use client';

import dayjs from 'dayjs';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { CategoryApplicableTo, CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionDrawer } from '../finance-transactions/transaction/TransactionDrawer';
import type {
  TransactionCreateSubmitInput,
  TransactionSuccessPayload,
} from '../finance-transactions/transaction/transactionFormShared';
import type { WishlistItemRow } from './wishlistRowModel';

type PurchaseWishlistTransactionDrawerProps = {
  row: WishlistItemRow | null;
  onClose: () => void;
  onRefresh?: () => void;
  onSuccess: (payload?: TransactionSuccessPayload) => void | Promise<void>;
  onCreateSubmit: (input: TransactionCreateSubmitInput) => Promise<TransactionSuccessPayload | void>;
};

export function PurchaseWishlistTransactionDrawer({
  row,
  onClose,
  onRefresh,
  onSuccess,
  onCreateSubmit,
}: PurchaseWishlistTransactionDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  if (!row) {
    return null;
  }

  return (
    <TransactionDrawer
      isOpen
      onClose={onClose}
      onSuccess={onSuccess}
      onRefresh={onRefresh}
      selectedAccountId={null}
      fixedCreditDebitType={CreditDebitType.Debit}
      showTypeSelector={false}
      initialDraft={{
        creditDebitType: CreditDebitType.Debit,
        amount: row.estimatedPrice ?? undefined,
        description: row.name,
        date: dayjs().format('YYYY-MM-DD'),
        categoryId:
          row.category &&
          (row.category.applicableTo === CategoryApplicableTo.Expense ||
            row.category.applicableTo === CategoryApplicableTo.Any)
            ? row.category.id
            : null,
      }}
      title={t('purchaseWishlist.purchaseDialog.transactionDrawerTitle')}
      ariaLabel={t('purchaseWishlist.purchaseDialog.transactionDrawerTitle')}
      submitText={t('purchaseWishlist.purchaseDialog.confirmWithTransaction')}
      onCreateSubmit={onCreateSubmit}
    />
  );
}
