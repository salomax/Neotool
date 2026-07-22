/**
 * Investment GraphQL helpers for E2E tests.
 * Uses raw operations via executeGraphQL (same approach as banking-graphql.ts).
 */

import { executeGraphQL } from './graphql';
import {
  AssetType,
  CouponFrequency,
  GridClassTwrPeriod,
  HoldingCloseReason,
  HoldingPricingMode,
  Indexer,
  InvestmentCashTransactionType,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PendingActionStatus,
  PendingActionType,
  PerformancePeriod,
  ValuationSource,
} from '../../../src/lib/graphql/types/__generated__/graphql';

export {
  AssetType,
  CouponFrequency,
  GridClassTwrPeriod,
  HoldingCloseReason,
  HoldingPricingMode,
  Indexer,
  InvestmentCashTransactionType,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PendingActionStatus,
  PendingActionType,
  PerformancePeriod,
  ValuationSource,
};

export interface InvestmentAsset {
  id: string;
  name: string;
  assetType: AssetType;
  issuerName?: string | null;
  ticker?: string | null;
  fundCnpj?: string | null;
  currencyCode: string;
}

export interface InvestmentHolding {
  id: string;
  financialAccountId: string;
  quantity: number;
  averageCost: number;
  pricingMode: HoldingPricingMode | null;
  comeCotasEnabled: boolean | null;
  administrationFeeAnnualPercent: number | null;
  indexer: Indexer | null;
  indexerPercentage: number | null;
  fixedAnnualRate: number | null;
  issueDate: string | null;
  maturityDate: string | null;
  gracePeriodDate: string | null;
  isTaxExempt: boolean;
  couponFrequency: CouponFrequency | null;
  couponDayOfMonth: number | null;
  grossValue: number | null;
  netValue: number | null;
  incomeTax: number | null;
  iof: number | null;
  isExpired: boolean;
  isExpiredPending: boolean;
  closedAt: string | null;
  closeReason: HoldingCloseReason | null;
  isClosed: boolean;
  valuationReferenceDate: string | null;
  valuationSource: ValuationSource;
  asset: {
    id: string;
    name: string;
    assetType: AssetType;
    ticker?: string | null;
  };
}

export interface InvestmentTransaction {
  id: string;
  holdingId: string;
  financialAccountId: string;
  type: InvestmentTransactionType;
  direction: InvestmentTransactionDirection;
  quantity: number | null;
  unitPrice: number | null;
  grossValue: number;
  netValue: number | null;
  incomeTax: number | null;
  iof: number | null;
  tradeDate: string;
  settlementDate: string | null;
  source: InvestmentSource;
  brokerNote: {
    brokerNoteNumber: string | null;
    grossValue: number;
    brokerageFee: number;
    clearingSettlementFee: number;
    clearingRegistrationFee: number;
    stockExchangeAssetTradeNoticeFee: number;
    stockExchangeFee: number;
    clearingCustodyFee: number;
    incomeTax: number;
    taxes: number;
    netValue: number;
  } | null;
}

export interface AssetGroupSummary {
  groupKey: string;
  label: string;
  count: number;
  totalCostBasis: number;
  totalGrossValue: number;
  grossGainPercent: number;
  twr: number | null;
  allocationPercent: number;
  isCash: boolean;
}

export interface PortfolioPerformance {
  twr: number | null;
  twrSince: string | null;
  twrAbsolute: number | null;
  period: PerformancePeriod;
  hasEstimatedValues: boolean;
}

export interface RecalculationStatus {
  isCalculating: boolean;
  jobId: string | null;
  errorCode: string | null;
  message: string | null;
}

export interface InvestmentPortfolioSummary {
  totalHoldingsCount: number;
  totalCostBasis: number;
  totalGrossValue: number;
  totalNetValue: number;
  totalIncomeTax: number;
  totalIof: number;
  grossGain: number;
  grossGainPercent: number;
  netGain: number;
  netGainPercent: number;
  cashBalance: number;
  totalPortfolioValue: number;
  hasEstimatedValues: boolean;
  valuationReferenceDate: string | null;
  currencyCode: string;
  byAssetGroup: AssetGroupSummary[];
  performance: PortfolioPerformance;
  recalculationStatus: RecalculationStatus;
}

export interface GridClassTwrPoint {
  date: string;
  quotaValue: number;
}

export interface PendingAction {
  id: string;
  type: PendingActionType;
  status: PendingActionStatus;
  payload: unknown;
  relatedHolding: {
    id: string;
    financialAccountId: string;
    quantity: number;
    averageCost: number;
    asset: {
      id: string;
      name: string;
      assetType: AssetType;
      ticker?: string | null;
    };
  } | null;
}

export interface CreateInvestmentAssetInput {
  name: string;
  assetType: AssetType;
  issuerName?: string;
  ticker?: string;
  fundCnpj?: string;
  currencyCode?: string;
}

export interface RecordInvestmentTransactionInput {
  financialAccountId: string;
  assetId: string;
  direction: InvestmentTransactionDirection;
  type: InvestmentTransactionType;
  tradeDate: string;
  settlementDate?: string;
  quantity?: number;
  unitPrice?: number;
  grossValue: number;
  netValue?: number;
  incomeTax?: number;
  iof?: number;
  pricingMode?: HoldingPricingMode;
  administrationFeeAnnualPercent?: number;
  brokerNote?: {
    brokerNoteNumber?: string;
    grossValue: number;
    brokerageFee?: number;
    clearingSettlementFee?: number;
    clearingRegistrationFee?: number;
    stockExchangeAssetTradeNoticeFee?: number;
    stockExchangeFee?: number;
    clearingCustodyFee?: number;
    incomeTax?: number;
    taxes?: number;
    netValue: number;
  };
  source?: InvestmentSource;
  indexer?: Indexer;
  indexerPercentage?: number;
  fixedAnnualRate?: number;
  issueDate?: string;
  maturityDate?: string;
  couponFrequency?: CouponFrequency;
  couponDayOfMonth?: number;
  isTaxExempt?: boolean;
  manualValuation?: {
    valuedAt: string;
    grossValue: number;
  };
}

export interface UpdateInvestmentTransactionInput {
  direction: InvestmentTransactionDirection;
  type: InvestmentTransactionType;
  tradeDate: string;
  settlementDate?: string;
  quantity?: number;
  unitPrice?: number;
  grossValue: number;
  netValue?: number;
  incomeTax?: number;
  iof?: number;
  pricingMode?: HoldingPricingMode;
  brokerNote?: {
    brokerNoteNumber?: string;
    grossValue: number;
    brokerageFee?: number;
    clearingSettlementFee?: number;
    clearingRegistrationFee?: number;
    stockExchangeAssetTradeNoticeFee?: number;
    stockExchangeFee?: number;
    clearingCustodyFee?: number;
    incomeTax?: number;
    taxes?: number;
    netValue: number;
  };
  indexer?: Indexer;
  indexerPercentage?: number;
  fixedAnnualRate?: number;
  issueDate?: string;
  maturityDate?: string;
  couponFrequency?: CouponFrequency;
  couponDayOfMonth?: number;
  isTaxExempt?: boolean;
  manualValuation?: {
    valuedAt: string;
    grossValue: number;
  };
}

export interface UpdateFundHoldingParamsInput {
  holdingId: string;
  administrationFeeAnnualPercent?: number | null;
  comeCotasEnabled?: boolean | null;
}

export interface CreateInvestmentCashTransactionInput {
  financialAccountId: string;
  date: string;
  type: InvestmentCashTransactionType;
  amount: number;
  description?: string;
  peerFinancialAccountId?: string;
  registerAsBalanceAdjustment?: boolean;
}

export interface UpdateInvestmentCashTransactionInput {
  amount: number;
  date: string;
  description?: string;
}

export interface ConfirmHoldingMaturityInput {
  holdingId: string;
  maturityDate: string;
  grossValue: number;
  netValue: number;
  incomeTax?: number;
  iof?: number;
  closeReason: HoldingCloseReason;
  cashMovement?: {
    financialAccountId: string;
    amount: number;
    date: string;
  };
}

export interface ConfirmHoldingMaturityResult {
  maturityTransaction: InvestmentTransaction;
  bankingTransaction: InvestmentCashTransaction | null;
  pendingAction: {
    id: string;
    status: PendingActionStatus;
  };
}

export interface ConfirmCouponPaymentInput {
  holdingId: string;
  tradeDate: string;
  grossValue: number;
  netValue: number;
  incomeTax: number;
  destinationAccountId: string;
  pendingActionId?: string;
}

export interface ConfirmCouponPaymentResult {
  couponTransaction: InvestmentTransaction;
  bankingTransaction: InvestmentCashTransaction | null;
  pendingAction: {
    id: string;
    status: PendingActionStatus;
  } | null;
}

export interface InvestmentScope {
  financialAccountId?: string;
}

export interface InvestmentCashTransaction {
  id: string;
  date: string;
  description: string;
  type: string;
  creditDebitType: string;
  amount: number;
  currencyCode: string;
  financialAccountId: string;
  source: string;
  status: string;
  transferPeerTransactionId: string | null;
  investmentTransactionId: string | null;
}

const INVESTMENT_ASSET_FIELDS = `
  id
  name
  assetType
  issuerName
  ticker
  fundCnpj
  currencyCode
`;

const INVESTMENT_HOLDING_FIELDS = `
  id
  financialAccountId
  quantity
  averageCost
  pricingMode
  comeCotasEnabled
  administrationFeeAnnualPercent
  indexer
  indexerPercentage
  fixedAnnualRate
  issueDate
  maturityDate
  gracePeriodDate
  isTaxExempt
  couponFrequency
  couponDayOfMonth
  grossValue
  netValue
  incomeTax
  iof
  isExpired
  isExpiredPending
  closedAt
  closeReason
  isClosed
  valuationReferenceDate
  valuationSource
  asset {
    id
    name
    assetType
    ticker
  }
`;

const INVESTMENT_TRANSACTION_FIELDS = `
  id
  holdingId
  financialAccountId
  type
  direction
  quantity
  unitPrice
  grossValue
  netValue
  incomeTax
  iof
  tradeDate
  settlementDate
  source
  brokerNote {
    brokerNoteNumber
    grossValue
    brokerageFee
    clearingSettlementFee
    clearingRegistrationFee
    stockExchangeAssetTradeNoticeFee
    stockExchangeFee
    clearingCustodyFee
    incomeTax
    taxes
    netValue
  }
`;

export async function createInvestmentAsset(
  input: CreateInvestmentAssetInput,
  token: string,
): Promise<InvestmentAsset> {
  const mutation = `
    mutation CreateInvestmentAsset($input: CreateInvestmentAssetInput!) {
      createInvestmentAsset(input: $input) {
        ${INVESTMENT_ASSET_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ createInvestmentAsset: InvestmentAsset }>(
    mutation,
    {
      input: {
        ...input,
        currencyCode: input.currencyCode ?? 'BRL',
      },
    },
    token,
  );

  if (result.errors || !result.data?.createInvestmentAsset) {
    throw new Error(
      `createInvestmentAsset failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.createInvestmentAsset;
}

export async function recordInvestmentTransaction(
  input: RecordInvestmentTransactionInput,
  token: string,
): Promise<InvestmentTransaction> {
  const mutation = `
    mutation RecordInvestmentTransaction($input: RecordInvestmentTransactionInput!) {
      recordInvestmentTransaction(input: $input) {
        ${INVESTMENT_TRANSACTION_FIELDS}
      }
    }
  `;

  const { source: _ignoredSource, ...inputWithoutSource } = input;

  const result = await executeGraphQL<{ recordInvestmentTransaction: InvestmentTransaction }>(
    mutation,
    {
      input: inputWithoutSource,
    },
    token,
  );

  if (result.errors || !result.data?.recordInvestmentTransaction) {
    throw new Error(
      `recordInvestmentTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.recordInvestmentTransaction;
}

export async function updateInvestmentTransaction(
  id: string,
  input: UpdateInvestmentTransactionInput,
  token: string,
): Promise<InvestmentTransaction> {
  const mutation = `
    mutation UpdateInvestmentTransaction($id: ID!, $input: UpdateInvestmentTransactionInput!) {
      updateInvestmentTransaction(id: $id, input: $input) {
        ${INVESTMENT_TRANSACTION_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ updateInvestmentTransaction: InvestmentTransaction }>(
    mutation,
    { id, input },
    token,
  );

  if (result.errors || !result.data?.updateInvestmentTransaction) {
    throw new Error(
      `updateInvestmentTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.updateInvestmentTransaction;
}

export async function updateFundHoldingParams(
  input: UpdateFundHoldingParamsInput,
  token: string,
): Promise<InvestmentHolding> {
  const mutation = `
    mutation UpdateFundHoldingParams($input: UpdateFundHoldingParamsInput!) {
      updateFundHoldingParams(input: $input) {
        ${INVESTMENT_HOLDING_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ updateFundHoldingParams: InvestmentHolding }>(
    mutation,
    { input },
    token,
  );

  if (result.errors || !result.data?.updateFundHoldingParams) {
    throw new Error(
      `updateFundHoldingParams failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.updateFundHoldingParams;
}

export async function deleteInvestmentTransaction(id: string, token: string): Promise<void> {
  const mutation = `
    mutation DeleteInvestmentTransaction($id: ID!) {
      deleteInvestmentTransaction(id: $id)
    }
  `;

  const result = await executeGraphQL<{ deleteInvestmentTransaction: boolean }>(
    mutation,
    { id },
    token,
  );

  if (result.errors || !result.data?.deleteInvestmentTransaction) {
    throw new Error(
      `deleteInvestmentTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }
}

export async function getInvestmentHoldings(
  token: string,
  scope: InvestmentScope = {},
): Promise<InvestmentHolding[]> {
  const query = `
    query InvestmentHoldings($financialAccountId: ID) {
      investmentHoldings(financialAccountId: $financialAccountId) {
        ${INVESTMENT_HOLDING_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ investmentHoldings: InvestmentHolding[] }>(
    query,
    scope,
    token,
  );

  if (result.errors) {
    throw new Error(`getInvestmentHoldings failed: ${result.errors[0].message}`);
  }

  return result.data?.investmentHoldings ?? [];
}

export async function getInvestmentHolding(id: string, token: string): Promise<InvestmentHolding | null> {
  const query = `
    query InvestmentHolding($id: ID!) {
      investmentHolding(id: $id) {
        ${INVESTMENT_HOLDING_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ investmentHolding: InvestmentHolding | null }>(
    query,
    { id },
    token,
  );

  if (result.errors) {
    throw new Error(`getInvestmentHolding failed: ${result.errors[0].message}`);
  }

  return result.data?.investmentHolding ?? null;
}

export async function getInvestmentTransactions(
  holdingId: string,
  token: string,
): Promise<InvestmentTransaction[]> {
  const query = `
    query InvestmentTransactions($holdingId: ID!) {
      investmentTransactions(holdingId: $holdingId) {
        ${INVESTMENT_TRANSACTION_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{ investmentTransactions: InvestmentTransaction[] }>(
    query,
    { holdingId },
    token,
  );

  if (result.errors) {
    throw new Error(`getInvestmentTransactions failed: ${result.errors[0].message}`);
  }

  return result.data?.investmentTransactions ?? [];
}

export async function createInvestmentCashTransaction(
  input: CreateInvestmentCashTransactionInput,
  token: string,
): Promise<{ id: string; amount: number; type: InvestmentCashTransactionType; date: string }> {
  const mutation = `
    mutation CreateInvestmentCashTransaction($input: CreateInvestmentCashTransactionInput!) {
      createInvestmentCashTransaction(input: $input) {
        id
        amount
        type
        date
      }
    }
  `;

  const result = await executeGraphQL<{
    createInvestmentCashTransaction: {
      id: string;
      amount: number;
      type: InvestmentCashTransactionType;
      date: string;
    };
  }>(
    mutation,
    { input },
    token,
  );

  if (result.errors || !result.data?.createInvestmentCashTransaction) {
    throw new Error(
      `createInvestmentCashTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.createInvestmentCashTransaction;
}

export async function updateInvestmentCashTransaction(
  id: string,
  input: UpdateInvestmentCashTransactionInput,
  token: string,
): Promise<InvestmentCashTransaction> {
  const mutation = `
    mutation UpdateInvestmentCashTransaction($id: ID!, $input: UpdateInvestmentCashTransactionInput!) {
      updateInvestmentCashTransaction(id: $id, input: $input) {
        id
        date
        description
        type
        creditDebitType
        amount
        currencyCode
        financialAccountId
        source
        status
        transferPeerTransactionId
        investmentTransactionId
      }
    }
  `;

  const result = await executeGraphQL<{ updateInvestmentCashTransaction: InvestmentCashTransaction }>(
    mutation,
    { id, input },
    token,
  );

  if (result.errors || !result.data?.updateInvestmentCashTransaction) {
    throw new Error(
      `updateInvestmentCashTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.updateInvestmentCashTransaction;
}

export async function deleteInvestmentCashTransaction(id: string, token: string): Promise<void> {
  const mutation = `
    mutation DeleteInvestmentCashTransaction($id: ID!) {
      deleteInvestmentCashTransaction(id: $id)
    }
  `;

  const result = await executeGraphQL<{ deleteInvestmentCashTransaction: boolean }>(
    mutation,
    { id },
    token,
  );

  if (result.errors || !result.data?.deleteInvestmentCashTransaction) {
    throw new Error(
      `deleteInvestmentCashTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }
}

export async function getInvestmentCashBalance(
  token: string,
  scope: InvestmentScope = {},
): Promise<number> {
  const query = `
    query InvestmentCashBalance($financialAccountId: ID) {
      investmentCashBalance(financialAccountId: $financialAccountId)
    }
  `;

  const result = await executeGraphQL<{ investmentCashBalance: number }>(query, scope, token);

  if (result.errors || typeof result.data?.investmentCashBalance !== 'number') {
    throw new Error(
      `getInvestmentCashBalance failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.investmentCashBalance;
}

export async function getInvestmentCashTransactions(
  token: string,
  options: { financialAccountId?: string; first?: number; after?: string } = {},
): Promise<InvestmentCashTransaction[]> {
  const query = `
    query InvestmentCashTransactions($financialAccountId: ID, $first: Int, $after: String) {
      investmentCashTransactions(financialAccountId: $financialAccountId, first: $first, after: $after) {
        edges {
          node {
            id
            date
            description
            type
            creditDebitType
            amount
            currencyCode
            financialAccountId
            source
            status
            transferPeerTransactionId
            investmentTransactionId
          }
        }
        pageInfo {
          hasNextPage
          endCursor
        }
      }
    }
  `;

  const targetCount = options.first ?? 100;
  const pageSize = Math.min(targetCount, 50);
  const rows: InvestmentCashTransaction[] = [];
  let nextCursor: string | undefined = options.after;

  while (rows.length < targetCount) {
    const result = await executeGraphQL<{
      investmentCashTransactions: {
        edges: Array<{ node: InvestmentCashTransaction }>;
        pageInfo: { hasNextPage: boolean; endCursor: string | null };
      };
    }>(
      query,
      {
        financialAccountId: options.financialAccountId,
        first: pageSize,
        after: nextCursor,
      },
      token,
    );

    if (result.errors) {
      throw new Error(`getInvestmentCashTransactions failed: ${result.errors[0].message}`);
    }

    const connection = result.data?.investmentCashTransactions;
    if (!connection) break;

    rows.push(...connection.edges.map((e) => e.node));

    if (!connection.pageInfo.hasNextPage || !connection.pageInfo.endCursor) {
      break;
    }
    nextCursor = connection.pageInfo.endCursor;
  }

  return rows.slice(0, targetCount);
}

export async function getInvestmentPortfolioSummary(
  token: string,
  options: { financialAccountId?: string; period?: PerformancePeriod } = {},
): Promise<InvestmentPortfolioSummary> {
  const query = `
    query InvestmentPortfolioSummary($financialAccountId: ID, $period: PerformancePeriod) {
      investmentPortfolioSummary(financialAccountId: $financialAccountId, period: $period) {
        totalHoldingsCount
        totalCostBasis
        totalGrossValue
        totalNetValue
        totalIncomeTax
        totalIof
        grossGain
        grossGainPercent
        netGain
        netGainPercent
        cashBalance
        totalPortfolioValue
        hasEstimatedValues
        valuationReferenceDate
        currencyCode
        byAssetGroup {
          groupKey
          label
          count
          totalCostBasis
          totalGrossValue
          grossGainPercent
          twr
          allocationPercent
          isCash
        }
        performance {
          twr
          twrSince
          twrAbsolute
          period
          hasEstimatedValues
        }
        recalculationStatus {
          isCalculating
          jobId
          errorCode
          message
        }
      }
    }
  `;

  const result = await executeGraphQL<{ investmentPortfolioSummary: InvestmentPortfolioSummary }>(
    query,
    {
      financialAccountId: options.financialAccountId,
      period: options.period ?? PerformancePeriod.All,
    },
    token,
  );

  if (result.errors || !result.data?.investmentPortfolioSummary) {
    throw new Error(
      `getInvestmentPortfolioSummary failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.investmentPortfolioSummary;
}

export async function getGridClassTwrSeries(
  token: string,
  gridClassKey: string,
  options: { financialAccountId?: string; period?: GridClassTwrPeriod } = {},
): Promise<GridClassTwrPoint[]> {
  const query = `
    query GridClassTwrSeries($financialAccountId: ID, $gridClassKey: String!, $period: GridClassTwrPeriod) {
      gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: $gridClassKey, period: $period) {
        date
        quotaValue
      }
    }
  `;

  const result = await executeGraphQL<{ gridClassTwrSeries: GridClassTwrPoint[] }>(
    query,
    {
      financialAccountId: options.financialAccountId,
      gridClassKey,
      period: options.period ?? GridClassTwrPeriod.OneYear,
    },
    token,
  );

  if (result.errors) {
    throw new Error(`getGridClassTwrSeries failed: ${result.errors[0].message}`);
  }

  return result.data?.gridClassTwrSeries ?? [];
}

export async function triggerPortfolioRecalculation(
  token: string,
  options: { financialAccountId?: string; recalculateFrom?: string } = {},
): Promise<boolean> {
  const mutation = `
    mutation TriggerPortfolioRecalculation($financialAccountId: ID, $recalculateFrom: String) {
      triggerPortfolioRecalculation(financialAccountId: $financialAccountId, recalculateFrom: $recalculateFrom)
    }
  `;

  const result = await executeGraphQL<{ triggerPortfolioRecalculation: boolean }>(
    mutation,
    options,
    token,
  );

  if (result.errors || typeof result.data?.triggerPortfolioRecalculation !== 'boolean') {
    throw new Error(
      `triggerPortfolioRecalculation failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.triggerPortfolioRecalculation;
}

export async function confirmHoldingMaturity(
  input: ConfirmHoldingMaturityInput,
  token: string,
): Promise<ConfirmHoldingMaturityResult> {
  const mutation = `
    mutation ConfirmHoldingMaturity($command: ConfirmHoldingMaturityInput!) {
      confirmHoldingMaturity(command: $command) {
        maturityTransaction {
          ${INVESTMENT_TRANSACTION_FIELDS}
        }
        bankingTransaction {
          id
          date
          description
          type
          creditDebitType
          amount
          currencyCode
          financialAccountId
          source
          status
          transferPeerTransactionId
          investmentTransactionId
        }
        pendingAction {
          id
          status
        }
      }
    }
  `;

  const result = await executeGraphQL<{ confirmHoldingMaturity: ConfirmHoldingMaturityResult }>(
    mutation,
    { command: input },
    token,
  );

  if (result.errors || !result.data?.confirmHoldingMaturity) {
    throw new Error(
      `confirmHoldingMaturity failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.confirmHoldingMaturity;
}

export async function confirmCouponPayment(
  input: ConfirmCouponPaymentInput,
  token: string,
): Promise<ConfirmCouponPaymentResult> {
  const mutation = `
    mutation ConfirmCouponPayment($input: ConfirmCouponPaymentInput!) {
      confirmCouponPayment(input: $input) {
        couponTransaction {
          ${INVESTMENT_TRANSACTION_FIELDS}
        }
        bankingTransaction {
          id
          date
          description
          type
          creditDebitType
          amount
          currencyCode
          financialAccountId
          source
          status
          transferPeerTransactionId
          investmentTransactionId
        }
        pendingAction {
          id
          status
        }
      }
    }
  `;

  const result = await executeGraphQL<{ confirmCouponPayment: ConfirmCouponPaymentResult }>(
    mutation,
    { input },
    token,
  );

  if (result.errors || !result.data?.confirmCouponPayment) {
    throw new Error(
      `confirmCouponPayment failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.confirmCouponPayment;
}

export async function getPendingActions(
  token: string,
  status?: PendingActionStatus,
): Promise<PendingAction[]> {
  const query = `
    query PendingActions($status: PendingActionStatus) {
      pendingActions(status: $status) {
        id
        type
        status
        payload
        relatedHolding {
          id
          financialAccountId
          quantity
          averageCost
          asset {
            id
            name
            assetType
            ticker
          }
        }
      }
    }
  `;

  const result = await executeGraphQL<{ pendingActions: PendingAction[] }>(
    query,
    { status },
    token,
  );

  if (result.errors) {
    throw new Error(`getPendingActions failed: ${result.errors[0].message}`);
  }

  return result.data?.pendingActions ?? [];
}

// ── TWR Debug ─────────────────────────────────────────────────────────────────

export interface TwrDailyStep {
  date: string;
  previousValue: number;
  currentValue: number;
  netFlow: number;
  dailyReturn: number;
  cumulativeFactor: number;
}

export interface TwrFlowEvent {
  date: string;
  eventType: string;
  grossAmount: number;
  canonicalNetFlow: number;
}

export interface PortfolioTwrDebug {
  startDate: string | null;
  endDate: string | null;
  twr: number | null;
  series: { date: string; totalValue: number }[];
  flowEvents: TwrFlowEvent[];
  dailySteps: TwrDailyStep[];
}

/**
 * Fetches the TWR debug breakdown for a financial account.
 * Useful for diagnosing assertion failures in E2E tests — shows the daily quota series,
 * flow events, and step-by-step TWR computation.
 */
export async function getPortfolioTwrDebug(
  token: string,
  options: { financialAccountId?: string; period?: PerformancePeriod } = {},
): Promise<PortfolioTwrDebug> {
  const query = `
    query PortfolioTwrDebug($financialAccountId: ID, $period: PerformancePeriod) {
      portfolioTwrDebug(financialAccountId: $financialAccountId, period: $period) {
        startDate
        endDate
        twr
        series {
          date
          totalValue
        }
        flowEvents {
          date
          eventType
          grossAmount
          canonicalNetFlow
        }
        dailySteps {
          date
          previousValue
          currentValue
          netFlow
          dailyReturn
          cumulativeFactor
        }
      }
    }
  `;

  const result = await executeGraphQL<{ portfolioTwrDebug: PortfolioTwrDebug }>(
    query,
    {
      financialAccountId: options.financialAccountId,
      period: options.period ?? PerformancePeriod.All,
    },
    token,
  );

  if (result.errors || !result.data?.portfolioTwrDebug) {
    throw new Error(
      `getPortfolioTwrDebug failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`,
    );
  }

  return result.data.portfolioTwrDebug;
}
