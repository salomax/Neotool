import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useCart } from '../useCart';
import { CartProvider } from '@/shared/providers/CartProvider';

describe.sequential('useCart', () => {
  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();
  });

  it('should return cart context when used within CartProvider', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    expect(result.current).toBeDefined();
    expect(result.current.items).toBeDefined();
    expect(result.current.itemCount).toBeDefined();
    expect(result.current.totalPriceCents).toBeDefined();
    expect(result.current.isEmpty).toBeDefined();
    expect(result.current.addItem).toBeDefined();
    expect(result.current.removeItem).toBeDefined();
    expect(result.current.updateQuantity).toBeDefined();
    expect(result.current.clearCart).toBeDefined();
  });

  it('should return empty cart initially', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    expect(result.current.items).toEqual([]);
    expect(result.current.itemCount).toBe(0);
    expect(result.current.totalPriceCents).toBe(0);
    expect(result.current.isEmpty).toBe(true);
  });

  it('should allow adding items to cart', async () => {
    const user = userEvent.setup();

    const Consumer = () => {
      const cart = useCart();
      return (
        <div>
          <div data-testid="items-length">{cart.items.length}</div>
          <div data-testid="item-count">{cart.itemCount}</div>
          <div data-testid="total">{cart.totalPriceCents}</div>
          <div data-testid="is-empty">{String(cart.isEmpty)}</div>
          <button
            data-testid="add-item"
            onClick={() =>
              cart.addItem({
                catalogItemId: 'item1',
                name: 'Test Item',
                priceCents: 1000,
                quantity: 1,
              })
            }
          >
            add
          </button>
        </div>
      );
    };

    let renderResult: ReturnType<typeof render>;
    await act(async () => {
      renderResult = render(
        <CartProvider>
          <Consumer />
        </CartProvider>
      );
    });
    const { getByTestId, findByTestId } = renderResult!;

    await findByTestId('items-length');

    await user.click(getByTestId('add-item'));

    await waitFor(() => {
      expect(getByTestId('items-length').textContent).toBe('1');
      expect(getByTestId('item-count').textContent).toBe('1');
      expect(getByTestId('total').textContent).toBe('1000');
      expect(getByTestId('is-empty').textContent).toBe('false');
    });
  });

  it('should allow removing items from cart', async () => {
    const user = userEvent.setup();

    const Consumer = () => {
      const cart = useCart();
      const itemId = cart.items[0]?.id;
      return (
        <div>
          <div data-testid="items-length">{cart.items.length}</div>
          <div data-testid="is-empty">{String(cart.isEmpty)}</div>
          <button
            data-testid="add-remove"
            onClick={() =>
              cart.addItem({
                catalogItemId: 'item1',
                name: 'Test Item',
                priceCents: 1000,
                quantity: 1,
              })
            }
          >
            add
          </button>
          <button
            data-testid="remove"
            onClick={() => {
              if (itemId) {
                cart.removeItem(itemId);
              }
            }}
          >
            remove
          </button>
        </div>
      );
    };

    let renderResult: ReturnType<typeof render>;
    await act(async () => {
      renderResult = render(
        <CartProvider>
          <Consumer />
        </CartProvider>
      );
    });
    const { getByTestId } = renderResult!;

    await user.click(getByTestId('add-remove'));
    await user.click(getByTestId('remove'));

    expect(getByTestId('items-length').textContent).toBe('0');
    expect(getByTestId('is-empty').textContent).toBe('true');
  });

  it('should allow updating item quantity', async () => {
    const user = userEvent.setup();

    const Consumer = () => {
      const cart = useCart();
      const itemId = cart.items[0]?.id;
      const handleUpdate = () => {
        if (itemId) {
          cart.updateQuantity(itemId, 3);
        }
      };
      return (
        <div>
          <div data-testid="items-length">{cart.items.length}</div>
          <div data-testid="quantity">{cart.items[0]?.quantity ?? 0}</div>
          <div data-testid="item-count">{cart.itemCount}</div>
          <div data-testid="total">{cart.totalPriceCents}</div>
          <button
            data-testid="add-update"
            onClick={() =>
              cart.addItem({
                catalogItemId: 'item1',
                name: 'Test Item',
                priceCents: 1000,
                quantity: 1,
              })
            }
          >
            add
          </button>
          <button data-testid="update" onClick={handleUpdate}>
            update
          </button>
        </div>
      );
    };

    let renderResult: ReturnType<typeof render>;
    await act(async () => {
      renderResult = render(
        <CartProvider>
          <Consumer />
        </CartProvider>
      );
    });
    const { getByTestId } = renderResult!;

    await user.click(getByTestId('add-update'));
    await user.click(getByTestId('update'));

    await waitFor(() => {
      expect(getByTestId('items-length').textContent).toBe('1');
      expect(getByTestId('quantity').textContent).toBe('3');
      expect(getByTestId('item-count').textContent).toBe('3');
      expect(getByTestId('total').textContent).toBe('3000');
    });
  });

  it('should allow clearing cart', async () => {
    const user = userEvent.setup();

    const Consumer = () => {
      const cart = useCart();
      return (
        <div>
          <div data-testid="items-length">{cart.items.length}</div>
          <div data-testid="is-empty">{String(cart.isEmpty)}</div>
          <button
            data-testid="add-clear"
            onClick={() =>
              cart.addItem({
                catalogItemId: 'item1',
                name: 'Test Item',
                priceCents: 1000,
                quantity: 1,
              })
            }
          >
            add
          </button>
          <button data-testid="clear" onClick={() => cart.clearCart()}>
            clear
          </button>
        </div>
      );
    };

    let renderResult: ReturnType<typeof render>;
    await act(async () => {
      renderResult = render(
        <CartProvider>
          <Consumer />
        </CartProvider>
      );
    });
    const { getByTestId } = renderResult!;

    await user.click(getByTestId('add-clear'));
    await user.click(getByTestId('clear'));

    expect(getByTestId('items-length').textContent).toBe('0');
    expect(getByTestId('is-empty').textContent).toBe('true');
  });
});
