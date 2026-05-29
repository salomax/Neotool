import { test, expect } from '../fixtures/investments';
import { createFinancialAccount } from '../helpers/banking-graphql';
import {
  AssetType,
  createInvestmentAsset,
  getInvestmentCashTransactions,
  getInvestmentHolding,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  getInvestmentTransactions,
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

test.describe('Investments TWR phase 3C equities', () => {
  test('INV-TWR-P3-EQ-01: weighted average, broker-note persistence, settlement override, and accepted equity asset types', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const autoSettlementAsset = await createInvestmentAsset(
      {
        name: uniqueName('Stock default settlement T+2'),
        assetType: AssetType.Stock,
        ticker: `D2${Date.now().toString().slice(-4)}`,
      },
      pfUser.token,
    );
    const autoTradeDate = isoDaysAgo(52);
    const autoSettlementTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: autoSettlementAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: autoTradeDate,
        quantity: 10,
        unitPrice: 20,
        grossValue: 200,
        netValue: 200,
      },
      pfUser.token,
    );
    expect(autoSettlementTx.settlementDate).toBeNull();

    const stock = await createInvestmentAsset(
      {
        name: uniqueName('Stock avg cost + broker note'),
        assetType: AssetType.Stock,
        ticker: `EQ${Date.now().toString().slice(-4)}`,
      },
      pfUser.token,
    );

    const firstTradeDate = isoDaysAgo(50);
    const firstSettlementDate = isoDaysAgo(47);

    const purchaseWithFees = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: firstTradeDate,
        settlementDate: firstSettlementDate,
        quantity: 100,
        unitPrice: 38.5,
        grossValue: 3850,
        netValue: 3845.75,
        brokerNote: {
          brokerNoteNumber: uniqueName('NT'),
          grossValue: 3850,
          brokerageFee: 3,
          clearingSettlementFee: 0.5,
          taxes: 0.25,
          netValue: 3845.75,
        },
      },
      pfUser.token,
    );

    expect(purchaseWithFees.settlementDate).toBe(firstSettlementDate);
    expect(purchaseWithFees.brokerNote).not.toBeNull();
    expectNear(purchaseWithFees.brokerNote?.brokerageFee ?? 0, 3, 6);
    expectNear(purchaseWithFees.brokerNote?.clearingSettlementFee ?? 0, 0.5, 6);
    expectNear(purchaseWithFees.brokerNote?.taxes ?? 0, 0.25, 6);
    expectNear(purchaseWithFees.brokerNote?.netValue ?? 0, 3845.75, 6);

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(38),
        quantity: 50,
        unitPrice: 42,
        grossValue: 2100,
        netValue: 2100,
      },
      pfUser.token,
    );

    const etf = await createInvestmentAsset(
      {
        name: uniqueName('ETF accepted'),
        assetType: AssetType.Etf,
        ticker: `EFA${Date.now().toString().slice(-3)}`,
      },
      pfUser.token,
    );
    const etfIntl = await createInvestmentAsset(
      {
        name: uniqueName('ETF international accepted'),
        assetType: AssetType.EtfInternational,
        ticker: `EFI${Date.now().toString().slice(-3)}`,
      },
      pfUser.token,
    );
    const bdr = await createInvestmentAsset(
      {
        name: uniqueName('BDR accepted'),
        assetType: AssetType.Bdr,
        ticker: `BDA${Date.now().toString().slice(-3)}`,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: etf.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(33),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: etfIntl.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(34),
        settlementDate: isoDaysAgo(32),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: bdr.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(33),
        settlementDate: isoDaysAgo(31),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const stockHolding = await findHolding(pfUser.token, brokerageAccount.id, stock.id);
    expect(stockHolding).not.toBeNull();
    expectNear(stockHolding!.quantity, 150, 6);
    expectNear(stockHolding!.averageCost, (100 * 38.5 + 50 * 42) / 150, 6);

    const etfHolding = await findHolding(pfUser.token, brokerageAccount.id, etf.id);
    const etfIntlHolding = await findHolding(pfUser.token, brokerageAccount.id, etfIntl.id);
    const bdrHolding = await findHolding(pfUser.token, brokerageAccount.id, bdr.id);
    expect(etfHolding).not.toBeNull();
    expect(etfIntlHolding).not.toBeNull();
    expect(bdrHolding).not.toBeNull();

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const linkedDebit = cashRows.find((row) => row.investmentTransactionId === purchaseWithFees.id);
    expect(linkedDebit).toBeTruthy();
    expect(linkedDebit?.creditDebitType).toBe('DEBIT');
    expectNear(linkedDebit?.amount ?? 0, 3845.75, 6);
  });

  test('INV-TWR-P3-EQ-02: full sale keeps closed record and JCP credits cash with holding invariant', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const saleAsset = await createInvestmentAsset(
      {
        name: uniqueName('Sale full position'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: saleAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(23),
        quantity: 100,
        unitPrice: 38.5,
        grossValue: 3850,
        netValue: 3845,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const saleHoldingBefore = await findHolding(pfUser.token, brokerageAccount.id, saleAsset.id);
    expect(saleHoldingBefore).not.toBeNull();
    const saleHoldingId = saleHoldingBefore!.id;

    const saleTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: saleAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(8),
        quantity: 100,
        unitPrice: 42,
        grossValue: 4200,
        netValue: 4190,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdingListed = await findHolding(pfUser.token, brokerageAccount.id, saleAsset.id);
    expect(holdingListed).toBeNull();
    const closedHolding = await getInvestmentHolding(saleHoldingId, pfUser.token);
    expect(closedHolding).not.toBeNull();
    expectNear(closedHolding!.quantity, 0, 8);
    expectNear(closedHolding!.averageCost, 38.5, 8);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const saleCash = cashRows.find((row) => row.investmentTransactionId === saleTx.id);
    expect(saleCash).toBeTruthy();
    expect(saleCash?.creditDebitType).toBe('CREDIT');
    expectNear(saleCash?.amount ?? 0, 4190, 6);

    const jcpAsset = await createInvestmentAsset(
      {
        name: uniqueName('JCP holding'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: jcpAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(18),
        quantity: 200,
        unitPrice: 20,
        grossValue: 4000,
        netValue: 4000,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const jcpHoldingBefore = await findHolding(pfUser.token, brokerageAccount.id, jcpAsset.id);
    expect(jcpHoldingBefore).not.toBeNull();

    const jcpTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: jcpAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.InterestOnEquity,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 100,
        netValue: 85,
        incomeTax: 15,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const jcpHoldingAfter = await findHolding(pfUser.token, brokerageAccount.id, jcpAsset.id);
    expect(jcpHoldingAfter).not.toBeNull();
    expectNear(jcpHoldingAfter!.quantity, jcpHoldingBefore!.quantity, 8);
    expectNear(jcpHoldingAfter!.averageCost, jcpHoldingBefore!.averageCost, 8);

    const rowsAfterJcp = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const jcpCash = rowsAfterJcp.find((row) => row.investmentTransactionId === jcpTx.id);
    expect(jcpCash).toBeTruthy();
    expect(jcpCash?.creditDebitType).toBe('CREDIT');
    expectNear(jcpCash?.amount ?? 0, 85, 6);
  });

  test('INV-TWR-P3-EQ-03: split, reverse split, bonus shares, and subscription preserve expected economics', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const splitAsset = await createInvestmentAsset(
      { name: uniqueName('Split asset'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(60),
        settlementDate: isoDaysAgo(58),
        quantity: 100,
        unitPrice: 60,
        grossValue: 6000,
        netValue: 6000,
      },
      pfUser.token,
    );
    const splitTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: splitAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockSplit,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 200,
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );

    const reverseAsset = await createInvestmentAsset(
      { name: uniqueName('Reverse split asset'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: reverseAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(55),
        settlementDate: isoDaysAgo(53),
        quantity: 400,
        unitPrice: 5,
        grossValue: 2000,
        netValue: 2000,
      },
      pfUser.token,
    );
    const reverseTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: reverseAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.ReverseSplit,
        tradeDate: isoDaysAgo(28),
        settlementDate: isoDaysAgo(28),
        quantity: 300,
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );

    const bonusAsset = await createInvestmentAsset(
      { name: uniqueName('Bonus shares asset'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: bonusAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(52),
        settlementDate: isoDaysAgo(50),
        quantity: 100,
        unitPrice: 50,
        grossValue: 5000,
        netValue: 5000,
      },
      pfUser.token,
    );
    const bonusTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: bonusAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.BonusShares,
        tradeDate: isoDaysAgo(26),
        settlementDate: isoDaysAgo(26),
        quantity: 10,
        unitPrice: 0,
        grossValue: 0,
        netValue: 0,
      },
      pfUser.token,
    );

    const subsAsset = await createInvestmentAsset(
      { name: uniqueName('Subscription asset'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: subsAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(50),
        settlementDate: isoDaysAgo(48),
        quantity: 1000,
        unitPrice: 55,
        grossValue: 55000,
        netValue: 55000,
      },
      pfUser.token,
    );
    const subscriptionTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: subsAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Subscription,
        tradeDate: isoDaysAgo(24),
        settlementDate: isoDaysAgo(22),
        quantity: 100,
        unitPrice: 45,
        grossValue: 4500,
        netValue: 4500,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const splitHolding = await findHolding(pfUser.token, brokerageAccount.id, splitAsset.id);
    expect(splitHolding).not.toBeNull();
    expectNear(splitHolding!.quantity, 300, 6);
    expectNear(splitHolding!.averageCost, 20, 6);

    const reverseHolding = await findHolding(pfUser.token, brokerageAccount.id, reverseAsset.id);
    expect(reverseHolding).not.toBeNull();
    expectNear(reverseHolding!.quantity, 100, 6);
    expectNear(reverseHolding!.averageCost, 20, 6);

    const bonusHolding = await findHolding(pfUser.token, brokerageAccount.id, bonusAsset.id);
    expect(bonusHolding).not.toBeNull();
    expectNear(bonusHolding!.quantity, 110, 6);
    expectNear(bonusHolding!.averageCost, (100 * 50) / 110, 6);

    const subscriptionHolding = await findHolding(pfUser.token, brokerageAccount.id, subsAsset.id);
    expect(subscriptionHolding).not.toBeNull();
    expectNear(subscriptionHolding!.quantity, 1100, 6);
    expectNear(subscriptionHolding!.averageCost, (1000 * 55 + 100 * 45) / 1100, 6);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 300,
    });
    expect(cashRows.some((row) => row.investmentTransactionId === splitTx.id)).toBe(false);
    expect(cashRows.some((row) => row.investmentTransactionId === reverseTx.id)).toBe(false);
    expect(cashRows.some((row) => row.investmentTransactionId === bonusTx.id)).toBe(false);

    const subscriptionCash = cashRows.find((row) => row.investmentTransactionId === subscriptionTx.id);
    expect(subscriptionCash).toBeTruthy();
    expect(subscriptionCash?.creditDebitType).toBe('DEBIT');
    expectNear(subscriptionCash?.amount ?? 0, 4500, 6);
  });

  test('INV-TWR-P3-EQ-04: dividend and stock lending credit cash while preserving holdings', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const stock = await createInvestmentAsset(
      { name: uniqueName('Dividend and lending stock'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(23),
        quantity: 500,
        unitPrice: 20,
        grossValue: 10000,
        netValue: 10000,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdingBefore = await findHolding(pfUser.token, brokerageAccount.id, stock.id);
    expect(holdingBefore).not.toBeNull();

    const dividendTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Dividend,
        tradeDate: isoDaysAgo(3),
        settlementDate: isoDaysAgo(3),
        grossValue: 50,
        netValue: 50,
        incomeTax: 0,
      },
      pfUser.token,
    );
    const lendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 250,
        netValue: 212.5,
        incomeTax: 37.5,
      },
      pfUser.token,
    );

    const etf = await createInvestmentAsset(
      { name: uniqueName('Lending ETF'), assetType: AssetType.Etf },
      pfUser.token,
    );
    const bdr = await createInvestmentAsset(
      { name: uniqueName('Lending BDR'), assetType: AssetType.Bdr },
      pfUser.token,
    );
    const reit = await createInvestmentAsset(
      { name: uniqueName('Lending REIT'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    const fiagro = await createInvestmentAsset(
      { name: uniqueName('Lending FIAGRO'), assetType: AssetType.AgribusinessFund },
      pfUser.token,
    );
    for (const asset of [etf, bdr, reit, fiagro]) {
      await recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: asset.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          tradeDate: isoDaysAgo(20),
          settlementDate: isoDaysAgo(18),
          quantity: 10,
          unitPrice: 20,
          grossValue: 200,
          netValue: 200,
        },
        pfUser.token,
      );
    }
    const etfLendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: etf.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 80,
        netValue: 68,
        incomeTax: 12,
      },
      pfUser.token,
    );
    const bdrLendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: bdr.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 80,
        netValue: 68,
        incomeTax: 12,
      },
      pfUser.token,
    );
    const reitLendingTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: reit.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.StockLending,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
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
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 80,
        netValue: 68,
        incomeTax: 12,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdingAfter = await findHolding(pfUser.token, brokerageAccount.id, stock.id);
    expect(holdingAfter).not.toBeNull();
    expectNear(holdingAfter!.quantity, holdingBefore!.quantity, 8);
    expectNear(holdingAfter!.averageCost, holdingBefore!.averageCost, 8);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const dividendCash = cashRows.find((row) => row.investmentTransactionId === dividendTx.id);
    const lendingCash = cashRows.find((row) => row.investmentTransactionId === lendingTx.id);
    const etfLendingCash = cashRows.find((row) => row.investmentTransactionId === etfLendingTx.id);
    const bdrLendingCash = cashRows.find((row) => row.investmentTransactionId === bdrLendingTx.id);
    const reitLendingCash = cashRows.find((row) => row.investmentTransactionId === reitLendingTx.id);
    const fiagroLendingCash = cashRows.find((row) => row.investmentTransactionId === fiagroLendingTx.id);
    expect(dividendCash).toBeTruthy();
    expect(lendingCash).toBeTruthy();
    expect(etfLendingCash).toBeTruthy();
    expect(bdrLendingCash).toBeTruthy();
    expect(reitLendingCash).toBeTruthy();
    expect(fiagroLendingCash).toBeTruthy();
    expect(dividendCash?.creditDebitType).toBe('CREDIT');
    expect(lendingCash?.creditDebitType).toBe('CREDIT');
    expect(etfLendingCash?.creditDebitType).toBe('CREDIT');
    expect(bdrLendingCash?.creditDebitType).toBe('CREDIT');
    expect(reitLendingCash?.creditDebitType).toBe('CREDIT');
    expect(fiagroLendingCash?.creditDebitType).toBe('CREDIT');
    expectNear(dividendCash?.amount ?? 0, 50, 6);
    expectNear(lendingCash?.amount ?? 0, 212.5, 6);
    expectNear(etfLendingCash?.amount ?? 0, 68, 6);
    expectNear(bdrLendingCash?.amount ?? 0, 68, 6);
    expectNear(reitLendingCash?.amount ?? 0, 68, 6);
    expectNear(fiagroLendingCash?.amount ?? 0, 68, 6);
  });

  test('INV-TWR-P3-EQ-05: custody transfer semantics and valuation fallback invariants', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
    twoTenants,
  }) => {
    const transferAsset = await createInvestmentAsset(
      { name: uniqueName('Custody transfer stock'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(28),
        quantity: 200,
        unitPrice: 55,
        grossValue: 11000,
        netValue: 11000,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const transferOut = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 100,
        grossValue: 5500,
        netValue: 5500,
      },
      pfUser.token,
    );
    const transferIn = await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 100,
        unitPrice: 55,
        grossValue: 5500,
        netValue: 5500,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceHolding = await findHolding(pfUser.token, brokerageAccount.id, transferAsset.id);
    const destinationHolding = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      transferAsset.id,
    );
    expect(sourceHolding).not.toBeNull();
    expect(destinationHolding).not.toBeNull();
    expectNear(sourceHolding!.quantity, 100, 6);
    expectNear(sourceHolding!.averageCost, 55, 6);
    expectNear(destinationHolding!.quantity, 100, 6);
    expectNear(destinationHolding!.averageCost, 55, 6);

    const transferOutFinal = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: transferAsset.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 100,
        grossValue: 5500,
        netValue: 5500,
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
        unitPrice: 55,
        grossValue: 5500,
        netValue: 5500,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceHoldingAfterFull = await findHolding(pfUser.token, brokerageAccount.id, transferAsset.id);
    const destinationHoldingAfterFull = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      transferAsset.id,
    );
    expect(sourceHoldingAfterFull).toBeNull();
    expect(destinationHoldingAfterFull).not.toBeNull();
    expectNear(destinationHoldingAfterFull!.quantity, 200, 6);
    expectNear(destinationHoldingAfterFull!.averageCost, 55, 6);

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: transferAsset.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.CustodyTransfer,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 500,
          grossValue: 27500,
          netValue: 27500,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/Transfer quantity|exceeds current holding quantity|insuficiente|Invalid input/i);

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
    expect(sourceRows.some((row) => row.investmentTransactionId === transferOutFinal.id)).toBe(false);
    expect(destinationRows.some((row) => row.investmentTransactionId === transferInFinal.id)).toBe(false);

    const fallbackAsset = await createInvestmentAsset(
      { name: uniqueName('Unavailable valuation stock'), assetType: AssetType.Stock },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fallbackAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(10),
        quantity: 10,
        unitPrice: 12,
        grossValue: 120,
        netValue: 120,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const fallbackHolding = await findHolding(pfUser.token, brokerageAccount.id, fallbackAsset.id);
    expect(fallbackHolding).not.toBeNull();
    expect(fallbackHolding!.valuationSource).toBe(ValuationSource.Unavailable);
    // No listed price → gross is null; cost basis remains quantity × averageCost.
    expect(fallbackHolding!.grossValue == null || fallbackHolding!.grossValue === 0).toBe(true);
    expectNear(fallbackHolding!.quantity * fallbackHolding!.averageCost, 120, 4);

    const listedAsset = await createInvestmentAsset(
      { name: uniqueName('Market valuation stock'), assetType: AssetType.Stock, ticker: 'PETR4' },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: listedAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(8),
        quantity: 5,
        unitPrice: 35,
        grossValue: 175,
        netValue: 175,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const listedHolding = await findHolding(pfUser.token, brokerageAccount.id, listedAsset.id);
    expect(listedHolding).not.toBeNull();
    expect([ValuationSource.MarketData, ValuationSource.Unavailable]).toContain(
      listedHolding!.valuationSource,
    );

    const tenantAAccount = await createFinancialAccount(
      {
        name: uniqueName('Cross-tenant source account'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      twoTenants.userA.token,
    );
    const tenantAAsset = await createInvestmentAsset(
      { name: uniqueName('Cross-tenant source stock'), assetType: AssetType.Stock },
      twoTenants.userA.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: tenantAAccount.id,
        assetId: tenantAAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(8),
        settlementDate: isoDaysAgo(6),
        quantity: 10,
        unitPrice: 30,
        grossValue: 300,
        netValue: 300,
      },
      twoTenants.userA.token,
    );

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: tenantAAccount.id,
          assetId: tenantAAsset.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.CustodyTransfer,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 1,
          grossValue: 30,
          netValue: 30,
        },
        twoTenants.userB.token,
      ),
    ).rejects.toThrow(/Access denied|forbidden|permission|Invalid input/i);

    const transferHoldingTransactions = await getInvestmentTransactions(
      destinationHoldingAfterFull!.id,
      pfUser.token,
    );
    expect(
      transferHoldingTransactions.some((tx) => tx.type === InvestmentTransactionType.CustodyTransfer),
    ).toBe(true);
  });
});
