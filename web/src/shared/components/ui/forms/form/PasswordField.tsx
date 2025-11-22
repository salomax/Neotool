"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, IconButton, InputAdornment } from "@mui/material";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";

export interface PasswordFieldProps {
  name: string;
  label?: string;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  translateError?: (key: string) => string;
}

export const PasswordField: React.FC<PasswordFieldProps> = ({
  name,
  label,
  helperText,
  fullWidth = true,
  translateError,
}) => {
  const { control } = useFormContext();
  const [show, setShow] = React.useState(false);
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        const errorMessage = fieldState.error?.message;
        const translatedError = errorMessage && translateError
          ? translateError(errorMessage)
          : errorMessage;
        return (
          <TextField
            {...field}
            type={show ? "text" : "password"}
            label={label}
            fullWidth={fullWidth}
            error={!!fieldState.error}
            helperText={translatedError ?? helperText}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    aria-label={show ? "Hide password" : "Show password"}
                    onClick={() => setShow((s) => !s)}
                    edge="end"
                  >
                    {show ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        );
      }}
    />
  );
};
