"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { Switch, FormControlLabel } from "@mui/material";

export interface ToggleFieldProps {
  name: string;
  label: string;
}

export const ToggleField: React.FC<ToggleFieldProps> = ({ name, label }) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel
          control={
            <Switch
              checked={!!field.value}
              onChange={(e, v) => field.onChange(v)}
            />
          }
          label={label}
        />
      )}
    />
  );
};
