"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, MenuItem, Stack, type TextFieldProps } from "@mui/material";

export interface SelectOption<T = string> {
  label: string;
  value: T;
  /** Optional leading icon rendered inside each MenuItem and the selected-value adornment. */
  icon?: React.ReactNode;
}

export interface SelectFieldProps<T = string> {
  name: string;
  label?: string;
  options: SelectOption<T>[];
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  size?: TextFieldProps["size"];
  onChange?: (value: T | null) => void;
  'data-testid'?: string;
}

export function SelectField<T = string>({
  name,
  label,
  options,
  helperText,
  fullWidth = true,
  size,
  onChange,
  'data-testid': dataTestId,
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
          size={size ?? "medium"}
          value={field.value ?? ""}
          onChange={(e) => {
            const value = (e.target.value as any) ?? "";
            field.onChange(value as any);
            onChange?.((value === "" ? null : (value as any)) as any);
          }}
          error={!!fieldState.error}
          helperText={fieldState.error?.message ?? helperText}
          data-testid={dataTestId}
        >
          {options.map((o) => (
            <MenuItem key={String(o.value)} value={o.value as any}>
              {o.icon ? (
                <Stack direction="row" spacing={1} alignItems="center">
                  {o.icon}
                  <span>{o.label}</span>
                </Stack>
              ) : (
                o.label
              )}
            </MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}
