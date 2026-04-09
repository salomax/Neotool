"use client";

import React, { useState, useEffect } from "react";
import { Controller, useFormContext } from "react-hook-form";
import {
  TextField,
  InputAdornment,
  IconButton,
  TextFieldProps,
} from "@mui/material";
import CancelIcon from "@mui/icons-material/Cancel";
import { NumericFormat } from "react-number-format";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { i18n } from "@/shared/i18n";

export interface MoneyInputProps {
  name: string;
  label?: string;
  currency?: string;
  locale?: string;
  placeholder?: string;
  required?: boolean;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  disabled?: boolean;
  size?: TextFieldProps["size"];
  InputProps?: TextFieldProps["InputProps"];
  autoFocus?: boolean;
  variant?: TextFieldProps["variant"];
  'data-testid'?: string;
  /** When true, users can enter negative amounts (e.g. initial balance / debt). Default false. */
  allowNegative?: boolean;
}

export const MoneyInput: React.FC<MoneyInputProps> = (props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={props.name}
      control={control}
      render={({ field, fieldState }) => (
        <BaseMoneyInput
          {...props}
          value={field.value}
          onChange={field.onChange}
          onBlur={field.onBlur}
          error={!!fieldState.error}
          helperText={fieldState.error?.message ?? props.helperText}
        />
      )}
    />
  );
};

export interface BaseMoneyInputProps extends Omit<MoneyInputProps, 'name'> {
  value: any;
  onChange: (value: any) => void;
  onBlur?: () => void;
  error?: boolean;
  name?: string;
}

export function BaseMoneyInput({
  value,
  onChange,
  onBlur,
  error,
  label,
  currency = "BRL",
  locale,
  placeholder,
  required,
  helperText,
  fullWidth = true,
  disabled,
  size,
  InputProps,
  name,
  autoFocus,
  variant,
  'data-testid': dataTestId,
  allowNegative = false,
}: BaseMoneyInputProps) {
  // Determine locale and separators
  const currentLocale = locale || i18n.language || "pt-BR";
  const { decimal, group } = getLocaleSeparators(currentLocale);
  const currencySymbol = currency === "BRL" ? "R$" : currency;

  // Local state to manage input value and avoid cursor jumps
  const [localValue, setLocalValue] = useState<string>("");

  // Sync with external value changes (e.g. form reset)
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect -- Controlled input sync with form value */
    if (value === undefined || value === null || value === "") {
      setLocalValue("");
    } else {
      setLocalValue(String(value));
    }
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [value]);

  return (
    <NumericFormat
      customInput={TextField}
      variant={variant}
      fullWidth={fullWidth}
      size={size}
      autoFocus={autoFocus}
      label={label}
      data-testid={dataTestId}
      name={name}
      required={required}
      error={error}
      helperText={helperText}
      placeholder={placeholder}
      disabled={disabled}
      value={localValue}
      onValueChange={(values) => {
        setLocalValue(values.value);
        // Update parent form value immediately
        const numVal = values.floatValue;
        onChange(numVal);
      }}
      onBlur={onBlur}
      thousandSeparator={group ?? false}
      decimalSeparator={decimal}
      decimalScale={2}
      fixedDecimalScale
      allowNegative={allowNegative}
      InputProps={{
        ...InputProps,
        startAdornment: (
          <InputAdornment position="start">
            {currencySymbol}
          </InputAdornment>
        ),
        endAdornment: (
          <>
            {localValue && !disabled && (
              <InputAdornment position="end" sx={{ ml: 0 }}>
                <IconButton
                  size="small"
                  aria-label="clear"
                  onClick={() => {
                    setLocalValue("");
                    onChange(undefined);
                  }}
                  edge="end"
                  sx={(theme) => ({
                    p: 0,
                    ml: -0.25,
                    mr: -0.75,
                    color: theme.palette.action.disabled,
                    "&:hover": { color: theme.palette.text.secondary },
                  })}
                >
                  <CancelIcon sx={{ fontSize: 16, mb: 0 }} />
                </IconButton>
              </InputAdornment>
            )}
            {InputProps?.endAdornment}
          </>
        ),
        inputProps: { 
          style: { textAlign: "right" },
          ...InputProps?.inputProps 
        },
      }}
    />
  );
}
