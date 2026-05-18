'use client';

import type {
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from '@/lib/graphql/types/__generated__/graphql';

/** Row shape from `INVESTMENT_TRANSACTIONS` for the holding edit drawer. */
export interface TransactionEditorExistingTransaction {
  id: string;
  direction: InvestmentTransactionDirection;
  type: InvestmentTransactionType;
  tradeDate: unknown;
  settlementDate?: unknown | null;
  quantity?: number | null;
  unitPrice?: number | null;
  grossValue: number;
  netValue?: number | null;
  incomeTax?: number | null;
  iof?: number | null;
  priceFactor?: number | null;
  pricingMode?: HoldingPricingMode | null;
  brokerNote?: {
    brokerageFee: number;
    clearingCustodyFee: number;
    clearingRegistrationFee: number;
    clearingSettlementFee: number;
    stockExchangeAssetTradeNoticeFee: number;
    stockExchangeFee: number;
    taxes: number;
    incomeTax: number;
  } | null;
}
