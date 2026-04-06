"use client";

import React from "react";
import { Link, Stack, useTheme } from "@mui/material";

export const PERIOD_OPTIONS = [
  "MTD",
  "YTD",
  "ONE_YEAR",
  "THREE_YEARS",
  "ALL",
] as const;

export type SummaryPeriod = (typeof PERIOD_OPTIONS)[number];

interface PeriodSelectorProps {
  selectedPeriod: SummaryPeriod;
  onSelectPeriod: (period: SummaryPeriod) => void;
  getPeriodLabel: (period: SummaryPeriod) => string;
}

export function PeriodSelector({
  selectedPeriod,
  onSelectPeriod,
  getPeriodLabel,
}: PeriodSelectorProps) {
  const theme = useTheme();

  return (
    <Stack
      direction="row"
      spacing={1}
      flexWrap="wrap"
      useFlexGap
      sx={{ width: "100%", justifyContent: "space-between" }}
    >
      {PERIOD_OPTIONS.map((period) => (
        <Link
          key={period}
          component="button"
          type="button"
          underline={selectedPeriod === period ? "always" : "hover"}
          onClick={() => onSelectPeriod(period)}
          aria-current={selectedPeriod === period ? "page" : undefined}
          sx={{
            fontSize: "0.8125rem",
            fontWeight: selectedPeriod === period ? 700 : 600,
            color:
              selectedPeriod === period
                ? theme.palette.primary.main
                : theme.palette.text.secondary,
            textUnderlineOffset: 3,
            "&:hover": {
              color: theme.palette.primary.main,
            },
          }}
        >
          {getPeriodLabel(period)}
        </Link>
      ))}
    </Stack>
  );
}
