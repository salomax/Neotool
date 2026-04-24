'use client';

import React from 'react';
import { FIXED_INCOME_GROUP_KEYS, FUND_GROUP_KEYS, type AssetGroupKey, type InvestmentHoldingGroup } from './investmentTableHelpers';
import { FixedIncomeHoldingsTable } from './FixedIncomeHoldingsTable';
import { FundHoldingsTable } from './FundHoldingsTable';
import { VariableIncomeHoldingsTable } from './VariableIncomeHoldingsTable';
import { StandardHoldingsTable } from './StandardHoldingsTable';

export interface InvestmentGroupTableProps {
  group: InvestmentHoldingGroup;
  expanded: boolean;
  locale: string;
  showConsolidatedCustodianColumn?: boolean;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  hideGroupHeader?: boolean;
}

export const InvestmentGroupTable = React.memo(function InvestmentGroupTable({
  group,
  expanded,
  locale,
  showConsolidatedCustodianColumn = false,
  onToggleGroup,
  hideGroupHeader = false,
}: InvestmentGroupTableProps) {
  if (FIXED_INCOME_GROUP_KEYS.has(group.key)) {
    return (
      <FixedIncomeHoldingsTable
        group={group}
        expanded={expanded}
        locale={locale}
        showConsolidatedCustodianColumn={showConsolidatedCustodianColumn}
        onToggleGroup={onToggleGroup}
        hideGroupHeader={hideGroupHeader}
      />
    );
  }

  if (FUND_GROUP_KEYS.has(group.key)) {
    return (
      <FundHoldingsTable
        group={group}
        expanded={expanded}
        locale={locale}
        onToggleGroup={onToggleGroup}
        hideGroupHeader={hideGroupHeader}
      />
    );
  }

  if (group.key === 'variableIncome') {
    return (
      <VariableIncomeHoldingsTable
        group={group}
        expanded={expanded}
        locale={locale}
        showConsolidatedCustodianColumn={showConsolidatedCustodianColumn}
        onToggleGroup={onToggleGroup}
        hideGroupHeader={hideGroupHeader}
      />
    );
  }

  return (
    <StandardHoldingsTable
      group={group}
      expanded={expanded}
      locale={locale}
      onToggleGroup={onToggleGroup}
      hideGroupHeader={hideGroupHeader}
    />
  );
});
