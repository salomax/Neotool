'use client';

import type { SxProps, Theme } from '@mui/material';
import {
  FUND_GROUP_KEYS,
  type AssetGroupKey,
} from './investmentTableHelpers';

/** Groups rendered by `FundHoldingsTable`, `VariableIncomeHoldingsTable`, or `StandardHoldingsTable`. */
export type NonFixedIncomeAssetGroupKey = Exclude<AssetGroupKey, 'fixedIncome'>;
export const NON_FIXED_INCOME_ACTION_COL_PX = 156;

const ROW_RESERVED_PX = 52;
const FLEXIBLE_ASSET_MIN_WIDTH_PX = 240;
const FLEXIBLE_GROSS_VALUE_COL_WIDTH_PX = 170;
const FLEXIBLE_GAIN_COL_WIDTH_PX = 160;

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
  const colWidth = `calc((100% - ${ROW_RESERVED_PX}px) / ${dataColumnCount})`;
  const actionColWidth = NON_FIXED_INCOME_ACTION_COL_PX;
  const isFlexibleAssetLayout = isFundGroup;

  return {
    actionColWidth,
    colWidth,
    isFundGroup,
    isVariableIncomeGroup,
    assetColSx: isFlexibleAssetLayout
      ? { flex: '1 1 0', minWidth: FLEXIBLE_ASSET_MIN_WIDTH_PX, pr: 1 }
      : { width: colWidth, flexShrink: 0 },
    maturityColSx: { width: colWidth },
    grossValueColSx: isFlexibleAssetLayout
      ? { width: FLEXIBLE_GROSS_VALUE_COL_WIDTH_PX, flexShrink: 0 }
      : { width: colWidth },
    gainColSx: isFlexibleAssetLayout ? { width: FLEXIBLE_GAIN_COL_WIDTH_PX, flexShrink: 0 } : { width: colWidth },
  };
}
