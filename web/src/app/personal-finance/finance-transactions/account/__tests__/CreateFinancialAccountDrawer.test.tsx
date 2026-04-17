'use client';

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import dayjs from 'dayjs';
import { CreateFinancialAccountDrawer } from '../CreateFinancialAccountDrawer';

const mockCreateAccount = vi.fn();
const mockUpdateAccount = vi.fn();
const mockDeleteAccount = vi.fn();
const mockUseQuery = vi.fn();

vi.mock('@/shared/components/ui/forms/form', async () => {
  const actual = await vi.importActual<typeof import('@/shared/components/ui/forms/form')>('@/shared/components/ui/forms/form');
  const { Controller, useFormContext } = await vi.importActual<any>('react-hook-form');

  return {
    ...actual,
    DatePickerField: ({ name, label }: { name: string; label?: string; minDate?: Date }) => {
      const { control } = useFormContext();
      return (
        <Controller
          name={name}
          control={control}
          render={({
            field,
            fieldState,
          }: {
            field: { value: unknown; onChange: (value: unknown) => void };
            fieldState: { error?: { message?: string } };
          }) => (
            <>
              <input
                aria-label={label}
                type="date"
                value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ''}
                onChange={(e) => field.onChange(e.target.value ? new Date(`${e.target.value}T00:00:00`) : null)}
              />
              {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
            </>
          )}
        />
      );
    },
  };
});

vi.mock('@apollo/client/react', () => ({
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
  useMutation: vi.fn((mutation: any) => {
    const operationName = mutation?.definitions?.[0]?.name?.value;

    if (operationName === 'CreateFinancialAccount') {
      return [
        (args: unknown) => mockCreateAccount(args),
        { loading: false, error: null },
      ];
    }

    if (operationName === 'UpdateFinancialAccount') {
      return [
        (args: unknown) => mockUpdateAccount(args),
        { loading: false, error: null },
      ];
    }

    if (operationName === 'DeleteFinancialAccount') {
      return [
        (args: unknown) => mockDeleteAccount(args),
        { loading: false, error: null },
      ];
    }

    return [vi.fn(), { loading: false, error: null }];
  }),
}));

vi.mock('@/shared/providers', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  }),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: { language: 'en-US' },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  const Drawer = ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null);
  const DrawerHeader = function DrawerHeader({ title, icon }: { title: React.ReactNode; icon?: React.ReactNode }) {
    return (
      <div>
        {icon}
        <h2>{title}</h2>
      </div>
    );
  };
  const DrawerBody = function DrawerBody({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  };
  const DrawerActionButtons = function DrawerActionButtons({
    cancelText,
    submitText,
    onCancel,
    deleteText,
    onDelete,
    submitForm,
    submitDisabled,
    cancelDisabled,
    deleteDisabled,
    deleteTestId,
  }: {
    cancelText: string;
    submitText: string;
    onCancel: () => void;
    deleteText?: string;
    onDelete?: () => void;
    submitForm?: string;
    submitDisabled?: boolean;
    cancelDisabled?: boolean;
    deleteDisabled?: boolean;
    deleteTestId?: string;
  }) {
    return (
      <div>
        {deleteText && onDelete ? (
          <button type="button" onClick={onDelete} disabled={deleteDisabled} data-testid={deleteTestId}>
            {deleteText}
          </button>
        ) : null}
        <button type="button" onClick={onCancel} disabled={cancelDisabled}>
          {cancelText}
        </button>
        <button type="submit" form={submitForm} disabled={submitDisabled}>
          {submitText}
        </button>
      </div>
    );
  };
  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  Drawer.ActionButtons = DrawerActionButtons;
  return { Drawer };
});

describe.sequential('CreateFinancialAccountDrawer', () => {
  const theme = createTheme();

  const renderDrawer = () =>
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer open onClose={vi.fn()} initialType="BANK" />
      </ThemeProvider>,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateAccount.mockResolvedValue({ data: { createFinancialAccount: { id: 'acc-1', name: 'Conta 1' } } });
    mockUpdateAccount.mockResolvedValue({ data: { updateFinancialAccount: { id: 'acc-1', name: 'Conta 1' } } });
    mockDeleteAccount.mockResolvedValue({ data: { deleteFinancialAccount: true } });
    mockUseQuery.mockReturnValue({
      data: {
        issuers: [
          { id: 'issuer-1', name: 'Banco Teste', logoAsset: null, compeCode: '001' },
        ],
      },
      loading: false,
      error: null,
    });
  });

  it('submits a default card name when the user leaves name blank', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          initialType="CARD"
          initialMaskedNumber="1234"
          initialClosingDay={7}
          initialDueDay={14}
        />
      </ThemeProvider>,
    );

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addCard' });
    await waitFor(() => expect(submitButton).toBeEnabled());
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: expect.objectContaining({
            input: expect.objectContaining({
              name: 'financeTransactions.createAccount.step2.enums.cardSubtype.CREDIT_CARD',
              cardData: expect.objectContaining({
                maskedNumber: '1234',
              }),
            }),
          }),
        }),
      );
    });
  });

  it('does not include last 4 digits in default card name', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          initialType="CARD"
          initialMaskedNumber="1234"
          initialClosingDay={7}
          initialDueDay={14}
        />
      </ThemeProvider>,
    );

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addCard' });
    await waitFor(() => expect(submitButton).toBeEnabled());
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: expect.objectContaining({
            input: expect.objectContaining({
              name: 'financeTransactions.createAccount.step2.enums.cardSubtype.CREDIT_CARD',
              cardData: expect.objectContaining({
                maskedNumber: '1234',
              }),
            }),
          }),
        }),
      );
    });
  });

  it('does not submit when last four digits is partial — validates on submit only', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          initialType="CARD"
          initialClosingDay={7}
          initialDueDay={14}
        />
      </ThemeProvider>,
    );

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const lastFourInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.maskedNumber');
    await user.type(lastFourInput, '12');

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addCard' });
    await waitFor(() => expect(submitButton).toBeEnabled());
    await user.click(submitButton);

    expect(mockCreateAccount).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByText('financeTransactions.createAccount.step2.validation.maskedNumberInvalid')).toBeInTheDocument();
    });
  });

  it('submits card as multi-card and hides last-four input when toggle is enabled', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer open onClose={vi.fn()} initialType="CARD" initialMaskedNumber="1234" />
      </ThemeProvider>,
    );

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const multiCardToggle = screen.getByRole('switch', {
      name: 'financeTransactions.createAccount.step2.fields.multiCard',
    });
    await user.click(multiCardToggle);
    expect(multiCardToggle).toBeChecked();
    expect(
      screen.queryByLabelText('financeTransactions.createAccount.step2.fields.maskedNumber'),
    ).not.toBeInTheDocument();

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addCard' });
    await waitFor(() => expect(submitButton).toBeEnabled());
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalled();
    });
    const payload = mockCreateAccount.mock.calls[0]?.[0]?.variables?.input?.cardData;
    expect(payload?.isMultiCard).toBe(true);
    expect(payload?.maskedNumber).toBeUndefined();
  });

  it('does not submit when account name exceeds 255 characters', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const nameInput = screen.getByLabelText(/financeTransactions\.createAccount\.step2\.fields\.accountName/i);
    await user.type(nameInput, 'A'.repeat(256));

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addAccount' });
    await waitFor(() => expect(submitButton).toBeDisabled());

    expect(mockCreateAccount).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(
        screen.getByText('financeTransactions.createAccount.step2.validation.accountNameMaxLength'),
      ).toBeInTheDocument();
    });
  });

  it('does not submit when initial balance is provided without initial balance date', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const nameInput = screen.getByLabelText(/financeTransactions\.createAccount\.step2\.fields\.accountName/i);
    await user.type(nameInput, 'Conta Banco Teste');

    const initialBalanceInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.initialBalance');
    await user.type(initialBalanceInput, '100');
    await user.tab();

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addAccount' });
    await waitFor(() => expect(submitButton).toBeDisabled());

    expect(mockCreateAccount).not.toHaveBeenCalled();
  });

  it('submits initialBalance and initialBalanceDate when both are provided', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const nameInput = screen.getByLabelText(/financeTransactions\.createAccount\.step2\.fields\.accountName/i);
    await user.type(nameInput, 'Conta Banco Teste');

    const initialBalanceInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.initialBalance');
    await user.type(initialBalanceInput, '250');

    const initialBalanceDateInput = await screen.findByLabelText(
      /financeTransactions\.createAccount\.step2\.fields\.initialBalanceDate/i,
    );
    const validDate = dayjs().subtract(1, 'day').format('YYYY-MM-DD');
    await user.type(initialBalanceDateInput, validDate);

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addAccount' });
    await waitFor(() => expect(submitButton).toBeEnabled());
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: expect.objectContaining({
            input: expect.objectContaining({
              initialBalance: 250,
              initialBalanceDate: validDate,
            }),
          }),
        }),
      );
    });
  });

  it('does not submit when initial balance date is older than moving window', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.issuer');
    await user.click(issuerInput);
    await user.click(screen.getByText('Banco Teste'));

    const nameInput = screen.getByLabelText(/financeTransactions\.createAccount\.step2\.fields\.accountName/i);
    await user.type(nameInput, 'Conta Banco Teste');

    const initialBalanceInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.initialBalance');
    await user.type(initialBalanceInput, '250');

    const initialBalanceDateInput = await screen.findByLabelText(
      /financeTransactions\.createAccount\.step2\.fields\.initialBalanceDate/i,
    );
    const invalidDate = dayjs().subtract(10, 'year').subtract(1, 'day').format('YYYY-MM-DD');
    await user.type(initialBalanceDateInput, invalidDate);

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addAccount' });
    await user.click(submitButton);

    expect(mockCreateAccount).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByText('financeTransactions.validation.dateTooOld')).toBeInTheDocument();
    });
  });

  it('renders billing cycle fields for charge cards', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer open onClose={vi.fn()} initialType="CARD" />
      </ThemeProvider>,
    );

    const subtypeInput = screen.getByLabelText('financeTransactions.createAccount.step2.fields.subtype');
    await user.click(subtypeInput);
    await user.click(screen.getByText('financeTransactions.createAccount.step2.enums.cardSubtype.CHARGE_CARD'));

    expect(screen.getByLabelText('financeTransactions.createAccount.step2.fields.closingDay')).toBeInTheDocument();
    expect(screen.getByLabelText('financeTransactions.createAccount.step2.fields.dueDay')).toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.creditLimit')).not.toBeInTheDocument();
  });

  it('keeps custom issuer name when opening edit mode for an account without issuerId', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          accountToEdit={{
            id: 'acc-1',
            type: 'BANK',
            subtype: 'CONTA_CORRENTE',
            name: 'Conta Manual',
            issuerName: 'Banco Manual',
            currencyCode: 'BRL',
            includeInNetWorth: true,
            includeInDashboard: true,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          } as any}
        />
      </ThemeProvider>,
    );

    const issuerNameInput = await screen.findByDisplayValue('Banco Manual');
    expect(issuerNameInput).toBeInTheDocument();

    const nameInput = screen.getByLabelText(/financeTransactions\.createAccount\.step2\.fields\.accountName/i);
    await user.clear(nameInput);
    await user.type(nameInput, 'Conta Manual Editada');

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.save' });
    await waitFor(() => expect(submitButton).toBeEnabled());
  });

  it('hydrates multi-card toggle in edit mode and keeps last-four hidden', async () => {
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          accountToEdit={{
            id: 'acc-card-multi',
            type: 'CREDIT',
            subtype: 'CREDIT_CARD',
            name: 'Cartão Multi',
            issuerName: 'Banco Manual',
            currencyCode: 'BRL',
            includeInNetWorth: true,
            includeInDashboard: true,
            cardData: {
              isMultiCard: true,
              maskedNumber: null,
              closingDay: 7,
              dueDay: 15,
            },
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          } as any}
        />
      </ThemeProvider>,
    );

    const multiCardToggle = await screen.findByRole('switch', {
      name: 'financeTransactions.createAccount.step2.fields.multiCard',
    });
    expect(multiCardToggle).toBeChecked();
    expect(
      screen.queryByLabelText('financeTransactions.createAccount.step2.fields.maskedNumber'),
    ).not.toBeInTheDocument();
  });

  it('shows remove account controls in edit mode and calls delete mutation on confirm', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onDeleted = vi.fn();
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={onClose}
          onDeleted={onDeleted}
          accountToEdit={{
            id: 'acc-delete-1',
            type: 'BANK',
            subtype: 'CONTA_CORRENTE',
            name: 'Conta para excluir',
            issuerName: 'Banco Manual',
            currencyCode: 'BRL',
            includeInNetWorth: true,
            includeInDashboard: true,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          } as any}
        />
      </ThemeProvider>,
    );

    const removeButton = await screen.findByTestId('account-form-remove');
    await user.click(removeButton);
    const dialog = await screen.findByTestId('account-delete-dialog');
    await user.click(within(dialog).getByRole('button', { name: 'financeTransactions.actions.deleteAccount' }));

    await waitFor(() => {
      expect(mockDeleteAccount).toHaveBeenCalledWith({ variables: { id: 'acc-delete-1' } });
    });
    expect(onDeleted).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('shows inline error when delete is blocked by transactions', async () => {
    const user = userEvent.setup();
    mockDeleteAccount.mockRejectedValueOnce({
      graphQLErrors: [{ extensions: { code: 'FINANCIAL_ACCOUNT_HAS_TRANSACTIONS' } }],
    });
    render(
      <ThemeProvider theme={theme}>
        <CreateFinancialAccountDrawer
          open
          onClose={vi.fn()}
          accountToEdit={{
            id: 'acc-delete-2',
            type: 'BANK',
            subtype: 'CONTA_CORRENTE',
            name: 'Conta bloqueada',
            issuerName: 'Banco Manual',
            currencyCode: 'BRL',
            includeInNetWorth: true,
            includeInDashboard: true,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          } as any}
        />
      </ThemeProvider>,
    );

    await user.click(await screen.findByTestId('account-form-remove'));
    const dialog = await screen.findByTestId('account-delete-dialog');
    await user.click(within(dialog).getByRole('button', { name: 'financeTransactions.actions.deleteAccount' }));

    await waitFor(() => {
      expect(screen.queryAllByText('financeTransactions.errors.accountHasTransactions').length).toBeGreaterThan(0);
    });
  });
});
