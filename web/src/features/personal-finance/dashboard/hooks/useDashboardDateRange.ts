import { useEffect, useState } from 'react';
import { DateRangeOption } from '../types';

export interface MonthYear {
  year: number;
  month: number;
}

function toDateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Triggers a render when local date changes (midnight rollover).
 * This keeps month range computations fresh on long-lived dashboard sessions.
 */
export function useTodayKey(): string {
  const [todayKey, setTodayKey] = useState(() => toDateKey(new Date()));

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    const scheduleNextTick = () => {
      const now = new Date();
      const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      const delayMs = Math.max(nextMidnight.getTime() - now.getTime() + 1000, 1000);
      timeoutId = setTimeout(() => {
        setTodayKey(toDateKey(new Date()));
        scheduleNextTick();
      }, delayMs);
    };

    scheduleNextTick();
    return () => {
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, []);

  return todayKey;
}

export function buildMonthRange(
  option: DateRangeOption,
  now: Date = new Date(),
  since?: MonthYear | null,
): MonthYear[] {
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;

  if (option === 'currentMonth') {
    return [{ year: currentYear, month: currentMonth }];
  }

  if (option === 'all' && since) {
    if (since.month < 1 || since.month > 12) {
      throw new RangeError(`Invalid since.month value: ${since.month}`);
    }
    const startDate = new Date(since.year, since.month - 1, 1);
    const endDate = new Date(currentYear, currentMonth - 1, 1);
    if (startDate > endDate) {
      return [{ year: currentYear, month: currentMonth }];
    }

    const months: MonthYear[] = [];
    const cursor = new Date(startDate);
    while (cursor <= endDate) {
      months.push({ year: cursor.getFullYear(), month: cursor.getMonth() + 1 });
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return months;
  }

  const monthCount =
    option === '3m'
      ? 3
      : option === '6m'
        ? 6
        : option === '1y'
          ? 12
          : option === '3y'
            ? 36
            : option === 'all'
              ? 60
              : 1;

  const months: MonthYear[] = [];
  for (let i = monthCount - 1; i >= 0; i--) {
    const d = new Date(currentYear, currentMonth - 1 - i, 1);
    months.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  }
  return months;
}
