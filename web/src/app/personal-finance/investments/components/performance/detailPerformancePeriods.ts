"use client";

import { HoldingPerformanceResolution } from "@/lib/graphql/types/__generated__/graphql";

export type DetailPerformancePeriod = "1M" | "3M" | "6M" | "1A" | "2A" | "ALL";

export const DETAIL_PERFORMANCE_PERIODS: DetailPerformancePeriod[] = ["1M", "3M", "6M", "1A", "2A", "ALL"];

interface DetailPerformanceQueryWindow {
  resolution: HoldingPerformanceResolution;
  fromDate?: string;
  toDate?: string;
}

const TWO_YEARS_IN_DAYS = 366 * 2;

export function resolveDetailPerformanceWindow(
  period: DetailPerformancePeriod,
  firstTradeDate: Date | null,
  today: Date = new Date(),
): DetailPerformanceQueryWindow {
  const clampedToday = startOfDay(today);
  if (period === "ALL") {
    const effectiveStart = firstTradeDate ? startOfDay(firstTradeDate) : null;
    const resolution =
      effectiveStart && diffInDays(effectiveStart, clampedToday) <= TWO_YEARS_IN_DAYS
        ? HoldingPerformanceResolution.Day
        : HoldingPerformanceResolution.Month;
    return {
      resolution,
      toDate: toLocalDateInput(clampedToday),
    };
  }

  return {
    resolution: HoldingPerformanceResolution.Day,
    fromDate: toLocalDateInput(subtractPeriod(clampedToday, period)),
    toDate: toLocalDateInput(clampedToday),
  };
}

export function parseUnknownDate(value: unknown): Date | null {
  if (!value) return null;
  if (value instanceof Date && !Number.isNaN(value.getTime())) return value;
  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const parsed = new Date(Number(year), Number(month) - 1, Number(day));
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function subtractPeriod(date: Date, period: Exclude<DetailPerformancePeriod, "ALL">): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  if (period === "1M") {
    next.setMonth(next.getMonth() - 1);
    return next;
  }
  if (period === "3M") {
    next.setMonth(next.getMonth() - 3);
    return next;
  }
  if (period === "6M") {
    next.setMonth(next.getMonth() - 6);
    return next;
  }
  if (period === "1A") {
    next.setFullYear(next.getFullYear() - 1);
    return next;
  }
  next.setFullYear(next.getFullYear() - 2);
  return next;
}

function diffInDays(from: Date, to: Date): number {
  const utcFrom = Date.UTC(from.getFullYear(), from.getMonth(), from.getDate());
  const utcTo = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.floor((utcTo - utcFrom) / 86_400_000);
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function toLocalDateInput(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
