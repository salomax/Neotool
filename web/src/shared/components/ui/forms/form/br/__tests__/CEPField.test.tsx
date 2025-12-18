import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

describe("CEPField", () => {
  it("renders CEP field", () => {
    renderCEPField();
    expect(screen.getByLabelText("CEP")).toBeInTheDocument();
  });

  it("applies correct CEP mask (99999-999)", async () => {
    const user = userEvent.setup();
    renderCEPField({}, { cep: "" });

    const input = screen.getByLabelText("CEP") as HTMLInputElement;
    await user.type(input, "12345678");

    // CEP mask should format as 12345-678
    await waitFor(() => {
      expect(input.value).toBe("12345-678");
    });
  });

  it("uses default placeholder when not provided", () => {
    renderCEPField();
    const input = screen.getByLabelText("CEP");
    expect(input).toHaveAttribute("placeholder", "00000-000");
  });

  it("uses custom placeholder when provided", () => {
    renderCEPField({ placeholder: "Enter CEP" });
    const input = screen.getByLabelText("CEP");
    expect(input).toHaveAttribute("placeholder", "Enter CEP");
  });

  it("handles disabled state", () => {
    renderCEPField({ disabled: true });
    const input = screen.getByLabelText("CEP");
    expect(input).toBeDisabled();
  });

  it("handles custom label", () => {
    renderCEPField({ label: "Postal Code" });
    expect(screen.getByLabelText("Postal Code")).toBeInTheDocument();
  });
});

