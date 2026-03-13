import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateInvestmentAccountDrawer } from './CreateInvestmentAccountDrawer';

const mockCreateAccount = vi.fn();
const mockUseQuery = vi.fn();
const successMock = vi.fn();

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
          InputProps: { endAdornment: null },
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
    { loading: false, error: null },
  ]),
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const actual = await vi.importActual<typeof import('@/shared/components/ui/forms/form')>(
    '@/shared/components/ui/forms/form',
  );
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');

  return {
    ...actual,
    SelectField: ({ name, label, options }: any) => {
      const { control } = rhf.useFormContext();
      return (
        <rhf.Controller
          name={name}
          control={control}
          render={({ field }) => (
            <select
              aria-label={label}
              value={field.value ?? ''}
              onChange={(event) => field.onChange(event.target.value)}
            >
              {options.map((option: any) => (
                <option key={String(option.value)} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
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

  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;

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

    mockUseQuery.mockReturnValue({
      data: {
        issuers: [
          { id: 'issuer-xp', name: 'XP Investimentos', logoAsset: null, compeCode: null },
          { id: 'issuer-rico', name: 'Rico', logoAsset: null, compeCode: null },
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

    await user.click(screen.getByRole('button', { name: 'investments.createAccount.actions.submit' }));

    await waitFor(() => {
      expect(screen.getByText('investments.createAccount.validation.issuerRequired')).toBeInTheDocument();
    });
    expect(mockCreateAccount).not.toHaveBeenCalled();
  });

  it('never renders bank/card-only fields', () => {
    renderDrawer();

    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.branchCode')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.accountNumber')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.network')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.creditLimit')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.closingDay')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.dueDay')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('financeTransactions.createAccount.step2.fields.initialBalance')).not.toBeInTheDocument();
  });

  it('submits INVESTMENT account with selected issuerId and triggers callbacks', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const issuerSelect = screen.getByLabelText('issuer-autocomplete-options');
    await act(async () => {
      await user.selectOptions(issuerSelect, 'issuer-xp');
    });

    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'investments.createAccount.actions.submit' }));
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
      await user.click(screen.getByRole('button', { name: 'investments.createAccount.actions.submit' }));
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
