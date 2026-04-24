'use client';

import { formatCurrency } from './investmentTableHelpers';
import type { HoldingRow } from './investmentTableHelpers';

const quantityFormatterCache = new Map<string, Intl.NumberFormat>();
const percentFormatterCache = new Map<string, Intl.NumberFormat>();

export function formatQuantity(value: unknown, locale: string): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';

  let formatter = quantityFormatterCache.get(locale);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 8 });
    quantityFormatterCache.set(locale, formatter);
  }

  return formatter.format(value);
}

export function formatPercentageRate(value: number, locale: string): string {
  let formatter = percentFormatterCache.get(locale);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, {
      style: 'percent',
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });
    percentFormatterCache.set(locale, formatter);
  }

  return formatter.format(value);
}

export function computePrincipal(holding: HoldingRow): number | null {
  if (typeof holding.averageCost !== 'number' || typeof holding.quantity !== 'number') return null;
  return holding.averageCost * holding.quantity;
}

export function formatValueOrPlaceholder(
  value: number | null | undefined,
  currencyCode: string | null | undefined,
  locale: string,
): string {
  if (typeof value !== 'number' || !Number.isFinite(value) || !currencyCode) return '—';
  return formatCurrency(value, currencyCode, locale);
}
