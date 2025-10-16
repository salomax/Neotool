"use client";

import React from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { useI18n } from '@/shared/i18n/hooks/useI18n';
import { inventoryTranslations } from './i18n';

export default function InventoryPage() {
  const { t } = useI18n(inventoryTranslations); // Auto-registers 'inventory' domain

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('title')}
        </Typography>
        <Button variant="contained">
          {t('addItem')}
        </Button>
      </Box>
      
      <Box sx={{ p: 2, border: '1px dashed #ccc', borderRadius: 1 }}>
        <Typography variant="body1" color="text.secondary">
          This page demonstrates the simplified i18n pattern. 
          Just import the translations and use them - no configuration needed!
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          Try switching languages to see the translations in action!
        </Typography>
      </Box>
    </Container>
  );
}
