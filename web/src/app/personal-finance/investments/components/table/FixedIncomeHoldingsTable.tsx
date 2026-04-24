"use client";

import React from "react";
import { alpha, useTheme } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { AssetTypeIcon } from "@/shared/components/ui/icons/AssetTypeIcon";
import { personalFinanceTranslations } from "../../../i18n";
import { FixedIncomeHoldingRow } from "./FixedIncomeHoldingRow";
import { buildFixedIncomeTableLayout } from "./fixedIncomeTableLayout";
import { InvestmentGroupTableShell } from "./InvestmentGroupTableShell";
import { getTableRadius } from "./themeUtils";
import type { AssetGroupKey, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface FixedIncomeHoldingsTableProps {
  group: InvestmentHoldingGroup;
  expanded: boolean;
  locale: string;
  showConsolidatedCustodianColumn?: boolean;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  hideGroupHeader?: boolean;
}

export const FixedIncomeHoldingsTable = React.memo(function FixedIncomeHoldingsTable({
  group,
  expanded,
  locale,
  showConsolidatedCustodianColumn = false,
  onToggleGroup,
  hideGroupHeader = false,
}: FixedIncomeHoldingsTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const layout = React.useMemo(
    () => buildFixedIncomeTableLayout(showConsolidatedCustodianColumn),
    [showConsolidatedCustodianColumn],
  );
  const dividerColor = React.useMemo(() => alpha(theme.palette.divider, 0.7), [theme.palette.divider]);
  const hoverColor = React.useMemo(() => alpha(theme.palette.primary.main, 0.035), [theme.palette.primary.main]);
  const tableRadius = getTableRadius(theme);

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
      ...(layout.showConsolidatedCustodianColumn
        ? [
            {
              key: "custodian",
              sx: layout.custodianColSx,
              content: <Label>{t("investments.cash.movements.columns.financialAccount")}</Label>,
            },
          ]
        : []),
      {
        key: "indexer",
        sx: layout.indexerColSx,
        content: <Label>{t("investments.table.columns.indexer")}</Label>,
      },
      {
        key: "maturityDate",
        align: "center" as const,
        sx: layout.maturityColSx,
        content: <Label>{t("investments.table.columns.maturityDate")}</Label>,
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
      leadingIcon={<AssetTypeIcon kind="FIXED_INCOME" sx={{ fontSize: 16 }} />}
      summaryAdornment={summaryAdornment}
      gainPercent={group.gainPercent}
      allocationPercent={group.allocationPercent}
      headerCells={headerCells}
      actionColWidth={layout.actionColWidth}
      tableRadius={tableRadius}
    >
      {group.holdings.map((holding) => (
        <FixedIncomeHoldingRow
          key={holding.id}
          holding={holding}
          layout={layout}
          locale={locale}
          dividerColor={dividerColor}
          hoverColor={hoverColor}
          buyLabel={buyLabel}
          sellLabel={sellLabel}
        />
      ))}
    </InvestmentGroupTableShell>
  );
});
