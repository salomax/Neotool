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
  const cleaned = text
    .replace(/R\$\s*/g, '')
    .replace(/\./g, '')
    .replace(',', '.')
    .trim();
  const value = parseFloat(cleaned);
  return Number.isFinite(value) ? value : 0;
}
