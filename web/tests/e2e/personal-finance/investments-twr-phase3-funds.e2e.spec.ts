import { randomBytes } from 'node:crypto';

import { test, expect } from '../fixtures/investments';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentCashBalance,
  getInvestmentCashTransactions,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  ValuationSource,
} from '../helpers/investment-graphql';
import { waitForRecalculation } from '../helpers/investment-e2e';
import { isoDaysAgo } from '../helpers/fixed-income-e2e';


test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 300000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** Matches banking `CnpjValidator` (14 digits + check digits). */
const CNPJ_W1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] as const;
const CNPJ_W2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] as const;

function calculateCnpjCheckDigit(digits: string, weights: readonly number[]): number {
  let sum = 0;
  for (let i = 0; i < weights.length; i++) {
    sum += parseInt(digits[i]!, 10) * weights[i]!;
  }
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}

/** Unique per call; valid for `createInvestmentAsset` / `fundCnpj`. */
function uniqueValidCnpj(): string {
  const perf =
    typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Math.random();
  const salt = `${Date.now()}-${perf}-${randomBytes(12).toString('hex')}-${Math.random().toString(36).slice(2)}-${(Math.random() * 1e15).toFixed(0)}`.replace(
    /\D/g,
    '',
  );
  let base = '';
  for (let i = 0; i < 12; i++) {
    base += salt[i % salt.length] ?? '0';
  }
  while (new Set(base.split('')).size === 1) {
    const last = (parseInt(base[11]!, 10) + 1) % 10;
    base = `${base.slice(0, 11)}${last}`;
  }
  const d1 = calculateCnpjCheckDigit(base, CNPJ_W1);
  const d2 = calculateCnpjCheckDigit(base + String(d1), CNPJ_W2);
  return base + String(d1) + String(d2);
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function findHolding(token: string, financialAccountId: string, assetId: string) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

test.describe('Investments TWR phase 3B funds', () => {
  test('INV-TWR-P3-FU-01: fund first application + subscription keeps weighted average and fund identity by CNPJ', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fundCnpj = uniqueValidCnpj();

    const fundFirst = await createInvestmentAsset(
      {
        name: uniqueName('Fundo CVM por CNPJ'),
        assetType: AssetType.MutualFund,
        fundCnpj,
      },
      pfUser.token,
    );
    const fundSecond = await createInvestmentAsset(
      {
        name: uniqueName('Fundo CVM por CNPJ duplicate'),
        assetType: AssetType.MutualFund,
        fundCnpj,
      },
      pfUser.token,
    );
    expect(fundSecond.id).toBe(fundFirst.id);

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fundFirst.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 1234.56,
        unitPrice: 10.5,
        grossValue: 12962.88,
        netValue: 12962.88,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fundFirst.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Subscription,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 500,
        unitPrice: 10.8,
        grossValue: 5400,
        netValue: 5400,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, fundFirst.id);
    expect(holding).not.toBeNull();
    expect(holding!.pricingMode).toBe(HoldingPricingMode.MarketData);
    expectNear(holding!.quantity, 1734.56, 6);

    const expectedAverageCost = (1234.56 * 10.5 + 500 * 10.8) / 1734.56;
    expectNear(holding!.averageCost, expectedAverageCost, 6);
  });

  test('INV-TWR-P3-FU-02: partial fund redemption credits user-confirmed net value and preserves average cost', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Fundo resgate parcial'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 1234.56,
        unitPrice: 10.5,
        grossValue: 12962.88,
        netValue: 12962.88,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const before = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const sale = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 500,
        grossValue: 5400,
        netValue: 5200,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(after - before, 5200, 6);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    const linkedRow = rows.find((row) => row.investmentTransactionId === sale.id);
    expect(linkedRow).toBeTruthy();
    expect(linkedRow?.creditDebitType).toBe('CREDIT');
    expectNear(linkedRow?.amount ?? 0, 5200, 6);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, fund.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 734.56, 6);
    expectNear(holding!.averageCost, 10.5, 6);
  });

  test('INV-TWR-P3-FU-03: WITHHOLDING_TAX reduces quotas with no cash movement and enforces fund-only/quantity validations', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Fundo come-cotas'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(40),
        quantity: 1000,
        unitPrice: 10,
        grossValue: 10000,
        netValue: 10000,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const cashBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const withholding = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.WithholdingTax,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 40.238,
        unitPrice: 10.56,
        grossValue: 425,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, fund.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 959.762, 6);

    const cashAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(cashAfter, cashBefore, 6);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    expect(rows.some((row) => row.investmentTransactionId === withholding.id)).toBe(false);

    const zeroGrossWithholding = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.WithholdingTax,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 10,
        unitPrice: 10,
        grossValue: 0,
      },
      pfUser.token,
    );
    expect(zeroGrossWithholding.type).toBe(InvestmentTransactionType.WithholdingTax);

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: fund.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.WithholdingTax,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 9999,
          unitPrice: 1,
          grossValue: 9999,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/Come-cotas quantity|insufficient|insuficiente|Invalid input/i);

    const pgbl = await createInvestmentAsset(
      {
        name: uniqueName('PGBL withholding invalid'),
        assetType: AssetType.Pgbl,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: pgbl.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: {
          valuedAt: isoDaysAgo(10),
          grossValue: 1000,
        },
      },
      pfUser.token,
    );

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: pgbl.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.WithholdingTax,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 0.1,
          unitPrice: 100,
          grossValue: 10,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/WITHHOLDING_TAX is only valid for MUTUAL_FUND|Invalid input/i);
  });

  test('INV-TWR-P3-FU-04: MANUAL and MARKET_DATA pricing modes drive valuation source and QUOTA_TRANSFER preserves quotas/cost with no cash leg', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const manualValuedAt = isoDaysAgo(1);

    const pgblManual = await createInvestmentAsset(
      {
        name: uniqueName('PGBL manual valuation'),
        assetType: AssetType.Pgbl,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: pgblManual.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 1,
        unitPrice: 50000,
        grossValue: 50000,
        netValue: 50000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: {
          valuedAt: manualValuedAt,
          grossValue: 50000,
        },
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const manualHolding = await findHolding(pfUser.token, brokerageAccount.id, pgblManual.id);
    expect(manualHolding).not.toBeNull();
    expect(manualHolding!.pricingMode).toBe(HoldingPricingMode.Manual);
    expect(manualHolding!.valuationSource).toBe(ValuationSource.Manual);
    expect(manualHolding!.valuationReferenceDate).toBe(manualValuedAt);

    const randomFundCnpj = uniqueValidCnpj();
    const marketDataFund = await createInvestmentAsset(
      {
        name: uniqueName('Mutual fund market-data/unavailable'),
        assetType: AssetType.MutualFund,
        fundCnpj: randomFundCnpj,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: marketDataFund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const marketHolding = await findHolding(pfUser.token, brokerageAccount.id, marketDataFund.id);
    expect(marketHolding).not.toBeNull();
    expect(marketHolding!.pricingMode).toBe(HoldingPricingMode.MarketData);
    expect(
      [ValuationSource.MarketData, ValuationSource.Unavailable].includes(marketHolding!.valuationSource),
    ).toBe(true);
    if (marketHolding!.valuationSource === ValuationSource.Unavailable) {
      // No NAV snapshot for this CNPJ → gross is null/0; cost basis remains on quantity × averageCost.
      expect(marketHolding!.grossValue == null || marketHolding!.grossValue === 0).toBe(true);
      expectNear(marketHolding!.quantity * marketHolding!.averageCost, 1000, 2);
    }

    const transferFund = await createInvestmentAsset(
      {
        name: uniqueName('Mutual fund quota transfer'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferFund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(25),
        quantity: 1234.56,
        unitPrice: 10.5,
        grossValue: 12962.88,
        netValue: 12962.88,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const transferOut = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferFund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.QuotaTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 1234.56,
        grossValue: 12962.88,
        netValue: 12962.88,
      },
      pfUser.token,
    );
    const transferIn = await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: transferFund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.QuotaTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 1234.56,
        unitPrice: 10.5,
        grossValue: 12962.88,
        netValue: 12962.88,
      },
      pfUser.token,
    );
    expect(transferOut.type).toBe(InvestmentTransactionType.QuotaTransfer);
    expect(transferIn.type).toBe(InvestmentTransactionType.QuotaTransfer);
    expect(transferOut.type).not.toBe(InvestmentTransactionType.CustodyTransfer);
    expect(transferIn.type).not.toBe(InvestmentTransactionType.CustodyTransfer);

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceHoldingAfter = await findHolding(pfUser.token, brokerageAccount.id, transferFund.id);
    const destinationHolding = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      transferFund.id,
    );
    expect(sourceHoldingAfter).toBeNull();
    expect(destinationHolding).not.toBeNull();
    expectNear(destinationHolding!.quantity, 1234.56, 6);
    expectNear(destinationHolding!.averageCost, 10.5, 6);

    const sourceRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const destinationRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 200,
    });
    expect(sourceRows.some((row) => row.investmentTransactionId === transferOut.id)).toBe(false);
    expect(destinationRows.some((row) => row.investmentTransactionId === transferIn.id)).toBe(false);
  });
});
