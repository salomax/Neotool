import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RecordFixedIncomeDrawer } from '../RecordFixedIncomeDrawer';
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';

const mockUseMutation = vi.fn();
const mockCreateAsset = vi.fn();
const mockRecordTransaction = vi.fn();
const mockRefetchQueries = vi.fn().mockResolvedValue([]);
const successMock = vi.fn();

let createMutationState: { loading: boolean; error: Error | null };
let recordMutationState: { loading: boolean; error: Error | null };
let recordOnCompleted: ((data: unknown) => void) | undefined;

vi.mock('@apollo/client/react', () => ({
  useApolloClient: () => ({
    refetchQueries: mockRefetchQueries,
  }),
  useMutation: (mutation: unknown, options?: { onCompleted?: (data: unknown) => void }) =>
    mockUseMutation(mutation, options),
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');

  return {
    FormTextField: ({
      name,
      label,
      type = 'text',
      InputProps,
      'data-testid': dataTestId,
    }: {
      name: string;
      label?: string;
      type?: string;
      InputProps?: { readOnly?: boolean };
      'data-testid'?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type={type}
            value={field.value ?? ''}
            readOnly={InputProps?.readOnly}
            onChange={(event) => field.onChange(event.target.value)}
            data-testid={dataTestId}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    SelectField: ({
      name,
      label,
      options,
    }: {
      name: string;
      label?: string;
      options: Array<{ value: string; label: string }>;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <select
            id={name}
            aria-label={label}
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value)}
          >
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    NumberField: ({
      name,
      label,
    }: {
      name: string;
      label?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type="number"
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    PercentField: ({
      name,
      label,
    }: {
      name: string;
      label?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type="number"
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    MoneyInput: ({
      name,
      label,
    }: {
      name: string;
      label?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type="number"
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    DatePickerField: ({
      name,
      label,
    }: {
      name: string;
      label?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            type="date"
            value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)
            }
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    CheckboxField: ({
      name,
      label,
      'data-testid': dataTestId,
    }: {
      name: string;
      label: string;
      'data-testid'?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field } = rhf.useController({ name, control });
      return (
        <label htmlFor={name}>
          <input
            id={name}
            aria-label={label}
            type="checkbox"
            checked={Boolean(field.value)}
            onChange={(event) => field.onChange(event.target.checked)}
            data-testid={dataTestId}
          />
          {label}
        </label>
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
  i18n: {
    language: 'pt-BR',
  },
}));

vi.mock('@/shared/components/ui/forms/components/FormErrorBanner', () => ({
  FormErrorBanner: ({ message }: { message: string }) => <div role="alert">{message}</div>,
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  const Drawer = ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null);
  const DrawerHeader = ({ title }: { title: React.ReactNode }) => <h2>{title}</h2>;
  const DrawerBody = ({ children }: { children: React.ReactNode }) => <div>{children}</div>;
  const DrawerFooter = ({ children }: { children: React.ReactNode }) => <div>{children}</div>;
  const DrawerActionButtons = ({
    cancelText,
    submitText,
    submittingText,
    onCancel,
    onSubmit,
    submitForm,
    cancelDisabled,
    submitDisabled,
    submitting,
  }: any) => (
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
  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  Drawer.Footer = DrawerFooter;
  Drawer.ActionButtons = DrawerActionButtons;
  return { Drawer };
});

describe.sequential('RecordFixedIncomeDrawer', () => {
  const onClose = vi.fn();
  const onRecorded = vi.fn();

  const renderDrawer = (financialAccountId: string | null = 'acc-1') =>
    render(
      <RecordFixedIncomeDrawer
        open
        onClose={onClose}
        financialAccountId={financialAccountId}
        financialAccounts={[{ id: 'acc-1', name: 'Conta Investimentos', issuerName: 'Corretora', themeColor: '#1976d2' } as any]}
        onRecorded={onRecorded}
      />,
    );

  const fillValidForm = async (
    overrides?: { tradeDate?: string; maturityDate?: string; indexerPercentage?: string },
  ) => {
    const assetName = screen.getByLabelText('investments.recordHolding.fields.assetName');
    const assetType = screen.getByLabelText('investments.recordHolding.fields.assetType');
    const issuer = screen.getByLabelText('investments.recordHolding.fields.issuerName');
    const indexer = screen.getByLabelText('investments.recordHolding.fields.indexer');
    const indexerPercentage = screen.getByLabelText('investments.recordHolding.fields.indexerPercentage');
    const grossValue = screen.getByLabelText('investments.recordHolding.fields.grossValue');
    const tradeDate = screen.getByLabelText('investments.recordHolding.fields.tradeDate');
    const maturityDate = screen.getByLabelText('investments.recordHolding.fields.maturityDate');

    await act(async () => {
      fireEvent.change(assetName, { target: { value: 'CDB Banco Inter 120% CDI' } });
      fireEvent.change(assetType, { target: { value: 'CDB' } });
      fireEvent.change(issuer, { target: { value: 'Banco Inter' } });
      fireEvent.change(indexer, { target: { value: 'CDI' } });
      fireEvent.change(indexerPercentage, { target: { value: overrides?.indexerPercentage ?? '120' } });
      fireEvent.change(grossValue, { target: { value: '10000' } });
      fireEvent.change(tradeDate, { target: { value: overrides?.tradeDate ?? '2026-03-12' } });
      fireEvent.change(maturityDate, { target: { value: overrides?.maturityDate ?? '2028-03-12' } });
    });
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockRefetchQueries.mockResolvedValue([]);

    createMutationState = { loading: false, error: null };
    recordMutationState = { loading: false, error: null };
    recordOnCompleted = undefined;

    mockUseMutation.mockImplementation((mutation: unknown, options?: { onCompleted?: (data: unknown) => void }) => {
      if (mutation === CREATE_INVESTMENT_ASSET) {
        return [mockCreateAsset, createMutationState];
      }

      if (mutation === RECORD_INVESTMENT_TRANSACTION) {
        recordOnCompleted = options?.onCompleted;
        return [mockRecordTransaction, recordMutationState];
      }

      return [vi.fn(), { loading: false, error: null }];
    });

    mockCreateAsset.mockResolvedValue({
      data: { createInvestmentAsset: { id: 'asset-1' } },
    });

    mockRecordTransaction.mockImplementation(async () => {
      const result = { recordInvestmentTransaction: { id: 'tx-1' } };
      recordOnCompleted?.(result);
      return { data: result };
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders all required fields', () => {
    renderDrawer();

    expect(screen.getByLabelText('investments.recordHolding.fields.assetName')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.assetType')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.issuerName')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.indexer')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.grossValue')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.tradeDate')).toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.maturityDate')).toBeInTheDocument();
  });

  it('toggles indexer fields when switching to pre-fixed', async () => {
    const user = userEvent.setup();
    renderDrawer();

    expect(screen.getByLabelText('investments.recordHolding.fields.indexerPercentage')).toBeInTheDocument();
    expect(screen.queryByLabelText('investments.recordHolding.fields.fixedAnnualRate')).not.toBeInTheDocument();

    await act(async () => {
      await user.selectOptions(screen.getByLabelText('investments.recordHolding.fields.indexer'), 'PRE_FIXADO');
    });

    expect(screen.queryByLabelText('investments.recordHolding.fields.indexerPercentage')).not.toBeInTheDocument();
    expect(screen.getByLabelText('investments.recordHolding.fields.fixedAnnualRate')).toBeInTheDocument();
  });

  it('auto-fills issuer as Tesouro Nacional when asset type is treasury', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await act(async () => {
      await user.selectOptions(screen.getByLabelText('investments.recordHolding.fields.assetType'), 'TREASURY');
    });

    const issuerInput = screen.getByLabelText('investments.recordHolding.fields.issuerName');
    expect(issuerInput).toHaveValue('Tesouro Nacional');
    expect(issuerInput).toHaveAttribute('readonly');
  });

  it('auto-checks and unchecks tax exemption based on asset type', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const taxExempt = screen.getByRole('checkbox', { name: 'investments.recordHolding.fields.isTaxExempt' });
    expect(taxExempt).not.toBeChecked();

    await act(async () => {
      await user.selectOptions(screen.getByLabelText('investments.recordHolding.fields.assetType'), 'LCI');
    });
    expect(taxExempt).toBeChecked();

    await act(async () => {
      await user.selectOptions(screen.getByLabelText('investments.recordHolding.fields.assetType'), 'CDB');
    });
    expect(taxExempt).not.toBeChecked();
  });

  it('auto-selects asset type based on asset name with case-sensitive match', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const assetName = screen.getByLabelText('investments.recordHolding.fields.assetName');
    const assetType = screen.getByLabelText('investments.recordHolding.fields.assetType');

    await act(async () => {
      await user.selectOptions(assetType, 'LCI');
    });
    expect(assetType).toHaveValue('LCI');

    await act(async () => {
      fireEvent.change(assetName, { target: { value: 'CDB Banco Inter 120% CDI' } });
    });
    expect(assetType).toHaveValue('CDB');

    await act(async () => {
      await user.selectOptions(assetType, 'LCI');
    });
    expect(assetType).toHaveValue('LCI');

    await act(async () => {
      fireEvent.change(assetName, { target: { value: 'cdb Banco Inter 120% CDI' } });
    });
    expect(assetType).toHaveValue('LCI');
  });

  it('shows validation errors when submitting empty form', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: 'investments.recordHolding.actions.submit' }));

    await waitFor(() => {
      expect(screen.getByText('investments.recordHolding.validation.assetNameRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.assetTypeRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.issuerRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.indexerRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.grossValueRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.tradeDateRequired')).toBeInTheDocument();
      expect(screen.getByText('investments.recordHolding.validation.maturityDateRequired')).toBeInTheDocument();
    });
  });

  it('validates maturity date must be after trade date', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await fillValidForm({
      tradeDate: '2026-12-01',
      maturityDate: '2026-01-01',
    });
    await user.click(screen.getByRole('button', { name: 'investments.recordHolding.actions.submit' }));

    await waitFor(() => {
      expect(screen.getByText('investments.recordHolding.validation.maturityAfterTrade')).toBeInTheDocument();
    });
  });

  it('calls createInvestmentAsset then recordInvestmentTransaction and closes on success', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await fillValidForm({ indexerPercentage: '120' });
    await user.click(screen.getByRole('button', { name: 'investments.recordHolding.actions.submit' }));

    await waitFor(() => {
      expect(mockCreateAsset).toHaveBeenCalledOnce();
      expect(mockRecordTransaction).toHaveBeenCalledOnce();
    });

    expect(mockCreateAsset).toHaveBeenCalledWith(
      expect.objectContaining({
        variables: {
          input: {
            name: 'CDB Banco Inter 120% CDI',
            assetType: 'CDB',
            issuerName: 'Banco Inter',
            currencyCode: 'BRL',
          },
        },
      }),
    );

    expect(mockRecordTransaction).toHaveBeenCalledWith(
      expect.objectContaining({
        variables: {
          input: expect.objectContaining({
            financialAccountId: 'acc-1',
            assetId: 'asset-1',
            direction: 'INFLOW',
            type: 'PURCHASE',
            quantity: 1,
            unitPrice: 10000,
            grossValue: 10000,
            indexer: 'CDI',
            indexerPercentage: 1.2,
            fixedAnnualRate: undefined,
            tradeDate: '2026-03-12',
            settlementDate: '2026-03-12',
            issueDate: '2026-03-12',
            maturityDate: '2028-03-12',
            isTaxExempt: false,
          }),
        },
      }),
    );

    await waitFor(() => {
      expect(mockRefetchQueries).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(successMock).toHaveBeenCalledWith('investments.recordHolding.success');
      expect(onRecorded).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('sends indexerPercentage as fraction for pt-BR decimals', async () => {
    const user = userEvent.setup();
    renderDrawer();

    // PercentField parses locale-formatted user input into a number.
    // For "8,80 %", the drawer should ultimately send 0.088 (8.80 / 100).
    await fillValidForm({ indexerPercentage: '8.8' });
    await user.click(screen.getByRole('button', { name: 'investments.recordHolding.actions.submit' }));

    await waitFor(() => {
      expect(mockRecordTransaction).toHaveBeenCalledOnce();
    });

    const firstCall = mockRecordTransaction.mock.calls[0]?.[0] as any;
    const sent = firstCall?.variables?.input?.indexerPercentage;
    expect(sent).toBeCloseTo(0.088, 12);
  });

  it('renders mutation error banner when mutation has error', () => {
    createMutationState = { loading: false, error: new Error('create failed') };
    renderDrawer();

    expect(screen.getByRole('alert')).toHaveTextContent('create failed');
  });
});
