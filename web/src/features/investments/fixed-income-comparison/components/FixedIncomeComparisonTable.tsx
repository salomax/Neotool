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
  Divider,
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
import { FormSectionLabel } from "./FormSectionLabel";

export interface FixedIncomeComparisonTableProps {
  result?: FixedIncomeComparisonQuery["fixedIncomeComparison"] | null;
  loading?: boolean;
}

function formatCurrencyFull(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatCurrencyParts(
  value: number,
  locale: string
): { prefix: string; main: string; suffix: string } {
  const parts = formatMetricValue(value, "currency", { locale });
  const absoluteValue = Math.abs(value);
  const abbreviationDivisor =
    absoluteValue >= 1_000_000_000_000
      ? 1_000_000_000_000
      : absoluteValue >= 1_000_000_000
      ? 1_000_000_000
      : absoluteValue >= 1_000_000
      ? 1_000_000
      : null;

  if (!abbreviationDivisor) {
    return parts;
  }

  const abbreviatedValue = absoluteValue / abbreviationDivisor;
  const formattedMain = abbreviatedValue.toLocaleString(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return {
    ...parts,
    main: value < 0 ? `-${formattedMain}` : formattedMain,
  };
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
    const { businessDays } = methodology.dayCount;
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

    const durationText = t(`${ns}.comparisonTablePeriodDays`, {
      businessDays,
    });

    const totalAmountText = formatCurrencyFull(summary.net, locale);
    const durationWithTotalText = t(`${ns}.comparisonTableDurationWithTotal`, {
      period: durationText,
      totalAmount: totalAmountText,
    });

    return { sortedRows, durationWithTotalText, baseRateModel };
  }, [result, t, ns]);

  if (loading) {
    return (
      <Card
        variant="outlined"
        hoverable={false}
        sx={{ p: 3, mb: 3, boxShadow: "none" }}
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

  const { sortedRows, durationWithTotalText } = content;

  const headerCellSx = {
    py: 1.5,
    px: 2,
  };

  return (
    <>
          <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          justifyContent: "space-between",
          gap: 2,
          px: 2,
          pt: 3,
        }}
      >
        <FormSectionLabel sx={{ fontSize: 14 }}>{t(`${ns}.comparisonTableTitle`)}</FormSectionLabel>
        <Chip
          label={durationWithTotalText}
          size="small"
          sx={{
            bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12),
            color: "primary.main",
            "& .MuiChip-label": { fontSize: 12 },
          }}
        />
      </Box>
      <Card
        variant="outlined"
        hoverable={false}
        sx={{
          mb: 3,
          borderRadius: 2,
          bgcolor: "none",
          boxShadow: "none",
          overflow: "hidden",
          "& .MuiTypography-root": { fontSize: 14 },
        }}
      >
        <TableContainer
          sx={{
            overflowX: "auto",
          }}
        >
          <Table
            name="fixed-income-comparison"
            sx={{
              border: "none",
              "& .MuiTableCell-root": { fontSize: 14, borderBottom: "none" },
              "& .MuiTableBody-root .MuiTableRow-root": {
                bgcolor: "transparent",
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
            <TableHead sx={{ borderBottom: "1px solid", borderColor: "divider" }}>
              <TableRow>
                <TableCell align="left" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnInvestment`)}
                  </FormSectionLabel>
                </TableCell>
                <TableCell align="right" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnRate`)}
                  </FormSectionLabel>
                </TableCell>
                <TableCell align="right" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnGross`)}
                  </FormSectionLabel>
                </TableCell>
                <TableCell align="right" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnIr`)}
                  </FormSectionLabel>
                </TableCell>
                <TableCell align="right" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnNet`)}
                  </FormSectionLabel>
                </TableCell>
                <TableCell align="right" sx={headerCellSx}>
                  <FormSectionLabel
                    mb={0}
                    sx={{ display: "inline-block", fontSize: 14 }}
                  >
                    {t(`${ns}.comparisonTableColumnRealGain`)}
                  </FormSectionLabel>
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
                        
                        color: "primary.main",
                      }}
                    >
                      {investmentLabel}
                    </TableCell>
                    <TableCell align="right" sx={{  }}>
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
                          sx={{ color: "success.main",  }}
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
                    <TableCell align="right" sx={{  }}>
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
    </>
  );
}
