import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm, FormProvider } from "react-hook-form";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { CEPField } from "../CEPField";

const renderCEPField = (
  props: Partial<React.ComponentProps<typeof CEPField>> = {},
  formDefaults: Record<string, any> = {}
) => {
  const Wrapper = () => {
    const methods = useForm({ defaultValues: formDefaults });
    return (
      <FormProvider {...methods}>
        <CEPField name="cep" label="CEP" {...props} />
      </FormProvider>
    );
  };

  return render(
    <AppThemeProvider>
      <Wrapper />
    </AppThemeProvider>
  );
};

describe.sequential("CEPField", () => {
  it("renders CEP field", () => {
    const { container } = renderCEPField();
    const input = within(container).getByLabelText("CEP");
    expect(input).toBeInTheDocument();
  });

  it("applies correct CEP mask (99999-999)", async () => {
    const user = userEvent.setup();
    renderCEPField({}, { cep: "" });

    const input = await screen.findByLabelText("CEP") as HTMLInputElement;

    // Type the CEP number
    await user.type(input, "12345678");

    // CEP mask should format as 12345-678
    // Wait for the masked value to appear in the input
    // IMaskInput may need a moment to process and update the display value
    await waitFor(
      () => {
        expect(input.value).toBe("12345-678");
      },
      { timeout: 3000, interval: 100 }
    );
  });

  it("uses default placeholder when not provided", () => {
    const { container } = renderCEPField();
    const input = within(container).getByLabelText("CEP");
    expect(input).toHaveAttribute("placeholder", "00000-000");
  });

  it("uses custom placeholder when provided", () => {
    const { container } = renderCEPField({ placeholder: "Enter CEP" });
    const input = within(container).getByLabelText("CEP");
    expect(input).toHaveAttribute("placeholder", "Enter CEP");
  });

  it("handles disabled state", () => {
    const { container } = renderCEPField({ disabled: true });
    const input = within(container).getByLabelText("CEP");
    expect(input).toBeDisabled();
  });

  it("handles custom label", () => {
    const { container } = renderCEPField({ label: "Postal Code" });
    const input = within(container).getByLabelText("Postal Code");
    expect(input).toBeInTheDocument();
  });
});

