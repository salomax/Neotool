import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PurchaseDecisionDialog } from '../PurchaseDecisionDialog';
import type { WishlistItemRow } from '../wishlistRowModel';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) =>
      values ? `${key}:${Object.values(values).join(',')}` : key,
  }),
}));

describe.sequential('PurchaseDecisionDialog', () => {
  const theme = createTheme();
  const row = {
    id: 'item-1',
    name: 'Nintendo Switch 2',
    estimatedPrice: 3999.9,
    currencyCode: 'BRL',
    category: null,
    priority: 'HIGH',
    notes: null,
    targetDate: null,
    productUrl: 'https://shop.example.com/switch-2',
    productImageUrl: 'https://cdn.example.com/switch-2.png',
    status: 'ACTIVE',
    sortOrder: 0,
    purchasedAt: null,
    createdAt: '2026-07-10T00:00:00Z',
    updatedAt: '2026-07-10T00:00:00Z',
  } as unknown as WishlistItemRow;

  afterEach(() => cleanup());

  function renderDialog(overrides: Partial<React.ComponentProps<typeof PurchaseDecisionDialog>> = {}) {
    return render(
      <ThemeProvider theme={theme}>
        <PurchaseDecisionDialog
          row={row}
          onClose={vi.fn()}
          onConfirmPurchase={vi.fn()}
          onConfirmPurchaseWithTransaction={vi.fn()}
          {...overrides}
        />
      </ThemeProvider>,
    );
  }

  it('renders the selected item name in the confirmation message', () => {
    renderDialog();

    expect(screen.getByText('purchaseWishlist.purchaseDialog.message:Nintendo Switch 2')).toBeInTheDocument();
  });

  it('forwards the purchase actions', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onConfirmPurchase = vi.fn();
    const onConfirmPurchaseWithTransaction = vi.fn();
    renderDialog({ onClose, onConfirmPurchase, onConfirmPurchaseWithTransaction });

    await user.click(screen.getByText('purchaseWishlist.purchaseDialog.confirm').closest('button')!);
    await user.click(screen.getByText('purchaseWishlist.purchaseDialog.confirmWithTransaction').closest('button')!);
    await user.click(screen.getByRole('button', { name: 'actions.cancel' }));

    expect(onConfirmPurchase).toHaveBeenCalledTimes(1);
    expect(onConfirmPurchaseWithTransaction).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
