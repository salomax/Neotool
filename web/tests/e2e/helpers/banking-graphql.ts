/**
 * Banking-specific GraphQL helpers for E2E tests.
 * Mirrors the structure of graphql.ts — plain template strings, no Apollo client.
 */

import { executeGraphQL } from './graphql';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface FinancialAccount {
  id: string;
  type: string;
  subtype: string;
  name: string;
  currencyCode: string;
  cardData?: {
    closingDay: number;
    dueDay: number;
    maskedNumber?: string;
  } | null;
  bankData?: {
    branchCode?: string;
    number?: string;
    maskedNumber?: string;
  } | null;
  createdAt: string;
}

export interface Transaction {
  id: string;
  financialAccountId: string;
  creditDebitType: 'CREDIT' | 'DEBIT';
  type: string;
  status: 'POSTED' | 'PENDING' | 'FUTURE';
  amount: number;
  currencyCode: string;
  date: string;
  description: string;
  notes?: string | null;
  tags?: string[];
  billId?: string | null;
}

export interface MonthSummary {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  closingBalance: number;
  openingBalance: number;
  transfersIn: number;
  transfersOut: number;
}

export interface CreditCardBill {
  id: string;
  financialAccountId: string;
  closingDate: string;
  dueDate: string;
  totalAmount: number;
  status: 'OPEN' | 'CLOSED' | 'PAID' | 'OVERDUE';
}

export interface CreateFinancialAccountInput {
  name: string;
  type: string;
  subtype: string;
  initialBalance?: number;
  initialBalanceDate?: string;
  currencyCode?: string;
  closingDay?: number;
  dueDay?: number;
  branchCode?: string;
  accountNumber?: string;
  checkDigit?: string;
  issuerId?: string;
  issuerName?: string;
  bankData?: {
    branchCode?: string;
    number?: string;
    checkDigit?: string;
    maskedNumber?: string;
  };
  cardData?: {
    maskedNumber?: string;
    bin?: string;
    cardNetwork?: string;
    cardLevel?: string;
    creditLimit?: number;
    closingDay?: number;
    dueDay?: number;
    linkedBankAccountId?: string;
  };
}

export interface CreateTransactionInput {
  financialAccountId: string;
  description: string;
  amount: number;
  creditDebitType: 'CREDIT' | 'DEBIT';
  type?: string;
  status?: 'POSTED' | 'PENDING' | 'FUTURE';
  date?: string;
  notes?: string;
  tags?: string[];
  categoryId?: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns today's date as ISO string (YYYY-MM-DD). */
export function today(): string {
  return new Date().toISOString().split('T')[0];
}

/** Returns the first day of the current month as ISO string. */
export function firstDayOfMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
}

/** Returns the last day of the current month as ISO string. */
export function lastDayOfMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0];
}

/** Returns a date in a given month offset from today (e.g. -1 = last month). */
export function dateInMonth(monthOffset: number, day = 15): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() + monthOffset, day)
    .toISOString()
    .split('T')[0];
}

function normalizeCreateFinancialAccountInput(input: CreateFinancialAccountInput): Record<string, unknown> {
  const normalized: Record<string, unknown> = {
    name: input.name,
    type: input.type,
    subtype: input.subtype,
    currencyCode: input.currencyCode ?? 'BRL',
    issuerId: input.issuerId,
    issuerName: input.issuerId ? undefined : input.issuerName ?? 'E2E Test Issuer',
  };

  if (typeof input.initialBalance === 'number' && input.initialBalance !== 0) {
    normalized.initialBalance = input.initialBalance;
    normalized.initialBalanceDate = input.initialBalanceDate ?? today();
  }

  if (input.type === 'BANK') {
    normalized.bankData = input.bankData ?? {
      branchCode: input.branchCode,
      number: input.accountNumber,
      checkDigit: input.checkDigit,
    };
  }

  const isCardSubtype = ['CREDIT_CARD', 'CHARGE_CARD', 'PREPAID_CARD', 'VOUCHER_CARD'].includes(input.subtype);
  if (isCardSubtype) {
    normalized.cardData = input.cardData ?? {
      closingDay: input.closingDay,
      dueDay: input.dueDay,
    };
  }

  return normalized;
}

// ── Financial Accounts ────────────────────────────────────────────────────────

const FINANCIAL_ACCOUNT_FIELDS = `
  id
  type
  subtype
  name
  currencyCode
  cardData {
    closingDay
    dueDay
    maskedNumber
  }
  bankData {
    branchCode
    number
    maskedNumber
  }
  createdAt
`;

export async function createFinancialAccount(
  input: CreateFinancialAccountInput,
  token: string
): Promise<FinancialAccount> {
  const mutation = `
    mutation CreateFinancialAccount($input: CreateFinancialAccountInput!) {
      createFinancialAccount(input: $input) {
        ${FINANCIAL_ACCOUNT_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{
    createFinancialAccount: FinancialAccount;
  }>(mutation, { input: normalizeCreateFinancialAccountInput(input) }, token);

  if (result.errors || !result.data?.createFinancialAccount) {
    throw new Error(
      `createFinancialAccount failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.createFinancialAccount;
}

export async function updateFinancialAccount(
  id: string,
  input: Partial<CreateFinancialAccountInput>,
  token: string
): Promise<FinancialAccount> {
  const mutation = `
    mutation UpdateFinancialAccount($id: ID!, $input: UpdateFinancialAccountInput!) {
      updateFinancialAccount(id: $id, input: $input) {
        ${FINANCIAL_ACCOUNT_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{
    updateFinancialAccount: FinancialAccount;
  }>(mutation, { id, input: normalizeCreateFinancialAccountInput(input as CreateFinancialAccountInput) }, token);

  if (result.errors || !result.data?.updateFinancialAccount) {
    throw new Error(
      `updateFinancialAccount failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.updateFinancialAccount;
}

export async function getFinancialAccounts(token: string): Promise<FinancialAccount[]> {
  const query = `
    query FinancialAccounts {
      financialAccounts {
        ${FINANCIAL_ACCOUNT_FIELDS}
      }
    }
  `;

  const result = await executeGraphQL<{
    financialAccounts: FinancialAccount[];
  }>(query, {}, token);

  if (result.errors) {
    throw new Error(
      `getFinancialAccounts failed: ${result.errors[0].message}`
    );
  }

  return result.data?.financialAccounts ?? [];
}

// ── Transactions ──────────────────────────────────────────────────────────────

const TRANSACTION_FIELDS = `
  id
  financialAccountId
  creditDebitType
  type
  status
  amount
  currencyCode
  date
  description
  notes
  tags
  billId
`;

export async function createTransaction(
  input: CreateTransactionInput,
  token: string
): Promise<Transaction> {
  const mutation = `
    mutation CreateTransaction($input: CreateTransactionInput!) {
      createTransaction(input: $input) {
        transaction {
          ${TRANSACTION_FIELDS}
        }
      }
    }
  `;

  const result = await executeGraphQL<{
    createTransaction: { transaction: Transaction };
  }>(mutation, { input }, token);

  if (result.errors || !result.data?.createTransaction?.transaction) {
    throw new Error(
      `createTransaction failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.createTransaction.transaction;
}

export async function deleteTransaction(id: string, token: string): Promise<void> {
  const mutation = `
    mutation DeleteTransaction($id: ID!) {
      deleteTransaction(id: $id)
    }
  `;

  const result = await executeGraphQL(mutation, { id }, token);

  if (result.errors) {
    throw new Error(`deleteTransaction failed: ${result.errors[0].message}`);
  }
}

export async function getTransactions(
  financialAccountId: string,
  token: string,
  options?: { month?: number; year?: number; first?: number }
): Promise<Transaction[]> {
  const query = `
    query Transactions(
      $financialAccountId: ID
      $month: Int
      $year: Int
      $first: Int
    ) {
      transactions(
        financialAccountId: $financialAccountId
        month: $month
        year: $year
        first: $first
      ) {
        edges {
          node {
            ${TRANSACTION_FIELDS}
          }
        }
      }
    }
  `;

  const result = await executeGraphQL<{
    transactions: { edges: Array<{ node: Transaction }> };
  }>(query, { financialAccountId, ...options, first: options?.first ?? 50 }, token);

  if (result.errors) {
    throw new Error(`getTransactions failed: ${result.errors[0].message}`);
  }

  return result.data?.transactions.edges.map((e) => e.node) ?? [];
}

// ── Month Summary ─────────────────────────────────────────────────────────────

export async function getMonthSummary(
  financialAccountId: string,
  year: number,
  month: number,
  token: string
): Promise<MonthSummary> {
  const query = `
    query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
      monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
        year
        month
        totalIncome
        totalExpenses
        closingBalance
        openingBalance
        transfersIn
        transfersOut
      }
    }
  `;

  const result = await executeGraphQL<{ monthSummary: MonthSummary }>(
    query,
    { financialAccountId, year, month },
    token
  );

  if (result.errors || !result.data?.monthSummary) {
    throw new Error(
      `getMonthSummary failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.monthSummary;
}

// ── Credit Card Bills ─────────────────────────────────────────────────────────

export async function getCreditCardBills(
  financialAccountId: string,
  token: string
): Promise<CreditCardBill[]> {
  const query = `
    query GetCreditCardBills($financialAccountId: ID!) {
      creditCardBills(financialAccountId: $financialAccountId) {
        id
        financialAccountId
        closingDate
        dueDate
        totalAmount
        status
      }
    }
  `;

  const result = await executeGraphQL<{ creditCardBills: CreditCardBill[] }>(
    query,
    { financialAccountId },
    token
  );

  if (result.errors) {
    throw new Error(`getCreditCardBills failed: ${result.errors[0].message}`);
  }

  return result.data?.creditCardBills ?? [];
}

export async function getCreditCardBill(
  financialAccountId: string,
  closingDate: string,
  token: string
): Promise<CreditCardBill | null> {
  const query = `
    query GetCreditCardBill($financialAccountId: ID!, $closingDate: Date) {
      creditCardBill(financialAccountId: $financialAccountId, closingDate: $closingDate) {
        id
        financialAccountId
        closingDate
        dueDate
        totalAmount
        status
      }
    }
  `;

  const result = await executeGraphQL<{ creditCardBill: CreditCardBill | null }>(
    query,
    { financialAccountId, closingDate },
    token
  );

  if (result.errors) {
    throw new Error(`getCreditCardBill failed: ${result.errors[0].message}`);
  }

  return result.data?.creditCardBill ?? null;
}
