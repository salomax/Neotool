"use client";

import React, { useMemo } from "react";
import {
  Box,
  Typography,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import Card from "@/shared/components/ui/primitives/Card";
import { Table } from "@/shared/components/ui/data-display/Table";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { useTranslation, i18n } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { formatMetricValue } from "@/shared/utils/formatMetricValue";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";
import type { FixedIncomeRateModel } from "@/lib/graphql/types/__generated__/graphql";

export interface FixedIncomeComparisonTableProps {
  result?: FixedIncomeComparisonQuery["fixedIncomeComparison"] | null;
  loading?: boolean;
}

function formatCurrencyParts(
  value: number,
  locale: string
): { prefix: string; main: string; suffix: string } {
  return formatMetricValue(value, "currency", { locale });
}

function formatPercentageParts(
  value: number,
  locale: string,
  decimals = 2,
  disableScaling = true
): { prefix: string; main: string; suffix: string } {
  return formatMetricValue(value, "percentage", {
    locale,
    decimals,
    disablePercentageScaling: disableScaling,
  });
}

function formatRateDisplay(
  equivalentRateBps: number,
  rateModel: FixedIncomeRateModel,
  locale: string,
  t: (key: string) => string,
  ns: string
): string {
  const isFixed = rateModel === "FIXED";
  const displayValue = equivalentRateBps / 100;
  const parts = formatMetricValue(displayValue, "percentage", {
    locale,
    decimals: isFixed ? 2 : 1,
    disablePercentageScaling: true,
  });
  const rateStr = [parts.prefix, parts.main, parts.suffix].filter(Boolean).join("");
  if (rateModel === "CDI") {
    return `${rateStr} ${t(`${ns}.comparisonTableRateModelCdi`)}`;
  }
  if (rateModel === "FIXED") {
    return `${rateStr} ${t(`${ns}.comparisonTableRateModelFixedSuffix`)}`;
  }
  return `${t(`${ns}.comparisonTableRateModelIpcaPrefix`)} ${rateStr}`;
}

function getInvestmentLabel(
  productGroup: "TAXABLE_IR" | "EXEMPT_IR",
  t: (key: string) => string,
  ns: string
): string {
  const groupLabel =
    productGroup === "TAXABLE_IR"
      ? t(`${ns}.taxableProductLabel`)
      : t(`${ns}.exemptProductLabel`);
  return groupLabel;
}

export function FixedIncomeComparisonTable({
  result,
  loading,
}: FixedIncomeComparisonTableProps) {
  const { t } = useTranslation(investmentsTranslations);
  const ns = "fixedIncomeComparison" as const;
  const locale = i18n?.language || "pt-BR";

  const content = useMemo(() => {
    if (!result?.rows?.length) return null;

    const { rows, methodology, summary } = result;
    const { calendarDays, businessDays } = methodology.dayCount;
    const baseRateModel = summary.baseRateModel as FixedIncomeRateModel;
    
    const sortedRows = [...rows].sort((a, b) => {
      const order = {
        TAXABLE_IR: 0,
        EXEMPT_IR: 1,
      } as const;
      const aOrder = order[a.productGroup as keyof typeof order] ?? 99;
      const bOrder = order[b.productGroup as keyof typeof order] ?? 99;
      return aOrder - bOrder;
    });

    const years = Math.round(calendarDays / 365) || 1;
    const yearsLabel =
      years === 1 ? t(`${ns}.comparisonTableYear`) : t(`${ns}.comparisonTableYears`);
    const businessDaysLabel = t(`${ns}.comparisonTableBusinessDays`);
    const durationText = t(`${ns}.comparisonTableDuration`, {
      years,
      yearsLabel,
      businessDays,
      businessDaysLabel,
    });

    return { sortedRows, durationText, baseRateModel };
  }, [result, t, ns]);

  if (loading) {
    return (
      <Card
        variant="outlined"
        sx={{ p: 3, mb: 3 }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            mb: 2,
          }}
        >
          <Skeleton variant="rectangular" width={280} height={32} />
          <Skeleton
            variant="rectangular"
            width={200}
            height={28}
            sx={{ borderRadius: 4 }}
          />
        </Box>
        <Skeleton
          variant="table"
          rows={4}
          columns={6}
          spacing={1}
          height={280}
        />
      </Card>
    );
  }

  if (!content) {
    return null;
  }

  const { sortedRows, durationText } = content;

  const headerCellSx = {
    fontWeight: 700,
    color: "primary.main",
    textTransform: "uppercase" as const,
    letterSpacing: "0.04em",
    py: 1.5,
    px: 2,
  };

  return (
    <Card
      variant="outlined"
      sx={{
        mb: 3,
        borderRadius: 2,
        overflow: "hidden",
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 2,
          px: 3,
          py: 3,
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        <Typography
          variant="h6"
          component="h2"
          sx={{ fontWeight: 700, color: "text.primary" }}
        >
          {t(`${ns}.comparisonTableTitle`)}
        </Typography>
        <Chip
          label={durationText}
          size="small"
          sx={{
            bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12),
            color: "primary.main",
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.04em",
          }}
        />
      </Box>
      <TableContainer
        sx={{
          overflowX: "auto",
        }}
      >
        <Table
          name="fixed-income-comparison"
          sx={{
            border: "none",
            "& .MuiTableCell-root": {
              borderBottom: "none",
            },
            "& .MuiTableBody-root .MuiTableRow-root": {
              bgcolor: "background.paper",
              transition: "background-color 0.15s ease-in-out",
            },
            "& .MuiTableBody-root .MuiTableRow-root:hover": {
              bgcolor: "action.hover",
            },
            "& .MuiTableBody-root .MuiTableCell-root": {
              py: 1.5,
              px: 2.5,
            },
            "& .MuiTableHead-root .MuiTableCell-root": {
              py: 1.5,
              px: 2.5,
            },
          }}
        >
          <TableHead>
            <TableRow
              sx={{
                bgcolor: "action.hover",
              }}
            >
              <TableCell align="left" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnInvestment`)}
              </TableCell>
              <TableCell align="right" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnRate`)}
              </TableCell>
              <TableCell align="right" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnGross`)}
              </TableCell>
              <TableCell align="right" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnIr`)}
              </TableCell>
              <TableCell align="right" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnNet`)}
              </TableCell>
              <TableCell align="right" sx={headerCellSx}>
                {t(`${ns}.comparisonTableColumnRealGain`)}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedRows.map((row, index) => {
              const grossParts = formatCurrencyParts(row.gross, locale);
              const netParts = formatCurrencyParts(row.net, locale);
              const gainParts = formatPercentageParts(
                row.realGainPercent,
                locale
              );
              const irExempt = row.isExempt;
              const investmentLabel = getInvestmentLabel(
                row.productGroup,
                t,
                ns
              );
              const rateDisplay = formatRateDisplay(
                row.equivalentRateBps,
                row.rateModel as FixedIncomeRateModel,
                locale,
                t,
                ns
              );

              return (
                <TableRow key={`${row.label}-${row.rateModel}-${index}`}>
                  <TableCell
                    align="left"
                    sx={{
                      fontWeight: 700,
                      color: "primary.main",
                    }}
                  >
                    {investmentLabel}
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {rateDisplay}
                  </TableCell>
                  <TableCell align="right">
                    {[grossParts.prefix, grossParts.main, grossParts.suffix]
                      .filter(Boolean)
                      .join(" ")}
                  </TableCell>
                  <TableCell align="right">
                    {irExempt ? (
                      <Typography
                        component="span"
                        sx={{ color: "success.main", fontWeight: 600 }}
                      >
                        {t(`${ns}.comparisonTableIrExempt`)}
                      </Typography>
                    ) : (
                      <Typography
                        component="span"
                        sx={{ color: "error.main" }}
                      >
                        -{" "}
                        {[
                          formatCurrencyParts(Math.abs(row.ir), locale).prefix,
                          formatCurrencyParts(Math.abs(row.ir), locale).main,
                          formatCurrencyParts(Math.abs(row.ir), locale).suffix,
                        ]
                          .filter(Boolean)
                          .join(" ")}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {[netParts.prefix, netParts.main, netParts.suffix]
                      .filter(Boolean)
                      .join(" ")}
                  </TableCell>
                  <TableCell align="right">
                    <Chip
                      size="small"
                      label={`+${gainParts.main}${gainParts.suffix}`}
                      sx={{
                        bgcolor: (theme) =>
                          (theme as any).custom?.palette?.successLightBg ??
                          theme.palette.success.light + "40",
                        color: "success.dark",
                        fontWeight: 700,
                        "& .MuiChip-label": {
                          px: 1,
                          py: 0.25,
                          fontSize: 11,
                        },
                      }}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  );
}
