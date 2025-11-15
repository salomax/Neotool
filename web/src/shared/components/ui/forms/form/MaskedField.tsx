"use client";

import * as React from "react";
import InputMask from "react-input-mask";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";

export type MaskedFieldProps = {
  name: string;
  label?: string;
  mask: string; // e.g. "(99) 99999-9999" or "999.999.999-99"
  placeholder?: string;
  disabled?: boolean;
  fullWidth?: boolean;
  inputProps?: TextFieldProps["inputProps"];
};

export const MaskedField: React.FC<MaskedFieldProps> = ({
  name,
  label,
  mask,
  placeholder,
  disabled,
  fullWidth = true,
  inputProps,
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <InputMask
          mask={mask}
          value={field.value ?? ""}
          onChange={field.onChange}
          onBlur={field.onBlur}
          disabled={disabled}
        >
          {(inputProps2: any) => (
            <TextField
              {...inputProps2}
              label={label}
              placeholder={placeholder}
              fullWidth={fullWidth}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              inputProps={inputProps}
            />
          )}
        </InputMask>
      )}
    />
  );
};
