"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { TextField, Select, MenuItem, Stack } from "@mui/material";
import { NumericFormat } from "react-number-format";
import {
  getLocaleSeparators,
  getCurrencyDefaultFractionDigits,
} from "@/shared/utils/locale";

export interface CurrencyFieldProps {
  name: string;
  label?: string;
  currency?: string; // ISO code, e.g., 'USD', 'BRL'
  currencyChoices?: string[]; // show selector if provided
  locale?: string; // e.g., 'en-US', 'pt-BR'
  min?: number;
  max?: number;
  step?: number;
  allowNegative?: boolean;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  onCurrencyChange?: (_code: string) => void;
  /** Fixed number of fraction digits for display & rounding (e.g., 0, 1, 2). If undefined, uses currency default. */
  fractionDigits?: number;
}

export const CurrencyField: React.FC<CurrencyFieldProps> = ({
  name,
  label,
  currency = "USD",
  currencyChoices,
  locale = "en-US",
  min,
  max,
  step,
  allowNegative = true,
  helperText,
  fullWidth = true,
  onCurrencyChange,
  fractionDigits,
}) => {
  const { control } = useFormContext();
  const [cur, setCur] = React.useState(currency);
  React.useEffect(() => setCur(currency), [currency]);
  const { decimal, group } = getLocaleSeparators(locale);
  const defaultDigits = getCurrencyDefaultFractionDigits(locale, cur);
  const digits =
    typeof fractionDigits === "number" ? fractionDigits : defaultDigits;
  const computedStep = step ?? Math.pow(10, -digits);

  const Selector =
    currencyChoices && currencyChoices.length > 0 ? (
      <Select
        size="small"
        value={cur}
        onChange={(e) => {
          setCur(e.target.value as string);
          onCurrencyChange?.(e.target.value as string);
        }}
        sx={{ minWidth: 88 }}
      >
        {currencyChoices.map((c) => (
          <MenuItem key={c} value={c}>
            {c}
          </MenuItem>
        ))}
      </Select>
    ) : null;

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            {Selector}
            <NumericFormat
              customInput={(props) => {
                const { 
                  name, min, max, step, disabled, color, width, height, 
                  size, style, className, id, tabIndex, autoFocus, 
                  autoComplete, autoCorrect, autoCapitalize, spellCheck,
                  placeholder, required, readOnly, form,
                  ...restProps 
                } = props;
                return (
                  <TextField
                    {...restProps}
                    {...(label && { label })}
                    fullWidth={fullWidth}
                    error={!!fieldState.error}
                    helperText={fieldState.error?.message ?? helperText}
                    InputProps={{
                      inputProps: {
                        step: computedStep,
                        style: { textAlign: "right" },
                      },
                    }}
                  />
                );
              }}
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
              thousandSeparator={group || false}
              decimalSeparator={decimal}
              allowNegative={allowNegative}
              decimalScale={digits}
              fixedDecimalScale
              allowLeadingZeros
              inputMode="decimal"
              type="text"
            />
          </Stack>
        );
      }}
    />
  );
};
