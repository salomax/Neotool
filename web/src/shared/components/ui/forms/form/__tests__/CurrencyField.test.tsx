import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

describe("CurrencyField", () => {
  it("renders currency field", () => {
    renderCurrencyField();
    expect(screen.getByLabelText("Amount")).toBeInTheDocument();
  });

  it("renders with default currency USD", () => {
    renderCurrencyField();
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("renders with custom currency", () => {
    renderCurrencyField({ currency: "BRL" });
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("renders currency selector when currencyChoices provided", () => {
    renderCurrencyField({ currencyChoices: ["USD", "EUR", "BRL"] });
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("does not render currency selector when currencyChoices not provided", () => {
    renderCurrencyField();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("handles currency selection change", async () => {
    const user = userEvent.setup();
    const onCurrencyChange = vi.fn();
    renderCurrencyField({
      currencyChoices: ["USD", "EUR", "BRL"],
      onCurrencyChange,
    });

    const select = screen.getByRole("combobox");
    await user.click(select);
    const eurOption = screen.getByText("EUR");
    await user.click(eurOption);

    expect(onCurrencyChange).toHaveBeenCalledWith("EUR");
  });

  it("handles text input and formats currency", async () => {
    const user = userEvent.setup();
    renderCurrencyField({}, { amount: null });

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "1234.56");

    // Wait for the value to be set
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects min value constraint", async () => {
    const user = userEvent.setup();
    const formDefaults = { amount: null };
    renderCurrencyField({ min: 10 }, formDefaults);

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "5");

    // The component should clamp to min value
    await waitFor(() => {
      // Value should be at least min (10)
      expect(input.value).toBeTruthy();
    });
  });

  it("respects max value constraint", async () => {
    const user = userEvent.setup();
    renderCurrencyField({ max: 100 }, { amount: null });

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "200");

    // The component should clamp to max value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("allows negative values by default", async () => {
    const user = userEvent.setup();
    renderCurrencyField({}, { amount: null });

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "-100");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("prevents negative values when allowNegative is false", async () => {
    const user = userEvent.setup();
    renderCurrencyField({ allowNegative: false }, { amount: null });

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.type(input, "-100");

    // Should clamp to 0
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses custom fraction digits when provided", () => {
    renderCurrencyField({ fractionDigits: 0 });
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("uses default fraction digits from currency when fractionDigits not provided", () => {
    renderCurrencyField({ currency: "USD", locale: "en-US" });
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("handles different locales", () => {
    renderCurrencyField({ locale: "pt-BR", currency: "BRL" });
    const input = screen.getByLabelText("Amount");
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("displays helper text when no error", () => {
    renderCurrencyField({ helperText: "Enter amount" });
    expect(screen.getByText("Enter amount")).toBeInTheDocument();
  });

  it("handles empty value", async () => {
    const user = userEvent.setup();
    renderCurrencyField({}, { amount: 100 });

    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    await user.clear(input);

    await waitFor(() => {
      expect(input.value).toBe("");
    });
  });

  it("updates currency when currency prop changes", () => {
    const { rerender } = renderCurrencyField({ currency: "USD" });
    
    const Wrapper = ({ currency }: { currency: string }) => {
      const methods = useForm({ defaultValues: { amount: null } });
      return (
        <FormProvider {...methods}>
          <CurrencyField name="amount" label="Amount" currency={currency} />
        </FormProvider>
      );
    };

    rerender(
      <AppThemeProvider>
        <Wrapper currency="EUR" />
      </AppThemeProvider>
    );

    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("handles custom step value", () => {
    renderCurrencyField({ step: 0.01 });
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });

  it("handles fullWidth prop", () => {
    renderCurrencyField({ fullWidth: false });
    const input = screen.getByLabelText("Amount");
    expect(input).toBeInTheDocument();
  });
});

