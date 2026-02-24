"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";

export function FixedIncomeComparisonEmptyState() {
  const { t } = useTranslation(investmentsTranslations);
  const ns = "fixedIncomeComparison" as const;

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        minHeight: 400,
        textAlign: "center",
        p: 3,
        color: "text.secondary",
      }}
    >
      <Box
        sx={{
          mb: 3,
          p: 3,
          borderRadius: "50%",
          bgcolor: (theme) =>
            theme.palette.mode === "dark"
              ? "rgba(255, 255, 255, 0.05)"
              : "rgba(0, 0, 0, 0.03)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <TrendingUpIcon sx={{ fontSize: 64, opacity: 0.5 }} />
      </Box>
      <Typography variant="h6" gutterBottom>
        {t(`${ns}.emptyStateTitle`)}
      </Typography>
      <Typography variant="body2" sx={{ maxWidth: 400, opacity: 0.7 }}>
        {t(`${ns}.emptyStateDescription`)}
      </Typography>
    </Box>
  );
}
