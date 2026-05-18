const DATE_ONLY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

export function isValidDate(value: unknown): value is Date {
  return value instanceof Date && !Number.isNaN(value.getTime());
}

export function toStartOfDayLocal(date: Date): Date {
  if (!isValidDate(date)) return new Date(Number.NaN);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function parseDateOnly(input: string | null | undefined): Date | null {
  if (!input) return null;
  const raw = input.trim();
  if (!raw) return null;

  const dateOnlyMatch = DATE_ONLY_PATTERN.exec(raw);
  if (dateOnlyMatch) {
    const year = Number(dateOnlyMatch[1]);
    const month = Number(dateOnlyMatch[2]);
    const day = Number(dateOnlyMatch[3]);
    const parsed = new Date(year, month - 1, day);
    if (!isValidDate(parsed)) return null;
    if (
      parsed.getFullYear() !== year ||
      parsed.getMonth() !== month - 1 ||
      parsed.getDate() !== day
    ) {
      return null;
    }
    return parsed;
  }

  const parsed = new Date(raw);
  if (!isValidDate(parsed)) return null;
  return toStartOfDayLocal(parsed);
}

export function formatDateOnly(date: Date): string {
  if (!isValidDate(date)) {
    throw new Error('formatDateOnly requires a valid Date');
  }

  const normalized = toStartOfDayLocal(date);
  const year = normalized.getFullYear();
  const month = String(normalized.getMonth() + 1).padStart(2, '0');
  const day = String(normalized.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Local start-of-day epoch ms for a date-only or datetime string; NaN when unparseable. */
export function dateOnlyToMs(input: string | null | undefined): number {
  const parsed = parseDateOnly(input);
  return parsed ? parsed.getTime() : NaN;
}

/** Format a date-only or datetime string for display in the user's locale (calendar day preserved). */
export function formatDateOnlyLocale(
  input: string | null | undefined,
  locale = 'pt-BR',
): string {
  if (input == null || String(input).trim() === '') return '';
  const parsed = parseDateOnly(input);
  if (!parsed) return String(input);
  return new Intl.DateTimeFormat(locale).format(parsed);
}
