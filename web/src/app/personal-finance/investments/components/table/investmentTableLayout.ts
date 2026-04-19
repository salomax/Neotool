'use client';

import type { SxProps, Theme } from '@mui/material';
import {
  FUND_GROUP_KEYS,
  type AssetGroupKey,
} from './investmentTableHelpers';

/** Groups rendered by `FundHoldingsTable`, `VariableIncomeHoldingsTable`, or `StandardHoldingsTable`. */
export type NonFixedIncomeAssetGroupKey = Exclude<AssetGroupKey, 'fixedIncome'>;
export const NON_FIXED_INCOME_ACTION_COL_PX = 156;

const FLEXIBLE_ASSET_MIN_WIDTH_PX = 240;
const FLEXIBLE_GROSS_VALUE_COL_WIDTH_PX = 170;
const FLEXIBLE_GAIN_COL_WIDTH_PX = 160;
const VARIABLE_INCOME_DATA_COL_PX = 144;

const fixedBase = (px: number): SxProps<Theme> => ({
  flex: `0 0 ${px}px`,
  width: px,
  maxWidth: px,
  flexShrink: 0,
  minWidth: 0,
});

export interface InvestmentGroupLayout {
  actionColWidth: number;
  colWidth: string;
  assetColSx: SxProps<Theme>;
  maturityColSx: SxProps<Theme>;
  grossValueColSx: SxProps<Theme>;
  gainColSx: SxProps<Theme>;
  isFundGroup: boolean;
  isVariableIncomeGroup: boolean;
}

export function buildInvestmentGroupLayout(groupKey: NonFixedIncomeAssetGroupKey): InvestmentGroupLayout {
  const isFundGroup = FUND_GROUP_KEYS.has(groupKey);
  const isVariableIncomeGroup = groupKey === 'variableIncome';
  const dataColumnCount = isFundGroup ? 3 : isVariableIncomeGroup ? 6 : 5;
  const actionColWidth = NON_FIXED_INCOME_ACTION_COL_PX;
  const colWidth = `calc((100% - ${actionColWidth}px) / ${dataColumnCount})`;
  const isFlexibleAssetLayout = isFundGroup;

  if (isVariableIncomeGroup) {
    const eq = fixedBase(VARIABLE_INCOME_DATA_COL_PX);
    return {
      actionColWidth,
      colWidth,
      isFundGroup,
      isVariableIncomeGroup,
      assetColSx: { flex: '1 1 auto', minWidth: 0, pr: 1 },
      maturityColSx: eq,
      gainColSx: { ...eq, textAlign: 'right' },
      grossValueColSx: { ...eq, textAlign: 'right' },
    };
  }

  const baseColSx = { width: colWidth, flexShrink: 0 } as const;

  return {
    actionColWidth,
    colWidth,
    isFundGroup,
    isVariableIncomeGroup,
    assetColSx: isFlexibleAssetLayout
      ? { flex: '1 1 auto', minWidth: FLEXIBLE_ASSET_MIN_WIDTH_PX, pr: 1 }
      : { width: colWidth, flexShrink: 0 },
    maturityColSx: baseColSx,
    grossValueColSx: isFundGroup
      ? { ...fixedBase(FLEXIBLE_GROSS_VALUE_COL_WIDTH_PX), textAlign: 'right' }
      : baseColSx,
    gainColSx: isFundGroup ? { ...fixedBase(FLEXIBLE_GAIN_COL_WIDTH_PX), textAlign: 'right' } : baseColSx,
  };
}
