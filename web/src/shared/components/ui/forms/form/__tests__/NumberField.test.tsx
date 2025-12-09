import React from "react";
import { render, screen } from "@testing-library/react";
import { useForm, FormProvider } from "react-hook-form";
import { NumberField } from "../NumberField";

function Wrapper() {
  const methods = useForm({ defaultValues: { amount: 5 } });
  return (
    <FormProvider {...methods}>
      <NumberField name="amount" label="Amount" />
    </FormProvider>
  );
}

describe("NumberField", () => {
  it("renders", () => {
    render(<Wrapper />);
    expect(screen.getByLabelText("Amount")).toBeInTheDocument();
  });
});
