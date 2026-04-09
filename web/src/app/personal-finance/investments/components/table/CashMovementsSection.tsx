"use client";

import React from "react";
import { Box } from "@mui/material";
import { CashTransactionsInlineTable } from "../CashTransactionsInlineTable";
import type { IssuerSelectionAccount } from "../IssuerSelection";

export interface CashMovementsSectionProps {
  financialAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  onSelectRelatedAsset?: (assetName: string) => void;
  bottomSpacing?: number;
}

/** Same outer chrome as finance TransactionTable: no outer card — only row cells use `background.paper`. */
export function CashMovementsSection({
  financialAccountId,
  investmentAccounts,
  onSelectRelatedAsset,
  bottomSpacing = 0,
}: CashMovementsSectionProps) {
  return (
    <Box sx={{ mb: bottomSpacing, bgcolor: "transparent" }}>
      <CashTransactionsInlineTable
        financialAccountId={financialAccountId}
        investmentAccounts={investmentAccounts}
        onSelectRelatedAsset={onSelectRelatedAsset}
      />
    </Box>
  );
}
