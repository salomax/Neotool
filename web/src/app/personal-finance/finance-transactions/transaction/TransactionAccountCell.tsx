import { Stack, Typography, useTheme } from '@mui/material';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import { memo } from 'react';
import { FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import type { FinancialAccount, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { Logo } from '@/shared/components/ui/primitives';
import { FINANCE_TABLE_BODY_FONT_SIZE } from '@/shared/components/ui/data-display/Table';

function isCardAccount(account: FinancialAccount): boolean {
  if (account.type === FinancialAccountType.Bank) return false;
  if (account.cardData) return true;
  return (
    account.subtype === FinancialAccountSubtype.CreditCard ||
    account.subtype === FinancialAccountSubtype.ChargeCard ||
    account.subtype === FinancialAccountSubtype.PrepaidCard ||
    account.subtype === FinancialAccountSubtype.VoucherCard
  );
}

function getCardLastFourDigits(account: FinancialAccount, transaction: Transaction): string | null {
  const raw = transaction.cardIdentifier || account.cardData?.maskedNumber || '';
  const digits = raw.replace(/\D/g, '');
  const last4 = digits.slice(-4);
  return last4.length === 4 ? last4 : null;
}

interface TransactionAccountCellProps {
  account: FinancialAccount;
  transaction: Transaction;
}

export const TransactionAccountCell = memo(function TransactionAccountCell({
  account,
  transaction,
}: TransactionAccountCellProps) {
  const theme = useTheme();
  const logoUrl = account.issuer?.logoAsset?.publicUrl;
  const showCardDetails = isCardAccount(account);
  const last4 = showCardDetails ? getCardLastFourDigits(account, transaction) : null;

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
          sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, lineHeight: 1.25 }}
          noWrap
        >
          {account.name}
        </Typography>
        {showCardDetails && (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <CardNetworkIcon network={account.cardData?.cardNetwork} size={18} />
            {last4 && (
              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                •••• {last4}
              </Typography>
            )}
          </Stack>
        )}
      </Stack>
    </Stack>
  );
});
