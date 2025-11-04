"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { Stack } from "@mui/material";
import dayjs, { Dayjs } from "dayjs";

export interface DatePickerFieldProps {
  name: string;
  label?: string;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
}

export const DatePickerField: React.FC<DatePickerFieldProps> = ({
  name,
  label,
  helperText,
  fullWidth = true,
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <DatePicker
          value={field.value ? dayjs(field.value) : null}
          onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
          slotProps={{
            textField: {
              label,
              fullWidth,
              error: !!fieldState.error,
              helperText: fieldState.error?.message ?? helperText,
            } as any,
          }}
        />
      )}
    />
  );
};

export interface DateRangeFieldProps {
  nameStart: string;
  nameEnd: string;
  labelStart?: string;
  labelEnd?: string;
}

export const DateRangeField: React.FC<DateRangeFieldProps> = ({
  nameStart,
  nameEnd,
  labelStart = "Start",
  labelEnd = "End",
}) => {
  const { control } = useFormContext();
  return (
    <Stack direction="row" spacing={2}>
      <Controller
        name={nameStart}
        control={control}
        render={({ field }) => (
          <DatePicker
            value={field.value ? dayjs(field.value) : null}
            onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
            slotProps={{ textField: { label: labelStart } as any }}
          />
        )}
      />
      <Controller
        name={nameEnd}
        control={control}
        render={({ field }) => (
          <DatePicker
            value={field.value ? dayjs(field.value) : null}
            onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
            slotProps={{ textField: { label: labelEnd } as any }}
          />
        )}
      />
    </Stack>
  );
};
