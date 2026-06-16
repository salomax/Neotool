"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import Card from "@/shared/components/ui/primitives/Card";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { useTranslation, i18n } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { formatMetricValue } from "@/shared/utils/formatMetricValue";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";
import { FixedIncomeProductGroup } from "@/lib/graphql/types/__generated__/graphql";

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
        hoverable={false}
        sx={{
          p: 3,
          mb: 3,
          boxShadow: "none",
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

  const comparisonRow =
    rows.find(
      (row) =>
        row.productGroup !== summary.baseProductGroup &&
        row.rateModel === summary.baseRateModel
    ) ?? rows.find((row) => row.productGroup !== summary.baseProductGroup);

  if (!comparisonRow) {
    return null;
  }

  const productGroupLabelKey = (productGroup: FixedIncomeProductGroup) => {
    switch (productGroup) {
      case FixedIncomeProductGroup.TaxableIr:
        return "taxableProductLabel";
      case FixedIncomeProductGroup.TaxableIrNoIof:
        return "taxableCommittedProductLabel";
      default:
        return "exemptProductLabel";
    }
  };

  const baseLabel = t(`${ns}.${productGroupLabelKey(summary.baseProductGroup)}`);
  const targetLabel = t(`${ns}.${productGroupLabelKey(comparisonRow.productGroup)}`);

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
  let suffixText = "";

  if (rateModel === "CDI") {
    suffixText = ` ${t(`${ns}.comparisonTableRateModelCdi`)}`;
  } else if (rateModel === "FIXED") {
    suffixText = ` ${t(`${ns}.comparisonTableRateModelFixedSuffix`)}`;
  } else if (rateModel === "IPCA_PLUS") {
    suffixText = ` ${t(`${ns}.comparisonTableRateModelIpcaPrefix`)}`;
  }

  return (
    <Card
      variant="outlined"
      hoverable={false}
      sx={{
        p: 3,
        mb: 3,
        bgcolor: "primary.main",
        backgroundImage: (theme) =>
          `linear-gradient(90deg, ${theme.palette.primary.dark} 0%, ${theme.palette.primary.main} 100%)`,
        boxShadow: "none",
        color: "primary.contrastText",
        "& .MuiTypography-root": { color: "inherit" },
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexDirection: { xs: "column", md: "row" },
          alignItems: { xs: "flex-start", md: "flex-end" },
          justifyContent: "flex-start",
          gap: 3,
          flexWrap: { xs: "nowrap", md: "wrap" },
        }}
      >
        <Box sx={{ textAlign: "left", minWidth: { md: 220 } }}>
          <Typography
            variant="h2"
            component="p"
            sx={{
              fontWeight: 900,
              color: "primary.contrastText",
              lineHeight: 1,
              fontSize: { xs: "2.8rem", md: "3rem" },
            }}
          >
            {rateParts.main}
            <Box
              component="span"
              sx={{
                ml: 0.5,
                fontSize: "0.5em",
                color: "primary.contrastText",
              }}
            >
              {rateParts.suffix}
              {suffixText}
            </Box>
          </Typography>
        </Box>

        <Box sx={{ maxWidth: 680, pb: { md: 0.25 } }}>
          <Typography
            variant="body1"
            sx={{ fontSize: 14, lineHeight: 1.5, color: "primary.contrastText" }}
          >
            {t(`${ns}.equivalentRateDescriptionPrefix`)}{" "}
            <Box component="span" sx={{ fontWeight: 700 }}>
              {targetLabel}
            </Box>{" "}
            {t(`${ns}.equivalentRateDescriptionMiddle`)}{" "}
            <Box component="span" sx={{ fontWeight: 700 }}>
              {baseLabel}
            </Box>
            {" "}{t(`${ns}.equivalentRateDescriptionSuffix`)}
          </Typography>
        </Box>
      </Box>
    </Card>
  );
}
