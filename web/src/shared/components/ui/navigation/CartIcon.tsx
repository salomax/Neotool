"use client";

import * as React from "react";
import { IconButton, IconButtonProps } from "@/shared/components/ui/primitives";
import { Badge as MuiBadge } from "@mui/material";
import { ShoppingCartIcon } from "@/shared/ui/mui-imports";
import { useCart } from "@/shared/hooks/cart";

export interface CartIconProps extends Omit<IconButtonProps, "onClick"> {
  onClick?: () => void;
  "aria-label"?: string;
}

/**
 * Cart icon component with badge showing item count
 * 
 * Displays a shopping cart icon with a badge showing the number of items
 * in the cart. The badge is hidden when the cart is empty.
 */
export const CartIcon: React.FC<CartIconProps> = ({
  onClick,
  "aria-label": ariaLabel,
  ...props
}) => {
  const { itemCount } = useCart();

  return (
    <MuiBadge
      badgeContent={itemCount > 0 ? itemCount : undefined}
      color="primary"
      overlap="rectangular"
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
    >
      <IconButton
        onClick={onClick}
        aria-label={ariaLabel || "Shopping cart"}
        {...props}
      >
        <ShoppingCartIcon />
      </IconButton>
    </MuiBadge>
  );
};

export default CartIcon;

