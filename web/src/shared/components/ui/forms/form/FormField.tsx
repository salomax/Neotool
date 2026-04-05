"use client";
import React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, TextFieldProps } from "./TextField";

export interface FormTextFieldProps extends Omit<TextFieldProps, "name"> {
  name: string;
}
export const FormTextField: React.FC<FormTextFieldProps> = ({
  name,
  size = "small",
  ...props
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          size={size}
          {...props}
          {...field}
          error={!!fieldState.error}
          helperText={fieldState.error?.message}
        />
      )}
    />
  );
};
