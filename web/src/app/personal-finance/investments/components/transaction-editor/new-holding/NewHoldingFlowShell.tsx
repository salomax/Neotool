"use client";

import * as React from "react";
import { Box } from "@mui/material";
import { InvestmentTransactionDirection } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../../i18n";
import type { IssuerSelectionAccount } from "../../IssuerSelection";
import { InvestmentRecordDrawer } from "../../InvestmentRecordDrawer";
import { RecordFundHoldingDrawer } from "../../RecordFundHoldingDrawer";
import { RecordEquityHoldingDrawer } from "../../RecordEquityHoldingDrawer";
import { RecordCryptoHoldingDrawer } from "../../RecordCryptoHoldingDrawer";
import type { NewBuyIntent, NewSellIntent } from "../intent";
import { NewHoldingAssetIdentity } from "./NewHoldingAssetIdentity";
import {
  newHoldingFormTitleKey,
  newHoldingSelectGate,
  newHoldingSelectTitleKey,
} from "./newHoldingFlowRegistry";
import { useNewHoldingFlowState } from "./useNewHoldingFlowState";
import type {
  CryptoGateResult,
  EquityGateResult,
  FundGateResult,
  NewHoldingEligibleClass,
  NewHoldingGateSelection,
} from "./newHoldingFlow.types";

function isFundSelection(s: NewHoldingGateSelection): s is FundGateResult {
  return s.kind === "fund_cvm" || s.kind === "fund_manual";
}

function isEquitySelection(s: NewHoldingGateSelection): s is EquityGateResult {
  return s.kind === "equity";
}

function isCryptoSelection(s: NewHoldingGateSelection): s is CryptoGateResult {
  return s.kind === "crypto";
}

function selectionKey(selection: NewHoldingGateSelection): string {
  if (selection.kind === "fund_cvm") return `cvm:${selection.fund.id}`;
  if (selection.kind === "fund_manual") return `manual:${selection.manualFundName}:${selection.manualAssetType}`;
  if (selection.kind === "equity") return `eq:${selection.asset.id}`;
  return `crypto:${selection.ticker}:${selection.name}`;
}

export interface NewHoldingFlowShellProps {
  open: boolean;
  onClose: () => void;
  intent: NewBuyIntent | NewSellIntent;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  onRecorded?: () => void | Promise<void>;
}

/**
 * Two-step flow for new fund / equity / crypto holdings: custodian account (outer), then asset gate, then values form.
 * Fixed income and row/edit intents do not use this shell.
 */
export function NewHoldingFlowShell({
  open,
  onClose,
  intent,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
}: NewHoldingFlowShellProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const assetClass = intent.assetClass as NewHoldingEligibleClass;
  const initialDirection =
    intent.kind === "new-sell" ? InvestmentTransactionDirection.Outflow : InvestmentTransactionDirection.Inflow;

  const resetKey = `${intent.kind}-${assetClass}`;
  const { phase, confirmSelection, goToSelect } = useNewHoldingFlowState({ open, resetKey });

  const SelectGate = newHoldingSelectGate[assetClass];

  const drawerTitle =
    phase.kind === "select" ? t(newHoldingSelectTitleKey[assetClass]) : t(newHoldingFormTitleKey[assetClass]);

  const issuerSelection = React.useMemo(() => {
    switch (assetClass) {
      case "funds":
        return {
          description: t("investments.recordFund.selectAccount.description"),
          emptyState: t("investments.recordFund.selectAccount.emptyState"),
          dataTestIdPrefix: "record-fund-select-account-",
        };
      case "stocks":
      case "reits":
        return {
          description: t("investments.recordEquity.selectAccount.description"),
          emptyState: t("investments.recordEquity.selectAccount.emptyState"),
          dataTestIdPrefix: "record-equity-select-account-",
        };
      case "crypto":
        return {
          description: t("investments.recordEquity.selectAccount.description"),
          emptyState: t("investments.recordEquity.selectAccount.emptyState"),
          dataTestIdPrefix: "record-crypto-select-account-",
        };
    }
  }, [assetClass, t]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={drawerTitle}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={issuerSelection}
    >
      {({ resolvedFinancialAccountId }) => (
        <Box>
          {phase.kind === "select" ? (
            <SelectGate onCancel={onClose} onContinue={confirmSelection} />
          ) : (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
              <NewHoldingAssetIdentity assetClass={assetClass} selection={phase.selection} onChangeAsset={goToSelect} />
              {assetClass === "funds" && isFundSelection(phase.selection) ? (
                <RecordFundHoldingDrawer
                  key={selectionKey(phase.selection)}
                  embedded
                  open={open}
                  onClose={onClose}
                  resolvedAccountId={resolvedFinancialAccountId}
                  financialAccounts={financialAccounts}
                  onRecorded={onRecorded}
                  initialDirection={initialDirection}
                  assetGate={{ mode: "locked", result: phase.selection }}
                />
              ) : null}
              {assetClass === "stocks" && isEquitySelection(phase.selection) ? (
                <RecordEquityHoldingDrawer
                  key={selectionKey(phase.selection)}
                  embedded
                  open={open}
                  onClose={onClose}
                  resolvedAccountId={resolvedFinancialAccountId}
                  financialAccounts={financialAccounts}
                  onRecorded={onRecorded}
                  initialDirection={initialDirection}
                  equityRecordVariant="stock"
                  assetGate={{ mode: "locked", result: phase.selection }}
                />
              ) : null}
              {assetClass === "reits" && isEquitySelection(phase.selection) ? (
                <RecordEquityHoldingDrawer
                  key={selectionKey(phase.selection)}
                  embedded
                  open={open}
                  onClose={onClose}
                  resolvedAccountId={resolvedFinancialAccountId}
                  financialAccounts={financialAccounts}
                  onRecorded={onRecorded}
                  initialDirection={initialDirection}
                  equityRecordVariant="fii"
                  assetGate={{ mode: "locked", result: phase.selection }}
                />
              ) : null}
              {assetClass === "crypto" && isCryptoSelection(phase.selection) ? (
                <RecordCryptoHoldingDrawer
                  key={selectionKey(phase.selection)}
                  embedded
                  open={open}
                  onClose={onClose}
                  resolvedAccountId={resolvedFinancialAccountId}
                  financialAccounts={financialAccounts}
                  onRecorded={onRecorded}
                  initialDirection={initialDirection}
                  assetGate={{ mode: "locked", result: phase.selection }}
                />
              ) : null}
            </Box>
          )}
        </Box>
      )}
    </InvestmentRecordDrawer>
  );
}
