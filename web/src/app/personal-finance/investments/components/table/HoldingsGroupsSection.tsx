"use client";

import React from "react";
import { InvestmentGroupTable } from "./InvestmentGroupTable";
import type { AssetGroupKey, HoldingRow, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface HoldingsGroupsSectionProps {
  groups: InvestmentHoldingGroup[];
  expandedGroups: Set<AssetGroupKey>;
  hideGroupHeader: boolean;
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
  locale,
  onToggleGroup,
  onOpenDetail,
  onOpenMaturityConfirm,
  onOpenActions,
  onBuy,
  onSell,
}: HoldingsGroupsSectionProps) {
  return (
    <>
      {groups.map((group, index) => (
        <InvestmentGroupTable
          key={group.key}
          group={group}
          expanded={hideGroupHeader ? true : expandedGroups.has(group.key)}
          locale={locale}
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
