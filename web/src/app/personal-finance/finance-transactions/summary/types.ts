import type { RecurringFrequency } from '@/lib/graphql/types/__generated__/graphql';

export interface CategoryTotalItem {
  category: { id: string; name: string; icon: string | null; color: string | null } | null;
  uncategorized: boolean;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface CategoryFilterSelection {
  categoryId: string | null;
  uncategorized: boolean;
}

export interface TagTotalItem {
  tag: string;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface FrequencyTotalItem {
  frequency: RecurringFrequency;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface MonthSummaryData {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  closingBalance: number;
  openingBalance: number;
  transfersIn: number;
  transfersOut: number;
  projectedClosingBalance: number | null;
  isCurrentMonth: boolean;
  isFutureMonth?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  tagsSummary?: TagTotalItem[];
}
