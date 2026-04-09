'use client';

import type { SxProps, Theme } from '@mui/material';
import {
  FIXED_INCOME_GROUP_KEYS,
  FUND_GROUP_KEYS,
  type AssetGroupKey,
} from './investmentTableHelpers';

export interface InvestmentGroupLayout {
  actionColWidth: number;
  colWidth: string;
  assetColSx: SxProps<Theme>;
  maturityColSx: SxProps<Theme>;
  grossValueColSx: SxProps<Theme>;
  gainColSx: SxProps<Theme>;
  indexerColSx: SxProps<Theme>;
  isFixedIncomeGroup: boolean;
  isFundGroup: boolean;
  isVariableIncomeGroup: boolean;
}

export function buildInvestmentGroupLayout(groupKey: AssetGroupKey): InvestmentGroupLayout {
  const isFundGroup = FUND_GROUP_KEYS.has(groupKey);
  const isFixedIncomeGroup = FIXED_INCOME_GROUP_KEYS.has(groupKey);
  const isVariableIncomeGroup = groupKey === 'variableIncome';
  const dataColumnCount = isFundGroup ? 3 : isFixedIncomeGroup ? 4 : isVariableIncomeGroup ? 6 : 5;
  const colWidth = `calc((100% - 52px) / ${dataColumnCount})`;
  /** Icon quick actions (apply/redeem) + ⋮; narrower than former text buttons */
  const actionColWidth = 152;
  const isFlexibleAssetLayout = isFixedIncomeGroup || isFundGroup;

  return {
    actionColWidth,
    colWidth,
    isFixedIncomeGroup,
    isFundGroup,
    isVariableIncomeGroup,
    assetColSx: isFlexibleAssetLayout ? { flex: '1 1 0', minWidth: 240, pr: 1 } : { width: colWidth, flexShrink: 0 },
    maturityColSx: isFixedIncomeGroup ? { width: 140, flexShrink: 0 } : { width: colWidth },
    grossValueColSx: isFlexibleAssetLayout ? { width: 170, flexShrink: 0 } : { width: colWidth },
    gainColSx: isFlexibleAssetLayout ? { width: 160, flexShrink: 0 } : { width: colWidth },
    indexerColSx: isFixedIncomeGroup ? { width: 160, flexShrink: 0 } : { width: colWidth },
  };
}
