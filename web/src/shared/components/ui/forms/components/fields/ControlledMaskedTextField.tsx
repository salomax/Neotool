// web/src/components/form/fields/ControlledMaskedTextField.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { TextField, TextFieldProps } from "@mui/material";

export type ControlledMaskedTextFieldProps = Omit<
  TextFieldProps,
  "name" | "defaultValue" | "onChange" | "value" | "render"
> & {
  control: Control<any>;
  name: string;
  mask: (_raw: string) => string;
  numeric?: boolean;
  transformOut?: (_masked: string) => any;
  transformIn?: (_formValue: any) => string;
};

export function ControlledMaskedTextField({
  control,
  name,
  helperText,
  mask,
  numeric,
  transformOut,
  transformIn,
  ...rest
}: ControlledMaskedTextFieldProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const display = (() => {
          const v = field.value ?? "";
          const str =
            typeof transformIn === "function" ? transformIn(v) : String(v);
          return mask(str);
        })();

        return (
          <TextField
            {...rest}
            fullWidth
            value={display}
            error={!!fieldState.error}
            helperText={fieldState.error?.message || helperText}
            inputProps={{
              ...(rest.inputProps || {}),
              inputMode: numeric ? "numeric" : rest.inputProps?.inputMode,
            }}
            onChange={(e) => {
              const raw = e.target.value ?? "";
              const masked = mask(String(raw));
              const out =
                typeof transformOut === "function"
                  ? transformOut(masked)
                  : masked;
              field.onChange(out);
            }}
          />
        );
      }}
    />
  );
}

export default ControlledMaskedTextField;
