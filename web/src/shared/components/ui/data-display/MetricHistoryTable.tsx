import * as React from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Box,
  Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { GrowthChip } from "./GrowthChip";
import { formatLargeCurrency } from "../forms/components/masks/br";

export interface MetricHistoryTableProps {
  data: Array<{
    quarter: string;
    value: number | null;
  }>;
  valueType: "currency" | "percentage" | "number";
  currency?: string;
}

export function MetricHistoryTable({
  data,
  valueType,
  currency = "BRL",
}: MetricHistoryTableProps) {
  const { t } = useTranslation("common");

  // Helper to format Quarter string (YYYYMM -> Qn YYYY)
  const formatQuarter = (quarterCode: string) => {
    if (!quarterCode || quarterCode.length !== 6) return quarterCode;
    const year = quarterCode.slice(0, 4);
    const month = quarterCode.slice(4, 6);
    let q = "";
    switch (month) {
      case "03": q = "Q1"; break;
      case "06": q = "Q2"; break;
      case "09": q = "Q3"; break;
      case "12": q = "Q4"; break;
      default: q = month;
    }
    return `${q} ${year}`;
  };

  // Helper to format value
  const formatValue = (val: number | null) => {
    if (val === null || val === undefined) return "-";
    if (valueType === "currency") {
      return formatLargeCurrency(val, currency);
    }
    if (valueType === "percentage") {
      return `${(val * 100).toFixed(2)}%`;
    }
    return val.toLocaleString();
  };

  // Process data:
  // 1. Ensure sorted by quarter (oldest first) to calc growth
  // 2. Compute growth
  // 3. Reverse to show newest first
  const processedData = React.useMemo(() => {
    // Clone and sort oldest -> newest
    const sorted = [...data].sort((a, b) => a.quarter.localeCompare(b.quarter));

    const withGrowth = sorted.map((item, index) => {
      let growth: number | null = null;
      if (index > 0) {
        const prev = sorted[index - 1];
        if (
          prev &&
          item.value != null &&
          prev.value != null &&
          prev.value !== 0
        ) {
          growth = (item.value - prev.value) / Math.abs(prev.value);
        }
      }
      return {
        ...item,
        growth,
      };
    });

    return withGrowth.reverse();
  }, [data]);

  return (
    <TableContainer component={Paper} elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
      <Box sx={{ p: 2, borderBottom: "1px solid", borderColor: "divider" }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, textTransform: "uppercase", color: "text.secondary" }}>
          {t("quarterlySummary", "Quarterly Summary")}
        </Typography>
      </Box>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem" }}>
              {t("quarter", "QUARTER")}
            </TableCell>
            <TableCell align="right" sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem" }}>
              {t("value", "VALUE")}
            </TableCell>
            <TableCell align="right" sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem" }}>
              {t("evolution", "EVOLUÇÃO %")}
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {processedData.map((row) => (
            <TableRow key={row.quarter} hover>
              <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                {formatQuarter(row.quarter)}
              </TableCell>
              <TableCell align="right">
                {formatValue(row.value)}
              </TableCell>
              <TableCell align="right">
                {row.growth != null && (
                  <GrowthChip growthPercentage={row.growth} />
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
