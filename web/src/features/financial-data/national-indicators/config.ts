export const CATEGORY_MAPPING: Record<string, string[]> = {
  inflation: ["IPCA", "IPCA_15", "INPC", "IGP_M", "IGP_DI", "IGP_10"],
  interest: ["SELIC", "CDI", "DI_OVER"],
  activity: ["GDP", "UNEMPLOYMENT"],
  income: ["AVERAGE_INCOME"],
  external: ["TRADE_BALANCE", "CURRENT_ACCOUNT", "INTERNATIONAL_RESERVES"],
  fiscal: ["PUBLIC_DEBT_GDP", "PRIMARY_RESULT"],
};

export const CATEGORIES = Object.keys(CATEGORY_MAPPING);
