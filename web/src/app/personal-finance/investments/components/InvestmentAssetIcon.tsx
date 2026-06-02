"use client";

import React from "react";
import type { SvgIconProps } from "@mui/material/SvgIcon";
import {
  AssetTypeIcon,
  type AssetTypeIconKind,
  getAssetTypeIconKindFromAssetType,
} from "@/shared/components/ui/icons/AssetTypeIcon";

export type InvestmentIconKind = AssetTypeIconKind;

export const getInvestmentIconKindFromAssetType = getAssetTypeIconKindFromAssetType;

export function InvestmentAssetIcon({ kind, ...props }: { kind: InvestmentIconKind } & SvgIconProps) {
  return <AssetTypeIcon kind={kind} {...props} />;
}
