// web/src/components/form/fields/ControlledSwitch.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import { FormControlLabel, Switch, SwitchProps } from "@mui/material";

export type ControlledSwitchProps = Omit<
  SwitchProps,
  "name" | "onChange" | "checked"
> & {
  control: Control<any>;
  name: string;
  label?: React.ReactNode;
};

export default function ControlledSwitch({
  control,
  name,
  label,
  ...rest
}: ControlledSwitchProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <FormControlLabel
          control={
            <Switch
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
