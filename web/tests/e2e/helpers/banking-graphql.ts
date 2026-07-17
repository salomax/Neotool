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
  includeInNetWorth: boolean;
  cardData?: {
    closingDay?: number;
    dueDay?: number;
    maskedNumber?: string;
    isMultiCard?: boolean;
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
  /** First day of the due month — bill identity and list ordering key. */
  referenceMonth: string;
  totalAmount: number;
  status: 'OPEN' | 'CLOSED' | 'PAID' | 'OVERDUE';
}

export type BillImportJobStatus =
  | 'PENDING'
  | 'EXTRACTING'
  | 'EXTRACTED'
  | 'LLM_PROCESSING'
  | 'AWAITING_REVIEW'
  | 'IMPORTING'
  | 'DONE'
  | 'FAILED';

export interface BillImportReviewTransaction {
  date: string | null;
  description: string;
  amount: number;
  creditDebitType: CreditDebitType;
  isBillPayment: boolean;
  installment?: { current: number; total: number } | null;
  categoryId?: string | null;
  categorySource?: string | null;
}

export interface BillImportReviewCard {
  reviewCardId: string;
  lastFourDigits?: string | null;
  closingDate?: string | null;
  dueDate?: string | null;
  transactions: BillImportReviewTransaction[];
}

export interface BillImportReview {
  cards: BillImportReviewCard[];
}

export interface BillImportJob {
  id: string;
  status: BillImportJobStatus;
  fileName?: string | null;
  createdAt: string;
  reviewPayload?: BillImportReview | null;
}

export interface ConfirmBillReviewTransactionInput {
  date: string;
  description: string;
  amount: number;
  creditDebitType: CreditDebitType;
  include: boolean;
  isBillPayment?: boolean;
  installment?: { current: number; total: number };
  categoryId?: string;
  categorySource?: string;
}

export interface ConfirmBillReviewCardInput {
  reviewCardId: string;
  financialAccountId: string;
  transactions: ConfirmBillReviewTransactionInput[];
}

export interface ConfirmBillImportInput {
  jobId: string;
  closingDate?: string;
  dueDate?: string;
  cards: ConfirmBillReviewCardInput[];
}

export interface CreateFinancialAccountInput {
  name: string;
  type: string;
  subtype: string;
  includeInNetWorth?: boolean;
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
    isMultiCard?: boolean;
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

/** Returns the first day of the due month (referenceMonth) for a YYYY-MM-DD due date. */
export function referenceMonthFromDueDate(dueDate: string): string {
  const [year, month] = dueDate.split('-');
  return `${year}-${month}-01`;
}

/** Encodes UTF-8 text as base64 for GraphQL file upload fields. */
export function toBase64Utf8(content: string): string {
  return Buffer.from(content, 'utf-8').toString('base64');
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
    includeInNetWorth: input.includeInNetWorth,
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
  if ('includeInNetWorth' in input) normalized.includeInNetWorth = input.includeInNetWorth;
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
  includeInNetWorth
  cardData {
    closingDay
    dueDay
    maskedNumber
    isMultiCard
  }
  bankData {
    branchCode
    number
    maskedNumber
  }
  createdAt
`;

const TRANSIENT_BANKING_ERROR_PATTERNS = [
  /connection closed before message completed/i,
  /http fetch failed from 'banking'/i,
  /socket hang up/i,
  /econnreset/i,
  /temporarily unavailable/i,
] as const;

function isTransientBankingErrorMessage(message: string | undefined): boolean {
  if (!message) return false;
  return TRANSIENT_BANKING_ERROR_PATTERNS.some((pattern) => pattern.test(message));
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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

  const normalizedInput = normalizeCreateFinancialAccountInput(input);
  const maxAttempts = 3;
  let lastErrorMessage = 'Unknown error';

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const result = await executeGraphQL<{
      createFinancialAccount: FinancialAccount;
    }>(mutation, { input: normalizedInput }, token);

    if (!result.errors && result.data?.createFinancialAccount) {
      return result.data.createFinancialAccount;
    }

    lastErrorMessage = result.errors?.[0]?.message ?? 'Unknown error';
    const shouldRetry =
      attempt < maxAttempts && isTransientBankingErrorMessage(lastErrorMessage);
    if (!shouldRetry) {
      break;
    }

    await sleep(300 * attempt);
  }

  throw new Error(`createFinancialAccount failed: ${lastErrorMessage}`);
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

export async function deleteFinancialAccount(id: string, token: string): Promise<void> {
  const mutation = `
    mutation DeleteFinancialAccount($id: ID!) {
      deleteFinancialAccount(id: $id)
    }
  `;

  const result = await executeGraphQL<{
    deleteFinancialAccount: boolean;
  }>(mutation, { id }, token);

  if (result.errors || !result.data?.deleteFinancialAccount) {
    throw new Error(
      `deleteFinancialAccount failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }
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

/** Lists transactions linked to a specific credit card bill (bill-mode filter). */
export async function getTransactionsByBillId(
  billId: string,
  financialAccountId: string,
  token: string,
  options?: { first?: number }
): Promise<Transaction[]> {
  const query = `
    query TransactionsByBill($billId: ID!, $financialAccountId: ID, $first: Int) {
      transactions(billId: $billId, financialAccountId: $financialAccountId, first: $first) {
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
  }>(query, { billId, financialAccountId, first: options?.first ?? 50 }, token);

  if (result.errors) {
    throw new Error(`getTransactionsByBillId failed: ${result.errors[0].message}`);
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
        referenceMonth
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
  referenceMonth: string,
  token: string
): Promise<CreditCardBill | null> {
  const query = `
    query GetCreditCardBill($financialAccountId: ID!, $referenceMonth: Date) {
      creditCardBill(financialAccountId: $financialAccountId, referenceMonth: $referenceMonth) {
        id
        financialAccountId
        closingDate
        dueDate
        referenceMonth
        totalAmount
        status
      }
    }
  `;

  const result = await executeGraphQL<{ creditCardBill: CreditCardBill | null }>(
    query,
    { financialAccountId, referenceMonth },
    token
  );

  if (result.errors) {
    throw new Error(`getCreditCardBill failed: ${result.errors[0].message}`);
  }

  return result.data?.creditCardBill ?? null;
}

/** Resolves a bill by id from the account bill list (when the spec already has billId from a transaction). */
export async function getCreditCardBillById(
  financialAccountId: string,
  billId: string,
  token: string
): Promise<CreditCardBill | null> {
  const bills = await getCreditCardBills(financialAccountId, token);
  return bills.find((b) => b.id === billId) ?? null;
}

// ── Bill Import ─────────────────────────────────────────────────────────────────

export async function submitBillImport(
  input: {
    fileName: string;
    fileBase64: string;
    financialAccountId?: string;
    password?: string;
  },
  token: string
): Promise<BillImportJob> {
  const mutation = `
    mutation SubmitBillImport($input: SubmitBillImportInput!) {
      submitBillImport(input: $input) {
        id
        status
        fileName
        createdAt
        reviewPayload {
          cards {
            reviewCardId
            lastFourDigits
            closingDate
            dueDate
            transactions {
              date
              description
              amount
              creditDebitType
              isBillPayment
              installment { current total }
              categoryId
              categorySource
            }
          }
        }
      }
    }
  `;

  const result = await executeGraphQL<{ submitBillImport: BillImportJob }>(
    mutation,
    {
      input: {
        importType: 'CREDIT_CARD_BILL',
        ...input,
      },
    },
    token
  );

  if (result.errors || !result.data?.submitBillImport) {
    throw new Error(
      `submitBillImport failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.submitBillImport;
}

export async function getBillImportJob(jobId: string, token: string): Promise<BillImportJob> {
  const query = `
    query BillImportJob($jobId: ID!) {
      billImportJob(jobId: $jobId) {
        id
        status
        fileName
        createdAt
        reviewPayload {
          cards {
            reviewCardId
            lastFourDigits
            closingDate
            dueDate
            transactions {
              date
              description
              amount
              creditDebitType
              isBillPayment
              installment { current total }
              categoryId
              categorySource
            }
          }
        }
      }
    }
  `;

  const result = await executeGraphQL<{ billImportJob: BillImportJob | null }>(
    query,
    { jobId },
    token
  );

  if (result.errors || !result.data?.billImportJob) {
    throw new Error(
      `getBillImportJob failed: ${result.errors?.[0]?.message ?? 'Job not found'}`
    );
  }

  return result.data.billImportJob;
}

export function buildConfirmBillImportInput(
  job: BillImportJob,
  financialAccountId: string,
  options: { closingDate?: string; dueDate?: string }
): ConfirmBillImportInput {
  const review = job.reviewPayload;
  if (!review?.cards?.length) {
    throw new Error('Bill import job has no review payload cards');
  }

  return {
    jobId: job.id,
    closingDate: options.closingDate,
    dueDate: options.dueDate,
    cards: review.cards.map((card) => ({
      reviewCardId: card.reviewCardId,
      financialAccountId,
      transactions: card.transactions
        .filter((tx) => !tx.isBillPayment)
        .map((tx) => {
          if (!tx.date) {
            throw new Error(`Review transaction "${tx.description}" is missing a date`);
          }
          return {
            date: tx.date,
            description: tx.description,
            amount: tx.amount,
            creditDebitType: tx.creditDebitType,
            include: true,
            ...(tx.installment ? { installment: tx.installment } : {}),
            ...(tx.categoryId
              ? { categoryId: tx.categoryId, categorySource: tx.categorySource ?? undefined }
              : {}),
          };
        }),
    })),
  };
}

export async function confirmBillImport(
  input: ConfirmBillImportInput,
  token: string
): Promise<BillImportJob> {
  const mutation = `
    mutation ConfirmBillImport($input: ConfirmBillImportInput!) {
      confirmBillImport(input: $input) {
        id
        status
        fileName
        createdAt
      }
    }
  `;

  const result = await executeGraphQL<{ confirmBillImport: BillImportJob }>(
    mutation,
    { input },
    token
  );

  if (result.errors || !result.data?.confirmBillImport) {
    throw new Error(
      `confirmBillImport failed: ${result.errors?.[0]?.message ?? 'Unknown error'}`
    );
  }

  return result.data.confirmBillImport;
}
