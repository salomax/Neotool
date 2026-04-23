import { randomBytes } from 'node:crypto';

import { test, expect } from '../fixtures/investments';
import { createFinancialAccount, formatLocalIsoDate } from '../helpers/banking-graphql';
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

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 360000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return formatLocalIsoDate(d);
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function waitForRecalculation(token: string, financialAccountId?: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const summary = await getInvestmentPortfolioSummary(token, {
          financialAccountId,
          period: PerformancePeriod.All,
        });
        return summary.recalculationStatus.isCalculating;
      },
      { timeout: 60000 },
    )
    .toBe(false);
}

async function findHolding(token: string, financialAccountId: string, assetId: string) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

test.describe('Investments TWR phase 3E crypto', () => {
  test('INV-TWR-P3-CR-01: multi-exchange holdings are independent, same-exchange updates weighted average, and D+0 is respected', async ({
    pfUser,
    cryptoAccount,
  }) => {
    const secondCryptoAccount = await createFinancialAccount(
      {
        name: uniqueName('Exchange Crypto B'),
        type: 'INVESTMENT',
        subtype: 'CRYPTO',
      },
      pfUser.token,
    );

    const btc = await createInvestmentAsset(
      {
        name: 'Bitcoin',
        ticker: 'BTC',
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );

    const btcNoSettlement = await createInvestmentAsset(
      {
        name: uniqueName('Bitcoin no-settlement input'),
        ticker: `BT${Date.now().toString().slice(-4)}`,
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );
    const noSettlementTradeDate = isoDaysAgo(41);
    const noSettlementTx = await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btcNoSettlement.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: noSettlementTradeDate,
        quantity: 0.01,
        unitPrice: 340000,
        grossValue: 3400,
        netValue: 3400,
      },
      pfUser.token,
    );
    expect(noSettlementTx.settlementDate).toBeNull();

    const buy1 = await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(40),
        quantity: 0.05,
        unitPrice: 350000,
        grossValue: 17500,
        netValue: 17500,
      },
      pfUser.token,
    );
    expect(buy1.settlementDate).toBe(buy1.tradeDate);

    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 0.02,
        unitPrice: 380000,
        grossValue: 7600,
        netValue: 7600,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: secondCryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(25),
        quantity: 0.02,
        unitPrice: 360000,
        grossValue: 7200,
        netValue: 7200,
      },
      pfUser.token,
    );

    const micro = await createInvestmentAsset(
      {
        name: uniqueName('Micro token'),
        ticker: `MC${Date.now().toString().slice(-4)}`,
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: micro.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 0.00001234,
        unitPrice: 100000,
        grossValue: 1.234,
        netValue: 1.234,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    await waitForRecalculation(pfUser.token, secondCryptoAccount.id);

    const sourceHolding = await findHolding(pfUser.token, cryptoAccount.id, btc.id);
    const destinationHolding = await findHolding(pfUser.token, secondCryptoAccount.id, btc.id);
    expect(sourceHolding).not.toBeNull();
    expect(destinationHolding).not.toBeNull();
    expectNear(sourceHolding!.quantity, 0.07, 8);
    expectNear(sourceHolding!.averageCost, (0.05 * 350000 + 0.02 * 380000) / 0.07, 4);
    expectNear(destinationHolding!.quantity, 0.02, 8);
    expectNear(destinationHolding!.averageCost, 360000, 6);

    const consolidated = await getInvestmentHoldings(pfUser.token, {});
    const btcRows = consolidated.filter((h) => h.asset.id === btc.id);
    expect(btcRows.length).toBeGreaterThanOrEqual(2);

    const microHolding = await findHolding(pfUser.token, cryptoAccount.id, micro.id);
    expect(microHolding).not.toBeNull();
    expectNear(microHolding!.quantity, 0.00001234, 10);
  });

  test('INV-TWR-P3-CR-02: partial sale with IR keeps average cost and credits net proceeds', async ({
    pfUser,
    cryptoAccount,
  }) => {
    const eth = await createInvestmentAsset(
      {
        name: uniqueName('Ethereum sale'),
        ticker: `ETH${Date.now().toString().slice(-3)}`,
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: eth.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(35),
        quantity: 0.05,
        unitPrice: 350000,
        grossValue: 17500,
        netValue: 17500,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: eth.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 0.02,
        unitPrice: 380000,
        grossValue: 7600,
        netValue: 7600,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, cryptoAccount.id);

    const before = await findHolding(pfUser.token, cryptoAccount.id, eth.id);
    expect(before).not.toBeNull();

    const sale = await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: eth.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(5),
        settlementDate: isoDaysAgo(5),
        quantity: 0.03,
        unitPrice: 400000,
        grossValue: 12000,
        netValue: 10800,
        incomeTax: 1200,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, cryptoAccount.id);

    const after = await findHolding(pfUser.token, cryptoAccount.id, eth.id);
    expect(after).not.toBeNull();
    expectNear(after!.quantity, 0.04, 8);
    expectNear(after!.averageCost, before!.averageCost, 6);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      first: 200,
    });
    const saleCash = rows.find((row) => row.investmentTransactionId === sale.id);
    expect(saleCash).toBeTruthy();
    expect(saleCash?.creditDebitType).toBe('CREDIT');
    expectNear(saleCash?.amount ?? 0, 10800, 6);
  });

  test('INV-TWR-P3-CR-03: custody transfer partial and full preserve average cost and do not touch cash ledger', async ({
    pfUser,
    cryptoAccount,
  }) => {
    const destination = await createFinancialAccount(
      {
        name: uniqueName('Exchange transfer destination'),
        type: 'INVESTMENT',
        subtype: 'CRYPTO',
      },
      pfUser.token,
    );

    const sol = await createInvestmentAsset(
      {
        name: uniqueName('Solana transfer'),
        ticker: `SOL${Date.now().toString().slice(-3)}`,
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: sol.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 0.07,
        unitPrice: 358571.43,
        grossValue: 25100,
        netValue: 25100,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, cryptoAccount.id);

    const transferOutPartial = await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: sol.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(3),
        settlementDate: isoDaysAgo(3),
        quantity: 0.04,
        grossValue: 14342.8572,
        netValue: 14342.8572,
      },
      pfUser.token,
    );
    const transferInPartial = await recordInvestmentTransaction(
      {
        financialAccountId: destination.id,
        assetId: sol.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(3),
        settlementDate: isoDaysAgo(3),
        quantity: 0.04,
        unitPrice: 358571.43,
        grossValue: 14342.8572,
        netValue: 14342.8572,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    await waitForRecalculation(pfUser.token, destination.id);

    const srcAfterPartial = await findHolding(pfUser.token, cryptoAccount.id, sol.id);
    const dstAfterPartial = await findHolding(pfUser.token, destination.id, sol.id);
    expect(srcAfterPartial).not.toBeNull();
    expect(dstAfterPartial).not.toBeNull();
    expectNear(srcAfterPartial!.quantity, 0.03, 8);
    expectNear(dstAfterPartial!.quantity, 0.04, 8);
    expectNear(dstAfterPartial!.averageCost, 358571.43, 4);

    const transferOutFinal = await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: sol.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        quantity: 0.03,
        grossValue: 10757.1429,
        netValue: 10757.1429,
      },
      pfUser.token,
    );
    const transferInFinal = await recordInvestmentTransaction(
      {
        financialAccountId: destination.id,
        assetId: sol.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        quantity: 0.03,
        unitPrice: 358571.43,
        grossValue: 10757.1429,
        netValue: 10757.1429,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    await waitForRecalculation(pfUser.token, destination.id);

    const srcAfterFinal = await findHolding(pfUser.token, cryptoAccount.id, sol.id);
    const dstAfterFinal = await findHolding(pfUser.token, destination.id, sol.id);
    expect(srcAfterFinal).toBeNull();
    expect(dstAfterFinal).not.toBeNull();
    expectNear(dstAfterFinal!.quantity, 0.07, 8);
    expectNear(dstAfterFinal!.averageCost, 358571.43, 4);

    const sourceRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: cryptoAccount.id,
      first: 300,
    });
    const destinationRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: destination.id,
      first: 300,
    });
    expect(sourceRows.some((row) => row.investmentTransactionId === transferOutPartial.id)).toBe(false);
    expect(destinationRows.some((row) => row.investmentTransactionId === transferInPartial.id)).toBe(false);
    expect(sourceRows.some((row) => row.investmentTransactionId === transferOutFinal.id)).toBe(false);
    expect(destinationRows.some((row) => row.investmentTransactionId === transferInFinal.id)).toBe(false);
  });

  test('INV-TWR-P3-CR-04: valuation fallback and per-account separation are consistent for crypto holdings', async ({
    pfUser,
    cryptoAccount,
  }) => {
    const secondCryptoAccount = await createFinancialAccount(
      {
        name: uniqueName('Exchange valuation B'),
        type: 'INVESTMENT',
        subtype: 'CRYPTO',
      },
      pfUser.token,
    );

    const btc = await createInvestmentAsset(
      {
        name: 'Bitcoin',
        ticker: 'BTC',
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 0.05,
        unitPrice: 350000,
        grossValue: 17500,
        netValue: 17500,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: secondCryptoAccount.id,
        assetId: btc.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(11),
        settlementDate: isoDaysAgo(11),
        quantity: 0.02,
        unitPrice: 360000,
        grossValue: 7200,
        netValue: 7200,
      },
      pfUser.token,
    );

    const unknown = await createInvestmentAsset(
      {
        name: uniqueName('Unknown valuation token'),
        ticker: `ZZ${randomBytes(4).toString('hex').toUpperCase()}`,
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: cryptoAccount.id,
        assetId: unknown.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(10),
        settlementDate: isoDaysAgo(10),
        quantity: 10,
        unitPrice: 11,
        grossValue: 110,
        netValue: 110,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    await waitForRecalculation(pfUser.token, secondCryptoAccount.id);

    const btcA = await findHolding(pfUser.token, cryptoAccount.id, btc.id);
    const btcB = await findHolding(pfUser.token, secondCryptoAccount.id, btc.id);
    expect(btcA).not.toBeNull();
    expect(btcB).not.toBeNull();
    expectNear(btcA!.quantity, 0.05, 8);
    expectNear(btcB!.quantity, 0.02, 8);
    expect([ValuationSource.MarketData, ValuationSource.Unavailable]).toContain(btcA!.valuationSource);
    expect([ValuationSource.MarketData, ValuationSource.Unavailable]).toContain(btcB!.valuationSource);

    const consolidated = await getInvestmentHoldings(pfUser.token, {});
    const btcRows = consolidated.filter((h) => h.asset.id === btc.id);
    expect(btcRows.length).toBeGreaterThanOrEqual(2);

    const unknownHolding = await findHolding(pfUser.token, cryptoAccount.id, unknown.id);
    expect(unknownHolding).not.toBeNull();
    expect(unknownHolding!.valuationSource).toBe(ValuationSource.Unavailable);
    // No market quote for synthetic ticker → gross is null; cost basis remains quantity × averageCost.
    expect(unknownHolding!.grossValue == null || unknownHolding!.grossValue === 0).toBe(true);
    expectNear(unknownHolding!.quantity * unknownHolding!.averageCost, 110, 4);

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: cryptoAccount.id,
          assetId: btc.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.StockLending,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          grossValue: 80,
          netValue: 68,
          incomeTax: 12,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/aluguel|lending|supports|Invalid input/i);

    await waitForRecalculation(pfUser.token, cryptoAccount.id);
    const btcAfterLending = await findHolding(pfUser.token, cryptoAccount.id, btc.id);
    expect(btcAfterLending).not.toBeNull();
    expectNear(btcAfterLending!.quantity, 0.05, 8);
    expectNear(btcAfterLending!.averageCost, btcA!.averageCost, 8);
  });
});
