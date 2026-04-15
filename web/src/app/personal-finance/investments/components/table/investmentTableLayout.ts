'use client';

import type { SxProps, Theme } from '@mui/material';
import {
  FUND_GROUP_KEYS,
  type AssetGroupKey,
} from './investmentTableHelpers';

/** Groups rendered by `FundHoldingsTable`, `VariableIncomeHoldingsTable`, or `StandardHoldingsTable`. */
export type NonFixedIncomeAssetGroupKey = Exclude<AssetGroupKey, 'fixedIncome'>;

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
  const colWidth = `calc((100% - 52px) / ${dataColumnCount})`;
  const actionColWidth = 120;
  const isFlexibleAssetLayout = isFundGroup;

  return {
    actionColWidth,
    colWidth,
    isFundGroup,
    isVariableIncomeGroup,
    assetColSx: isFlexibleAssetLayout ? { flex: '1 1 0', minWidth: 240, pr: 1 } : { width: colWidth, flexShrink: 0 },
    maturityColSx: { width: colWidth },
    grossValueColSx: isFlexibleAssetLayout ? { width: 170, flexShrink: 0 } : { width: colWidth },
    gainColSx: isFlexibleAssetLayout ? { width: 160, flexShrink: 0 } : { width: colWidth },
  };
}
