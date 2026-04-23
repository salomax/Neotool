import { test, expect } from '../fixtures/investments';
import { formatLocalIsoDate } from '../helpers/banking-graphql';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  getInvestmentTransactions,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  updateInvestmentTransaction,
} from '../helpers/investment-graphql';
import { signIntoPage } from '../helpers/navigation';
import { InvestmentsPage } from '../pages/personal-finance/InvestmentsPage';

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 180000 });

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return formatLocalIsoDate(d);
}

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

async function waitForRecalculation(token: string, financialAccountId?: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const summary = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.Ytd,
        });
        return summary.recalculationStatus.isCalculating;
      },
      { timeout: 45000 },
    )
    .toBe(false);
}

function expectNear(actual: number, expected: number, precision = 2): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function openInvestmentsPage(page: import('@playwright/test').Page): Promise<InvestmentsPage> {
  await signIntoPage(page);
  const investmentsPage = new InvestmentsPage(page);
  await investmentsPage.goto();
  return investmentsPage;
}

test.describe('Investments TWR UI parity', () => {
  test('INV-TWR-UI-01: summary amounts on screen match API summary for scoped account', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação UI Summary Scoped'), assetType: AssetType.Stock },
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

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const apiSummary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiSummary.totalPortfolioValue);
    expectNear(await investmentsPage.summary.getTotalContributedAmount(), apiSummary.totalCostBasis);
    expectNear(await investmentsPage.summary.getGrossGainAmount(), apiSummary.grossGain);
    expectNear(await investmentsPage.summary.getCashBalanceAmount(), apiSummary.cashBalance);
  });

  test('INV-TWR-UI-02: switching account scope updates UI totals consistently with API', async ({
    page,
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const assetA = await createInvestmentAsset(
      { name: uniqueName('Ação UI Scope A'), assetType: AssetType.Stock },
      pfUser.token,
    );
    const assetB = await createInvestmentAsset(
      { name: uniqueName('Ação UI Scope B'), assetType: AssetType.Stock },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: assetA.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(14),
        settlementDate: isoDaysAgo(14),
        quantity: 10,
        unitPrice: 100,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: assetB.id,
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

    const apiScopedA = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });
    const apiScopedB = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });
    const apiConsolidated = await getInvestmentPortfolioSummary(pfUser.token, {
      period: PerformancePeriod.Ytd,
    });

    const investmentsPage = await openInvestmentsPage(page);

    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiScopedA.totalPortfolioValue);

    await investmentsPage.selectAccount(secondaryBrokerageAccount.name, secondaryBrokerageAccount.id);
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiScopedB.totalPortfolioValue);

    await investmentsPage.selectConsolidatedView();
    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiConsolidated.totalPortfolioValue);
  });

  test('INV-TWR-UI-03: new transaction is reflected in total contributed on screen', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const asset = await createInvestmentAsset(
      { name: uniqueName('Ação UI Live Update'), assetType: AssetType.Stock },
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
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    const beforeContributed = await investmentsPage.summary.getTotalContributedAmount();

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(6),
        settlementDate: isoDaysAgo(6),
        quantity: 5,
        unitPrice: 100,
        grossValue: 500,
        netValue: 500,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const apiAfter = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });

    await investmentsPage.goto();
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    const afterContributed = await investmentsPage.summary.getTotalContributedAmount();
    expectNear(afterContributed, apiAfter.totalCostBasis, 2);
    expect(afterContributed).toBeGreaterThan(beforeContributed);
  });

  test('INV-TWR-UI-04: manual valuation update is reflected on summary UI', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      { name: uniqueName('Fundo UI Manual Valuation'), assetType: AssetType.MutualFund },
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
        manualValuation: { valuedAt: isoDaysAgo(15), grossValue: 1000 },
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdings = await getInvestmentHoldings(pfUser.token, { financialAccountId: brokerageAccount.id });
    const holding = holdings.find((h) => h.asset.id === fund.id);
    expect(holding).toBeDefined();
    const txs = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchase = txs.find((tx) => tx.type === InvestmentTransactionType.Purchase);
    expect(purchase).toBeDefined();

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    const beforeTotalPortfolio = await investmentsPage.summary.getTotalPortfolioValue();

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
        manualValuation: { valuedAt: isoDaysAgo(1), grossValue: 1300 },
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const apiAfter = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });

    await investmentsPage.goto();
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    const afterTotalPortfolio = await investmentsPage.summary.getTotalPortfolioValue();
    expectNear(afterTotalPortfolio, apiAfter.totalPortfolioValue, 2);
    expect(afterTotalPortfolio).toBeGreaterThan(beforeTotalPortfolio);
  });

  test('INV-TWR-UI-05: TWR badge shown in UI matches API performance.twr', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const fund = await createInvestmentAsset(
      { name: uniqueName('Fundo UI TWR Badge'), assetType: AssetType.MutualFund },
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
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt: isoDaysAgo(1), grossValue: 1200 },
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const apiSummary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.Ytd,
    });
    expect(apiSummary.performance.twr).not.toBeNull();

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await investmentsPage.summary.selectYearToDatePeriod();

    await expect
      .poll(async () => investmentsPage.summary.getPerformanceTwr(), { timeout: 45000 })
      .not.toBeNull();

    const uiTwr = await investmentsPage.summary.getPerformanceTwr();
    expect(uiTwr).not.toBeNull();
    expectNear(uiTwr as number, apiSummary.performance.twr as number, 3);
  });
});
