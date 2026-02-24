"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { alpha } from "@mui/material/styles";
import Card from "@/shared/components/ui/primitives/Card";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { useTranslation, i18n } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { formatMetricValue } from "@/shared/utils/formatMetricValue";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";

export interface FixedIncomeSummaryCardProps {
  result?: FixedIncomeComparisonQuery["fixedIncomeComparison"] | null;
  loading?: boolean;
}

export function FixedIncomeSummaryCard({
  result,
  loading,
}: FixedIncomeSummaryCardProps) {
  const { t } = useTranslation(investmentsTranslations);
  const ns = "fixedIncomeComparison" as const;

  if (loading) {
    return (
      <Card
        variant="outlined"
        sx={{
          p: 3,
          mb: 3,
        }}
      >
        <Skeleton
          variant="rectangular"
          height={80}
          sx={{ borderRadius: 2 }}
        />
      </Card>
    );
  }

  if (!result) {
    return null;
  }

  const { summary, rows } = result;

  const oppositeGroup =
    summary.baseProductGroup === "TAXABLE_IR" ? "EXEMPT_IR" : "TAXABLE_IR";

  const comparisonRow =
    rows.find(
      (row) =>
        row.productGroup === oppositeGroup &&
        row.rateModel === summary.baseRateModel
    ) ?? rows.find((row) => row.productGroup === oppositeGroup);

  if (!comparisonRow) {
    return null;
  }

  const baseLabelKey =
    summary.baseProductGroup === "TAXABLE_IR"
      ? "taxableProductLabel"
      : "exemptProductLabel";

  const targetLabelKey =
    summary.baseProductGroup === "TAXABLE_IR"
      ? "exemptProductLabel"
      : "taxableProductLabel";

  const baseLabel = t(`${ns}.${baseLabelKey}`);
  const targetLabel = t(`${ns}.${targetLabelKey}`);

  const locale = i18n?.language || "pt-BR";

  const isFixedRate = summary.baseRateModel === "FIXED";

  const rateParts = formatMetricValue(
    comparisonRow.equivalentRateBps / 100,
    "percentage",
    {
      locale,
      decimals: isFixedRate ? 2 : 1,
      disablePercentageScaling: true,
    }
  );

  const rateModel = comparisonRow.rateModel;
  let prefixText = "";
  let suffixText = "";

  if (rateModel === "CDI") {
    suffixText = ` ${t(`${ns}.comparisonTableRateModelCdi`)}`;
  } else if (rateModel === "FIXED") {
    suffixText = ` ${t(`${ns}.comparisonTableRateModelFixedSuffix`)}`;
  } else if (rateModel === "IPCA_PLUS") {
    prefixText = t(`${ns}.comparisonTableRateModelIpcaPrefix`);
  }

  return (
    <Card
      variant="outlined"
      sx={{
        p: 3,
        mb: 3,
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexDirection: { xs: "column", md: "row" },
          alignItems: { xs: "flex-start", md: "center" },
          justifyContent: "space-between",
          gap: 2,
        }}
      >
        <Box sx={{ flex: 1 }}>
          <Typography
            variant="overline"
            sx={{
              display: "block",
              color: "text.secondary",
              letterSpacing: "0.08em",
              textTransform: "uppercase",
              mb: 0.5,
              fontSize: "0.85rem",
            }}
          >
            {t(`${ns}.netEquivalentTitle`)}
          </Typography>
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ fontSize: "1rem", lineHeight: 1.5 }}
          >
            {t(`${ns}.netEquivalentLine1Prefix`)}
            <Box
              component="span"
              sx={{
                fontWeight: 700,
                color: "primary.main",
                display: "inline-block",
                borderBottom: (theme) =>
                  `3px solid ${alpha(theme.palette.primary.main, 0.18)}`,
              }}
            >
              {baseLabel}
              {t(`${ns}.netEquivalentLine1Suffix`, {
                baseRate: summary.baseRateDisplay,
              })}
            </Box>
            <br />
            {t(`${ns}.netEquivalentAssetsPrefix`)}
            <Box
              component="span"
              sx={{
                fontWeight: 700,
                color: "primary.main",
                display: "inline-block",
                borderBottom: (theme) =>
                  `3px solid ${alpha(theme.palette.primary.main, 0.18)}`,
              }}
            >
              {targetLabel}
            </Box>{" "}
            {t(`${ns}.netEquivalentLine3`)}
          </Typography>
        </Box>

        <Box
          sx={{
            textAlign: { xs: "left", md: "right" },
            minWidth: { md: 180 },
          }}
        >
          <Typography
            variant="h2"
            component="p"
            sx={{
              fontWeight: 900,
              color: "text.primary",
              lineHeight: 1.1,
              fontSize: { xs: "2.2rem", md: "2.5rem" },
            }}
          >
            {prefixText && (
              <Box
                component="span"
                sx={{
                  mr: 0.5,
                  fontSize: "0.5em",
                  color: "text.secondary",
                  fontWeight: 700,
                }}
              >
                {prefixText}
              </Box>
            )}
            {rateParts.main}
            <Box
              component="span"
              sx={{
                ml: 0.5,
                fontSize: "0.5em",
              }}
            >
              {rateParts.suffix}
              {suffixText}
            </Box>
          </Typography>
        </Box>
      </Box>
    </Card>
  );
}
