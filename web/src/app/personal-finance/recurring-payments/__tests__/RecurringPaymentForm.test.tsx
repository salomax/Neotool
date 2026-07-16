import React from 'react';
import dayjs from 'dayjs';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
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
        'recurringPayments.form.firstDueDate': 'Next due date',
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
        'actions.cancel': 'Cancel',
        'actions.save': 'Save',
        'financeTransactions.actions.saving': 'Saving',
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

  function DrawerActionButtonsMock({
    cancelText,
    submitText,
    onCancel,
    submitForm,
    submitDisabled,
  }: {
    cancelText: string;
    submitText: string;
    onCancel: () => void;
    submitForm?: string;
    submitDisabled?: boolean;
  }) {
    return (
      <div data-testid="drawer-action-buttons">
        <button type="button" onClick={onCancel}>
          {cancelText}
        </button>
        <button type="submit" form={submitForm} disabled={submitDisabled}>
          {submitText}
        </button>
      </div>
    );
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

const datePickerFieldSpy = vi.fn();

vi.mock('@/shared/components/ui/forms/form', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');

  return {
    DatePickerField: ({
      name,
      label,
      minDate,
      maxDate,
      onErrorChange,
    }: {
      name: string;
      label?: string;
      minDate?: Date;
      maxDate?: Date;
      onErrorChange?: (reason: string | null) => void;
    }) => {
      const { control } = rhf.useFormContext();
      const { field } = rhf.useController({ name, control });
      datePickerFieldSpy({ name, minDate, maxDate, onErrorChange });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type="date"
            value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00.000Z`) : null)
            }
          />
          <button type="button" onClick={() => onErrorChange?.('maxDate')}>
            Trigger due date max error
          </button>
        </div>
      );
    },
    FormTextField: ({ label, name }: { label: string; name: string }) => {
      const { register } = rhf.useFormContext();
      return <input aria-label={label} {...register(name)} />;
    },
    MoneyInput: ({ label }: { label: string }) => <input aria-label={label} />,
    NumberField: ({ label }: { label: string }) => <input aria-label={label} />,
    SelectField: ({ label }: { label: string }) => <input aria-label={label} />,
    TextField: ({ label }: { label: string }) => <input aria-label={label} />,
  };
});

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
  const createRecurringMock = vi.fn();
  const expectedMinDate = dayjs().startOf('day').toDate();
  const expectedMaxDate = dayjs().startOf('day').add(3, 'year').toDate();
  const outOfRangeFutureDate = dayjs(expectedMaxDate).add(1, 'day').format('YYYY-MM-DD');

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
    vi.mocked(useMutation).mockImplementation((mutation: any) => {
      const operationName = mutation?.definitions?.[0]?.name?.value;
      if (operationName === 'CreateRecurringPayment') {
        return [createRecurringMock, { loading: false, error: null }] as any;
      }
      return [vi.fn(), { loading: false, error: null }] as any;
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the recurring payment form shell', () => {
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

    expect(screen.getByRole('form', { name: 'New recurring payment' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Payment account (optional)' })).toBeInTheDocument();
  });

  it('passes the create-mode due date bounds to the date picker', () => {
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

    expect(datePickerFieldSpy).toHaveBeenLastCalledWith(
      expect.objectContaining({
        name: 'dueDate',
        minDate: expectedMinDate,
        maxDate: expectedMaxDate,
      }),
    );
  });

  it('blocks submission when the due date value is outside the allowed range', async () => {
    const view = render(
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

    const nameInput = view.getAllByLabelText('Name')[0]!;
    const dueDateInput = view.getAllByLabelText('Next due date')[0]!;
    const saveButton = view.getAllByRole('button', { name: 'Save' })[0]!;

    fireEvent.change(nameInput, { target: { value: 'Internet' } });
    fireEvent.change(dueDateInput, { target: { value: outOfRangeFutureDate } });

    expect(saveButton).toBeDisabled();
    fireEvent.click(saveButton);

    expect(createRecurringMock).not.toHaveBeenCalled();
  });

  it('blocks submission when manual input leaves the picker in an invalid state', () => {
    const view = render(
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

    const nameInput = view.getAllByLabelText('Name')[0]!;
    const saveButton = view.getAllByRole('button', { name: 'Save' })[0]!;

    fireEvent.change(nameInput, { target: { value: 'Gym' } });
    fireEvent.click(view.getAllByText('Trigger due date max error')[0]!);

    expect(saveButton).toBeDisabled();
    fireEvent.click(saveButton);

    expect(createRecurringMock).not.toHaveBeenCalled();
  });
});
