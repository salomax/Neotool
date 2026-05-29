import { formatLocalIsoDate } from './banking-graphql';

/**
 * Coupon day aligned with today so monthly instruments stay inside the
 * pending-action visibility window (typically ~7 days before due).
 */
export function couponDaySoon(): number {
  const today = new Date().getDate();
  return Math.min(Math.max(today, 1), 28);
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
