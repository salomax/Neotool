import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCart } from '../useCart';
import { CartProvider } from '@/shared/providers/CartProvider';

describe('useCart', () => {
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
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    // Wait for initialization
    await waitFor(() => {
      expect(result.current.items).toEqual([]);
    });

    result.current.addItem({
      catalogItemId: 'item1',
      name: 'Test Item',
      priceCents: 1000,
      quantity: 1,
    });

    await waitFor(() => {
      expect(result.current.items).toHaveLength(1);
      expect(result.current.itemCount).toBe(1);
      expect(result.current.totalPriceCents).toBe(1000);
      expect(result.current.isEmpty).toBe(false);
    });
  });

  it('should allow removing items from cart', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    result.current.addItem({
      catalogItemId: 'item1',
      name: 'Test Item',
      priceCents: 1000,
      quantity: 1,
    });

    const itemId = result.current.items[0]?.id;
    if (itemId) {
      result.current.removeItem(itemId);
    }

    expect(result.current.items).toHaveLength(0);
    expect(result.current.isEmpty).toBe(true);
  });

  it('should allow updating item quantity', async () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    // Wait for initialization
    await waitFor(() => {
      expect(result.current.items).toEqual([]);
    });

    result.current.addItem({
      catalogItemId: 'item1',
      name: 'Test Item',
      priceCents: 1000,
      quantity: 1,
    });

    await waitFor(() => {
      expect(result.current.items).toHaveLength(1);
    });

    const itemId = result.current.items[0]?.id;
    if (itemId) {
      result.current.updateQuantity(itemId, 3);
    }

    await waitFor(() => {
      expect(result.current.items[0]?.quantity).toBe(3);
      expect(result.current.itemCount).toBe(3);
      expect(result.current.totalPriceCents).toBe(3000);
    });
  });

  it('should allow clearing cart', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <CartProvider>{children}</CartProvider>
    );

    const { result } = renderHook(() => useCart(), { wrapper });

    result.current.addItem({
      catalogItemId: 'item1',
      name: 'Test Item',
      priceCents: 1000,
      quantity: 1,
    });

    result.current.clearCart();

    expect(result.current.items).toHaveLength(0);
    expect(result.current.isEmpty).toBe(true);
  });
});

