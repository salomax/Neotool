// web/src/components/form/fields/ControlledDatePicker.tsx
"use client";

import * as React from "react";
import { Controller, Control } from "react-hook-form";
import {
  DatePicker,
  DatePickerProps,
  LocalizationProvider,
} from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs, { Dayjs } from "dayjs";
import {
  APP_DATE_PICKER_VIEWS,
  mergeAppDatePickerSlotProps,
} from "@/shared/components/ui/forms/form/appDatePickerConfig";

/** Stores native Date by default (valueAsDate=true), even usando Dayjs no UI */
export type ControlledDatePickerProps = Omit<
  DatePickerProps,
  "name" | "value" | "onChange"
> & {
  control: Control<any>;
  name: string;
  /** Se true (default), converte Dayjs -> Date no form state. Se false, armazena Dayjs. */
  valueAsDate?: boolean;
};

export function ControlledDatePicker({
  control,
  name,
  valueAsDate = true,
  views,
  slotProps,
  ...rest
}: ControlledDatePickerProps) {
  const mergedSlotProps = React.useMemo(() => mergeAppDatePickerSlotProps(slotProps), [slotProps]);
  const resolvedViews = views !== undefined ? [...views] : [...APP_DATE_PICKER_VIEWS];

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Controller
        control={control}
        name={name}
        render={({ field }) => {
          // field.value pode ser Date ou Dayjs; normalizamos para Dayjs pro componente
          const value: Dayjs | null =
            field.value == null
              ? null
              : dayjs.isDayjs(field.value)
                ? (field.value as Dayjs)
                : dayjs(field.value as Date);

          return (
            <DatePicker
              {...rest}
              views={resolvedViews}
              slotProps={mergedSlotProps}
              value={value}
              onChange={(v) => {
                if (valueAsDate) {
                  field.onChange(v ? v.toDate() : null); // mantém compatível com zod date()
                } else {
                  field.onChange(v ?? null);
                }
              }}
            />
          );
        }}
      />
    </LocalizationProvider>
  );
}

export default ControlledDatePicker;
