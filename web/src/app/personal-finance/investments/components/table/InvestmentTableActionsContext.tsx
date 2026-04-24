"use client";

import React from "react";
import type { HoldingRow } from "./investmentTableHelpers";

export interface InvestmentTableActions {
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenMaturityConfirm: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onBuy: (holding: HoldingRow) => void;
  onSell: (holding: HoldingRow) => void;
}

const InvestmentTableActionsContext = React.createContext<InvestmentTableActions | null>(null);

export function InvestmentTableActionsProvider({
  children,
  actions,
}: {
  children: React.ReactNode;
  actions: InvestmentTableActions;
}) {
  return <InvestmentTableActionsContext.Provider value={actions}>{children}</InvestmentTableActionsContext.Provider>;
}

export function useInvestmentTableActions(): InvestmentTableActions {
  const context = React.useContext(InvestmentTableActionsContext);
  if (!context) throw new Error("useInvestmentTableActions must be used within InvestmentTableActionsProvider");
  return context;
}
