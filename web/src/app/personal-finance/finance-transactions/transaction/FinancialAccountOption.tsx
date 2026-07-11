import { AccountBalance as BankIcon, CreditCard as CardIcon, Wallet as WalletIcon } from '@mui/icons-material';
import { Stack, Typography } from '@mui/material';
import type { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { Logo } from '@/shared/components/ui/primitives';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { getMaskedLastFour, isCardAccount } from '../utils/accountUtils';

const BANK_SUBTYPE_LABELS: Partial<Record<FinancialAccountSubtype, string>> = {
  [FinancialAccountSubtype.ContaCorrente]: 'financeTransactions.accountOption.subtypes.CONTA_CORRENTE',
  [FinancialAccountSubtype.ContaPoupanca]: 'financeTransactions.accountOption.subtypes.CONTA_POUPANCA',
  [FinancialAccountSubtype.ContaDigital]: 'financeTransactions.accountOption.subtypes.CONTA_DIGITAL',
  [FinancialAccountSubtype.ContaSalario]: 'financeTransactions.accountOption.subtypes.CONTA_SALARIO',
  [FinancialAccountSubtype.ContaConjunta]: 'financeTransactions.accountOption.subtypes.CONTA_CONJUNTA',
  [FinancialAccountSubtype.Wallet]: 'financeTransactions.accountOption.subtypes.WALLET',
};

function renderAccountTypeIcon(type: FinancialAccountType) {
  if (type === FinancialAccountType.Bank) return <BankIcon sx={{ fontSize: '1rem' }} />;
  if (type === FinancialAccountType.Credit || type === FinancialAccountType.Payment) {
    return <CardIcon sx={{ fontSize: '1rem' }} />;
  }
  if (type === FinancialAccountType.Investment) return <WalletIcon sx={{ fontSize: '1rem' }} />;
  return <BankIcon sx={{ fontSize: '1rem' }} />;
}

function getBankSubtypeLabel(account: FinancialAccount, t: (key: string) => string): string | null {
  const key = BANK_SUBTYPE_LABELS[account.subtype];
  return key ? t(key) : null;
}

export interface FinancialAccountOptionProps {
  account: FinancialAccount;
}

export function FinancialAccountOption({ account }: FinancialAccountOptionProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const logoUrl = account.issuer?.logoAsset?.publicUrl;
  const showCardDetails = isCardAccount(account);
  const last4 = getMaskedLastFour(account.cardData?.maskedNumber);
  const showMultipleCardsLabel = !last4 && Boolean(account.cardData?.isMultiCard);
  const bankSubtypeLabel = showCardDetails ? null : getBankSubtypeLabel(account, t);

  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, width: '100%' }}>
      <Logo
        src={logoUrl || undefined}
        size="xs"
        sx={{
          fontSize: '0.75rem',
          bgcolor: logoUrl ? 'transparent' : 'grey.300',
          flexShrink: 0,
        }}
      >
        {!logoUrl &&
          (showCardDetails
            ? <CardNetworkIcon network={account.cardData?.cardNetwork} size={16} />
            : renderAccountTypeIcon(account.type))}
      </Logo>
      <Stack spacing={0.25} sx={{ minWidth: 0, flex: 1 }}>
        <Typography variant="body2" noWrap>
          {account.name}
        </Typography>
        {showCardDetails ? (
          <Stack direction="row" spacing={0.75} alignItems="center" sx={{ minWidth: 0 }}>
            <CardNetworkIcon network={account.cardData?.cardNetwork} size={16} />
            {last4 ? (
              <Typography variant="caption" color="text.secondary" noWrap>
                •••• {last4}
              </Typography>
            ) : showMultipleCardsLabel ? (
              <Typography variant="caption" color="text.secondary" noWrap>
                {t('financeTransactions.multipleCards')}
              </Typography>
            ) : null}
          </Stack>
        ) : bankSubtypeLabel ? (
          <Typography variant="caption" color="text.secondary" noWrap>
            {bankSubtypeLabel}
          </Typography>
        ) : null}
      </Stack>
    </Stack>
  );
}
