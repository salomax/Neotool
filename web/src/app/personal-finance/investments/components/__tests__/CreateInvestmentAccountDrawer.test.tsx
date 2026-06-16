import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateInvestmentAccountDrawer } from '../CreateInvestmentAccountDrawer';

const mockCreateAccount = vi.fn();
const mockUseQuery = vi.fn();
const successMock = vi.fn();
let mutationError: unknown = null;
const extractErrorMessageMock = vi.fn((..._args: any[]) => 'translated error');

vi.mock('@mui/material', async () => {
  const actual = await vi.importActual<typeof import('@mui/material')>('@mui/material');

  function MockAutocomplete(props: any) {
    const {
      options = [],
      value = null,
      onChange,
      getOptionLabel = (option: any) => option?.label ?? '',
      renderInput,
    } = props;

    return (
      <div>
        {renderInput({
          id: 'issuer-autocomplete',
          inputProps: {},
          InputLabelProps: {},
          InputProps: { startAdornment: null, endAdornment: null },
        })}
        <select
          aria-label="issuer-autocomplete-options"
          value={value?.value ?? ''}
          onChange={(event) => {
            const selected = options.find((option: any) => String(option.value) === event.target.value) ?? null;
            onChange?.(null, selected);
          }}
        >
          <option value="">--</option>
          {options.map((option: any) => (
            <option key={option.value} value={option.value}>
              {getOptionLabel(option)}
            </option>
          ))}
        </select>
      </div>
    );
  }

  return {
    ...actual,
    Autocomplete: MockAutocomplete,
  };
});

vi.mock('@apollo/client/react', () => ({
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
  useMutation: vi.fn((_mutation: unknown, options?: { onCompleted?: (data: any) => void }) => [
    (args: unknown) => {
      mockCreateAccount(args);
      options?.onCompleted?.({ createFinancialAccount: { id: 'acc-1', name: 'Conta XP' } });
      return Promise.resolve({ data: { createFinancialAccount: { id: 'acc-1', name: 'Conta XP' } } });
    },
    { loading: false, error: mutationError },
  ]),
}));

vi.mock('@/shared/utils/error', () => ({
  extractErrorMessage: (...args: any[]) => extractErrorMessageMock(...args),
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const actual = await vi.importActual<typeof import('@/shared/components/ui/forms/form')>(
    '@/shared/components/ui/forms/form',
  );
  const { useFormContext } = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');
  const { Controller } = await vi.importActual<any>('react-hook-form');

  return {
    ...actual,
    SelectField: ({ name, label, options }: any) => {
      const { register } = useFormContext();
      return (
        <select aria-label={label} {...register(name)}>
          {options.map((option: any) => (
            <option key={String(option.value)} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    },
    MoneyInput: ({ name, label }: { name: string; label?: string }) => {
      const { control } = useFormContext();
      return (
        <Controller
          name={name}
          control={control}
          render={({ field }: { field: { value: unknown; onChange: (value: unknown) => void } }) => (
            <input
              aria-label={label}
              type="number"
              value={typeof field.value === 'number' ? field.value : ''}
              onChange={(e) => field.onChange(e.target.value === '' ? undefined : Number(e.target.value))}
            />
          )}
        />
      );
    },
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

vi.mock('@/shared/providers', () => ({
  useToast: () => ({
    success: successMock,
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
  const Drawer = ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? <div>{children}</div> : null;

  const DrawerHeader = function DrawerHeader({ title }: { title: React.ReactNode }) {
    return <h2>{title}</h2>;
  };

  const DrawerBody = function DrawerBody({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  };

  const DrawerFooter = function DrawerFooter({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  };

  const DrawerActionButtons = function DrawerActionButtons({
    cancelText,
    submitText,
    submittingText,
    onCancel,
    onSubmit,
    submitForm,
    cancelDisabled,
    submitDisabled,
    submitting,
  }: any) {
    return (
      <div>
        <button type="button" onClick={onCancel} disabled={cancelDisabled}>
          {cancelText}
        </button>
        <button
          type={submitForm ? 'submit' : 'button'}
          form={submitForm}
          onClick={submitForm ? undefined : onSubmit}
          disabled={submitDisabled}
        >
          {submitting ? submittingText : submitText}
        </button>
      </div>
    );
  };

  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  Drawer.Footer = DrawerFooter;
  Drawer.ActionButtons = DrawerActionButtons;

  return { Drawer };
});

describe.sequential('CreateInvestmentAccountDrawer', () => {
  const onClose = vi.fn();
  const onCreated = vi.fn();

  const renderDrawer = () =>
    render(
      <CreateInvestmentAccountDrawer
        open
        onClose={onClose}
        onCreated={onCreated}
      />,
    );

  beforeEach(() => {
    vi.clearAllMocks();
    mutationError = null;

    mockUseQuery.mockReturnValue({
      data: {
        issuers: [
          { id: 'issuer-xp', name: 'XP Investimentos', logoAsset: null, compeCode: null },
          { id: 'issuer-rico', name: 'Rico', logoAsset: null, compeCode: null },
          { id: 'issuer-caixa', name: 'Caixa economica federal', logoAsset: null, compeCode: '104' },
        ],
      },
      loading: false,
      error: null,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the form directly with no bank/card type selection step', () => {
    renderDrawer();

    expect(screen.getByTestId('investment-subtype-select')).toBeInTheDocument();
    expect(screen.queryByText('financeTransactions.createAccount.step1.bankAccount.label')).not.toBeInTheDocument();
    expect(screen.queryByText('financeTransactions.createAccount.step1.card.label')).not.toBeInTheDocument();
  });

  it('shows issuer validation error when submitting without issuer selection', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: 'actions.save' }));

    await waitFor(() => {
      expect(screen.getByText('investments.createAccount.validation.issuerRequired')).toBeInTheDocument();
    });
    expect(mockCreateAccount).not.toHaveBeenCalled();
  });

  it('shows translated error message when mutation fails with INTERNAL_ERROR', () => {
    mutationError = {
      graphQLErrors: [
        {
          message: 'Internal server error',
          extensions: {
            code: 'INTERNAL_ERROR',
            service: 'banking',
            classification: 'DataFetchingException',
          },
        },
      ],
    };

    renderDrawer();

    expect(screen.getByText('translated error')).toBeInTheDocument();
    expect(extractErrorMessageMock).toHaveBeenCalledWith(mutationError, 'errors.default');
  });

  it('never renders card-only fields', () => {
    renderDrawer();

    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.network')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.creditLimit')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.closingDay')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.dueDay')).not.toBeInTheDocument();
  });

  it('does not render bank or initial balance fields', () => {
    renderDrawer();

    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.branchCode')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.accountNumber')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.checkDigit')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.initialBalance')).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/financeTransactions\.createAccount\.step2\.fields\.initialBalanceDate/i),
    ).not.toBeInTheDocument();
  });

  it('submits INVESTMENT account with selected issuerId and triggers callbacks', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerSelect = screen.getByLabelText('issuer-autocomplete-options');
    await act(async () => {
      await user.selectOptions(issuerSelect, 'issuer-xp');
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'actions.save' }));
    });

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            input: expect.objectContaining({
              type: 'INVESTMENT',
              subtype: 'BROKERAGE',
              issuerId: 'issuer-xp',
            }),
          },
        }),
      );
    });

    expect(successMock).toHaveBeenCalledWith('investments.createAccount.success');
    expect(onCreated).toHaveBeenCalledWith({ id: 'acc-1', name: 'Conta XP' });
    expect(onClose).toHaveBeenCalled();
  });

  it('submits BENEFIT account when subtype is FGTS for Caixa issuer', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerSelect = screen.getByLabelText('issuer-autocomplete-options');
    await act(async () => {
      await user.selectOptions(issuerSelect, 'issuer-caixa');
    });

    const subtypeSelect = screen.getByLabelText('investments.createAccount.fields.subtypeLabel');
    await act(async () => {
      await user.selectOptions(subtypeSelect, 'FGTS');
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'actions.save' }));
    });

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            input: expect.objectContaining({
              type: 'BENEFIT',
              subtype: 'FGTS',
              issuerId: 'issuer-caixa',
            }),
          },
        }),
      );
    });
  });

  it('supports custom issuer flow and sends issuerName instead of issuerId', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerSelect = screen.getByLabelText('issuer-autocomplete-options');
    await act(async () => {
      await user.selectOptions(issuerSelect, 'custom');
    });

    const customIssuerInput = await screen.findByLabelText(/investments\.createAccount\.fields\.customIssuerLabel/i);
    const accountNameInput = screen.getByTestId('investment-account-name-input');

    await act(async () => {
      await user.type(customIssuerInput, 'Rico Investimentos');
      await user.clear(accountNameInput);
      await user.type(accountNameInput, 'Minha Carteira Rico');
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'actions.save' }));
    });

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            input: expect.objectContaining({
              type: 'INVESTMENT',
              subtype: 'BROKERAGE',
              issuerName: 'Rico Investimentos',
              name: 'Minha Carteira Rico',
            }),
          },
        }),
      );
    });

    const submittedInput = mockCreateAccount.mock.calls.at(-1)?.[0]?.variables?.input;
    expect(submittedInput?.issuerId).toBeUndefined();
  });

  it('clears issuerId when the selected issuer is not in the refreshed issuer list', async () => {
    const user = userEvent.setup();

    mockUseQuery.mockImplementation((_query: unknown, options?: any) => {
      const categories = options?.variables?.categories ?? [];
      const isCrypto = Array.isArray(categories) && categories.includes('CRYPTO_EXCHANGE');

      return {
        data: {
          issuers: isCrypto
            ? [{ id: 'issuer-binance', name: 'Binance', logoAsset: null, compeCode: null }]
            : [{ id: 'issuer-xp', name: 'XP Investimentos', logoAsset: null, compeCode: null }],
        },
        loading: false,
        error: null,
      };
    });

    renderDrawer();

    const issuerSelect = screen.getByLabelText('issuer-autocomplete-options');
    await act(async () => {
      await user.selectOptions(issuerSelect, 'issuer-xp');
    });

    const subtypeSelect = screen.getByLabelText('investments.createAccount.fields.subtypeLabel');
    await act(async () => {
      await user.selectOptions(subtypeSelect, 'CRYPTO');
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'actions.save' }));
    });

    await waitFor(() => {
      expect(screen.getByText('investments.createAccount.validation.issuerRequired')).toBeInTheDocument();
    });
    expect(mockCreateAccount).not.toHaveBeenCalled();
  });

  it('fetches issuer list for investment categories and updates to crypto exchange when subtype changes', async () => {
    const user = userEvent.setup();
    renderDrawer();

    expect(mockUseQuery).toHaveBeenCalled();

    const initialOptions = mockUseQuery.mock.calls[0]?.[1] as { variables?: { categories?: string[] }; skip?: boolean };
    expect(initialOptions?.skip).toBe(false);
    expect(initialOptions?.variables?.categories).toEqual(['BANK', 'BROKERAGE']);

    const subtypeSelect = screen.getByLabelText('investments.createAccount.fields.subtypeLabel');
    await act(async () => {
      await user.selectOptions(subtypeSelect, 'CRYPTO');
    });

    await waitFor(() => {
      const latestCall = mockUseQuery.mock.calls.at(-1)?.[1] as { variables?: { categories?: string[] } };
      expect(latestCall?.variables?.categories).toEqual(['CRYPTO_EXCHANGE']);
    });
  });
});
