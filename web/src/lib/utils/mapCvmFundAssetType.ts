import { AssetType } from "@/lib/graphql/types/__generated__/graphql";

export type CvmFundAssetType =
  | "FIXED_INCOME_FUND"
  | "EQUITY_FUND"
  | "MULTI_MARKET_FUND"
  | "FOREX_FUND"
  | "MUTUAL_FUND"
  | "PGBL"
  | "VGBL"
  | (string & {});

export function mapCvmFundAssetType(assetType: CvmFundAssetType): AssetType {
  switch (assetType) {
    case "PGBL":
      return AssetType.Pgbl;
    case "VGBL":
      return AssetType.Vgbl;
    case "FIXED_INCOME_FUND":
    case "EQUITY_FUND":
    case "MULTI_MARKET_FUND":
    case "FOREX_FUND":
    case "MUTUAL_FUND":
    default:
      return AssetType.MutualFund;
  }
}

export function formatFundAssetType(
  assetType: CvmFundAssetType,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  return t(`investments.funds.assetTypes.${String(assetType)}` as string);
}

