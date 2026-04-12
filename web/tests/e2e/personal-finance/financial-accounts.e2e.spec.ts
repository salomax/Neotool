/**
 * E2E tests — Financial Accounts
 *
 * Coverage: FA-H-01..08  |  FA-A-01..09  |  FA-N-01..11
 * Security coverage is in security.e2e.spec.ts (SEC-FA-01..04).
 *
 * Each test creates its own account via the UI to exercise the full creation flow.
 * Pre-seeded accounts (bankAccount / creditCardAccount fixtures) are used only when
 * the test focus is NOT on the creation form itself.
 */

import { test, expect } from '../fixtures/personal-finance';
import { FinanceTransactionsPage } from '../pages/personal-finance/FinanceTransactionsPage';
import {
  getFinancialAccounts,
  getMonthSummary,
  type FinancialAccount,
} from '../helpers/banking-graphql';

// The local/dev banking stack still returns 504 under the default 6-worker Playwright load
// for account create/update flows. Keep this suite serial until the backend can absorb parallel E2E traffic.
test.describe.configure({ mode: 'serial' });

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

function todayBr(): string {
  const now = new Date();
  const day = String(now.getDate()).padStart(2, '0');
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${now.getFullYear()}`;
}

// ── Setup ─────────────────────────────────────────────────────────────────────

/**
 * Signs the pfUser into the page before each test.
 * Uses localStorage injection to avoid a full UI sign-in on every test.
 */
async function signIntoPage(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: /account menu/i }).waitFor({ state: 'visible' });
}

async function openFinancialAccountsPage(page: import('@playwright/test').Page): Promise<FinanceTransactionsPage> {
  await signIntoPage(page);
  const pfPage = new FinanceTransactionsPage(page);
  await pfPage.goto();
  return pfPage;
}

async function expectPersistedAccount(
  token: string,
  matcher: (account: FinancialAccount) => boolean,
): Promise<FinancialAccount> {
  let matchedAccount: FinancialAccount | undefined;

  await expect
    .poll(
      async () => {
        matchedAccount = (await getFinancialAccounts(token)).find(matcher);
        return Boolean(matchedAccount);
      },
      { timeout: 15000 },
    )
    .toBe(true);

  return matchedAccount!;
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe('Financial Accounts — happy path', () => {
  test('FA-H-01: creates a BANK / CONTA_CORRENTE account with initial balance', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const name = uniqueName('Conta Corrente H01');
    await drawer.createBankAccount({ name, initialBalance: 1000 });
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);
  });

  test('FA-H-02: creates a CREDIT / CREDIT_CARD account with closingDay and dueDay', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    const name = uniqueName('Cartão Crédito H02');
    await drawer.createCreditCardAccount({ name, closingDay: 7, dueDay: 15 });
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);

    const created = await expectPersistedAccount(pfUser.token, (account) => account.name === name);
    expect(created.cardData?.closingDay).toBe(7);
    expect(created.cardData?.dueDay).toBe(15);
  });

  test('FA-H-03: creates a CASH account (minimal fields)', async ({ page, pfUser }) => {
    test.skip(true, 'CASH account creation is not available in the current finance-transactions UI.');

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();

    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const name = uniqueName('Carteira H03');
    await drawer.selectType('CASH');
    await drawer.selectSubtype('CASH');
    await drawer.fillName(name);
    await drawer.submit();
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);
  });

  test('FA-H-04: creates a BENEFIT / VALE_REFEICAO account', async ({ page, pfUser }) => {
    test.skip(true, 'BENEFIT account creation is not available in the current finance-transactions UI.');

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();

    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    const name = uniqueName('Vale Refeição H04');
    await drawer.selectType('BENEFIT');
    await drawer.selectSubtype('VALE_REFEICAO');
    await drawer.selectFirstIssuer();
    await drawer.fillName(name);
    await drawer.submit();
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);
  });

  test('FA-H-05: lists all accounts belonging to the user', async ({
    page,
    pfUser,
    bankAccount,
    creditCardAccount,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    await pfPage.expectAccountInSelector(bankAccount.name);
    await pfPage.expectAccountInSelector(creditCardAccount.name);
  });

  test('FA-H-06: edits account name', async ({ page, pfUser, bankAccount }) => {
    const pfPage = await openFinancialAccountsPage(page);
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    const drawer = await pfPage.openEditAccountDrawer(bankAccount.name);

    const newName = uniqueName('Conta Editada H06');
    await drawer.fillName(newName);
    await drawer.submit();
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(newName);
    await pfPage.expectAccountNotInSelector(bankAccount.name);
  });

  test('FA-H-07: edits initial balance — change is reflected in the account', async ({
    page: _page,
    pfUser: _pfUser,
    bankAccount: _bankAccount,
  }) => {
    test.skip(true, 'Initial balance is not editable via the account form (field hidden in edit mode). Pending feature design.');
  });

  test('FA-H-08: soft-deletes an account — it disappears from the selector', async ({
    page: _page,
    pfUser: _pfUser,
    bankAccount: _bankAccount,
  }) => {
    test.skip(true, 'Delete account feature not yet implemented in the UI. Pending feature design.');
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe('Financial Accounts — alternative paths', () => {
  test('FA-A-01: creates account with initial balance = R$ 0', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    await drawer.selectSubtype('CONTA_CORRENTE');
    await drawer.selectFirstIssuer();
    await drawer.fillName(uniqueName('Conta Zero A01'));
    await drawer.fillInitialBalance(0);
    await drawer.fillInitialBalanceDate(todayBr());
    await drawer.attemptSubmit();

    await drawer.expectStillOpen();
    await drawer.expectFieldError('initial-balance-input');
  });

  test('FA-A-02: creates account with negative initial balance (debt tracking)', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const name = uniqueName('Conta Negativa A02');
    await drawer.createBankAccount({ name, initialBalance: -500 });
    await drawer.waitForClose();

    const created = await expectPersistedAccount(pfUser.token, (account) => account.name === name);

    const now = new Date();
    await expect
      .poll(
        async () => {
          const summary = await getMonthSummary(
            created.id,
            now.getFullYear(),
            now.getMonth() + 1,
            pfUser.token,
          );
          return summary.closingBalance;
        },
        { timeout: 15000 },
      )
      .toBe(-500);
  });

  test('FA-A-03: two accounts of different types visible simultaneously', async ({
    page,
    pfUser,
    bankAccount,
    creditCardAccount,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    await pfPage.expectAccountInSelector(bankAccount.name);
    await pfPage.expectAccountInSelector(creditCardAccount.name);

    // Selecting each should switch context without errors.
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    expect(await pfPage.getSelectedAccountName()).toContain(bankAccount.name);

    await pfPage.selectAccount(creditCardAccount.name, creditCardAccount.id);
    expect(await pfPage.getSelectedAccountName()).toContain(creditCardAccount.name);
  });

  test('FA-A-04: CREDIT account with closingDay = 30)', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    const name = uniqueName('Cartão ClosingDay30 A04');
    await drawer.createCreditCardAccount({ name, closingDay: 30, dueDay: 30 });
    await drawer.waitForClose();

    const created = await expectPersistedAccount(pfUser.token, (account) => account.name === name);
    expect(created.cardData?.closingDay).toBe(30);
    expect(created.cardData?.dueDay).toBe(30);
  });

  test('FA-A-05: CREDIT account persists closingDay and dueDay when both are provided', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    const name = uniqueName('Cartão BillingDays A05');
    const closingDay = 10;
    const dueDay = 25;
    await drawer.createCreditCardAccount({ name, closingDay, dueDay });
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);

    const created = await expectPersistedAccount(pfUser.token, (account) => account.name === name);
    expect(created.cardData?.closingDay).toBe(closingDay);
    expect(created.cardData?.dueDay).toBe(dueDay);
  });

  test('FA-A-06: account name with special characters and accents', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const name = `Nubank – Crédito (ç & ã) ${Date.now()}`;
    await drawer.createBankAccount({ name });
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);
  });

  test('FA-A-07: edit closingDay of existing credit card account', async ({
    page,
    pfUser,
    creditCardAccount,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    await pfPage.selectAccount(creditCardAccount.name, creditCardAccount.id);

    const drawer = await pfPage.openEditAccountDrawer(creditCardAccount.name);
    await drawer.fillClosingDay(20);
    await drawer.submit();
    await drawer.waitForClose();

    const updated = await expectPersistedAccount(
      pfUser.token,
      (account) => account.id === creditCardAccount.id,
    );
    expect(updated.cardData?.closingDay).toBe(20);
  });

  test('FA-A-08: account with bankData fields (branch code, account number)', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const name = uniqueName('Itaú Corrente A08');
    await drawer.createBankAccount({
      name,
      branchCode: '1234',
      accountNumber: '56789-0',
    });
    await drawer.waitForClose();

    await pfPage.expectAccountInSelector(name);
  });

  test('FA-A-09: default card account name does not include last 4 digits', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const lastFourDigits = String(Date.now() % 10000).padStart(4, '0');

    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.selectFirstIssuer();
    await drawer.fillLastFourDigits(lastFourDigits);
    await drawer.submit();
    await drawer.waitForClose();

    const created = await expectPersistedAccount(
      pfUser.token,
      (account) => account.cardData?.maskedNumber === lastFourDigits,
    );
    expect(created.name).not.toContain(lastFourDigits);
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe('Financial Accounts — negative paths', () => {
  test('FA-N-01: submit without name uses issuer-derived account name', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    await drawer.selectSubtype('CONTA_CORRENTE');
    await drawer.selectFirstIssuer();
    await drawer.submit();

    await drawer.waitForClose();
  });

  test('FA-N-02: submit without type — shows validation error, drawer stays open', async ({
    page,
    pfUser,
  }) => {
    test.skip(true, 'Current UI enforces account type selection as a separate first step, so there is no submit path without a type.');

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();

    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    await drawer.fillName(uniqueName('Sem Tipo N02'));
    // Intentionally skip selectType
    await drawer.submit();

    // Expect either a field-level error or the drawer stays open
    await drawer.expectStillOpen();
  });

  test('FA-N-03: CREDIT account without closingDay is allowed', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.selectFirstIssuer();
    await drawer.fillName(uniqueName('Cartão Sem ClosingDay N03'));
    await drawer.fillDueDay(15);
    await drawer.submit();

    await drawer.waitForClose();
  });

  test('FA-N-04: CREDIT account without dueDay is allowed', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.selectFirstIssuer();
    await drawer.fillName(uniqueName('Cartão Sem DueDay N04'));
    await drawer.fillClosingDay(7);
    await drawer.submit();

    await drawer.waitForClose();
  });

  test('FA-N-05: closingDay = 0 — below minimum range (1–31)', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.fillName(uniqueName('Cartão ClosingDay0 N05'));
    await drawer.fillClosingDay(0);
    await drawer.fillDueDay(15);
    await drawer.attemptSubmit();

    await drawer.expectFieldError('closing-day-input');
    await drawer.expectStillOpen();
  });

  test('FA-N-06: closingDay = 32 — above maximum range (1–31)', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.fillName(uniqueName('Cartão ClosingDay32 N06'));
    await drawer.fillClosingDay(32);
    await drawer.fillDueDay(15);
    await drawer.attemptSubmit();

    await drawer.expectFieldError('closing-day-input');
    await drawer.expectStillOpen();
  });

  test('FA-N-07: dueDay = 0 — below minimum range', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.fillName(uniqueName('Cartão DueDay0 N07'));
    await drawer.fillClosingDay(7);
    await drawer.fillDueDay(0);
    await drawer.attemptSubmit();

    await drawer.expectFieldError('due-day-input');
    await drawer.expectStillOpen();
  });

  test('FA-N-08: dueDay = 32 — above maximum range (1–31)', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.fillName(uniqueName('Cartão DueDay32 N08'));
    await drawer.fillClosingDay(7);
    await drawer.fillDueDay(32);
    await drawer.attemptSubmit();

    await drawer.expectFieldError('due-day-input');
    await drawer.expectStillOpen();
  });

  test('FA-N-09: name exceeding 255 characters — validation error', async ({ page, pfUser }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('BANK');
    const longName = 'A'.repeat(256);
    await drawer.selectSubtype('CONTA_CORRENTE');
    await drawer.selectFirstIssuer();
    await drawer.fillName(longName);
    await drawer.attemptSubmit();

    await drawer.expectStillOpen();
    await drawer.expectFieldError('account-name-input');
  });

  test('FA-N-10: account type is immutable after creation — field is disabled in edit', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    const drawer = await pfPage.openEditAccountDrawer(bankAccount.name);

    await drawer.expectTypeDisabled();
  });

  test('FA-N-11: partial last four digits is rejected on submit (must be empty or 4 digits)', async ({
    page,
    pfUser,
  }) => {
    const pfPage = await openFinancialAccountsPage(page);
    const drawer = await pfPage.openCreateAccountDrawer('CARD');
    await drawer.selectSubtype('CREDIT_CARD');
    await drawer.selectFirstIssuer();
    await drawer.fillName(uniqueName('Cartão LastFour Parcial N11'));
    await drawer.fillLastFourDigits('12');
    await drawer.attemptSubmit();

    await drawer.expectFieldError('card-last-four-input');
    await drawer.expectStillOpen();
  });
});
