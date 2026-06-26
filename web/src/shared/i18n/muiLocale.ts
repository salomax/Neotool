/**
 * MUI date picker locale utilities.
 *
 * Lives in shared/i18n so that app-level providers can import locale helpers
 * without depending on UI form components.
 */

export function getMuiDateAdapterLocaleFromLanguageTag(lang: string | undefined): string {
  const l = lang?.toLowerCase() ?? "pt";
  return l.startsWith("pt") ? "pt-br" : "en";
}

type MuiDatePickerTranslate = (key: string) => string;

export function buildMuiDatePickerLocaleText(t: MuiDatePickerTranslate) {
  return {
    fieldDayPlaceholder: () => t("datePicker.fieldDay"),
    fieldMonthPlaceholder: () => t("datePicker.fieldMonth"),
    fieldYearPlaceholder: () => t("datePicker.fieldYear"),
    datePickerToolbarTitle: t("datePicker.toolbarTitle"),
    cancelButtonLabel: t("datePicker.actions.cancel"),
    okButtonLabel: t("datePicker.actions.confirm"),
    clearButtonLabel: t("datePicker.actions.clear"),
    todayButtonLabel: t("datePicker.actions.today"),
  };
}
