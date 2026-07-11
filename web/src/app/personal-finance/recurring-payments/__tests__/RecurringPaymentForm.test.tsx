import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { RecurringPaymentForm } from '../RecurringPaymentForm';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  CreditCardNetwork,
  FinancialAccountSubtype,
  FinancialAccountType,
  RecurringFrequency,
} from '@/lib/graphql/types/__generated__/graphql';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
  useMutation: vi.fn(),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'recurringPayments.form.drawerTitleNew': 'New recurring payment',
        'recurringPayments.form.name': 'Name',
        'recurringPayments.form.accountOptional': 'Payment account (optional)',
        'recurringPayments.form.accountSearchPlaceholder': 'Search accounts...',
        'recurringPayments.form.creditDebitType': 'Direction',
        'recurringPayments.form.expectedAmount': 'Expected amount',
        'recurringPayments.form.variableAmount': 'Variable amount',
        'recurringPayments.form.frequency': 'Frequency',
        'recurringPayments.form.category': 'Category',
        'recurringPayments.frequency.MONTHLY': 'Monthly',
        'recurringPayments.frequency.WEEKLY': 'Weekly',
        'recurringPayments.frequency.YEARLY': 'Yearly',
        'recurringPayments.frequency.CUSTOM': 'Custom',
        'financeTransactions.multipleCards': 'Multiple cards',
        'financeTransactions.accountOption.subtypes.CONTA_CORRENTE': 'Checking',
        'financeTransactions.selector.noAccountsFound': 'No accounts found',
        'financeTransactions.debit': 'Debit',
        'financeTransactions.credit': 'Credit',
      };
      return translations[key] ?? (params?.date as string | undefined) ?? key;
    },
  }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  function DrawerMock({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerMock.displayName = 'DrawerMock';

  function DrawerHeaderMock({ title }: { title: string }) {
    return <div>{title}</div>;
  }
  DrawerHeaderMock.displayName = 'DrawerHeaderMock';

  function DrawerBodyMock({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerBodyMock.displayName = 'DrawerBodyMock';

  function DrawerFooterMock({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerFooterMock.displayName = 'DrawerFooterMock';

  function DrawerActionButtonsMock() {
    return <div data-testid="drawer-action-buttons" />;
  }
  DrawerActionButtonsMock.displayName = 'DrawerActionButtonsMock';

  const Drawer = Object.assign(DrawerMock, {
    Header: DrawerHeaderMock,
    Body: DrawerBodyMock,
    Footer: DrawerFooterMock,
    ActionButtons: DrawerActionButtonsMock,
  });

  return { Drawer };
});

vi.mock('@/shared/components/ui/forms/components', () => ({
  FormStack: function FormStackMock({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  },
}));

vi.mock('@/shared/components/ui/feedback', () => ({
  ErrorAlert: () => null,
}));

vi.mock('@/shared/components/ui/forms/form', () => ({
  DatePickerField: () => <div />,
  FormTextField: ({ label, name }: { label: string; name: string }) => <input aria-label={label} name={name} />,
  MoneyInput: ({ label }: { label: string }) => <input aria-label={label} />,
  NumberField: ({ label }: { label: string }) => <input aria-label={label} />,
  SelectField: ({ label }: { label: string }) => <input aria-label={label} />,
  TextField: ({ label }: { label: string }) => <input aria-label={label} />,
}));

vi.mock('@/app/personal-finance/finance-transactions/category/CategoryPicker', () => ({
  CategoryPicker: () => null,
}));

vi.mock('@/app/personal-finance/finance-transactions/category/CategoryIcon', () => ({
  CategoryIcon: () => null,
}));

vi.mock('@/app/personal-finance/finance-transactions/transaction/MerchantSelect', () => ({
  MerchantSelect: () => <div />,
}));

vi.mock('@/shared/components/ui/icons/CardNetworkIcon', () => ({
  CardNetworkIcon: ({ network }: { network?: string | null }) => <span>{network ?? 'none'}</span>,
}));

describe('RecurringPaymentForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useQuery).mockReturnValue({
      data: {
        financialAccounts: [
          {
            id: 'bank-1',
            name: 'Conta Corrente XP',
            type: FinancialAccountType.Bank,
            subtype: FinancialAccountSubtype.ContaCorrente,
            themeColor: null,
            issuer: null,
            issuerName: null,
            cardData: null,
          },
          {
            id: 'card-1',
            name: 'Cartão XP',
            type: FinancialAccountType.Credit,
            subtype: FinancialAccountSubtype.CreditCard,
            themeColor: null,
            issuer: null,
            issuerName: null,
            cardData: {
              cardNetwork: CreditCardNetwork.Visa,
              maskedNumber: null,
              cardLevel: null,
              isMultiCard: true,
              closingDay: null,
              dueDay: null,
              creditLimit: null,
            },
          },
        ],
        transactionCategories: [],
      },
    } as any);
    vi.mocked(useMutation).mockReturnValue([vi.fn(), { loading: false, error: null }] as any);
  });

  it('uses shared AccountSelect with enriched account rows', async () => {
    const user = userEvent.setup();

    render(
      <ThemeProvider theme={createTheme()}>
        <RecurringPaymentForm
          open
          onClose={vi.fn()}
          onSaved={vi.fn()}
          recurring={null}
          fromTransaction={null}
        />
      </ThemeProvider>,
    );

    const combobox = screen.getByRole('combobox', { name: 'Payment account (optional)' });
    await user.click(combobox);

    await waitFor(() => {
      expect(screen.getByText('Conta Corrente XP')).toBeInTheDocument();
      expect(screen.getByText('Checking')).toBeInTheDocument();
      expect(screen.getByText('Cartão XP')).toBeInTheDocument();
      expect(screen.getByText('Multiple cards')).toBeInTheDocument();
    });
  });
});
