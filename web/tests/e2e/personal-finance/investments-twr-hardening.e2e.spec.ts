import { randomBytes } from 'node:crypto';

import { test, expect } from '../fixtures/investments';
import { createFinancialAccount } from '../helpers/banking-graphql';
import { isoDaysAgo } from '../helpers/fixed-income-e2e';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  getInvestmentTransactions,
  getPortfolioTwrDebug,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  Indexer,
  PerformancePeriod,
  recordInvestmentTransaction,
  updateInvestmentTransaction,
} from '../helpers/investment-graphql';
import { waitForRecalculation } from '../helpers/investment-e2e';
import { E2E_RECALC_TIMEOUT_MS } from '../helpers/timeouts';

async function waitForPortfolioTwr(
  token: string,
  financialAccountId: string,
): Promise<number> {
  let twr: number | null = null;
  await expect
    .poll(
      async () => {
        const summary = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.All,
        });
        twr = summary.performance.twr;
        return twr != null;
      },
      { timeout: E2E_RECALC_TIMEOUT_MS },
    )
    .toBe(true);
  return twr!;
}

async function waitForStablePortfolioSummary(
  token: string,
  financialAccountId: string,
) {
  let stable:
    | Awaited<ReturnType<typeof getInvestmentPortfolioSummary>>
    | null = null;

  await expect
    .poll(
      async () => {
        const first = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.All,
        });
        const second = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.All,
        });

        const twrDiff = Math.abs((first.performance.twr ?? 0) - (second.performance.twr ?? 0));
        const portfolioValueDiff = Math.abs(first.totalPortfolioValue - second.totalPortfolioValue);

        if (twrDiff <= 0.000005 && portfolioValueDiff <= 0.000001) {
          stable = second;
          return true;
        }

        return false;
      },
      { timeout: E2E_RECALC_TIMEOUT_MS },
    )
    .toBe(true);

  return stable!;
}

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 300000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

function expectAbsDiffWithin(actual: number, expected: number, epsilon: number): void {
  expect(Math.abs(actual - expected)).toBeLessThanOrEqual(epsilon);
}

async function findHolding(token: string, financialAccountId: string, assetId: string) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

test.describe('Investments TWR hardening', () => {
  test('INV-TWR-HARD-01: no-op transaction replay is idempotent after recalculation convergence', async ({
    pfUser,
  }) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Hardening idempotent account'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Hardening idempotent fund'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );

    const tradeDate = isoDaysAgo(45);
    const valuedAt = isoDaysAgo(1);

    await recordInvestmentTransaction(
      {
        financialAccountId: account.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt, grossValue: 1120 },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, account.id);

    const holding = await findHolding(pfUser.token, account.id, fund.id);
    expect(holding).not.toBeNull();
    const txs = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchaseTx = txs.find((tx) => tx.type === InvestmentTransactionType.Purchase);
    expect(purchaseTx).toBeTruthy();

    const before = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });

    await updateInvestmentTransaction(
      purchaseTx!.id,
      {
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt, grossValue: 1120 },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, account.id);

    const after = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });

    expectNear(after.totalCostBasis, before.totalCostBasis, 8);
    expectNear(after.totalGrossValue, before.totalGrossValue, 8);
    expectNear(after.totalPortfolioValue, before.totalPortfolioValue, 8);
    expectNear(after.performance.twr ?? 0, before.performance.twr ?? 0, 8);

    const replayA = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });
    const replayB = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });
    expectNear(replayA.totalPortfolioValue, after.totalPortfolioValue, 8);
    expectNear(replayB.totalPortfolioValue, after.totalPortfolioValue, 8);
    expectNear(replayA.performance.twr ?? 0, after.performance.twr ?? 0, 8);
    expectNear(replayB.performance.twr ?? 0, after.performance.twr ?? 0, 8);
  });

  test('INV-TWR-HARD-02: proportional partitioning across accounts preserves return ratio', async ({
    pfUser,
  }) => {
    const accountLarge = await createFinancialAccount(
      {
        name: uniqueName('Hardening partition large'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );
    const accountSmall = await createFinancialAccount(
      {
        name: uniqueName('Hardening partition small'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    const fundLarge = await createInvestmentAsset(
      {
        name: uniqueName('Hardening partition fund large'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );
    const fundSmall = await createInvestmentAsset(
      {
        name: uniqueName('Hardening partition fund small'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );

    const tradeDate = isoDaysAgo(35);
    const valuedAt = isoDaysAgo(1);

    await recordInvestmentTransaction(
      {
        financialAccountId: accountLarge.id,
        assetId: fundLarge.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt, grossValue: 1150 },
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: accountSmall.id,
        assetId: fundSmall.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 25,
        unitPrice: 10,
        grossValue: 250,
        netValue: 250,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt, grossValue: 287.5 },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, accountLarge.id);
    await waitForRecalculation(pfUser.token, accountSmall.id);

    const largeTwr = await waitForPortfolioTwr(pfUser.token, accountLarge.id);
    const smallTwr = await waitForPortfolioTwr(pfUser.token, accountSmall.id);

    const largeSummary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: accountLarge.id,
      period: PerformancePeriod.All,
    });
    const smallSummary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: accountSmall.id,
      period: PerformancePeriod.All,
    });

    expectNear(largeTwr, smallTwr, 8);
    expectNear(largeSummary.grossGainPercent, smallSummary.grossGainPercent, 8);
    expectNear(largeSummary.totalCostBasis / smallSummary.totalCostBasis, 4, 8);
    expectNear(largeSummary.totalGrossValue / smallSummary.totalGrossValue, 4, 8);
  });

  test('INV-TWR-HARD-03: same-day equivalent transaction reordering yields equivalent final performance', async ({
    pfUser,
  }) => {
    const accountA = await createFinancialAccount(
      {
        name: uniqueName('Hardening reorder A'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );
    const accountB = await createFinancialAccount(
      {
        name: uniqueName('Hardening reorder B'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    const asset = await createInvestmentAsset(
      {
        name: uniqueName('Hardening reorder cdb'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    const tradeDate = isoDaysAgo(30);
    const maturityDate = isoDaysAgo(-365);

    await recordInvestmentTransaction(
      {
        financialAccountId: accountA.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 1,
        unitPrice: 400,
        grossValue: 400,
        netValue: 400,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: tradeDate,
        maturityDate,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: accountA.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 1,
        unitPrice: 600,
        grossValue: 600,
        netValue: 600,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: tradeDate,
        maturityDate,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: accountB.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 1,
        unitPrice: 600,
        grossValue: 600,
        netValue: 600,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: tradeDate,
        maturityDate,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: accountB.id,
        assetId: asset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 1,
        unitPrice: 400,
        grossValue: 400,
        netValue: 400,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: tradeDate,
        maturityDate,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, accountA.id);
    await waitForRecalculation(pfUser.token, accountB.id);

    const holdingA = await findHolding(pfUser.token, accountA.id, asset.id);
    const holdingB = await findHolding(pfUser.token, accountB.id, asset.id);
    expect(holdingA).not.toBeNull();
    expect(holdingB).not.toBeNull();

    expectNear(holdingA!.quantity, 2, 8);
    expectNear(holdingB!.quantity, 2, 8);
    expectNear(holdingA!.averageCost, 500, 8);
    expectNear(holdingB!.averageCost, 500, 8);
    expectNear(holdingA!.grossValue ?? 0, holdingB!.grossValue ?? 0, 8);

    const summaryA = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: accountA.id,
      period: PerformancePeriod.All,
    });
    const summaryB = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: accountB.id,
      period: PerformancePeriod.All,
    });

    expectNear(summaryA.totalCostBasis, summaryB.totalCostBasis, 8);
    expectNear(summaryA.totalGrossValue, summaryB.totalGrossValue, 8);
    expectNear(summaryA.totalPortfolioValue, summaryB.totalPortfolioValue, 8);
    expectAbsDiffWithin(summaryA.performance.twr ?? 0, summaryB.performance.twr ?? 0, 0.00002);
  });

  test('INV-TWR-HARD-04: timeline replay under multiple no-op updates remains idempotent', async ({
    pfUser,
  }) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Hardening replay account'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );
    const stock = await createInvestmentAsset(
      {
        name: uniqueName('Hardening replay stock'),
        assetType: AssetType.Stock,
        ticker: `HR${randomBytes(3).toString('hex').toUpperCase()}`,
      },
      pfUser.token,
    );

    const purchaseDays = [45, 42, 39, 36, 33, 30, 27, 24];
    for (const [idx, daysAgo] of purchaseDays.entries()) {
      const quantity = 10 + idx;
      const unitPrice = 10 + idx * 0.5;
      const grossValue = quantity * unitPrice;
      await recordInvestmentTransaction(
        {
          financialAccountId: account.id,
          assetId: stock.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          tradeDate: isoDaysAgo(daysAgo),
          settlementDate: isoDaysAgo(daysAgo),
          quantity,
          unitPrice,
          grossValue,
          netValue: grossValue,
        },
        pfUser.token,
      );
    }

    await waitForRecalculation(pfUser.token, account.id);

    const holding = await findHolding(pfUser.token, account.id, stock.id);
    expect(holding).not.toBeNull();
    const transactions = await getInvestmentTransactions(holding!.id, pfUser.token);
    const purchases = transactions
      .filter((tx) => tx.type === InvestmentTransactionType.Purchase)
      .sort((a, b) => a.tradeDate.localeCompare(b.tradeDate));
    expect(purchases.length).toBeGreaterThanOrEqual(8);

    const before = await waitForStablePortfolioSummary(pfUser.token, account.id);

    for (const tx of purchases.slice(0, 5)) {
      const updateInput: {
        direction: InvestmentTransactionDirection;
        type: InvestmentTransactionType;
        tradeDate: string;
        grossValue: number;
        settlementDate?: string;
        quantity?: number;
        unitPrice?: number;
        netValue?: number;
        incomeTax?: number;
        iof?: number;
      } = {
        direction: tx.direction,
        type: tx.type,
        tradeDate: tx.tradeDate,
        grossValue: tx.grossValue,
      };
      if (tx.settlementDate) updateInput.settlementDate = tx.settlementDate;
      if (tx.quantity != null) updateInput.quantity = tx.quantity;
      if (tx.unitPrice != null) updateInput.unitPrice = tx.unitPrice;
      if (tx.netValue != null) updateInput.netValue = tx.netValue;
      if (tx.incomeTax != null) updateInput.incomeTax = tx.incomeTax;
      if (tx.iof != null) updateInput.iof = tx.iof;

      await updateInvestmentTransaction(
        tx.id,
        updateInput,
        pfUser.token,
      );
    }

    await waitForRecalculation(pfUser.token, account.id);

    const after = await waitForStablePortfolioSummary(pfUser.token, account.id);

    // On TWR mismatch emit a full debug breakdown — quota series, flow events, daily steps —
    // so the next failure immediately reveals which day is missing its flow event.
    const twrDiff = Math.abs((after.performance.twr ?? 0) - (before.performance.twr ?? 0));
    if (twrDiff > 0.00002) {
      const debugAfter = await getPortfolioTwrDebug(pfUser.token, {
        financialAccountId: account.id,
        period: PerformancePeriod.All,
      }).catch(() => null);
      console.error(
        '[INV-TWR-HARD-04] TWR mismatch after no-op updates:\n' +
          `  before.twr=${before.performance.twr}  after.twr=${after.performance.twr}  diff=${twrDiff}\n` +
          `  twrDebug=${JSON.stringify(debugAfter, null, 2)}`,
      );
    }

    expectNear(after.totalCostBasis, before.totalCostBasis, 6);
    expectNear(after.totalGrossValue, before.totalGrossValue, 6);
    expectNear(after.totalPortfolioValue, before.totalPortfolioValue, 6);
    expectAbsDiffWithin(after.performance.twr ?? 0, before.performance.twr ?? 0, 0.00002);

    const replayA = await waitForStablePortfolioSummary(pfUser.token, account.id);
    const replayB = await waitForStablePortfolioSummary(pfUser.token, account.id);
    expectNear(replayA.totalPortfolioValue, replayB.totalPortfolioValue, 8);
    expectAbsDiffWithin(replayA.performance.twr ?? 0, replayB.performance.twr ?? 0, 0.000005);
  });

  test('INV-TWR-HARD-05: drift budget across periods stays within epsilon for same-month timeline', async ({
    pfUser,
  }) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Hardening drift account'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );
    const fund = await createInvestmentAsset(
      {
        name: uniqueName('Hardening drift fund'),
        assetType: AssetType.MutualFund,
      },
      pfUser.token,
    );

    const tradeDate = isoDaysAgo(10);
    const valuedAt = isoDaysAgo(1);
    await recordInvestmentTransaction(
      {
        financialAccountId: account.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: tradeDate,
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt, grossValue: 1110 },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, account.id);

    const all = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });
    const ytd = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.Ytd,
    });
    const mtd = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.Mtd,
    });
    const oneYear = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.OneYear,
    });
    const threeYears = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.ThreeYears,
    });

    expect(all.performance.twr).not.toBeNull();
    const twrAll = all.performance.twr ?? 0;
    const twrYtd = ytd.performance.twr ?? 0;
    const twrMtd = mtd.performance.twr ?? 0;
    const twrOneYear = oneYear.performance.twr ?? 0;
    const twrThreeYears = threeYears.performance.twr ?? 0;

    // Same-month timeline: all these windows should converge within integration epsilon.
    expectAbsDiffWithin(twrAll, twrYtd, 0.00002);
    expectAbsDiffWithin(twrAll, twrMtd, 0.00002);
    expectAbsDiffWithin(twrAll, twrOneYear, 0.00002);
    expectAbsDiffWithin(twrAll, twrThreeYears, 0.00002);

    const replay = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: account.id,
      period: PerformancePeriod.All,
    });
    expectAbsDiffWithin(replay.performance.twr ?? 0, twrAll, 0.000005);
  });
});
