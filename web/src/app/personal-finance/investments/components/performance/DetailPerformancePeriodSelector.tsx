"use client";

import * as React from "react";
import { ButtonBase, Stack } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import type { DetailPerformancePeriod } from "./detailPerformancePeriods";
import { DETAIL_PERFORMANCE_PERIODS } from "./detailPerformancePeriods";

interface DetailPerformancePeriodSelectorProps {
  value: DetailPerformancePeriod;
  onChange: (period: DetailPerformancePeriod) => void;
}

export function DetailPerformancePeriodSelector({
  value,
  onChange,
}: DetailPerformancePeriodSelectorProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
      {DETAIL_PERFORMANCE_PERIODS.map((period) => {
        const selected = period === value;
        return (
          <ButtonBase
            key={period}
            onClick={() => onChange(period)}
            sx={{
              borderRadius: 999,
              border: "1px solid",
              borderColor: selected ? "primary.main" : "divider",
              bgcolor: selected ? "primary.50" : "transparent",
              color: selected ? "primary.main" : "text.secondary",
              fontSize: "0.75rem",
              fontWeight: 600,
              px: 1.25,
              py: 0.5,
              lineHeight: 1,
            }}
          >
            {t(`investments.detailDrawer.chart.period.${period}`)}
          </ButtonBase>
        );
      })}
    </Stack>
  );
}

export default DetailPerformancePeriodSelector;
