"use client";

import React from "react";
import { Box, Fade } from "@mui/material";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import type { InvestmentGridClassKey } from "./investmentGridConfig";
import { InvestmentGridTabsPanel } from "./InvestmentGridTabsPanel";

export interface InvestmentGridDetailPanelProps {
  selectedAssetClassKey: InvestmentGridClassKey;
  onClassChange: (key: InvestmentGridClassKey) => void;
  onBackToGrid: () => void;
  onOpenRecordDrawerForClass: (key: InvestmentGridClassKey) => void;
  selectedAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGridDetailPanel({
  selectedAssetClassKey,
  onClassChange,
  onBackToGrid,
  onOpenRecordDrawerForClass,
  selectedAccountId,
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
          onOpenRecordDrawerForClass={onOpenRecordDrawerForClass}
          selectedAccountId={selectedAccountId}
          investmentAccounts={investmentAccounts}
          searchQuery={searchQuery}
          onSelectSearchAsset={onSelectSearchAsset}
        />
      </Box>
    </Fade>
  );
}
