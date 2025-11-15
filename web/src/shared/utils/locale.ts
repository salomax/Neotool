export type LocaleSeparators = { decimal: string; group?: string };

export function getLocaleSeparators(locale: string): LocaleSeparators {
  try {
    const parts = new Intl.NumberFormat(locale).formatToParts(12345.6);
    const decimal = parts.find((p) => p.type === "decimal")?.value ?? ".";
    const group = parts.find((p) => p.type === "group")?.value;
    return group ? { decimal, group } : { decimal };
  } catch {
    return { decimal: ".", group: "," };
  }
}

export function getCurrencyDefaultFractionDigits(
  locale: string,
  currency: string,
): number {
  try {
    const opts = new Intl.NumberFormat(locale, {
      style: "currency",
      currency,
    }).resolvedOptions();
    // Some locales might differ min/max by 0/2; use max to keep standard precision
    return Math.max(
      opts.minimumFractionDigits ?? 2,
      opts.maximumFractionDigits ?? 2,
    );
  } catch {
    return 2;
  }
}
