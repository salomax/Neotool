"use client";

import * as React from "react";
import { IMaskInput } from "react-imask";
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

// Convert react-input-mask format (9 = digit) to react-imask format (0 = digit)
const convertMaskFormat = (mask: string): string => {
  return mask.replace(/9/g, "0");
};

// Custom input component that wraps IMaskInput for Material-UI TextField
const MaskedInput = React.forwardRef<
  HTMLInputElement,
  {
    onChange: (event: { target: { name: string; value: string } }) => void;
    name: string;
    mask: string;
    value: string;
    disabled?: boolean;
  }
>(function MaskedInput(props, ref) {
  const { onChange, mask, ...other } = props;
  const convertedMask = convertMaskFormat(mask);
  
  return (
    <IMaskInput
      {...other}
      mask={convertedMask}
      definitions={{
        "0": /[0-9]/,
      }}
      inputRef={ref}
      onAccept={(value: string) => {
        onChange({
          target: {
            name: props.name,
            value,
          },
        });
      }}
      overwrite
    />
  );
});

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
        <TextField
          label={label}
          placeholder={placeholder}
          fullWidth={fullWidth}
          disabled={disabled}
          error={!!fieldState.error}
          helperText={fieldState.error?.message}
          value={field.value ?? ""}
          onBlur={field.onBlur}
          InputProps={{
            inputComponent: MaskedInput as any,
            inputProps: {
              mask: mask,
              name: name,
              onChange: field.onChange,
              value: field.value ?? "",
              disabled: disabled,
              ...inputProps,
            },
          }}
        />
      )}
    />
  );
};
