"use client";

import type { SxProps, Theme } from "@mui/material";

/** Fixed width for indexer / custodian / maturity / gain / gross (gain & gross use `equalNumericColSx`). */
export const FIXED_INCOME_DATA_COL_PX = 144;

/** Narrow column: overflow menu (+ quick actions on hover). */
export const FIXED_INCOME_ACTION_COL_PX = 132;

export interface FixedIncomeTableLayout {
  actionColWidth: number;
  /** Ativo — grows to fill remaining row width */
  assetColSx: SxProps<Theme>;
  showConsolidatedCustodianColumn: boolean;
  custodianColSx: SxProps<Theme>;
  indexerColSx: SxProps<Theme>;
  maturityColSx: SxProps<Theme>;
  gainColSx: SxProps<Theme>;
  grossValueColSx: SxProps<Theme>;
}

const equalBase = (px: number): SxProps<Theme> => ({
  flex: `0 0 ${px}px`,
  width: px,
  maxWidth: px,
  flexShrink: 0,
  minWidth: 0,
});

export function buildFixedIncomeTableLayout(showConsolidatedCustodianColumn: boolean): FixedIncomeTableLayout {
  const px = FIXED_INCOME_DATA_COL_PX;
  const eq = equalBase(px);
  return {
    actionColWidth: FIXED_INCOME_ACTION_COL_PX,
    showConsolidatedCustodianColumn,
    assetColSx: {
      flex: "1 1 auto",
      minWidth: 0,
      pr: 1,
    },
    custodianColSx: eq,
    indexerColSx: eq,
    maturityColSx: eq,
    gainColSx: { ...eq, textAlign: "right" },
    grossValueColSx: { ...eq, textAlign: "right" },
  };
}
