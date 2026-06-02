import { test, expect } from '../fixtures/investments';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentCashTransactions,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  ValuationSource,
} from '../helpers/investment-graphql';
import { waitForRecalculation } from '../helpers/investment-e2e';
import { isoDaysAgo } from '../helpers/fixed-income-e2e';


test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 360000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function findHolding(token: string, financialAccountId: string, assetId: string) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

test.describe('Investments TWR phase 3D reits', () => {
  test('INV-TWR-P3-RE-01: second purchase updates weighted average and accepts both FII and FIAGRO assets', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const settlementDefaultAsset = await createInvestmentAsset(
      {
        name: uniqueName('FII settlement default'),
        assetType: AssetType.RealEstateFund,
      },
      pfUser.token,
    );
    const defaultTradeDate = isoDaysAgo(44);
    const purchaseWithoutSettlement = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: settlementDefaultAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: defaultTradeDate,
        quantity: 20,
        unitPrice: 10,
        grossValue: 200,
        netValue: 200,
      },
      pfUser.token,
    );
    expect(purchaseWithoutSettlement.settlementDate).toBeNull();

    const fii = await createInvestmentAsset(
      {
        name: uniqueName('FII weighted average'),
        assetType: AssetType.RealEstateFund,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(38),
        quantity: 200,
        unitPrice: 155,
        grossValue: 31000,
        netValue: 31000,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(28),
        quantity: 100,
        unitPrice: 165,
        grossValue: 16500,
        netValue: 16500,
      },
      pfUser.token,
    );

    const fiagro = await createInvestmentAsset(
      {
        name: uniqueName('FIAGRO accepted'),
        assetType: AssetType.AgribusinessFund,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fiagro.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(29),
        settlementDate: isoDaysAgo(27),
        quantity: 50,
        unitPrice: 20,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const fiiHolding = await findHolding(pfUser.token, brokerageAccount.id, fii.id);
    expect(fiiHolding).not.toBeNull();
    expectNear(fiiHolding!.quantity, 300, 6);
    expectNear(fiiHolding!.averageCost, (200 * 155 + 100 * 165) / 300, 6);

    const fiagroHolding = await findHolding(pfUser.token, brokerageAccount.id, fiagro.id);
    expect(fiagroHolding).not.toBeNull();
    expectNear(fiagroHolding!.quantity, 50, 6);
  });

  test('INV-TWR-P3-RE-02: taxed dividend and amortization credit cash while holding stays unchanged', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fii = await createInvestmentAsset(
      {
        name: uniqueName('FII dividend + amortization'),
        assetType: AssetType.RealEstateFund,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(33),
        quantity: 500,
        unitPrice: 10.2,
        grossValue: 5100,
        netValue: 5093,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const before = await findHolding(pfUser.token, brokerageAccount.id, fii.id);
    expect(before).not.toBeNull();

    const dividendTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Dividend,
        tradeDate: isoDaysAgo(5),
        settlementDate: isoDaysAgo(5),
        grossValue: 100,
        netValue: 85,
        incomeTax: 15,
      },
      pfUser.token,
    );

    const amortizationTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(3),
        settlementDate: isoDaysAgo(3),
        grossValue: 200,
        netValue: 200,
        incomeTax: 0,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const after = await findHolding(pfUser.token, brokerageAccount.id, fii.id);
    expect(after).not.toBeNull();
    expectNear(after!.quantity, before!.quantity, 8);
    expectNear(after!.averageCost, before!.averageCost, 8);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const dividendCash = rows.find((row) => row.investmentTransactionId === dividendTx.id);
    const amortizationCash = rows.find((row) => row.investmentTransactionId === amortizationTx.id);
    expect(dividendCash).toBeTruthy();
    expect(amortizationCash).toBeTruthy();
    expect(dividendCash?.creditDebitType).toBe('CREDIT');
    expect(amortizationCash?.creditDebitType).toBe('CREDIT');
    expectNear(dividendCash?.amount ?? 0, 85, 6);
    expectNear(amortizationCash?.amount ?? 0, 200, 6);
  });

  test('INV-TWR-P3-RE-03: subscription, split/reverse split, and stock lending (FII + FIAGRO) follow expected mechanics', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const subsAsset = await createInvestmentAsset(
      { name: uniqueName('FII subscription'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: subsAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(45),
        settlementDate: isoDaysAgo(43),
        quantity: 200,
        unitPrice: 155,
        grossValue: 31000,
        netValue: 31000,
      },
      pfUser.token,
    );
    const subscriptionTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: subsAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Subscription,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(18),
        quantity: 10,
        unitPrice: 160,
        grossValue: 1600,
        netValue: 1600,
      },
      pfUser.token,
    );

    const splitAsset = await createInvestmentAsset(
      { name: uniqueName('FII split'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(38),
        quantity: 100,
        unitPrice: 120,
        grossValue: 12000,
        netValue: 12000,
      },
      pfUser.token,
    );
    const splitTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockSplit,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 100,
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );
    const reverseTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.ReverseSplit,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 100,
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );

    const fiagro = await createInvestmentAsset(
      { name: uniqueName('FIAGRO lending'), assetType: AssetType.AgribusinessFund },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fiagro.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(33),
        quantity: 1000,
        unitPrice: 10,
        grossValue: 10000,
        netValue: 10000,
      },
      pfUser.token,
    );
    const fiiLendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(8),
        settlementDate: isoDaysAgo(8),
        grossValue: 80,
        netValue: 68,
        incomeTax: 12,
      },
      pfUser.token,
    );
    const fiagroLendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fiagro.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(7),
        settlementDate: isoDaysAgo(7),
        grossValue: 80,
        netValue: 68,
        incomeTax: 12,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const subsHolding = await findHolding(pfUser.token, brokerageAccount.id, subsAsset.id);
    expect(subsHolding).not.toBeNull();
    expectNear(subsHolding!.quantity, 210, 6);
    expectNear(subsHolding!.averageCost, (200 * 155 + 10 * 160) / 210, 6);

    const splitHolding = await findHolding(pfUser.token, brokerageAccount.id, splitAsset.id);
    expect(splitHolding).not.toBeNull();
    expectNear(splitHolding!.quantity, 100, 6);
    expectNear(splitHolding!.averageCost, 120, 6);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 300,
    });
    expect(rows.some((row) => row.investmentTransactionId === splitTx.id)).toBe(false);
    expect(rows.some((row) => row.investmentTransactionId === reverseTx.id)).toBe(false);

    const subscriptionCash = rows.find((row) => row.investmentTransactionId === subscriptionTx.id);
    expect(subscriptionCash).toBeTruthy();
    expect(subscriptionCash?.creditDebitType).toBe('DEBIT');
    expectNear(subscriptionCash?.amount ?? 0, 1600, 6);

    const fiiLendingCash = rows.find((row) => row.investmentTransactionId === fiiLendingTx.id);
    const fiagroLendingCash = rows.find((row) => row.investmentTransactionId === fiagroLendingTx.id);
    expect(fiiLendingCash).toBeTruthy();
    expect(fiagroLendingCash).toBeTruthy();
    expect(fiiLendingCash?.creditDebitType).toBe('CREDIT');
    expect(fiagroLendingCash?.creditDebitType).toBe('CREDIT');
    expectNear(fiiLendingCash?.amount ?? 0, 68, 6);
    expectNear(fiagroLendingCash?.amount ?? 0, 68, 6);
  });

  test('INV-TWR-P3-RE-04: custody transfer semantics and valuation-source fallback are consistent for REITs', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const transferAsset = await createInvestmentAsset(
      { name: uniqueName('FII custody transfer'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(32),
        settlementDate: isoDaysAgo(30),
        quantity: 200,
        unitPrice: 10.2,
        grossValue: 2040,
        netValue: 2040,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const transferOutPartial = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        quantity: 100,
        grossValue: 1020,
        netValue: 1020,
      },
      pfUser.token,
    );
    const transferInPartial = await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        quantity: 100,
        unitPrice: 10.2,
        grossValue: 1020,
        netValue: 1020,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceAfterPartial = await findHolding(pfUser.token, brokerageAccount.id, transferAsset.id);
    const destinationAfterPartial = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      transferAsset.id,
    );
    expect(sourceAfterPartial).not.toBeNull();
    expect(destinationAfterPartial).not.toBeNull();
    expectNear(sourceAfterPartial!.quantity, 100, 6);
    expectNear(destinationAfterPartial!.quantity, 100, 6);
    expectNear(destinationAfterPartial!.averageCost, 10.2, 6);
    expect(transferOutPartial.type).toBe(InvestmentTransactionType.CustodyTransfer);
    expect(transferInPartial.type).toBe(InvestmentTransactionType.CustodyTransfer);
    expect(transferOutPartial.type).not.toBe(InvestmentTransactionType.QuotaTransfer);
    expect(transferInPartial.type).not.toBe(InvestmentTransactionType.QuotaTransfer);

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: transferAsset.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.CustodyTransfer,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 150,
          grossValue: 1530,
          netValue: 1530,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/Transfer quantity|exceeds current holding quantity|insuficiente|Invalid input/i);

    const transferOutFinal = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 100,
        grossValue: 1020,
        netValue: 1020,
      },
      pfUser.token,
    );
    const transferInFinal = await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 100,
        unitPrice: 10.2,
        grossValue: 1020,
        netValue: 1020,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceAfterFinal = await findHolding(pfUser.token, brokerageAccount.id, transferAsset.id);
    const destinationAfterFinal = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      transferAsset.id,
    );
    expect(sourceAfterFinal).toBeNull();
    expect(destinationAfterFinal).not.toBeNull();
    expectNear(destinationAfterFinal!.quantity, 200, 6);
    expectNear(destinationAfterFinal!.averageCost, 10.2, 6);

    const sourceRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 300,
    });
    const destinationRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 300,
    });
    expect(sourceRows.some((row) => row.investmentTransactionId === transferOutFinal.id)).toBe(false);
    expect(destinationRows.some((row) => row.investmentTransactionId === transferInFinal.id)).toBe(false);

    const listedAsset = await createInvestmentAsset(
      {
        name: uniqueName('Listed FII for valuation'),
        assetType: AssetType.RealEstateFund,
        ticker: 'MXRF11',
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: listedAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(13),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );

    const unavailableAsset = await createInvestmentAsset(
      {
        name: uniqueName('Unlisted FII fallback'),
        assetType: AssetType.RealEstateFund,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: unavailableAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(14),
        settlementDate: isoDaysAgo(12),
        quantity: 10,
        unitPrice: 11,
        grossValue: 110,
        netValue: 110,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const listedHolding = await findHolding(pfUser.token, brokerageAccount.id, listedAsset.id);
    const unavailableHolding = await findHolding(pfUser.token, brokerageAccount.id, unavailableAsset.id);
    expect(listedHolding).not.toBeNull();
    expect(unavailableHolding).not.toBeNull();
    expect([ValuationSource.MarketData, ValuationSource.Unavailable]).toContain(listedHolding!.valuationSource);
    expect(unavailableHolding!.valuationSource).toBe(ValuationSource.Unavailable);
    expectNear(listedHolding!.incomeTax ?? 0, 0, 8);
    expectNear(unavailableHolding!.incomeTax ?? 0, 0, 8);
    // No ticker / listed price → gross is null; cost basis remains quantity × averageCost.
    expect(unavailableHolding!.grossValue == null || unavailableHolding!.grossValue === 0).toBe(true);
    expectNear(unavailableHolding!.quantity * unavailableHolding!.averageCost, 110, 4);
  });

  test('INV-TWR-P4A-RE-01: AMORTIZATION transactions are recorded for REIT and non-REIT assets in current API behavior', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const fii = await createInvestmentAsset(
      {
        name: uniqueName('FII amortization validation'),
        assetType: AssetType.RealEstateFund,
      },
      pfUser.token,
    );
    const stock = await createInvestmentAsset(
      {
        name: uniqueName('Stock amortization rejection'),
        assetType: AssetType.Stock,
        ticker: `STK${Date.now().toString().slice(-4)}`,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(18),
        quantity: 100,
        unitPrice: 12,
        grossValue: 1200,
        netValue: 1200,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(18),
        quantity: 100,
        unitPrice: 10,
        grossValue: 1000,
        netValue: 1000,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const zeroAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );
    expect(zeroAmortization.type).toBe(InvestmentTransactionType.Amortization);

    const netAboveGrossAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        grossValue: 100,
        netValue: 120,
      },
      pfUser.token,
    );
    expect(netAboveGrossAmortization.type).toBe(InvestmentTransactionType.Amortization);

    const stockAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        grossValue: 50,
        netValue: 50,
      },
      pfUser.token,
    );
    expect(stockAmortization.type).toBe(InvestmentTransactionType.Amortization);
  });
});
