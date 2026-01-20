import { useState, useMemo } from "react";
import { Box, Typography, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useTranslation } from "react-i18next";

import { MetricGrid, MetricGridItem, MetricValueDisplay, MetricLineChart, MetricHistoryTable, GrowthChip } from "@/shared/components/ui/data-display";
import { SegmentedControl } from "@/shared/components/ui/inputs";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useBacenInstitutionBalanceSummary } from "@/features/financial_data/hooks/useBacenInstitutionBalanceSummary";

// --- Types & Interfaces ---

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

  const { chartData, periodGrowth } = useMemo(() => {
    if (!selectedMetric?.chartData || selectedMetric.chartData.length === 0) {
      return { chartData: [], periodGrowth: null };
    }

    const allData = selectedMetric.chartData; // Oldest -> Newest
    let quarters = 20;
    if (period === "1Y") quarters = 4;
    if (period === "3Y") quarters = 12;
    if (period === "5Y") quarters = 20;

    // Filter chart data
    const startIndex = Math.max(0, allData.length - quarters);
    const filteredChartData = allData.slice(startIndex);

    // Calculate growth
    const currentItem = allData[allData.length - 1];
    const comparisonIndex = allData.length - 1 - quarters;
    const comparisonItem = allData[comparisonIndex];

    let growth: number | null = null;
    if (
      currentItem?.value != null &&
      comparisonItem?.value != null &&
      comparisonItem.value !== 0
    ) {
      growth =
        (currentItem.value - comparisonItem.value) / Math.abs(comparisonItem.value);
    }

    return { chartData: filteredChartData, periodGrowth: growth };
  }, [selectedMetric, period]);

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

      <Drawer
        open={!!selectedMetric}
        onClose={() => setSelectedMetric(null)}
        size="lg"
        anchor="right"
      >
        <Drawer.Header>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="h6" sx={{ textTransform: 'uppercase', fontWeight: 700 }}>
              {selectedMetric?.label || "Details"}
            </Typography>
          </Box>
          <IconButton onClick={() => setSelectedMetric(null)}>
            <CloseIcon />
          </IconButton>
        </Drawer.Header>
        <Drawer.Body>
          <Box sx={{ p: 2, pt: 0, display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', width: '100%' }}>
              <Box>
                {selectedMetric && (
                  <MetricValueDisplay
                    actualValue={selectedMetric.actualValue}
                    valueType={selectedMetric.valueType}
                    currency={selectedMetric.currency}
                    locale={selectedMetric.locale}
                    totalizerFormat={selectedMetric.totalizerFormat}
                    currencyLabels={selectedMetric.currencyLabels}
                    align="left"
                  />
                )}
              </Box>
              <Box>
                {periodGrowth != null && (
                  <GrowthChip growthPercentage={periodGrowth} />
                )}
              </Box>
            </Box>
            {selectedMetric && chartData && chartData.length > 0 && (
              <Box sx={{ width: '100%', mt: 4, display: 'flex', flexDirection: 'column' }}>
                <Box sx={{ mb: 2 }}>
                  <SegmentedControl
                    value={period}
                    onChange={setPeriod}
                    options={[
                      { value: "1Y", label: t("periods.1y", "1Y") },
                      { value: "3Y", label: t("periods.3y", "3Y") },
                      { value: "5Y", label: t("periods.5y", "5Y") },
                    ]}
                    fullWidth
                  />
                </Box>
                <MetricLineChart
                  data={chartData}
                  label={selectedMetric.label}
                  valueType={selectedMetric.valueType}
                  showLegend={false}
                />
                <MetricHistoryTable
                  data={chartData}
                  valueType={selectedMetric.valueType}
                  currency={selectedMetric.currency}
                />
              </Box>
            )}
          </Box>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
}
