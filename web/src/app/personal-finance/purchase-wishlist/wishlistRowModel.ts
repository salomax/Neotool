'use client';

import type {
  PurchaseWishlistActiveItemsQuery,
} from './purchaseWishlistOperations.generated';
import type { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';

export type WishlistItemRow = PurchaseWishlistActiveItemsQuery['wishlistItems'][number];

const PRIORITY_ORDER: Record<WishlistItemPriority, number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
};

export type WishlistSummaryMetrics = {
  totalEstimated: number;
  totalItems: number;
  byPriority: Record<WishlistItemPriority, { count: number; totalEstimated: number }>;
  byCategory: Array<{
    key: string;
    name: string;
    count: number;
    totalEstimated: number;
    percentage: number;
    category: WishlistItemRow['category'];
  }>;
};

export type WishlistCategoryFilterSelection = {
  categoryId: string | null;
  uncategorized: boolean;
};

export function sortWishlistItems(rows: WishlistItemRow[]): WishlistItemRow[] {
  return [...rows].sort((left, right) => {
    if (left.sortOrder !== right.sortOrder) {
      return left.sortOrder - right.sortOrder;
    }

    const priorityDelta = PRIORITY_ORDER[left.priority] - PRIORITY_ORDER[right.priority];
    if (priorityDelta !== 0) {
      return priorityDelta;
    }

    return left.name.localeCompare(right.name, 'pt-BR');
  });
}

export function applyWishlistOrder(rows: WishlistItemRow[], orderedIds: string[] | null): WishlistItemRow[] {
  if (!orderedIds || orderedIds.length === 0) {
    return rows;
  }

  const rowById = new Map(rows.map((row) => [row.id, row] as const));
  const orderedRows: WishlistItemRow[] = [];

  for (const id of orderedIds) {
    const row = rowById.get(id);
    if (row) {
      orderedRows.push(row);
      rowById.delete(id);
    }
  }

  if (orderedRows.length === rows.length) {
    return orderedRows;
  }

  return [...orderedRows, ...rows.filter((row) => rowById.has(row.id))];
}

export function matchesWishlistCategoryFilter(
  row: Pick<WishlistItemRow, 'category'>,
  activeCategoryFilter: WishlistCategoryFilterSelection | null,
): boolean {
  if (!activeCategoryFilter) {
    return true;
  }

  if (activeCategoryFilter.uncategorized) {
    return row.category == null;
  }

  return row.category?.id === activeCategoryFilter.categoryId;
}

export function filterWishlistItems(
  rows: WishlistItemRow[],
  query: string,
  filters?: {
    priority?: WishlistItemPriority | null;
    category?: WishlistCategoryFilterSelection | null;
  },
): WishlistItemRow[] {
  const trimmedQuery = query.trim().toLowerCase();

  return rows.filter((row) => {
    if (filters?.priority && row.priority !== filters.priority) {
      return false;
    }

    if (!matchesWishlistCategoryFilter(row, filters?.category ?? null)) {
      return false;
    }

    if (!trimmedQuery) {
      return true;
    }

    const haystack = [row.name, row.notes, row.productUrl, row.category?.name, row.priority]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();

    return haystack.includes(trimmedQuery);
  });
}

export function computeWishlistSummaryMetrics(rows: WishlistItemRow[]): WishlistSummaryMetrics {
  const metrics: WishlistSummaryMetrics = {
    totalEstimated: 0,
    totalItems: rows.length,
    byPriority: {
      HIGH: { count: 0, totalEstimated: 0 },
      MEDIUM: { count: 0, totalEstimated: 0 },
      LOW: { count: 0, totalEstimated: 0 },
    },
    byCategory: [],
  };

  const byCategoryMap = new Map<string, WishlistSummaryMetrics['byCategory'][number]>();

  rows.forEach((row) => {
    const estimate = row.estimatedPrice ?? 0;
    metrics.totalEstimated += estimate;
    metrics.byPriority[row.priority].count += 1;
    metrics.byPriority[row.priority].totalEstimated += estimate;

    const key = row.category?.id ?? 'uncategorized';
    const current = byCategoryMap.get(key);
    if (current) {
      current.count += 1;
      current.totalEstimated += estimate;
      return;
    }

    byCategoryMap.set(key, {
      key,
      name: row.category?.name ?? 'uncategorized',
      count: 1,
      totalEstimated: estimate,
      percentage: 0,
      category: row.category,
    });
  });

  metrics.byCategory = [...byCategoryMap.values()].sort((left, right) => {
    if (right.count !== left.count) {
      return right.count - left.count;
    }

    return left.name.localeCompare(right.name, 'pt-BR');
  });

  metrics.byCategory = metrics.byCategory.map((entry) => ({
    ...entry,
    percentage: metrics.totalEstimated > 0 ? (entry.totalEstimated / metrics.totalEstimated) * 100 : 0,
  }));

  return metrics;
}

export function formatWishlistCurrency(value: number | null | undefined, currency = 'BRL'): string {
  if (value == null) {
    return '—';
  }

  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
  }).format(value);
}

export function formatWishlistDate(value: unknown): string {
  if (typeof value !== 'string' || !value) {
    return '—';
  }

  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('pt-BR').format(date);
}

export function hasValidExternalUrl(value: unknown): boolean {
  if (typeof value !== 'string' || !value) {
    return false;
  }

  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}
