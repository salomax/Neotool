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
import { formatTableDate } from "@/shared/utils/date";

export interface MetricHistoryItem {
  period: string;
  value: number | null;
  [key: string]: any;
}

export interface MetricHistoryTableProps {
  data: MetricHistoryItem[];
  valueType: "currency" | "percentage" | "number";
  currency?: string;
  /**
   * Title of the table section.
   * Defaults to "Quarterly Summary" (translated)
   */
  title?: string;
  /**
   * Label for the period column.
   * Defaults to "QUARTER" (translated)
   */
  periodLabel?: string;
  /**
   * Locale for formatting dates and numbers.
   * @default "pt-BR"
   */
  locale?: string;
  /**
   * Function to format the period/date string.
   * Defaults to formatting YYYYMM as quarter if applicable.
   */
  onFormatPeriod?: (value: string) => React.ReactNode;
  /**
   * Whether to disable multiplying percentage values by 100.
   * Useful when the data is already in percentage format (e.g. 10.5 for 10.5%).
   */
  disablePercentageScaling?: boolean;
}

export function MetricHistoryTable({
  data,
  valueType,
  currency = "BRL",
  title,
  periodLabel,
  locale = "pt-BR",
  onFormatPeriod,
  disablePercentageScaling = false,
}: MetricHistoryTableProps) {
  const { t } = useTranslation("common");

  // Helper to format Quarter string (YYYYMM -> Qn YYYY)
  const formatQuarter = (quarterCode: string) => {
    if (!quarterCode || quarterCode.length !== 6) return quarterCode;
    const year = quarterCode.slice(0, 4);
    const month = quarterCode.slice(4, 6);
    let qKey = "";
    switch (month) {
      case "03": qKey = "q1"; break;
      case "06": qKey = "q2"; break;
      case "09": qKey = "q3"; break;
      case "12": qKey = "q4"; break;
      default: return `${month} ${year}`;
    }
    return `${t(`calendar.quarters.${qKey}`, qKey.toUpperCase())} ${year}`;
  };

  const defaultFormatPeriod = (value: string) => {
    // If it looks like a quarter code (6 digits), try to format it as a quarter
    // This maintains backward compatibility for existing quarter-based data
    if (/^\d{6}$/.test(value)) {
       return formatQuarter(value);
    }
    
    // Try to parse as date
    const date = new Date(value);
    if (!isNaN(date.getTime())) {
      // Check if it's a valid date string (not just a number that happens to be a valid timestamp)
      // and format it according to locale
      // Use UTC to avoid timezone shifts if the input is just a date string like "2023-01-01"
      return formatTableDate(date, locale);
    }

    return value;
  };

  const formatPeriod = onFormatPeriod || defaultFormatPeriod;
  const displayTitle = title || t("summary", "Summary");
  const displayPeriodLabel = periodLabel || t("quarter", "QUARTER");

  // Helper to format value
  const formatValue = (val: number | null) => {
    if (val === null || val === undefined) return "-";
    
    let formattedString = "";
    if (valueType === "currency") {
      // Use locale-aware currency formatter if needed, but keeping formatLargeCurrency for now as it handles 'bi', 'mi' suffixes
      // Ideally formatLargeCurrency should accept locale, or we use Intl.NumberFormat
      formattedString = formatLargeCurrency(val, currency);
    } else if (valueType === "percentage") {
      const percentageValue = disablePercentageScaling ? val : val * 100;
      // Use locale for percentage formatting
      // Note: toLocaleString with style: 'percent' expects 0-1 range (e.g. 0.5 = 50%)
      // But here percentageValue is already scaled (e.g. 10.5) if !disablePercentageScaling or if data is already percent
      // So we use 'decimal' style with suffix % manually to control the value exactly
      formattedString = `${percentageValue.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
    } else {
      formattedString = val.toLocaleString(locale);
    }

    // Parse to bold only the numeric part
    // Regex matches: 
    // Group 1: Prefix (non-digits/dots/commas, e.g. "R$ ", "-")
    // Group 2: Number (digits, dots, commas)
    // Group 3: Suffix (e.g. " bi", "%")
    const match = formattedString.match(/^([^\d]*)([\d,.]+)(.*)$/);
    if (match) {
      return (
        <span>
          <span style={{ fontWeight: 400 }}>{match[1]}</span>
          <span style={{ fontWeight: 600 }}>{match[2]}</span>
          <span style={{ fontWeight: 400 }}>{match[3]}</span>
        </span>
      );
    }
    return formattedString;
  };

  // Process data:
  // 1. Ensure sorted by period (oldest first) to calc growth
  // 2. Compute growth
  // 3. Reverse to show newest first
  const processedData = React.useMemo(() => {
    // Clone and sort oldest -> newest
    const sorted = [...data].sort((a, b) => {
      const pA = a.period || a.quarter || "";
      const pB = b.period || b.quarter || "";
      return pA.localeCompare(pB);
    });

    const withGrowth = sorted.map((item, index) => {
      let growth: number | null = null;
      if (index > 0) {
        const prev = sorted[index - 1];
        if (
          prev &&
          item.value != null &&
          prev.value != null
        ) {
          if (valueType === "percentage") {
            const diff = item.value - prev.value;
            growth = disablePercentageScaling ? diff / 100 : diff;
          } else if (prev.value !== 0) {
            growth = (item.value - prev.value) / Math.abs(prev.value);
          }
        }
      }
      return {
        ...item,
        displayPeriod: item.period || item.quarter || "",
        growth,
      };
    });

    return withGrowth.reverse();
  }, [data, valueType, disablePercentageScaling]);

  return (
    <TableContainer component={Paper} elevation={0} sx={{ border: "1px solid", borderColor: "divider" }}>
      <Box sx={{ p: 2, borderBottom: "1px solid", borderColor: "divider" }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, textTransform: "uppercase", color: "text.secondary" }}>
          {displayTitle}
        </Typography>
      </Box>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase" }}>
              {displayPeriodLabel}
            </TableCell>
            <TableCell align="right" sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase" }}>
              {t("value", "VALUE")}
            </TableCell>
            <TableCell align="right" sx={{ color: "text.secondary", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase" }}>
              {valueType === "percentage" ? t("variation", "VARIAÇÃO") : t("evolution", "EVOLUÇÃO %")}
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {processedData.map((row, index) => (
            <TableRow key={row.displayPeriod || index} hover>
              <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                {formatPeriod(row.displayPeriod)}
              </TableCell>
              <TableCell align="right">
                {formatValue(row.value)}
              </TableCell>
              <TableCell align="right">
                {row.growth != null && (
                  <GrowthChip growthPercentage={row.growth} locale={locale} />
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
