"use client";

import React from "react";
import { InvestmentGroupTable } from "./InvestmentGroupTable";
import type { AssetGroupKey, HoldingRow, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface HoldingsGroupsSectionProps {
  groups: InvestmentHoldingGroup[];
  expandedGroups: Set<AssetGroupKey>;
  hideGroupHeader: boolean;
  /** When null, portfolio is consolidated — fixed-income tables show the custodian column. */
  selectedAccountId: string | null;
  locale: string;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenMaturityConfirm: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onBuy?: (holding: HoldingRow) => void;
  onSell?: (holding: HoldingRow) => void;
}

export function HoldingsGroupsSection({
  groups,
  expandedGroups,
  hideGroupHeader,
  selectedAccountId,
  locale,
  onToggleGroup,
  onOpenDetail,
  onOpenMaturityConfirm,
  onOpenActions,
  onBuy,
  onSell,
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
          onOpenDetail={onOpenDetail}
          onOpenMaturityConfirm={onOpenMaturityConfirm}
          onOpenActions={onOpenActions}
          onBuy={onBuy}
          onSell={onSell}
          hideGroupHeader={hideGroupHeader}
        />
      ))}
    </>
  );
}
