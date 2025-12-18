// web/src/components/form/fields/ControlledPercentField.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";
import { parseLocaleNumber } from "../masks/br";

export type ControlledPercentFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render" | "type"
> & {
  /** stores 0..1 */
  control: Control<any>;
  name: string;
  locale?: string;
  fractionDigits?: number;
};

export function ControlledPercentField({
  control,
  name,
  helperText,
  locale,
  fractionDigits = 0,
  ...rest
}: ControlledPercentFieldProps) {
  const [editing, setEditing] = React.useState(false);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const human = typeof field.value === "number" ? field.value * 100 : "";
        const display = editing
          ? String(human)
          : typeof field.value === "number"
            ? new Intl.NumberFormat(locale, {
                style: "percent",
                minimumFractionDigits: fractionDigits,
                maximumFractionDigits: fractionDigits,
              }).format(field.value)
            : "";
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
              if (parsed === null) return;
              field.onChange(parsed / 100);
            }}
            onChange={(e) => {
              if (!editing) return;
              // keep human %
              const parsed = parseLocaleNumber(e.target.value, locale);
              if (parsed !== null) field.onChange(parsed / 100);
            }}
          />
        );
      }}
    />
  );
}

export default ControlledPercentField;
