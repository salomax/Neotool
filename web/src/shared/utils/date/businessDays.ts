import dayjs from 'dayjs';
import { toStartOfDayLocal } from './dateOnly';

/** B3 equities and FIIs (REITs): T+2 settlement in business days (weekdays). */
export const B3_EQUITY_REIT_SETTLEMENT_BUSINESS_DAYS = 2;

/**
 * Whether the local calendar day is Saturday or Sunday.
 * B3 exchange holidays are not applied here; only weekends are skipped.
 */
export function isLocalWeekendDate(date: Date): boolean {
  const d = toStartOfDayLocal(date);
  const dow = d.getDay();
  return dow === 0 || dow === 6;
}

/**
 * Moves forward from the trade (negotiation) date, counting each **weekday**
 * (Mon–Fri) as one business day. The trade date itself is not counted.
 *
 * Example: Friday trade + 2 → Tuesday (skips Sat/Sun).
 */
export function addLocalWeekdayBusinessDays(tradeDate: Date, businessDays: number): Date {
  if (businessDays <= 0) {
    return toStartOfDayLocal(tradeDate);
  }
  let d = dayjs(toStartOfDayLocal(tradeDate));
  let added = 0;
  while (added < businessDays) {
    d = d.add(1, 'day');
    const dow = d.day();
    if (dow !== 0 && dow !== 6) {
      added += 1;
    }
  }
  return toStartOfDayLocal(d.toDate());
}

/**
 * Default **Data de liquidação** for B3 **stocks** and **REITs** (FIIs): D+2 **dias úteis**
 * (Mon–Fri only from the trade date).
 */
export function defaultB3EquityOrReitSettlementFromTradeDate(tradeDate: Date): Date {
  return addLocalWeekdayBusinessDays(tradeDate, B3_EQUITY_REIT_SETTLEMENT_BUSINESS_DAYS);
}
