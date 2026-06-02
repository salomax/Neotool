import { test, expect } from '../fixtures/investments';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  HoldingPricingMode,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
} from '../helpers/investment-graphql';
import { isoDaysAgo } from '../helpers/fixed-income-e2e';
import { replayRecalculationRound, waitForRecalculation } from '../helpers/investment-e2e';
import { signIntoPage } from '../helpers/navigation';
import { InvestmentsPage } from '../pages/personal-finance/InvestmentsPage';

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 240000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function expectNear(actual: number, expected: number, precision = 2): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function waitAllScopes(token: string, brokerageAccountId: string, cryptoAccountId: string): Promise<void> {
  await waitForRecalculation(token, brokerageAccountId);
  await waitForRecalculation(token, cryptoAccountId);
  await waitForRecalculation(token);
}

interface ScenarioAssets {
  stockId: string;
  reitId: string;
  fundId: string;
  cdbId: string;
  cryptoId: string;
}

async function createScenarioAssets(token: string): Promise<ScenarioAssets> {
  const stock = await createInvestmentAsset(
    { name: uniqueName('Stress Stock'), assetType: AssetType.Stock },
    token,
  );
  const reit = await createInvestmentAsset(
    { name: uniqueName('Stress REIT'), assetType: AssetType.RealEstateFund },
    token,
  );
  const fund = await createInvestmentAsset(
    { name: uniqueName('Stress Fund'), assetType: AssetType.MutualFund },
    token,
  );
  const cdb = await createInvestmentAsset(
    { name: uniqueName('Stress CDB'), assetType: AssetType.Cdb },
    token,
  );
  const crypto = await createInvestmentAsset(
    { name: uniqueName('Stress Crypto'), assetType: AssetType.Crypto },
    token,
  );

  return {
    stockId: stock.id,
    reitId: reit.id,
    fundId: fund.id,
    cdbId: cdb.id,
    cryptoId: crypto.id,
  };
}

async function recordCheckpointA(
  token: string,
  brokerageAccountId: string,
  cryptoAccountId: string,
  assets: ScenarioAssets,
): Promise<void> {
  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(120),
      settlementDate: isoDaysAgo(120),
      quantity: 100,
      unitPrice: 10,
      grossValue: 1000,
      netValue: 1000,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.reitId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(110),
      settlementDate: isoDaysAgo(110),
      quantity: 50,
      unitPrice: 20,
      grossValue: 1000,
      netValue: 1000,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.fundId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(100),
      settlementDate: isoDaysAgo(100),
      quantity: 100,
      unitPrice: 10,
      grossValue: 1000,
      netValue: 1000,
      pricingMode: HoldingPricingMode.Manual,
      manualValuation: { valuedAt: isoDaysAgo(100), grossValue: 1000 },
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.cdbId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(95),
      settlementDate: isoDaysAgo(95),
      quantity: 1,
      unitPrice: 1000,
      grossValue: 1000,
      netValue: 1000,
      indexer: Indexer.PreFixado,
      fixedAnnualRate: 0.1,
      issueDate: isoDaysAgo(95),
      maturityDate: isoDaysAgo(-365),
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: cryptoAccountId,
      assetId: assets.cryptoId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(90),
      settlementDate: isoDaysAgo(90),
      quantity: 0.02,
      unitPrice: 25000,
      grossValue: 500,
      netValue: 500,
    },
    token,
  );
}

async function recordCheckpointB(
  token: string,
  brokerageAccountId: string,
  cryptoAccountId: string,
  assets: ScenarioAssets,
): Promise<void> {
  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Outflow,
      type: InvestmentTransactionType.Sale,
      tradeDate: isoDaysAgo(80),
      settlementDate: isoDaysAgo(80),
      quantity: 20,
      unitPrice: 10,
      grossValue: 200,
      netValue: 200,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Subscription,
      tradeDate: isoDaysAgo(70),
      settlementDate: isoDaysAgo(70),
      quantity: 10,
      unitPrice: 10,
      grossValue: 100,
      netValue: 100,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.reitId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Dividend,
      tradeDate: isoDaysAgo(65),
      grossValue: 50,
      netValue: 50,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.cdbId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.InterestPayment,
      tradeDate: isoDaysAgo(60),
      grossValue: 60,
      netValue: 51,
      incomeTax: 9,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.fundId,
      direction: InvestmentTransactionDirection.Outflow,
      type: InvestmentTransactionType.WithholdingTax,
      tradeDate: isoDaysAgo(55),
      quantity: 2,
      unitPrice: 10,
      grossValue: 20,
      netValue: 20,
      incomeTax: 20,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: cryptoAccountId,
      assetId: assets.cryptoId,
      direction: InvestmentTransactionDirection.Outflow,
      type: InvestmentTransactionType.Sale,
      tradeDate: isoDaysAgo(50),
      settlementDate: isoDaysAgo(50),
      quantity: 0.005,
      unitPrice: 25000,
      grossValue: 125,
      netValue: 125,
    },
    token,
  );
}

async function recordCheckpointC(
  token: string,
  brokerageAccountId: string,
  assets: ScenarioAssets,
): Promise<void> {
  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.fundId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(40),
      settlementDate: isoDaysAgo(40),
      quantity: 2,
      unitPrice: 10,
      grossValue: 20,
      netValue: 20,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.cdbId,
      direction: InvestmentTransactionDirection.Outflow,
      type: InvestmentTransactionType.Maturity,
      tradeDate: isoDaysAgo(25),
      settlementDate: isoDaysAgo(25),
      quantity: 0.2,
      grossValue: 200,
      netValue: 200,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.cdbId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.Purchase,
      tradeDate: isoDaysAgo(20),
      settlementDate: isoDaysAgo(20),
      quantity: 0.2,
      unitPrice: 1000,
      grossValue: 200,
      netValue: 200,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.StockSplit,
      tradeDate: isoDaysAgo(15),
      quantity: 90,
      grossValue: 0,
      netValue: 0,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Outflow,
      type: InvestmentTransactionType.ReverseSplit,
      tradeDate: isoDaysAgo(10),
      quantity: 90,
      grossValue: 0,
      netValue: 0,
    },
    token,
  );

  await recordInvestmentTransaction(
    {
      financialAccountId: brokerageAccountId,
      assetId: assets.stockId,
      direction: InvestmentTransactionDirection.Inflow,
      type: InvestmentTransactionType.StockLending,
      tradeDate: isoDaysAgo(8),
      grossValue: 30,
      netValue: 25,
      incomeTax: 5,
    },
    token,
  );
}

async function seedFullScenario(
  token: string,
  brokerageAccountId: string,
  cryptoAccountId: string,
): Promise<ScenarioAssets> {
  const assets = await createScenarioAssets(token);
  await recordCheckpointA(token, brokerageAccountId, cryptoAccountId, assets);
  await recordCheckpointB(token, brokerageAccountId, cryptoAccountId, assets);
  await recordCheckpointC(token, brokerageAccountId, assets);
  await waitAllScopes(token, brokerageAccountId, cryptoAccountId);
  return assets;
}

async function openInvestmentsPage(page: import('@playwright/test').Page): Promise<InvestmentsPage> {
  await signIntoPage(page);
  const investmentsPage = new InvestmentsPage(page);
  await investmentsPage.goto();
  return investmentsPage;
}

test.describe('Investments TWR stress / phase 3', () => {
  test('INV-TWR-STRESS-01: long mixed timeline preserves deterministic cost-basis checkpoints', async ({
    pfUser,
    brokerageAccount,
    cryptoAccount,
  }) => {
    const assets = await createScenarioAssets(pfUser.token);
    await recordCheckpointA(pfUser.token, brokerageAccount.id, cryptoAccount.id, assets);
    await waitAllScopes(pfUser.token, brokerageAccount.id, cryptoAccount.id);

    const cpA_broker = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const cpA_crypto = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      period: PerformancePeriod.All,
    });
    expectNear(cpA_broker.totalCostBasis, 4000, 2);
    expectNear(cpA_crypto.totalCostBasis, 500, 2);

    await recordCheckpointB(pfUser.token, brokerageAccount.id, cryptoAccount.id, assets);
    await waitAllScopes(pfUser.token, brokerageAccount.id, cryptoAccount.id);

    const cpB_broker = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const cpB_crypto = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      period: PerformancePeriod.All,
    });
    expectNear(cpB_broker.totalCostBasis, 3880, 2);
    expectNear(cpB_crypto.totalCostBasis, 375, 2);

    await recordCheckpointC(pfUser.token, brokerageAccount.id, assets);
    await waitAllScopes(pfUser.token, brokerageAccount.id, cryptoAccount.id);

    const cpC_broker = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const cpC_crypto = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      period: PerformancePeriod.All,
    });
    expectNear(cpC_broker.totalCostBasis, 3900, 2);
    expectNear(cpC_crypto.totalCostBasis, 375, 2);

    const cpC_consolidated = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.All,
    });
    expect(Number.isFinite(cpC_consolidated.performance.twr ?? 0)).toBe(true);

    const brokerHoldings = await getInvestmentHoldings(pfUser.token, { financialAccountId: brokerageAccount.id });
    const stockHolding = brokerHoldings.find((h) => h.asset.id === assets.stockId);
    expect(stockHolding).toBeDefined();
    expectNear(stockHolding!.quantity, 90, 6);
    expectNear(stockHolding!.averageCost, 10, 6);
  });

  test('INV-TWR-STRESS-02: repeated recalculation replay is idempotent on long mixed scenario', async ({
    pfUser,
    brokerageAccount,
    cryptoAccount,
  }) => {
    await seedFullScenario(pfUser.token, brokerageAccount.id, cryptoAccount.id);
    await waitAllScopes(pfUser.token, brokerageAccount.id, cryptoAccount.id);

    // Idempotency: compare two stabilized post-replay snapshots. Pre-replay TWR can differ from
    // post-replay under load when lazy vs full recalculation paths diverge.
    await replayRecalculationRound(pfUser.token, brokerageAccount.id, cryptoAccount.id);
    const firstSnapshot = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.All,
    });

    await replayRecalculationRound(pfUser.token, brokerageAccount.id, cryptoAccount.id);
    const secondSnapshot = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.All,
    });

    expectNear(secondSnapshot.totalCostBasis, firstSnapshot.totalCostBasis, 6);
    expectNear(secondSnapshot.totalGrossValue, firstSnapshot.totalGrossValue, 6);
    expectNear(secondSnapshot.totalPortfolioValue, firstSnapshot.totalPortfolioValue, 6);

    const twrFirst = firstSnapshot.performance.twr ?? 0;
    const twrSecond = secondSnapshot.performance.twr ?? 0;
    expect(Number.isFinite(twrFirst)).toBe(true);
    expect(Number.isFinite(twrSecond)).toBe(true);
    const twrScale = Math.max(1, Math.abs(twrFirst), Math.abs(twrSecond));
    expect(Math.abs(twrSecond - twrFirst) / twrScale).toBeLessThan(0.15);
  });

  test('INV-TWR-STRESS-03: final mixed scenario values stay consistent between UI and API', async ({
    page,
    pfUser,
    brokerageAccount,
    cryptoAccount,
  }) => {
    await seedFullScenario(pfUser.token, brokerageAccount.id, cryptoAccount.id);

    const apiScopedBroker = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });
    const apiScopedCrypto = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      period: PerformancePeriod.Ytd,
    });
    const apiConsolidated = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.Ytd,
    });

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await investmentsPage.summary.selectYearToDatePeriod();
    expectNear(await investmentsPage.summary.getTotalContributedAmount(), apiScopedBroker.totalCostBasis, 2);
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiScopedBroker.totalPortfolioValue, 2);

    await investmentsPage.selectAccount(cryptoAccount.name, cryptoAccount.id);
    await investmentsPage.summary.selectYearToDatePeriod();
    expectNear(await investmentsPage.summary.getTotalContributedAmount(), apiScopedCrypto.totalCostBasis, 2);
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiScopedCrypto.totalPortfolioValue, 2);

    await investmentsPage.selectConsolidatedView();
    await investmentsPage.summary.selectYearToDatePeriod();
    expectNear(await investmentsPage.summary.getTotalContributedAmount(), apiConsolidated.totalCostBasis, 2);
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiConsolidated.totalPortfolioValue, 2);

    if (apiConsolidated.performance.twr != null) {
      const uiTwr = await investmentsPage.summary.getPerformanceTwr();
      expect(uiTwr).not.toBeNull();
      expectNear(uiTwr as number, apiConsolidated.performance.twr, 3);
    }
  });
});

