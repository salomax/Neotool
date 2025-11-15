// web/src/components/form/fields/ControlledCurrencyField.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";
import { formatCurrency, parseLocaleNumber } from "../masks/br";

export type ControlledCurrencyFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render" | "type"
> & {
  control: Control<any>;
  name: string;
  currency?: string;
  locale?: string;
};

export function ControlledCurrencyField({
  control,
  name,
  helperText,
  currency = "BRL",
  locale,
  ...rest
}: ControlledCurrencyFieldProps) {
  const [editing, setEditing] = React.useState(false);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const display = editing
          ? String(field.value ?? "")
          : formatCurrency(field.value ?? 0, currency, locale);
        return (
          <TextField
            {...rest}
            fullWidth
            value={display}
            error={!!fieldState.error}
            helperText={fieldState.error?.message || helperText}
            inputProps={{ inputMode: "decimal", style: { textAlign: "right" } }}
            onFocus={() => setEditing(true)}
            onBlur={(e) => {
              setEditing(false);
              const parsed = parseLocaleNumber(e.target.value, locale);
              field.onChange(parsed ?? field.value ?? 0);
            }}
            onChange={(e) => {
              const raw = e.target.value;
              // keep raw while editing
              if (editing) {
                field.onChange(parseLocaleNumber(raw, locale));
              }
            }}
          />
        );
      }}
    />
  );
}

export default ControlledCurrencyField;
