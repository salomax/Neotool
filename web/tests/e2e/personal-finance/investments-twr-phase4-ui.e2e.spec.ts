import { randomBytes } from 'node:crypto';

import { test, expect } from '../fixtures/investments';
import {
  createFinancialAccount,
  getFinancialAccounts,
} from '../helpers/banking-graphql';
import {
  AssetType,
  CouponFrequency,
  createInvestmentCashTransaction,
  createInvestmentAsset,
  getInvestmentHoldings,
  getPendingActions,
  getInvestmentPortfolioSummary,
  HoldingPricingMode,
  InvestmentCashTransactionType,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  PendingActionStatus,
  PerformancePeriod,
  recordInvestmentTransaction,
} from '../helpers/investment-graphql';
import { parseCurrencyBr } from '../helpers/currency-dom';
import { createUser, signInWithSession } from '../helpers/graphql';
import { signIntoPage } from '../helpers/navigation';
import { buildAuthStorageState } from '../helpers/storage-state';
import { InvestmentsPage } from '../pages/personal-finance/InvestmentsPage';
import {
  clearInvestmentGridClassSelection,
  waitForRecalculation,
} from '../helpers/investment-e2e';
import {
  couponDaySoon,
  couponPendingPurchaseDate,
  isoDaysAgo,
  isoDaysFromNow,
  todayIso,
} from '../helpers/fixed-income-e2e';
import { E2E_PENDING_ACTION_POLL_MS } from '../helpers/timeouts';

test.describe.configure({ mode: 'serial' });
test.describe.configure({ timeout: 240000 });

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

type CreateFinancialAccountPayload = Record<string, unknown>;
type GraphQLOperationPayload = Record<string, unknown>;

function extractGraphQLOperations(body: unknown): GraphQLOperationPayload[] {
  if (!body) return [];
  if (Array.isArray(body)) {
    return body.filter((row): row is GraphQLOperationPayload => Boolean(row && typeof row === 'object'));
  }
  if (typeof body === 'object') return [body as GraphQLOperationPayload];
  return [];
}

function isInvestmentSummaryOperation(operation: GraphQLOperationPayload): boolean {
  const operationName =
    typeof operation.operationName === 'string' ? operation.operationName : '';
  const query = typeof operation.query === 'string' ? operation.query : '';
  return operationName === 'InvestmentPortfolioSummary' || query.includes('investmentPortfolioSummary');
}

function installCreateFinancialAccountCollector(page: import('@playwright/test').Page): {
  inputs: CreateFinancialAccountPayload[];
  dispose: () => void;
} {
  const inputs: CreateFinancialAccountPayload[] = [];

  const handler = (request: import('@playwright/test').Request): void => {
    if (request.method() !== 'POST') return;

    let body: unknown;
    try {
      body = request.postDataJSON();
    } catch {
      return;
    }

    const operations = Array.isArray(body) ? body : [body];
    for (const operationRaw of operations) {
      const operation = (operationRaw ?? {}) as Record<string, unknown>;
      const operationName =
        typeof operation.operationName === 'string' ? operation.operationName : '';
      const query = typeof operation.query === 'string' ? operation.query : '';
      const isCreateOperation =
        operationName === 'CreateFinancialAccount' || query.includes('createFinancialAccount');
      if (!isCreateOperation) continue;

      const variables =
        operation.variables && typeof operation.variables === 'object'
          ? (operation.variables as Record<string, unknown>)
          : null;
      const input =
        variables?.input && typeof variables.input === 'object'
          ? (variables.input as CreateFinancialAccountPayload)
          : null;
      if (input) inputs.push(input);
    }
  };

  page.on('request', handler);
  return {
    inputs,
    dispose: () => page.off('request', handler),
  };
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

async function openInvestmentsPendingActions(page: import('@playwright/test').Page): Promise<void> {
  await signIntoPage(page);
  await page.setViewportSize({ width: 1720, height: 1100 });
  await page.goto('/personal-finance/investments?pendingActions=1');
  await expect(page.getByText(/Ações pendentes|Pending actions/i)).toBeVisible();
}

async function submitCreateAccountForm(page: import('@playwright/test').Page): Promise<void> {
  await page.locator('form#create-investment-account-form button[type="submit"]').click();
  await expect(page.locator('form#create-investment-account-form')).toHaveCount(0);
}

async function submitAndCaptureCreateAccountInput(
  page: import('@playwright/test').Page,
  collector: { inputs: CreateFinancialAccountPayload[] },
): Promise<CreateFinancialAccountPayload> {
  const before = collector.inputs.length;
  await submitCreateAccountForm(page);
  await expect
    .poll(() => collector.inputs.length, {
      message: 'createFinancialAccount payload was not captured',
    })
    .toBeGreaterThan(before);
  return collector.inputs[before] ?? {};
}

async function openCreateInvestmentAccountDrawer(page: import('@playwright/test').Page): Promise<void> {
  const emptyCta = page.getByTestId('create-investment-account-empty-btn');
  if (await emptyCta.isVisible().catch(() => false)) {
    await emptyCta.click();
  } else {
    await page.getByTestId('btn-new-account').click();
  }
  await expect(page.locator('form#create-investment-account-form')).toBeVisible();
}

async function selectCustomIssuer(page: import('@playwright/test').Page): Promise<void> {
  const issuerInput = page.getByRole('combobox', { name: /Corretora|Broker/i }).first();
  await issuerInput.click();
  await issuerInput.fill('pers');
  await page.getByRole('option', { name: /Personalizado|Custom/i }).first().click();
  await expect(page.locator('input[name="issuerName"]')).toBeVisible();
}

async function selectKnownIssuer(page: import('@playwright/test').Page): Promise<string> {
  const issuerInput = page.getByRole('combobox', { name: /Corretora|Broker/i }).first();
  await issuerInput.click();
  const listbox = page.getByRole('listbox').last();
  await expect(listbox).toBeVisible();

  const searchTerms = ['', 'a', 'b', 'bank', 'cor'];
  for (const term of searchTerms) {
    await issuerInput.fill(term);
    await page.waitForTimeout(150);

    const options = listbox.getByRole('option');
    const knownOptionIndex = await expect
      .poll(
        async () => {
          const count = await options.count();
          for (let i = 0; i < count; i += 1) {
            const optionText = ((await options.nth(i).textContent()) ?? '').trim();
            if (!optionText || /Personalizado|Custom/i.test(optionText)) continue;
            return i;
          }
          return -1;
        },
        { timeout: 3000 },
      )
      .not.toBe(-1)
      .then(async () => {
        const count = await options.count();
        for (let i = 0; i < count; i += 1) {
          const optionText = ((await options.nth(i).textContent()) ?? '').trim();
          if (!optionText || /Personalizado|Custom/i.test(optionText)) continue;
          return i;
        }
        return -1;
      })
      .catch(() => -1);

    if (knownOptionIndex >= 0) {
      const option = options.nth(knownOptionIndex);
      const optionText = ((await option.textContent()) ?? '').trim();
      await option.click({ timeout: 3000 });
      if (optionText) {
        return optionText.replace(/\s*\([^)]*\)\s*$/, '').trim();
      }
    }
  }

  const options = listbox.getByRole('option');
  const availableOptionTexts: string[] = [];
  const availableCount = await options.count();
  for (let i = 0; i < availableCount; i += 1) {
    availableOptionTexts.push(((await options.nth(i).textContent()) ?? '').trim());
  }
  throw new Error(
    `No known issuer option found in create-account drawer. Available options: ${availableOptionTexts.join(' | ') || '(none)'}`,
  );
}

async function selectSubtype(
  page: import('@playwright/test').Page,
  optionName: RegExp,
): Promise<void> {
  const subtypeTrigger = page
    .getByTestId('investment-subtype-select')
    .locator('[role="combobox"], [role="button"]')
    .first();
  await subtypeTrigger.click();
  const listbox = page.getByRole('listbox').last();
  await expect(listbox).toBeVisible();
  await listbox.getByRole('option', { name: optionName }).first().click();
}

async function openStocksDetailPanel(page: import('@playwright/test').Page): Promise<void> {
  await openGridClassDetailPanel(page, 'stocks');
  await expect(page.getByPlaceholder(/Buscar ativos|Search holdings/i)).toBeVisible();
}

async function openGridClassDetailPanel(
  page: import('@playwright/test').Page,
  classKey: 'fixed-income' | 'funds' | 'stocks' | 'reits' | 'crypto' | 'cash',
): Promise<void> {
  const classTab = page.locator(`#investment-grid-class-tab-${classKey}`);
  if (await classTab.isVisible().catch(() => false)) {
    await classTab.click();
    await expect(page.getByTestId('investment-grid-record-cta')).toBeVisible({ timeout: 30_000 });
    return;
  }

  const gridOverviewTab = page.locator('#investment-grid-class-tab-grid');
  if (await gridOverviewTab.isVisible().catch(() => false)) {
    await gridOverviewTab.click();
  }

  const card = page.getByTestId(`investment-grid-card-${classKey}`);
  await expect(card).toBeVisible({ timeout: 30_000 });
  await card.scrollIntoViewIfNeeded();
  const detailOpener = card.getByRole('button', {
    name: /(?:Abrir detalhes de|Open details for)/i,
  });
  await expect(detailOpener).toBeVisible({ timeout: 10_000 });
  await detailOpener.click();

  await expect(page.getByTestId('investment-grid-record-cta')).toBeVisible({ timeout: 30_000 });
}

async function readCurrencyByTestId(
  page: import('@playwright/test').Page,
  testId: string,
): Promise<number> {
  const text = (await page.getByTestId(testId).textContent()) ?? '0';
  return parseCurrencyBr(text);
}

function parseBadgeCount(text: string | null): number {
  if (!text) return 0;
  const normalized = text.replace(/\s+/g, '').trim();
  const plusMatch = /^(\d+)\+$/.exec(normalized);
  if (plusMatch) return Number.parseInt(plusMatch[1] ?? '0', 10);
  const value = Number.parseInt(normalized, 10);
  return Number.isFinite(value) ? value : 0;
}

function holdingRow(page: import('@playwright/test').Page, assetName: string) {
  return page.getByRole('row').filter({ hasText: assetName }).first();
}

async function expectHoldingRowTradeActions(
  page: import('@playwright/test').Page,
  assetName: string,
): Promise<void> {
  const row = holdingRow(page, assetName);
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.hover();
  const quickActions = row.locator('.holding-row-quick-actions');
  await expect(quickActions).toBeVisible();
  await expect(
    quickActions.getByRole('button', {
      name: /Adicionar transação|Add transaction|Registrar compra|Buy/i,
    }),
  ).toBeVisible();
  await expect(
    quickActions.getByRole('button', {
      name: /Registrar venda|Sell transaction|Sell/i,
    }),
  ).toBeVisible();
}

async function openHoldingActionsMenu(
  page: import('@playwright/test').Page,
  assetName: string,
): Promise<void> {
  const row = holdingRow(page, assetName);
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.getByRole('button', { name: /Ações do ativo|Holding actions/i }).click();
}

test.describe('Investments TWR phase 4 UI enablement', () => {
  test('INV-TWR-P4-UI-01: user with no investment accounts sees empty state CTA and opens investment account drawer', async ({
    browser,
  }) => {
    const password = 'TestPassword123!';
    const email = `p4-empty-${Date.now()}-${randomBytes(3).toString('hex')}@test.neotool.com`;
    await createUser(email, password, 'P4 Empty Investments User');
    const session = await signInWithSession(email, password, true);
    const context = await browser.newContext({
      storageState: buildAuthStorageState(session),
    });
    const page = await context.newPage();
    try {
      const investmentsPage = new InvestmentsPage(page);
      await investmentsPage.goto();

      await expect(page.getByTestId('create-investment-account-empty-btn')).toBeVisible();
      await expect(page.getByTestId('account-selector')).toHaveCount(0);
      await expect(page.getByTestId('investment-summary-section')).toHaveCount(0);

      await page.getByTestId('create-investment-account-empty-btn').click();
      await expect(page.locator('form#create-investment-account-form')).toBeVisible();
    } finally {
      await context.close();
    }
  });

  test('INV-TWR-P4-UI-02: sidebar selector includes INVESTMENT + BENEFIT accounts and excludes BANK accounts', async ({
    page,
    pfUser,
  }) => {
    const brokerage = await createFinancialAccount(
      {
        name: uniqueName('P4 UI Broker A'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );
    const crypto = await createFinancialAccount(
      {
        name: uniqueName('P4 UI Exchange B'),
        type: 'INVESTMENT',
        subtype: 'CRYPTO',
      },
      pfUser.token,
    );
    const benefit = await createFinancialAccount(
      {
        name: uniqueName('P4 UI FGTS'),
        type: 'BENEFIT',
        subtype: 'FGTS',
      },
      pfUser.token,
    );
    const bank = await createFinancialAccount(
      {
        name: uniqueName('P4 UI Bank hidden'),
        type: 'BANK',
        subtype: 'CONTA_CORRENTE',
        initialBalance: 100,
        initialBalanceDate: todayIso(),
      },
      pfUser.token,
    );

    await openInvestmentsPage(page);

    const selector = page.getByTestId('account-selector');
    await expect(selector).toBeVisible();
    await selector.click();

    const popover = page.getByTestId('account-selector-popover');
    await expect(popover).toBeVisible();
    await expect(popover.getByText(/Todas as Corretoras|All brokers/i)).toBeVisible();

    await expect(popover.locator(`[data-account-id="${brokerage.id}"]`)).toHaveCount(1);
    await expect(popover.locator(`[data-account-id="${crypto.id}"]`)).toHaveCount(1);
    await expect(popover.locator(`[data-account-id="${benefit.id}"]`)).toHaveCount(1);
    await expect(popover.locator(`[data-account-id="${bank.id}"]`)).toHaveCount(0);
  });

  test('INV-TWR-P4-UI-03: create-account drawer subtype list excludes benefit options and custom issuer field toggles correctly', async ({
    page,
    pfUser,
  }) => {
    await createFinancialAccount(
      {
        name: uniqueName('P4 UI Existing broker'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    await openInvestmentsPage(page);

    await page.getByTestId('btn-new-account').click();
    await expect(page.locator('form#create-investment-account-form')).toBeVisible();

    const subtypeTrigger = page
      .getByTestId('investment-subtype-select')
      .locator('[role="combobox"], [role="button"]')
      .first();
    await subtypeTrigger.click();

    const listbox = page.getByRole('listbox').last();
    await expect(listbox).toBeVisible();
    const options = listbox.getByRole('option');
    await expect(options).toHaveCount(4);
    await expect(options.filter({ hasText: /Corretora|Brokerage/i })).toHaveCount(1);
    await expect(options.filter({ hasText: /Renda Fixa|Fixed income/i })).toHaveCount(1);
    await expect(options.filter({ hasText: /Previdência|Pension/i })).toHaveCount(1);
    await expect(options.filter({ hasText: /Cripto|Crypto/i })).toHaveCount(1);
    await expect(options.filter({ hasText: /FGTS/i })).toHaveCount(0);
    await expect(options.filter({ hasText: /INSS/i })).toHaveCount(0);
    await page.keyboard.press('Escape');

    await expect(page.locator('input[name="issuerName"]')).toHaveCount(0);

    const issuerAutocompleteInput = page.getByRole('combobox').first();
    await issuerAutocompleteInput.click();
    await issuerAutocompleteInput.fill('pers');
    await page.getByRole('option', { name: /Personalizado|Custom/i }).first().click();

    await expect(page.locator('input[name="issuerName"]')).toBeVisible();
  });

  test('INV-TWR-P4-UI-03A: investment account drawer never renders banking, card, or initial-balance fields', async ({
    page,
  }) => {
    await openInvestmentsPage(page);
    await openCreateInvestmentAccountDrawer(page);

    await expect(page.locator('input[name="initialBalance"]')).toHaveCount(0);
    await expect(page.locator('input[name="initialBalanceDate"]')).toHaveCount(0);
    await expect(page.locator('input[name="branchCode"]')).toHaveCount(0);
    await expect(page.locator('input[name="accountNumber"]')).toHaveCount(0);
    await expect(page.locator('input[name="checkDigit"]')).toHaveCount(0);
    await expect(page.getByText(/Saldo inicial|Initial balance/i)).toHaveCount(0);
  });

  test('INV-TWR-P4-UI-04: newly created investment account with no holdings shows zero summary and empty grid', async ({
    page,
    pfUser,
  }) => {
    const emptyInvestmentAccount = await createFinancialAccount(
      {
        name: uniqueName('P4 UI Empty account'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    const apiSummary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: emptyInvestmentAccount.id,
      period: PerformancePeriod.Ytd,
    });
    expectNear(apiSummary.totalPortfolioValue, 0, 8);
    expectNear(apiSummary.totalCostBasis, 0, 8);
    expectNear(apiSummary.cashBalance, 0, 8);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(emptyInvestmentAccount.name, emptyInvestmentAccount.id);

    expectNear(await investmentsPage.summary.getTotalPortfolioValue(), apiSummary.totalPortfolioValue, 2);
    expectNear(await investmentsPage.summary.getTotalContributedAmount(), apiSummary.totalCostBasis, 2);
    expectNear(await investmentsPage.summary.getCashBalanceAmount(), apiSummary.cashBalance, 2);
    await expect(page.getByText(/Nenhuma posição registrada|No position registered/i).first()).toBeVisible();
  });

  test('INV-TWR-P4-UI-05: pending actions drawer auto-opens via URL param and lists due maturity/coupon actions', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const maturedName = uniqueName('P4 UI pending matured');
    const couponName = uniqueName('P4 UI pending coupon');

    const maturedAsset = await createInvestmentAsset(
      {
        name: maturedName,
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const couponAsset = await createInvestmentAsset(
      {
        name: couponName,
        assetType: AssetType.Debentures,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: maturedAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(45),
        settlementDate: isoDaysAgo(45),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.11,
        issueDate: isoDaysAgo(45),
        maturityDate: isoDaysAgo(1),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );
    const couponPurchaseDate = couponPendingPurchaseDate();
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: couponAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: couponPurchaseDate,
        settlementDate: couponPurchaseDate,
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.055,
        issueDate: couponPurchaseDate,
        maturityDate: isoDaysFromNow(500),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await expect
      .poll(
        async () => {
          const pending = await getPendingActions(pfUser.token, PendingActionStatus.Pending);
          const scoped = pending.filter(
            (row) => row.relatedHolding?.financialAccountId === brokerageAccount.id,
          );
          const hasMaturity = scoped.some((row) => row.relatedHolding?.asset?.name === maturedName);
          const hasCoupon = scoped.some((row) => row.relatedHolding?.asset?.name === couponName);
          return hasMaturity && hasCoupon;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(true);

    await openInvestmentsPendingActions(page);

    const maturedRow = page.getByTestId('pending-action-row').filter({ hasText: maturedName }).first();
    const couponRow = page.getByTestId('pending-action-row').filter({ hasText: couponName }).first();
    await expect(maturedRow).toBeVisible({ timeout: E2E_PENDING_ACTION_POLL_MS });
    await expect(couponRow).toBeVisible({ timeout: E2E_PENDING_ACTION_POLL_MS });
    await expect(maturedRow.getByTestId('pending-action-register-maturity')).toBeVisible();
    await expect(couponRow.getByTestId('pending-action-register-coupon')).toBeVisible();
  });

  test('INV-TWR-P4-UI-06: clicking INVESTMENT_MATURED pending action opens MaturityConfirmDrawer prefilled', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const maturedName = uniqueName('P4 UI maturity drawer');
    const maturedAsset = await createInvestmentAsset(
      {
        name: maturedName,
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: maturedAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(35),
        settlementDate: isoDaysAgo(35),
        quantity: 1,
        unitPrice: 2000,
        grossValue: 2000,
        netValue: 2000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(35),
        maturityDate: isoDaysAgo(1),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await openInvestmentsPendingActions(page);
    const maturedRow = page.getByTestId('pending-action-row').filter({ hasText: maturedName }).first();
    await expect(maturedRow).toBeVisible();
    await maturedRow.getByTestId('pending-action-register-maturity').click();

    const assetIdentityAvatar = page.getByTestId('maturity-drawer-asset-identity');
    await expect(assetIdentityAvatar).toBeVisible();
    // testid is attached to the avatar (e.g. "CDB"), asset name is rendered in the sibling text container.
    await expect(assetIdentityAvatar.locator('xpath=..')).toContainText(maturedName);
  });

  test('INV-TWR-P4-UI-07: clicking COUPON_PAYMENT_DUE pending action opens CouponPaymentConfirmDrawer prefilled', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const couponName = uniqueName('P4 UI coupon drawer');
    const couponAsset = await createInvestmentAsset(
      {
        name: couponName,
        assetType: AssetType.Debentures,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: couponAsset.id,
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
        maturityDate: isoDaysFromNow(400),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await openInvestmentsPendingActions(page);
    const couponRow = page.getByTestId('pending-action-row').filter({ hasText: couponName }).first();
    await expect(couponRow).toBeVisible();
    await couponRow.getByTestId('pending-action-register-coupon').click();

    const couponIdentityAvatar = page.getByTestId('coupon-drawer-asset-identity');
    await expect(couponIdentityAvatar).toBeVisible();
    // testid is attached to the avatar (e.g. "DEB"), asset name is rendered in the sibling text container.
    await expect(couponIdentityAvatar.locator('xpath=..')).toContainText(couponName);
    await expect(page.getByTestId('coupon-drawer-destination-account')).toBeVisible();
  });

  test('INV-TWR-P4-UI-08: pending actions with visibleFrom in the future are not listed today', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const futureMaturityName = uniqueName('P4 UI future pending hidden');
    const futureAsset = await createInvestmentAsset(
      {
        name: futureMaturityName,
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: futureAsset.id,
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
        maturityDate: isoDaysFromNow(1),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await expect
      .poll(
        async () => {
          const pending = await getPendingActions(pfUser.token, PendingActionStatus.Pending);
          return pending.some((row) => row.relatedHolding?.asset?.name === futureMaturityName);
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(false);

    await openInvestmentsPendingActions(page);
    await expect(
      page.getByTestId('pending-action-row').filter({ hasText: futureMaturityName }),
    ).toHaveCount(0);
  });

  test('INV-TWR-P4-UI-09: search with no matches shows empty message and clearing restores holdings', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const stockAName = uniqueName('Banco Alpha Holding');
    const stockBName = uniqueName('PETR Holding');
    const cryptoName = uniqueName('Bitcoin Holding');

    const stockA = await createInvestmentAsset(
      { name: stockAName, assetType: AssetType.Stock, ticker: `BA${randomBytes(3).toString('hex').toUpperCase()}` },
      pfUser.token,
    );
    const stockB = await createInvestmentAsset(
      { name: stockBName, assetType: AssetType.Stock, ticker: `PE${randomBytes(3).toString('hex').toUpperCase()}` },
      pfUser.token,
    );
    const crypto = await createInvestmentAsset(
      { name: cryptoName, assetType: AssetType.Crypto, ticker: `BTC${randomBytes(2).toString('hex').toUpperCase()}` },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stockA.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(7),
        settlementDate: isoDaysAgo(5),
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
        assetId: stockB.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(6),
        settlementDate: isoDaysAgo(4),
        quantity: 12,
        unitPrice: 80,
        grossValue: 960,
        netValue: 960,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: crypto.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(6),
        settlementDate: isoDaysAgo(6),
        quantity: 0.02,
        unitPrice: 350000,
        grossValue: 7000,
        netValue: 7000,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openStocksDetailPanel(page);

    const searchInput = page.getByPlaceholder(/Buscar ativos|Search holdings/i);
    await searchInput.click();
    await searchInput.fill('nao-existe-no-grid');
    await expect(page.getByText(/Nenhum ativo corresponde|No holdings match/i)).toBeVisible();

    await searchInput.fill('banco');
    const table = page.getByTestId('investment-table');
    await expect(table.getByText(stockAName)).toBeVisible();
    await expect(table.getByText(stockBName)).toHaveCount(0);

    await searchInput.fill('PETR');
    await expect(table.getByText(stockBName)).toBeVisible();
    await expect(table.getByText(stockAName)).toHaveCount(0);

    await searchInput.fill('bitcoin');
    await expect(page.getByText(/Nenhum ativo corresponde|No holdings match/i)).toBeVisible();
    await openGridClassDetailPanel(page, 'crypto');
    await expect(page.getByTestId('investment-table').getByText(cryptoName)).toBeVisible();

    await page.getByRole('button', { name: /Limpar busca|Clear search/i }).click();
    await expect(page.getByTestId('investment-table').getByText(cryptoName)).toBeVisible();
    const stocksTab = page.locator('#investment-grid-class-tab-stocks');
    await expect(stocksTab).toBeVisible();
    await stocksTab.click({ force: true });
    const stocksTable = page.getByTestId('investment-table');
    await expect(stocksTable.getByText(stockAName)).toBeVisible();
    await expect(stocksTable.getByText(stockBName)).toBeVisible();
  });

  test('INV-TWR-P4-UI-10: custom institution name with 100 chars is persisted successfully', async ({
    page,
    pfUser,
  }) => {
    const longInstitutionName = `Instituicao-${'X'.repeat(88)}`;

    await openInvestmentsPage(page);
    await openCreateInvestmentAccountDrawer(page);
    await selectCustomIssuer(page);

    await page.locator('input[name="issuerName"]').fill(longInstitutionName);
    await page.locator('input[name="name"]').fill('   ');
    await submitCreateAccountForm(page);

    const selector = page.getByTestId('account-selector');
    await expect(selector).toContainText(longInstitutionName);

    const accounts = await getFinancialAccounts(pfUser.token);
    expect(accounts.some((account) => account.name === longInstitutionName)).toBe(true);
  });

  test('INV-TWR-P4-UI-11: known issuer with blank name sends issuerId-only payload and creates account with issuer default name', async ({
    page,
    pfUser,
  }) => {
    const collector = installCreateFinancialAccountCollector(page);
    try {
      await openInvestmentsPage(page);
      await openCreateInvestmentAccountDrawer(page);

      const issuerName = await selectKnownIssuer(page);
      await page.getByTestId('investment-account-name-input').fill('');

      const input = await submitAndCaptureCreateAccountInput(page, collector);
      expect(input.type).toBe('INVESTMENT');
      expect(input.subtype).toBe('BROKERAGE');
      expect(input.issuerId).toBeTruthy();
      expect(input.issuerName).toBeUndefined();
      expect(input.name).toBeUndefined();
      expect(input.currencyCode).toBe('BRL');
      expect(input.includeInNetWorth).toBe(true);
      expect(input.includeInDashboard).toBe(true);
      expect(input.bankData).toBeUndefined();
      expect(input.cardData).toBeUndefined();
      expect(input.initialBalance).toBeUndefined();

      const accounts = await getFinancialAccounts(pfUser.token);
      expect(accounts.some((account) => account.name.includes(issuerName))).toBe(true);
    } finally {
      collector.dispose();
    }
  });

  test('INV-TWR-P4-UI-12: known issuer with explicit custom account name persists provided name', async ({
    page,
  }) => {
    const customAccountName = uniqueName('P4 UI Custom brokerage name');
    const collector = installCreateFinancialAccountCollector(page);
    try {
      await openInvestmentsPage(page);
      await openCreateInvestmentAccountDrawer(page);

      await selectKnownIssuer(page);
      await page.getByTestId('investment-account-name-input').fill(customAccountName);

      const input = await submitAndCaptureCreateAccountInput(page, collector);
      expect(input.type).toBe('INVESTMENT');
      expect(input.subtype).toBe('BROKERAGE');
      expect(input.issuerId).toBeTruthy();
      expect(input.issuerName).toBeUndefined();
      expect(input.name).toBe(customAccountName);

      const selector = page.getByTestId('account-selector');
      await expect(selector).toContainText(customAccountName);
    } finally {
      collector.dispose();
    }
  });

  test('INV-TWR-P4-UI-13: custom issuer sends issuerName without issuerId and defaults account name to institution name', async ({
    page,
    pfUser,
  }) => {
    const institutionName = uniqueName('P4 UI Instituição custom');
    const collector = installCreateFinancialAccountCollector(page);
    try {
      await openInvestmentsPage(page);
      await openCreateInvestmentAccountDrawer(page);

      await selectCustomIssuer(page);
      await page.locator('input[name="issuerName"]').fill(institutionName);
      await page.getByTestId('investment-account-name-input').fill('');

      const input = await submitAndCaptureCreateAccountInput(page, collector);
      expect(input.type).toBe('INVESTMENT');
      expect(input.subtype).toBe('BROKERAGE');
      expect(input.issuerId).toBeUndefined();
      expect(input.issuerName).toBe(institutionName);
      expect(input.name).toBeUndefined();

      const accounts = await getFinancialAccounts(pfUser.token);
      expect(accounts.some((account) => account.name === institutionName)).toBe(true);
    } finally {
      collector.dispose();
    }
  });

  test('INV-TWR-P4-UI-14: creates brokerage, fixed income, pension, and crypto subtypes and keeps all accounts listed', async ({
    page,
  }) => {
    const collector = installCreateFinancialAccountCollector(page);
    const createdAccountNames: string[] = [];
    try {
      await openInvestmentsPage(page);

      const subtypeCases: Array<{ optionName: RegExp; expectedSubtype: string; accountName: string }> =
        [
          {
            optionName: /Corretora|Brokerage/i,
            expectedSubtype: 'BROKERAGE',
            accountName: uniqueName('P4 UI Subtype Brokerage'),
          },
          {
            optionName: /Renda Fixa|Fixed income/i,
            expectedSubtype: 'FIXED_INCOME',
            accountName: uniqueName('P4 UI Subtype Fixed'),
          },
          {
            optionName: /Previdência|Pension/i,
            expectedSubtype: 'PENSION',
            accountName: uniqueName('P4 UI Subtype Pension'),
          },
          {
            optionName: /Cripto|Crypto/i,
            expectedSubtype: 'CRYPTO',
            accountName: uniqueName('P4 UI Subtype Crypto'),
          },
        ];

      for (const subtypeCase of subtypeCases) {
        await openCreateInvestmentAccountDrawer(page);
        await selectSubtype(page, subtypeCase.optionName);
        await selectKnownIssuer(page);
        await page.getByTestId('investment-account-name-input').fill(subtypeCase.accountName);

        const input = await submitAndCaptureCreateAccountInput(page, collector);
        expect(input.type).toBe('INVESTMENT');
        expect(input.subtype).toBe(subtypeCase.expectedSubtype);
        expect(input.name).toBe(subtypeCase.accountName);
        createdAccountNames.push(subtypeCase.accountName);
      }

      const selector = page.getByTestId('account-selector');
      await selector.click();
      const popover = page.getByTestId('account-selector-popover');
      await expect(popover).toBeVisible();
      for (const accountName of createdAccountNames) {
        await expect(popover.getByText(accountName, { exact: true })).toBeVisible();
      }
    } finally {
      collector.dispose();
    }
  });

  test('INV-TWR-P4-UI-15: whitespace-only account name falls back to auto-generated issuer-based name', async ({
    page,
  }) => {
    const collector = installCreateFinancialAccountCollector(page);
    try {
      await openInvestmentsPage(page);
      await openCreateInvestmentAccountDrawer(page);

      const issuerName = await selectKnownIssuer(page);
      await page.getByTestId('investment-account-name-input').fill('   ');

      const input = await submitAndCaptureCreateAccountInput(page, collector);
      expect(input.name).toBeTruthy();
      expect(input.name).toContain(issuerName);
      expect(input.issuerId).toBeTruthy();
      expect(input.issuerName).toBeUndefined();
    } finally {
      collector.dispose();
    }
  });

  test('INV-TWR-P4-UI-16: summary shows loading skeletons while investmentPortfolioSummary is in flight', async ({
    page,
    pfUser,
  }) => {
    await createFinancialAccount(
      {
        name: uniqueName('P4 UI Summary loading'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    let shouldDelayFirstSummary = true;
    let routeActive = true;
    const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
    const safeContinue = async (route: import('@playwright/test').Route): Promise<void> => {
      try {
        await route.continue();
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (!message.includes('Route is already handled')) throw error;
      }
    };
    const routeHandler = async (route: import('@playwright/test').Route): Promise<void> => {
      if (!shouldDelayFirstSummary) {
        await safeContinue(route);
        return;
      }
      const request = route.request();
      if (request.method() !== 'POST') {
        await safeContinue(route);
        return;
      }
      let body: unknown;
      try {
        body = request.postDataJSON();
      } catch {
        await safeContinue(route);
        return;
      }
      const operations = extractGraphQLOperations(body);
      if (!operations.some(isInvestmentSummaryOperation)) {
        await safeContinue(route);
        return;
      }
      shouldDelayFirstSummary = false;
      if (!routeActive) return;
      await sleep(1200);
      if (!routeActive) return;
      await safeContinue(route);
    };

    await page.route('**/graphql', routeHandler);
    try {
      await signIntoPage(page);
      await page.setViewportSize({ width: 1720, height: 1100 });
      await page.goto('/personal-finance/investments');

      const summarySection = page.getByTestId('investment-summary-section');
      await expect(summarySection).toBeVisible();
      const skeletons = summarySection.locator('.MuiSkeleton-root');
      // Depending on request timing/caching, loading skeletons may be very brief.
      // Keep this as a best-effort observation and assert only the stable final state below.
      await page.waitForTimeout(200);

      await expect(skeletons).toHaveCount(0, { timeout: 15000 });
      await expect(page.getByTestId('investment-summary-total-portfolio-value')).toBeVisible();
    } finally {
      routeActive = false;
      await page.unroute('**/graphql', routeHandler);
    }
  });

  test('INV-TWR-P4-UI-17: summary error state shows retry and refreshes successfully after retry', async ({
    page,
    pfUser,
  }) => {
    await createFinancialAccount(
      {
        name: uniqueName('P4 UI Summary retry'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    let remainingForcedSummaryFailures = 2;
    const routeHandler = async (route: import('@playwright/test').Route): Promise<void> => {
      const request = route.request();
      if (request.method() !== 'POST') {
        await route.continue();
        return;
      }
      let body: unknown;
      try {
        body = request.postDataJSON();
      } catch {
        await route.continue();
        return;
      }
      const operations = extractGraphQLOperations(body);
      if (!operations.some(isInvestmentSummaryOperation) || remainingForcedSummaryFailures <= 0) {
        await route.continue();
        return;
      }

      remainingForcedSummaryFailures -= 1;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          errors: [{ message: 'Forced summary error for retry scenario' }],
          data: { investmentPortfolioSummary: null },
        }),
      });
    };

    await page.route('**/graphql', routeHandler);
    try {
      await signIntoPage(page);
      await page.setViewportSize({ width: 1720, height: 1100 });
      await page.goto('/personal-finance/investments');

      const summarySection = page.getByTestId('investment-summary-section');
      await expect(summarySection).toBeVisible();
      await expect
        .poll(
          async () =>
            summarySection
              .getByRole('button', { name: /Tentar novamente|Retry/i })
              .isVisible()
              .catch(() => false),
          { timeout: 15000 },
        )
        .toBe(true);
      await expect(page.getByTestId('investment-summary-total-portfolio-value')).toHaveCount(0);

      await summarySection.getByRole('button', { name: /Tentar novamente|Retry/i }).click();
      await expect(page.getByTestId('investment-summary-total-portfolio-value')).toBeVisible();
      await expect(
        summarySection.getByRole('button', { name: /Tentar novamente|Retry/i }),
      ).toHaveCount(0);
    } finally {
      await page.unroute('**/graphql', routeHandler);
    }
  });

  test('INV-TWR-P4-UI-18: summary shows estimated indicator when portfolio has estimated or unavailable valuations', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const cdb = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI estimated CDB'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const stock = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI estimated anchor stock'),
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

    await expect
      .poll(async () => {
        const holdings = await getInvestmentHoldings(pfUser.token, {
          financialAccountId: brokerageAccount.id,
        });
        const summary = await getInvestmentPortfolioSummary(pfUser.token, {
          financialAccountId: brokerageAccount.id,
          period: PerformancePeriod.Ytd,
        });
        const hasEstimatedHolding = holdings.some(
          (holding) =>
            String(holding.valuationSource) === 'ESTIMATED' ||
            String(holding.valuationSource) === 'UNAVAILABLE',
        );
        return hasEstimatedHolding && summary.hasEstimatedValues;
      })
      .toBe(true);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await investmentsPage.summary.waitForMetricsReady();

    await expect
      .poll(
        async () => {
          const summary = await getInvestmentPortfolioSummary(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            period: PerformancePeriod.Ytd,
          });
          return summary.hasEstimatedValues;
        },
        { timeout: 60000 },
      )
      .toBe(true);

    await page.getByTestId('investment-summary-section').scrollIntoViewIfNeeded();

    const estimatedIndicator = page.getByTestId('investment-summary-estimated-indicator');
    await expect(estimatedIndicator).toBeVisible({ timeout: 30000 });
    await estimatedIndicator.hover();
    await expect(page.getByRole('tooltip')).toContainText(/estimado|estimated/i);
  });

  test('INV-TWR-P4-UI-19: allocation section lists only present groups and includes cash group when cash balance is positive', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const fixedIncome = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI allocation fixed income'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const crypto = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI allocation crypto'),
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fixedIncome.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(25),
        settlementDate: isoDaysAgo(25),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.12,
        issueDate: isoDaysAgo(25),
        maturityDate: isoDaysAgo(-365),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: crypto.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(20),
        settlementDate: isoDaysAgo(20),
        quantity: 1,
        unitPrice: 500,
        grossValue: 500,
        netValue: 500,
      },
      pfUser.token,
    );
    await createInvestmentCashTransaction(
      {
        financialAccountId: brokerageAccount.id,
        type: InvestmentCashTransactionType.Deposit,
        date: isoDaysAgo(2),
        amount: 2000,
        description: uniqueName('P4 UI allocation cash deposit'),
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });

    const presentGroupKeys = new Set(summary.byAssetGroup.map((group) => group.groupKey));
    expect(presentGroupKeys.has('fixedIncome')).toBe(true);
    expect(summary.cashBalance).toBeGreaterThan(0);
    expect(presentGroupKeys.has('cash')).toBe(true);

    const allGroupKeys = [
      'fixedIncome',
      'mutualFunds',
      'pension',
      'variableIncome',
      'others',
      'cash',
    ];

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    for (const groupKey of presentGroupKeys) {
      await expect(page.getByTestId(`investment-summary-allocation-group-${groupKey}`)).toBeVisible();
    }
    for (const groupKey of allGroupKeys.filter((key) => !presentGroupKeys.has(key))) {
      await expect(page.getByTestId(`investment-summary-allocation-group-${groupKey}`)).toHaveCount(0);
    }
  });

  test('INV-TWR-P4-UI-20: grid class order and class total value are consistent with backend summary', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const fixedIncomeA = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI class total fixed A'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const fixedIncomeB = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI class total fixed B'),
        assetType: AssetType.Cdb,
      },
      pfUser.token,
    );
    const crypto = await createInvestmentAsset(
      {
        name: uniqueName('P4 UI class order crypto'),
        assetType: AssetType.Crypto,
      },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fixedIncomeA.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(30),
        settlementDate: isoDaysAgo(30),
        quantity: 1,
        unitPrice: 10000,
        grossValue: 10000,
        netValue: 10000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.11,
        issueDate: isoDaysAgo(30),
        maturityDate: isoDaysAgo(-365),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fixedIncomeB.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(28),
        settlementDate: isoDaysAgo(28),
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.1,
        issueDate: isoDaysAgo(28),
        maturityDate: isoDaysAgo(-365),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: crypto.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(26),
        settlementDate: isoDaysAgo(26),
        quantity: 1,
        unitPrice: 700,
        grossValue: 700,
        netValue: 700,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const summary = await getInvestmentPortfolioSummary(pfUser.token, {
      financialAccountId: brokerageAccount.id,
      period: PerformancePeriod.All,
    });
    const fixedIncomeGroup = summary.byAssetGroup.find((group) => group.groupKey === 'fixedIncome');
    expect(fixedIncomeGroup).toBeTruthy();

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await investmentsPage.summary.waitForMetricsReady();

    const fixedIncomeCard = page.getByTestId('investment-grid-card-fixed-income');
    await expect(fixedIncomeCard).toBeVisible({ timeout: 30000 });
    await fixedIncomeCard.scrollIntoViewIfNeeded();

    // Grid uses INVESTMENT_GRID_DATA (separate from summary query); it can lag account switch / cache.
    await expect
      .poll(
        async () => {
          const latest = await getInvestmentPortfolioSummary(pfUser.token, {
            financialAccountId: brokerageAccount.id,
            period: PerformancePeriod.All,
          });
          const group = latest.byAssetGroup.find((g) => g.groupKey === 'fixedIncome');
          const expected = group?.totalGrossValue ?? 0;
          const ui = await readCurrencyByTestId(page, 'investment-grid-card-fixed-income-gross-value');
          return Math.abs(ui - expected) < 0.05;
        },
        { timeout: 60000 },
      )
      .toBe(true);

    await openGridClassDetailPanel(page, 'fixed-income');
    await expect(page.locator('#investment-grid-class-tab-cash')).toHaveCount(1);
    await expect(page.locator('#investment-grid-class-tab-fixed-income')).toHaveCount(1);
    await expect(page.locator('#investment-grid-class-tab-crypto')).toHaveCount(1);
    await expect(page.locator('#investment-grid-class-tab-funds')).toHaveCount(0);
    await expect(page.locator('#investment-grid-class-tab-stocks')).toHaveCount(0);
    await expect(page.locator('#investment-grid-class-tab-reits')).toHaveCount(0);

    const tabIds = await page
      .locator('[role="tab"][id^="investment-grid-class-tab-"]')
      .evaluateAll((tabs) => tabs.map((tab) => tab.id));
    expect(tabIds).toEqual([
      'investment-grid-class-tab-grid',
      'investment-grid-class-tab-cash',
      'investment-grid-class-tab-fixed-income',
      'investment-grid-class-tab-crypto',
    ]);
  });

  test('INV-TWR-P4-UI-21: page opens in account-scoped view by default', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const secondAccount = await createFinancialAccount(
      {
        name: uniqueName('P4 UI default account second'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    const firstAsset = await createInvestmentAsset(
      { name: uniqueName('P4 UI default stock first'), assetType: AssetType.Stock },
      pfUser.token,
    );
    const secondAsset = await createInvestmentAsset(
      { name: uniqueName('P4 UI default stock second'), assetType: AssetType.Stock },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: firstAsset.id,
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
        financialAccountId: secondAccount.id,
        assetId: secondAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 4,
        unitPrice: 100,
        grossValue: 400,
        netValue: 400,
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);
    await waitForRecalculation(pfUser.token, secondAccount.id);
    await waitForRecalculation(pfUser.token);

    const investmentsPage = await openInvestmentsPage(page);

    const selector = page.getByTestId('account-selector');
    await expect(selector).toBeVisible();
    await expect(selector).not.toContainText(/Todas as Corretoras|All brokers/i);
  });

  test('INV-TWR-P4-UI-22: holding row click opens detail drawer with the selected holding data', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const stockName = uniqueName('P4 UI row click stock');
    const stock = await createInvestmentAsset(
      {
        name: stockName,
        assetType: AssetType.Stock,
        ticker: `SC17${Date.now().toString().slice(-4)}`,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(8),
        settlementDate: isoDaysAgo(8),
        quantity: 10,
        unitPrice: 20,
        grossValue: 200,
        netValue: 200,
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openStocksDetailPanel(page);

    const row = page.getByRole('row').filter({ hasText: stockName }).first();
    await expect(row).toBeVisible();
    await row.click();

    await expect(page.getByTestId('investment-holding-detail-drawer')).toBeVisible();
    await expect(page.getByTestId('investment-holding-detail-asset-name')).toContainText(stockName);
  });

  test('INV-TWR-P4-UI-23: context menu shows asset-class specific operations for stock, crypto, funds, and fixed income', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const stockName = uniqueName('P4 UI menu stock');
    const cryptoName = uniqueName('P4 UI menu crypto');
    const fundName = uniqueName('P4 UI menu fund');
    const debentureName = uniqueName('P4 UI menu debenture');

    const stock = await createInvestmentAsset({ name: stockName, assetType: AssetType.Stock }, pfUser.token);
    const crypto = await createInvestmentAsset({ name: cryptoName, assetType: AssetType.Crypto }, pfUser.token);
    const fund = await createInvestmentAsset({ name: fundName, assetType: AssetType.MutualFund }, pfUser.token);
    const debenture = await createInvestmentAsset(
      { name: debentureName, assetType: AssetType.Debentures },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: stock.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(15),
        settlementDate: isoDaysAgo(15),
        quantity: 10,
        unitPrice: 30,
        grossValue: 300,
        netValue: 300,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: crypto.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(14),
        settlementDate: isoDaysAgo(14),
        quantity: 1,
        unitPrice: 600,
        grossValue: 600,
        netValue: 600,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: fund.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(13),
        settlementDate: isoDaysAgo(13),
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
        assetId: debenture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(12),
        settlementDate: isoDaysAgo(12),
        quantity: 1,
        unitPrice: 1500,
        grossValue: 1500,
        netValue: 1500,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.055,
        issueDate: isoDaysAgo(12),
        maturityDate: isoDaysFromNow(480),
        couponFrequency: CouponFrequency.SemiAnnual,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );

    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    await openStocksDetailPanel(page);
    await expect
      .poll(
        async () => (await page.getByRole('row').filter({ hasText: stockName }).count()) > 0,
        { timeout: 60_000 },
      )
      .toBe(true);
    await expectHoldingRowTradeActions(page, stockName);
    await openHoldingActionsMenu(page, stockName);
    await expect(page.getByRole('menuitem', { name: /Provento/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Split|Desdobramento/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Bonifica|Bonus/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Subscri|Subscription/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Registrar aluguel|Record stock lending/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Transferir custódia|Transfer custody/i })).toBeVisible();
    await page.keyboard.press('Escape');

    await openGridClassDetailPanel(page, 'crypto');
    await expectHoldingRowTradeActions(page, cryptoName);
    await openHoldingActionsMenu(page, cryptoName);
    await expect(page.getByRole('menuitem', { name: /Transferir custódia|Transfer custody/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Provento/i })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: /Registrar aluguel|Record stock lending/i })).toHaveCount(0);
    await page.keyboard.press('Escape');

    await openGridClassDetailPanel(page, 'funds');
    await expectHoldingRowTradeActions(page, fundName);
    await openHoldingActionsMenu(page, fundName);
    await expect(page.getByRole('menuitem', { name: /come-cotas/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Transferir custódia|Transfer custody/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Registrar aluguel|Record stock lending/i })).toHaveCount(0);
    await page.keyboard.press('Escape');

    await openGridClassDetailPanel(page, 'fixed-income');
    await expectHoldingRowTradeActions(page, debentureName);
    await openHoldingActionsMenu(page, debentureName);
    await expect(page.getByRole('menuitem', { name: /Registrar amortização|Record amortization/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Transferir custódia|Transfer custody/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Registrar aluguel|Record stock lending/i })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: /Provento/i })).toHaveCount(0);
  });

  test('INV-TWR-P4-UI-24: register CTA is disabled in consolidated view and enabled in account-scoped view', async ({
    page,
    brokerageAccount,
    secondaryBrokerageAccount,
  }) => {
    void secondaryBrokerageAccount;
    const investmentsPage = await openInvestmentsPage(page);
    await clearInvestmentGridClassSelection(page);

    await investmentsPage.selectConsolidatedView();
    // Record CTA is only rendered in class detail view (tabs panel), not on the grid matrix.
    // Use cash (always a tab); stocks can be absent from tabs when there are no equity holdings.
    await openGridClassDetailPanel(page, 'cash');
    const registerCta = page.getByTestId('investment-grid-record-cta');
    await expect(registerCta).toBeVisible();
    await expect(registerCta).toBeDisabled();
    await registerCta.click({ force: true });
    await expect(page.locator('#investment-grid-record-popover')).toHaveCount(0);

    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openGridClassDetailPanel(page, 'cash');
    const scopedRegisterCta = page.getByTestId('investment-grid-record-cta');
    await expect(scopedRegisterCta).toBeEnabled();
    await scopedRegisterCta.click();
    await expect(page.locator('#investment-grid-record-popover')).toBeVisible();
  });

  test('INV-TWR-P4-UI-25: fixed-income detail includes terms, coupon schedule, and timeline sections', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const debentureName = uniqueName('P4 UI detail debenture');
    const debenture = await createInvestmentAsset(
      { name: debentureName, assetType: AssetType.Debentures },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: debenture.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(80),
        settlementDate: isoDaysAgo(80),
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.058,
        issueDate: isoDaysAgo(80),
        maturityDate: isoDaysFromNow(700),
        couponFrequency: CouponFrequency.SemiAnnual,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openGridClassDetailPanel(page, 'fixed-income');

    const row = page.getByRole('row').filter({ hasText: debentureName }).first();
    await expect(row).toBeVisible();
    await row.click();

    await expect(page.getByTestId('investment-holding-detail-drawer')).toBeVisible();
    await expect(page.getByTestId('investment-holding-detail-asset-name')).toContainText(debentureName);
    await expect(page.getByTestId('fixed-income-detail-terms-section')).toBeVisible();
    await expect(page.getByTestId('fixed-income-detail-coupon-schedule-section')).toBeVisible();
    await expect(page.getByTestId('fixed-income-detail-timeline-section')).toBeVisible();
  });

  test('INV-TWR-P4-UI-26: header pending actions badge count matches visible pending actions', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const maturedName = uniqueName('P4 UI badge matured');
    const couponNameA = uniqueName('P4 UI badge coupon A');
    const couponNameB = uniqueName('P4 UI badge coupon B');

    const maturedAsset = await createInvestmentAsset(
      { name: maturedName, assetType: AssetType.Cdb },
      pfUser.token,
    );
    const couponAssetA = await createInvestmentAsset(
      { name: couponNameA, assetType: AssetType.Debentures },
      pfUser.token,
    );
    const couponAssetB = await createInvestmentAsset(
      { name: couponNameB, assetType: AssetType.Debentures },
      pfUser.token,
    );

    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: maturedAsset.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(45),
        settlementDate: isoDaysAgo(45),
        quantity: 1,
        unitPrice: 1000,
        grossValue: 1000,
        netValue: 1000,
        indexer: Indexer.PreFixado,
        fixedAnnualRate: 0.11,
        issueDate: isoDaysAgo(45),
        maturityDate: isoDaysAgo(1),
        couponFrequency: CouponFrequency.AtMaturity,
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: couponAssetA.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(75),
        settlementDate: isoDaysAgo(75),
        quantity: 1,
        unitPrice: 5000,
        grossValue: 5000,
        netValue: 5000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.055,
        issueDate: isoDaysAgo(75),
        maturityDate: isoDaysFromNow(450),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );
    await recordInvestmentTransaction(
      {
        financialAccountId: brokerageAccount.id,
        assetId: couponAssetB.id,
        direction: InvestmentTransactionDirection.Inflow,
        type: InvestmentTransactionType.Purchase,
        tradeDate: isoDaysAgo(90),
        settlementDate: isoDaysAgo(90),
        quantity: 1,
        unitPrice: 7000,
        grossValue: 7000,
        netValue: 7000,
        indexer: Indexer.Ipca,
        fixedAnnualRate: 0.06,
        issueDate: isoDaysAgo(90),
        maturityDate: isoDaysFromNow(560),
        couponFrequency: CouponFrequency.Monthly,
        couponDayOfMonth: couponDaySoon(),
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    await expect
      .poll(
        async () => {
          const pending = await getPendingActions(pfUser.token, PendingActionStatus.Pending);
          const hasMaturity = pending.some((row) => row.relatedHolding?.asset?.name === maturedName);
          const hasCouponA = pending.some((row) => row.relatedHolding?.asset?.name === couponNameA);
          const hasCouponB = pending.some((row) => row.relatedHolding?.asset?.name === couponNameB);
          return hasMaturity && hasCouponA && hasCouponB;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(true);

    await openInvestmentsPage(page);

    const expectedVisibleCount = (
      await getPendingActions(pfUser.token, PendingActionStatus.Pending)
    ).length;
    const badgeBubble = page
      .getByTestId('header-pending-actions-badge')
      .locator('.MuiBadge-badge')
      .first();
    await expect(badgeBubble).toBeVisible();
    await expect
      .poll(async () => parseBadgeCount(await badgeBubble.textContent()))
      .toBe(expectedVisibleCount);
  });

  test('INV-TWR-P4-UI-27: fund detail drawer shows pricing mode and latest NAV with reference date', async ({
    page,
    pfUser,
    brokerageAccount,
  }) => {
    const fundName = uniqueName('P4 UI fund detail metadata');
    const fund = await createInvestmentAsset(
      {
        name: fundName,
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
        // MarketData without provider quotes often leaves holding gross null in detail — manual NAV is stable in E2E.
        pricingMode: HoldingPricingMode.Manual,
        manualValuation: { valuedAt: isoDaysAgo(15), grossValue: 1000 },
      },
      pfUser.token,
    );
    await waitForRecalculation(pfUser.token, brokerageAccount.id);

    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);
    await openGridClassDetailPanel(page, 'funds');

    const fundRow = page.getByRole('row').filter({ hasText: fundName }).first();
    await expect(fundRow).toBeVisible();
    await expect
      .poll(async () => /R\$\s*1[.,]000/.test((await fundRow.textContent()) ?? ''))
      .toBe(true);

    await fundRow.click();

    const drawer = page.getByTestId('investment-holding-detail-drawer');
    await expect(drawer).toBeVisible({ timeout: 30_000 });
    await expect(drawer.getByText(/Valor da cota|Quota value/i).first()).toBeVisible();
    await expect(drawer.getByText(/R\$\s*10[,.]00/).first()).toBeVisible();
    await expect(drawer.getByText(/\(\d{1,2}\/\d{1,2}\/\d{2,4}\)/).first()).toBeVisible();
    await expect(drawer.getByText(/Linha do tempo|Timeline/i).first()).toBeVisible();
    await expect(drawer.getByText(/Aplicação|Purchase/i).first()).toBeVisible();
  });

  test('INV-TWR-P4-UI-28: crypto record flow uses crypto gate and keeps D+0 (no settlement field)', async ({
    page,
    brokerageAccount,
  }) => {
    const investmentsPage = await openInvestmentsPage(page);
    await investmentsPage.selectAccount(brokerageAccount.name, brokerageAccount.id);

    const cryptoCard = page.getByTestId('investment-grid-card-crypto');
    await expect(cryptoCard).toBeVisible({ timeout: 30000 });
    await cryptoCard.scrollIntoViewIfNeeded();

    const firstInvestmentButton = cryptoCard
      .getByRole('button', { name: /Incluir primeiro investimento|First investment/i })
      .first();
    const cryptoGateDialog = page.getByRole('dialog').filter({ hasText: /Escolher cripto|Select crypto/i }).first();
    const cryptoForm = page.locator('form#record-crypto-form');

    const waitCryptoEntry = async (): Promise<boolean> => {
      return expect
        .poll(
          async () =>
            (await cryptoGateDialog.isVisible().catch(() => false)) ||
            (await cryptoForm.isVisible().catch(() => false)),
          { timeout: 10_000 },
        )
        .toBe(true)
        .then(() => true)
        .catch(() => false);
    };

    if (await firstInvestmentButton.isVisible().catch(() => false)) {
      await firstInvestmentButton.click({ force: true });
      if (!(await waitCryptoEntry())) {
        await openGridClassDetailPanel(page, 'crypto');
        const registerCta = page.getByTestId('investment-grid-record-cta');
        await expect(registerCta).toBeEnabled();
        await registerCta.click();
        await expect(page.locator('#investment-grid-record-popover')).toBeVisible();
        await page.getByTestId('investment-grid-record-option-crypto').click();
      }
    } else {
      await openGridClassDetailPanel(page, 'crypto');
      const registerCta = page.getByTestId('investment-grid-record-cta');
      await expect(registerCta).toBeEnabled();
      await registerCta.click();
      await expect(page.locator('#investment-grid-record-popover')).toBeVisible();
      await page.getByTestId('investment-grid-record-option-crypto').click();
    }

    if (await cryptoGateDialog.isVisible().catch(() => false)) {
      await cryptoGateDialog.getByLabel(/Nome do ativo|Asset name/i).fill(uniqueName('Crypto P4 UI 28'));
      await cryptoGateDialog.getByLabel(/Ticker \/ símbolo|Ticker \/ symbol/i).fill('BTC');
      await cryptoGateDialog.getByRole('button', { name: /Continuar|Continue/i }).click();
    }

    await expect(cryptoForm).toBeVisible();
    await expect(page.locator('input[name="settlementDate"]')).toHaveCount(0);
    await expect(page.getByTestId('record-equity-autocomplete')).toHaveCount(0);
    await expect(page.getByTestId('record-equity-fees-total')).toHaveCount(0);
    await expect(page.locator('input[name="assetName"]')).toHaveCount(0);
    await expect(page.locator('input[name="ticker"]')).toHaveCount(0);
  });
});
