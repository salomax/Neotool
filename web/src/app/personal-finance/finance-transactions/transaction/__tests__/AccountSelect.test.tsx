import type { ReactElement } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { AccountSelect } from '../AccountSelect';
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
        'financeTransactions.selector.noAccountsFound': 'No accounts found',
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

function renderSelect(ui: ReactElement) {
  return render(<ThemeProvider theme={createTheme()}>{ui}</ThemeProvider>);
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

describe('AccountSelect', () => {
  it('renders bank subtype in autocomplete options', () => {
    const view = renderSelect(
      <AccountSelect
        value="bank-1"
        accounts={[
          makeAccount({
            id: 'bank-1',
            name: 'Conta Corrente XP',
          }),
        ]}
        isReadonly={false}
        onChange={vi.fn()}
        inputBottomBorderSx={{}}
        label="Account"
        dataTestId="account-select-root"
      />,
    );

    expect(view.getByTestId('account-select-root')).toHaveStyle({ width: '100%' });

    const combobox = within(view.container).getByRole('combobox');
    expect(combobox).toHaveValue('Conta Corrente XP');

    fireEvent.mouseDown(combobox);
    const option = screen.getByTestId('account-select-option-bank-1');
    expect(within(option).getByText('Conta Corrente XP')).toBeInTheDocument();
    expect(within(option).getByText('Checking')).toBeInTheDocument();
  });

  it('renders multiple cards label for multi-card options', () => {
    const view = renderSelect(
      <AccountSelect
        value="card-1"
        accounts={[
          makeAccount({
            id: 'card-1',
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
            } as any,
          }),
        ]}
        isReadonly={false}
        onChange={vi.fn()}
        inputBottomBorderSx={{}}
        label="Account"
        dataTestId="account-select-root"
      />,
    );

    const combobox = within(view.container).getByRole('combobox');
    expect(combobox).toHaveValue('Cartão XP');

    fireEvent.mouseDown(combobox);
    const option = screen.getByTestId('account-select-option-card-1');
    expect(within(option).getByText('Multiple cards')).toBeInTheDocument();
  });
});
