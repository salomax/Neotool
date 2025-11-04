"use client";

import * as React from "react";
import { Autocomplete, CircularProgress, TextField } from "@mui/material";
import { Controller, useFormContext } from "react-hook-form";

export type AsyncAutocompleteProps<T> = {
  name: string;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  debounceMs?: number;
  fetchOptions: (_q: string) => Promise<T[]>;
  getOptionLabel: (_o: T) => string;
  isOptionEqualToValue?: (_a: T, _b: T) => boolean;
};

export function AsyncAutocomplete<T>({
  name,
  label,
  placeholder,
  disabled,
  debounceMs = 300,
  fetchOptions,
  getOptionLabel,
  isOptionEqualToValue,
}: AsyncAutocompleteProps<T>) {
  const { control } = useFormContext();
  const [open, setOpen] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [options, setOptions] = React.useState<T[]>([]);
  const [input, setInput] = React.useState("");

  React.useEffect(() => {
    if (!open) return;
    const id = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetchOptions(input);
        setOptions(res);
      } finally {
        setLoading(false);
      }
    }, debounceMs);
    return () => clearTimeout(id);
  }, [open, input, debounceMs, fetchOptions]);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Autocomplete
          open={open}
          onOpen={() => setOpen(true)}
          onClose={() => setOpen(false)}
          loading={loading}
          options={options}
          value={field.value ?? null}
          onChange={(_event, v) => field.onChange(v)}
          inputValue={input}
          onInputChange={(_event, v) => setInput(v)}
          getOptionLabel={getOptionLabel}
          isOptionEqualToValue={isOptionEqualToValue as any}
          disabled={disabled || false}
          renderInput={(params) => {
            const { InputLabelProps, ...restParams } = params;
            return (
              <TextField
                {...restParams}
                {...(label && { label })}
                {...(placeholder && { placeholder })}
                error={!!fieldState.error}
                helperText={fieldState.error?.message}
                size="medium"
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {loading ? <CircularProgress size={16} /> : null}
                      {params.InputProps.endAdornment}
                    </>
                  ),
                }}
              />
            );
          }}
        />
      )}
    />
  );
}
