import { describe, expect, it } from 'vitest';
import { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';
import {
  computeWishlistSummaryMetrics,
  filterWishlistItems,
  matchesWishlistCategoryFilter,
  sortWishlistItems,
  type WishlistItemRow,
} from '../wishlistRowModel';

const rows = [
  {
    id: 'row-1',
    name: 'Nintendo Switch 2',
    estimatedPrice: 3999.9,
    currencyCode: 'BRL',
    category: { id: 'gaming', name: 'Gaming', color: '#123456', icon: 'stadia' },
    priority: WishlistItemPriority.High,
    notes: 'Bundle',
    targetDate: null,
    productUrl: 'https://shop.example.com/switch-2',
    productImageUrl: null,
    status: 'ACTIVE',
    sortOrder: 1,
    purchasedAt: null,
    createdAt: '2026-07-10T00:00:00Z',
    updatedAt: '2026-07-10T00:00:00Z',
  },
  {
    id: 'row-2',
    name: 'Office Chair',
    estimatedPrice: 899.9,
    currencyCode: 'BRL',
    category: null,
    priority: WishlistItemPriority.Medium,
    notes: null,
    targetDate: null,
    productUrl: null,
    productImageUrl: null,
    status: 'ACTIVE',
    sortOrder: 0,
    purchasedAt: null,
    createdAt: '2026-07-10T00:00:00Z',
    updatedAt: '2026-07-10T00:00:00Z',
  },
  {
    id: 'row-3',
    name: 'Headphones',
    estimatedPrice: 599.9,
    currencyCode: 'BRL',
    category: { id: 'gaming', name: 'Gaming', color: '#123456', icon: 'stadia' },
    priority: WishlistItemPriority.Low,
    notes: null,
    targetDate: null,
    productUrl: null,
    productImageUrl: null,
    status: 'ACTIVE',
    sortOrder: 2,
    purchasedAt: null,
    createdAt: '2026-07-10T00:00:00Z',
    updatedAt: '2026-07-10T00:00:00Z',
  },
] as unknown as WishlistItemRow[];

describe('wishlistRowModel', () => {
  it('sorts rows by sortOrder before priority and name', () => {
    expect(sortWishlistItems(rows).map((row) => row.id)).toEqual(['row-2', 'row-1', 'row-3']);
  });

  it('filters rows by text, priority, and category', () => {
    const filtered = filterWishlistItems(rows, 'switch', {
      priority: WishlistItemPriority.High,
      category: { categoryId: 'gaming', uncategorized: false },
    });

    expect(filtered.map((row) => row.id)).toEqual(['row-1']);
  });

  it('matches uncategorized category filters explicitly', () => {
    expect(matchesWishlistCategoryFilter(rows[1], { categoryId: null, uncategorized: true })).toBe(true);
    expect(matchesWishlistCategoryFilter(rows[0], { categoryId: null, uncategorized: true })).toBe(false);
  });

  it('computes totals by priority and category', () => {
    const metrics = computeWishlistSummaryMetrics(rows);

    expect(metrics.totalItems).toBe(3);
    expect(metrics.totalEstimated).toBeCloseTo(5499.7, 2);
    expect(metrics.byPriority[WishlistItemPriority.High].count).toBe(1);
    expect(metrics.byCategory[0]?.name).toBe('Gaming');
    expect(metrics.byCategory[0]?.count).toBe(2);
  });
});
