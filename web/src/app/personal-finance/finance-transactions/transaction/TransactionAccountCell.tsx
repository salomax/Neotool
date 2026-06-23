import { Stack, Typography, useTheme } from '@mui/material';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import { memo } from 'react';
import type { FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { Logo } from '@/shared/components/ui/primitives';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

type FinancialAccountListItem = NonNullable<FinancialAccountsQuery['financialAccounts']>[number];

function isCardAccount(account: FinancialAccountListItem): boolean {
  if (account.type === FinancialAccountType.Bank) return false;
  if (account.cardData) return true;
  return (
    account.subtype === FinancialAccountSubtype.CreditCard ||
    account.subtype === FinancialAccountSubtype.ChargeCard ||
    account.subtype === FinancialAccountSubtype.PrepaidCard ||
    account.subtype === FinancialAccountSubtype.VoucherCard
  );
}

interface CardDisplayState {
  last4: string | null;
  showMultipleCardsLabel: boolean;
}

function getCardDisplayState(account: FinancialAccountListItem, transaction: Transaction): CardDisplayState {
  const txRaw = transaction.cardIdentifier || '';
  const txDigits = txRaw.replace(/\D/g, '').slice(-4);
  if (txDigits.length === 4) {
    return { last4: txDigits, showMultipleCardsLabel: false };
  }

  const isMultiCard = account.cardData?.isMultiCard ?? false;
  if (isMultiCard) {
    return { last4: null, showMultipleCardsLabel: true };
  }

  const accountRaw = account.cardData?.maskedNumber || '';
  const accountDigits = accountRaw.replace(/\D/g, '').slice(-4);
  return {
    last4: accountDigits.length === 4 ? accountDigits : null,
    showMultipleCardsLabel: false,
  };
}

interface TransactionAccountCellProps {
  account: FinancialAccountListItem;
  transaction: Transaction;
  fontSize?: string;
  lineHeight?: number;
}

export const TransactionAccountCell = memo(function TransactionAccountCell({
  account,
  transaction,
  fontSize = '0.7rem',
  lineHeight = 1.35,
}: TransactionAccountCellProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const logoUrl = account.issuer?.logoAsset?.publicUrl;
  const showCardDetails = isCardAccount(account);
  const cardDisplay = showCardDetails ? getCardDisplayState(account, transaction) : null;

  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Logo
        src={logoUrl || undefined}
        size="sm"
        sx={{
          fontSize: '0.75rem',
          bgcolor: logoUrl ? 'transparent' : theme.palette.grey[300],
        }}
      >
        {!logoUrl && (
          account.type === FinancialAccountType.Bank
            ? <BankIcon sx={{ fontSize: '1rem' }} />
            : <CardNetworkIcon network={account.cardData?.cardNetwork} size={16} />
        )}
      </Logo>
      <Stack spacing={0} sx={{ minWidth: 0 }}>
        <Typography
          variant="body2"
          sx={{ fontSize, lineHeight }}
          noWrap
        >
          {account.name}
        </Typography>
        {showCardDetails && (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <CardNetworkIcon network={account.cardData?.cardNetwork} size={18} />
            {cardDisplay?.last4 ? (
              <Typography variant="caption" color="text.secondary" sx={{ fontSize, lineHeight }}>
                •••• {cardDisplay.last4}
              </Typography>
            ) : cardDisplay?.showMultipleCardsLabel ? (
              <Typography variant="caption" color="text.secondary" sx={{ fontSize, lineHeight }}>
                {t('financeTransactions.multipleCards')}
              </Typography>
            ) : null}
          </Stack>
        )}
      </Stack>
    </Stack>
  );
});
