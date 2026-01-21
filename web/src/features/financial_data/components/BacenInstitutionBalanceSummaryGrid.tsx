"use client";

import { useState } from "react";
import { Box } from "@mui/material";
import { useTranslation } from "react-i18next";

import { MetricGrid, MetricGridItem, MetricDetailsDrawer } from "@/shared/components/ui/data-display";
import { useBacenInstitutionBalanceSummary } from "@/features/financial_data/hooks/useBacenInstitutionBalanceSummary";
import { useChartPeriodFilter } from "@/shared/hooks/charts";

interface BacenInstitutionBalanceSummaryGridProps {
  institutionId: string;
}

export function BacenInstitutionBalanceSummaryGrid({
  institutionId,
}: BacenInstitutionBalanceSummaryGridProps) {
  const { t } = useTranslation("common");
  const [selectedMetric, setSelectedMetric] = useState<MetricGridItem | null>(null);
  const [period, setPeriod] = useState<string>("5Y");
  const { gridItems, loading } =
    useBacenInstitutionBalanceSummary(institutionId);

  // Use the shared hook for period filtering
  const { filteredData: chartData, periodGrowth } = useChartPeriodFilter(
    selectedMetric?.chartData,
    period
  );

  if (!institutionId) return null;

  // Split items:
  // First line: Items 0-2 (Net Profit, Basel Index, Fixed Assets Index)
  // Remaining lines: All other items (Total Assets, Funding, etc.) - Dynamic grid
  const firstLineItems = gridItems.slice(0, 3).map(item => ({
    ...item,
    onOpen: () => setSelectedMetric(item)
  }));
  const remainingItems = gridItems.slice(3).map(item => ({
    ...item,
    onOpen: () => setSelectedMetric(item)
  }));

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
          loading={loading}
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
        metric={selectedMetric}
        growth={periodGrowth}
        chartData={chartData}
        period={period}
        onPeriodChange={setPeriod}
        periodOptions={[
          { value: "1Y", label: t("periods.1y", "1Y") },
          { value: "3Y", label: t("periods.3y", "3Y") },
          { value: "5Y", label: t("periods.5y", "5Y") },
        ]}
      />
    </Box>
  );
}
