"use client";

import React from "react";
import { RecordCashDrawer } from "../RecordCashDrawer";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import { TransactionEditor } from "../transaction-editor/TransactionEditor";
import type { GridRecordDrawer } from "./investmentGridConfig";
import type { InvestmentAssetClass } from "../transaction-editor/intent";

export interface InvestmentGridDrawersProps {
  recordDrawer: GridRecordDrawer;
  onCloseRecordDrawer: () => void;
  selectedAccountId: string | null;
  bankAccounts: NonNullable<FinancialAccountsQuery["financialAccounts"]>;
  investmentAccounts: IssuerSelectionAccount[];
  onRecorded: () => void | Promise<void>;
}

export function InvestmentGridDrawers({
  recordDrawer,
  onCloseRecordDrawer,
  selectedAccountId,
  bankAccounts,
  investmentAccounts,
  onRecorded,
}: InvestmentGridDrawersProps) {
  const editorIntent =
    recordDrawer && recordDrawer !== "cash"
      ? ({ kind: "new-buy", assetClass: recordDrawer as InvestmentAssetClass } as const)
      : null;

  return (
    <>
      <TransactionEditor
        open={Boolean(editorIntent)}
        intent={editorIntent}
        onClose={onCloseRecordDrawer}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
        onRecorded={onRecorded}
      />
      <RecordCashDrawer
        open={recordDrawer === "cash"}
        onClose={onCloseRecordDrawer}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
        bankAccounts={bankAccounts}
        listContextAccountId={selectedAccountId}
        onRecorded={onRecorded}
      />
    </>
  );
}
