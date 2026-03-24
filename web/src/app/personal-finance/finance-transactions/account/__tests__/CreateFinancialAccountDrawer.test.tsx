'use client';

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import dayjs from 'dayjs';
import { CreateFinancialAccountDrawer } from '../CreateFinancialAccountDrawer';

const mockCreateAccount = vi.fn();
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
  useMutation: vi.fn((_mutation: unknown, options?: { onCompleted?: (data: any) => void }) => [
    (args: unknown) => {
      mockCreateAccount(args);
      options?.onCompleted?.({ createFinancialAccount: { id: 'acc-1', name: 'Conta 1' } });
      return Promise.resolve({ data: { createFinancialAccount: { id: 'acc-1', name: 'Conta 1' } } });
    },
    { loading: false, error: null },
  ]),
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
    submitForm,
    submitDisabled,
    cancelDisabled,
  }: {
    cancelText: string;
    submitText: string;
    onCancel: () => void;
    submitForm?: string;
    submitDisabled?: boolean;
    cancelDisabled?: boolean;
  }) {
    return (
      <div>
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
              name: 'financeTransactions.createAccount.step2.enums.cardSubtype.CREDIT_CARD • 1234',
              cardData: expect.objectContaining({
                maskedNumber: '1234',
              }),
            }),
          }),
        }),
      );
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
    const invalidDate = dayjs().subtract(5, 'year').subtract(1, 'day').format('YYYY-MM-DD');
    await user.type(initialBalanceDateInput, invalidDate);

    const submitButton = screen.getByRole('button', { name: 'financeTransactions.actions.addAccount' });
    await waitFor(() => expect(submitButton).toBeDisabled());

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
});
