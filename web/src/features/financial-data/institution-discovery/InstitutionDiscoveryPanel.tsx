"use client";

import * as React from "react";
import { Box, Grid, Typography, Divider } from "@mui/material";
import { usePopularInstitutions } from "./hooks/usePopularInstitutions";
import { 
  PopularInstitutionsCarousel, 
  InstitutionRankingTable 
} from "@/widgets/financial-data";
import { TopInstitutionMetric } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";

export function InstitutionDiscoveryPanel() {
  const { popularInstitutions, rankings, loading, quarterInfo } = usePopularInstitutions();
  const { t } = useTranslation(financialDataTranslations);
  
  // Manage visibility state for fade-in effect
  const [visible, setVisible] = React.useState(false);

  React.useEffect(() => {
    // Show immediately if loading, or if we have data
    if (loading || popularInstitutions.length > 0 || Object.values(rankings).some(r => r.length > 0)) {
        setVisible(true);
    }
  }, [loading, popularInstitutions, rankings]);

  const quarterMessage = React.useMemo(() => {
    if (!quarterInfo) return null;
    
    const { month, year } = quarterInfo;
    // Map month (3, 6, 9, 12) to quarter (1, 2, 3, 4)
    const quarterMap: Record<number, string> = {
      3: "1",
      6: "2",
      9: "3",
      12: "4",
    };

    const quarterIndex = quarterMap[month];
    if (!quarterIndex) return null;

    return t("lastBalanceSummary.lastUpdateMessage", {
      quarter: t(`lastBalanceSummary.quarters.${quarterIndex}`),
      year: year.toString(),
    });
  }, [quarterInfo, t]);

  if (!loading && !popularInstitutions.length) {
    return null;
  }

  return (
    <Box sx={{ 
      opacity: visible ? 1 : 0, 
      transition: "opacity 0.4s ease-in-out",
      display: "flex", 
      flexDirection: "column", 
      gap: 2,
      pb: 2
    }}>
      <PopularInstitutionsCarousel items={popularInstitutions} loading={loading} />
      
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
           <InstitutionRankingTable 
             title={t("discovery.rankings.netProfit")}
             metric={TopInstitutionMetric.Profit} 
             items={rankings[TopInstitutionMetric.Profit]} 
             loading={loading}
           />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
           <InstitutionRankingTable 
             title={t("discovery.rankings.basileiaIndex")}
             metric={TopInstitutionMetric.BasileiaIndex} 
             items={rankings[TopInstitutionMetric.BasileiaIndex]} 
             loading={loading}
           />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
           <InstitutionRankingTable 
             title={t("discovery.rankings.mobilizationIndex")}
             metric={TopInstitutionMetric.MobilizationIndex} 
             items={rankings[TopInstitutionMetric.MobilizationIndex]} 
             loading={loading}
           />
        </Grid>
      </Grid>
      
      {/* Footer */}
      {!loading && (
        <Box sx={{ mt: 2, mb: 2 }}>
           <Divider sx={{ mb: 2 }} />
           {quarterMessage && (
             <Typography variant="caption" color="text.secondary" display="block">
               {quarterMessage}
             </Typography>
           )}
           <Typography variant="caption" color="text.secondary" display="block">
             {t("footer.ifDataSource")}
           </Typography>
        </Box>
      )}
    </Box>
  );
}
