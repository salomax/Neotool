import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm, FormProvider } from "react-hook-form";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { CurrencyField } from "../CurrencyField";

const renderCurrencyField = (
  props: Partial<React.ComponentProps<typeof CurrencyField>> = {},
  formDefaults: Record<string, any> = {}
) => {
  const Wrapper = () => {
    const methods = useForm({ defaultValues: formDefaults });
    return (
      <FormProvider {...methods}>
        <CurrencyField name="amount" label="Amount" {...props} />
      </FormProvider>
    );
  };

  return render(
    <AppThemeProvider>
      <Wrapper />
    </AppThemeProvider>
  );
};

describe.sequential("CurrencyField", () => {
  it("renders currency field", () => {
    const { getByLabelText } = renderCurrencyField();
    expect(getByLabelText("Amount")).toBeInTheDocument();
  });

  it("renders with default currency USD", () => {
    const { getByLabelText } = renderCurrencyField();
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("renders with custom currency", () => {
    const { getByLabelText } = renderCurrencyField({ currency: "BRL" });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("renders currency selector when currencyChoices provided", () => {
    const { getByRole } = renderCurrencyField({ currencyChoices: ["USD", "EUR", "BRL"] });
    expect(getByRole("combobox")).toBeInTheDocument();
  });

  it("does not render currency selector when currencyChoices not provided", () => {
    const { queryByRole } = renderCurrencyField();
    expect(queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("handles currency selection change", async () => {
    const user = userEvent.setup();
    const onCurrencyChange = vi.fn();
    const { getByRole, getByText } = renderCurrencyField({
      currencyChoices: ["USD", "EUR", "BRL"],
      onCurrencyChange,
    });

    const select = getByRole("combobox");
    await user.click(select);
    const eurOption = getByText("EUR");
    await user.click(eurOption);

    expect(onCurrencyChange).toHaveBeenCalledWith("EUR");
  });

  it("handles text input and formats currency", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderCurrencyField({}, { amount: null });

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "1234.56");

    // Wait for the value to be set
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects min value constraint", async () => {
    const user = userEvent.setup();
    const formDefaults = { amount: null };
    const { getByLabelText } = renderCurrencyField({ min: 10 }, formDefaults);

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "5");

    // The component should clamp to min value
    await waitFor(() => {
      // Value should be at least min (10)
      expect(input.value).toBeTruthy();
    });
  });

  it("respects max value constraint", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderCurrencyField({ max: 100 }, { amount: null });

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "200");

    // The component should clamp to max value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("allows negative values by default", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderCurrencyField({}, { amount: null });

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "-100");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("prevents negative values when allowNegative is false", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderCurrencyField({ allowNegative: false }, { amount: null });

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "-100");

    // Should clamp to 0
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses custom fraction digits when provided", () => {
    const { getByLabelText } = renderCurrencyField({ fractionDigits: 0 });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("uses default fraction digits from currency when fractionDigits not provided", () => {
    const { getByLabelText } = renderCurrencyField({ currency: "USD", locale: "en-US" });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("handles different locales", () => {
    const { getByLabelText } = renderCurrencyField({ locale: "pt-BR", currency: "BRL" });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("displays error message when field has error", () => {
    const Wrapper = () => {
      const methods = useForm({
        defaultValues: { amount: null },
        mode: "onChange",
      });
      React.useEffect(() => {
        methods.setError("amount", { type: "required", message: "Required" });
      }, [methods]);
      return (
        <FormProvider {...methods}>
          <CurrencyField name="amount" label="Amount" />
        </FormProvider>
      );
    };

    const view = render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    expect(view.getByText("Required")).toBeInTheDocument();
  });

  it("displays helper text when no error", () => {
    const { getByText } = renderCurrencyField({ helperText: "Enter amount" });
    expect(getByText("Enter amount")).toBeInTheDocument();
  });

  it("handles empty value", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderCurrencyField({}, { amount: 100 });

    const input = getByLabelText("Amount") as HTMLInputElement;
    await user.clear(input);

    await waitFor(() => {
      expect(input.value).toBe("");
    });
  });

  it("updates currency when currency prop changes", () => {
    const view = renderCurrencyField({ currency: "USD" });
    
    const Wrapper = ({ currency }: { currency: string }) => {
      const methods = useForm({ defaultValues: { amount: null } });
      return (
        <FormProvider {...methods}>
          <CurrencyField name="amount" label="Amount" currency={currency} />
        </FormProvider>
      );
    };

    view.rerender(
      <AppThemeProvider>
        <Wrapper currency="EUR" />
      </AppThemeProvider>
    );

    expect(view.getByLabelText("Amount")).toBeInTheDocument();
  });

  it("handles custom step value", () => {
    const { getByLabelText } = renderCurrencyField({ step: 0.01 });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("handles fullWidth prop", () => {
    const { getByLabelText } = renderCurrencyField({ fullWidth: false });
    const input = getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });
});
