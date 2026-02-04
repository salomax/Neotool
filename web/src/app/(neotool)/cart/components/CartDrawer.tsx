"use client";

import * as React from "react";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useCart } from "@/shared/hooks/cart";
import type { CartItem } from "@/shared/hooks/cart/types";
import { Box, Typography, Button, Divider, IconButton, Stack } from "@mui/material";
import { DeleteIcon } from "@/shared/ui/mui-imports";

export interface CartDrawerProps {
  open: boolean;
  onClose: () => void;
  width?: number | string;
}

/**
 * CartDrawer component - Displays shopping cart in a drawer
 * Shows cart items, quantities, and totals with options to modify or checkout
 */
export const CartDrawer: React.FC<CartDrawerProps> = ({
  open,
  onClose,
  width,
}) => {
  const { items, itemCount, totalPriceCents, isEmpty, removeItem, clearCart } = useCart();

  const formatPrice = (cents: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(cents / 100);
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="right"
      size={width ? undefined : "sm"}
      width={width}
      sx={{
        "& .MuiDrawer-paper": {
          borderRadius: 0,
        },
      }}
    >
      <Drawer.Header title={`Shopping Cart (${itemCount})`} showCloseButton={true} />
      <Drawer.Body>
        {isEmpty ? (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              flex: 1,
              textAlign: "center",
            }}
          >
            <Typography variant="h6" color="text.secondary" gutterBottom>
              Your cart is empty
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Add items to your cart to see them here
            </Typography>
          </Box>
        ) : (
          <Stack spacing={2}>
            {items.map((item: CartItem) => (
              <Box key={item.id}>
                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                    gap: 2,
                  }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="subtitle1" fontWeight="medium">
                      {item.name}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Qty: {item.quantity}
                    </Typography>
                    <Typography variant="body1" fontWeight="medium" sx={{ mt: 0.5 }}>
                      {formatPrice(item.priceCents * item.quantity)}
                    </Typography>
                  </Box>
                  <IconButton
                    size="small"
                    onClick={() => removeItem(item.id)}
                    aria-label={`Remove ${item.name} from cart`}
                    color="error"
                  >
                    <DeleteIcon />
                  </IconButton>
                </Box>
                <Divider sx={{ mt: 2 }} />
              </Box>
            ))}
          </Stack>
        )}
      </Drawer.Body>
      {!isEmpty && (
        <Drawer.Footer>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 2,
            }}
          >
            <Typography variant="h6">Total:</Typography>
            <Typography variant="h6" fontWeight="bold">
              {formatPrice(totalPriceCents)}
            </Typography>
          </Box>
          <Stack spacing={1}>
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={() => {
                // TODO: Implement checkout
                console.log("Checkout clicked");
              }}
            >
              Checkout
            </Button>
            <Button
              variant="outlined"
              fullWidth
              onClick={clearCart}
              color="error"
            >
              Clear Cart
            </Button>
          </Stack>
        </Drawer.Footer>
      )}
    </Drawer>
  );
};

