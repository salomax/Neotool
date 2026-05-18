"use client";

import * as React from "react";
import type { InvestmentAssetClass } from "../intent";
import type { NewHoldingEligibleClass, NewHoldingSelectGateProps } from "./newHoldingFlow.types";
import { FundAssetGate } from "./FundAssetGate";
import { EquityAssetGate } from "./EquityAssetGate";
import { CryptoAssetGate } from "./CryptoAssetGate";

/** i18n keys for drawer titles — resolved with `t()` in the shell. */
export const newHoldingSelectTitleKey: Record<NewHoldingEligibleClass, string> = {
  funds: "investments.newHoldingFlow.selectFundTitle",
  stocks: "investments.newHoldingFlow.selectStockTitle",
  reits: "investments.newHoldingFlow.selectReitTitle",
  crypto: "investments.newHoldingFlow.selectCryptoTitle",
};

export const newHoldingFormTitleKey: Record<NewHoldingEligibleClass, string> = {
  funds: "investments.recordFund.title",
  stocks: "investments.recordEquity.titles.stock",
  reits: "investments.recordEquity.titles.fii",
  crypto: "investments.grid.classes.crypto",
};

export const newHoldingSelectGate: Record<
  NewHoldingEligibleClass,
  React.ComponentType<NewHoldingSelectGateProps>
> = {
  funds: FundAssetGate,
  stocks: (props) => <EquityAssetGate {...props} equityRecordVariant="stock" />,
  reits: (props) => <EquityAssetGate {...props} equityRecordVariant="fii" />,
  crypto: CryptoAssetGate,
};

export function isNewHoldingEligibleClass(
  assetClass: InvestmentAssetClass,
): assetClass is NewHoldingEligibleClass {
  return assetClass !== "fixed-income";
}
