import { describe, expect, it } from 'vitest';
import { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import {
  matchesRecurringCategoryFilter,
  matchesRecurringDueBucketFilter,
  matchesRecurringFrequencyFilter,
  type RecurringPaymentRow,
} from '../recurringPaymentRowModel';

describe('recurringPaymentRowModel filters', () => {
  const activeMonthlyRow: RecurringPaymentRow = {
    id: 'row-1',
    financialAccountId: 'acc-1',
    name: 'Rent',
    creditDebitType: 'DEBIT',
    expectedAmount: 2500,
    currencyCode: 'BRL',
    frequency: RecurringFrequency.Monthly,
    remindDaysBefore: 3,
    status: RecurringPaymentStatus.Active,
    nextDueDate: '2026-07-05',
    category: { id: 'housing', name: 'Housing', color: '#123456', icon: 'home' },
  };

  const pausedWeeklyRow: RecurringPaymentRow = {
    id: 'row-2',
    financialAccountId: 'acc-1',
    name: 'Gym',
    creditDebitType: 'DEBIT',
    expectedAmount: 150,
    currencyCode: 'BRL',
    frequency: RecurringFrequency.Weekly,
    remindDaysBefore: 3,
    status: RecurringPaymentStatus.Paused,
    nextDueDate: '2026-07-11',
    category: null,
  };

  it('matches category filters including uncategorized', () => {
    expect(matchesRecurringCategoryFilter(activeMonthlyRow, { categoryId: 'housing', uncategorized: false })).toBe(true);
    expect(matchesRecurringCategoryFilter(activeMonthlyRow, { categoryId: 'other', uncategorized: false })).toBe(false);
    expect(matchesRecurringCategoryFilter(pausedWeeklyRow, { categoryId: null, uncategorized: true })).toBe(true);
  });

  it('matches frequency filters', () => {
    expect(matchesRecurringFrequencyFilter(activeMonthlyRow, RecurringFrequency.Monthly)).toBe(true);
    expect(matchesRecurringFrequencyFilter(activeMonthlyRow, RecurringFrequency.Weekly)).toBe(false);
    expect(matchesRecurringFrequencyFilter(activeMonthlyRow, null)).toBe(true);
  });

  it('applies due bucket filters only to active rows', () => {
    const today = Date.parse('2026-07-10T00:00:00.000Z');

    expect(matchesRecurringDueBucketFilter(activeMonthlyRow, 'overdue', today)).toBe(true);
    expect(matchesRecurringDueBucketFilter(activeMonthlyRow, 'next7Days', today)).toBe(false);
    expect(matchesRecurringDueBucketFilter(pausedWeeklyRow, 'next7Days', today)).toBe(false);
    expect(matchesRecurringDueBucketFilter(pausedWeeklyRow, null, today)).toBe(true);
  });
});
