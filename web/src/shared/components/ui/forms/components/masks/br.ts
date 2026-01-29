// web/src/components/form/masks/br.ts
export function onlyDigits(value: string | number | null | undefined): string {
  return String(value ?? "").replace(/\D+/g, "");
}

export function maskCPF(value: string): string {
  const digits = onlyDigits(value).slice(0, 11);
  return digits
    .replace(/^(\d{3})(\d)/, "$1.$2")
    .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1-$2");
}

export function maskCNPJ(value: string): string {
  const digits = onlyDigits(value).slice(0, 14);
  return digits
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}

export function maskCEP(value: string): string {
  const digits = onlyDigits(value).slice(0, 8);
  return digits.replace(/^(\d{5})(\d{1,3})?$/, (_, p1, p2) =>
    p2 ? `${p1}-${p2}` : p1,
  );
}

export function maskPhoneBR(value: string): string {
  // (99) 99999-9999 or (99) 9999-9999
  const digits = onlyDigits(value).slice(0, 11);
  if (digits.length === 0) return '';
  if (digits.length === 2) {
    return `(${digits})`;
  }
  if (digits.length <= 10) {
    return digits
      .replace(/^(\d{2})(\d)/, "($1) $2")
      .replace(/(\d{4})(\d)/, "$1-$2");
  }
  return digits
    .replace(/^(\d{2})(\d)/, "($1) $2")
    .replace(/(\d{5})(\d)/, "$1-$2");
}

export function parseLocaleNumber(
  input: string,
  locale?: string,
): number | null {
  if (!input || input.trim() === '') return null;
  
  // If no locale is provided, default to en-US (uses . as decimal separator)
  const defaultLocale = locale || 'en-US';
  const example = 1000.5;
  const formatted = Intl.NumberFormat(defaultLocale).format(example);
  const group = formatted.match(/[^0-9]/)?.[0] ?? ",";
  const decimal = formatted.replace(/[0-9]/g, "")[1] ?? ".";

  let normalized = input
    .replace(new RegExp("\\" + group, "g"), "")
    .replace(new RegExp("\\" + decimal), ".");
  const n = parseFloat(normalized);
  return isNaN(n) ? null : n;
}

export function formatCurrency(
  value: number | null | undefined,
  currency = "BRL",
  locale?: string,
): string {
  const v = typeof value === "number" ? value : 0;
  // Default to pt-BR for BRL currency if no locale is provided
  const defaultLocale = locale || (currency === "BRL" ? "pt-BR" : undefined);
  return new Intl.NumberFormat(defaultLocale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(v);
}

/**
 * Format large currency values (billions/millions) in a compact format
 * Example: 30000000000 -> "R$ 30 bi" (with billionsLabel="bi")
 * 
 * @param value - The numeric value to format
 * @param currency - ISO currency code (default: "BRL")
 * @param locale - Locale string (default: "pt-BR" for BRL, otherwise undefined)
 * @param labels - Object with "billions" and "millions" labels for compact format
 * @returns Formatted currency string
 */
export function formatLargeCurrency(
  value: number | null | undefined,
  currency = "BRL",
  locale?: string,
  labels?: { billions: string; millions: string },
): string {
  if (value == null || value === 0) {
    return formatCurrency(0, currency, locale);
  }

  const isNegative = value < 0;
  const absValue = Math.abs(value);

  const defaultLocale = locale || (currency === "BRL" ? "pt-BR" : undefined);
  const billionsLabel = labels?.billions || "bi";
  const millionsLabel = labels?.millions || "mi";

  const billions = absValue / 1_000_000_000;

  if (billions >= 1) {
    // Extract currency symbol using formatToParts
    const parts = new Intl.NumberFormat(defaultLocale, {
      style: "currency",
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).formatToParts(1);
    const currencySymbol = parts.find((p) => p.type === "currency")?.value || "";

    const formatted = `${currencySymbol} ${billions.toFixed(0)} ${billionsLabel}`;
    return isNegative ? `-${formatted}` : formatted;
  }

  const millions = absValue / 1_000_000;
  if (millions >= 1) {
    // Extract currency symbol using formatToParts
    const parts = new Intl.NumberFormat(defaultLocale, {
      style: "currency",
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).formatToParts(1);
    const currencySymbol = parts.find((p) => p.type === "currency")?.value || "";

    const formatted = `${currencySymbol} ${millions.toFixed(1)} ${millionsLabel}`;
    return isNegative ? `-${formatted}` : formatted;
  }

  return formatCurrency(value, currency, locale);
}
