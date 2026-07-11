import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';
import { PurchaseWishlistSummaryPanel } from '../PurchaseWishlistSummaryPanel';
import type { WishlistItemRow } from '../wishlistRowModel';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string | number>) =>
      values ? `${key}:${Object.values(values).join(',')}` : key,
  }),
}));

describe.sequential('PurchaseWishlistSummaryPanel', () => {
  const theme = createTheme();
  const rows = [
    {
      id: 'row-1',
      name: 'Nintendo Switch 2',
      estimatedPrice: 3999.9,
      currencyCode: 'BRL',
      category: { id: 'gaming', name: 'Gaming', color: '#123456', icon: 'stadia' },
      priority: WishlistItemPriority.High,
      notes: null,
      targetDate: null,
      productUrl: 'https://shop.example.com/switch-2',
      productImageUrl: null,
      status: 'ACTIVE',
      sortOrder: 0,
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
      sortOrder: 1,
      purchasedAt: null,
      createdAt: '2026-07-10T00:00:00Z',
      updatedAt: '2026-07-10T00:00:00Z',
    },
  ] as unknown as WishlistItemRow[];

  afterEach(() => cleanup());

  function renderWithTheme(component: React.ReactNode) {
    return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
  }

  it('forwards priority and category filters from the summary', () => {
    const onPriorityClick = vi.fn();
    const onCategoryClick = vi.fn();

    renderWithTheme(
      <PurchaseWishlistSummaryPanel
        rows={rows}
        onPriorityClick={onPriorityClick}
        onCategoryClick={onCategoryClick}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'purchaseWishlist.form.priority: purchaseWishlist.priority.HIGH' }));
    fireEvent.click(screen.getByRole('button', { name: /filterByCategory: Gaming/i }));

    expect(onPriorityClick).toHaveBeenCalledWith(WishlistItemPriority.High);
    expect(onCategoryClick).toHaveBeenCalledWith({ categoryId: 'gaming', uncategorized: false });
  });

  it('renders the uncategorized bucket when all items have no category', () => {
    renderWithTheme(
      <PurchaseWishlistSummaryPanel
        rows={
          [
            {
              ...rows[1],
              id: 'row-3',
            },
          ] as WishlistItemRow[]
        }
      />,
    );

    expect(screen.getByText('purchaseWishlist.summary.byCategory')).toBeInTheDocument();
  });
});
