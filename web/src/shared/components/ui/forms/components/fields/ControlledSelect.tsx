// web/src/components/form/fields/ControlledSelect.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, MenuItem, TextFieldProps } from "@mui/material";

export type Option = { label: string; value: string | number };

export type ControlledSelectProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render" | "select"
> & {
  control: Control<any>;
  name: string;
  options: Option[];
};

export function ControlledSelect({
  control,
  name,
  options,
  helperText,
  ...rest
}: ControlledSelectProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <TextField
          {...rest}
          {...field}
          select
          fullWidth
          error={!!fieldState.error}
          helperText={fieldState.error?.message || helperText}
        >
          {options.map((opt) => (
            <MenuItem key={String(opt.value)} value={opt.value}>
              {opt.label}
            </MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}

export default ControlledSelect;
