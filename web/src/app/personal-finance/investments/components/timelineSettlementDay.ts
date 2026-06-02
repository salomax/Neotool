/**
 * Calendar day key (YYYY-MM-DD) for comparing trade vs settlement without timezone surprises
 * when the API sends plain dates.
 */
export function investmentTransactionCalendarDayKey(value: unknown): string | null {
  if (value == null || value === "") return null;
  const raw = String(value).trim();
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(raw);
  if (m) return `${m[1]}-${m[2]}-${m[3]}`;
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return null;
  const y = d.getUTCFullYear();
  const mo = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${mo}-${day}`;
}

export function isSameInvestmentEventAndSettlementDay(tradeDate: unknown, settlementDate: unknown): boolean {
  const t = investmentTransactionCalendarDayKey(tradeDate);
  const s = investmentTransactionCalendarDayKey(settlementDate);
  return t != null && s != null && t === s;
}
