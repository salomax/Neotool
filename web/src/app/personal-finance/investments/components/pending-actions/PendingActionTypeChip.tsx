"use client";

import React from "react";
import { Chip } from "@mui/material";
import { alpha, useTheme, type Theme } from "@mui/material/styles";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";

type ChipPaletteKey = "primary" | "secondary" | "success" | "warning" | "info" | "error";

const TYPE_COLOR_MAP: Record<PendingActionType, ChipPaletteKey> = {
  [PendingActionType.InvestmentMatured]: "warning",
  [PendingActionType.CouponPaymentDue]: "primary",
  [PendingActionType.RecurringPaymentDue]: "info",
  [PendingActionType.BankAccountNegativeBalance]: "error",
  [PendingActionType.AgentWriteConfirmation]: "secondary",
};

export interface PendingActionTypeChipProps {
  type: PendingActionType;
  size?: "small" | "medium";
}

function chipColor(type: PendingActionType, palette: Theme["palette"]): string {
  const key = TYPE_COLOR_MAP[type] ?? "info";
  return palette[key].main;
}

export function PendingActionTypeChip({ type, size = "small" }: PendingActionTypeChipProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const color = chipColor(type, theme.palette);
  const label = t(`investments.pendingActions.types.${type}` as "investments.pendingActions.types.INVESTMENT_MATURED");

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
