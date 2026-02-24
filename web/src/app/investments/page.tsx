"use client";

import React from "react";
import { Container, Page } from "@/shared/components/ui/layout";
import { ModuleCard } from "@/shared/components/ui/navigation/ModuleCard";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { FixedIncomeComparisonIcon } from "./components/icons";
import { Box, Typography, Grid } from "@mui/material";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";

export default function InvestmentsPage() {
  const { t } = useTranslation(investmentsTranslations);

  usePageTitle(t("routes.investments"));

  return (
    <Page>
      <Container maxWidth="xl" sx={{ py: 8 }}>
        <Box sx={{ textAlign: "center", mb: 8, maxWidth: 800, mx: "auto" }}>
          <Typography
            variant="h3"
            component="h1"
            gutterBottom
            fontWeight="bold"
            sx={{ color: "text.primary" }}
          >
            {t("title")}
          </Typography>
          <Typography variant="h6" color="text.secondary" fontWeight="normal">
            {t("subtitle")}
          </Typography>
        </Box>

        <Grid container spacing={4} justifyContent="center">
          <Grid size={{ xs: 12, md: 6, lg: 5 }}>
            <ModuleCard
              title={t("cards.fixedIncomeComparison.title")}
              description={t("cards.fixedIncomeComparison.description")}
              icon={<FixedIncomeComparisonIcon size="sm" />}
              href="/investments/fixed-income-comparison"
              actionLabel={t("actions.accessModule")}
            />
          </Grid>
        </Grid>
      </Container>
    </Page>
  );
}
