// web/src/components/form/fields/ControlledCheckbox.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { FormControlLabel, Checkbox, CheckboxProps } from "@mui/material";

export type ControlledCheckboxProps = Omit<
  CheckboxProps,
  "name" | "onChange" | "checked"
> & {
  control: Control<any>;
  name: string;
  label?: React.ReactNode;
};

export default function ControlledCheckbox({
  control,
  name,
  label,
  ...rest
}: ControlledCheckboxProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <FormControlLabel
          control={
            <Checkbox
              {...rest}
              checked={!!field.value}
              onChange={(_, v) => field.onChange(v)}
            />
          }
          label={label}
        />
      )}
    />
  );
}
