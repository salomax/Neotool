import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm, FormProvider } from "react-hook-form";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { CNPJField } from "../CNPJField";

const renderCNPJField = (
  props: Partial<React.ComponentProps<typeof CNPJField>> = {},
  formDefaults: Record<string, any> = {}
) => {
  const Wrapper = () => {
    const methods = useForm({ defaultValues: formDefaults });
    return (
      <FormProvider {...methods}>
        <CNPJField name="cnpj" label="CNPJ" {...props} />
      </FormProvider>
    );
  };

  return render(
    <AppThemeProvider>
      <Wrapper />
    </AppThemeProvider>
  );
};

describe.sequential("CNPJField", () => {
  it("renders CNPJ field", () => {
    const { container } = renderCNPJField();
    const input = within(container).getByLabelText("CNPJ");
    expect(input).toBeInTheDocument();
  });

  it("applies correct CNPJ mask (99.999.999/9999-99)", async () => {
    const user = userEvent.setup();
    renderCNPJField({}, { cnpj: "" });

    const input = await screen.findByLabelText("CNPJ") as HTMLInputElement;
    await user.type(input, "12345678000190");

    // CNPJ mask should format as 12.345.678/0001-90
    // Wait for the masked value to appear in the input
    // IMaskInput may need a moment to process and update the display value
    await waitFor(
      () => {
        expect(input.value).toBe("12.345.678/0001-90");
      },
      { timeout: 3000, interval: 100 }
    );
  });

  it("uses default placeholder when not provided", () => {
    const { container } = renderCNPJField();
    const input = within(container).getByLabelText("CNPJ");
    expect(input).toHaveAttribute("placeholder", "00.000.000/0000-00");
  });

  it("uses custom placeholder when provided", () => {
    const { container } = renderCNPJField({ placeholder: "Enter CNPJ" });
    const input = within(container).getByLabelText("CNPJ");
    expect(input).toHaveAttribute("placeholder", "Enter CNPJ");
  });

  it("handles disabled state", () => {
    const { container } = renderCNPJField({ disabled: true });
    const input = within(container).getByLabelText("CNPJ");
    expect(input).toBeDisabled();
  });

  it("handles custom label", () => {
    const { container } = renderCNPJField({ label: "Company ID" });
    const input = within(container).getByLabelText("Company ID");
    expect(input).toBeInTheDocument();
  });
});
