"use client";

import React from "react";
import { Container, Page } from "@/shared/components/ui/layout";
import { Grid } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { FixedIncomeSimulatorForm } from "@/features/investments/fixed-income-comparison/components/FixedIncomeSimulatorForm";
import { FixedIncomeSummaryCard } from "@/features/investments/fixed-income-comparison/components/FixedIncomeSummaryCard";
import { FixedIncomeComparisonTable } from "@/features/investments/fixed-income-comparison/components/FixedIncomeComparisonTable";
import { FixedIncomePerformanceChart } from "@/features/investments/fixed-income-comparison/components/FixedIncomePerformanceChart";
import { FixedIncomeMethodologyCard } from "@/features/investments/fixed-income-comparison/components/FixedIncomeMethodologyCard";
import { FixedIncomeComparisonEmptyState } from "@/features/investments/fixed-income-comparison/components/FixedIncomeComparisonEmptyState";
import type { FixedIncomeComparisonQuery } from "@/features/investments/fixed-income-comparison/api/queries.generated";

/**
 * Fixed Income Comparison page. Future (T4.5.1): when navigating from a specific
 * holding context (e.g. portfolio table row), pre-fill simulation fields from that holding.
 */
export default function FixedIncomeComparisonPageClient() {
  const { t } = useTranslation(investmentsTranslations);

  const [comparisonResult, setComparisonResult] =
    React.useState<FixedIncomeComparisonQuery["fixedIncomeComparison"] | null>(
      null
    );
  const [comparisonLoading, setComparisonLoading] = React.useState(false);

  usePageTitle(t("cards.fixedIncomeComparison.title"));
  useBreadcrumbLabel(
    "fixed-income-comparison",
    t("cards.fixedIncomeComparison.title")
  );

  const hasResult = !!comparisonResult;

  return (
    <Page component="main">
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Grid
          container
          spacing={4}
          sx={{
            flexWrap: { lg: "nowrap" },
            alignItems: "flex-start",
          }}
        >
          <Grid size={{ xs: 12, lg: 3 }} sx={{ flexShrink: 0 }}>
            <FixedIncomeSimulatorForm
              onComparisonResult={setComparisonResult}
              onComparisonLoadingChange={setComparisonLoading}
            />
          </Grid>
          <Grid
            size={{ xs: 12, lg: 9 }}
            sx={{
              flexGrow: 1,
              minWidth: 0,
            }}
          >
            {comparisonLoading || hasResult ? (
              <>
                <FixedIncomeSummaryCard
                  result={comparisonResult}
                  loading={comparisonLoading}
                />
                <FixedIncomeComparisonTable
                  result={comparisonResult}
                  loading={comparisonLoading}
                />
                <FixedIncomePerformanceChart
                  result={comparisonResult}
                  loading={comparisonLoading}
                />
                <FixedIncomeMethodologyCard
                  result={comparisonResult}
                  loading={comparisonLoading}
                />
              </>
            ) : (
              <FixedIncomeComparisonEmptyState />
            )}
          </Grid>
        </Grid>
      </Container>
    </Page>
  );
}
