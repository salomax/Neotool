import { Box } from "@mui/material";

import { MetricGrid } from "@/shared/components/ui/data-display";
import { useBacenInstitutionBalanceSummary } from "@/features/financial_data/hooks/useBacenInstitutionBalanceSummary";

// --- Types & Interfaces ---

interface BacenInstitutionBalanceSummaryGridProps {
  institutionId: string;
}

export function BacenInstitutionBalanceSummaryGrid({
  institutionId,
}: BacenInstitutionBalanceSummaryGridProps) {
  const { gridItems, loading } =
    useBacenInstitutionBalanceSummary(institutionId);

  if (!institutionId) return null;

  // Split items into three lines:
  // First line: Items 0-2 (Net Profit, Basel Index, Fixed Assets Index)
  // Second line: Items 3-7 (Total Assets, Funding, Credit Portfolio, Net Equity, Reference Equity)
  // Third line: Items 8-11 (Classified Credit Portfolio, Current and Long-term Liabilities, Liabilities, Securities)
  const firstLineItems = gridItems.slice(0, 3);
  const secondLineItems = gridItems.slice(3, 8);
  const thirdLineItems = gridItems.slice(8, 12);

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
          items={secondLineItems}
          loading={loading}
          columns={{ xs: 1, sm: 2, md: 5 }}
          gap={2}
          skeletonHeights={{ metric: 140, percentage: 140 }}
        />
      </Box>
      <Box sx={{ mt: 2 }}>
        <MetricGrid
          items={thirdLineItems}
          loading={loading}
          columns={{ xs: 1, sm: 2, md: 4 }}
          gap={2}
          skeletonHeights={{ metric: 140, percentage: 140 }}
        />
      </Box>
    </Box>
  );
}
