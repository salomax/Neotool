import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { TransactionFormFields } from '../TransactionFormFields';
import {
  CreditCardNetwork,
  FinancialAccountSubtype,
  FinancialAccountType,
} from '@/lib/graphql/types/__generated__/graphql';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) =>
      ({
        'financeTransactions.multipleCards': 'Multiple cards',
        'financeTransactions.accountOption.subtypes.CONTA_CORRENTE': 'Checking',
        'financeTransactions.selector.noAccountsFound': 'No accounts found',
      })[key] ?? key,
  }),
}));

vi.mock('@/app/personal-finance/finance-transactions/transaction/TypeSelector', () => ({
  TypeSelector: () => <div data-testid="type-selector" />,
}));

vi.mock('@/app/personal-finance/finance-transactions/category/CategorySelect', () => ({
  CategorySelect: () => <div data-testid="tx-category-select" />,
}));

vi.mock('@/shared/components/ui/forms/form/TagsInput', () => ({
  TagsInput: () => <div data-testid="tags-input" />,
}));

vi.mock('@/shared/components/ui/forms/form/MoneyInput', () => ({
  BaseMoneyInput: (props: any) => <input data-testid="tx-amount-input" {...props} />,
}));

vi.mock('@/shared/components/ui/icons/CardNetworkIcon', () => ({
  CardNetworkIcon: ({ network }: { network?: string | null }) => (
    <span data-testid="card-network-icon">{network ?? 'none'}</span>
  ),
}));

function renderFields() {
  const controller = {
    form: {
      description: 'Compra',
      setDescription: vi.fn(),
      isBalanceAdjustmentEdit: false,
      inputBottomBorderSx: {},
      categoryId: null,
      setCategoryId: vi.fn(),
      filteredCategories: [],
      creditDebitType: 'DEBIT',
      tags: [],
      setTags: vi.fn(),
      availableTags: [],
      accountId: 'card-1',
      setAccountId: vi.fn(),
      accounts: [
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
      amountGridColumn: 'auto',
      amount: 50,
      setAmount: vi.fn(),
      gridTemplateColumns: '1fr',
      isDateInvalidForSave: false,
    },
    save: {
      fieldErrors: {
        description: false,
        account: false,
        amount: false,
      },
      handleKeyDown: vi.fn(),
      handleTypeChange: vi.fn(),
    },
    isBillPaymentEdit: false,
    t: (key: string) =>
      ({
        'financeTransactions.accountOrCard': 'Account/Card',
        'financeTransactions.multipleCards': 'Multiple cards',
        'financeTransactions.date': 'Date',
        'financeTransactions.category.label': 'Category',
        'financeTransactions.description': 'Description',
        'financeTransactions.tags': 'Tags',
        'financeTransactions.validation.required': 'Required',
      })[key] ?? key,
  } as any;

  return render(
    <ThemeProvider theme={createTheme()}>
      <TransactionFormFields controller={controller} selectedAccountId={null} layout="drawer" showTypeSelector={false} />
    </ThemeProvider>,
  );
}

describe('TransactionFormFields', () => {
  it('shows enriched account details through AccountSelect', () => {
    renderFields();

    const combobox = screen.getByRole('combobox');
    expect(combobox).toHaveValue('Cartão XP');

    fireEvent.mouseDown(combobox);
    const option = screen.getByTestId('account-select-option-card-1');
    expect(within(option).getByText('Multiple cards')).toBeInTheDocument();
  });
});
