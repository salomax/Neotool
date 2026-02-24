"use client";

import React from "react";
import { Box, Chip, Typography } from "@mui/material";
import Card from "@/shared/components/ui/primitives/Card";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { useTranslation, i18n } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { formatMetricValue } from "@/shared/utils/formatMetricValue";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";

export interface FixedIncomeMethodologyCardProps {
  result?: FixedIncomeComparisonQuery["fixedIncomeComparison"] | null;
  loading?: boolean;
}

function formatPercent(value: number, locale: string): string {
  const parts = formatMetricValue(value, "percentage", {
    locale,
    decimals: 1,
  });
  return `${parts.main}${parts.suffix}`;
}

function bracketRangeLabel(
  fromCalendarDays: number,
  toCalendarDays: number | null,
  t: (key: string, options?: Record<string, string | number>) => string,
  ns: string
): string {
  if (toCalendarDays == null) {
    return t(`${ns}.methodologyTaxRangeFrom`, { from: fromCalendarDays });
  }
  return t(`${ns}.methodologyTaxRangeBetween`, {
    from: fromCalendarDays,
    to: toCalendarDays,
  });
}

function formatAccrualLabel(accrualConvention: string): string {
  if (accrualConvention === "BUSINESS_252") {
    return "252 dias úteis por ano (padrão de mercado)";
  }
  return accrualConvention;
}

export function FixedIncomeMethodologyCard({
  result,
  loading,
}: FixedIncomeMethodologyCardProps) {
  const { t } = useTranslation(investmentsTranslations);
  const ns = "fixedIncomeComparison" as const;
  const locale = i18n?.language || "pt-BR";

  if (loading) {
    return (
      <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
        <Skeleton
          variant="rectangular"
          height={210}
          sx={{ borderRadius: 2 }}
        />
      </Card>
    );
  }

  if (!result?.methodology) {
    return null;
  }

  const { dayCount, tax } = result.methodology;
  const accrualLabel = formatAccrualLabel(dayCount.accrualConvention);
  const activeBracketKey = `${tax.activeBracket.fromCalendarDays}-${tax.activeBracket.toCalendarDays ?? "null"}`;
  const sortedBrackets = [...tax.bracketTable].sort(
    (a, b) => a.fromCalendarDays - b.fromCalendarDays
  );

  const activeBracketLabel = bracketRangeLabel(
    tax.activeBracket.fromCalendarDays,
    tax.activeBracket.toCalendarDays,
    t,
    ns
  );

  return (
    <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
      <Box
        sx={{
          borderBottom: 1,
          borderColor: "divider",
          pb: 2,
          mb: 4,
        }}
      >
        <Typography
          variant="h6"
          component="h2"
          sx={{ fontWeight: 700 }}
        >
          {t(`${ns}.methodologyTitle`)}
        </Typography>
      </Box>

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "1.1fr 1fr" },
          gap: { xs: 3, md: 6 },
        }}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Typography
            variant="overline"
            sx={{
              letterSpacing: "0.12em",
              textTransform: "uppercase",
              color: "text.secondary",
            }}
          >
            {t(`${ns}.methodologyDayCountTitle`, "Convenção de Dias")}
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ lineHeight: 1.6 }}
          >
            {t(`${ns}.methodologyRuleDayCount`, {
              accrualLabel,
              businessDays: dayCount.businessDays,
              calendarDays: dayCount.calendarDays,
            })}
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ lineHeight: 1.6 }}
          >
            {t(`${ns}.methodologyRuleTargetNetAtEndDate`)}
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ lineHeight: 1.6 }}
          >
            {t(`${ns}.methodologyRuleTax`, {
              appliesOnProfitOnly: tax.appliesOnProfitOnly
                ? t(`${ns}.methodologyTaxAppliesOnProfitOnly`)
                : t(`${ns}.methodologyTaxAppliesOnGrossValue`),
            })}
          </Typography>
        </Box>

        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Typography
            variant="overline"
            sx={{
              letterSpacing: "0.12em",
              textTransform: "uppercase",
              color: "text.secondary",
            }}
          >
            {t(`${ns}.methodologyTaxTableTitle`)}
          </Typography>
          <Chip
            size="small"
            color="primary"
            variant="outlined"
            sx={{
              alignSelf: "flex-start",
              maxWidth: { xs: "100%", sm: 360 },
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
            label={t(`${ns}.methodologyActiveBracketLabel`, {
              range: activeBracketLabel,
              rate: formatPercent(tax.activeBracket.rateDecimal, locale),
            })}
          />
          <Box
            sx={{
              mt: 1,
              borderRadius: 2,
              border: 1,
              borderColor: "divider",
              overflow: "hidden",
            }}
          >
            {sortedBrackets.map((bracket, index) => {
              const rowKey = `${bracket.fromCalendarDays}-${bracket.toCalendarDays ?? "null"}`;
              const isActive = rowKey === activeBracketKey;
              const rangeLabel = bracketRangeLabel(
                bracket.fromCalendarDays,
                bracket.toCalendarDays,
                t,
                ns
              );
              const isLast = index === sortedBrackets.length - 1;

              return (
                <Box
                  key={rowKey}
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    px: 2,
                    py: 1.5,
                    borderBottom: isLast ? "none" : "1px solid",
                    borderBottomColor: "divider",
                    bgcolor: isActive ? "action.hover" : "background.paper",
                  }}
                >
                  <Typography
                    variant="body2"
                    sx={{ color: "text.secondary", fontWeight: isActive ? 600 : 400 }}
                  >
                    {rangeLabel}
                  </Typography>
                  <Typography
                    variant="body2"
                    sx={{
                      fontWeight: 700,
                      color: isActive ? "primary.main" : "text.primary",
                    }}
                  >
                    {formatPercent(bracket.rateDecimal, locale)}
                  </Typography>
                </Box>
              );
            })}
          </Box>
        </Box>
      </Box>
    </Card>
  );
}

export default FixedIncomeMethodologyCard;
