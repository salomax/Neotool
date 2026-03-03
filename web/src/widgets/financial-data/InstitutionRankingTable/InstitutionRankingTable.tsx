"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { 
  Box, 
  Table, 
  TableBody, 
  TableCell, 
  TableContainer, 
  TableHead, 
  TableRow, 
  Typography, 
  Avatar, 
  Stack, 
  Tooltip,
  Skeleton,
  Divider
} from "@mui/material";
import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import { 
  TopInstitutionMetric, 
  PopularInstitutionTopMetricItem 
} from "@/lib/graphql/types/__generated__/graphql";
import { createSemanticPath } from "@/shared/utils/url";
import { formatMetricValue } from "@/shared/utils/formatMetricValue";
import { useTranslation, i18n } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";

export interface InstitutionRankingTableProps {
  title: string;
  metric: TopInstitutionMetric;
  items: PopularInstitutionTopMetricItem[];
  loading?: boolean;
}

export function InstitutionRankingTable({ title, metric, items, loading }: InstitutionRankingTableProps) {
  const router = useRouter();
  const { t } = useTranslation(financialDataTranslations);

  const handleRowClick = (item: PopularInstitutionTopMetricItem) => {
    const institution = item.institution;
    router.push(`/financial-data/bacen-institutions/${createSemanticPath(institution.name, institution.id)}`);
  };

  const formatValue = (value: number | null | undefined) => {
    if (value == null) return "-";
    
    if (metric === TopInstitutionMetric.Profit) {
      const parts = formatMetricValue(value, "currency", { locale: i18n.language });
      return `${parts.prefix} ${parts.main}${parts.suffix}`;
    }
    // Percentages
    // For Basileia and Mobilization, value is likely 0.15 for 15%
    const parts = formatMetricValue(value, "percentage", { locale: i18n.language });
    return `${parts.main}${parts.suffix}`;
  };

  if (loading) {
     return (
        <Box sx={{ border: "1px solid", borderColor: "divider", borderRadius: 2, p: 2 }}>
            <Skeleton variant="text" width="60%" height={32} sx={{ mb: 2 }} />
            {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} variant="rectangular" height={40} sx={{ mb: 1, borderRadius: 1 }} />
            ))}
        </Box>
     );
  }

  return (
    <>
      <Box sx={{ px: 2, pb: 1, pt: 0 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block" }}>
          {title}
        </Typography>
      </Box>
      <Box sx={{ 
        border: "1px solid", 
        borderColor: "divider", 
        borderRadius: 2, 
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        bgcolor: "background.paper" // Match Card bg color
      }}>
        <TableContainer>
          <Table size="small" stickyHeader sx={{ paddingY: 1 }}>
            <TableBody>
              {items.map((item) => {
                const isRestricted = item.institution.dataRestriction?.isRestricted;
                
                return (
                  <TableRow 
                    key={item.rank} 
                    hover 
                    onClick={() => handleRowClick(item)}
                    sx={{ cursor: "pointer" }}
                  >
                    <TableCell>
                      <Stack direction="row" spacing={2} alignItems="center">
                        <Avatar 
                          src={item.institution.logoAsset?.publicUrl || undefined}
                          sx={{ 
                            width: 32, 
                            height: 32, 
                            fontSize: 14,
                            borderRadius: 1,
                            border: "1px solid",
                            borderColor: "divider",
                            bgcolor: "background.default"
                          }}
                        >
                          {(item.institution.tradeName || item.institution.name).charAt(0)}
                        </Avatar>
                        <Typography variant="body1" noWrap sx={{ maxWidth: 160, fontWeight: 400, }}>
                          {item.institution.tradeName || item.institution.name}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, color: "text.primary" }}>
                      {isRestricted ? (
                        <Tooltip title={item.institution.dataRestriction?.reason || t("messages.dataRestricted")}>
                          <LockOutlinedIcon sx={{ fontSize: 16, color: "text.disabled", verticalAlign: "middle" }} />
                        </Tooltip>
                      ) : (
                        formatValue(item.metricValue)
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}              
              {!items.length && (
                <TableRow>
                  <TableCell colSpan={3} align="center" sx={{ py: 4, color: "text.secondary" }}>
                    {t("messages.noDataAvailable")}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    </>
  );
}
