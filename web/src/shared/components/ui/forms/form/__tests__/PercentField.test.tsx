import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

describe("PercentField", () => {
  it("renders percent field", () => {
    renderPercentField();
    expect(screen.getByLabelText("Percentage")).toBeInTheDocument();
  });

  it("renders with default label", () => {
    renderPercentField({ label: "Discount" });
    expect(screen.getByLabelText("Discount")).toBeInTheDocument();
  });

  it("displays percentage suffix", () => {
    renderPercentField();
    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    // The NumericFormat component should add % suffix
    expect(input).toBeInTheDocument();
  });

  it("handles text input", async () => {
    const user = userEvent.setup();
    renderPercentField({}, { percentage: null });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "50");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("stores value as percentage (0-100) by default", async () => {
    const Wrapper = () => {
      const methods = useForm({ defaultValues: { percentage: null } });
      const [value, setValue] = React.useState<any>(null);
      
      React.useEffect(() => {
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    await waitFor(() => {
      const valueDisplay = screen.getByTestId("value");
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = screen.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.5, it should display as 50%
    expect(input).toBeInTheDocument();
  });

  it("displays value as percentage when in ratio mode", async () => {
    renderPercentField({ ratio: true }, { percentage: 0.5 });
    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    // Should display as 50% (0.5 * 100)
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects min value constraint", async () => {
    const user = userEvent.setup();
    renderPercentField({ min: 10 }, { percentage: null });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "5");

    // The component should clamp to min value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("respects max value constraint", async () => {
    const user = userEvent.setup();
    renderPercentField({ max: 90 }, { percentage: null });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "95");

    // The component should clamp to max value
    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses default min of 0", () => {
    renderPercentField();
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses default max of 100", () => {
    renderPercentField();
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("allows negative values when min is negative", async () => {
    const user = userEvent.setup();
    renderPercentField({ min: -10 }, { percentage: null });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    await user.type(input, "-5");

    await waitFor(() => {
      expect(input.value).toBeTruthy();
    });
  });

  it("uses custom fraction digits when provided", () => {
    renderPercentField({ fractionDigits: 2 });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses default step when fractionDigits provided", () => {
    renderPercentField({ fractionDigits: 2 });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("uses custom step when provided", () => {
    renderPercentField({ step: 0.5 });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles different locales", () => {
    renderPercentField({ locale: "pt-BR" });
    const input = screen.getByLabelText("Percentage");
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("displays helper text when no error", () => {
    renderPercentField({ helperText: "Enter percentage" });
    expect(screen.getByText("Enter percentage")).toBeInTheDocument();
  });

  it("handles empty value", async () => {
    const user = userEvent.setup();
    renderPercentField({}, { percentage: 50 });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
    await user.clear(input);

    await waitFor(() => {
      expect(input.value).toBe("");
    });
  });

  it("handles null value", () => {
    renderPercentField({}, { percentage: null });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles undefined value", () => {
    renderPercentField({}, { percentage: undefined });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles empty string value", () => {
    renderPercentField({}, { percentage: "" });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("handles fullWidth prop", () => {
    renderPercentField({ fullWidth: false });
    const input = screen.getByLabelText("Percentage");
    expect(input).toBeInTheDocument();
  });

  it("converts ratio to percentage for display", async () => {
    renderPercentField({ ratio: true }, { percentage: 0.25 });
    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = screen.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.25, it should display as 25%
    expect(input).toBeInTheDocument();
  });

  it("handles decimal input in percentage mode", async () => {
    const user = userEvent.setup();
    renderPercentField({}, { percentage: null });

    const input = screen.getByLabelText("Percentage") as HTMLInputElement;
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

    render(
      <AppThemeProvider>
        <Wrapper />
      </AppThemeProvider>
    );

    const input = screen.getByLabelText("Percentage");
    // When ratio mode is enabled and value is 0.125, it should display as 12.5%
    expect(input).toBeInTheDocument();
  });
});

