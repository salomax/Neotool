"use client";

import React, { useState } from "react";
import { Controller, Control, FieldValues, Path } from "react-hook-form";
import {
  Box,
  Typography,
  TextField,
  InputAdornment,
  IconButton,
} from "@mui/material";
import CancelIcon from "@mui/icons-material/Cancel";
import { NumericFormat } from "react-number-format";
import { getLocaleSeparators } from "@/shared/utils/locale";

interface PrincipalInputInnerProps {
  field: { value: number | ""; onChange: (v: number | "") => void; onBlur: () => void };
  fieldState: { error?: { message?: string } };
  requiredMessage: string;
  ns: string;
  t: (key: string) => string;
}

/** Inner principal input with local state to avoid focus loss; syncs to form on blur. */
function PrincipalInputInner({
  field,
  fieldState,
  requiredMessage,
  ns,
  t,
}: PrincipalInputInnerProps) {
  const { decimal, group } = getLocaleSeparators("pt-BR");
  const [prevFieldValue, setPrevFieldValue] = useState(field.value);
  const [localValue, setLocalValue] = useState<string>(() =>
    field.value === "" || field.value == null ? "" : String(Number(field.value))
  );

  if (field.value !== prevFieldValue) {
    setPrevFieldValue(field.value);
    const next =
      field.value === "" || field.value == null ? "" : String(Number(field.value));
    setLocalValue(next);
  }

  const parseLocal = (): number | "" => {
    if (localValue === "") return "";
    const normalized = localValue
      .replace(/\s/g, "")
      .replace(new RegExp(`\\${group ?? "."}`, "g"), "")
      .replace(decimal, ".");
    const num = parseFloat(normalized);
    return Number.isNaN(num) ? "" : num;
  };

  return (
    <Box>
      <Typography
        variant="caption"
        sx={{
          display: "block",
          fontWeight: 700,
          textTransform: "uppercase",
          letterSpacing: "0.05em",
          color: "text.secondary",
          mb: 1,
        }}
      >
        {t(`${ns}.principal`)}
      </Typography>
      <NumericFormat
        customInput={TextField}
        fullWidth
        size="small"
        error={!!fieldState.error}
        helperText={fieldState.error?.message}
        value={localValue}
        onValueChange={(values) => setLocalValue(values.value ?? "")}
        onBlur={() => {
          const parsed = parseLocal();
          setLocalValue(parsed === "" ? "" : String(parsed));
          field.onChange(parsed);
          field.onBlur();
        }}
        thousandSeparator={group ?? false}
        decimalSeparator={decimal}
        decimalScale={2}
        fixedDecimalScale
        allowNegative={false}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">R$</InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                type="button"
                size="small"
                aria-label="reset"
                onClick={() => {
                  setLocalValue("");
                  field.onChange("");
                }}
                sx={{ mr: -0.5 }}
              >
                <CancelIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          ),
          inputProps: { style: { textAlign: "right" } },
        }}
      />
    </Box>
  );
}

interface PrincipalInputProps<T extends FieldValues> {
  name: Path<T>;
  control: Control<T>;
  requiredMessage: string;
  ns: string;
  t: (key: string) => string;
}

export function PrincipalInput<T extends FieldValues>({
  name,
  control,
  requiredMessage,
  ns,
  t,
}: PrincipalInputProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      rules={{
        required: requiredMessage,
        validate: (v) =>
          v === "" || (typeof v === "number" && v <= 0) ? requiredMessage : true,
      }}
      render={({ field, fieldState }) => (
        <PrincipalInputInner
          field={field as any}
          fieldState={fieldState}
          requiredMessage={requiredMessage}
          ns={ns}
          t={t}
        />
      )}
    />
  );
}
