import { test, expect } from '../fixtures/investments';
import { createFinancialAccount } from '../helpers/banking-graphql';
import {
  AssetType,
  CouponFrequency,
  confirmCouponPayment,
  confirmHoldingMaturity,
  createInvestmentAsset,
  createInvestmentCashTransaction,
  deleteInvestmentCashTransaction,
  getInvestmentCashBalance,
  getInvestmentCashTransactions,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  HoldingCloseReason,
  HoldingPricingMode,
  Indexer,
  InvestmentCashTransactionType,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  updateInvestmentCashTransaction,
  ValuationSource,
} from '../helpers/investment-graphql';
import { waitForRecalculation } from '../helpers/investment-e2e';
import { isoDaysAgo } from '../helpers/fixed-income-e2e';


test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 240000 });

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

test.describe('Investments TWR phase 2 backlog', () => {
  test('INV-TWR-P2-01: ADJUSTMENT creates BALANCE_ADJUSTMENT rows with proper credit/debit polarity', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const before = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const plusDesc = uniqueName('Ajuste positivo');
    const minusDesc = uniqueName('Ajuste negativo');

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(10),
        type: InvestmentCashTransactionType.Adjustment,
        amount: 200,
        description: plusDesc,
      },
      pfUser.token,
    );

    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(9),
        type: InvestmentCashTransactionType.Adjustment,
        amount: -150,
        description: minusDesc,
      },
      pfUser.token,
    );

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });

    const plus = rows.find((row) => row.description === plusDesc);
    const minus = rows.find((row) => row.description === minusDesc);

    expect(plus).toBeTruthy();
    expect(minus).toBeTruthy();
    expect(plus?.type).toBe('BALANCE_ADJUSTMENT');
    expect(minus?.type).toBe('BALANCE_ADJUSTMENT');
    expect(plus?.creditDebitType).toBe('CREDIT');
    expect(minus?.creditDebitType).toBe('DEBIT');
    expectNear(plus?.amount ?? 0, 200, 6);
    expectNear(minus?.amount ?? 0, 150, 6);

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(after - before, 50, 6);
  });

  test('INV-TWR-P2-02: updating MANUAL cash row updates amount and account balance', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const baseline = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const created = await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(8),
        type: InvestmentCashTransactionType.Deposit,
        amount: 500,
        description: uniqueName('Deposito editavel'),
      },
      pfUser.token,
    );

    const updated = await updateInvestmentCashTransaction(
      created.id,
      {
        amount: 450,
        date: created.date,
        description: uniqueName('Deposito editado'),
      },
      pfUser.token,
    );

    expectNear(updated.amount, 450, 6);

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(after - baseline, 450, 6);
  });

  test('INV-TWR-P2-03: deleting MANUAL cash row soft-deletes entry and reverts balance impact', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const baseline = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const created = await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        date: isoDaysAgo(7),
        type: InvestmentCashTransactionType.Withdrawal,
        amount: 180,
        description: uniqueName('Saque removivel'),
      },
      pfUser.token,
    );

    await deleteInvestmentCashTransaction(created.id, pfUser.token);

    await expect
      .poll(
        async () => {
          const rows = await getInvestmentCashTransactions(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            first: 100,
          });
          return rows.some((row) => row.id === created.id);
        },
        { timeout: 30000 },
      )
      .toBe(false);

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(after, baseline, 6);
  });

  test('INV-TWR-P2-04: MATURITY can generate linked CREDIT cash row on same account', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB maturity same account'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(120),
        settlementDate: isoDaysAgo(120),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.15,
        issueDate: isoDaysAgo(120),
        maturityDate: isoDaysAgo(1),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, cdb.id);
    expect(holding).not.toBeNull();

    const before = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const result = await confirmHoldingMaturity(
      {
        holdingId: holding!.id,
        maturityDate: isoDaysAgo(1),
        grossValue: 1100,
        netValue: 1085,
        incomeTax: 15,
        closeReason: HoldingCloseReason.Matured,
        cashMovement: {
          financialAccountId: brokerageAccount.id,
          amount: 1085,
          date: isoDaysAgo(1),
        },
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const rows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    const cashRow = rows.find((row) => row.investmentTransactionId === result.maturityTransaction.id);

    expect(cashRow).toBeTruthy();
    expect(cashRow?.creditDebitType).toBe('CREDIT');
    expectNear(cashRow?.amount ?? 0, 1085, 6);

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(after - before, 1085, 6);
  });

  test('INV-TWR-P2-05: income coupon can credit a different destination account', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB coupon cross account'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(80),
        settlementDate: isoDaysAgo(80),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(80),
        maturityDate: isoDaysAgo(-240),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: 15,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, cdb.id);
    expect(holding).not.toBeNull();

    const sourceBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const targetBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });

    const result = await confirmCouponPayment(
      {
        holdingId: holding!.id,
        tradeDate: isoDaysAgo(2),
        grossValue: 100,
        netValue: 85,
        incomeTax: 15,
        destinationAccountId: secondaryBrokerageAccount.id,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const targetRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 100,
    });
    const targetCashRow = targetRows.find(
      (row) => row.investmentTransactionId === result.couponTransaction.id,
    );
    expect(targetCashRow).toBeTruthy();
    expect(targetCashRow?.financialAccountId).toBe(secondaryBrokerageAccount.id);
    expect(targetCashRow?.creditDebitType).toBe('CREDIT');
    expectNear(targetCashRow?.amount ?? 0, 85, 6);

    const sourceAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const targetAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });

    expectNear(sourceAfter, sourceBefore, 6);
    expectNear(targetAfter - targetBefore, 85, 6);
  });

  test('INV-TWR-P2-06: SALE cash credit defaults to the same custodian account', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const stock = await createInvestmentAsset(
      {
        name: uniqueName('Acao default cash destination'),
        assetType: AssetType.Stock,
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
        settlementDate: isoDaysAgo(20),
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
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(5),
        settlementDate: isoDaysAgo(5),
        quantity: 4,
        unitPrice: 120,
        grossValue: 480,
        netValue: 480,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const sourceRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    const targetRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 100,
    });

    expect(sourceRows.some((row) => row.investmentTransactionId === sale.id)).toBe(true);
    expect(targetRows.some((row) => row.investmentTransactionId === sale.id)).toBe(false);
  });

  test('INV-TWR-P2-07: investment cash transaction is rejected for BANK accounts', async ({ pfUser }) => {
    const bankAccount = await createFinancialAccount(
      {
        name: uniqueName('Conta banco invalida para caixa investimentos'),
        type: 'BANK',
        subtype: 'CONTA_CORRENTE',
      },
      pfUser.token,
    );

    await expect(
      createInvestmentCashTransaction(
        {
          financialAccountId: bankAccount.id,
          date: isoDaysAgo(2),
          type: InvestmentCashTransactionType.Deposit,
          amount: 100,
          description: 'Nao deve aceitar BANK',
        },
        pfUser.token,
      ),
    ).rejects.toThrow(
      /INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT|investment or benefit custodian account|Invalid input/i,
    );
  });

  test('INV-TWR-P2-08: investment cash transaction is accepted for BENEFIT accounts', async ({ pfUser }) => {
    const benefitAccount = await createFinancialAccount(
      {
        name: uniqueName('FGTS aceita caixa investimentos'),
        type: 'BENEFIT',
        subtype: 'FGTS',
      },
      pfUser.token,
    );

    const before = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: benefitAccount.id,
    });

    const created = await createInvestmentCashTransaction(
      {
        financialAccountId: benefitAccount.id,
        date: isoDaysAgo(2),
        type: InvestmentCashTransactionType.Deposit,
        amount: 250,
        description: 'Aporte em conta BENEFIT',
      },
      pfUser.token,
    );
    expect(created.id).toBeTruthy();

    const after = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: benefitAccount.id,
    });
    expectNear(after - before, 250, 6);
  });

  test('INV-TWR-P2-09: summary net metrics follow totalNetValue and provisioned taxes', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB net gain formula'),
        assetType: AssetType.Cdb,
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
        fixedAnnualRate: 1.2,
        issueDate: isoDaysAgo(20),
        maturityDate: isoDaysAgo(-120),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expect(summary.totalIncomeTax + summary.totalIof).toBeGreaterThan(0);
    expectNear(summary.totalNetValue, summary.totalGrossValue - summary.totalIncomeTax - summary.totalIof, 4);
    expectNear(summary.netGain, summary.totalNetValue - summary.totalCostBasis, 4);
  });

  test('INV-TWR-P2-10: hasEstimatedValues=true when portfolio includes ESTIMATED/UNAVAILABLE holdings', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB estimated valuation'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    const stock = await createInvestmentAsset(
      {
        name: uniqueName('Acao sem ticker para unavailable'),
        assetType: AssetType.Stock,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(40),
        settlementDate: isoDaysAgo(40),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.Cdi,
        indexerPercentage: 1.0,
        issueDate: isoDaysAgo(40),
        maturityDate: isoDaysAgo(-120),
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(35),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    expect(
      holdings.some(
        (h) =>
          h.valuationSource === ValuationSource.Estimated ||
          h.valuationSource === ValuationSource.Unavailable,
      ),
    ).toBe(true);
    expect(summary.hasEstimatedValues).toBe(true);
  });

  test('INV-TWR-P2-11: hasEstimatedValues=false when all holdings are MARKET_DATA/CALCULATED', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB calculated valuation'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(60),
        settlementDate: isoDaysAgo(60),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.15,
        issueDate: isoDaysAgo(60),
        maturityDate: isoDaysAgo(-180),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expect(holdings.length).toBe(1);
    expect(holdings[0].valuationSource).toBe(ValuationSource.Calculated);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    expect(summary.hasEstimatedValues).toBe(false);
  });

  test('INV-TWR-P2-12: valuation/manual priority and representative asset-group mapping are consistent', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const stock = await createInvestmentAsset(
      { name: uniqueName('Stock group mapping'), assetType: AssetType.Stock },
      pfUser.token,
    );
    const fii = await createInvestmentAsset(
      { name: uniqueName('FII group mapping'), assetType: AssetType.RealEstateFund },
      pfUser.token,
    );
    const cdb = await createInvestmentAsset(
      { name: uniqueName('CDB group mapping'), assetType: AssetType.Cdb },
      pfUser.token,
    );
    const fund = await createInvestmentAsset(
      { name: uniqueName('Fund manual valuation source'), assetType: AssetType.MutualFund },
      pfUser.token,
    );
    const crypto = await createInvestmentAsset(
      { name: uniqueName('Crypto group mapping'), assetType: AssetType.Crypto },
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
        assetId: fii.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(19),
        settlementDate: isoDaysAgo(19),
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
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(18),
        settlementDate: isoDaysAgo(18),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(18),
        maturityDate: isoDaysAgo(-180),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(17),
        settlementDate: isoDaysAgo(17),
        quantity: 10,
        unitPrice: 10,
        grossValue: 100,
        netValue: 100,
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: {
          valuedAt: isoDaysAgo(1),
          grossValue: 130,
        },
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: crypto.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(16),
        settlementDate: isoDaysAgo(16),
        quantity: 1,
        unitPrice: 100,
        grossValue: 100,
        netValue: 100,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdings = await getInvestmentHoldings(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const manualFundHolding = holdings.find((h) => h.asset.id === fund.id);
    expect(manualFundHolding).toBeTruthy();
    expect(manualFundHolding?.valuationSource).toBe(ValuationSource.Manual);
    expectNear(manualFundHolding?.grossValue ?? 0, 130, 6);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const groupCount = new Map(summary.byAssetGroup.map((g) => [g.groupKey, g.count]));

    expect(groupCount.get('variableIncome')).toBe(2); // STOCK + REAL_ESTATE_FUND
    expect(groupCount.get('fixedIncome')).toBe(1); // CDB
    expect(groupCount.get('mutualFunds')).toBe(1); // MUTUAL_FUND
    expect(groupCount.get('others')).toBe(1); // CRYPTO
  });
});
