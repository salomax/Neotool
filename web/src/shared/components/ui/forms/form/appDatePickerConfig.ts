"use client";

import type { Theme } from "@mui/material/styles";
import type { DatePickerProps } from "@mui/x-date-pickers/DatePicker";
import type { DateView } from "@mui/x-date-pickers/models";
import { monthCalendarClasses } from "@mui/x-date-pickers/MonthCalendar";
import { pickersCalendarHeaderClasses } from "@mui/x-date-pickers/PickersCalendarHeader";
import { yearCalendarClasses } from "@mui/x-date-pickers/YearCalendar";
import { labelTypographySx } from "@/shared/components/ui/primitives/Label";

/** App-wide date picker: day grid by default; year/month via header switch. */
export const APP_DATE_PICKER_VIEWS: readonly DateView[] = ["year", "month", "day"];

/** Display format aligned with `DatePickerField` / adapter locale (`pt-br` → BR). */
export function appDatePickerDisplayFormat(adapterLocale: string): string {
  return adapterLocale.toLowerCase().startsWith("pt") ? "DD/MM/YYYY" : "MM/DD/YYYY";
}

export function appDatePickerDisplayFormatFromLanguageTag(lang: string | undefined): string {
  const l = lang?.toLowerCase() ?? "pt";
  return l.startsWith("pt") ? "DD/MM/YYYY" : "MM/DD/YYYY";
}


export {
  getMuiDateAdapterLocaleFromLanguageTag,
  buildMuiDatePickerLocaleText,
} from "@/shared/i18n/muiLocale";

/** Calendar header + month grid match shared `<Label />` typography (`labelTypographySx`). */
function appDatePickerLabelTypographySx(theme: Theme) {
  return { ...labelTypographySx(theme), fontSize: "0.8rem" };
}

const appDatePickerCalendarSlotPropsDefined = {
  calendarHeader: {
    sx: (theme: Theme) => ({
      [`& .${pickersCalendarHeaderClasses.labelContainer}`]: {
        ...appDatePickerLabelTypographySx(theme),
      },
      [`& .${pickersCalendarHeaderClasses.label}`]: {
        fontSize: "0.8rem",
      },
    }),
  },
  yearButton: {
    sx: (theme: Theme) => ({
      ...appDatePickerLabelTypographySx(theme),
      [`&.${yearCalendarClasses.selected}`]: {
        color: theme.palette.primary.contrastText,
      },
    }),
  },
  monthButton: {
    sx: (theme: Theme) => ({
      ...appDatePickerLabelTypographySx(theme),
      [`&.${monthCalendarClasses.selected}`]: {
        color: theme.palette.primary.contrastText,
      },
    }),
  },
  layout: {
    sx: {
      "& .MuiPickersLayout-contentWrapper": {
        marginBottom: 0,
      },
      "& .MuiPickersActionBar-root": {
        gap: 1,
        px: 2,
        pt: 1,
        pb: 2,
        mt: 0.5,
      },
      "& .MuiPickersActionBar-root .MuiButton-root:first-of-type": {
        border: "1px solid",
        borderColor: "divider",
        color: "text.primary",
        "&:hover": {
          borderColor: "divider",
          backgroundColor: "action.hover",
        },
      },
    },
  },
};

export const appDatePickerSlotProps =
  appDatePickerCalendarSlotPropsDefined as DatePickerProps["slotProps"];

/**
 * Standard calendar `slotProps` plus optional extras (e.g. `textField`, `day`, `actionBar`).
 * Deep-merges `calendarHeader`, `yearButton` and `monthButton` `sx` when the caller overrides those slots.
 */
export function mergeAppDatePickerSlotProps(
  extra?: DatePickerProps["slotProps"] | null,
): DatePickerProps["slotProps"] {
  const base = appDatePickerCalendarSlotPropsDefined;
  if (!extra) {
    return { ...appDatePickerSlotProps };
  }

  const {
    calendarHeader: extraCal,
    yearButton: extraYear,
    monthButton: extraMonth,
    layout: extraLayout,
    ...restExtra
  } = extra;
  const baseCal = base.calendarHeader as { sx?: unknown };
  const baseYear = base.yearButton as { sx?: unknown };
  const baseMonth = base.monthButton as { sx?: unknown };
  const baseLayout = base.layout as { sx?: unknown };

  return {
    ...base,
    ...restExtra,
    ...(extraCal != null
      ? {
          calendarHeader: {
            ...base.calendarHeader,
            ...extraCal,
            sx: [baseCal.sx, (extraCal as { sx?: unknown }).sx].filter(Boolean),
          },
        }
      : {}),
    ...(extraYear != null
      ? {
          yearButton: {
            ...base.yearButton,
            ...extraYear,
            sx: [baseYear.sx, (extraYear as { sx?: unknown }).sx].filter(Boolean),
          },
        }
      : {}),
    ...(extraMonth != null
      ? {
          monthButton: {
            ...base.monthButton,
            ...extraMonth,
            sx: [baseMonth.sx, (extraMonth as { sx?: unknown }).sx].filter(Boolean),
          },
        }
      : {}),
    ...(extraLayout != null
      ? {
          layout: {
            ...base.layout,
            ...extraLayout,
            sx: [baseLayout.sx, (extraLayout as { sx?: unknown }).sx].filter(Boolean),
          },
        }
      : {}),
  } as DatePickerProps["slotProps"];
}
