'use client';

import type { AssetType } from '@/lib/graphql/types/__generated__/graphql';
import type { FundAutocompleteValue } from '../../FundAutocomplete';
import type { EquityAutocompleteValue } from '../../EquityAutocomplete';
import type { InvestmentAssetClass } from '../intent';

/** CVM-listed fund chosen in step 1 (financial-data suggestion shape). */
export type FundGateResultCvm = {
  kind: 'fund_cvm';
  listingMode: 'CVM';
  fund: FundAutocompleteValue;
};

/** Manual fund identity before create-investment-asset on submit. */
export type FundGateResultManual = {
  kind: 'fund_manual';
  listingMode: 'MANUAL';
  manualFundName: string;
  manualFundCnpj: string;
  manualAssetType: AssetType;
};

export type FundGateResult = FundGateResultCvm | FundGateResultManual;

export type EquityGateResult = {
  kind: 'equity';
  asset: EquityAutocompleteValue;
};

export type CryptoGateResult = {
  kind: 'crypto';
  name: string;
  ticker: string;
  network: string;
};

export type NewHoldingGateSelection = FundGateResult | EquityGateResult | CryptoGateResult;

export type NewHoldingEligibleClass = Exclude<InvestmentAssetClass, 'fixed-income'>;

export type NewHoldingFlowPhase =
  | { kind: 'select' }
  | { kind: 'form'; selection: NewHoldingGateSelection };

/** Step 1 gate screens receive this contract (strategy / registry). */
export interface NewHoldingSelectGateProps {
  onContinue: (selection: NewHoldingGateSelection) => void;
  onCancel: () => void;
}
