"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import {
  RadioGroup,
  FormControlLabel,
  Radio,
  FormLabel,
  FormControl,
} from "@mui/material";

export interface RadioGroupFieldProps {
  name: string;
  label?: string;
  options: { label: string; value: string }[];
  row?: boolean;
}

export const RadioGroupField: React.FC<RadioGroupFieldProps> = ({
  name,
  label,
  options,
  row = true,
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControl>
          {label && <FormLabel>{label}</FormLabel>}
          <RadioGroup {...field} row={row}>
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
};
