"use client";

import React from "react";
import { Chip } from "@mui/material";
import { alpha, useTheme } from "@mui/material/styles";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import { formatFundAssetType, type CvmFundAssetType } from "@/lib/utils/mapCvmFundAssetType";

type FundChipPalette = "primary" | "secondary" | "success" | "warning" | "info";

const FUND_TYPE_COLOR_MAP: Record<string, FundChipPalette> = {
  FIXED_INCOME_FUND: "success",
  EQUITY_FUND: "primary",
  MULTI_MARKET_FUND: "secondary",
  FOREX_FUND: "warning",
  MUTUAL_FUND: "info",
  PGBL: "info",
  VGBL: "info",
};

export interface FundTypeChipProps {
  assetType: string | null | undefined;
  size?: "small" | "medium";
}

export function FundTypeChip({ assetType, size = "small" }: FundTypeChipProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  const paletteKey = FUND_TYPE_COLOR_MAP[String(assetType)] ?? "info";
  const color = theme.palette[paletteKey].main;

  const label = assetType ? formatFundAssetType(assetType as CvmFundAssetType, t) : "—";

  return (
    <Chip
      size={size}
      variant="outlined"
      label={label}
      sx={{
        bgcolor: alpha(color, 0.1),
        color,
        borderColor: alpha(color, 0.35),
        "& .MuiChip-label": { px: 1 },
      }}
    />
  );
}

export default FundTypeChip;
