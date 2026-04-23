/**
 * E2E tests — Investments / TWR critical invariants
 *
 * Coverage anchors:
 * - requirements/scenarios/04-investment-performance.md: SC-10..20
 * - requirements/scenarios/05-fixed-income.md: SC-01 (entry path)
 * - requirements/scenarios/06-funds.md: SC-08 (come-cotas semantics)
 * - requirements/scenarios/07-equities.md: SC-01, SC-06, SC-08
 * - requirements/scenarios/08-reits.md: SC-01, SC-05, SC-06
 * - requirements/scenarios/09-crypto.md: SC-01, SC-10
 * - twr-manual-tests.md: TWR-MAN-01, 02, 04, 06, 08, 09
 */

import { test, expect } from '../fixtures/investments';
import { formatLocalIsoDate } from '../helpers/banking-graphql';
import {
  AssetType,
  CouponFrequency,
  createInvestmentAsset,
  createInvestmentCashTransaction,
  deleteInvestmentTransaction,
  getGridClassTwrSeries,
  getInvestmentCashBalance,
  getInvestmentCashTransactions,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  getInvestmentTransactions,
  GridClassTwrPeriod,
  HoldingPricingMode,
  Indexer,
  InvestmentCashTransactionType,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  triggerPortfolioRecalculation,
  updateInvestmentTransaction,
} from '../helpers/investment-graphql';

// Keep serial while investment recalculation jobs are still expensive under parallel Playwright load.
test.describe.configure({ mode: 'serial' });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return formatLocalIsoDate(d);
}

async function waitForRecalculation(
  token: string,
  financialAccountId?: string,
): Promise<void> {
  await expect
    .poll(
      async () => {
        const summary = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.All,
        });
        return summary.recalculationStatus.isCalculating;
      },
      { timeout: 45000 },
    )
    .toBe(false);
}

async function findHolding(
  token: string,
  financialAccountId: string,
  assetId: string,
) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

function cashDeltaFromRows(rows: Array<{ creditDebitType: string; amount: number }>): number {
  return rows.reduce((acc, row) => {
    if (row.creditDebitType === 'CREDIT') return acc + row.amount;
    if (row.creditDebitType === 'DEBIT') return acc - row.amount;
    return acc;
  }, 0);
}

test.describe('Investments TWR invariants', () => {
  test('INV-TWR-01: deposit events (new money) do not create artificial return', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Neutralidade Aporte'),
        assetType: AssetType.Stock,
        issuerName: 'Issuer E2E',
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
        source: InvestmentSource.Manual,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 5,
        unitPrice: 100,
        grossValue: 500,
        netValue: 500,
        source: InvestmentSource.Manual,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 15);
    expectNear(holding!.averageCost, 100);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expectNear(summary.performance.twr ?? 0, 0);
  });

  test('INV-TWR-02: withdrawal events (sale/resgate) do not create artificial return', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Neutralidade Saque'),
        assetType: AssetType.Stock,
        issuerName: 'Issuer E2E',
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(18),
        settlementDate: isoDaysAgo(18),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(8),
        settlementDate: isoDaysAgo(8),
        quantity: 4,
        unitPrice: 100,
        grossValue: 400,
        netValue: 400,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 6);
    expectNear(holding!.averageCost, 100);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expectNear(summary.performance.twr ?? 0, 0);
  });

  test('INV-TWR-03: manual custodian cash movements do not alter TWR', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Caixa Manual'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(9),
        type: InvestmentCashTransactionType.Deposit,
        amount: 2000,
        description: 'Aporte manual E2E',
      },
      pfUser.token,
    );

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(7),
        type: InvestmentCashTransactionType.Withdrawal,
        amount: 500,
        description: 'Saque manual E2E',
      },
      pfUser.token,
    );

    const after = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expectNear((after.performance.twr ?? 0) - (before.performance.twr ?? 0), 0);
    expectNear(after.cashBalance - before.cashBalance, 1500);
  });

  test('INV-TWR-04: WITHHOLDING_TAX reduces quotas but does not create cash ledger impact', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Fundo Come-Cotas E2E'),
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
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(25),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    const cashBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.WithholdingTax,
        tradeDate: isoDaysAgo(5),
        quantity: 5,
        unitPrice: 10,
        grossValue: 50,
        incomeTax: 50,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, fund.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 95);

    const cashAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(cashAfter, cashBefore);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    expectNear(summary.performance.twr ?? 0, 0);
  });

  test('INV-TWR-05: closed holdings are excluded from open portfolio totals', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Encerramento Holding'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(5),
        settlementDate: isoDaysAgo(5),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expect(holdings.find((h) => h.asset.id === asset.id)).toBeUndefined();

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expect(summary.totalHoldingsCount).toBe(0);
    expectNear(summary.totalGrossValue, 0);
  });

  test('INV-TWR-06: update/delete transaction replays state and preserves idempotent end result', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Replay Fluxo'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(16),
        settlementDate: isoDaysAgo(16),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(holding).not.toBeNull();
    const txs = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchase = txs.find((tx) => tx.type === InvestmentTransactionType.Purchase);
    expect(purchase).toBeDefined();

    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    await updateInvestmentTransaction(
      purchase!.id,
      {
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(16),
        settlementDate: isoDaysAgo(16),
        quantity: 10,
        unitPrice: 120,
        grossValue: 1200,
        netValue: 1200,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const txsAfterUpdate = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchaseAfterUpdate = txsAfterUpdate.find((tx) => tx.id === purchase!.id);
    expect(purchaseAfterUpdate).toBeDefined();
    expectNear(purchaseAfterUpdate!.grossValue, 1200);
    expectNear(purchaseAfterUpdate!.quantity ?? 0, 10);

    const updatedHolding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(updatedHolding).not.toBeNull();
    expectNear(updatedHolding!.quantity, 10);
    expect(updatedHolding!.averageCost).toBeGreaterThan(0);

    await deleteInvestmentTransaction(purchase!.id, pfUser.token);
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const deletedHolding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(deletedHolding).toBeNull();

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    expect(summary.totalHoldingsCount).toBe(0);
  });

  test('INV-TWR-07: scoped vs consolidated results remain consistent for partitioned positions', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Particionamento'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(14),
        settlementDate: isoDaysAgo(14),
        quantity: 6,
        unitPrice: 100,
        grossValue: 600,
        netValue: 600,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(14),
        settlementDate: isoDaysAgo(14),
        quantity: 4,
        unitPrice: 100,
        grossValue: 400,
        netValue: 400,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);
    await waitForRecalculation(pfUser.token);

    const scopedA = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const scopedB = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const consolidated = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.All,
    });

    expectNear(scopedA.performance.twr ?? 0, 0);
    expectNear(scopedB.performance.twr ?? 0, 0);
    expectNear(consolidated.performance.twr ?? 0, 0);
    expectNear(consolidated.totalGrossValue, scopedA.totalGrossValue + scopedB.totalGrossValue);
  });

  test('INV-TWR-08: CASH grid class returns empty TWR series in holdings-only contract', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação GridClass'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(8),
        settlementDate: isoDaysAgo(8),
        quantity: 2,
        unitPrice: 100,
        grossValue: 200,
        netValue: 200,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const cashSeries = await getGridClassTwrSeries(pfUser.token, 'cash', {
      financialAccountId: brokerageAccount.id,
      period: GridClassTwrPeriod.OneYear,
    });
    expect(cashSeries).toEqual([]);

    const stocksSeries = await getGridClassTwrSeries(pfUser.token, 'stocks', {
      financialAccountId: brokerageAccount.id,
      period: GridClassTwrPeriod.OneYear,
    });
    expect(stocksSeries.length).toBeGreaterThan(0);
  });

  test('INV-TWR-09: triggerPortfolioRecalculation is idempotent for same historical window', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Reprocessamento'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Dividend,
        tradeDate: isoDaysAgo(10),
        grossValue: 50,
        netValue: 50,
        incomeTax: 0,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    await triggerPortfolioRecalculation(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      recalculateFrom: isoDaysAgo(35),
    });
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await triggerPortfolioRecalculation(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      recalculateFrom: isoDaysAgo(35),
    });
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const after = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expectNear(after.totalGrossValue, before.totalGrossValue);
    expectNear(after.totalCostBasis, before.totalCostBasis);
    expectNear(after.performance.twr ?? 0, before.performance.twr ?? 0);
  });

  test('INV-TWR-10: mixed multi-asset timeline remains stable with concurrent events', async ({
    pfUser,
    brokerageAccount,
    cryptoAccount,
  }) => {
    const stock = await createInvestmentAsset(
      { name: uniqueName('Stock Multi'), assetType: AssetType.Stock },
      pfUser.token,
    );
    const reit = await createInvestmentAsset(
      { name: uniqueName('FII Multi'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Fund Multi'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );
    const cdb = await createInvestmentAsset(
      { name: uniqueName('CDB Multi'), assetType: AssetType.Cdb },
      pfUser.token,
    );
    const btc = await createInvestmentAsset(
      {
        name: uniqueName('BTC Multi'),
        assetType: AssetType.Crypto,
        ticker: `BTC${Date.now().toString().slice(-5)}`,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: reit.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(28),
        settlementDate: isoDaysAgo(28),
        quantity: 200,
        unitPrice: 5,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(26),
        settlementDate: isoDaysAgo(26),
        quantity: 50,
        unitPrice: 20,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.MarketData,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(24),
        settlementDate: isoDaysAgo(24),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(24),
        maturityDate: isoDaysAgo(-365),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(22),
        settlementDate: isoDaysAgo(22),
        quantity: 0.05,
        unitPrice: 300000,
        grossValue: 15000,
        netValue: 15000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Dividend,
        tradeDate: isoDaysAgo(18),
        grossValue: 100,
        netValue: 100,
        incomeTax: 0,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: reit.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(16),
        settlementDate: isoDaysAgo(16),
        quantity: 50,
        unitPrice: 5,
        grossValue: 250,
        netValue: 250,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.WithholdingTax,
        tradeDate: isoDaysAgo(14),
        quantity: 2,
        unitPrice: 20,
        grossValue: 40,
        incomeTax: 40,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 0.02,
        unitPrice: 300000,
        grossValue: 6000,
        netValue: 6000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    await waitForRecalculation(pfUser.token);

    const consolidated = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.All,
    });

    expect(consolidated.recalculationStatus.errorCode).toBeNull();
    expect(consolidated.totalHoldingsCount).toBeGreaterThan(0);
    expect(consolidated.totalGrossValue).toBeGreaterThan(0);
    expect(Number.isFinite(consolidated.performance.twr ?? 0)).toBe(true);

    const brokerHoldings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const cryptoHoldings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: cryptoAccount.id,
    });

    expect(brokerHoldings.length).toBeGreaterThanOrEqual(3);
    expect(cryptoHoldings.length).toBeGreaterThanOrEqual(1);
  });

  test('INV-TWR-11: partial sale keeps average cost stable across STOCK, FII, and CRYPTO', async ({
    pfUser,
    brokerageAccount,
    cryptoAccount,
  }) => {
    const scenarios = [
      {
        label: 'STOCK',
        assetType: AssetType.Stock,
        financialAccountId: brokerageAccount.id,
        quantityBuy: 100,
        unitPrice: 20,
        quantitySell: 40,
      },
      {
        label: 'REIT',
        assetType: AssetType.RealEstateFund,
        financialAccountId: brokerageAccount.id,
        quantityBuy: 300,
        unitPrice: 11,
        quantitySell: 120,
      },
      {
        label: 'CRYPTO',
        assetType: AssetType.Crypto,
        financialAccountId: cryptoAccount.id,
        quantityBuy: 0.05,
        unitPrice: 300000,
        quantitySell: 0.02,
        ticker: `BTC${Date.now().toString().slice(-6)}`,
      },
    ] as const;

    for (const s of scenarios) {
      const asset = await createInvestmentAsset(
        {
          name: uniqueName(`Asset ${s.label}`),
          assetType: s.assetType,
          ticker: s.ticker,
        },
        pfUser.token,
      );

      await recordInvestmentTransaction(
        {
          financialAccountId: s.financialAccountId,
          assetId: asset.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          tradeDate: isoDaysAgo(18),
          settlementDate: isoDaysAgo(18),
          quantity: s.quantityBuy,
          unitPrice: s.unitPrice,
          grossValue: s.quantityBuy * s.unitPrice,
          netValue: s.quantityBuy * s.unitPrice,
        },
        pfUser.token,
      );

      await recordInvestmentTransaction(
        {
          financialAccountId: s.financialAccountId,
          assetId: asset.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.Sale,
          tradeDate: isoDaysAgo(10),
          settlementDate: isoDaysAgo(10),
          quantity: s.quantitySell,
          unitPrice: s.unitPrice,
          grossValue: s.quantitySell * s.unitPrice,
          netValue: s.quantitySell * s.unitPrice,
        },
        pfUser.token,
      );

      await waitForRecalculation(pfUser.token, s.financialAccountId);

      const holding = await findHolding(pfUser.token, s.financialAccountId, asset.id);
      expect(holding).not.toBeNull();
      expectNear(holding!.averageCost, s.unitPrice, 6);
      expectNear(holding!.quantity, s.quantityBuy - s.quantitySell, 6);
    }
  });

  test('INV-TWR-12: dividend, JCP, and stock lending do not change holding quantity/cost basis', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Proventos'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 100,
        unitPrice: 50,
        grossValue: 5000,
        netValue: 5000,
      },
      pfUser.token,
    );

    const incomeEvents = [
      { type: InvestmentTransactionType.Dividend, gross: 100, net: 100, incomeTax: 0 },
      { type: InvestmentTransactionType.InterestOnEquity, gross: 100, net: 85, incomeTax: 15 },
      { type: InvestmentTransactionType.StockLending, gross: 80, net: 68, incomeTax: 12 },
    ] as const;

    for (const [idx, event] of incomeEvents.entries()) {
      await recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: asset.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: event.type,
          tradeDate: isoDaysAgo(12 - idx),
          grossValue: event.gross,
          netValue: event.net,
          incomeTax: event.incomeTax,
        },
        pfUser.token,
      );
    }

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, asset.id);
    expect(holding).not.toBeNull();
    expectNear(holding!.quantity, 100);
    expectNear(holding!.averageCost, 50);

    const txs = await getInvestmentTransactions(holding!.id, pfUser.token);
    expect(txs.some((tx) => tx.type === InvestmentTransactionType.Dividend)).toBe(true);
    expect(txs.some((tx) => tx.type === InvestmentTransactionType.InterestOnEquity)).toBe(true);
    expect(txs.some((tx) => tx.type === InvestmentTransactionType.StockLending)).toBe(true);
  });

  test('INV-TWR-13: initial quota is 1.00, deposit does not change quota price, and TWR matches quota ratio', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação Quota 1'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 5,
        unitPrice: 100,
        grossValue: 500,
        netValue: 500,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const series = await getGridClassTwrSeries(pfUser.token, 'stocks', {
      financialAccountId: brokerageAccount.id,
      period: GridClassTwrPeriod.All,
    });
    expect(series.length).toBeGreaterThan(0);

    const firstQuota = series[0].quotaValue;
    const lastQuota = series[series.length - 1].quotaValue;
    expectNear(firstQuota, 1, 6);
    expectNear(lastQuota, firstQuota, 6);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    const twrFromQuota = (lastQuota / firstQuota) - 1;
    expectNear(summary.performance.twr ?? 0, twrFromQuota, 6);
  });

  test('INV-TWR-14: summary updates on next query after transaction (on-read recalculation behavior)', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Ação OnRead'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(5),
        settlementDate: isoDaysAgo(5),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await expect
      .poll(
        async () => {
          const summary = await getInvestmentPortfolioSummary(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            period: PerformancePeriod.All,
          });
          return summary.totalHoldingsCount;
        },
        { timeout: 45000 },
      )
      .toBeGreaterThan(before.totalHoldingsCount);
  });

  test('INV-TWR-15: manual valuation update is reflected in subsequent summary reads', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Fundo Valorização Manual'),
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
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: {
          valuedAt: isoDaysAgo(15),
          grossValue: 1000,
        },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, fund.id);
    expect(holding).not.toBeNull();
    const txs = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchase = txs.find((tx) => tx.type === InvestmentTransactionType.Purchase);
    expect(purchase).toBeDefined();

    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    await updateInvestmentTransaction(
      purchase!.id,
      {
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: {
          valuedAt: isoDaysAgo(1),
          grossValue: 1200,
        },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await expect
      .poll(
        async () => {
          const after = await getInvestmentPortfolioSummary(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            period: PerformancePeriod.All,
          });
          return after.totalGrossValue;
        },
        { timeout: 45000 },
      )
      .toBeGreaterThan(before.totalGrossValue);
  });

  test('INV-TWR-16: PURCHASE creates a linked DEBIT cash ledger entry', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação Cash DEBIT'), assetType: AssetType.Stock },
      pfUser.token,
    );

    const tx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(7),
        settlementDate: isoDaysAgo(7),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await expect
      .poll(
        async () => {
          const cashRows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            first: 500,
          });
          return cashRows.find((row) => row.investmentTransactionId === tx.id) ?? null;
        },
        { timeout: 20000 },
      )
      .toMatchObject({
        creditDebitType: 'DEBIT',
        amount: 1000,
      });
  });

  test('INV-TWR-17: SALE creates a linked CREDIT cash ledger entry', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação Cash CREDIT'), assetType: AssetType.Stock },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    const sale = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(6),
        settlementDate: isoDaysAgo(6),
        quantity: 4,
        unitPrice: 100,
        grossValue: 400,
        netValue: 400,
      },
      pfUser.token,
    );

    await expect
      .poll(
        async () => {
          const cashRows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
          });
          return cashRows.find((row) => row.investmentTransactionId === sale.id) ?? null;
        },
        { timeout: 20000 },
      )
      .toMatchObject({
        creditDebitType: 'CREDIT',
        amount: 400,
      });
  });

  test('INV-TWR-18: income events create linked CREDIT cash rows', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const stock = await createInvestmentAsset(
      { name: uniqueName('Ação Renda'), assetType: AssetType.Stock },
      pfUser.token,
    );
    const cdb = await createInvestmentAsset(
      { name: uniqueName('CDB Renda'), assetType: AssetType.Cdb },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(20),
        maturityDate: isoDaysAgo(-365),
      },
      pfUser.token,
    );

    const events = await Promise.all([
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: stock.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Dividend,
          tradeDate: isoDaysAgo(10),
          grossValue: 100,
          netValue: 100,
          incomeTax: 0,
        },
        pfUser.token,
      ),
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: cdb.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.InterestPayment,
          tradeDate: isoDaysAgo(10),
          grossValue: 100,
          netValue: 85,
          incomeTax: 15,
        },
        pfUser.token,
      ),
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: cdb.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Amortization,
          tradeDate: isoDaysAgo(9),
          grossValue: 100,
          netValue: 100,
          incomeTax: 0,
        },
        pfUser.token,
      ),
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: stock.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.StockLending,
          tradeDate: isoDaysAgo(8),
          grossValue: 80,
          netValue: 68,
          incomeTax: 12,
        },
        pfUser.token,
      ),
    ]);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    for (const event of events) {
      const linked = cashRows.find((row) => row.investmentTransactionId === event.id);
      expect(linked).toBeDefined();
      expect(linked!.creditDebitType).toBe('CREDIT');
    }
  });

  test('INV-TWR-19: updating investment transaction keeps a single linked cash row (idempotent)', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação Idempotência Cash'), assetType: AssetType.Stock },
      pfUser.token,
    );

    const purchase = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await updateInvestmentTransaction(
      purchase.id,
      {
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 12,
        unitPrice: 100,
        grossValue: 1200,
        netValue: 1200,
      },
      pfUser.token,
    );

    await expect
      .poll(
        async () => {
          const rows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
          });
          const linked = rows.filter((row) => row.investmentTransactionId === purchase.id);
          return {
            count: linked.length,
            amount: linked[0]?.amount ?? 0,
          };
        },
        { timeout: 20000 },
      )
      .toEqual({ count: 1, amount: 1200 });
  });

  test('INV-TWR-20: deleting investment transaction removes linked cash row', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação Delete Cash'), assetType: AssetType.Stock },
      pfUser.token,
    );

    const purchase = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await expect
      .poll(
        async () => {
          const rows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
          });
          return rows.some((row) => row.investmentTransactionId === purchase.id);
        },
        { timeout: 20000 },
      )
      .toBe(true);

    await deleteInvestmentTransaction(purchase.id, pfUser.token);

    await expect
      .poll(
        async () => {
          const rows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
          });
          return rows.some((row) => row.investmentTransactionId === purchase.id);
        },
        { timeout: 20000 },
      )
      .toBe(false);
  });

  test('INV-TWR-21: cash balance equals ledger sum and can be negative', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const balanceBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(4),
        type: InvestmentCashTransactionType.Withdrawal,
        amount: 1000,
        description: 'Saque manual para saldo negativo',
      },
      pfUser.token,
    );
    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(3),
        type: InvestmentCashTransactionType.Deposit,
        amount: 200,
        description: 'Aporte parcial',
      },
      pfUser.token,
    );

    const balanceAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    expectNear(balanceAfter - balanceBefore, -800, 6);
    expect(balanceAfter).toBeLessThan(balanceBefore);
  });

  test('INV-TWR-22: consolidated cash balance equals sum of scoped balances', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const scopedABefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const scopedBBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });
    const consolidatedBefore = await getInvestmentCashBalance(pfUser.token);

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(2),
        type: InvestmentCashTransactionType.Deposit,
        amount: 300,
      },
      pfUser.token,
    );
    await createInvestmentCashTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        date: isoDaysAgo(2),
        type: InvestmentCashTransactionType.Deposit,
        amount: 200,
      },
      pfUser.token,
    );
    await createInvestmentCashTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        date: isoDaysAgo(1),
        type: InvestmentCashTransactionType.Withdrawal,
        amount: 50,
      },
      pfUser.token,
    );

    const scopedAAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const scopedBAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });
    const consolidatedAfter = await getInvestmentCashBalance(pfUser.token);

    const scopedADelta = scopedAAfter - scopedABefore;
    const scopedBDelta = scopedBAfter - scopedBBefore;
    const consolidatedDelta = consolidatedAfter - consolidatedBefore;

    expectNear(scopedADelta, 300, 6);
    expectNear(scopedBDelta, 150, 6);
    expectNear(consolidatedDelta, scopedADelta + scopedBDelta, 6);
  });
});
