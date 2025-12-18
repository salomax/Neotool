// web/src/components/form/fields/ControlledTextField.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";

export type ControlledTextFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render"
> & {
  control: Control<any>;
  name: string;
};

export function ControlledTextField({
  control,
  name,
  helperText,
  ...rest
}: ControlledTextFieldProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <TextField
          {...rest}
          {...field}
          fullWidth
          error={!!fieldState.error}
          helperText={fieldState.error?.message || helperText}
        />
      )}
    />
  );
}

export default ControlledTextField;
