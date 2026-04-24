"use client";

import React from "react";
import { InvestmentGroupTable } from "./InvestmentGroupTable";
import type { AssetGroupKey, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface HoldingsGroupsSectionProps {
  groups: InvestmentHoldingGroup[];
  expandedGroups: Set<AssetGroupKey>;
  hideGroupHeader: boolean;
  /** When null, portfolio is consolidated — fixed-income tables show the custodian column. */
  selectedAccountId: string | null;
  locale: string;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
}

export function HoldingsGroupsSection({
  groups,
  expandedGroups,
  hideGroupHeader,
  selectedAccountId,
  locale,
  onToggleGroup,
}: HoldingsGroupsSectionProps) {
  const showConsolidatedCustodianColumn = selectedAccountId == null;

  return (
    <>
      {groups.map((group) => (
        <InvestmentGroupTable
          key={group.key}
          group={group}
          expanded={hideGroupHeader ? true : expandedGroups.has(group.key)}
          locale={locale}
          showConsolidatedCustodianColumn={showConsolidatedCustodianColumn}
          onToggleGroup={onToggleGroup}
          hideGroupHeader={hideGroupHeader}
        />
      ))}
    </>
  );
}
