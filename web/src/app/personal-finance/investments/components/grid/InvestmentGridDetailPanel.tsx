"use client";

import React from "react";
import { Box, Fade } from "@mui/material";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import type { InvestmentGridClassKey } from "./investmentGridConfig";
import { InvestmentGridTabsPanel } from "./InvestmentGridTabsPanel";

export interface InvestmentGridDetailPanelProps {
  selectedAssetClassKey: InvestmentGridClassKey;
  onClassChange: (key: InvestmentGridClassKey) => void;
  onBackToGrid: () => void;
  onRecordCta: () => void;
  selectedAccountId: string | null;
  bankAccounts: NonNullable<FinancialAccountsQuery["financialAccounts"]>;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGridDetailPanel({
  selectedAssetClassKey,
  onClassChange,
  onBackToGrid,
  onRecordCta,
  selectedAccountId,
  bankAccounts,
  investmentAccounts,
  searchQuery,
  onSelectSearchAsset,
}: InvestmentGridDetailPanelProps) {
  return (
    <Fade in timeout={260}>
      <Box>
        <InvestmentGridTabsPanel
          selectedAssetClassKey={selectedAssetClassKey}
          onClassChange={onClassChange}
          onBackToGrid={onBackToGrid}
          onRecordCta={onRecordCta}
          selectedAccountId={selectedAccountId}
          bankAccounts={bankAccounts}
          investmentAccounts={investmentAccounts}
          searchQuery={searchQuery}
          onSelectSearchAsset={onSelectSearchAsset}
        />
      </Box>
    </Fade>
  );
}
