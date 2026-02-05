"use client";

import { useState } from "react";
import { Box } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useQuery } from "@apollo/client/react";

import { MetricGrid, MetricGridItem, MetricDetailsDrawer, MetricCardProps } from "@/shared/components/ui/data-display";
import { useBacenInstitutionBalanceSummary } from "@/features/financial-data/view-balance-summary";
import { useChartPeriodFilter } from "@/shared/hooks/charts";
import { BACEN_INSTITUTION } from "@/features/financial-data/view-institution-detail/api";

interface BacenInstitutionBalanceSummaryGridProps {
  institutionId: string;
}

type MetricItem = MetricCardProps & { type?: 'metric'; chartData?: any[] };

export function BacenInstitutionBalanceSummaryGrid({
  institutionId,
}: BacenInstitutionBalanceSummaryGridProps) {
  const { t, i18n } = useTranslation("common");
  const [selectedMetric, setSelectedMetric] = useState<MetricItem | null>(null);
  const [period, setPeriod] = useState<string>("5Y");
  const { gridItems, loading } =
    useBacenInstitutionBalanceSummary(institutionId);

  const { data: institutionData, loading: loadingInstitution } = useQuery<any>(BACEN_INSTITUTION, {
    variables: { id: institutionId },
    skip: !institutionId,
  });

  const ratings = institutionData?.bacenFinanceInstitution?.ratings || [];

  // Use the shared hook for period filtering
  const { filteredData: chartData, periodGrowth } = useChartPeriodFilter(
    selectedMetric?.chartData,
    period,
    {
      valueType: selectedMetric?.valueType,
      disablePercentageScaling: selectedMetric?.disablePercentageScaling,
    }
  );

  if (!institutionId) return null;

  // Split items:
  // First line: Items 0-2 (Net Profit, Basel Index, Fixed Assets Index)
  // Remaining lines: All other items (Total Assets, Funding, etc.) - Dynamic grid
  const firstLineItems: MetricGridItem[] = gridItems.slice(0, 3).map(item => ({
    ...item,
    onOpen: () => setSelectedMetric(item as MetricItem)
  }));
  const remainingItems: MetricGridItem[] = gridItems.slice(3).map(item => ({
    ...item,
    onOpen: () => setSelectedMetric(item as MetricItem)
  }));

  if (ratings.length > 0 || loadingInstitution) {
    remainingItems.unshift({
      type: 'rating',
      ratings: ratings,
      loading: loadingInstitution
    } as MetricGridItem);
  }

  return (
    <Box>
      <MetricGrid
        items={firstLineItems}
        loading={loading}
        templateColumns={{
          xs: "repeat(1, 1fr)",
          sm: "repeat(1, 1fr)",
          md: "4fr 3fr 3fr",
          lg: "4fr 3fr 3fr",
        }}
        gap={2}
        skeletonHeights={{ metric: 140, percentage: 140 }}
      />
      <Box sx={{ mt: 2 }}>
        <MetricGrid
          items={remainingItems}
          loading={loading && !loadingInstitution} // Only show grid loading if main metrics are loading
          templateColumns={{
            xs: "1fr",
            sm: "repeat(auto-fill, minmax(240px, 1fr))",
          }}
          gap={2}
          skeletonHeights={{ metric: 140, percentage: 140 }}
        />
      </Box>

      <MetricDetailsDrawer
        open={!!selectedMetric}
        onClose={() => setSelectedMetric(null)}
        title={selectedMetric?.label || t("actions.details", "Details")}
        metric={selectedMetric ? { ...selectedMetric, locale: i18n.language } : null}
        growth={periodGrowth}
        chartData={chartData}
        period={period}
        onPeriodChange={setPeriod}
        periodOptions={[
          { value: "1Y", label: t("periods.1y", "1Y") },
          { value: "3Y", label: t("periods.3y", "3Y") },
          { value: "5Y", label: t("periods.5y", "5Y") },
        ]}
        historyTablePeriodLabel={t("calendar.quarter", "Quarter")}
      />
    </Box>
  );
}
