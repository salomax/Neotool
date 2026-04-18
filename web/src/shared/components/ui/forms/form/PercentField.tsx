"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, type TextFieldProps } from "@mui/material";
import { NumericFormat } from "react-number-format";
import { getLocaleSeparators } from "@/shared/utils/locale";

export interface PercentFieldProps {
  name: string;
  label?: string;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  /** Defaults to `medium` with other app form fields. */
  size?: TextFieldProps["size"];
  min?: number;
  max?: number;
  step?: number;
  /** If true, keep value as 0..1 ("ratio" mode). Default false stores 0..100. */
  ratio?: boolean;
  /** Fixed number of fraction digits for display & rounding (e.g., 0, 1, 2). */
  fractionDigits?: number;
  locale?: string;
  'data-testid'?: string;
}

export const PercentField: React.FC<PercentFieldProps> = ({
  name,
  label,
  helperText,
  fullWidth = true,
  size = "medium",
  min = 0,
  max = 100,
  step,
  ratio = false,
  fractionDigits,
  locale = "en-US",
  'data-testid': dataTestId,
}) => {
  const { control } = useFormContext();
  const { decimal, group } = getLocaleSeparators(locale);
  const decimalScale =
    typeof fractionDigits === "number" ? fractionDigits : undefined;
  const computedStep =
    step ??
    (typeof decimalScale === "number" ? Math.pow(10, -decimalScale) : 0.1);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        const display = (() => {
          if (field.value == null || field.value === "") return "";
          const n = Number(field.value);
          return ratio ? n * 100 : n;
        })();
        return (
          <NumericFormat
            customInput={TextField}
            {...(label && { label })}
            size={size}
            fullWidth={fullWidth}
            error={!!fieldState.error}
            helperText={fieldState.error?.message ?? helperText}
            data-testid={dataTestId}
            value={display}
            onValueChange={(values) => {
              let v = values.floatValue;
              if (v == null) {
                field.onChange("");
                return;
              }
              if (v < min) v = min;
              if (v > max) v = max;
              field.onChange(ratio ? v / 100 : v);
            }}
            {...({
              thousandSeparator: group || false,
              decimalSeparator: decimal,
              decimalScale: decimalScale,
              fixedDecimalScale: typeof decimalScale === "number",
              allowNegative: min < 0,
              suffix: " %",
            } as any)}
            inputProps={{
              step: computedStep,
              style: { textAlign: "right" as const },
            }}
          />
        );
      }}
    />
  );
};
