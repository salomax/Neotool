"use client";

import React from "react";
import CandlestickChartIcon from "@mui/icons-material/CandlestickChart";
import CorporateFareIcon from "@mui/icons-material/CorporateFare";
import CurrencyBitcoinIcon from "@mui/icons-material/CurrencyBitcoin";
import Diversity3Icon from "@mui/icons-material/Diversity3";
import ElderlyIcon from "@mui/icons-material/Elderly";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import LanIcon from "@mui/icons-material/Lan";
import MobiledataOffIcon from "@mui/icons-material/MobiledataOff";
import PaidIcon from "@mui/icons-material/Paid";
import SavingsIcon from "@mui/icons-material/Savings";
import ShowChartIcon from "@mui/icons-material/ShowChart";
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
  FUND: Diversity3Icon,
  STOCK: CandlestickChartIcon,
  CRYPTO: CurrencyBitcoinIcon,
  REAL_ESTATE_FUND: CorporateFareIcon,
  OPTION: MobiledataOffIcon,
  DERIVATIVES: ShowChartIcon,
  STRUCTURED: LanIcon,
  PENSION: ElderlyIcon,
  OTHER: PaidIcon,
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
