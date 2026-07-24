import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import {
  FundNavSeriesDocument,
  InvestmentFundNavDocument,
  SearchInvestmentFundsAutocompleteDocument,
} from '@/lib/graphql/operations/financial-data/queries.generated';
import { FundUpdateFields } from '../FundUpdateFields';
import type { UpdateHoldingFormValues } from '../types';

const mockUseQuery = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      if (key === 'investments.updateHolding.sections.dates') return 'Datas';
      if (key === 'investments.updateHolding.sections.amounts') return 'Valores';
      if (key === 'investments.updateHolding.sections.pricing') return 'Precificação';
      if (key === 'investments.updateHolding.sections.taxes') return 'Impostos';
      if (key === 'investments.updateHolding.sections.totals') return 'Totais';
      if (key === 'investments.recordFund.flows.byAmount') return 'Por valor aplicado';
      if (key === 'investments.recordFund.flows.byQuantity') return 'Por quantidade de cotas';
      return key;
    },
  }),
}));

vi.mock('@/shared/components/ui/forms/form', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');

  const renderNumberInput = ({
    name,
    label,
    dataTestId,
  }: {
    name: string;
    label?: string;
    dataTestId?: string;
  }) => {
    const { control } = rhf.useFormContext();
    const { field } = rhf.useController({ name, control });
    return (
      <div>
        <label htmlFor={name}>{label}</label>
        <input
          id={name}
          aria-label={label}
          data-testid={dataTestId}
          type="number"
          value={field.value ?? ''}
          onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
        />
      </div>
    );
  };

  return {
    MoneyInput: ({
      name,
      label,
      disabled,
      'data-testid': dataTestId,
    }: {
      name: string;
      label?: string;
      disabled?: boolean;
      'data-testid'?: string;
    }) => {
      const { control } = rhf.useFormContext();
      const { field } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            data-testid={dataTestId}
            type="number"
            disabled={disabled}
            value={field.value ?? ''}
            onChange={(event) =>
              field.onChange(event.target.value === '' ? undefined : Number(event.target.value))
            }
          />
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
    }) => renderNumberInput({ name, label, dataTestId }),
    DatePickerField: ({ name, label }: { name: string; label?: string }) => {
      const { control } = rhf.useFormContext();
      const { field } = rhf.useController({ name, control });
      return (
        <div>
          <label htmlFor={name}>{label}</label>
          <input
            id={name}
            aria-label={label}
            data-testid={`fund-update-${name}`}
            type="date"
            value={
              field.value instanceof Date
                ? field.value.toISOString().slice(0, 10)
                : typeof field.value === 'string'
                  ? field.value
                  : ''
            }
            onChange={(event) => {
              field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined);
            }}
          />
        </div>
      );
    },
  };
});

vi.mock('@/shared/components/ui/forms/components', () => ({
  FormStack: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  FormRowStack: ({
    children,
    'data-testid': dataTestId,
  }: {
    children: React.ReactNode;
    'data-testid'?: string;
    alignItems?: string;
  }) => <div data-testid={dataTestId}>{children}</div>,
}));

vi.mock('@/shared/components/ui/forms/components/FormTabs', () => ({
  FormTabs: ({
    children,
    value,
    onChange,
    'data-testid': dataTestId,
  }: {
    children: React.ReactNode;
    value: string;
    onChange: (_: unknown, next: string) => void;
    'data-testid'?: string;
  }) => (
    <div data-testid={dataTestId} data-value={value}>
      {React.Children.map(children, (child) => {
        if (!React.isValidElement(child)) return child;
        const tabValue = (child.props as { value?: string; label?: string }).value;
        const label = (child.props as { value?: string; label?: string }).label;
        return (
          <button type="button" onClick={() => onChange(null, tabValue ?? '')}>
            {label}
          </button>
        );
      })}
    </div>
  ),
}));

vi.mock('../PositionSummaryStrip', () => ({
  PositionSummaryStrip: () => null,
}));

vi.mock('../../InvestmentTradeAndSettlementDateRow', async () => {
  const rhf = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');
  return {
    InvestmentTradeAndSettlementDateRow: () => {
      const { control } = rhf.useFormContext();
      const { field } = rhf.useController({ name: 'tradeDate', control });
      return (
        <input
          data-testid="fund-update-trade-date"
          type="date"
          value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ''}
          onChange={(event) => {
            field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined);
          }}
        />
      );
    },
  };
});

vi.mock('../UpdateFormSectionLabel', () => ({
  UpdateFormSectionLabel: ({ title }: { title: string }) => <div>{title}</div>,
}));

function mockNavQueries(opts?: { navPrice?: number }) {
  const navPrice = opts?.navPrice ?? 12.345678;
  mockUseQuery.mockImplementation((document: unknown) => {
    if (document === SearchInvestmentFundsAutocompleteDocument) {
      return {
        data: {
          searchInvestmentFundsAutocomplete: {
            suggestions: [
              {
                id: 'fund-1',
                name: 'Fundo Teste',
                cnpj: '12.345.678/0001-99',
              },
            ],
          },
        },
        loading: false,
        error: undefined,
      };
    }
    if (document === InvestmentFundNavDocument) {
      return {
        data: {
          investmentFund: {
            id: 'fund-1',
            priceHistory: [{ closePrice: navPrice }],
          },
        },
        loading: false,
        error: undefined,
      };
    }
    if (document === FundNavSeriesDocument) {
      return { data: undefined, loading: false, error: undefined };
    }
    return { data: undefined, loading: false, error: undefined };
  });
}

function Harness({
  onReady,
  defaultValues,
  enableNav = false,
  ...props
}: Partial<React.ComponentProps<typeof FundUpdateFields>> & {
  onReady?: (methods: ReturnType<typeof useForm<UpdateHoldingFormValues>>) => void;
  defaultValues?: Partial<UpdateHoldingFormValues>;
  /** When false, omit fund identifiers so NAV effects do not run (avoids cross-test leaks). */
  enableNav?: boolean;
}) {
  const methods = useForm<UpdateHoldingFormValues>({
    defaultValues: {
      tradeDate: new Date('2026-07-20T00:00:00'),
      settlementDate: new Date('2026-07-21T00:00:00'),
      ...defaultValues,
    },
  });

  React.useEffect(() => {
    onReady?.(methods);
  }, [methods, onReady]);

  return (
    <FormProvider {...methods}>
      <FundUpdateFields
        direction={InvestmentTransactionDirection.Inflow}
        currentValue={1000}
        currentQuantity={100}
        currencyCode="BRL"
        quantityUnit="cotas"
        locale="pt-BR"
        minDate={new Date('2020-01-01')}
        maxDate={new Date('2026-07-22')}
        isManualPricingMode={false}
        fundCnpj={enableNav ? '12.345.678/0001-99' : null}
        tradeDateLabel="Data da aplicação"
        settlementDateLabel="Data de liquidação"
        grossValueLabel="Valor aplicado"
        netValueLabel="Valor líquido"
        quantityLabel="Quantidade de cotas"
        unitPriceLabel="Valor da cota"
        valuedAtLabel="Data do valor"
        incomeTaxLabel="IR"
        iofLabel="IOF"
        {...props}
      />
    </FormProvider>
  );
}

describe.sequential('FundUpdateFields', () => {
  beforeEach(() => {
    mockUseQuery.mockReset();
    mockUseQuery.mockReturnValue({
      data: undefined,
      loading: false,
      error: undefined,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('mirrors create-fund value flow tabs and shows amount + unit price by default', () => {
    render(<Harness />);

    expect(screen.getByTestId('fund-update-value-flow-tabs')).toBeTruthy();
    expect(screen.getByText('Por valor aplicado')).toBeTruthy();
    expect(screen.getByText('Por quantidade de cotas')).toBeTruthy();
    expect(screen.getByTestId('fund-update-by-amount-row')).toBeTruthy();
    expect(screen.getByTestId('fund-update-gross-value')).toBeTruthy();
    expect(screen.getByTestId('fund-update-unit-price')).toBeTruthy();
  });

  it('shows pricing section with valuedAt only in manual pricing mode', () => {
    render(<Harness isManualPricingMode />);
    expect(screen.getByTestId('fund-update-unit-price')).toBeTruthy();
    expect(screen.getByText('Precificação')).toBeTruthy();
    expect(screen.getByTestId('fund-update-valuedAt')).toBeTruthy();
  });

  it('suggests NAV into unitPrice when trade date is set', async () => {
    mockNavQueries({ navPrice: 15.25 });
    render(<Harness enableNav />);

    await waitFor(() => {
      expect((screen.getByTestId('fund-update-unit-price') as HTMLInputElement).value).toBe('15.25');
    });
  });

  it('re-applies NAV when tradeDate changes', async () => {
    mockNavQueries({ navPrice: 10 });
    render(<Harness enableNav />);

    await waitFor(() => {
      expect((screen.getByTestId('fund-update-unit-price') as HTMLInputElement).value).toBe('10');
    });

    mockNavQueries({ navPrice: 20.5 });
    fireEvent.change(screen.getByTestId('fund-update-trade-date'), {
      target: { value: '2026-07-15' },
    });

    await waitFor(() => {
      expect((screen.getByTestId('fund-update-unit-price') as HTMLInputElement).value).toBe('20.5');
    });
  });

  it('does not overwrite unitPrice when disableAutoUnitPrice is set (edit mode)', async () => {
    mockNavQueries({ navPrice: 99 });
    render(
      <Harness
        enableNav
        disableAutoUnitPrice
        defaultValues={{ unitPrice: 11.11, tradeDate: new Date('2026-07-20T00:00:00') }}
      />,
    );

    // Allow effects to run; they must not overwrite the seeded price.
    await act(async () => {
      await Promise.resolve();
    });

    expect((screen.getByTestId('fund-update-unit-price') as HTMLInputElement).value).toBe('11.11');
  });

  it('derives quantity from grossValue and unitPrice in BY_AMOUNT flow', async () => {
    mockNavQueries({ navPrice: 10 });
    let form: ReturnType<typeof useForm<UpdateHoldingFormValues>> | undefined;
    render(
      <Harness
        enableNav
        onReady={(methods) => {
          form = methods;
        }}
      />,
    );

    await waitFor(() => {
      expect((screen.getByTestId('fund-update-unit-price') as HTMLInputElement).value).toBe('10');
    });

    await act(async () => {
      form?.setValue('grossValue', 1000, { shouldDirty: true, shouldValidate: true });
    });

    await waitFor(() => {
      expect(form?.getValues('quantity')).toBe(100);
    });
  });

  it('derives read-only netValue below taxes on outflow (gross − IR − IOF)', async () => {
    let form: ReturnType<typeof useForm<UpdateHoldingFormValues>> | undefined;
    render(
      <Harness
        direction={InvestmentTransactionDirection.Outflow}
        defaultValues={{ incomeTax: 0, iof: 0 }}
        onReady={(methods) => {
          form = methods;
        }}
      />,
    );

    expect(screen.getByText('Impostos')).toBeTruthy();
    expect(screen.getByText('Totais')).toBeTruthy();
    const netInput = screen.getByTestId('fund-update-net-value') as HTMLInputElement;
    expect(netInput.disabled).toBe(true);

    await act(async () => {
      form?.setValue('grossValue', 1000, { shouldDirty: true, shouldValidate: true });
      form?.setValue('incomeTax', 150, { shouldDirty: true, shouldValidate: true });
      form?.setValue('iof', 50, { shouldDirty: true, shouldValidate: true });
    });

    await waitFor(() => {
      expect(form?.getValues('netValue')).toBe(800);
      expect(netInput.value).toBe('800');
    });
  });
});
