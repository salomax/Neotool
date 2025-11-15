// web/src/components/form/fields/ControlledRadioGroup.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import {
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
} from "@mui/material";

export type RadioOption = { label: string; value: string };

export type ControlledRadioGroupProps = {
  control: Control<any>;
  name: string;
  label?: string;
  options: RadioOption[];
  row?: boolean;
};

export default function ControlledRadioGroup({
  control,
  name,
  label,
  options,
  row,
}: ControlledRadioGroupProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <FormControl>
          {label && <FormLabel>{label}</FormLabel>}
          <RadioGroup {...field} {...(row !== undefined && { row })}>
            {options.map((o) => (
              <FormControlLabel
                key={o.value}
                value={o.value}
                control={<Radio />}
                label={o.label}
              />
            ))}
          </RadioGroup>
        </FormControl>
      )}
    />
  );
}
