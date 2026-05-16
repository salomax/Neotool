import type { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringFrequency as RF, RecurringPaymentStatus as RPS } from '@/lib/graphql/types/__generated__/graphql';

export interface RecurringPaymentRow {
  id: string;
  financialAccountId: string | null;
  name: string;
  creditDebitType: 'CREDIT' | 'DEBIT';
  type?: string | null;
  expectedAmount?: number | null;
  currencyCode: string;
  frequency: RecurringFrequency;
  dayOfMonth?: number | null;
  intervalDays?: number | null;
  remindDaysBefore: number;
  status: RecurringPaymentStatus;
  nextDueDate: string;
  lastPaidDate?: string | null;
  notes?: string | null;
  category?: { id: string; name: string; color: string | null; icon: string | null } | null;
  merchant?: { id: string; name: string; logoAsset?: { publicUrl: string | null } | null } | null;
}

const MS_PER_DAY = 86400000;

export function startOfTodayUtc(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function parseDueDateMs(nextDueDate: string): number {
  const t = Date.parse(String(nextDueDate));
  return Number.isNaN(t) ? NaN : t;
}

export function isOverdue(row: RecurringPaymentRow): boolean {
  if (row.status !== RPS.Active) return false;
  const t = parseDueDateMs(row.nextDueDate);
  if (Number.isNaN(t)) return false;
  return t < startOfTodayUtc();
}

export type RecurringDueBucket = 'overdue' | 'next7Days' | 'nextMonth';

const DUE_BUCKET_ORDER: RecurringDueBucket[] = ['overdue', 'next7Days', 'nextMonth'];

/** Classifies a row by due date for table grouping (overdue → next 7 days → after that). */
export function getRecurringDueBucket(row: RecurringPaymentRow, today = startOfTodayUtc()): RecurringDueBucket | null {
  const dueMs = parseDueDateMs(row.nextDueDate);
  if (Number.isNaN(dueMs)) return 'nextMonth';

  const weekEnd = today + 7 * MS_PER_DAY;
  if (dueMs < today) return 'overdue';
  if (dueMs < weekEnd) return 'next7Days';
  return 'nextMonth';
}

export function groupRecurringPaymentsByDueBucket(
  rows: RecurringPaymentRow[],
): { bucket: RecurringDueBucket; rows: RecurringPaymentRow[] }[] {
  const buckets: Record<RecurringDueBucket, RecurringPaymentRow[]> = {
    overdue: [],
    next7Days: [],
    nextMonth: [],
  };

  for (const row of rows) {
    const bucket = getRecurringDueBucket(row);
    if (bucket) buckets[bucket].push(row);
  }

  const sortByDueThenName = (list: RecurringPaymentRow[]) =>
    [...list].sort((a, b) => {
      const da = parseDueDateMs(a.nextDueDate);
      const db = parseDueDateMs(b.nextDueDate);
      if (!Number.isNaN(da) && !Number.isNaN(db) && da !== db) return da - db;
      return a.name.localeCompare(b.name);
    });

  return DUE_BUCKET_ORDER.map((bucket) => ({
    bucket,
    rows: sortByDueThenName(buckets[bucket]),
  })).filter((g) => g.rows.length > 0);
}

/** Rough monthly factor for "committed monthly" KPI (subtitle: approximate). */
export function monthlyFactorForFrequency(frequency: RecurringFrequency, intervalDays?: number | null): number {
  switch (frequency) {
    case RF.Monthly:
      return 1;
    case RF.Weekly:
      return 52 / 12;
    case RF.Yearly:
      return 1 / 12;
    case RF.Custom: {
      const days = intervalDays && intervalDays > 0 ? intervalDays : 30;
      return 30 / days;
    }
    default:
      return 1;
  }
}

export function computeRecurringSummaryMetrics(rows: RecurringPaymentRow[]) {
  const today = startOfTodayUtc();
  const weekEnd = today + 7 * MS_PER_DAY;

  let monthlyCommittedApprox = 0;
  let next7Count = 0;
  let next7Sum = 0;
  let overdueCount = 0;

  for (const row of rows) {
    if (row.status !== RPS.Active) continue;

    const dueMs = parseDueDateMs(row.nextDueDate);
    if (!Number.isNaN(dueMs)) {
      if (dueMs < today) overdueCount += 1;
      if (dueMs >= today && dueMs < weekEnd) {
        next7Count += 1;
        if (row.expectedAmount != null && Number.isFinite(row.expectedAmount)) {
          next7Sum += row.expectedAmount;
        }
      }
    }

    if (row.expectedAmount != null && Number.isFinite(row.expectedAmount)) {
      monthlyCommittedApprox += row.expectedAmount * monthlyFactorForFrequency(row.frequency, row.intervalDays);
    }
  }

  return { monthlyCommittedApprox, next7Count, next7Sum, overdueCount };
}

export type DistributionMode = 'category' | 'frequency';

export function buildCategoryDistribution(rows: RecurringPaymentRow[]): { key: string; label: string; amount: number; pct: number }[] {
  const activeWithAmount = rows.filter(
    (r) => r.status === RPS.Active && r.expectedAmount != null && Number.isFinite(r.expectedAmount),
  );
  const total = activeWithAmount.reduce((s, r) => s + (r.expectedAmount as number), 0);
  if (total <= 0) return [];

  const map = new Map<string, { label: string; amount: number }>();
  for (const r of activeWithAmount) {
    const key = r.category?.id ?? '__none__';
    const label = r.category?.name ?? '';
    const prev = map.get(key) ?? { label, amount: 0 };
    prev.amount += r.expectedAmount as number;
    map.set(key, prev);
  }

  return [...map.entries()]
    .map(([key, v]) => ({
      key,
      label: v.label,
      amount: v.amount,
      pct: Math.round((v.amount / total) * 100),
    }))
    .sort((a, b) => b.amount - a.amount);
}

export function buildFrequencyDistribution(rows: RecurringPaymentRow[]): { key: string; label: string; amount: number; pct: number }[] {
  const activeWithAmount = rows.filter(
    (r) => r.status === RPS.Active && r.expectedAmount != null && Number.isFinite(r.expectedAmount),
  );
  const total = activeWithAmount.reduce((s, r) => s + (r.expectedAmount as number), 0);
  if (total <= 0) return [];

  const map = new Map<string, number>();
  for (const r of activeWithAmount) {
    map.set(r.frequency, (map.get(r.frequency) ?? 0) + (r.expectedAmount as number));
  }

  return [...map.entries()]
    .map(([key, amount]) => ({
      key,
      label: key,
      amount,
      pct: Math.round((amount / total) * 100),
    }))
    .sort((a, b) => b.amount - a.amount);
}
