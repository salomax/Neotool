import { test, expect } from '../fixtures/investments';
import {
  AssetType,
  CouponFrequency,
  confirmCouponPayment,
  confirmHoldingMaturity,
  createInvestmentAsset,
  getInvestmentCashBalance,
  getInvestmentCashTransactions,
  getInvestmentHoldings,
  getInvestmentPortfolioSummary,
  getPendingActions,
  HoldingCloseReason,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PendingActionStatus,
  PendingActionType,
  PerformancePeriod,
  recordInvestmentTransaction,
  ValuationSource,
} from '../helpers/investment-graphql';
import { waitForRecalculation } from '../helpers/investment-e2e';
import {
  couponDaySoon,
  couponPendingPurchaseDate,
  isoDaysAgo,
  isoDaysFromNow,
} from '../helpers/fixed-income-e2e';
import { E2E_PENDING_ACTION_POLL_MS } from '../helpers/timeouts';

import { signIntoPage } from '../helpers/navigation';
import { InvestmentsPage } from '../pages/personal-finance/InvestmentsPage';

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 300000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function expectNear(actual: number, expected: number, precision = 6): void {
  expect(actual).toBeCloseTo(expected, precision);
}

async function openInvestmentsPage(
  page: import('@playwright/test').Page,
): Promise<InvestmentsPage> {
  await signIntoPage(page);
  const investmentsPage = new InvestmentsPage(page);
  await investmentsPage.goto();
  return investmentsPage;
}

async function openFixedIncomeRecordDrawer(
  page: import('@playwright/test').Page,
): Promise<void> {
  const fixedIncomeCard = page.getByTestId('investment-grid-card-fixed-income');
  await expect(fixedIncomeCard).toBeVisible({ timeout: 30000 });
  await fixedIncomeCard.scrollIntoViewIfNeeded();

  // Prefer matrix-card action: it works even when class tabs are hidden for empty holdings.
  const cardActionButton = fixedIncomeCard
    .getByRole('button', { name: /Incluir primeiro investimento|First investment/i })
    .first();
  if (await cardActionButton.isVisible().catch(() => false)) {
    await cardActionButton.click({ force: true });
    const fixedIncomeForm = page.locator('form#record-fixed-income-form');
    if (await fixedIncomeForm.isVisible().catch(() => false)) {
      return;
    }
    await expect(fixedIncomeForm).toBeVisible({ timeout: 5000 }).catch(() => undefined);
    if (await fixedIncomeForm.isVisible().catch(() => false)) return;
  }

  // Fallback: open class detail and then popover CTA.
  const detailButton = fixedIncomeCard
    .getByRole('button', { name: /(?:Abrir detalhes de|Open details for)/i })
    .first();
  if (await detailButton.isVisible().catch(() => false)) {
    await detailButton.click({ force: true });
  } else {
    await fixedIncomeCard.click({ force: true });
  }

  await expect
    .poll(
      async () => {
        const cta = page.getByTestId('investment-grid-record-cta');
        if (!(await cta.isVisible().catch(() => false))) return false;
        await cta.click({ force: true });
        const fixedIncomeOption = page.getByTestId('investment-grid-record-option-fixed-income');
        if (!(await fixedIncomeOption.isVisible().catch(() => false))) return false;
        await fixedIncomeOption.click({ force: true });
        return await page.locator('form#record-fixed-income-form').isVisible().catch(() => false);
      },
      { timeout: 30000 },
    )
    .toBe(true);
  await expect(page.locator('form#record-fixed-income-form')).toBeVisible();
}

async function selectByTestIdOption(
  page: import('@playwright/test').Page,
  testId: string,
  optionName: RegExp,
): Promise<void> {
  const trigger = page
    .getByTestId(testId)
    .locator('[role="combobox"], [role="button"]')
    .first();
  await trigger.click();
  const listbox = page.getByRole('listbox').last();
  await expect(listbox).toBeVisible();
  await listbox.getByRole('option', { name: optionName }).first().click();
}

async function findHolding(token: string, financialAccountId: string, assetId: string) {
  const holdings = await getInvestmentHoldings(token, { financialAccountId });
  return holdings.find((h) => h.asset.id === assetId) ?? null;
}

async function findVisiblePendingAction(
  token: string,
  holdingId: string,
  type: PendingActionType,
) {
  const pending = await getPendingActions(token, PendingActionStatus.Pending);
  return pending.find((action) => action.type === type && action.relatedHolding?.id === holdingId) ?? null;
}

test.describe('Investments TWR phase 3A fixed income', () => {
  test('INV-TWR-P3-FI-01: LCI pre-fixed purchase persists fixed-income terms and keeps tax-exempt valuation', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const lci = await createInvestmentAsset(
      {
        name: uniqueName('LCI pre-fix terms'),
        assetType: AssetType.Lci,
        issuerName: 'Banco E2E LCI',
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: lci.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(120),
        settlementDate: isoDaysAgo(120),
        quantity: 1,
        unitPrice: 10000,
        grossValue: 10000,
        netValue: 10000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.14,
        issueDate: isoDaysAgo(120),
        maturityDate: isoDaysFromNow(365),
        couponFrequency: CouponFrequency.AtMaturity,
        isTaxExempt: true,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, lci.id);
    expect(holding).not.toBeNull();
    expect(holding!.indexer).toBe(Indexer.PreFixado);
    expectNear(holding!.fixedAnnualRate ?? 0, 0.14, 8);
    expect(holding!.indexerPercentage).toBeNull();
    expect(holding!.couponFrequency).toBe(CouponFrequency.AtMaturity);
    expect(holding!.couponDayOfMonth).toBeNull();
    expect(holding!.isTaxExempt).toBe(true);
    expect(holding!.valuationSource).toBe(ValuationSource.Calculated);
    expectNear(holding!.incomeTax ?? 0, 0, 8);
    expectNear(holding!.iof ?? 0, 0, 8);
  });

  test('INV-TWR-P3-FI-02: coupon-bearing debenture creates visible COUPON_PAYMENT_DUE and rejects couponDayOfMonth out of range', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const couponDay = couponDaySoon();

    const debenture = await createInvestmentAsset(
      {
        name: uniqueName('Debenture coupon pending'),
        assetType: AssetType.Debentures,
        issuerName: 'Emissor Debenture E2E',
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: debenture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(60),
        settlementDate: isoDaysAgo(60),
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.055,
        issueDate: isoDaysAgo(60),
        maturityDate: isoDaysFromNow(400),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDay,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, debenture.id);
    expect(holding).not.toBeNull();
    expect(holding!.couponFrequency).toBe(CouponFrequency.Monthly);
    expect(holding!.couponDayOfMonth).toBe(couponDay);

    const couponPending = await findVisiblePendingAction(
      pfUser.token,
      holding!.id,
      PendingActionType.CouponPaymentDue,
    );
    expect(couponPending).not.toBeNull();
    const payload = couponPending!.payload as { couponFrequency?: string; couponDate?: string };
    expect(payload.couponFrequency).toBe('MONTHLY');
    expect(typeof payload.couponDate).toBe('string');

    const debentureAnnualHidden = await createInvestmentAsset(
      {
        name: uniqueName('Debenture coupon hidden before visible window'),
        assetType: AssetType.Debentures,
        issuerName: 'Emissor Debenture Anual E2E',
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: debentureAnnualHidden.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 1,
        unitPrice: 4000,
        grossValue: 4000,
        netValue: 4000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.052,
        issueDate: isoDaysAgo(20),
        maturityDate: isoDaysFromNow(900),
        couponFrequency: CouponFrequency.Annual,
        couponDayOfMonth: 28,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    const hiddenHolding = await findHolding(pfUser.token, brokerageAccount.id, debentureAnnualHidden.id);
    expect(hiddenHolding).not.toBeNull();
    const hiddenCouponPending = await findVisiblePendingAction(
      pfUser.token,
      hiddenHolding!.id,
      PendingActionType.CouponPaymentDue,
    );
    expect(hiddenCouponPending).toBeNull();

    const invalidDebenture = await createInvestmentAsset(
      {
        name: uniqueName('Debenture invalid coupon day'),
        assetType: AssetType.Debentures,
      },
      pfUser.token,
    );

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: invalidDebenture.id,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          tradeDate: isoDaysAgo(30),
          settlementDate: isoDaysAgo(30),
          quantity: 1,
          unitPrice: 3000,
          grossValue: 3000,
          netValue: 3000,
          indexer: Indexer.PreFixado,
          fixedAnnualRate: 0.11,
          issueDate: isoDaysAgo(30),
          maturityDate: isoDaysFromNow(200),
          couponFrequency: CouponFrequency.Monthly,
          couponDayOfMonth: 32,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/couponDayOfMonth|1-31|1–31|Invalid input/i);
  });

  test('INV-TWR-P3-FI-03: early redemption with IOF credits net cash and cancels visible maturity pending action', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB early redemption'),
        assetType: AssetType.Cdb,
        issuerName: 'Banco E2E CDB',
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
        unitPrice: 10000,
        grossValue: 10000,
        netValue: 10000,
        indexer: Indexer.Cdi,
        indexerPercentage: 1.15,
        issueDate: isoDaysAgo(40),
        maturityDate: isoDaysAgo(2),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdingBeforeSale = await findHolding(pfUser.token, brokerageAccount.id, cdb.id);
    expect(holdingBeforeSale).not.toBeNull();

    const maturityPendingBefore = await findVisiblePendingAction(
      pfUser.token,
      holdingBeforeSale!.id,
      PendingActionType.InvestmentMatured,
    );
    expect(maturityPendingBefore).not.toBeNull();

    const cashBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const saleTx = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Sale,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 1,
        unitPrice: 10500,
        grossValue: 10500,
        netValue: 9900,
        iof: 120,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const cashAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(cashAfter - cashBefore, 9900, 6);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    const linkedCashRow = cashRows.find((row) => row.investmentTransactionId === saleTx.id);
    expect(linkedCashRow).toBeTruthy();
    expect(linkedCashRow?.creditDebitType).toBe('CREDIT');
    expectNear(linkedCashRow?.amount ?? 0, 9900, 6);

    const maturityPendingAfter = await findVisiblePendingAction(
      pfUser.token,
      holdingBeforeSale!.id,
      PendingActionType.InvestmentMatured,
    );
    expect(maturityPendingAfter).toBeNull();
  });

  test('INV-TWR-P3-FI-04: maturity confirmation succeeds on matured holdings and rejects non-matured holdings', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdbFuture = await createInvestmentAsset(
      {
        name: uniqueName('CDB maturity future invalid'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbFuture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(15),
        maturityDate: isoDaysFromNow(30),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const futureHolding = await findHolding(pfUser.token, brokerageAccount.id, cdbFuture.id);
    expect(futureHolding).not.toBeNull();

    await expect(
      confirmHoldingMaturity(
        {
          holdingId: futureHolding!.id,
          maturityDate: isoDaysFromNow(30),
          grossValue: 1100,
          netValue: 1080,
          closeReason: HoldingCloseReason.Matured,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/HOLDING_MATURITY_INVALID_STATE|not matured|maturity/i);

    const cdbMatured = await createInvestmentAsset(
      {
        name: uniqueName('CDB maturity success'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbMatured.id,
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
        maturityDate: isoDaysAgo(1),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const maturedHolding = await findHolding(pfUser.token, brokerageAccount.id, cdbMatured.id);
    expect(maturedHolding).not.toBeNull();

    const cashBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });

    const result = await confirmHoldingMaturity(
      {
        holdingId: maturedHolding!.id,
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

    expect(result.pendingAction.status).toBe(PendingActionStatus.Completed);

    const cashAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    expectNear(cashAfter - cashBefore, 1085, 6);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 100,
    });
    const linkedRow = cashRows.find(
      (row) => row.investmentTransactionId === result.maturityTransaction.id,
    );
    expect(linkedRow).toBeTruthy();
    expect(linkedRow?.creditDebitType).toBe('CREDIT');
    expectNear(linkedRow?.amount ?? 0, 1085, 6);

    await expect(
      confirmHoldingMaturity(
        {
          holdingId: maturedHolding!.id,
          maturityDate: isoDaysAgo(1),
          grossValue: 1110,
          netValue: 1090,
          closeReason: HoldingCloseReason.Matured,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/already closed|invalid state|pending action|maturity|Invalid input/i);
  });

  test('INV-TWR-P3-FI-05: coupon payment keeps position invariant and credits destination account while enforcing value validations', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const couponDay = couponDaySoon();

    const debenture = await createInvestmentAsset(
      {
        name: uniqueName('Debenture coupon confirm'),
        assetType: AssetType.Debentures,
        issuerName: 'Emissor Cupom E2E',
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: debenture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(75),
        settlementDate: isoDaysAgo(75),
        quantity: 1,
        unitPrice: 10000,
        grossValue: 10000,
        netValue: 10000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.06,
        issueDate: isoDaysAgo(75),
        maturityDate: isoDaysFromNow(500),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDay,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holdingBefore = await findHolding(pfUser.token, brokerageAccount.id, debenture.id);
    expect(holdingBefore).not.toBeNull();

    const pendingAction = await findVisiblePendingAction(
      pfUser.token,
      holdingBefore!.id,
      PendingActionType.CouponPaymentDue,
    );
    expect(pendingAction).not.toBeNull();

    await expect(
      confirmCouponPayment(
        {
          holdingId: holdingBefore!.id,
          tradeDate: isoDaysAgo(1),
          grossValue: 0,
          netValue: 0,
          incomeTax: 0,
          destinationAccountId: secondaryBrokerageAccount.id,
          pendingActionId: pendingAction!.id,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/grossValue|must be > 0|Invalid input/i);

    await expect(
      confirmCouponPayment(
        {
          holdingId: holdingBefore!.id,
          tradeDate: isoDaysAgo(1),
          grossValue: 100,
          netValue: 120,
          incomeTax: 0,
          destinationAccountId: secondaryBrokerageAccount.id,
          pendingActionId: pendingAction!.id,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/netValue|grossValue|must be <= grossValue|Invalid input/i);

    const sourceBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const targetBefore = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });

    const result = await confirmCouponPayment(
      {
        holdingId: holdingBefore!.id,
        tradeDate: isoDaysAgo(1),
        grossValue: 500,
        netValue: 425,
        incomeTax: 75,
        destinationAccountId: secondaryBrokerageAccount.id,
        pendingActionId: pendingAction!.id,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: brokerageAccount.id,
    });
    const targetAfter = await getInvestmentCashBalance(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
    });

    expectNear(sourceAfter, sourceBefore, 6);
    expectNear(targetAfter - targetBefore, 425, 6);

    const targetRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 100,
    });
    const couponCashRow = targetRows.find(
      (row) => row.investmentTransactionId === result.couponTransaction.id,
    );
    expect(couponCashRow).toBeTruthy();
    expect(couponCashRow?.creditDebitType).toBe('CREDIT');
    expectNear(couponCashRow?.amount ?? 0, 425, 6);

    const holdingAfter = await findHolding(pfUser.token, brokerageAccount.id, debenture.id);
    expect(holdingAfter).not.toBeNull();
    expectNear(holdingAfter!.quantity, holdingBefore!.quantity, 8);
    expectNear(holdingAfter!.averageCost, holdingBefore!.averageCost, 8);
  });

  test('INV-TWR-P3-FI-06: valuation source invariants and custody transfer semantics via canonical CUSTODY_TRANSFER events', async ({
    pfUser,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    const cdbCalculated = await createInvestmentAsset(
      {
        name: uniqueName('CDB valuation calculated'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const cdbUnavailable = await createInvestmentAsset(
      {
        name: uniqueName('CDB valuation unavailable'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const lciExempt = await createInvestmentAsset(
      {
        name: uniqueName('LCI tax exempt valuation'),
        assetType: AssetType.Lci,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbCalculated.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(420),
        settlementDate: isoDaysAgo(420),
        quantity: 1,
        unitPrice: 2000,
        grossValue: 2000,
        netValue: 2000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(420),
        maturityDate: isoDaysFromNow(600),
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbUnavailable.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(90),
        settlementDate: isoDaysAgo(90),
        quantity: 1,
        unitPrice: 1500,
        grossValue: 1500,
        netValue: 1500,
        indexer: Indexer.Outros,
        issueDate: isoDaysAgo(90),
        maturityDate: isoDaysFromNow(240),
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: lciExempt.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(120),
        settlementDate: isoDaysAgo(120),
        quantity: 1,
        unitPrice: 1200,
        grossValue: 1200,
        netValue: 1200,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(120),
        maturityDate: isoDaysFromNow(365),
        isTaxExempt: true,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const calcHolding = await findHolding(pfUser.token, brokerageAccount.id, cdbCalculated.id);
    const unavailableHolding = await findHolding(pfUser.token, brokerageAccount.id, cdbUnavailable.id);
    const exemptHolding = await findHolding(pfUser.token, brokerageAccount.id, lciExempt.id);
    const cdbEstimated = await createInvestmentAsset(
      {
        name: uniqueName('CDB valuation estimated'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbEstimated.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(180),
        settlementDate: isoDaysAgo(180),
        quantity: 1,
        unitPrice: 1800,
        grossValue: 1800,
        netValue: 1800,
        indexer: Indexer.Cdi,
        indexerPercentage: 1.1,
        issueDate: isoDaysAgo(180),
        maturityDate: isoDaysFromNow(540),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const estimatedHolding = await findHolding(pfUser.token, brokerageAccount.id, cdbEstimated.id);

    expect(calcHolding).not.toBeNull();
    expect(unavailableHolding).not.toBeNull();
    expect(exemptHolding).not.toBeNull();
    expect(estimatedHolding).not.toBeNull();

    expect(calcHolding!.valuationSource).toBe(ValuationSource.Calculated);
    expect(unavailableHolding!.valuationSource).toBe(ValuationSource.Unavailable);
    expect([ValuationSource.Estimated, ValuationSource.Unavailable]).toContain(
      estimatedHolding!.valuationSource,
    );
    expect(exemptHolding!.isTaxExempt).toBe(true);
    expectNear(exemptHolding!.incomeTax ?? 0, 0, 8);
    expectNear(exemptHolding!.iof ?? 0, 0, 8);

    const transferOut = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdbCalculated.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 1,
        grossValue: 2000,
        netValue: 2000,
      },
      pfUser.token,
    );
    expect(transferOut.type).toBe(InvestmentTransactionType.CustodyTransfer);

    const transferIn = await recordInvestmentTransaction(
      {
        financialAccountId: secondaryBrokerageAccount.id,
        assetId: cdbCalculated.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.CustodyTransfer,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        quantity: 1,
        unitPrice: 2000,
        grossValue: 2000,
        netValue: 2000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(420),
        maturityDate: isoDaysFromNow(600),
      },
      pfUser.token,
    );
    expect(transferIn.type).toBe(InvestmentTransactionType.CustodyTransfer);

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondaryBrokerageAccount.id);

    const sourceHoldingAfterTransfer = await findHolding(
      pfUser.token,
      brokerageAccount.id,
      cdbCalculated.id,
    );
    const destinationHolding = await findHolding(
      pfUser.token,
      secondaryBrokerageAccount.id,
      cdbCalculated.id,
    );

    expect(sourceHoldingAfterTransfer).toBeNull();
    expect(destinationHolding).not.toBeNull();
    expectNear(destinationHolding!.quantity, 1, 8);
    expectNear(destinationHolding!.averageCost, 2000, 8);

    const sourceCashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 200,
    });
    const destinationCashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: secondaryBrokerageAccount.id,
      first: 200,
    });

    expect(sourceCashRows.some((row) => row.investmentTransactionId === transferOut.id)).toBe(false);
    expect(destinationCashRows.some((row) => row.investmentTransactionId === transferIn.id)).toBe(false);
  });

  test('INV-TWR-P4A-FI-01: AMORTIZATION credits cash for fixed-income assets', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cri = await createInvestmentAsset(
      {
        name: uniqueName('CRI amortization'),
        assetType: AssetType.Cri,
      },
      pfUser.token,
    );
    const treasury = await createInvestmentAsset(
      {
        name: uniqueName('Treasury amortization'),
        assetType: AssetType.Treasury,
        issuerName: 'Tesouro Nacional',
      },
      pfUser.token,
    );
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB amortization rejection'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cri.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(120),
        settlementDate: isoDaysAgo(120),
        quantity: 1,
        unitPrice: 10000,
        grossValue: 10000,
        netValue: 10000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(120),
        maturityDate: isoDaysFromNow(720),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: treasury.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(240),
        settlementDate: isoDaysAgo(240),
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.06,
        issueDate: isoDaysAgo(240),
        maturityDate: isoDaysFromNow(3650),
        couponFrequency: CouponFrequency.SemiAnnual,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(90),
        settlementDate: isoDaysAgo(90),
        quantity: 1,
        unitPrice: 2000,
        grossValue: 2000,
        netValue: 2000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(90),
        maturityDate: isoDaysFromNow(400),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const criBefore = await findHolding(pfUser.token, brokerageAccount.id, cri.id);
    const treasuryHolding = await findHolding(pfUser.token, brokerageAccount.id, treasury.id);
    expect(criBefore).not.toBeNull();
    expect(treasuryHolding).not.toBeNull();
    expect(treasuryHolding!.couponFrequency).toBe(CouponFrequency.SemiAnnual);
    expect(treasuryHolding!.couponDayOfMonth).toBeNull();

    const criAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cri.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(2),
        settlementDate: isoDaysAgo(2),
        grossValue: 1000,
        netValue: 1000,
        incomeTax: 0,
      },
      pfUser.token,
    );
    const treasuryAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: treasury.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        grossValue: 500,
        netValue: 500,
        incomeTax: 0,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const criAfter = await findHolding(pfUser.token, brokerageAccount.id, cri.id);
    expect(criAfter).not.toBeNull();
    expectNear(criAfter!.quantity, criBefore!.quantity, 8);
    expectNear(criAfter!.averageCost, criBefore!.averageCost, 8);

    const cashRows = await getInvestmentCashTransactions(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      first: 300,
    });
    const criCash = cashRows.find((row) => row.investmentTransactionId === criAmortization.id);
    const treasuryCash = cashRows.find((row) => row.investmentTransactionId === treasuryAmortization.id);
    expect(criCash).toBeTruthy();
    expect(treasuryCash).toBeTruthy();
    expect(criCash?.creditDebitType).toBe('CREDIT');
    expect(treasuryCash?.creditDebitType).toBe('CREDIT');
    expectNear(criCash?.amount ?? 0, 1000, 6);
    expectNear(treasuryCash?.amount ?? 0, 500, 6);

    const cdbAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cdb.id,
        direction: InvestmentTransactionDirection.Outflow,
        type: InvestmentTransactionType.Amortization,
        tradeDate: isoDaysAgo(1),
        settlementDate: isoDaysAgo(1),
        grossValue: 200,
        netValue: 200,
      },
      pfUser.token,
    );
    expect(cdbAmortization.type).toBe(InvestmentTransactionType.Amortization);

    const zeroAmortization = await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: cri.id,
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
        assetId: cri.id,
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
  });

  test('INV-TWR-P4A-FI-02: CUSTODY_TRANSFER rejects quantity greater than current fixed-income holding quantity', async ({
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('CDB transfer validation'),
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
        fixedAnnualRate: 0.11,
        issueDate: isoDaysAgo(20),
        maturityDate: isoDaysFromNow(700),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await expect(
      recordInvestmentTransaction(
        {
          financialAccountId: brokerageAccount.id,
          assetId: cdb.id,
          direction: InvestmentTransactionDirection.Outflow,
          type: InvestmentTransactionType.CustodyTransfer,
          tradeDate: isoDaysAgo(1),
          settlementDate: isoDaysAgo(1),
          quantity: 2,
          grossValue: 2000,
          netValue: 2000,
        },
        pfUser.token,
      ),
    ).rejects.toThrow(/Transfer quantity|exceeds current holding quantity|insuficiente|Invalid input/i);
  });

  test('INV-TWR-P3-FI-07: treasury fixed-income form pre-fills Tesouro issuer and coupon-bearing purchase schedules coupon pending action', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openFixedIncomeRecordDrawer(page);

    await selectByTestIdOption(page, 'record-asset-type', /Tesouro|Treasury/i);
    const issuerInput = page.locator('input[name="issuerName"]').first();
    await expect(issuerInput).toHaveValue(/Tesouro Nacional/i);
    await expect(issuerInput).toHaveAttribute('readonly', '');

    await selectByTestIdOption(page, 'record-indexer', /CDI/i);
    await expect(page.getByTestId('record-indexer-percentage')).toBeVisible();
    await expect(page.getByTestId('record-pre-fixed-rate')).toHaveCount(0);

    await selectByTestIdOption(page, 'record-indexer', /Pré-fixado|Pre-fixed|Prefixado/i);
    await expect(page.getByTestId('record-pre-fixed-rate')).toBeVisible();
    await expect(page.getByTestId('record-indexer-percentage')).toHaveCount(0);

    await selectByTestIdOption(page, 'record-coupon-frequency', /No vencimento|At maturity/i);
    const couponDayInput = page.locator('input[name="couponDayOfMonth"]').first();
    await expect(couponDayInput).toHaveCount(0);

    await selectByTestIdOption(page, 'record-coupon-frequency', /Semestral|Semiannual/i);
    await expect(couponDayInput).toHaveCount(0);

    await page
      .locator('form#record-fixed-income-form')
      .getByRole('button', { name: /cancelar|cancel/i })
      .click();
    await expect(page.locator('form#record-fixed-income-form')).toHaveCount(0);

    const debenture = await createInvestmentAsset(
      {
        name: uniqueName('Debenture coupon FI07'),
        assetType: AssetType.Debentures,
        issuerName: 'Emissor Debenture E2E',
      },
      pfUser.token,
    );

    const couponDay = couponDaySoon();
    const purchaseDate = couponPendingPurchaseDate();
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: debenture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: purchaseDate,
        settlementDate: purchaseDate,
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.06,
        issueDate: purchaseDate,
        maturityDate: isoDaysFromNow(900),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDay,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const holding = await findHolding(pfUser.token, brokerageAccount.id, debenture.id);
    expect(holding).not.toBeNull();
    let couponPending: Awaited<ReturnType<typeof findVisiblePendingAction>> = null;
    await expect
      .poll(
        async () => {
          couponPending = await findVisiblePendingAction(
            pfUser.token,
            holding!.id,
            PendingActionType.CouponPaymentDue,
          );
          return couponPending != null;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(true);
    const payload = couponPending!.payload as { couponFrequency?: string; couponDate?: string };
    expect(payload.couponFrequency).toBe('MONTHLY');
    expect(typeof payload.couponDate).toBe('string');
  });

  test('INV-TWR-P3-FI-08: empty PURCHASE fixed-income form shows required field validation errors', async ({
    page,
    brokerageAccount,
  }) => {
    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openFixedIncomeRecordDrawer(page);

    const submit = page.getByTestId('record-fixed-income-submit-btn');
    await expect(submit).toBeEnabled({ timeout: 15_000 });
    await submit.click();

    await expect(page.getByText(/Nome do ativo é obrigatório|Asset name is required/i)).toBeVisible();
    await expect(page.getByText(/Tipo de ativo é obrigatório|Asset type is required/i)).toBeVisible();
    await expect(page.getByText(/Emissor é obrigatório|Issuer is required/i)).toBeVisible();
    await expect(page.getByText(/Data da aplicação é obrigatória|Application date is required/i)).toBeVisible();
    await expect(page.getByText(/Data de vencimento é obrigatória|Maturity date is required/i)).toBeVisible();
    await expect(page.getByText(/Indexador é obrigatório|Indexer is required/i)).toBeVisible();
    await expect(
      page.getByText(/Valor aplicado é obrigatório|Invested amount is required|Valor deve ser maior que zero|Amount must be greater than zero/i),
    ).toBeVisible();
  });
});
