import dayjs from 'dayjs';
import { z } from 'zod';
import { defaultB3EquityOrReitSettlementFromTradeDate } from '@/shared/utils/date/businessDays';
import { toStartOfDayLocal } from '@/shared/utils/date/dateOnly';

/**
 * Aligns with update-holding asset classes (`fixedIncome`, `fund`, `equity`, `crypto`) without importing app modules.
 */
export type InvestmentSettlementAssetKind = 'fixedIncome' | 'fund' | 'equity' | 'crypto';

/** Maximum calendar span from trade date to settlement date (inclusive). */
export const SETTLEMENT_MAX_YEARS_AFTER_TRADE = 1;

/**
 * Default **Data de liquidação** from the trade / negotiation date (same rules for inflow and outflow):
 * - **Fixed income** and **funds**: D+0 (same calendar day as trade).
 * - **Equities / REITs (FIIs)**: D+2 **weekday** business days (Mon–Fri; B3 holidays not modeled on the client).
 * - **Crypto**: D+0.
 */
export function defaultSettlementDateFromTrade(kind: InvestmentSettlementAssetKind, tradeDate: Date): Date {
  const trade = toStartOfDayLocal(tradeDate);
  if (kind === 'equity') {
    return defaultB3EquityOrReitSettlementFromTradeDate(trade);
  }
  return trade;
}

/** Latest allowed settlement date: trade date + {@link SETTLEMENT_MAX_YEARS_AFTER_TRADE} (start of day). */
export function maxSettlementDateFromTrade(tradeDate: Date): Date {
  return dayjs(toStartOfDayLocal(tradeDate))
    .add(SETTLEMENT_MAX_YEARS_AFTER_TRADE, 'year')
    .startOf('day')
    .toDate();
}

export function getInvestmentSettlementBounds(
  tradeDate: Date | undefined,
  minAllowedDate: Date,
): { min: Date; max: Date } {
  if (!tradeDate || Number.isNaN(tradeDate.getTime())) {
    const min = toStartOfDayLocal(minAllowedDate);
    return { min, max: dayjs(min).add(SETTLEMENT_MAX_YEARS_AFTER_TRADE, 'year').toDate() };
  }
  const trade = dayjs(toStartOfDayLocal(tradeDate)).startOf('day');
  const minBound = dayjs(toStartOfDayLocal(minAllowedDate)).startOf('day');
  return {
    min: trade.isAfter(minBound, 'day') ? trade.toDate() : minBound.toDate(),
    max: maxSettlementDateFromTrade(tradeDate),
  };
}

/**
 * Picks a form settlement date: uses `settlementDate` when present, otherwise the default for `kind`,
 * then clamps to `[tradeDate, tradeDate + 1 year]` (inclusive, start-of-day).
 */
export function coerceInvestmentSettlementDate(
  kind: InvestmentSettlementAssetKind,
  tradeDate: Date,
  settlementDate?: Date,
): Date {
  const trade = dayjs(toStartOfDayLocal(tradeDate)).startOf('day');
  const maxD = dayjs(maxSettlementDateFromTrade(tradeDate)).startOf('day');
  const proposed = dayjs(toStartOfDayLocal(defaultSettlementDateFromTrade(kind, tradeDate))).startOf('day');
  const base =
    settlementDate != null
      ? dayjs(toStartOfDayLocal(settlementDate)).startOf('day')
      : proposed;
  let s = base;
  if (s.isBefore(trade, 'day')) s = trade;
  if (s.isAfter(maxD, 'day')) s = maxD;
  return toStartOfDayLocal(s.toDate());
}

export function validateInvestmentSettlementDates(
  data: { tradeDate?: Date; settlementDate?: Date },
  ctx: z.RefinementCtx,
  t: (key: string, options?: Record<string, unknown>) => string,
): void {
  if (!data.tradeDate || !data.settlementDate) return;

  const trade = dayjs(data.tradeDate).startOf('day');
  const settlement = dayjs(data.settlementDate).startOf('day');

  if (settlement.isBefore(trade, 'day')) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.updateHolding.validation.settlementOnOrAfterTrade'),
      path: ['settlementDate'],
    });
  }

  if (settlement.isAfter(trade.add(SETTLEMENT_MAX_YEARS_AFTER_TRADE, 'year'), 'day')) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.updateHolding.validation.settlementMaxOneYearFromTrade'),
      path: ['settlementDate'],
    });
  }
}
