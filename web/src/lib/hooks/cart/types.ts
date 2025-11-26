/**
 * Cart item type - represents an item in the shopping cart
 */
export interface CartItem {
  id: string;
  catalogItemId: string;
  name: string;
  priceCents: number;
  quantity: number;
}

/**
 * Input type for adding items to the cart
 */
export interface CartItemInput {
  catalogItemId: string;
  name: string;
  priceCents: number;
  quantity?: number;
}

/**
 * Cart type - represents the entire shopping cart
 */
export type Cart = CartItem[];

