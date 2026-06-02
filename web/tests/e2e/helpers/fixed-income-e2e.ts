import { formatLocalIsoDate } from './banking-graphql';

/**
 * Coupon day aligned with today so monthly instruments stay inside the
 * pending-action visibility window (typically ~7 days before due).
 *
 * Pair with {@link couponPendingPurchaseDate} — a ~28-day purchase often leaves
 * the first coupon more than 7 days away, so the action exists but is filtered out.
 */
export function couponDaySoon(): number {
  const today = new Date().getDate();
  return Math.min(Math.max(today, 1), 28);
}

/** Lookback used by stable coupon-pending specs (FI-02, P4-UI-07, FI-05). */
export const COUPON_PENDING_PURCHASE_LOOKBACK_DAYS = 60;

/** Trade/issue date for purchases that must surface COUPON_PAYMENT_DUE immediately. */
export function couponPendingPurchaseDate(): string {
  return isoDaysAgo(COUPON_PENDING_PURCHASE_LOOKBACK_DAYS);
}

export function todayIso(): string {
  const now = new Date();
  now.setHours(12, 0, 0, 0);
  return formatLocalIsoDate(now);
}

export function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return formatLocalIsoDate(d);
}

export function isoDaysFromNow(days: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() + days);
  return formatLocalIsoDate(d);
}
