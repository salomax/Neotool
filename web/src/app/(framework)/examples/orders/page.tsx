"use client";

import React from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { useI18n } from '@/shared/i18n/hooks/useI18n';
import { ordersTranslations } from './i18n';

export default function OrdersPage() {
  const { t } = useI18n(ordersTranslations); // This will automatically register the 'orders' domain

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('title')}
        </Typography>
        <Button variant="contained">
          {t('addOrder')}
        </Button>
      </Box>
      
      <Box sx={{ p: 2, border: '1px dashed #ccc', borderRadius: 1 }}>
        <Typography variant="body1" color="text.secondary">
          This page demonstrates automatic domain registration. 
          The 'orders' domain translations were loaded automatically when you used useI18n('orders').
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          Try switching languages to see the translations in action!
        </Typography>
      </Box>
    </Container>
  );
}
