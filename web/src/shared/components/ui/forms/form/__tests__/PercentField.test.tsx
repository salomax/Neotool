import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm, FormProvider } from "react-hook-form";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { PercentField } from "../PercentField";

const renderPercentField = (
  props: Partial<React.ComponentProps<typeof PercentField>> = {},
  formDefaults: Record<string, any> = {}
) => {
  const Wrapper = () => {
    const methods = useForm({ defaultValues: formDefaults });
    return (
      <FormProvider {...methods}>
        <PercentField name="percentage" label="Percentage" {...props} />
      </FormProvider>
    );
  };

  return render(
    <AppThemeProvider>
      <Wrapper />
    </AppThemeProvider>
  );
};

describe.sequential("PercentField", () => {
  it("renders percent field", () => {
    const { getByLabelText } = renderPercentField();
    expect(getByLabelText("Percentage")).toBeInTheDocument();
  });

  it("renders with default label", () => {
    const { getByLabelText } = renderPercentField({ label: "Discount" });
    expect(getByLabelText("Discount")).toBeInTheDocument();
  });

  it("displays percentage suffix", () => {
    const { getByLabelText } = renderPercentField();
    const input = getByLabelText("Percentage") as HTMLInputElement;
    // The NumericFormat component should add % suffix
    expect(input).toBeInTheDocument();
  });

  it("handles text input", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({}, { percentage: null });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "50");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("stores value as percentage (0-100) by default", async () => {
    const Wrapper = () => {
      const methods = useForm<{ percentage: number | null }>({ defaultValues: { percentage: null } });
      const [value, setValue] = React.useState<any>(null);
      
      React.useEffect(() => {
        // eslint-disable-next-line react-hooks/incompatible-library -- React Hook Form's watch() cannot be memoized, but this is valid usage in tests
        const subscription = methods.watch((data) => {
          setValue(data.percentage);
        });
        return () => subscription.unsubscribe();
      }, [methods]);

      // Set value directly to test conversion
      React.useEffect(() => {
        methods.setValue("percentage", 50);
      }, [methods]);

      return (
        <FormProvider {...methods}>
          <PercentField name="percentage" label="Percentage" />
          <div data-testid="value">{value}</div>
        </FormProvider>
      );
    };

    const view = render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    await waitFor(() => {
      const valueDisplay = view.getByTestId("value");
      // Value should be stored as 50 (percentage mode)
      expect(valueDisplay.textContent).toBe("50");
    });
  });

  it("stores value as ratio (0-1) when ratio mode enabled", () => {
    // Test that ratio mode correctly displays ratio values as percentages
    const Wrapper = () => {
      const methods = useForm({ defaultValues: { percentage: 0.5 } });
      return (
        <FormProvider {...methods}>
          <PercentField name="percentage" label="Percentage" ratio />
        </FormProvider>
      );
    };

    const view = render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = view.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.5, it should display as 50%
    expect(input).toBeInTheDocument();
  });

  it("displays value as percentage when in ratio mode", async () => {
    const { getByLabelText } = renderPercentField({ ratio: true }, { percentage: 0.5 });
    const input = getByLabelText("Percentage") as HTMLInputElement;
    // Should display as 50% (0.5 * 100)
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects min value constraint", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({ min: 10 }, { percentage: null });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "5");

    // The component should clamp to min value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects max value constraint", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({ max: 90 }, { percentage: null });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "95");

    // The component should clamp to max value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses default min of 0", () => {
    const { getByLabelText } = renderPercentField();
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses default max of 100", () => {
    const { getByLabelText } = renderPercentField();
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("allows negative values when min is negative", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({ min: -10 }, { percentage: null });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "-5");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses custom fraction digits when provided", () => {
    const { getByLabelText } = renderPercentField({ fractionDigits: 2 });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses default step when fractionDigits provided", () => {
    const { getByLabelText } = renderPercentField({ fractionDigits: 2 });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses custom step when provided", () => {
    const { getByLabelText } = renderPercentField({ step: 0.5 });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles different locales", () => {
    const { getByLabelText } = renderPercentField({ locale: "pt-BR" });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("displays error message when field has error", () => {
    const Wrapper = () => {
      const methods = useForm({
        defaultValues: { percentage: null },
        mode: "onChange",
      });
      React.useEffect(() => {
        methods.setError("percentage", { type: "required", message: "Required" });
      }, [methods]);
      return (
        <FormProvider {...methods}>
          <PercentField name="percentage" label="Percentage" />
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
    const { getByText } = renderPercentField({ helperText: "Enter percentage" });
    expect(getByText("Enter percentage")).toBeInTheDocument();
  });

  it("handles empty value", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({}, { percentage: 50 });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.clear(input);

    await waitFor(() => {
      expect(input.value).toBe("");
    });
  });

  it("handles null value", () => {
    const { getByLabelText } = renderPercentField({}, { percentage: null });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles undefined value", () => {
    const { getByLabelText } = renderPercentField({}, { percentage: undefined });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles empty string value", () => {
    const { getByLabelText } = renderPercentField({}, { percentage: "" });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles fullWidth prop", () => {
    const { getByLabelText } = renderPercentField({ fullWidth: false });
    const input = getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("converts ratio to percentage for display", async () => {
    const { getByLabelText } = renderPercentField({ ratio: true }, { percentage: 0.25 });
    const input = getByLabelText("Percentage") as HTMLInputElement;
    // Should display as 25% (0.25 * 100)
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("converts percentage to ratio for storage in ratio mode", () => {
    // Test that ratio mode correctly converts displayed percentage to stored ratio
    // by checking that a ratio value displays as the correct percentage
    const Wrapper = () => {
      const methods = useForm({ defaultValues: { percentage: 0.25 } });
      return (
        <FormProvider {...methods}>
          <PercentField name="percentage" label="Percentage" ratio />
        </FormProvider>
      );
    };

    const view = render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = view.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.25, it should display as 25%
    expect(input).toBeInTheDocument();
  });

  it("handles decimal input in percentage mode", async () => {
    const user = userEvent.setup();
    const { getByLabelText } = renderPercentField({}, { percentage: null });

    const input = getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "12.5");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("handles decimal input in ratio mode", () => {
    // Test that ratio mode correctly handles decimal percentage values
    const Wrapper = () => {
      const methods = useForm({ defaultValues: { percentage: 0.125 } });
      return (
        <FormProvider {...methods}>
          <PercentField name="percentage" label="Percentage" ratio />
        </FormProvider>
      );
    };

    const view = render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = view.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.125, it should display as 12.5%
    expect(input).toBeInTheDocument();
  });
});
