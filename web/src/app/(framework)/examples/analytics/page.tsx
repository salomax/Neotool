"use client";

import React from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { useTranslation } from '@/shared/i18n';
import { analyticsTranslations } from './i18n';

export default function AnalyticsPage() {
  // TypeScript will enforce that analyticsTranslations follows DomainTranslations contract
  const { t } = useTranslation(analyticsTranslations);

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('title')}
        </Typography>
        <Button variant="contained">
          {t('refresh')}
        </Button>
      </Box>
      
      <Box sx={{ p: 2, border: '1px dashed #ccc', borderRadius: 1 }}>
        <Typography variant="body1" color="text.secondary">
          This page demonstrates TypeScript type safety for i18n translations.
          The analyticsTranslations object must follow the DomainTranslations contract.
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          Try switching languages to see the translations in action!
        </Typography>
      </Box>
    </Container>
  );
}
