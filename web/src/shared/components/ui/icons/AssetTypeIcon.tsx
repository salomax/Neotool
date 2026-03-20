"use client";

import React from "react";
import ApartmentIcon from "@mui/icons-material/Apartment";
import CandlestickChartIcon from "@mui/icons-material/CandlestickChart";
import CurrencyBitcoinIcon from "@mui/icons-material/CurrencyBitcoin";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import SavingsIcon from "@mui/icons-material/Savings";
import ShowChartIcon from "@mui/icons-material/ShowChart";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import type { SvgIconProps } from "@mui/material/SvgIcon";

export type AssetTypeIconKind =
  | "FIXED_INCOME"
  | "FUND"
  | "STOCK"
  | "CRYPTO"
  | "REAL_ESTATE_FUND"
  | "OPTION"
  | "DERIVATIVES"
  | "STRUCTURED"
  | "PENSION"
  | "OTHER";

const ICON_BY_KIND: Record<AssetTypeIconKind, React.ComponentType<SvgIconProps>> = {
  FIXED_INCOME: SavingsIcon,
  FUND: TrendingUpIcon,
  STOCK: CandlestickChartIcon,
  CRYPTO: CurrencyBitcoinIcon,
  REAL_ESTATE_FUND: ApartmentIcon,
  OPTION: ShowChartIcon,
  DERIVATIVES: ShowChartIcon,
  STRUCTURED: ShowChartIcon,
  PENSION: SavingsIcon,
  OTHER: HelpOutlineIcon,
};

const FIXED_INCOME_ASSET_TYPES = new Set([
  "CDB",
  "RDB",
  "LCI",
  "LCA",
  "LC",
  "LF",
  "LIG",
  "DEBENTURES",
  "CRI",
  "CRA",
  "TREASURY",
]);

const FUND_ASSET_TYPES = new Set([
  "MUTUAL_FUND",
  "FIXED_INCOME_FUND",
  "EQUITY_FUND",
  "MULTI_MARKET_FUND",
  "FOREX_FUND",
]);

const STOCK_ASSET_TYPES = new Set([
  "STOCK",
  "STOCK_RIGHTS",
  "BDR",
  "ETF",
  "ETF_FIXED_INCOME",
  "ETF_INTERNATIONAL",
]);

export function getAssetTypeIconKindFromAssetType(assetType: string | null | undefined): AssetTypeIconKind {
  if (!assetType) return "OTHER";
  const normalized = String(assetType);

  if (FIXED_INCOME_ASSET_TYPES.has(normalized)) return "FIXED_INCOME";
  if (FUND_ASSET_TYPES.has(normalized)) return "FUND";
  if (STOCK_ASSET_TYPES.has(normalized)) return "STOCK";
  if (normalized === "CRYPTO") return "CRYPTO";
  if (normalized === "REAL_ESTATE_FUND" || normalized === "AGRIBUSINESS_FUND") return "REAL_ESTATE_FUND";
  if (normalized === "OPTION") return "OPTION";
  if (normalized === "DERIVATIVES") return "DERIVATIVES";
  if (normalized === "COE") return "STRUCTURED";
  if (normalized === "PGBL" || normalized === "VGBL") return "PENSION";
  return "OTHER";
}

export function AssetTypeIcon({
  kind,
  assetType,
  ...props
}: { kind?: AssetTypeIconKind; assetType?: string | null | undefined } & SvgIconProps) {
  const resolvedKind = kind ?? getAssetTypeIconKindFromAssetType(assetType);
  const Icon = ICON_BY_KIND[resolvedKind] ?? HelpOutlineIcon;
  return <Icon {...props} />;
}
