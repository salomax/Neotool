"use client";

import React from "react";
import { Box } from "@mui/material";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { InvestmentGridCardMatrix } from "./InvestmentGridCardMatrix";
import { InvestmentGridDetailPanel } from "./InvestmentGridDetailPanel";
import { InvestmentGridDrawers } from "./InvestmentGridDrawers";
import { InvestmentGridSkeleton } from "./InvestmentGridSkeleton";
import { useInvestmentGrid } from "./useInvestmentGrid";

interface InvestmentGridProps {
  selectedAccountId: string | null;
  bankAccounts: NonNullable<FinancialAccountsQuery["financialAccounts"]>;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGrid({
  selectedAccountId,
  bankAccounts,
  investmentAccounts,
  searchQuery,
  onSelectSearchAsset,
}: InvestmentGridProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const grid = useInvestmentGrid(selectedAccountId);

  if (grid.loading && !grid.data) {
    return <InvestmentGridSkeleton />;
  }

  if (grid.error && !grid.data) {
    return (
      <Box sx={{ mt: 2 }}>
        <ErrorAlert
          error={grid.error}
          fallbackMessage={t("investments.grid.loadError.description")}
          onRetry={() => void grid.refetch()}
        />
      </Box>
    );
  }

  return (
    <Box>
      {grid.error ? (
        <ErrorAlert
          error={grid.error}
          fallbackMessage={t("investments.grid.loadError.description")}
          onRetry={() => void grid.refetch()}
        />
      ) : null}

      {!grid.selectedAssetClassKey ? (
        <InvestmentGridCardMatrix
          metricsByKey={grid.metricsByKey}
          assetCountByKey={grid.assetCountByKey}
          chartSeriesByKey={grid.chartSeriesByKey}
          onOpenClass={grid.selectAssetClass}
          onOpenRecordDrawerForClass={grid.openRecordDrawerForClass}
        />
      ) : (
        <InvestmentGridDetailPanel
          selectedAssetClassKey={grid.selectedAssetClassKey}
          onClassChange={grid.selectAssetClass}
          onBackToGrid={grid.clearAssetClass}
          onOpenRecordDrawerForClass={grid.openRecordDrawerForClass}
          selectedAccountId={selectedAccountId}
          investmentAccounts={investmentAccounts}
          searchQuery={searchQuery}
          onSelectSearchAsset={onSelectSearchAsset}
        />
      )}

      <InvestmentGridDrawers
        recordDrawer={grid.recordDrawer}
        equityRecordVariant={grid.equityRecordVariant}
        onCloseRecordDrawer={grid.closeRecordDrawer}
        selectedAccountId={selectedAccountId}
        bankAccounts={bankAccounts}
        investmentAccounts={investmentAccounts}
        onRecorded={grid.refreshGridQueries}
      />
    </Box>
  );
}
