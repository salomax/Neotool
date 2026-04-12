/**
 * Banking-specific GraphQL helpers for E2E tests.
 * Mirrors the structure of graphql.ts — plain template strings, no Apollo client.
 *
 * Re-exports banking enums from app codegen — prefer `CreditDebitType.Debit` etc. over raw strings.
 * @see docs/03-features/personal-finance/requirements/02-transactions.md
 */

import { executeGraphQL } from './graphql';
import {
  CreditDebitType,
  TransactionStatus,
  TransactionType,
} from './banking-schema-enums';

export {
  CategoryApplicableTo,
  CreditDebitType,
  TransactionStatus,
  TransactionType,
} from './banking-schema-enums';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface FinancialAccount {
  id: string;
  type: string;
  subtype: string;
  name: string;
  currencyCode: string;
  cardData?: {
    closingDay?: number;
    dueDay?: number;
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
  creditDebitType: CreditDebitType;
  type: TransactionType;
  status: TransactionStatus;
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
  /** Required for past-dated ledger tests when paired with `initialBalance`; helpers default to `today()` if omitted. */
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
  creditDebitType: CreditDebitType;
  /** When omitted, defaults to OtherExpense (DEBIT) or OtherIncome (CREDIT) — same as the manual UI. */
  type?: TransactionType;
  status?: TransactionStatus;
  date?: string;
  notes?: string;
  tags?: string[];
  categoryId?: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * YYYY-MM-DD using the environment's local calendar.
 * Prefer this over `toISOString().split('T')[0]` for dates built with the `Date(y, m, d)`
 * constructor — in timezones ahead of UTC the UTC string can fall on the previous day.
 */
export function formatLocalIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Returns today's local calendar date as YYYY-MM-DD. */
export function today(): string {
  return formatLocalIsoDate(new Date());
}

/** Returns the first day of the current month as YYYY-MM-DD (local). */
export function firstDayOfMonth(): string {
  const now = new Date();
  return formatLocalIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

/** Returns the last day of the current month as YYYY-MM-DD (local). */
export function lastDayOfMonth(): string {
  const now = new Date();
  return formatLocalIsoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0));
}

/** Returns a date in a given month offset from today (e.g. -1 = last month), local calendar. */
export function dateInMonth(monthOffset: number, day = 15): string {
  const now = new Date();
  return formatLocalIsoDate(
    new Date(now.getFullYear(), now.getMonth() + monthOffset, day),
  );
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

  if (typeof input.initialBalance === 'number') {
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

function normalizeUpdateFinancialAccountInput(
  input: Partial<CreateFinancialAccountInput>
): Record<string, unknown> {
  if (!input.type || !input.subtype) {
    throw new Error('updateFinancialAccount requires input.type and input.subtype');
  }

  const normalized: Record<string, unknown> = {
    type: input.type,
    subtype: input.subtype,
  };

  if ('name' in input) normalized.name = input.name;
  if ('currencyCode' in input) normalized.currencyCode = input.currencyCode;
  if ('issuerId' in input) normalized.issuerId = input.issuerId;
  if ('issuerName' in input) normalized.issuerName = input.issuerName;

  if (input.bankData !== undefined) {
    normalized.bankData = input.bankData;
  } else {
    const bankData: Record<string, unknown> = {};
    if ('branchCode' in input) bankData.branchCode = input.branchCode;
    if ('accountNumber' in input) bankData.number = input.accountNumber;
    if ('checkDigit' in input) bankData.checkDigit = input.checkDigit;
    if (Object.keys(bankData).length > 0) normalized.bankData = bankData;
  }

  if (input.cardData !== undefined) {
    normalized.cardData = input.cardData;
  } else {
    const cardData: Record<string, unknown> = {};
    if ('closingDay' in input) cardData.closingDay = input.closingDay;
    if ('dueDay' in input) cardData.dueDay = input.dueDay;
    if (Object.keys(cardData).length > 0) normalized.cardData = cardData;
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
  }>(mutation, { id, input: normalizeUpdateFinancialAccountInput(input) }, token);

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

function defaultTransactionTypeForCreditDebit(
  creditDebitType: CreditDebitType,
): TransactionType {
  return creditDebitType === CreditDebitType.Credit
    ? TransactionType.OtherIncome
    : TransactionType.OtherExpense;
}

/** API requires `type`; align with manual UI when callers omit it. */
function normalizeCreateTransactionInput(
  input: CreateTransactionInput,
): CreateTransactionInput & { type: TransactionType } {
  return {
    ...input,
    type: input.type ?? defaultTransactionTypeForCreditDebit(input.creditDebitType),
  };
}

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
  }>(mutation, { input: normalizeCreateTransactionInput(input) }, token);

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

/**
 * Lists transactions for an account. When `month` / `year` are omitted, the API uses the
 * **current calendar month** (same default as the finance-transactions UI table). Pass explicit
 * `year` and `month` when loading rows in another month (e.g. past-dated txs, import fixtures,
 * or POSTED rows dated in a **calendar-future** month where `monthSummary` headers may differ by version).
 */
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

/**
 * Monthly header totals and balances. For months **strictly after** the server’s current calendar
 * month, income/expense semantics may combine POSTED/PENDING with FUTURE depending on banking
 * version; empty months and “earliest tx after requested month” edge cases are documented in
 * `transactions.e2e.spec.ts`.
 */
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
