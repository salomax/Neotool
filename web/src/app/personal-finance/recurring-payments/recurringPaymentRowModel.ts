import type { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringFrequency as RF, RecurringPaymentStatus as RPS } from '@/lib/graphql/types/__generated__/graphql';
import type { CategoryTotalItem, FrequencyTotalItem } from '../finance-transactions/summary/types';
import { dateOnlyToMs } from '@/shared/utils/date/dateOnly';

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

export interface RecurringCategoryFilterSelection {
  categoryId: string | null;
  uncategorized: boolean;
}

const MS_PER_DAY = 86400000;

export function startOfTodayUtc(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function parseDueDateMs(nextDueDate: string): number {
  return dateOnlyToMs(nextDueDate);
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

export function matchesRecurringCategoryFilter(
  row: RecurringPaymentRow,
  selection: RecurringCategoryFilterSelection | null,
): boolean {
  if (!selection) return true;
  if (selection.uncategorized) return !row.category;
  return row.category?.id === selection.categoryId;
}

export function matchesRecurringFrequencyFilter(
  row: RecurringPaymentRow,
  frequency: RecurringFrequency | null,
): boolean {
  if (!frequency) return true;
  return row.frequency === frequency;
}

export function matchesRecurringDueBucketFilter(
  row: RecurringPaymentRow,
  bucket: RecurringDueBucket | null,
  today = startOfTodayUtc(),
): boolean {
  if (!bucket) return true;
  if (row.status !== RPS.Active) return false;
  return getRecurringDueBucket(row, today) === bucket;
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

export function computeRecurringSummaryMetrics(rows: RecurringPaymentRow[], today = startOfTodayUtc()) {
  let monthlyCommittedApprox = 0;
  let overdueCount = 0;
  let overdueSum = 0;
  let next7Count = 0;
  let next7Sum = 0;
  let nextMonthCount = 0;
  let nextMonthSum = 0;

  for (const row of rows) {
    if (row.status !== RPS.Active) continue;

    const bucket = getRecurringDueBucket(row, today);
    if (bucket === 'overdue') overdueCount += 1;
    else if (bucket === 'next7Days') next7Count += 1;
    else if (bucket === 'nextMonth') nextMonthCount += 1;

    if (row.expectedAmount != null && Number.isFinite(row.expectedAmount)) {
      const amount = row.expectedAmount;
      if (bucket === 'overdue') overdueSum += amount;
      else if (bucket === 'next7Days') next7Sum += amount;
      else if (bucket === 'nextMonth') nextMonthSum += amount;
      monthlyCommittedApprox += amount * monthlyFactorForFrequency(row.frequency, row.intervalDays);
    }
  }

  return {
    monthlyCommittedApprox,
    overdueCount,
    overdueSum,
    next7Count,
    next7Sum,
    nextMonthCount,
    nextMonthSum,
  };
}

export function buildRecurringCategoryTotals(rows: RecurringPaymentRow[]): CategoryTotalItem[] {
  const activeWithAmount = rows.filter(
    (r) => r.status === RPS.Active && r.expectedAmount != null && Number.isFinite(r.expectedAmount),
  );
  const total = activeWithAmount.reduce((s, r) => s + (r.expectedAmount as number), 0);
  if (total <= 0) return [];

  const map = new Map<
    string,
    { category: RecurringPaymentRow['category']; amount: number; count: number }
  >();

  for (const r of activeWithAmount) {
    const key = r.category?.id ?? '__none__';
    const prev = map.get(key) ?? { category: r.category ?? null, amount: 0, count: 0 };
    prev.amount += r.expectedAmount as number;
    prev.count += 1;
    if (!prev.category && r.category) prev.category = r.category;
    map.set(key, prev);
  }

  return [...map.entries()]
    .map(([key, v]) => {
      const uncategorized = key === '__none__';
      return {
        category: uncategorized
          ? null
          : v.category
            ? {
                id: v.category.id,
                name: v.category.name,
                icon: v.category.icon,
                color: v.category.color,
              }
            : null,
        uncategorized,
        totalAmount: v.amount,
        transactionCount: v.count,
        percentage: Math.round((v.amount / total) * 100),
      };
    })
    .sort((a, b) => b.totalAmount - a.totalAmount);
}

export function buildRecurringFrequencyTotals(rows: RecurringPaymentRow[]): FrequencyTotalItem[] {
  const activeWithAmount = rows.filter(
    (r) => r.status === RPS.Active && r.expectedAmount != null && Number.isFinite(r.expectedAmount),
  );
  const total = activeWithAmount.reduce((s, r) => s + (r.expectedAmount as number), 0);
  if (total <= 0) return [];

  const map = new Map<RecurringFrequency, { amount: number; count: number }>();
  for (const r of activeWithAmount) {
    const prev = map.get(r.frequency) ?? { amount: 0, count: 0 };
    prev.amount += r.expectedAmount as number;
    prev.count += 1;
    map.set(r.frequency, prev);
  }

  return [...map.entries()]
    .map(([frequency, v]) => ({
      frequency,
      totalAmount: v.amount,
      transactionCount: v.count,
      percentage: Math.round((v.amount / total) * 100),
    }))
    .sort((a, b) => b.totalAmount - a.totalAmount);
}
