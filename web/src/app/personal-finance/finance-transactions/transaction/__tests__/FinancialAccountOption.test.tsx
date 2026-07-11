import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { FinancialAccountOption } from '../FinancialAccountOption';
import {
  CreditCardNetwork,
  FinancialAccountSubtype,
  FinancialAccountType,
  type FinancialAccount,
} from '@/lib/graphql/types/__generated__/graphql';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'financeTransactions.multipleCards': 'Multiple cards',
        'financeTransactions.accountOption.subtypes.CONTA_CORRENTE': 'Checking',
      };
      return translations[key] ?? key;
    },
  }),
}));

vi.mock('@/shared/components/ui/icons/CardNetworkIcon', () => ({
  CardNetworkIcon: ({ network }: { network?: string | null }) => (
    <span data-testid="card-network-icon">{network ?? 'none'}</span>
  ),
}));

function renderOption(account: FinancialAccount) {
  return render(
    <ThemeProvider theme={createTheme()}>
      <FinancialAccountOption account={account} />
    </ThemeProvider>,
  );
}

function makeAccount(overrides: Partial<FinancialAccount>): FinancialAccount {
  return {
    id: 'acc-1',
    name: 'Conta principal',
    type: FinancialAccountType.Bank,
    subtype: FinancialAccountSubtype.ContaCorrente,
    themeColor: null,
    issuer: null,
    issuerName: null,
    cardData: null,
    currencyCode: 'BRL',
    includeInNetWorth: true,
    includeInDashboard: true,
    currentBalance: null,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  } as FinancialAccount;
}

describe('FinancialAccountOption', () => {
  it('renders bank subtype labels when the subtype is known', () => {
    renderOption(makeAccount({ name: 'Conta Corrente XP' }));

    expect(screen.getByText('Conta Corrente XP')).toBeInTheDocument();
    expect(screen.getByText('Checking')).toBeInTheDocument();
  });

  it('omits the secondary bank label when the subtype is not mapped', () => {
    const view = renderOption(
      makeAccount({
        subtype: FinancialAccountSubtype.Brokerage,
      }),
    );

    expect(within(view.container).queryByText('Checking')).not.toBeInTheDocument();
  });

  it('renders the masked last four digits for card accounts', () => {
    renderOption(
      makeAccount({
        name: 'Cartão XP',
        type: FinancialAccountType.Credit,
        subtype: FinancialAccountSubtype.CreditCard,
        cardData: {
          cardNetwork: CreditCardNetwork.Visa,
          maskedNumber: '**** 1234',
          cardLevel: null,
          isMultiCard: false,
          closingDay: null,
          dueDay: null,
          creditLimit: null,
        } as FinancialAccount['cardData'],
      }),
    );

    expect(screen.getByText('•••• 1234')).toBeInTheDocument();
  });

  it('renders the multiple cards label for multi-card accounts', () => {
    renderOption(
      makeAccount({
        name: 'Cartão XP',
        type: FinancialAccountType.Credit,
        subtype: FinancialAccountSubtype.CreditCard,
        cardData: {
          cardNetwork: CreditCardNetwork.Visa,
          maskedNumber: null,
          cardLevel: null,
          isMultiCard: true,
          closingDay: null,
          dueDay: null,
          creditLimit: null,
        } as FinancialAccount['cardData'],
      }),
    );

    expect(screen.getByText('Multiple cards')).toBeInTheDocument();
    expect(screen.getAllByTestId('card-network-icon')).not.toHaveLength(0);
  });
});
