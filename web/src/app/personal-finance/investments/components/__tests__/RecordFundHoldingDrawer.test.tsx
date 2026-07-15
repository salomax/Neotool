import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RecordFundHoldingDrawer } from '../RecordFundHoldingDrawer';
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION, UPDATE_INVESTMENT_ASSET, UPDATE_INVESTMENT_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { AssetType, InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';

const mockUseMutation = vi.fn();
const mockUseQuery = vi.fn();
const mockCreateAsset = vi.fn();
const mockRecordTransaction = vi.fn();
const mockUpdateAsset = vi.fn();
const mockUpdateTransaction = vi.fn();
const mockRefetchQueries = vi.fn().mockResolvedValue([]);
const successMock = vi.fn();

let createMutationState: { loading: boolean; error: Error | null };
let recordMutationState: { loading: boolean; error: Error | null };
let updateAssetMutationState: { loading: boolean; error: Error | null };
let updateTxMutationState: { loading: boolean; error: Error | null };

vi.mock('@apollo/client/react', () => ({
  useApolloClient: () => ({
    refetchQueries: mockRefetchQueries,
  }),
  useMutation: (mutation: unknown) => mockUseMutation(mutation),
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');

  return {
    MoneyInput: ({
      name,
      label,
      'data-testid': dataTestId,
    }: {
      name: string;
      label?: string;
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
            type="number"
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
            data-testid={dataTestId}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    NumberField: ({
      name,
      label,
      'data-testid': dataTestId,
    }: {
      name: string;
      label?: string;
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
            type="number"
            value={field.value ?? ''}
            onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
            data-testid={dataTestId}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
      );
    },
    DatePickerField: ({
      name,
      label,
      'data-testid': dataTestId,
    }: {
      name: string;
      label?: string;
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
            type="date"
            value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)
            }
            data-testid={dataTestId}
          />
          {fieldState.error?.message ? <span>{fieldState.error.message}</span> : null}
        </div>
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

vi.mock('@/shared/hooks/ui', () => ({
  useDebounce: function <T>(value: T): T {
    return value;
  },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  const Drawer = ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null);
  const DrawerHeader = ({ title }: { title: React.ReactNode }) => <h2>{title}</h2>;
  const DrawerBody = ({ children }: { children: React.ReactNode }) => <div>{children}</div>;
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
  Drawer.ActionButtons = DrawerActionButtons;
  return { Drawer };
});

vi.mock('@/shared/components/ui/forms/components/FormStack', () => ({
  FormStack: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/shared/components/ui/forms/components/FormRowStack', () => ({
  FormRowStack: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/shared/components/ui/forms/components/FormTabs', () => ({
  FormTabs: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/shared/components/ui/primitives/Label', () => ({
  Label: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/shared/components/ui/inputs/SegmentedControl', () => ({
  SegmentedControl: ({ value, onChange, options }: any) => (
    <select aria-label="manual-asset-type" value={value} onChange={(event) => onChange(event.target.value)}>
      {options.map((option: { value: string; label: string }) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  ),
}));

vi.mock('../FundAutocomplete', () => ({
  FundAutocomplete: () => <div data-testid="record-fund-autocomplete" />,
}));

vi.mock('../InvestmentTradeAndSettlementDateRow', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');
  return {
    InvestmentTradeAndSettlementDateRow: ({
      tradeDateLabel,
      settlementDateLabel,
      tradeDateTestId,
      settlementDateTestId,
    }: {
      tradeDateLabel: string;
      settlementDateLabel: string;
      tradeDateTestId: string;
      settlementDateTestId: string;
    }) => {
      const { control } = rhf.useFormContext();
      const tradeDateField = rhf.useController({ name: 'tradeDate', control });
      const settlementDateField = rhf.useController({ name: 'settlementDate', control });
      return (
        <div>
          <label htmlFor="tradeDate">{tradeDateLabel}</label>
          <input
            id="tradeDate"
            type="date"
            aria-label={tradeDateLabel}
            data-testid={tradeDateTestId}
            value={tradeDateField.field.value instanceof Date ? tradeDateField.field.value.toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              tradeDateField.field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)
            }
          />
          <label htmlFor="settlementDate">{settlementDateLabel}</label>
          <input
            id="settlementDate"
            type="date"
            aria-label={settlementDateLabel}
            data-testid={settlementDateTestId}
            value={settlementDateField.field.value instanceof Date ? settlementDateField.field.value.toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              settlementDateField.field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)
            }
          />
        </div>
      );
    },
  };
});

vi.mock('../InvestmentRecordDrawer', () => ({
  InvestmentRecordDrawer: ({ children }: { children: (args: { resolvedFinancialAccountId: string | null }) => React.ReactNode }) =>
    <div>{children({ resolvedFinancialAccountId: 'fa-1' })}</div>,
}));

vi.mock('../IssuerSelection', () => ({}));
vi.mock('@/shared/utils/date/dateOnly', async () => {
  const actual = await vi.importActual<typeof import('@/shared/utils/date/dateOnly')>('@/shared/utils/date/dateOnly');
  return actual;
});
vi.mock('@/shared/utils/investment/settlementDatePolicy', async () => {
  const actual = await vi.importActual<typeof import('@/shared/utils/investment/settlementDatePolicy')>(
    '@/shared/utils/investment/settlementDatePolicy',
  );
  return actual;
});
vi.mock('../hooks/useSyncDefaultSettlementDate', () => ({
  useSyncDefaultSettlementDate: vi.fn(),
}));
vi.mock('@/shared/components/ui/feedback', () => ({
  ErrorAlert: () => null,
}));

describe.sequential('RecordFundHoldingDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    createMutationState = { loading: false, error: null };
    recordMutationState = { loading: false, error: null };
    updateAssetMutationState = { loading: false, error: null };
    updateTxMutationState = { loading: false, error: null };

    mockUseMutation.mockImplementation((mutation: unknown) => {
      if (mutation === CREATE_INVESTMENT_ASSET) return [mockCreateAsset, createMutationState];
      if (mutation === RECORD_INVESTMENT_TRANSACTION) return [mockRecordTransaction, recordMutationState];
      if (mutation === UPDATE_INVESTMENT_ASSET) return [mockUpdateAsset, updateAssetMutationState];
      if (mutation === UPDATE_INVESTMENT_TRANSACTION) return [mockUpdateTransaction, updateTxMutationState];
      return [vi.fn(), { loading: false, error: null }];
    });

    mockUseQuery.mockImplementation((_query: unknown, options?: { variables?: { to?: string } }) => {
      const navByDate: Record<string, number> = {
        '2026-07-15': 10.5,
        '2026-07-10': 10.5,
        '2026-07-11': 10.75,
      };
      const requestedDate = options?.variables?.to ?? '2026-07-15';
      const closePrice = navByDate[requestedDate] ?? 10.5;

      return {
        data: {
          investmentFund: {
            id: 'fund-1',
            latestNav: { date: requestedDate, closePrice },
            priceHistory: [{ date: requestedDate, closePrice }],
          },
        },
        loading: false,
        error: null,
      };
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('keeps unit price visible in by amount flow when a NAV suggestion exists', async () => {
    const user = userEvent.setup();

    await act(async () => {
      render(
        <RecordFundHoldingDrawer
          open
          onClose={vi.fn()}
          financialAccountId="fa-1"
          financialAccounts={[]}
          embedded
          resolvedAccountId="fa-1"
          assetGate={{
            mode: 'locked',
            result: {
              kind: 'fund_cvm',
              listingMode: 'CVM',
              fund: {
                id: 'fund-1',
                name: 'Fund Test',
                cnpj: '12345678000199',
                assetType: 'FUND',
                isActive: true,
              },
            },
          }}
          initialDirection={InvestmentTransactionDirection.Inflow}
        />,
      );
    });

    expect(await screen.findByTestId('record-fund-gross-value')).toBeInTheDocument();
    expect(screen.getByTestId('record-fund-unit-price')).toBeInTheDocument();

    await user.clear(screen.getByTestId('record-fund-gross-value'));
    await user.type(screen.getByTestId('record-fund-gross-value'), '1000');
    await user.clear(screen.getByTestId('record-fund-unit-price'));
    await user.type(screen.getByTestId('record-fund-unit-price'), '12.3456789');

    await waitFor(() => {
      expect(screen.getByTestId('record-fund-gross-value')).toHaveValue(1000);
      expect(screen.getByTestId('record-fund-unit-price')).toHaveValue(12.3456789);
    });
  });

  it('updates unit price when trade date changes for CVM funds', async () => {
    await act(async () => {
      render(
        <RecordFundHoldingDrawer
          open
          onClose={vi.fn()}
          financialAccountId="fa-1"
          financialAccounts={[]}
          embedded
          resolvedAccountId="fa-1"
          assetGate={{
            mode: 'locked',
            result: {
              kind: 'fund_cvm',
              listingMode: 'CVM',
              fund: {
                id: 'fund-1',
                name: 'Fund Test',
                cnpj: '12345678000199',
                assetType: 'FUND',
                isActive: true,
              },
            },
          }}
          initialDirection={InvestmentTransactionDirection.Inflow}
        />,
      );
    });

    await waitFor(() => {
      expect(screen.getByTestId('record-fund-unit-price')).toHaveValue(10.5);
    });

    fireEvent.change(screen.getByTestId('record-fund-trade-date'), {
      target: { value: '2026-07-11' },
    });

    await waitFor(() => {
      expect(screen.getByTestId('record-fund-unit-price')).toHaveValue(10.75);
    });
  });
});
