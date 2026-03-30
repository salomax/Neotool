/**
 * Personal Finance fixtures for E2E tests.
 *
 * Design decisions:
 * - pfUser is worker-scoped (created once per worker) to avoid N sign-ups per test.
 * - bankAccount / creditCardAccount / transactions are test-scoped (created per test).
 * - No DELETE_FINANCIAL_ACCOUNT mutation exists — test data accumulates in the test
 *   environment. Accounts are named with unique timestamps to avoid collisions.
 * - twoTenants creates two isolated users for cross-tenant security tests.
 */

/* eslint-disable react-hooks/rules-of-hooks */
import { test as base, Browser, BrowserContext } from '@playwright/test';
import { signIn, createUser } from '../helpers/graphql';
import {
  createFinancialAccount,
  createTransaction,
  deleteTransaction,
  FinancialAccount,
  Transaction,
} from '../helpers/banking-graphql';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface PfUser {
  id: string;
  email: string;
  password: string;
  token: string;
}

export interface TwoTenants {
  userA: PfUser;
  userB: PfUser;
  ctxA: BrowserContext;
  ctxB: BrowserContext;
}

export interface PfFixtures {
  /** A unique user with a valid JWT token, scoped per worker for speed. */
  pfUser: PfUser;
  /** A BANK/CONTA_CORRENTE account with R$ 1.000 initial balance. */
  bankAccount: FinancialAccount;
  /** A CREDIT/CREDIT_CARD account (closingDay=7, dueDay=15). */
  creditCardAccount: FinancialAccount;
  /** A POSTED DEBIT transaction in bankAccount. */
  postedExpense: Transaction;
  /** A POSTED CREDIT transaction in bankAccount. */
  postedIncome: Transaction;
  /** Two isolated browser contexts signed in as different users (for security tests). */
  twoTenants: TwoTenants;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function uniqueEmail(): string {
  return `pf-test-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@test.neotool.com`;
}

function todayIso(): string {
  return new Date().toISOString().split('T')[0];
}

async function createPfUser(): Promise<PfUser> {
  const email = uniqueEmail();
  const password = 'TestPassword123!';
  const user = await createUser(email, password, 'PF Test User');
  const token = await signIn(email, password);
  return { id: user.id, email, password, token };
}

/**
 * Signs a user into a browser context by injecting the JWT token into localStorage.
 * This avoids a full UI sign-in flow and is safe because auth.ts reads auth_token
 * from localStorage.
 */
async function injectAuthToken(ctx: BrowserContext, token: string): Promise<void> {
  await ctx.addInitScript((t) => {
    localStorage.setItem('auth_token', t);
  }, token);
}

// ── Fixture definitions ───────────────────────────────────────────────────────

export const test = base.extend<PfFixtures>({
  /**
   * One unique user per worker. Worker scope avoids creating N users for N tests
   * in the same worker process.
   */
  pfUser: [
    async (_args, use) => {
      const user = await createPfUser();
      await use(user);
    },
    { scope: 'worker' },
  ],

  /**
   * BANK / CONTA_CORRENTE account with R$ 1.000 initial balance.
   * Created fresh per test so tests don't share transaction state.
   */
  bankAccount: async ({ pfUser }, use) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Conta Corrente E2E'),
        type: 'BANK',
        subtype: 'CONTA_CORRENTE',
        initialBalance: 1000.0,
      },
      pfUser.token
    );
    await use(account);
    // Note: no DELETE_FINANCIAL_ACCOUNT mutation — data is left in test environment.
  },

  /**
   * CREDIT / CREDIT_CARD account with closingDay=7 and dueDay=15.
   */
  creditCardAccount: async ({ pfUser }, use) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Cartão Crédito E2E'),
        type: 'CREDIT',
        subtype: 'CREDIT_CARD',
        initialBalance: 0,
        closingDay: 7,
        dueDay: 15,
      },
      pfUser.token
    );
    await use(account);
  },

  /**
   * A POSTED DEBIT transaction (expense) of R$ 100 in bankAccount.
   * Deleted after the test to keep account state clean.
   */
  postedExpense: async ({ pfUser, bankAccount }, use) => {
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: uniqueName('Despesa E2E'),
        amount: 100.0,
        creditDebitType: 'DEBIT',
        type: 'EXPENSE',
        status: 'POSTED',
        date: todayIso(),
      },
      pfUser.token
    );
    await use(tx);
    await deleteTransaction(tx.id, pfUser.token).catch(() => {
      // Ignore teardown errors — the transaction may have already been deleted by the test.
    });
  },

  /**
   * A POSTED CREDIT transaction (income) of R$ 500 in bankAccount.
   * Deleted after the test to keep account state clean.
   */
  postedIncome: async ({ pfUser, bankAccount }, use) => {
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: uniqueName('Receita E2E'),
        amount: 500.0,
        creditDebitType: 'CREDIT',
        type: 'SALARY',
        status: 'POSTED',
        date: todayIso(),
      },
      pfUser.token
    );
    await use(tx);
    await deleteTransaction(tx.id, pfUser.token).catch(() => {});
  },

  /**
   * Two isolated browser contexts signed in as different users.
   * Used exclusively for cross-tenant security tests.
   *
   * userA creates a financial account; userB attempts to access/modify it.
   */
  twoTenants: async ({ browser }: { browser: Browser }, use) => {
    const [userA, userB] = await Promise.all([createPfUser(), createPfUser()]);

    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();

    await Promise.all([
      injectAuthToken(ctxA, userA.token),
      injectAuthToken(ctxB, userB.token),
    ]);

    const tenants: TwoTenants = { userA, userB, ctxA, ctxB };
    await use(tenants);

    await Promise.all([ctxA.close(), ctxB.close()]);
  },
});

export { expect } from '@playwright/test';
