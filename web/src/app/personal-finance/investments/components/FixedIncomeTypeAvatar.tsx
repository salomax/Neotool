"use client";

import * as React from "react";
import { Avatar, type Theme } from "@mui/material";
import { useTheme } from "@mui/material/styles";

/** Short labels shown inside the type avatar (max ~3 chars for layout). */
const AVATAR_LABEL_BY_TYPE: Record<string, string> = {
  CDB: "CDB",
  RDB: "RDB",
  LCI: "LCI",
  LCA: "LCA",
  LC: "LC",
  LF: "LF",
  LIG: "LIG",
  ETF_FIXED_INCOME: "ETF",
  TREASURY: "TES",
  DEBENTURES: "DEB",
  CRI: "CRI",
  CRA: "CRA",
};

export function fixedIncomeTypeAvatarLabel(assetType: string | null | undefined): string {
  if (!assetType) return "?";
  if (AVATAR_LABEL_BY_TYPE[assetType]) return AVATAR_LABEL_BY_TYPE[assetType];
  const compact = assetType.replace(/_/g, "");
  return compact.slice(0, 3).toUpperCase() || "?";
}

export function getFixedIncomeTypeAvatarColor(theme: Theme, assetType: string | null | undefined): string {
  const map = theme.custom.fixedIncomeTypeColors;
  const fallback = map.DEFAULT ?? "#5C6BC0";
  if (!assetType) return fallback;
  return map[assetType] ?? fallback;
}

export interface FixedIncomeTypeAvatarProps {
  assetType: string | null | undefined;
  size?: number | "auto";
  "data-testid"?: string;
}

/**
 * Fixed-income row avatar: colored tile by asset type (CDB, LCI, …), not custodian logo.
 */
export function FixedIncomeTypeAvatar({
  assetType,
  size = 32,
  "data-testid": dataTestId,
}: FixedIncomeTypeAvatarProps) {
  const theme = useTheme();
  const bg = getFixedIncomeTypeAvatarColor(theme, assetType);
  const label = fixedIncomeTypeAvatarLabel(assetType);

  return (
    <Avatar
      variant="rounded"
      data-testid={dataTestId}
      sx={{
        width: size === "auto" ? "auto" : size,
        height: size === "auto" ? "auto" : size,
        flexShrink: 0,
        bgcolor: bg,
        color: theme.palette.common.white,
        fontSize: label.length <= 3 ? "0.7rem" : "0.6rem",
        fontWeight: 700,
        borderRadius: "0.3rem",
      }}
    >
      {label}
    </Avatar>
  );
}
