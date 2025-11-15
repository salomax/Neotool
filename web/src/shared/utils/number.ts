export type LocaleSeparators = { decimal: string; group: string | undefined };

/** Get decimal and group separators for a given locale using Intl parts. */
export function getLocaleSeparators(locale: string): LocaleSeparators {
  try {
    const parts = new Intl.NumberFormat(locale).formatToParts(12345.6);
    const decimal = parts.find((p) => p.type === "decimal")?.value ?? ".";
    const group = parts.find((p) => p.type === "group")?.value;
    return { decimal, group };
  } catch {
    return { decimal: ".", group: "," };
  }
}

/**
 * Normalize a user-typed number string to a parseable form.
 * - Accepts both '.' and ',' as decimal markers.
 * - Keeps only the LAST decimal marker as the decimal separator.
 * - Removes group separators and spaces.
 * - Preserves leading '-' if present and allowed.
 * Returns '' or '-' to keep typing state when applicable.
 */
export function normalizeDecimalInput(
  raw: string,
  locale: string,
  allowNegative = true,
): string {
  if (raw == null) return "";
  let s = String(raw).trim();

  // Allow just '-' while typing
  if (s === "-" && allowNegative) return s;
  if (s === "") return "";

  const { decimal: _decimal, group } = getLocaleSeparators(locale);
  // Remove spaces (including NBSP) and group separators
  s = s.replace(/\s|\u00A0/g, "");
  if (group) s = s.split(group).join("");

  // Replace ALL '.' and ',' with a placeholder, then restore only the last as '.'
  const placeholder = "#";
  s = s.replace(/[\.,]/g, placeholder);
  const last = s.lastIndexOf(placeholder);
  if (last !== -1) {
    s =
      s.slice(0, last).replace(new RegExp(placeholder, "g"), "") +
      "." +
      s.slice(last + 1).replace(new RegExp(placeholder, "g"), "");
  }

  // Keep digits, optional leading '-' and single '.'
  s = s.replace(/(?!^-)[^0-9.]/g, "");
  // If multiple dots slipped in, keep only first
  const firstDot = s.indexOf(".");
  if (firstDot !== -1) {
    const head = s.slice(0, firstDot + 1);
    const tail = s.slice(firstDot + 1).replace(/\./g, "");
    s = head + tail;
  }

  // Prevent "-." -> "-0."
  if (s === "-.") s = "-0.";
  if (s.startsWith(".")) s = "0" + s;
  if (s === "-") return allowNegative ? s : "";

  return s;
}

/** Round to fixed fraction digits if provided. */
export function roundTo(n: number, fractionDigits?: number): number {
  if (!Number.isFinite(n)) return n;
  if (typeof fractionDigits !== "number") return n;
  const factor = Math.pow(10, fractionDigits);
  return Math.round(n * factor) / factor;
}

/** Parse a localized number string to number, honoring fractionDigits and allowNegative. */
export function parseLocaleNumber(
  raw: string,
  locale: string,
  opts?: { fractionDigits?: number; allowNegative?: boolean },
): number | "" {
  const s = normalizeDecimalInput(raw, locale, opts?.allowNegative !== false);
  if (s === "" || s === "-") return s as any;
  const n = Number(s);
  if (Number.isNaN(n)) return "" as any;
  return roundTo(n, opts?.fractionDigits);
}
