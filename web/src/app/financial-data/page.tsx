"use client";

import React from "react";
import { Container, Page } from "@/shared/components/ui/layout";
import { ModuleCard } from "@/shared/components/ui/navigation/ModuleCard";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { FinancialInstitutionsIcon, NationalIndicatorsIcon } from "./components/icons";
import { Box, Typography, Grid } from "@mui/material";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import HistoryIcon from '@mui/icons-material/History';

export default function FinancialDataPage() {
  const { t } = useTranslation(financialDataTranslations);
  
  usePageTitle(t("routes.financialData"));

  return (
    <Page>
      <Container
        maxWidth="xl"
        sx={{
          py: 8,
          minWidth: 0, // Keep module cards and headings from pushing the public grid wider than the viewport.
          overflowX: "hidden",
        }}
      >
        <Box sx={{ textAlign: "center", mb: 8, maxWidth: 800, mx: "auto" }}>
          <Typography variant="h3" component="h1" gutterBottom fontWeight="bold" sx={{ color: "text.primary" }}>
            {t("title")}
          </Typography>
          <Typography variant="h6" color="text.secondary" fontWeight="normal">
            {t("subtitle")}
          </Typography>
        </Box>

        <Grid container spacing={4} justifyContent="center">
          <Grid size={{ xs: 12, md: 6, lg: 5 }}>
            <ModuleCard
              title={t("cards.financialInstitutions.title")}
              description={t("cards.financialInstitutions.description")}
              icon={<FinancialInstitutionsIcon size="sm" />}
              href="/financial-data/bacen-institutions"
              actionLabel={t("actions.accessModule")}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6, lg: 5 }}>
            <ModuleCard
              title={t("cards.nationalIndicators.title")}
              description={t("cards.nationalIndicators.description")}
              icon={<NationalIndicatorsIcon size="sm" />}
              href="/financial-data/national-indicators"
              actionLabel={t("actions.accessModule")}
            />
          </Grid>
        </Grid>

        <Box sx={{ mt: 8, textAlign: "center" }}>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
            <Box component="span" sx={{ fontSize: 16 }}><HistoryIcon /></Box>
            {t("footer.source")}
          </Typography>
        </Box>
      </Container>
    </Page>
  );
}
