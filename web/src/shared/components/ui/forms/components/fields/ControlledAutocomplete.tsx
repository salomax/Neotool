// web/src/components/form/fields/ControlledAutocomplete.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import CircularProgress from '@mui/material/CircularProgress';

export type AutoOption = { label: string; id: string | number };

export type ControlledAutocompleteProps = {
  control: Control<any>;
  name: string;
  label?: string;
  placeholder?: string;
  loadOptions: (input: string) => Promise<AutoOption[]>;
};

export function ControlledAutocomplete({
  control,
  name,
  label,
  placeholder,
  loadOptions,
}: ControlledAutocompleteProps) {
  const [open, setOpen] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [options, setOptions] = React.useState<AutoOption[]>([]);

  const fetch = React.useMemo(() => {
    let active = true;
    return async (input: string) => {
      setLoading(true);
      try {
        const res = await loadOptions(input);
        if (active) setOptions(res);
      } finally {
        if (active) setLoading(false);
      }
      return () => {
        active = false;
      };
    };
  }, [loadOptions]);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <Autocomplete
          open={open}
          onOpen={() => setOpen(true)}
          onClose={() => setOpen(false)}
          options={options}
          loading={loading}
          value={options.find((o) => o.id === field.value) ?? null}
          onChange={(_, opt) => field.onChange(opt?.id ?? null)}
          onInputChange={(_event, input) => {
            fetch(input);
          }}
          getOptionLabel={(o) => o.label}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          renderInput={(params) => {
            // Filter out undefined values to avoid TypeScript errors
            const filteredParams = Object.fromEntries(
              Object.entries(params).filter(([_, value]) => value !== undefined)
            );
            return (
              <TextField
                {...filteredParams}
                {...(label && { label })}
                {...(placeholder && { placeholder })}
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

export default ControlledAutocomplete;
