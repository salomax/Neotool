"use client";

import * as React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import type { DatePickerProps } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import type { DateView } from "@mui/x-date-pickers/models";
import { Stack } from "@mui/material";
import dayjs, { Dayjs } from "dayjs";
import "dayjs/locale/pt-br";
import {
  APP_DATE_PICKER_VIEWS,
  buildMuiDatePickerLocaleText,
  getMuiDateAdapterLocaleFromLanguageTag,
  mergeAppDatePickerSlotProps,
} from "./appDatePickerConfig";
import { TextField, type TextFieldProps } from "./TextField";
import { toStartOfDayLocal } from "@/shared/utils/date/dateOnly";

/**
 * When `disableFuture` is on, MUI still builds the year list from `minDate`–`maxDate` (default max is 2099),
 * so every future year appears disabled instead of omitted. Capping `maxDate` at end of today makes the
 * year view and month bounds match “no future” without relying only on per-cell disabled state.
 */
function resolveMaxDateForDisableFuture(
  maxDate: Date | undefined,
  disableFuture: boolean,
  now: Dayjs = dayjs(),
): Date | undefined {
  if (!disableFuture) return maxDate;
  const endOfToday = now.endOf("day").toDate();
  if (maxDate == null) return endOfToday;
  const userEnd = dayjs(maxDate).endOf("day");
  const todayStart = now.startOf("day");
  if (userEnd.isBefore(todayStart)) {
    return maxDate;
  }
  return endOfToday;
}

/** Same outlined input as `MoneyInput` / `FormTextField` (PickersTextField is a few px taller). */
const DATE_PICKER_TEXT_FIELD_SLOTS = { textField: TextField } as const;

/**
 * Maps a Dayjs picker value into RHF form state.
 * - cleared or invalid / partial Dayjs → `null` (never write Invalid Date; clears stale valid values)
 * - valid Dayjs → local start-of-day Date (including out-of-range, so Zod can reject on submit)
 */
export function toFormDateValue(value: Dayjs | null): Date | null {
  if (!value || !value.isValid()) return null;
  return toStartOfDayLocal(value.toDate());
}

export type DatePickerTextFieldSize = NonNullable<TextFieldProps["size"]>;

/**
 * Outlined date field: match `./TextField` vertical padding for `size`, and `paddingRight: 0` so the
 * calendar `endAdornment` matches MUI’s adorned layout (shared `TextField` uses uniform padding otherwise).
 */
export function getDatePickerOutlinedInputSx(size: DatePickerTextFieldSize = "medium") {
  const vertical =
    size === "small" ? "10.5px" : size === "large" ? "16.5px" : "14.5px";
  return {
    "& .MuiOutlinedInput-input": {
      paddingTop: vertical,
      paddingBottom: vertical,
      paddingLeft: "14px",
      paddingRight: 0,
      height: "1.4375em",
      lineHeight: "1.4375em",
    },
  } as const;
}

const COMMON_NS = "common";

export {
  APP_DATE_PICKER_VIEWS,
  appDatePickerDisplayFormat,
  appDatePickerDisplayFormatFromLanguageTag,
  appDatePickerSlotProps,
  buildMuiDatePickerLocaleText,
  getMuiDateAdapterLocaleFromLanguageTag,
  mergeAppDatePickerSlotProps,
} from "./appDatePickerConfig";

function useDatePickerI18nConfig() {
  const { i18n } = useTranslation();
  return React.useMemo(() => {
    const lang = i18n.language?.toLowerCase() ?? "pt";
    const isPt = lang.startsWith("pt");
    return {
      adapterLocale: getMuiDateAdapterLocaleFromLanguageTag(lang),
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
  disablePast?: boolean;
  disableFuture?: boolean;
  /** Defaults to `APP_DATE_PICKER_VIEWS` (year/month/day; opens on day grid). Override to narrow views. */
  views?: readonly DateView[];
  /** Initial view when opening; default follows MUI (typically `day` when present in `views`). */
  openTo?: DateView;
  /** Extra MUI `DatePicker` `slotProps` merged with app standards (`mergeAppDatePickerSlotProps`). `textField` merges with error/helper wiring. */
  pickerSlotProps?: DatePickerProps["slotProps"];
  /** Input density; matches `FormTextField` / `MoneyInput` (default `medium`). Overrides `pickerSlotProps.textField.size` when set. */
  size?: DatePickerTextFieldSize;
  onErrorChange?: (reason: string | null) => void;
  'data-testid'?: string;
}

type DatePickerFieldInnerProps = {
  field: any;
  fieldState: any;
  dateFormat: string;
  effectiveLabel: string;
  fullWidth: boolean;
  minDate?: Date;
  maxDate?: Date;
  disablePast?: boolean;
  disableFuture?: boolean;
  views?: readonly DateView[];
  openTo?: DateView;
  pickerSlotProps?: DatePickerProps["slotProps"];
  textFieldSize?: DatePickerTextFieldSize;
  helperText?: React.ReactNode;
  dataTestId?: string;
  pickerError: string | null;
  pickerErrorText?: React.ReactNode;
  formatHint: string;
  setPickerError: (reason: any) => void;
};

const DatePickerFieldInner: React.FC<DatePickerFieldInnerProps> = ({
  field,
  fieldState,
  dateFormat,
  effectiveLabel,
  fullWidth,
  minDate,
  maxDate,
  disablePast,
  disableFuture,
  views,
  openTo,
  pickerSlotProps,
  textFieldSize,
  helperText,
  dataTestId,
  pickerError,
  pickerErrorText,
  formatHint,
  setPickerError,
}) => {
  const [draftValue, setDraftValue] = React.useState<Dayjs | null>(field.value ? dayjs(field.value) : null);
  // When invalid typing clears RHF to null, skip the next field→draft sync so the typed draft stays visible.
  const skipNextFieldSyncRef = React.useRef(false);

  React.useEffect(() => {
    if (skipNextFieldSyncRef.current) {
      skipNextFieldSyncRef.current = false;
      return;
    }
    setDraftValue(field.value ? dayjs(field.value) : null);
  }, [field.value]);

  const resolvedViews = views !== undefined ? [...views] : [...APP_DATE_PICKER_VIEWS];
  const mergedNonTextFieldSlotProps = React.useMemo(() => {
    if (!pickerSlotProps) return mergeAppDatePickerSlotProps();
    const { textField: _omit, ...rest } = pickerSlotProps;
    void _omit;
    return mergeAppDatePickerSlotProps(rest);
  }, [pickerSlotProps]);

  const pickerTextFieldRaw =
    typeof pickerSlotProps?.textField === "object" && pickerSlotProps.textField !== null
      ? (pickerSlotProps.textField as Record<string, unknown> & { sx?: unknown })
      : {};
  const {
    sx: pickerTextFieldSx,
    size: pickerSlotTextFieldSize,
    ...pickerTextFieldRest
  } = pickerTextFieldRaw;
  const resolvedTextFieldSize: DatePickerTextFieldSize =
    textFieldSize ??
    (pickerSlotTextFieldSize === "small" || pickerSlotTextFieldSize === "medium" || pickerSlotTextFieldSize === "large"
      ? pickerSlotTextFieldSize
      : "medium");
  const mergedTextFieldSx = [
    getDatePickerOutlinedInputSx(resolvedTextFieldSize),
    ...(pickerTextFieldSx != null
      ? Array.isArray(pickerTextFieldSx)
        ? pickerTextFieldSx
        : [pickerTextFieldSx]
      : []),
  ];

  return (
    <DatePicker
      enableAccessibleFieldDOMStructure={false}
      slots={DATE_PICKER_TEXT_FIELD_SLOTS}
      format={dateFormat}
      views={resolvedViews}
      openTo={openTo}
      value={draftValue}
      onChange={(v: Dayjs | null) => {
        setDraftValue(v);
        // Sync valid dates into RHF (including out-of-range) so Zod can reject on submit.
        // Invalid/partial Dayjs clears RHF to null — never Invalid Date, never a stale prior date.
        if (v != null && !v.isValid()) {
          skipNextFieldSyncRef.current = true;
        }
        field.onChange(toFormDateValue(v));
      }}
      onError={setPickerError}
      minDate={minDate ? dayjs(minDate) : undefined}
      maxDate={maxDate ? dayjs(maxDate) : undefined}
      disablePast={disablePast === true}
      disableFuture={disableFuture === true}
      slotProps={{
        ...mergedNonTextFieldSlotProps,
        textField: {
          ...pickerTextFieldRest,
          size: resolvedTextFieldSize,
          sx: mergedTextFieldSx,
          label: effectiveLabel,
          placeholder: formatHint,
          fullWidth,
          error: !!fieldState.error || !!pickerError,
          helperText: fieldState.error?.message ?? pickerErrorText ?? helperText,
          "data-testid": dataTestId,
          inputProps: {
            ...(typeof pickerTextFieldRaw.inputProps === "object" &&
            pickerTextFieldRaw.inputProps !== null
              ? pickerTextFieldRaw.inputProps
              : {}),
            "data-testid": dataTestId,
          },
        } as any,
      }}
    />
  );
};

export const DatePickerField: React.FC<DatePickerFieldProps> = ({
  name,
  label,
  helperText,
  fullWidth = true,
  minDate,
  maxDate,
  disablePast,
  disableFuture,
  views,
  openTo,
  pickerSlotProps,
  size,
  onErrorChange,
  'data-testid': dataTestId,
}) => {
  const { t } = useTranslation(COMMON_NS);
  const { control } = useFormContext();
  const { adapterLocale, dateFormat } = useDatePickerI18nConfig();
  const [pickerError, setPickerError] = React.useState<string | null>(null);
  const effectiveMinDate = minDate ?? dayjs().subtract(10, "year").startOf("day").toDate();
  const disableFutureFlag = disableFuture === true;
  const todayCalendarKey = dayjs().format("YYYY-MM-DD");
  const today = React.useMemo(() => dayjs(todayCalendarKey), [todayCalendarKey]);
  const resolvedMaxDate = React.useMemo(
    () => resolveMaxDateForDisableFuture(maxDate, disableFutureFlag, today),
    [maxDate, disableFutureFlag, today],
  );
  const effectiveLabel = label ?? t("datePicker.placeholder");
  const localeText = React.useMemo(() => buildMuiDatePickerLocaleText(t), [t]);
  const handlePickerError = React.useCallback(
    (reason: unknown) => {
      const nextReason = typeof reason === "string" ? reason : null;
      setPickerError(nextReason);
      onErrorChange?.(nextReason);
    },
    [onErrorChange],
  );
  const pickerErrorText = React.useMemo(() => {
    if (!pickerError) return undefined;

    if (pickerError === "invalidDate") {
      return t("datePicker.errors.invalidDate");
    }

    if (pickerError === "minDate") {
      return t("datePicker.errors.minDate", {
        date: dayjs(effectiveMinDate).format(dateFormat),
      });
    }

    if (pickerError === "maxDate" && resolvedMaxDate) {
      return t("datePicker.errors.maxDate", { date: dayjs(resolvedMaxDate).format(dateFormat) });
    }

    if (pickerError === "disablePast") {
      return t("datePicker.errors.disablePast");
    }

    if (pickerError === "disableFuture") {
      return t("datePicker.errors.disableFuture");
    }

    return t("datePicker.errors.invalidDate");
  }, [dateFormat, effectiveMinDate, pickerError, resolvedMaxDate, t]);
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
          <DatePickerFieldInner
            field={field}
            fieldState={fieldState}
            dateFormat={dateFormat}
            effectiveLabel={effectiveLabel}
            fullWidth={fullWidth}
            minDate={effectiveMinDate}
            maxDate={resolvedMaxDate}
            disablePast={disablePast}
            disableFuture={disableFuture}
            views={views}
            openTo={openTo}
            pickerSlotProps={pickerSlotProps}
            textFieldSize={size}
            helperText={helperText}
            dataTestId={dataTestId}
            pickerError={pickerError}
            pickerErrorText={pickerErrorText}
            formatHint={t("datePicker.formatHint")}
            setPickerError={handlePickerError}
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
  size?: DatePickerTextFieldSize;
}

export const DateRangeField: React.FC<DateRangeFieldProps> = ({
  nameStart,
  nameEnd,
  labelStart,
  labelEnd,
  size = "medium",
}) => {
  const { t } = useTranslation(COMMON_NS);
  const { control } = useFormContext();
  const { adapterLocale, dateFormat } = useDatePickerI18nConfig();
  const startLabel = labelStart ?? t("datePicker.start");
  const endLabel = labelEnd ?? t("datePicker.end");
  const localeText = React.useMemo(() => buildMuiDatePickerLocaleText(t), [t]);
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
              enableAccessibleFieldDOMStructure={false}
              slots={DATE_PICKER_TEXT_FIELD_SLOTS}
              format={dateFormat}
              views={[...APP_DATE_PICKER_VIEWS]}
              value={field.value ? dayjs(field.value) : null}
              onChange={(v: Dayjs | null) =>
                field.onChange(v ? toStartOfDayLocal(v.toDate()) : null)
              }
              slotProps={{
                ...mergeAppDatePickerSlotProps(),
                textField: {
                  size,
                  label: startLabel,
                  placeholder: t("datePicker.formatHint"),
                  sx: [getDatePickerOutlinedInputSx(size)],
                } as any,
              }}
            />
          )}
        />
        <Controller
          name={nameEnd}
          control={control}
          render={({ field }) => (
            <DatePicker
              enableAccessibleFieldDOMStructure={false}
              slots={DATE_PICKER_TEXT_FIELD_SLOTS}
              format={dateFormat}
              views={[...APP_DATE_PICKER_VIEWS]}
              value={field.value ? dayjs(field.value) : null}
              onChange={(v: Dayjs | null) =>
                field.onChange(v ? toStartOfDayLocal(v.toDate()) : null)
              }
              slotProps={{
                ...mergeAppDatePickerSlotProps(),
                textField: {
                  size,
                  label: endLabel,
                  placeholder: t("datePicker.formatHint"),
                  sx: [getDatePickerOutlinedInputSx(size)],
                } as any,
              }}
            />
          )}
        />
      </Stack>
    </LocalizationProvider>
  );
};
