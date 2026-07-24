"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import {
  IconButton,
  InputAdornment,
  TextField,
  type TextFieldProps,
  type SxProps,
  type Theme,
} from "@mui/material";
import CancelIcon from "@mui/icons-material/Cancel";
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
  disabled?: boolean;
  /** Optional styles for the clear (×) icon button when the field has a value. */
  clearButtonSx?: SxProps<Theme>;
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
  disabled = false,
  clearButtonSx,
  'data-testid': dataTestId,
}) => {
  const { control } = useFormContext();
  const { decimal, group } = getLocaleSeparators(locale);
  const decimalScale =
    typeof fractionDigits === "number" ? fractionDigits : undefined;
  const computedStep =
    step ??
    (typeof decimalScale === "number" ? Math.pow(10, -decimalScale) : 0.1);

  const baseClearButtonSx: SxProps<Theme> = (theme) => ({
    px: 1,
    ml: -0.25,
    mr: -0.75,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    // Standard `Input` uses slightly asymmetric vertical padding on the text field,
    // so a geometrically centered icon reads low next to digits.
    transform: "translateY(-2px)",
    color: theme.palette.action.disabled,
    "&:hover": { color: theme.palette.text.secondary },
  });

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
        const hasValue = display !== "" && display != null && !Number.isNaN(Number(display));

        return (
          <NumericFormat
            customInput={TextField}
            {...(label && { label })}
            size={size}
            fullWidth={fullWidth}
            disabled={disabled}
            error={!!fieldState.error}
            helperText={fieldState.error?.message ?? helperText}
            data-testid={dataTestId}
            value={display}
            onValueChange={(values) => {
              let v = values.floatValue;
              if (v == null) {
                field.onChange(null);
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
            InputProps={{
              endAdornment:
                hasValue && !disabled ? (
                  <InputAdornment
                    position="end"
                    sx={{
                      ml: 0,
                      alignSelf: "center",
                      display: "flex",
                      alignItems: "center",
                      maxHeight: "none",
                    }}
                  >
                    <IconButton
                      size="small"
                      aria-label="clear"
                      onClick={() => field.onChange(null)}
                      edge="end"
                      sx={
                        clearButtonSx
                          ? Array.isArray(clearButtonSx)
                            ? [baseClearButtonSx, ...clearButtonSx]
                            : [baseClearButtonSx, clearButtonSx]
                          : [baseClearButtonSx]
                      }
                    >
                      <CancelIcon sx={{ fontSize: 16, display: "block" }} />
                    </IconButton>
                  </InputAdornment>
                ) : undefined,
            }}
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
