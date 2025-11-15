"use client";
import React from "react";
import {
  FormControl,
  InputLabel,
  Select as MUISelect,
  MenuItem,
  SelectChangeEvent,
} from "@mui/material";

export interface SelectOption {
  label: string;
  value: string | number;
}
export interface SelectProps {
  id?: string;
  label?: string;
  value?: string | number | null | undefined;
  options: SelectOption[];
  onChange?: (_value: string | number | null) => void;
  disabled?: boolean;
  /** Whether to allow clearing the selection (shows a "None" option) */
  allowClear?: boolean;
  /** Custom label for the clear/none option */
  clearLabel?: string;
}

export const Select: React.FC<SelectProps> = ({
  id,
  label,
  value,
  options,
  onChange,
  disabled,
  allowClear = false,
  clearLabel = 'None',
}) => {
  const handleChange = (e: SelectChangeEvent) => {
    const newValue = e.target.value;
    // Convert empty string to null for consistency
    const normalizedValue = newValue === "" ? null : newValue;
    onChange?.(normalizedValue as any);
  };
  const labelId = id ? `${id}-label` : undefined;
  
  // Normalize value for MUI Select (empty string for null/undefined)
  const selectValue = value ?? "";
  
  return (
    <FormControl fullWidth {...(disabled && { disabled })}>
      {label && <InputLabel id={labelId}>{label}</InputLabel>}
      <MUISelect
        {...(labelId && { labelId })}
        {...(id && { id })}
        value={selectValue}
        {...(label && { label })}
        onChange={handleChange as any}
      >
        {allowClear && (
          <MenuItem value="">
            <em>{clearLabel}</em>
          </MenuItem>
        )}
        {options.map((opt) => (
          <MenuItem key={String(opt.value)} value={opt.value}>
            {opt.label}
          </MenuItem>
        ))}
      </MUISelect>
    </FormControl>
  );
};
export default Select;
