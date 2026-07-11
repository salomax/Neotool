'use client';

import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { PurchaseWishlistForm } from '../PurchaseWishlistForm';
import { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';
import type { WishlistItemRow } from '../wishlistRowModel';

const mockFetchPreview = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(() => ({ data: { transactionCategories: [] } })),
  useLazyQuery: vi.fn(() => [mockFetchPreview, { loading: false }]),
  useMutation: vi.fn(() => [vi.fn(), { loading: false, error: null }]),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  function Drawer({ open, children }: { open: boolean; children: React.ReactNode }) {
    return open ? <div>{children}</div> : null;
  }
  Drawer.displayName = 'DrawerMock';

  function DrawerHeader({ title }: { title: React.ReactNode }) {
    return <h2>{title}</h2>;
  }
  DrawerHeader.displayName = 'DrawerHeaderMock';

  function DrawerBody({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerBody.displayName = 'DrawerBodyMock';

  function DrawerFooter({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }
  DrawerFooter.displayName = 'DrawerFooterMock';

  function DrawerActionButtons({
    cancelText,
    submitText,
    onCancel,
    onSubmit,
  }: {
    cancelText: string;
    submitText: string;
    onCancel: () => void;
    onSubmit: () => void;
  }) {
    return (
      <div>
        <button type="button" onClick={onCancel}>
          {cancelText}
        </button>
        <button type="button" onClick={onSubmit}>
          {submitText}
        </button>
      </div>
    );
  }
  DrawerActionButtons.displayName = 'DrawerActionButtonsMock';

  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  Drawer.Footer = DrawerFooter;
  Drawer.ActionButtons = DrawerActionButtons;

  return { Drawer };
});

vi.mock('@/shared/components/ui/forms/form/MoneyInput', () => ({
  BaseMoneyInput: ({
    label,
    value,
    onChange,
  }: {
    label: string;
    value?: number;
    onChange: (value: number | undefined) => void;
  }) => (
    <label>
      {label}
      <input
        aria-label={label}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value ? Number(event.target.value) : undefined)}
      />
    </label>
  ),
}));

vi.mock('@/shared/components/ui/forms/form', () => ({
  APP_DATE_PICKER_VIEWS: ['day'],
  appDatePickerDisplayFormatFromLanguageTag: () => 'DD/MM/YYYY',
  mergeAppDatePickerSlotProps: (value: unknown) => value,
}));

vi.mock('@/shared/ui/mui-imports', () => ({
  DatePicker: ({ slotProps }: { slotProps?: { textField?: { label?: string } } }) => (
    <input aria-label={slotProps?.textField?.label ?? 'date'} />
  ),
}));

vi.mock('../../finance-transactions/category/CategorySelect', () => ({
  CategorySelect: () => <div data-testid="category-select" />,
}));

const theme = createTheme();

function renderForm(
  overrides: Partial<React.ComponentProps<typeof PurchaseWishlistForm>> = {},
) {
  const onClose = vi.fn();
  const onSubmit = vi.fn();

  render(
    <ThemeProvider theme={theme}>
      <PurchaseWishlistForm open onClose={onClose} onSubmit={onSubmit} {...overrides} />
    </ThemeProvider>,
  );

  return { onClose, onSubmit };
}

describe.sequential('PurchaseWishlistForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('autofills empty name and estimated price from the debounced product preview', async () => {
    const user = userEvent.setup();
    mockFetchPreview.mockResolvedValue({
      data: {
        wishlistProductMetadataPreview: {
          name: 'Nintendo Switch 2',
          estimatedPrice: 3999.9,
          imageUrl: 'https://cdn.example.com/switch-2.png',
        },
      },
    });

    renderForm();

    await user.type(screen.getByLabelText('purchaseWishlist.form.productUrl'), 'https://shop.example.com/switch-2');

    await waitFor(
      () =>
        expect(mockFetchPreview).toHaveBeenCalledWith({
          variables: { url: 'https://shop.example.com/switch-2' },
        }),
      { timeout: 2_000 },
    );
    await waitFor(
      () =>
        expect(screen.getByRole('textbox', { name: /purchaseWishlist\.form\.name/ })).toHaveValue('Nintendo Switch 2'),
      { timeout: 2_000 },
    );
    await waitFor(
      () => expect(screen.getByLabelText('purchaseWishlist.form.estimatedPrice')).toHaveValue('3999.9'),
      { timeout: 2_000 },
    );
    expect(screen.getByRole('img')).toHaveAttribute('src', 'https://cdn.example.com/switch-2.png');
  });

  it('does not overwrite manually edited values when preview data arrives', async () => {
    const user = userEvent.setup();
    mockFetchPreview.mockResolvedValue({
      data: {
        wishlistProductMetadataPreview: {
          name: 'Preview name',
          estimatedPrice: 499.5,
          imageUrl: 'https://cdn.example.com/manual.png',
        },
      },
    });

    renderForm({
      item: {
        id: 'item-1',
        name: 'Manual name',
        estimatedPrice: 123.45,
        category: null,
        categoryId: null,
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
        currencyCode: 'BRL',
      } as WishlistItemRow,
    });

    await user.clear(screen.getByLabelText('purchaseWishlist.form.productUrl'));
    await user.type(screen.getByLabelText('purchaseWishlist.form.productUrl'), 'https://shop.example.com/manual');

    await waitFor(() => expect(mockFetchPreview).toHaveBeenCalled(), { timeout: 2_000 });
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'https://cdn.example.com/manual.png'), {
      timeout: 2_000,
    });
    expect(screen.getByRole('textbox', { name: /purchaseWishlist\.form\.name/ })).toHaveValue('Manual name');
    expect(screen.getByLabelText('purchaseWishlist.form.estimatedPrice')).toHaveValue('123.45');
  });
});
