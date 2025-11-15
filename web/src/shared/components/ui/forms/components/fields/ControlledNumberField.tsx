// web/src/components/form/fields/ControlledNumberField.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";
import { parseLocaleNumber } from "../masks/br";

export type ControlledNumberFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render" | "type"
> & {
  control: Control<any>;
  name: string;
  locale?: string;
};

export function ControlledNumberField({
  control,
  name,
  helperText,
  locale,
  ...rest
}: ControlledNumberFieldProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <TextField
          {...rest}
          {...field}
          fullWidth
          inputProps={{ inputMode: "decimal", style: { textAlign: "right" } }}
          error={!!fieldState.error}
          helperText={fieldState.error?.message || helperText}
          onChange={(e) => {
            const raw = e.target.value;
            const parsed = parseLocaleNumber(raw, locale);
            field.onChange(parsed);
          }}
          value={field.value ?? ""}
        />
      )}
    />
  );
}

export default ControlledNumberField;
