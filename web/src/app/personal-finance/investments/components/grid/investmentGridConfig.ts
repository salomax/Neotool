import { AssetType } from "@/lib/graphql/types/__generated__/graphql";
import type { InvestmentIconKind } from "../InvestmentAssetIcon";
import type { AssetGroupKey } from "../table/investmentTableHelpers";

export const INVESTMENT_GRID_CLASS_KEYS = [
  "cash",
  "fixed-income",
  "funds",
  "stocks",
  "reits",
  "crypto",
] as const;

export type InvestmentGridClassKey = (typeof INVESTMENT_GRID_CLASS_KEYS)[number];

export type GridRecordDrawer = "cash" | "fixedIncome" | "fund" | "equity" | null;

/** Maps each grid class to the record drawer used for the primary "+ Investimento" CTA. */
export const RECORD_DRAWER_BY_CLASS: Record<InvestmentGridClassKey, Exclude<GridRecordDrawer, null>> = {
  cash: "cash",
  "fixed-income": "fixedIncome",
  funds: "fund",
  stocks: "equity",
  reits: "equity",
  crypto: "equity",
};

export interface InvestmentGridClassDefinition {
  key: InvestmentGridClassKey;
  iconKind: InvestmentIconKind;
  /** i18n key (personal-finance domain) */
  titleKey: string;
  primaryActionKey: string;
  secondaryActionKey: string;
  visibleGroupKeys?: AssetGroupKey[];
  visibleAssetTypes?: AssetType[];
  includeCashCard?: boolean;
}

export const INVESTMENT_GRID_CLASS_DEFINITIONS: readonly InvestmentGridClassDefinition[] = [
  {
    key: "cash",
    iconKind: "OTHER",
    titleKey: "investments.grid.classes.cash",
    primaryActionKey: "investments.grid.actions.contributeCash",
    secondaryActionKey: "investments.transactions.redeem",
    includeCashCard: true,
    visibleGroupKeys: [],
  },
  {
    key: "fixed-income",
    iconKind: "FIXED_INCOME",
    titleKey: "investments.grid.classes.fixedIncome",
    primaryActionKey: "investments.transactions.apply",
    secondaryActionKey: "investments.transactions.redeem",
    includeCashCard: false,
    visibleGroupKeys: ["bankFixedIncome", "creditFixedIncome"],
  },
  {
    key: "funds",
    iconKind: "FUND",
    titleKey: "investments.grid.classes.funds",
    primaryActionKey: "investments.transactions.apply",
    secondaryActionKey: "investments.transactions.redeem",
    includeCashCard: false,
    visibleGroupKeys: ["mutualFunds", "pension"],
  },
  {
    key: "stocks",
    iconKind: "STOCK",
    titleKey: "investments.grid.classes.stocks",
    primaryActionKey: "investments.transactions.buy",
    secondaryActionKey: "investments.transactions.sell",
    includeCashCard: false,
    visibleAssetTypes: [
      AssetType.Stock,
      AssetType.StockRights,
      AssetType.Bdr,
      AssetType.Etf,
      AssetType.EtfInternational,
    ],
  },
  {
    key: "reits",
    iconKind: "REAL_ESTATE_FUND",
    titleKey: "investments.grid.classes.reits",
    primaryActionKey: "investments.transactions.buy",
    secondaryActionKey: "investments.transactions.sell",
    includeCashCard: false,
    visibleAssetTypes: [AssetType.RealEstateFund, AssetType.AgribusinessFund],
  },
  {
    key: "crypto",
    iconKind: "CRYPTO",
    titleKey: "investments.grid.classes.crypto",
    primaryActionKey: "investments.transactions.buy",
    secondaryActionKey: "investments.transactions.sell",
    includeCashCard: false,
    visibleAssetTypes: [AssetType.Crypto],
  },
];
