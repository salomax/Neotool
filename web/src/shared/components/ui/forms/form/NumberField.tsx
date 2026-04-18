"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField } from "@mui/material";
import type { TextFieldProps } from "@mui/material";
import { NumericFormat } from "react-number-format";
import { getLocaleSeparators } from "@/shared/utils/locale";

export interface NumberFieldProps {
  name: string;
  label?: string;
  min?: number;
  max?: number;
  step?: number;
  allowNegative?: boolean;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  size?: TextFieldProps["size"];
  disabled?: boolean;
  locale?: string;
  fractionDigits?: number;
  fixedDecimalScale?: boolean;
  'data-testid'?: string;
}

export const NumberField: React.FC<NumberFieldProps> = ({
  name,
  label,
  min,
  max,
  step,
  allowNegative = true,
  helperText,
  fullWidth = true,
  size = "medium",
  disabled = false,
  locale = "en-US",
  fractionDigits,
  fixedDecimalScale,
  'data-testid': dataTestId,
}) => {
  const { control } = useFormContext();
  const { decimal, group } = getLocaleSeparators(locale);
  const decimalScale =
    typeof fractionDigits === "number" ? fractionDigits : undefined;
  const computedStep =
    step ??
    (typeof decimalScale === "number" ? Math.pow(10, -decimalScale) : 1);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <NumericFormat
          customInput={TextField}
          value={field.value ?? ""}
          onValueChange={(values) => {
            let v = values.floatValue;
            if (v == null) {
              field.onChange("");
              return;
            }
            if (!allowNegative && v < 0) v = 0;
            if (min != null && v < min) v = min;
            if (max != null && v > max) v = max;
            field.onChange(v);
          }}
          {...({
            thousandSeparator: group || false,
            decimalSeparator: decimal,
            allowNegative: allowNegative,
            decimalScale: decimalScale,
            fixedDecimalScale: typeof decimalScale === "number" && fixedDecimalScale !== false,
            allowLeadingZeros: true,
          } as any)}
          {...(label && { label })}
          size={size}
          fullWidth={fullWidth}
          disabled={disabled}
          error={!!fieldState.error}
          helperText={fieldState.error?.message ?? helperText}
          data-testid={dataTestId}
          InputProps={{
            inputProps: { step: computedStep, style: { textAlign: "right" } },
          }}
        />
      )}
    />
  );
};
