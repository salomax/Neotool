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

  // Split items:
  // First line: Items 0-2 (Net Profit, Basel Index, Fixed Assets Index)
  // Remaining lines: All other items (Total Assets, Funding, etc.) - Dynamic grid
  const firstLineItems = gridItems.slice(0, 3);
  const remainingItems = gridItems.slice(3);

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
    </Box>
  );
}
