import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

describe("CNPJField", () => {
  it("renders CNPJ field", () => {
    renderCNPJField();
    expect(screen.getByLabelText("CNPJ")).toBeInTheDocument();
  });

  it("applies correct CNPJ mask (99.999.999/9999-99)", async () => {
    const user = userEvent.setup();
    renderCNPJField({}, { cnpj: "" });

    const input = screen.getByLabelText("CNPJ") as HTMLInputElement;
    await user.type(input, "12345678000190");

    // CNPJ mask should format as 12.345.678/0001-90
    await waitFor(() => {
      expect(input.value).toBe("12.345.678/0001-90");
    });
  });

  it("uses default placeholder when not provided", () => {
    renderCNPJField();
    const input = screen.getByLabelText("CNPJ");
    expect(input).toHaveAttribute("placeholder", "00.000.000/0000-00");
  });

  it("uses custom placeholder when provided", () => {
    renderCNPJField({ placeholder: "Enter CNPJ" });
    const input = screen.getByLabelText("CNPJ");
    expect(input).toHaveAttribute("placeholder", "Enter CNPJ");
  });

  it("handles disabled state", () => {
    renderCNPJField({ disabled: true });
    const input = screen.getByLabelText("CNPJ");
    expect(input).toBeDisabled();
  });

  it("handles custom label", () => {
    renderCNPJField({ label: "Company ID" });
    expect(screen.getByLabelText("Company ID")).toBeInTheDocument();
  });
});

