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
  const { primaryGridItems, secondaryGridItems, loading } =
    useBacenInstitutionBalanceSummary(institutionId);

  if (!institutionId) return null;

  return (
    <Box>
      <MetricGrid
        items={primaryGridItems}
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
          items={secondaryGridItems}
          loading={loading}
          columns={{ xs: 1, sm: 2, md: 5 }}
          gap={2}
          skeletonHeights={{ metric: 140, percentage: 140 }}
        />
      </Box>
    </Box>
  );
}
