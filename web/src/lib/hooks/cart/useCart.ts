"use client";

import { useCartContext } from "@/shared/providers/CartProvider";

/**
 * Hook to access cart state and operations
 * 
 * This is a convenience wrapper around useCartContext that provides
 * a cleaner API for components to interact with the cart.
 * 
 * @returns Cart context with items, counts, totals, and operations
 */
export function useCart() {
  return useCartContext();
}

