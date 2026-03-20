import { AssetType } from "@/lib/graphql/types/__generated__/graphql";

export type B3AssetType =
  | "EQUITY"
  | "FII"
  | "OPTION_CALL"
  | "OPTION_PUT"
  | (string & {});

/**
 * Maps a B3 asset_type string (from financial-data) to the banking AssetType enum.
 */
export function mapB3AssetType(assetType: B3AssetType): AssetType {
  switch (assetType) {
    case "FII":
      return AssetType.RealEstateFund;
    case "OPTION_CALL":
    case "OPTION_PUT":
      return AssetType.Option;
    case "EQUITY":
    default:
      return AssetType.Stock;
  }
}

/**
 * Returns a human-readable label for a B3 asset type.
 */
export function formatB3AssetType(
  assetType: B3AssetType,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  return t(`investments.b3AssetTypes.${String(assetType)}` as string, {
    defaultValue: String(assetType).replaceAll("_", " "),
  });
}
