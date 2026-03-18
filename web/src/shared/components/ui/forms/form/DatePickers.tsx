"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { Stack } from "@mui/material";
import dayjs, { Dayjs } from "dayjs";
import "dayjs/locale/pt-br";

const COMMON_NS = "common";

function useDatePickerI18nConfig() {
  const { i18n } = useTranslation();
  return React.useMemo(() => {
    const lang = i18n.language?.toLowerCase() ?? "pt";
    const isPt = lang.startsWith("pt");
    return {
      adapterLocale: isPt ? "pt-br" : "en",
      dateFormat: isPt ? "DD/MM/YYYY" : "MM/DD/YYYY",
    };
  }, [i18n.language]);
}

export interface DatePickerFieldProps {
  name: string;
  label?: string;
  helperText?: React.ReactNode;
  fullWidth?: boolean;
  minDate?: Date;
  maxDate?: Date;
  'data-testid'?: string;
}

export const DatePickerField: React.FC<DatePickerFieldProps> = ({
  name,
  label,
  helperText,
  fullWidth = true,
  minDate,
  maxDate,
  'data-testid': dataTestId,
}) => {
  const { t } = useTranslation(COMMON_NS);
  const { control } = useFormContext();
  const { adapterLocale, dateFormat } = useDatePickerI18nConfig();
  const [pickerError, setPickerError] = React.useState<string | null>(null);
  const effectiveLabel = label ?? t("datePicker.placeholder");
  const localeText = React.useMemo(
    () => ({
      fieldDayPlaceholder: () => t("datePicker.fieldDay"),
      fieldMonthPlaceholder: () => t("datePicker.fieldMonth"),
      fieldYearPlaceholder: () => t("datePicker.fieldYear"),
    }),
    [t]
  );
  const pickerErrorText = React.useMemo(() => {
    if (!pickerError) return undefined;

    if (pickerError === "invalidDate") {
      return t("datePicker.errors.invalidDate");
    }

    if (pickerError === "minDate" && minDate) {
      return t("datePicker.errors.minDate", { date: dayjs(minDate).format(dateFormat) });
    }

    if (pickerError === "maxDate" && maxDate) {
      return t("datePicker.errors.maxDate", { date: dayjs(maxDate).format(dateFormat) });
    }

    if (pickerError === "disablePast") {
      return t("datePicker.errors.disablePast");
    }

    if (pickerError === "disableFuture") {
      return t("datePicker.errors.disableFuture");
    }

    return t("datePicker.errors.invalidDate");
  }, [dateFormat, maxDate, minDate, pickerError, t]);
  return (
    <LocalizationProvider
      dateAdapter={AdapterDayjs}
      adapterLocale={adapterLocale}
      localeText={localeText}
    >
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState }) => (
          <DatePicker
            format={dateFormat}
            value={field.value ? dayjs(field.value) : null}
            onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
            onError={(reason) => setPickerError(reason as any)}
            minDate={minDate ? dayjs(minDate) : undefined}
            maxDate={maxDate ? dayjs(maxDate) : undefined}
            slotProps={{
              textField: {
                label: effectiveLabel,
                placeholder: t("datePicker.formatHint"),
                fullWidth,
                error: !!fieldState.error || !!pickerError,
                helperText: fieldState.error?.message ?? pickerErrorText ?? helperText,
                'data-testid': dataTestId,
                inputProps: {
                  'data-testid': dataTestId,
                },
              } as any,
            }}
          />
        )}
      />
    </LocalizationProvider>
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
  labelStart,
  labelEnd,
}) => {
  const { t } = useTranslation(COMMON_NS);
  const { control } = useFormContext();
  const { adapterLocale, dateFormat } = useDatePickerI18nConfig();
  const startLabel = labelStart ?? t("datePicker.start");
  const endLabel = labelEnd ?? t("datePicker.end");
  const localeText = React.useMemo(
    () => ({
      fieldDayPlaceholder: () => t("datePicker.fieldDay"),
      fieldMonthPlaceholder: () => t("datePicker.fieldMonth"),
      fieldYearPlaceholder: () => t("datePicker.fieldYear"),
    }),
    [t]
  );
  return (
    <LocalizationProvider
      dateAdapter={AdapterDayjs}
      adapterLocale={adapterLocale}
      localeText={localeText}
    >
      <Stack direction="row" spacing={2}>
        <Controller
          name={nameStart}
          control={control}
          render={({ field }) => (
            <DatePicker
              format={dateFormat}
              value={field.value ? dayjs(field.value) : null}
              onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
              slotProps={{ textField: { label: startLabel, placeholder: t("datePicker.formatHint") } as any }}
            />
          )}
        />
        <Controller
          name={nameEnd}
          control={control}
          render={({ field }) => (
            <DatePicker
              format={dateFormat}
              value={field.value ? dayjs(field.value) : null}
              onChange={(v) => field.onChange(v ? (v as Dayjs).toDate() : null)}
              slotProps={{ textField: { label: endLabel, placeholder: t("datePicker.formatHint") } as any }}
            />
          )}
        />
      </Stack>
    </LocalizationProvider>
  );
};
