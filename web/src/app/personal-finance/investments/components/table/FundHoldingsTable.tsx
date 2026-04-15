"use client";

import React from "react";
import { alpha, useTheme } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { AssetTypeIcon, type AssetTypeIconKind } from "@/shared/components/ui/icons/AssetTypeIcon";
import { personalFinanceTranslations } from "../../../i18n";
import { FundHoldingRow } from "./FundHoldingRow";
import { buildInvestmentGroupLayout, type NonFixedIncomeAssetGroupKey } from "./investmentTableLayout";
import { InvestmentGroupTableShell } from "./InvestmentGroupTableShell";
import type { HoldingRow, AssetGroupKey, InvestmentHoldingGroup } from "./investmentTableHelpers";

const ICON_KIND: Record<"mutualFunds" | "pension", AssetTypeIconKind> = {
  mutualFunds: "FUND",
  pension: "FUND",
};

export interface FundHoldingsTableProps {
  group: InvestmentHoldingGroup;
  expanded: boolean;
  locale: string;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onBuy?: (holding: HoldingRow) => void;
  onSell?: (holding: HoldingRow) => void;
  hideGroupHeader?: boolean;
}

export const FundHoldingsTable = React.memo(function FundHoldingsTable({
  group,
  expanded,
  locale,
  onToggleGroup,
  onOpenDetail,
  onOpenActions,
  onBuy,
  onSell,
  hideGroupHeader = false,
}: FundHoldingsTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const layout = React.useMemo(
    () => buildInvestmentGroupLayout(group.key as NonFixedIncomeAssetGroupKey),
    [group.key],
  );
  const dividerColor = React.useMemo(() => alpha(theme.palette.divider, 0.7), [theme.palette.divider]);
  const hoverColor = React.useMemo(() => alpha(theme.palette.primary.main, 0.035), [theme.palette.primary.main]);
  const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;

  const buyLabel = t("investments.transactions.apply");
  const sellLabel = t("investments.transactions.redeem");

  const summaryAdornment = React.useMemo(() => {
    if (!group.valueOriginKey || group.total === "—") return null;
    return (
      <ValueOriginIndicator
        origin={group.valueOriginKey}
        title={t(`investments.table.valueOrigin.labels.${group.valueOriginKey}`)}
        description={t(`investments.table.valueOrigin.descriptions.${group.valueOriginKey}`)}
        ariaLabel={t("investments.table.valueOrigin.ariaLabel")}
        offsetPx={18}
      />
    );
  }, [group.total, group.valueOriginKey, t]);

  const headerCells = React.useMemo(
    () => [
      {
        key: "asset",
        sx: layout.assetColSx,
        content: <Label>{t("investments.table.columns.asset")}</Label>,
      },
      {
        key: "gain",
        align: "right" as const,
        sx: layout.gainColSx,
        content: <Label>{t("investments.table.columns.gain")}</Label>,
      },
      {
        key: "grossValue",
        align: "right" as const,
        sx: layout.grossValueColSx,
        content: <Label>{t("investments.table.columns.grossValue")}</Label>,
      },
    ],
    [layout, t],
  );

  const iconKind = ICON_KIND[group.key as "mutualFunds" | "pension"];

  return (
    <InvestmentGroupTableShell
      hideGroupHeader={hideGroupHeader}
      group={group}
      expanded={expanded}
      onToggleGroup={onToggleGroup}
      title={group.title}
      summary={group.summary}
      leadingIcon={<AssetTypeIcon kind={iconKind} sx={{ fontSize: 16 }} />}
      summaryAdornment={summaryAdornment}
      gainPercent={group.gainPercent}
      allocationPercent={group.allocationPercent}
      headerCells={headerCells}
      actionColWidth={layout.actionColWidth}
      tableRadius={tableRadius}
    >
      {group.holdings.map((holding) => (
        <FundHoldingRow
          key={holding.id}
          holding={holding}
          layout={layout}
          locale={locale}
          dividerColor={dividerColor}
          hoverColor={hoverColor}
          t={t}
          onOpenDetail={onOpenDetail}
          onOpenActions={onOpenActions}
          onBuy={buyLabel ? onBuy : undefined}
          onSell={sellLabel ? onSell : undefined}
          buyLabel={buyLabel}
          sellLabel={sellLabel}
        />
      ))}
    </InvestmentGroupTableShell>
  );
});
