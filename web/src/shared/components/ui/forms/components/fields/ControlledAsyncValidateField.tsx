// web/src/components/form/fields/ControlledAsyncValidateField.tsx
"use client";

import * as React from "react";
import {
  Controller,
  Control,
  useWatch,
} from "react-hook-form";
import {
  TextField,
  TextFieldProps,
  InputAdornment,
  CircularProgress,
} from "@mui/material";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";

export type ControlledAsyncValidateFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render"
> & {
  control: Control<any>;
  name: string;
  /** Função assíncrona: retorna true se válido, ou mensagem de erro */
  asyncValidate: (_value: string) => Promise<true | string>;
  delay?: number;
};

export default function ControlledAsyncValidateField({
  control,
  name,
  asyncValidate,
  delay = 600,
  helperText,
  ...rest
}: ControlledAsyncValidateFieldProps) {
  const [status, setStatus] = React.useState<
    "idle" | "loading" | "ok" | "error"
  >("idle");

  // Watch the field value at component level
  const fieldValue = useWatch({ control, name });

  // Use useEffect for validation at component level
  React.useEffect(() => {
    if (!fieldValue) {
      setStatus("idle");
      return;
    }

    const timeoutId = setTimeout(async () => {
      setStatus("loading");
      try {
        const res = await asyncValidate(String(fieldValue));
        setStatus(res === true ? "ok" : "error");
      } catch {
        setStatus("error");
      }
    }, delay);

    return () => clearTimeout(timeoutId);
  }, [fieldValue, delay, asyncValidate]);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const adornment = (
          <InputAdornment position="end">
            {status === "loading" && <CircularProgress size={16} />}
            {status === "ok" && (
              <CheckCircleOutlineIcon color="success" fontSize="small" />
            )}
            {status === "error" && (
              <ErrorOutlineIcon color="error" fontSize="small" />
            )}
          </InputAdornment>
        );

        return (
          <TextField
            {...rest}
            {...field}
            fullWidth
            error={!!fieldState.error}
            helperText={fieldState.error?.message || helperText}
            InputProps={{
              ...(rest.InputProps || {}),
              endAdornment: (
                <>
                  {rest.InputProps?.endAdornment}
                  {adornment}
                </>
              ),
            }}
          />
        );
      }}
    />
  );
}