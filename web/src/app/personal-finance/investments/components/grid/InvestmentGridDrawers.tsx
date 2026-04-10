"use client";

import React from "react";
import { RecordCashDrawer } from "../RecordCashDrawer";
import { RecordEquityHoldingDrawer } from "../RecordEquityHoldingDrawer";
import { RecordFixedIncomeDrawer } from "../RecordFixedIncomeDrawer";
import { RecordFundHoldingDrawer } from "../RecordFundHoldingDrawer";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { InvestmentTransactionDirection } from "@/lib/graphql/types/__generated__/graphql";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import type { GridRecordDrawer } from "./investmentGridConfig";

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
  return (
    <>
      <RecordFixedIncomeDrawer
        open={recordDrawer === "fixedIncome"}
        onClose={onCloseRecordDrawer}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
        onRecorded={onRecorded}
      />
      <RecordFundHoldingDrawer
        open={recordDrawer === "fund"}
        onClose={onCloseRecordDrawer}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
        initialDirection={InvestmentTransactionDirection.Inflow}
        onRecorded={onRecorded}
      />
      <RecordEquityHoldingDrawer
        open={recordDrawer === "equity"}
        onClose={onCloseRecordDrawer}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
        initialDirection={InvestmentTransactionDirection.Inflow}
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
