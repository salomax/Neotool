"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { FormControlLabel, Switch, type SwitchProps } from "@mui/material";

export interface ToggleFieldProps {
  name: string;
  label: string;
  size?: SwitchProps["size"];
  "data-testid"?: string;
}

export const ToggleField: React.FC<ToggleFieldProps> = ({
  name,
  label,
  size,
  "data-testid": dataTestId,
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel
          control={
            <Switch
              size={size}
              checked={!!field.value}
              onChange={(e, v) => field.onChange(v)}
              data-testid={dataTestId}
            />
          }
          label={label}
        />
      )}
    />
  );
};
