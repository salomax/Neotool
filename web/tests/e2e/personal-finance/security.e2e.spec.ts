/**
 * E2E tests — Security (Cross-Tenant Isolation)
 *
 * Coverage: SEC-FA-01..04 | SEC-TX-01..05 | SEC-MS-01..02 |
 *           SEC-CB-01..03 | SEC-IM-01..02 | SEC-IDOR-01..02 | SEC-TOK-01..04
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Context:
 *   The system is multi-tenant. Every entity is scoped by account_id.
 *   A user MUST only read and mutate their own data.
 *
 * Test model:
 *   - twoTenants fixture: two isolated users (userA, userB) in separate
 *     browser contexts, each with their own JWT tokens.
 *   - userA creates data; userB attempts to access/modify it.
 *   - All GraphQL calls go through executeGraphQL() with the target user's token.
 *
 * Expected responses for cross-tenant violations:
 *   - Queries: null or empty list (never returns another tenant's data)
 *   - Mutations: GraphQL error "not found" or "forbidden" (never silently succeeds)
 *   - The exact error message may vary; we match on /not found|forbidden|unauthorized/i
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from '../fixtures/personal-finance';
import {
  createFinancialAccount,
  createTransaction,
  deleteTransaction,
  getFinancialAccounts,
  getCreditCardBills,
  getMonthSummary,
  today,
} from '../helpers/banking-graphql';
import { executeGraphQL } from '../helpers/graphql';

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

const NOT_FOUND = /not found|não encontrad|forbidden|unauthorized/i;

// =============================================================================
// SEC-FA — Financial Accounts
// =============================================================================

test.describe('Security — Financial Accounts', () => {
  test('SEC-FA-01: user B cannot read user A\'s financial account by ID', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta Segura FA01 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 1000 },
      userA.token
    );

    const result = await executeGraphQL(`
      query GetFinancialAccount($id: ID!) {
        financialAccount(id: $id) { id name }
      }
    `, { id: accountA.id }, userB.token);

    // Must return null or error — NEVER userA's account data
    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      expect(result.data?.financialAccount).toBeNull();
    }
  });

  test('SEC-FA-02: user B\'s financialAccounts query returns only their own accounts', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta FA02-A ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    const accountsB = await getFinancialAccounts(userB.token);
    const accountIdsBofB = new Set(accountsB.map((a) => a.id));

    // userA's account must not appear in userB's list
    expect(accountIdsBofB.has(accountA.id)).toBe(false);
  });

  test('SEC-FA-03: user B cannot update user A\'s account', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta FA03 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    const result = await executeGraphQL(`
      mutation UpdateFinancialAccount($id: ID!, $input: UpdateFinancialAccountInput!) {
        updateFinancialAccount(id: $id, input: $input) { id name }
      }
    `, { id: accountA.id, input: { name: 'Hacked by B' } }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);

    // Verify the name was NOT changed
    const accountsA = await getFinancialAccounts(userA.token);
    const stillIntact = accountsA.find((a) => a.id === accountA.id);
    expect(stillIntact?.name).not.toBe('Hacked by B');
  });

  test('SEC-FA-04: user B cannot delete user A\'s account', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta FA04 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    // Try delete mutation if it exists; otherwise try update with a deletion flag
    const result = await executeGraphQL(`
      mutation DeleteFinancialAccount($id: ID!) {
        deleteFinancialAccount(id: $id)
      }
    `, { id: accountA.id }, userB.token);

    // Must be an error — account must still exist for userA
    expect(result.errors).toBeDefined();

    const accountsA = await getFinancialAccounts(userA.token);
    expect(accountsA.some((a) => a.id === accountA.id)).toBe(true);
  });
});

// =============================================================================
// SEC-TX — Transactions
// =============================================================================

test.describe('Security — Transactions', () => {
  test('SEC-TX-01: user B cannot read user A\'s transactions', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta TX01 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: accountA.id, description: uniqueDesc('Tx Secreta TX01'), amount: 9999, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    // userB queries transactions of userA's account
    const result = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id description amount } }
        }
      }
    `, { financialAccountId: accountA.id, first: 50 }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      const edges = result.data?.transactions?.edges ?? [];
      const found = edges.some((e: any) => e.node.id === tx.id);
      expect(found).toBe(false);
    }

    await deleteTransaction(tx.id, userA.token);
  });

  test('SEC-TX-02: user B cannot create a transaction in user A\'s account', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta TX02 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    const result = await executeGraphQL(`
      mutation CreateTransaction($input: CreateTransactionInput!) {
        createTransaction(input: $input) {
          transaction { id financialAccountId }
        }
      }
    `, {
      input: {
        financialAccountId: accountA.id,
        description: 'Invasão TX02',
        amount: 1,
        creditDebitType: 'DEBIT',
        type: 'EXPENSE',
        status: 'POSTED',
        date: today(),
      },
    }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);

    // Verify userA's account was not touched
    const result2 = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id description } }
        }
      }
    `, { financialAccountId: accountA.id, first: 50 }, userA.token);

    const edges = result2.data?.transactions?.edges ?? [];
    expect(edges.some((e: any) => e.node.description === 'Invasão TX02')).toBe(false);
  });

  test('SEC-TX-03: user B cannot update user A\'s transaction', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta TX03 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: accountA.id, description: 'Original TX03', amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    const result = await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id description }
      }
    `, { id: tx.id, input: { description: 'Hacked by B' } }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);

    // Verify transaction was NOT changed
    const txsA = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id description } }
        }
      }
    `, { financialAccountId: accountA.id, first: 50 }, userA.token);

    const original = (txsA.data?.transactions?.edges ?? [])
      .find((e: any) => e.node.id === tx.id);
    expect(original?.node?.description).toBe('Original TX03');

    await deleteTransaction(tx.id, userA.token);
  });

  test('SEC-TX-04: user B cannot delete user A\'s transaction', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta TX04 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: accountA.id, description: uniqueDesc('Tx TX04'), amount: 50, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    const result = await executeGraphQL(`
      mutation DeleteTransaction($id: ID!) { deleteTransaction(id: $id) }
    `, { id: tx.id }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);

    // Transaction still exists for userA
    const txsA = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id } }
        }
      }
    `, { financialAccountId: accountA.id, first: 50 }, userA.token);

    const stillExists = (txsA.data?.transactions?.edges ?? [])
      .some((e: any) => e.node.id === tx.id);
    expect(stillExists).toBe(true);

    await deleteTransaction(tx.id, userA.token);
  });

  test('SEC-TX-05: transactions query filtered by another tenant\'s accountId returns empty', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta TX05 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: accountA.id, description: uniqueDesc('Private TX05'), amount: 777, creditDebitType: 'CREDIT', status: 'POSTED', date: today() },
      userA.token
    );

    // userB passes userA's accountId in their query — must get nothing
    const result = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id amount } }
        }
      }
    `, { financialAccountId: accountA.id, first: 50 }, userB.token);

    if (!result.errors) {
      const edges = result.data?.transactions?.edges ?? [];
      expect(edges.length).toBe(0);
    }

    await deleteTransaction(tx.id, userA.token);
  });
});

// =============================================================================
// SEC-MS — Monthly Summaries
// =============================================================================

test.describe('Security — Monthly Summaries', () => {
  test('SEC-MS-01: user B cannot read user A\'s monthly summary', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta MS01 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 5000 },
      userA.token
    );

    const result = await executeGraphQL(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          closingBalance totalIncome totalExpenses
        }
      }
    `, { financialAccountId: accountA.id, year: 2026, month: 3 }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      // If no error, the returned data must NOT reveal userA's balance
      expect(result.data?.monthSummary?.closingBalance).not.toBe(5000);
    }
  });

  test('SEC-MS-02: user B creating transaction in A\'s account does not affect A\'s summary', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta MS02 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 1000 },
      userA.token
    );

    const now = new Date();
    const summaryBefore = await getMonthSummary(accountA.id, now.getFullYear(), now.getMonth() + 1, userA.token);

    // userB tries to create a transaction in userA's account (must fail — see SEC-TX-02)
    await executeGraphQL(`
      mutation CreateTransaction($input: CreateTransactionInput!) {
        createTransaction(input: $input) { transaction { id } }
      }
    `, {
      input: {
        financialAccountId: accountA.id,
        description: 'Pollution attempt',
        amount: 99999,
        creditDebitType: 'DEBIT',
        type: 'EXPENSE',
        status: 'POSTED',
        date: today(),
      },
    }, userB.token);

    const summaryAfter = await getMonthSummary(accountA.id, now.getFullYear(), now.getMonth() + 1, userA.token);

    // userA's summary must be completely unchanged
    expect(summaryAfter.closingBalance).toBeCloseTo(summaryBefore.closingBalance, 2);
    expect(summaryAfter.totalExpenses).toBeCloseTo(summaryBefore.totalExpenses, 2);
  });
});

// =============================================================================
// SEC-CB — Credit Card Bills
// =============================================================================

test.describe('Security — Credit Card Bills', () => {
  test('SEC-CB-01: user B cannot read user A\'s credit card bills', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const cardA = await createFinancialAccount(
      { name: `Cartão CB01 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', initialBalance: 0, closingDay: 7, dueDay: 15 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: cardA.id, description: uniqueDesc('Compra CB01'), amount: 200, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    // userB attempts to query userA's bills
    const result = await executeGraphQL(`
      query GetCreditCardBills($financialAccountId: ID!) {
        creditCardBills(financialAccountId: $financialAccountId) {
          id totalAmount status
        }
      }
    `, { financialAccountId: cardA.id }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      expect(result.data?.creditCardBills ?? []).toHaveLength(0);
    }

    await deleteTransaction(tx.id, userA.token);
  });

  test('SEC-CB-02: user B cannot read a specific bill of user A', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const cardA = await createFinancialAccount(
      { name: `Cartão CB02 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', initialBalance: 0, closingDay: 7, dueDay: 15 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: cardA.id, description: uniqueDesc('Compra CB02'), amount: 150, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    const bills = await getCreditCardBills(cardA.id, userA.token);
    const bill = bills[0];

    const result = await executeGraphQL(`
      query GetCreditCardBill($financialAccountId: ID!, $closingDate: Date) {
        creditCardBill(financialAccountId: $financialAccountId, closingDate: $closingDate) {
          id totalAmount
        }
      }
    `, { financialAccountId: cardA.id, closingDate: bill?.closingDate }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      expect(result.data?.creditCardBill).toBeNull();
    }

    await deleteTransaction(tx.id, userA.token);
  });

  test('SEC-CB-03: user B cannot pay user A\'s bill', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const cardA = await createFinancialAccount(
      { name: `Cartão CB03 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', initialBalance: 0, closingDay: 7, dueDay: 15 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: cardA.id, description: uniqueDesc('Compra CB03'), amount: 300, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    const bills = await getCreditCardBills(cardA.id, userA.token);
    const billId = bills[0]?.id;

    const result = await executeGraphQL(`
      mutation PayBill($billId: ID!, $amount: Float!) {
        payBill(billId: $billId, amount: $amount) { id status }
      }
    `, { billId, amount: 300 }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);

    await deleteTransaction(tx.id, userA.token);
  });
});

// =============================================================================
// SEC-IM — Transaction Import
// =============================================================================

test.describe('Security — Transaction Import', () => {
  test('SEC-IM-01: user B cannot import transactions into user A\'s account', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta IM01 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    const result = await executeGraphQL(`
      mutation ImportTransactionsFile($input: ImportTransactionsFileInput!) {
        importTransactionsFile(input: $input) {
          import { id status importedCount }
        }
      }
    `, {
      input: {
        financialAccountId: accountA.id,
        fileName: 'inject.csv',
        fileContent: btoa('Data,Descricao,Valor\n2026-03-01,Injecao,-1'),
      },
    }, userB.token);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(NOT_FOUND);
  });

  test('SEC-IM-02: user B cannot list user A\'s import jobs', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta IM02 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    const result = await executeGraphQL(`
      query TransactionImports($financialAccountId: ID) {
        transactionImports(financialAccountId: $financialAccountId) {
          id status
        }
      }
    `, { financialAccountId: accountA.id }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      expect(result.data?.transactionImports ?? []).toHaveLength(0);
    }
  });
});

// =============================================================================
// SEC-IDOR — Insecure Direct Object Reference
// =============================================================================

test.describe('Security — IDOR (ID Enumeration)', () => {
  test('SEC-IDOR-01: querying a valid UUID that belongs to another tenant returns null/error', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    // userA creates an account — the ID is a valid UUID in the system
    const accountA = await createFinancialAccount(
      { name: `Conta IDOR01 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );

    // userB uses userA's valid UUID directly
    const result = await executeGraphQL(`
      query GetFinancialAccount($id: ID!) {
        financialAccount(id: $id) { id name type }
      }
    `, { id: accountA.id }, userB.token);

    if (result.errors) {
      expect(result.errors[0].message).toMatch(NOT_FOUND);
    } else {
      expect(result.data?.financialAccount).toBeNull();
    }
  });

  test('SEC-IDOR-02: using a transaction ID as an account ID — type mismatch error, no data leak', async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;

    const accountA = await createFinancialAccount(
      { name: `Conta IDOR02 ${Date.now()}`, type: 'BANK', subtype: 'CONTA_CORRENTE', initialBalance: 0 },
      userA.token
    );
    const tx = await createTransaction(
      { financialAccountId: accountA.id, description: 'IDOR02', amount: 1, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      userA.token
    );

    // userB uses a transaction ID where an account ID is expected
    const result = await executeGraphQL(`
      query Transactions($financialAccountId: ID, $first: Int) {
        transactions(financialAccountId: $financialAccountId, first: $first) {
          edges { node { id amount } }
        }
      }
    `, { financialAccountId: tx.id, first: 10 }, userB.token);

    // Must not leak any data — error or empty list
    if (!result.errors) {
      const edges = result.data?.transactions?.edges ?? [];
      expect(edges.length).toBe(0);
    }

    await deleteTransaction(tx.id, userA.token);
  });
});

// =============================================================================
// SEC-TOK — Token Manipulation
// =============================================================================

test.describe('Security — Token Manipulation', () => {
  test('SEC-TOK-01: expired JWT returns 401 Unauthorized', async ({
    twoTenants,
  }) => {
    // Simulate an expired token by using a well-formed but expired JWT
    // This is a known-expired token with exp in the past (static fixture)
    const expiredToken =
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
      '.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QiLCJleHAiOjEwMDAwMDAwMDB9' +
      '.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';

    const result = await executeGraphQL(`
      query { financialAccounts { id } }
    `, {}, expiredToken);

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(/unauthorized|forbidden|invalid.*token|token.*invalid|expired/i);
  });

  test('SEC-TOK-02: malformed JWT (invalid signature) returns 401', async () => {
    const malformedToken = 'header.payload.INVALID_SIGNATURE';

    const result = await executeGraphQL(`
      query { financialAccounts { id } }
    `, {}, malformedToken);

    expect(result.errors).toBeDefined();
  });

  test('SEC-TOK-03: request with no Authorization header returns 401', async () => {
    // executeGraphQL called with no token
    const result = await executeGraphQL(`
      query { financialAccounts { id } }
    `, {}, undefined);

    expect(result.errors).toBeDefined();
  });

  test('SEC-TOK-04: request with an empty Bearer token returns 401', async () => {
    const result = await executeGraphQL(`
      query { financialAccounts { id } }
    `, {}, '');

    expect(result.errors).toBeDefined();
  });
});
