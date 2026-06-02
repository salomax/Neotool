/**
 * Currency strings as rendered in the UI for E2E (typically pt-BR: `R$ 1.234,56`).
 *
 * **Test id rule:** When you `textContent()` a node and parse it with {@link parseCurrencyBr},
 * the `data-testid` must be on an element that contains **only** the formatted amount (e.g. the
 * value `Typography` or `CurrencyAmountParts`). If the test id sits on a parent that also includes
 * labels, percentages, or other copy, concatenated text breaks `parseFloat` and you often get `0`.
 *
 * Production examples: `MonthSummary` uses `amountTestId` on income/expense metric rows;
 * `InvestmentSummary` uses dedicated test ids on portfolio / gain / metric amounts.
 */
export function parseCurrencyBr(text: string): number {
  const normalized = text
    .replace(/[^\d,.-]/g, '')
    .trim();

  if (!normalized) return 0;

  const lastComma = normalized.lastIndexOf(',');
  const lastDot = normalized.lastIndexOf('.');
  const decimalSeparator =
    lastComma > lastDot
      ? ','
      : lastDot > lastComma
        ? '.'
        : null;

  const cleaned = decimalSeparator
    ? normalized
        .replace(new RegExp(`\\${decimalSeparator === ',' ? '.' : ','}`, 'g'), '')
        .replace(decimalSeparator, '.')
    : normalized.replace(/[,.]/g, '');

  const value = parseFloat(cleaned);
  return Number.isFinite(value) ? value : 0;
}

/**
 * Parses percentage strings rendered in pt-BR UI (e.g. `12,34%`, `-0,50%`) into decimal values
 * (e.g. `0.1234`, `-0.005`).
 */
export function parsePercentBr(text: string): number | null {
  const normalizedMinus = text.replace(/−/g, '-');
  const cleaned = normalizedMinus
    .replace(/\s/g, '')
    .replace('%', '')
    .replace(/\./g, '')
    .replace(',', '.');

  if (!cleaned || cleaned === '-' || cleaned === '+') return null;

  const value = parseFloat(cleaned);
  if (!Number.isFinite(value)) return null;
  return value / 100;
}
