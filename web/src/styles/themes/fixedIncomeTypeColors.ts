import type { Mode } from "./tokens";

/**
 * Avatar background colors per GraphQL `AssetType` for fixed-income holdings.
 * Chosen for contrast with white label text in both light and dark app modes.
 */
export const fixedIncomeTypeColorsByMode: Record<Mode, Record<string, string>> = {
  light: {
    CDB: "#1565C0",
    RDB: "#0277BD",
    LCI: "#2E7D32",
    LCA: "#00695C",
    LC: "#283593",
    LF: "#6A1B9A",
    LIG: "#00838F",
    ETF_FIXED_INCOME: "#E65100",
    TREASURY: "#F9A825",
    DEBENTURES: "#C62828",
    CRI: "#388E3C",
    CRA: "#558B2F",
    DEFAULT: "#5C6BC0",
  },
  dark: {
    CDB: "#1976D2",
    RDB: "#0288D1",
    LCI: "#388E3C",
    LCA: "#00796B",
    LC: "#3949AB",
    LF: "#7B1FA2",
    LIG: "#0097A7",
    ETF_FIXED_INCOME: "#F57C00",
    TREASURY: "#FBC02D",
    DEBENTURES: "#D32F2F",
    CRI: "#43A047",
    CRA: "#689F38",
    DEFAULT: "#7986CB",
  },
};
