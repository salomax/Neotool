import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RecordFundHoldingDrawer } from "../RecordFundHoldingDrawer";
import { AssetType } from "@/lib/graphql/types/__generated__/graphql";

const mockUseMutation = vi.fn();
const mockUseQuery = vi.fn();

vi.mock("@apollo/client/react", () => ({
  useApolloClient: () => ({
    refetchQueries: vi.fn(),
  }),
  useMutation: (mutation: unknown) => mockUseMutation(mutation),
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
}));

vi.mock("@/shared/components/ui/forms/form", async () => {
  const rhf = await vi.importActual<typeof import("react-hook-form")>("react-hook-form");

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
          value={field.value ?? ""}
          onChange={(event) => field.onChange(event.target.value === "" ? undefined : Number(event.target.value))}
        />
      </div>
    );
  };

  return {
    MoneyInput: ({
      name,
      label,
      "data-testid": dataTestId,
    }: {
      name: string;
      label?: string;
      "data-testid"?: string;
    }) => renderNumberInput({ name, label, dataTestId }),
    NumberField: ({
      name,
      label,
      "data-testid": dataTestId,
    }: {
      name: string;
      label?: string;
      "data-testid"?: string;
    }) => renderNumberInput({ name, label, dataTestId }),
    PercentField: ({
      name,
      label,
      "data-testid": dataTestId,
    }: {
      name: string;
      label?: string;
      "data-testid"?: string;
    }) => renderNumberInput({ name, label, dataTestId }),
    DatePickerField: ({
      name,
      label,
      "data-testid": dataTestId,
    }: {
      name: string;
      label?: string;
      "data-testid"?: string;
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
            type="date"
            value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ""}
            onChange={(event) =>
              field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)
            }
          />
        </div>
      );
    },
  };
});

vi.mock("@/shared/components/ui/layout/Drawer", () => ({
  Drawer: {
    ActionButtons: ({
      submitForm,
      submitText,
      onCancel,
    }: {
      submitForm?: string;
      submitText?: string;
      onCancel: () => void;
    }) => (
      <div>
        <button type="button" onClick={onCancel}>
          cancel
        </button>
        <button type="submit" form={submitForm}>
          {submitText}
        </button>
      </div>
    ),
  },
}));

vi.mock("@/shared/providers", () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  }),
}));

vi.mock("@/shared/i18n", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: {
    language: "pt-BR",
  },
}));

vi.mock("@/shared/components/ui/feedback", () => ({
  ErrorAlert: () => null,
}));

vi.mock("@/shared/components/ui/forms/components/FormStack", () => ({
  FormStack: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/shared/components/ui/forms/components/FormRowStack", () => ({
  FormRowStack: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/shared/components/ui/forms/components/FormTabs", () => ({
  FormTabs: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("../FundAutocomplete", async () => {
  const rhf = await vi.importActual<typeof import("react-hook-form")>("react-hook-form");

  return {
    FundAutocomplete: () => {
      const { setValue, watch } = rhf.useFormContext();
      const value = watch("fund");

      React.useEffect(() => {
        if (value) return;
        setValue("fund", {
          id: "fund-provider-1",
          name: "Fundo XP",
          cnpj: "12345678000199",
          assetType: "MUTUAL_FUND",
          isActive: true,
        });
      }, [setValue, value]);

      return <div data-testid="record-fund-autocomplete" />;
    },
  };
});

vi.mock("../InvestmentTradeAndSettlementDateRow", async () => {
  const rhf = await vi.importActual<typeof import("react-hook-form")>("react-hook-form");

  const BoundDateField = ({
    name,
    label,
    dataTestId,
  }: {
    name: string;
    label: string;
    dataTestId: string;
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
          type="date"
          value={field.value instanceof Date ? field.value.toISOString().slice(0, 10) : ""}
          onChange={(event) => field.onChange(event.target.value ? new Date(`${event.target.value}T00:00:00`) : undefined)}
        />
      </div>
    );
  };

  return {
    InvestmentTradeAndSettlementDateRow: () => (
      <div>
        <BoundDateField
          name="tradeDate"
          label="investments.recordFund.fields.tradeDate"
          dataTestId="record-fund-trade-date"
        />
        <BoundDateField
          name="settlementDate"
          label="investments.recordFund.fields.settlementDate"
          dataTestId="record-fund-settlement-date"
        />
      </div>
    ),
  };
});

vi.mock("../InvestmentRecordDrawer", () => ({
  InvestmentRecordDrawer: ({
    children,
  }: {
    children: (args: { resolvedFinancialAccountId: string }) => React.ReactNode;
  }) => <div>{children({ resolvedFinancialAccountId: "account-1" })}</div>,
}));

vi.mock("../update/UpdateFormSectionLabel", () => ({
  UpdateFormSectionLabel: ({ title }: { title: string }) => <div>{title}</div>,
}));

vi.mock("@/shared/components/ui/inputs/SegmentedControl", () => ({
  SegmentedControl: () => null,
}));

vi.mock("@/shared/components/ui/primitives/Label", () => ({
  Label: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("../hooks/useSyncDefaultSettlementDate", () => ({
  useSyncDefaultSettlementDate: vi.fn(),
}));

describe("RecordFundHoldingDrawer", () => {
  beforeEach(() => {
    cleanup();
    mockUseQuery.mockReturnValue({ data: null, loading: false, error: null });
    mockUseMutation.mockReturnValue([vi.fn(), { loading: false, error: null }]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders the administration fee input", () => {
    render(
      <RecordFundHoldingDrawer
        open
        onClose={vi.fn()}
        prefillHolding={{
          id: "holding-1",
          asset: {
            id: "asset-1",
            name: "Fundo XP",
            assetType: AssetType.MutualFund,
          },
        }}
      />,
    );

    expect(screen.getByLabelText("investments.recordFund.fields.administrationFeeAnnualPercent")).toBeInTheDocument();
  });
});
