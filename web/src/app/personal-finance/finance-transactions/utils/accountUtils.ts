import type { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

type CardAwareAccount = Pick<FinancialAccount, 'type' | 'subtype' | 'cardData'>;

const CARD_ACCOUNT_SUBTYPES = new Set<FinancialAccountSubtype>([
  FinancialAccountSubtype.CreditCard,
  FinancialAccountSubtype.ChargeCard,
  FinancialAccountSubtype.PrepaidCard,
  FinancialAccountSubtype.VoucherCard,
]);

export function isCardAccount(account: CardAwareAccount): boolean {
  if (account.type === FinancialAccountType.Bank) return false;
  if (account.cardData) return true;
  return CARD_ACCOUNT_SUBTYPES.has(account.subtype);
}

export function getMaskedLastFour(maskedNumber?: string | null): string | null {
  const digits = (maskedNumber ?? '').replace(/\D/g, '').slice(-4);
  return digits.length === 4 ? digits : null;
}
