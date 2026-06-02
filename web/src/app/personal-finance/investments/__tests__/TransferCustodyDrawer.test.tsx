import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransferCustodyDrawer, type TransferCustodyDrawerHolding } from '../TransferCustodyDrawer';
import { TRANSFER_CUSTODY } from '@/lib/graphql/operations/banking/mutations';

const mockUseMutation = vi.fn();
const mockTransferCustody = vi.fn();
const toastSuccessMock = vi.fn();
const toastErrorMock = vi.fn();

let mutationState: { loading: boolean; error: Error | null };

vi.mock('@apollo/client/react', () => ({
  useMutation: (mutation: unknown) => mockUseMutation(mutation),
}));

vi.mock('@/shared/providers', () => ({
  useToast: () => ({
    success: toastSuccessMock,
    error: toastErrorMock,
    info: vi.fn(),
    warning: vi.fn(),
  }),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      key === 'investments.transferCustody.form.quantityHint'
        ? `${key}:${String(params?.quantity ?? '')}`
        : key,
  }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  function Drawer({ open, children }: { open: boolean; children: React.ReactNode }) {
    return open ? <div>{children}</div> : null;
  }
  Drawer.displayName = 'MockDrawer';
  function DrawerHeader({ title }: { title: React.ReactNode }) {
    return <h2>{title}</h2>;
  }
  DrawerHeader.displayName = 'MockDrawerHeader';
  function DrawerBody({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerBody.displayName = 'MockDrawerBody';
  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  function DrawerActionButtons({
    submitText,
    submitForm,
    onCancel,
    cancelText,
  }: {
    submitText: string;
    submitForm: string;
    onCancel: () => void;
    cancelText: string;
  }) {
    return (
      <div>
        <button type="button" onClick={onCancel}>
          {cancelText}
        </button>
        <button type="submit" form={submitForm}>
          {submitText}
        </button>
      </div>
    );
  }
  DrawerActionButtons.displayName = 'MockDrawerActionButtons';
  Drawer.ActionButtons = DrawerActionButtons;
  return { Drawer };
});

vi.mock('@/shared/components/ui/feedback', () => ({
  ErrorAlert: ({ error }: { error?: Error | null }) => (error ? <div>{error.message}</div> : null),
}));

vi.mock('../components/HoldingIdentity', () => ({
  HoldingIdentity: ({ assetName }: { assetName: string }) => <div>{assetName}</div>,
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');
  return {
    DatePickerField: ({ name, label }: { name: string; label: string }) => {
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
            onChange={(event) => field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : null)}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    SelectField: ({
      name,
      label,
      options,
      helperText,
    }: {
      name: string;
      label: string;
      options: Array<{ value: string; label: string }>;
      helperText?: React.ReactNode;
    }) => {
      const { control } = rhf.useFormContext();
      const { field, fieldState } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <select id={name} aria-label={label} value={field.value ?? ''} onChange={(event) => field.onChange(event.target.value)}>
            <option value="">--</option>
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
          {helperText ? <span>{helperText}</span> : null}
        </div>
      );
    },
    ToggleField: ({ name, label }: { name: string; label: string }) => {
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
          />
          {label}
        </label>
      );
    },
    NumberField: ({ name, label }: { name: string; label: string }) => {
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
  };
});

const holding: TransferCustodyDrawerHolding = {
  id: 'holding-1',
  financialAccountId: 'acc-source',
  quantity: 100,
  averageCost: 12.34,
  asset: {
    id: 'asset-1',
    name: 'PETR4',
    assetType: 'STOCK',
  },
};

describe.sequential('TransferCustodyDrawer', () => {
  const onClose = vi.fn();
  const onSuccess = vi.fn();

  beforeEach(() => {
    mutationState = { loading: false, error: null };
    mockTransferCustody.mockResolvedValue({ data: {} });
    mockUseMutation.mockImplementation((mutation: unknown) => {
      if (mutation === TRANSFER_CUSTODY) {
        return [mockTransferCustody, mutationState] as const;
      }
      return [vi.fn(), { loading: false, error: null }] as const;
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows helper text when no destination account is available', () => {
    render(
      <TransferCustodyDrawer
        open
        onClose={onClose}
        holding={holding}
        investmentAccounts={[{ id: 'acc-source', name: 'Conta origem', subtype: 'BROKERAGE' }]}
        onSuccess={onSuccess}
      />,
    );

    expect(screen.getByText('investments.transferCustody.form.noDestinationAccountAvailable')).toBeInTheDocument();
  });

  it('submits full transfer with quantity undefined', async () => {
    const user = userEvent.setup();
    render(
      <TransferCustodyDrawer
        open
        onClose={onClose}
        holding={holding}
        investmentAccounts={[
          { id: 'acc-source', name: 'Conta origem', subtype: 'BROKERAGE' },
          { id: 'acc-destination', name: 'Conta destino', subtype: 'BROKERAGE' },
        ]}
        onSuccess={onSuccess}
      />,
    );

    await user.selectOptions(
      screen.getByLabelText('investments.transferCustody.form.destinationAccount'),
      'acc-destination',
    );
    await user.click(screen.getByRole('button', { name: 'investments.transferCustody.submit' }));

    await waitFor(() => {
      expect(mockTransferCustody).toHaveBeenCalled();
    });

    const firstCall = mockTransferCustody.mock.calls[0][0];
    expect(firstCall.variables.input.sourceHoldingId).toBe('holding-1');
    expect(firstCall.variables.input.destinationFinancialAccountId).toBe('acc-destination');
    expect(firstCall.variables.input.quantity).toBeUndefined();
    expect(typeof firstCall.variables.input.tradeDate).toBe('string');
    expect(toastSuccessMock).toHaveBeenCalledWith('investments.transferCustody.success');
    expect(onSuccess).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('submits partial transfer with quantity value', async () => {
    const user = userEvent.setup();
    render(
      <TransferCustodyDrawer
        open
        onClose={onClose}
        holding={holding}
        investmentAccounts={[
          { id: 'acc-source', name: 'Conta origem', subtype: 'BROKERAGE' },
          { id: 'acc-destination', name: 'Conta destino', subtype: 'BROKERAGE' },
        ]}
        onSuccess={onSuccess}
      />,
    );

    await user.selectOptions(
      screen.getByLabelText('investments.transferCustody.form.destinationAccount'),
      'acc-destination',
    );
    await user.click(screen.getByLabelText('investments.transferCustody.form.fullTransferToggle'));
    await user.clear(screen.getByLabelText('investments.transferCustody.form.quantity'));
    await user.type(screen.getByLabelText('investments.transferCustody.form.quantity'), '25');
    await user.click(screen.getByRole('button', { name: 'investments.transferCustody.submit' }));

    await waitFor(() => {
      expect(mockTransferCustody).toHaveBeenCalled();
    });

    const firstCall = mockTransferCustody.mock.calls[0][0];
    expect(firstCall.variables.input.quantity).toBe(25);
  });

  it('does not call toast error when mutation rejects', async () => {
    const user = userEvent.setup();
    mockTransferCustody.mockRejectedValue(new Error('boom'));
    render(
      <TransferCustodyDrawer
        open
        onClose={onClose}
        holding={holding}
        investmentAccounts={[
          { id: 'acc-source', name: 'Conta origem', subtype: 'BROKERAGE' },
          { id: 'acc-destination', name: 'Conta destino', subtype: 'BROKERAGE' },
        ]}
        onSuccess={onSuccess}
      />,
    );

    await user.selectOptions(
      screen.getByLabelText('investments.transferCustody.form.destinationAccount'),
      'acc-destination',
    );
    await user.click(screen.getByRole('button', { name: 'investments.transferCustody.submit' }));

    await waitFor(() => {
      expect(mockTransferCustody).toHaveBeenCalled();
    });

    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
