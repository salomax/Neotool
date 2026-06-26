"use client";

import * as React from "react";
import { LocalizationProvider } from "@mui/x-date-pickers";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { useTranslation } from "react-i18next";
import "dayjs/locale/pt-br";
import {
  buildMuiDatePickerLocaleText,
  getMuiDateAdapterLocaleFromLanguageTag,
} from "./muiLocale";

function useAppLocalization() {
  const { i18n, t } = useTranslation("common");
  const adapterLocale = React.useMemo(
    () => getMuiDateAdapterLocaleFromLanguageTag(i18n.language),
    [i18n.language],
  );
  const localeText = React.useMemo(() => buildMuiDatePickerLocaleText(t), [t]);
  return { adapterLocale, localeText };
}

export function AppLocalizationProvider({ children }: { children: React.ReactNode }) {
  const { adapterLocale, localeText } = useAppLocalization();
  return (
    <LocalizationProvider
      dateAdapter={AdapterDayjs}
      adapterLocale={adapterLocale}
      localeText={localeText}
    >
      {children}
    </LocalizationProvider>
  );
}
