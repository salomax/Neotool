/**
 * GraphQL helpers for transaction-rules E2E coverage.
 */

import { expect } from '@playwright/test';
import fs from 'fs';
import os from 'os';
import path from 'path';
import { executeGraphQL } from './graphql';
import {
  CategoryApplicableTo,
  CategorySource,
  PatternType,
  RuleCreationMode,
  TransactionType,
} from '../../../src/lib/graphql/types/__generated__/graphql';
import { IMPORT_FIXTURE_MONTH, IMPORT_FIXTURE_YEAR } from './finance-transactions-e2e';
import { E2E_IMPORT_JOB_TIMEOUT_MS } from './timeouts';

export { CategoryApplicableTo, CategorySource, PatternType, RuleCreationMode, TransactionType };

export interface TransactionCategory {
  id: string;
  name: string;
  applicableTo: CategoryApplicableTo;
}

export interface TransactionRule {
  id: string;
  pattern: string;
  patternType: PatternType;
  applicableTo: CategoryApplicableTo;
  typeOverride?: CategoryApplicableTo | null;
  categoryId?: string | null;
  merchantId?: string | null;
  descriptionOverride?: string | null;
  tagsOverride?: string[] | null;
  priority: number;
  position: number;
  isSystem: boolean;
  isImplicit: boolean;
}

export interface TransactionWithRuleFields {
  id: string;
  description: string;
  type?: TransactionType | null;
  categoryId?: string | null;
  categorySource?: CategorySource | null;
  merchantId?: string | null;
  tags?: string[];
  category?: { id: string; name: string } | null;
  merchant?: { id: string; name: string } | null;
  appliedRuleHistory?: Array<{ ruleId: string; appliedAt: string }>;
}

export interface CreateTransactionRuleInput {
  pattern: string;
  patternType: PatternType;
  applicableTo: CategoryApplicableTo;
  typeOverride?: CategoryApplicableTo;
  categoryId?: string;
  merchantId?: string;
  descriptionOverride?: string;
  tagsOverride?: string[];
  priority?: number;
}

type ImportJobSnapshot = {
  id: string;
  status: string;
  importedCount: number | null;
};

function assertNoErrors<T>(
  result: { errors?: Array<{ message: string; extensions?: Record<string, unknown> }>; data?: T },
  label: string,
): T {
  if (result.errors?.length) {
    const err = result.errors[0]!;
    const params = (err.extensions?.parameters as Record<string, unknown> | undefined);
    const reason = params?.reason ? ` — reason: ${params.reason}` : '';
    const code = err.extensions?.code ? ` [${err.extensions.code}]` : '';
    throw new Error(`${label}: ${err.message ?? 'Unknown GraphQL error'}${code}${reason}`);
  }
  if (result.data == null) {
    throw new Error(`${label}: missing data`);
  }
  return result.data;
}

export async function getTransactionCategories(token: string): Promise<TransactionCategory[]> {
  const result = await executeGraphQL<{ transactionCategories: TransactionCategory[] }>(
    `query { transactionCategories { id name applicableTo } }`,
    {},
    token,
  );
  return assertNoErrors(result, 'getTransactionCategories').transactionCategories;
}

export async function findExpenseCategory(token: string): Promise<TransactionCategory> {
  const categories = await getTransactionCategories(token);
  const category = categories.find((c) => c.applicableTo === CategoryApplicableTo.Expense);
  if (!category) throw new Error('No EXPENSE category available for transaction-rules E2E');
  return category;
}

export async function findIncomeCategory(token: string): Promise<TransactionCategory> {
  const categories = await getTransactionCategories(token);
  const category = categories.find((c) => c.applicableTo === CategoryApplicableTo.Income);
  if (!category) throw new Error('No INCOME category available for transaction-rules E2E');
  return category;
}

export async function findAnotherExpenseCategory(
  token: string,
  excludeId: string,
): Promise<TransactionCategory> {
  const categories = await getTransactionCategories(token);
  const category = categories.find(
    (c) => c.applicableTo === CategoryApplicableTo.Expense && c.id !== excludeId,
  );
  if (!category) throw new Error('Need at least two EXPENSE categories for priority E2E');
  return category;
}

export async function createMerchant(
  name: string,
  token: string,
): Promise<{ id: string; name: string }> {
  const result = await executeGraphQL<{ createMerchant: { id: string; name: string } }>(
    `
      mutation CreateMerchant($input: CreateMerchantInput!) {
        createMerchant(input: $input) {
          id
          name
        }
      }
    `,
    { input: { name } },
    token,
  );
  return assertNoErrors(result, 'createMerchant').createMerchant;
}

export async function createTransactionRule(
  input: CreateTransactionRuleInput,
  token: string,
): Promise<TransactionRule> {
  const result = await executeGraphQL<{ createTransactionRule: TransactionRule }>(
    `
      mutation CreateTransactionRule($input: CreateTransactionRuleInput!) {
        createTransactionRule(input: $input) {
          id
          pattern
          patternType
          applicableTo
          typeOverride
          categoryId
          merchantId
          descriptionOverride
          tagsOverride
          priority
          position
          isSystem
          isImplicit
        }
      }
    `,
    { input },
    token,
  );
  return assertNoErrors(result, 'createTransactionRule').createTransactionRule;
}

export async function updateTransactionRule(
  id: string,
  input: Partial<CreateTransactionRuleInput>,
  token: string,
): Promise<TransactionRule> {
  const result = await executeGraphQL<{ updateTransactionRule: TransactionRule }>(
    `
      mutation UpdateTransactionRule($id: ID!, $input: UpdateTransactionRuleInput!) {
        updateTransactionRule(id: $id, input: $input) {
          id
          pattern
          patternType
          applicableTo
          typeOverride
          categoryId
          merchantId
          descriptionOverride
          tagsOverride
          priority
          position
          isSystem
          isImplicit
        }
      }
    `,
    { id, input },
    token,
  );
  return assertNoErrors(result, 'updateTransactionRule').updateTransactionRule;
}

export async function deleteTransactionRule(id: string, token: string): Promise<void> {
  const result = await executeGraphQL<{ deleteTransactionRule: boolean }>(
    `mutation DeleteTransactionRule($id: ID!) { deleteTransactionRule(id: $id) }`,
    { id },
    token,
  );
  assertNoErrors(result, 'deleteTransactionRule');
}

export async function previewTransactionRule(
  pattern: string,
  patternType: PatternType,
  sample: string,
  token: string,
): Promise<boolean> {
  const result = await executeGraphQL<{ previewTransactionRule: boolean }>(
    `
      query PreviewTransactionRule($pattern: String!, $patternType: PatternType!, $sample: String!) {
        previewTransactionRule(pattern: $pattern, patternType: $patternType, sample: $sample)
      }
    `,
    { pattern, patternType, sample },
    token,
  );
  return assertNoErrors(result, 'previewTransactionRule').previewTransactionRule;
}

export async function applyTransactionRuleToHistorical(
  id: string,
  token: string,
): Promise<number> {
  const result = await executeGraphQL<{
    applyTransactionRuleToHistorical: { affectedCount: number };
  }>(
    `
      mutation ApplyTransactionRuleToHistorical($id: ID!) {
        applyTransactionRuleToHistorical(id: $id) {
          affectedCount
        }
      }
    `,
    { id },
    token,
  );
  return assertNoErrors(result, 'applyTransactionRuleToHistorical').applyTransactionRuleToHistorical
    .affectedCount;
}

export async function reorderTransactionRules(
  ids: string[],
  token: string,
): Promise<TransactionRule[]> {
  const result = await executeGraphQL<{ reorderTransactionRules: TransactionRule[] }>(
    `
      mutation ReorderTransactionRules($ids: [ID!]!) {
        reorderTransactionRules(ids: $ids) {
          id
          pattern
          priority
          position
          categoryId
        }
      }
    `,
    { ids },
    token,
  );
  return assertNoErrors(result, 'reorderTransactionRules').reorderTransactionRules;
}

export async function listUserTransactionRules(
  token: string,
  search?: string,
): Promise<TransactionRule[]> {
  const result = await executeGraphQL<{
    transactionRules: { edges: Array<{ node: TransactionRule }> };
  }>(
    `
      query TransactionRules($search: String) {
        transactionRules(isSystem: false, search: $search, first: 50) {
          edges {
            node {
              id
              pattern
              patternType
              applicableTo
              typeOverride
              categoryId
              merchantId
              descriptionOverride
              tagsOverride
              priority
              position
              isSystem
              isImplicit
            }
          }
        }
      }
    `,
    { search: search ?? null },
    token,
  );
  return assertNoErrors(result, 'listUserTransactionRules').transactionRules.edges.map((e) => e.node);
}

export async function updateBankingUserPreferences(
  ruleCreationMode: RuleCreationMode,
  token: string,
): Promise<void> {
  const result = await executeGraphQL(
    `
      mutation UpdateBankingUserPreferences($input: UpdateBankingUserPreferencesInput!) {
        updateBankingUserPreferences(input: $input) {
          ruleCreationMode
        }
      }
    `,
    { input: { ruleCreationMode } },
    token,
  );
  assertNoErrors(result, 'updateBankingUserPreferences');
}

type TransactionRuleFieldsNode = {
  id: string;
  description: string;
  type?: TransactionType | null;
  categorySource?: CategorySource | null;
  tags?: string[];
  category?: { id: string; name: string } | null;
  merchant?: { id: string; name: string } | null;
  appliedRuleHistory?: Array<{ ruleId: string; appliedAt: string }>;
};

function mapTransactionRuleFields(node: TransactionRuleFieldsNode): TransactionWithRuleFields {
  return {
    id: node.id,
    description: node.description,
    type: node.type,
    categorySource: node.categorySource,
    tags: node.tags,
    category: node.category,
    merchant: node.merchant,
    categoryId: node.category?.id ?? null,
    merchantId: node.merchant?.id ?? null,
    appliedRuleHistory: node.appliedRuleHistory,
  };
}

export async function findTransactionByDescription(
  financialAccountId: string,
  description: string,
  token: string,
): Promise<TransactionWithRuleFields | undefined> {
  const result = await executeGraphQL<{
    transactions: { edges: Array<{ node: TransactionRuleFieldsNode }> };
  }>(
    `
      query Transactions($financialAccountId: ID, $search: String, $year: Int, $month: Int, $first: Int) {
        transactions(
          financialAccountId: $financialAccountId
          search: $search
          year: $year
          month: $month
          first: $first
        ) {
          edges {
            node {
              id
              description
              type
              categorySource
              tags
              category { id name }
              merchant { id name }
              appliedRuleHistory { ruleId appliedAt }
            }
          }
        }
      }
    `,
    {
      financialAccountId,
      search: description,
      year: IMPORT_FIXTURE_YEAR,
      month: IMPORT_FIXTURE_MONTH,
      first: 20,
    },
    token,
  );

  const edges = assertNoErrors(result, 'findTransactionByDescription').transactions.edges;
  const node = edges.find((edge) => edge.node.description === description)?.node;
  return node ? mapTransactionRuleFields(node) : undefined;
}

/** Builds a one-row OFX bank statement for March 2026 fixture month. */
export function buildSingleTransactionOfx(params: {
  memo: string;
  amount: string;
  trnType: 'DEBIT' | 'CREDIT';
  fitId?: string;
  datePosted?: string;
}): string {
  const fitId = params.fitId ?? `E2E-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  const datePosted = params.datePosted ?? '20260315120000';
  const trnType = params.trnType;
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  const content = `OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<SIGNONMSGSRSV1>
<SONRS>
<STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<DTSERVER>20260325120000</DTSERVER>
<LANGUAGE>POR</LANGUAGE>
</SONRS>
</SIGNONMSGSRSV1>
<BANKMSGSRSV1>
<STMTTRNRS>
<TRNUID>${suffix}</TRNUID>
<STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<STMTRS>
<CURDEF>BRL</CURDEF>
<BANKACCTFROM>
<BANKID>001</BANKID>
<ACCTID>12345-6</ACCTID>
<ACCTTYPE>CHECKING</ACCTTYPE>
</BANKACCTFROM>
<BANKTRANLIST>
<DTSTART>20260301000000</DTSTART>
<DTEND>20260325000000</DTEND>
<STMTTRN>
<TRNTYPE>${trnType}</TRNTYPE>
<DTPOSTED>${datePosted}</DTPOSTED>
<TRNAMT>${params.amount}</TRNAMT>
<FITID>${fitId}</FITID>
<MEMO>${params.memo}</MEMO>
</STMTTRN>
</BANKTRANLIST>
<LEDGERBAL>
<BALAMT>1000.00</BALAMT>
<DTASOF>20260325000000</DTASOF>
</LEDGERBAL>
</STMTRS>
</STMTTRNRS>
</BANKMSGSRSV1>
</OFX>
`;
  const tmpPath = path.join(os.tmpdir(), `e2e-rule-import-${suffix}.ofx`);
  fs.writeFileSync(tmpPath, content);
  return tmpPath;
}

export async function importOfxAndWait(
  financialAccountId: string,
  token: string,
  filePath: string,
): Promise<ImportJobSnapshot> {
  const fileBytes = fs.readFileSync(filePath);
  const submit = await executeGraphQL<{ importTransactionsFile: { import: ImportJobSnapshot } }>(
    `
      mutation ImportTransactionsFile($input: ImportTransactionsFileInput!) {
        importTransactionsFile(input: $input) {
          import {
            id
            status
            importedCount
          }
        }
      }
    `,
    {
      input: {
        financialAccountId,
        fileName: path.basename(filePath),
        fileBase64: fileBytes.toString('base64'),
      },
    },
    token,
  );
  const job = assertNoErrors(submit, 'importTransactionsFile').importTransactionsFile.import;

  let latest = job;
  await expect
    .poll(
      async () => {
        const polled = await executeGraphQL<{ transactionImport: ImportJobSnapshot | null }>(
          `
            query TransactionImport($id: ID!) {
              transactionImport(id: $id) {
                id
                status
                importedCount
              }
            }
          `,
          { id: job.id },
          token,
        );
        latest = assertNoErrors(polled, 'transactionImport').transactionImport ?? latest;
        return latest.status === 'COMPLETED' || latest.status === 'FAILED' || latest.status === 'REVIEW_REQUIRED';
      },
      { timeout: E2E_IMPORT_JOB_TIMEOUT_MS },
    )
    .toBe(true);

  if (latest.status !== 'COMPLETED') {
    throw new Error(`Import job ${latest.id} ended with status ${latest.status}`);
  }
  return latest;
}
