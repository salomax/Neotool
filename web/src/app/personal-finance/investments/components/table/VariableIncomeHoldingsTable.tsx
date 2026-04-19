"use client";

import React from "react";
import { alpha, useTheme } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { AssetTypeIcon } from "@/shared/components/ui/icons/AssetTypeIcon";
import { personalFinanceTranslations } from "../../../i18n";
import { VariableIncomeHoldingRow } from "./VariableIncomeHoldingRow";
import { buildInvestmentGroupLayout } from "./investmentTableLayout";
import { InvestmentGroupTableShell } from "./InvestmentGroupTableShell";
import type { HoldingRow, AssetGroupKey, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface VariableIncomeHoldingsTableProps {
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

export const VariableIncomeHoldingsTable = React.memo(function VariableIncomeHoldingsTable({
  group,
  expanded,
  locale,
  onToggleGroup,
  onOpenDetail,
  onOpenActions,
  onBuy,
  onSell,
  hideGroupHeader = false,
}: VariableIncomeHoldingsTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const layout = React.useMemo(() => buildInvestmentGroupLayout("variableIncome"), []);
  const dividerColor = React.useMemo(() => alpha(theme.palette.divider, 0.7), [theme.palette.divider]);
  const hoverColor = React.useMemo(() => alpha(theme.palette.primary.main, 0.035), [theme.palette.primary.main]);
  const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;

  const buyLabel = t("investments.transactions.buy");
  const sellLabel = t("investments.transactions.sell");

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
        key: "quantity",
        align: "right" as const,
        sx: layout.maturityColSx,
        content: <Label>{t("investments.table.columns.quantity")}</Label>,
      },
      {
        key: "averagePrice",
        align: "right" as const,
        sx: layout.maturityColSx,
        content: <Label>{t("investments.table.columns.averagePrice")}</Label>,
      },
      {
        key: "latestPrice",
        align: "right" as const,
        sx: layout.maturityColSx,
        content: <Label>{t("investments.table.columns.latestPrice")}</Label>,
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

  return (
    <InvestmentGroupTableShell
      hideGroupHeader={hideGroupHeader}
      group={group}
      expanded={expanded}
      onToggleGroup={onToggleGroup}
      title={group.title}
      summary={group.summary}
      leadingIcon={<AssetTypeIcon kind="STOCK" sx={{ fontSize: 16 }} />}
      summaryAdornment={summaryAdornment}
      gainPercent={group.gainPercent}
      allocationPercent={group.allocationPercent}
      headerCells={headerCells}
      actionColWidth={layout.actionColWidth}
      tableRadius={tableRadius}
    >
      {group.holdings.map((holding) => (
        <VariableIncomeHoldingRow
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
