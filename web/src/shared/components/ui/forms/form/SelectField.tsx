"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, MenuItem } from "@mui/material";

export interface SelectOption<T = string> {
  label: string;
  value: T;
}

export interface SelectFieldProps<T = string> {
  name: string;
  label?: string;
  options: SelectOption<T>[];
  helperText?: React.ReactNode;
  fullWidth?: boolean;
}

export function SelectField<T = string>({
  name,
  label,
  options,
  helperText,
  fullWidth = true,
}: SelectFieldProps<T>) {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          select
          label={label}
          fullWidth={fullWidth}
          value={field.value ?? ""}
          onChange={(e) => field.onChange(e.target.value as any)}
          error={!!fieldState.error}
          helperText={fieldState.error?.message ?? helperText}
        >
          {options.map((o) => (
            <MenuItem key={String(o.value)} value={o.value as any}>
              {o.label}
            </MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}
