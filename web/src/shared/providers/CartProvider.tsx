"use client";

import * as React from "react";
import { CartItem, Cart, CartItemInput } from "@/lib/hooks/cart/types";

type CartContextType = {
  // Cart state
  items: CartItem[];
  itemCount: number;
  totalPriceCents: number;
  isEmpty: boolean;
  
  // Cart operations
  addItem: (item: CartItemInput) => void;
  removeItem: (itemId: string) => void;
  updateQuantity: (itemId: string, quantity: number) => void;
  clearCart: () => void;
};

const CartContext = React.createContext<CartContextType | null>(null);

export const useCartContext = (): CartContextType => {
  const ctx = React.useContext(CartContext);
  if (!ctx) {
    throw new Error("useCartContext must be used within CartProvider");
  }
  return ctx;
};

type CartProviderProps = {
  children: React.ReactNode;
};

const CART_STORAGE_KEY = "procureflow_cart";
const MAX_QUANTITY = 9999;
const MIN_QUANTITY = 1;

/**
 * Generate a unique ID for a cart item
 */
const generateCartItemId = (catalogItemId: string): string => {
  return `cart_item_${catalogItemId}`;
};

/**
 * Load cart from localStorage
 */
const loadCartFromStorage = (): CartItem[] => {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const stored = localStorage.getItem(CART_STORAGE_KEY);
    if (!stored) {
      return [];
    }
    const parsed = JSON.parse(stored);
    // Validate structure
    if (Array.isArray(parsed)) {
      return parsed.filter(
        (item) =>
          item &&
          typeof item === "object" &&
          typeof item.id === "string" &&
          typeof item.catalogItemId === "string" &&
          typeof item.name === "string" &&
          typeof item.priceCents === "number" &&
          typeof item.quantity === "number" &&
          item.quantity >= MIN_QUANTITY
      );
    }
    return [];
  } catch (e) {
    console.error("Error loading cart from localStorage:", e);
    // Clear invalid data
    try {
      localStorage.removeItem(CART_STORAGE_KEY);
    } catch (clearError) {
      console.error("Error clearing invalid cart data:", clearError);
    }
    return [];
  }
};

/**
 * Save cart to localStorage
 */
const saveCartToStorage = (items: CartItem[]): void => {
  if (typeof window === "undefined") {
    return;
  }

  try {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
  } catch (e) {
    // Handle quota exceeded or other storage errors
    if (e instanceof DOMException && e.name === "QuotaExceededError") {
      console.error("localStorage quota exceeded. Cart data not saved.");
    } else {
      console.error("Error saving cart to localStorage:", e);
    }
  }
};

/**
 * Calculate cart totals from items
 */
const calculateCartTotals = (items: CartItem[]): {
  itemCount: number;
  totalPriceCents: number;
  isEmpty: boolean;
} => {
  const itemCount = items.reduce((sum, item) => sum + item.quantity, 0);
  const totalPriceCents = items.reduce(
    (sum, item) => sum + item.priceCents * item.quantity,
    0
  );
  const isEmpty = items.length === 0;

  return { itemCount, totalPriceCents, isEmpty };
};

export const CartProvider: React.FC<CartProviderProps> = ({ children }) => {
  const [items, setItems] = React.useState<CartItem[]>([]);
  const [isInitialized, setIsInitialized] = React.useState(false);

  // Initialize cart from localStorage on mount
  React.useEffect(() => {
    const loadedItems = loadCartFromStorage();
    setItems(loadedItems);
    setIsInitialized(true);
  }, []);

  // Save to localStorage whenever items change (after initialization)
  React.useEffect(() => {
    if (isInitialized) {
      saveCartToStorage(items);
    }
  }, [items, isInitialized]);

  // Calculate derived values
  const { itemCount, totalPriceCents, isEmpty } = React.useMemo(
    () => calculateCartTotals(items),
    [items]
  );

  // Add item to cart (or increase quantity if already exists)
  const addItem = React.useCallback((input: CartItemInput) => {
    setItems((currentItems) => {
      const quantity = Math.max(MIN_QUANTITY, Math.min(MAX_QUANTITY, input.quantity || 1));
      const itemId = generateCartItemId(input.catalogItemId);

      // Check if item already exists
      const existingIndex = currentItems.findIndex((item) => item.id === itemId);

      if (existingIndex >= 0) {
        // Update quantity of existing item
        const updated = [...currentItems];
        const existingItem = updated[existingIndex];
        if (!existingItem) return currentItems;
        const newQuantity = Math.min(
          MAX_QUANTITY,
          existingItem.quantity + quantity
        );
        updated[existingIndex] = {
          ...existingItem,
          quantity: newQuantity,
        };
        return updated;
      } else {
        // Add new item
        if (!itemId || !input.catalogItemId || !input.name || input.priceCents === undefined) {
          return currentItems;
        }
        const newItem: CartItem = {
          id: itemId,
          catalogItemId: input.catalogItemId,
          name: input.name,
          priceCents: input.priceCents,
          quantity,
        };
        return [...currentItems, newItem];
      }
    });
  }, []);

  // Remove item from cart
  const removeItem = React.useCallback((itemId: string) => {
    setItems((currentItems) =>
      currentItems.filter((item) => item.id !== itemId)
    );
  }, []);

  // Update item quantity
  const updateQuantity = React.useCallback((itemId: string, quantity: number) => {
    const validatedQuantity = Math.max(MIN_QUANTITY, Math.min(MAX_QUANTITY, quantity));

    setItems((currentItems) => {
      const index = currentItems.findIndex((item) => item.id === itemId);
      if (index < 0) {
        return currentItems;
      }

      // If quantity is 0 or less, remove the item
      if (validatedQuantity < MIN_QUANTITY) {
        return currentItems.filter((item) => item.id !== itemId);
      }

      // Update quantity
      const updated = [...currentItems];
      const existingItem = currentItems[index];
      if (!existingItem) return currentItems;
      updated[index] = {
        ...existingItem,
        quantity: validatedQuantity,
      };
      return updated;
    });
  }, []);

  // Clear entire cart
  const clearCart = React.useCallback(() => {
    setItems([]);
  }, []);

  const value: CartContextType = {
    items,
    itemCount,
    totalPriceCents,
    isEmpty,
    addItem,
    removeItem,
    updateQuantity,
    clearCart,
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
};

