"use client";

import * as React from "react";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useCart } from "@/lib/hooks/cart/useCart";
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
  width = 400,
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
      width={width}
      showCloseButton={true}
      title={`Shopping Cart (${itemCount})`}
      sx={{
        "& .MuiDrawer-paper": {
          borderRadius: 0,
        },
      }}
    >
      <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
        {isEmpty ? (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              flex: 1,
              p: 3,
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
          <>
            <Box sx={{ flex: 1, overflow: "auto", p: 2 }}>
              <Stack spacing={2}>
                {items.map((item) => (
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
            </Box>
            <Box sx={{ p: 2, borderTop: 1, borderColor: "divider" }}>
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
            </Box>
          </>
        )}
      </Box>
    </Drawer>
  );
};

