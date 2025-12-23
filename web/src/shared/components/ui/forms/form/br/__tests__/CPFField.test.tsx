import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm, FormProvider } from "react-hook-form";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { CPFField } from "../CPFField";

const renderCPFField = (
  props: Partial<React.ComponentProps<typeof CPFField>> = {},
  formDefaults: Record<string, any> = {}
) => {
  const Wrapper = () => {
    const methods = useForm({ defaultValues: formDefaults });
    return (
      <FormProvider {...methods}>
        <CPFField name="cpf" label="CPF" {...props} />
      </FormProvider>
    );
  };

  return render(
    <AppThemeProvider>
      <Wrapper />
    </AppThemeProvider>
  );
};

 describe.sequential("CPFField", () => {
  it("renders CPF field", () => {
    const { container } = renderCPFField();
    const input = within(container).getByLabelText("CPF");
    expect(input).toBeInTheDocument();
  });

  it("applies correct CPF mask (999.999.999-99)", async () => {
    const user = userEvent.setup();
    renderCPFField({}, { cpf: "" });

    const input = await screen.findByLabelText("CPF") as HTMLInputElement;

    // Type the CPF number
    await user.type(input, "12345678901");

    // CPF mask should format as 123.456.789-01
    // Wait for the masked value to appear in the input
    // IMaskInput may need a moment to process and update the display value
    await waitFor(
      () => {
        expect(input.value).toBe("123.456.789-01");
      },
      { timeout: 3000, interval: 100 }
    );
  });

  it("uses default placeholder when not provided", () => {
    const { container } = renderCPFField();
    const input = within(container).getByLabelText("CPF");
    expect(input).toHaveAttribute("placeholder", "000.000.000-00");
  });

  it("uses custom placeholder when provided", () => {
    const { container } = renderCPFField({ placeholder: "Enter CPF" });
    const input = within(container).getByLabelText("CPF");
    expect(input).toHaveAttribute("placeholder", "Enter CPF");
  });

  it("handles disabled state", () => {
    const { container } = renderCPFField({ disabled: true });
    const input = within(container).getByLabelText("CPF");
    expect(input).toBeDisabled();
  });

  it("handles custom label", () => {
    const { container } = renderCPFField({ label: "Tax ID" });
    const input = within(container).getByLabelText("Tax ID");
    expect(input).toBeInTheDocument();
  });
});
