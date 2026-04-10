"use client";

import React from "react";
import { Box, Fade } from "@mui/material";
import { InvestmentCard } from "./InvestmentCard";
import { INVESTMENT_GRID_CLASS_DEFINITIONS, type InvestmentGridClassKey } from "./investmentGridConfig";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";

export interface InvestmentGridCardMatrixProps {
  metricsByKey: Record<
    InvestmentGridClassKey,
    { grossValue: number; percentageGain: number | null }
  >;
  assetCountByKey: Record<InvestmentGridClassKey, number | null>;
  chartSeriesByKey: Record<InvestmentGridClassKey, TimeSeriesDataPoint[]>;
  onOpenClass: (key: InvestmentGridClassKey) => void;
  onOpenRecordDrawerForClass: (key: InvestmentGridClassKey) => void;
}

export function InvestmentGridCardMatrix({
  metricsByKey,
  assetCountByKey,
  chartSeriesByKey,
  onOpenClass,
  onOpenRecordDrawerForClass,
}: InvestmentGridCardMatrixProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Fade in timeout={220}>
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2 }}>
        {INVESTMENT_GRID_CLASS_DEFINITIONS.map((def) => (
          <Box key={def.key}>
            <InvestmentCard
              title={t(def.titleKey)}
              assetCount={assetCountByKey[def.key] ?? null}
              grossValue={metricsByKey[def.key]?.grossValue ?? 0}
              currency="BRL"
              percentageGain={metricsByKey[def.key]?.percentageGain ?? null}
              iconKind={def.iconKind}
              primaryActionLabel={t(def.primaryActionKey)}
              secondaryActionLabel={t(def.secondaryActionKey)}
              chartData={chartSeriesByKey[def.key] ?? []}
              singleActionLabel={def.key === "cash" ? t("investments.grid.movimentar") : undefined}
              onSingleActionClick={def.key === "cash" ? () => onOpenRecordDrawerForClass(def.key) : undefined}
              onFirstInvestmentClick={() => onOpenRecordDrawerForClass(def.key)}
              onOpenDetail={() => onOpenClass(def.key)}
            />
          </Box>
        ))}
      </Box>
    </Fade>
  );
}
