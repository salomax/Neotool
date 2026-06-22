/**
 * E2E — Transaction rules
 *
 * Coverage: pattern matching, import-time application, CRUD, preview, apply-to-historical,
 * priority/reorder, ruleCreationMode coverage, and rules management UI.
 *
 * Uses GraphQL for setup/assertions (reliable) plus focused UI coverage on the rules page.
 * Import rows use March 2026 OFX fixtures — `bankAccount` is extended with `initialBalanceDate: 2026-01-01`.
 *
 * Conventions (avoid flakes):
 * - Use `uniqueTestMarker()` for patterns/memos — not seeded merchant terms (netflix, uber, salario, pix).
 * - Assert user-rule outcomes via `expectUserRuleApplied` / `expectUserRuleNotApplied`, not bare `categoryId` null.
 * - EXACT/REGEX: match `normalizeDescription(memo)`; REGEX is full-string (`^…$`) in Kotlin.
 * - Duplicate API constraint: unique `(pattern, patternType)` — vary patternType when testing precedence.
 * - descriptionOverride: look up tx by override text, not original memo.
 * - UI locators: bilingual pt|en — default locale is Portuguese.
 */

import fs from 'fs';
import type { Page } from '@playwright/test';
import { test as basePfTest, expect } from '../fixtures/personal-finance';
import {
  createFinancialAccount,
  deleteTransaction,
  getMonthSummary,
} from '../helpers/banking-graphql';
import { IMPORT_FIXTURE_MONTH, IMPORT_FIXTURE_YEAR } from '../helpers/finance-transactions-e2e';
import {
  applyTransactionRuleToHistorical,
  buildSingleTransactionOfx,
  CategoryApplicableTo,
  CategorySource,
  createMerchant,
  createTransactionRule,
  deleteTransactionRule,
  findAnotherExpenseCategory,
  findExpenseCategory,
  findIncomeCategory,
  findTransactionByDescription,
  importOfxAndWait,
  listUserTransactionRules,
  PatternType,
  previewTransactionRule,
  reorderTransactionRules,
  RuleCreationMode,
  TransactionType,
  updateBankingUserPreferences,
  updateTransactionRule,
} from '../helpers/transaction-rules-e2e';
import { E2E_HEAVY_DESCRIBE_TIMEOUT_MS } from '../helpers/timeouts';
import { signIntoPage } from '../helpers/navigation';
import { FinanceTransactionsPage } from '../pages/personal-finance/FinanceTransactionsPage';

function uniqueLabel(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** Collision-free token — avoids seeded system rules (netflix, uber, salario, pix, etc.). */
function uniqueTestMarker(prefix: string): string {
  return `${prefix}${Date.now()}${Math.random().toString(36).slice(2, 7)}`;
}

/** Mirrors banking TransactionDescriptionNormalizer — EXACT patterns must match normalized text. */
function normalizeDescription(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[^\p{ASCII}]/gu, '')
    .replace(/[^a-zA-Z0-9\s*]/g, ' ')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

function expectUserRuleApplied(
  tx: { categoryId?: string | null; appliedRuleHistory?: Array<{ ruleId: string }> },
  ruleId: string,
  categoryId?: string,
): void {
  if (categoryId !== undefined) {
    expect(tx.categoryId).toBe(categoryId);
  }
  expect(tx.appliedRuleHistory?.some((entry) => entry.ruleId === ruleId)).toBe(true);
}

function expectUserRuleNotApplied(
  tx: { categoryId?: string | null; appliedRuleHistory?: Array<{ ruleId: string }> },
  ruleId: string,
  categoryId?: string,
): void {
  if (categoryId !== undefined) {
    expect(tx.categoryId).not.toBe(categoryId);
  }
  expect(tx.appliedRuleHistory?.some((entry) => entry.ruleId === ruleId) ?? false).toBe(false);
}

function uniqueBankAccountName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** Bilingual UI strings — app defaults to pt-BR in local E2E. */
const RULES_SEARCH_PLACEHOLDER = /Buscar\.\.\.|Search by pattern/i;
const RULES_CUSTOM_TAB = /Regras Customizadas|Custom Rules/i;
const RULES_NEW_BUTTON = /Nova Regra|New Rule/i;
const RULES_DRAWER_TITLE = /Nova Regra|New Rule/i;
const RULES_TYPE_OVERRIDE_LABEL = /Tipo de reclassificacao|Reclassification type/i;
const TRANSFER_OPTION = /Transferência|Transfer/i;

function quotedRulePattern(pattern: string): string {
  return `"${pattern}"`;
}

async function waitForRulesManagementPage(page: Page): Promise<void> {
  await page.waitForURL(/\/personal-finance\/finance-transactions\/transaction-rules/);
  await expect(page.getByRole('tab', { name: RULES_CUSTOM_TAB })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByPlaceholder(RULES_SEARCH_PLACEHOLDER)).toBeVisible({ timeout: 15_000 });
}

const test = basePfTest.extend({
  bankAccount: async ({ pfUser }, provideAccount) => {
    const account = await createFinancialAccount(
      {
        name: uniqueBankAccountName('Conta E2E Rules'),
        type: 'BANK',
        subtype: 'CONTA_CORRENTE',
        initialBalance: 1000.0,
        initialBalanceDate: '2026-01-01',
      },
      pfUser.token,
    );
    await provideAccount(account);
  },
});

test.describe.configure({ mode: 'serial', timeout: E2E_HEAVY_DESCRIBE_TIMEOUT_MS });

test.describe('Transaction rules', () => {
  async function importDebitWithMemo(
    financialAccountId: string,
    token: string,
    memo: string,
    amount = '-25.00',
    lookupDescription = memo,
  ) {
    const ofxPath = buildSingleTransactionOfx({
      memo,
      amount,
      trnType: 'DEBIT',
    });
    try {
      const job = await importOfxAndWait(financialAccountId, token, ofxPath);
      expect(job.importedCount).toBeGreaterThan(0);
      const tx = await findTransactionByDescription(financialAccountId, lookupDescription, token);
      expect(tx).toBeDefined();
      return tx!;
    } finally {
      fs.unlinkSync(ofxPath);
    }
  }

  async function importCreditWithMemo(
    financialAccountId: string,
    token: string,
    memo: string,
    amount = '100.00',
    lookupDescription = memo,
  ) {
    const ofxPath = buildSingleTransactionOfx({
      memo,
      amount,
      trnType: 'CREDIT',
    });
    try {
      await importOfxAndWait(financialAccountId, token, ofxPath);
      const tx = await findTransactionByDescription(financialAccountId, lookupDescription, token);
      expect(tx).toBeDefined();
      return tx!;
    } finally {
      fs.unlinkSync(ofxPath);
    }
  }

  test('TR-H-01: CONTAINS rule assigns category on OFX import', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('contains');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, expenseCategory.id);
      expect(tx.categorySource).toBe(CategorySource.Rule);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-02: uppercase pattern matches lowercase import description', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('case');
    const memo = uniqueLabel(marker.toLowerCase());
    const rule = await createTransactionRule(
      {
        pattern: marker.toUpperCase(),
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, expenseCategory.id);
      expect(tx.categorySource).toBe(CategorySource.Rule);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-03: CONTAINS does not match pattern embedded inside another word', async ({
    pfUser,
    bankAccount,
  }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    // Synthetic token embedded in a longer word — avoids seeded "uber" system rules on memo text.
    const embedded = uniqueTestMarker('uber');
    const memo = uniqueLabel(`CLAU${embedded}BER LTDA`);
    const rule = await createTransactionRule(
      {
        pattern: embedded,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleNotApplied(tx, rule.id, expenseCategory.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-04: STARTS_WITH rule matches import description', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('startswith');
    const memo = uniqueLabel(`${marker} VILA`);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.StartsWith,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, expenseCategory.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-05: EXACT rule matches import description', async ({ pfUser, bankAccount }) => {
    const incomeCategory = await findIncomeCategory(pfUser.token);
    const marker = uniqueTestMarker('exact');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: normalizeDescription(memo),
        patternType: PatternType.Exact,
        applicableTo: CategoryApplicableTo.Income,
        categoryId: incomeCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importCreditWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, incomeCategory.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-06: EXPENSE rule does not match CREDIT import rows', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('expenseonly');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importCreditWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleNotApplied(tx, rule.id, expenseCategory.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-07: INCOME rule matches CREDIT import rows only', async ({ pfUser, bankAccount }) => {
    const incomeCategory = await findIncomeCategory(pfUser.token);
    const marker = uniqueTestMarker('incomecredit');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Income,
        categoryId: incomeCategory.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importCreditWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, incomeCategory.id);
      expect(tx.categorySource).toBe(CategorySource.Rule);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-08: descriptionOverride is applied on import', async ({ pfUser, bankAccount }) => {
    const marker = uniqueTestMarker('descoverride');
    const memo = uniqueLabel(`${marker}*123`);
    const override = `Delivery ${uniqueTestMarker('label')}`;
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        descriptionOverride: override,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo, '-25.00', override);
      expect(tx.description).toBe(override);
      expectUserRuleApplied(tx, rule.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, override, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-09: tagsOverride is applied on import', async ({ pfUser, bankAccount }) => {
    const marker = uniqueTestMarker('tagsoverride');
    const memo = uniqueLabel(marker);
    const tags = [`tag-${uniqueTestMarker('a')}`, `tag-${uniqueTestMarker('b')}`];
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        tagsOverride: tags,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expect(tx.tags).toEqual(expect.arrayContaining(tags));
      expectUserRuleApplied(tx, rule.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-10: merchantId is applied on import', async ({ pfUser, bankAccount }) => {
    const merchant = await createMerchant(uniqueLabel('Merchant Rule'), pfUser.token);
    const marker = uniqueTestMarker('merchantrule');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        merchantId: merchant.id,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expect(tx.merchantId).toBe(merchant.id);
      expect(tx.merchant?.name).toBe(merchant.name);
      expectUserRuleApplied(tx, rule.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-11: applyTransactionRuleToHistorical updates existing imports', async ({
    pfUser,
    bankAccount,
  }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('historical');
    const memo = uniqueLabel(marker);
    let ruleId: string | undefined;

    try {
      const imported = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expect(imported.categoryId).not.toBe(expenseCategory.id);
      expect(imported.appliedRuleHistory ?? []).toHaveLength(0);

      const rule = await createTransactionRule(
        {
          pattern: marker,
          patternType: PatternType.Contains,
          applicableTo: CategoryApplicableTo.Expense,
          categoryId: expenseCategory.id,
        },
        pfUser.token,
      );
      ruleId = rule.id;

      const affected = await applyTransactionRuleToHistorical(rule.id, pfUser.token);
      expect(affected).toBeGreaterThanOrEqual(1);

      const updated = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      expectUserRuleApplied(updated!, rule.id, expenseCategory.id);
      expect(updated?.categorySource).toBe(CategorySource.Rule);
    } finally {
      if (ruleId) await deleteTransactionRule(ruleId, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-12: previewTransactionRule reflects matching semantics', async ({ pfUser }) => {
    const token = uniqueTestMarker('preview');
    const matches = await previewTransactionRule(
      token,
      PatternType.Contains,
      `PAGAMENTO ${token} EATS`,
      pfUser.token,
    );
    const embedded = await previewTransactionRule(
      token,
      PatternType.Contains,
      `CLAU${token}BER LTDA`,
      pfUser.token,
    );

    expect(matches).toBe(true);
    expect(embedded).toBe(false);
  });

  test('TR-H-13: updateTransactionRule changes import matching', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('updaterule');
    const memo = uniqueLabel(marker);
    let ruleId: string | undefined;

    try {
      const rule = await createTransactionRule(
        {
          pattern: uniqueTestMarker('nomatch'),
          patternType: PatternType.Contains,
          applicableTo: CategoryApplicableTo.Expense,
          categoryId: expenseCategory.id,
        },
        pfUser.token,
      );
      ruleId = rule.id;

      const beforeUpdate = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleNotApplied(beforeUpdate, rule.id, expenseCategory.id);

      await updateTransactionRule(
        rule.id,
        { pattern: marker },
        pfUser.token,
      );

      const memoAfter = uniqueLabel(marker);
      const afterUpdate = await importDebitWithMemo(bankAccount.id, pfUser.token, memoAfter);
      expectUserRuleApplied(afterUpdate, rule.id, expenseCategory.id);

      await deleteTransaction(beforeUpdate.id, pfUser.token).catch(() => {});
      await deleteTransaction(afterUpdate.id, pfUser.token).catch(() => {});
    } finally {
      if (ruleId) await deleteTransactionRule(ruleId, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-14: deleteTransactionRule stops matching new imports', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('deleterule');
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    const memoMatched = uniqueLabel(marker);
    const memoAfterDelete = uniqueLabel(marker);

    try {
      const matched = await importDebitWithMemo(bankAccount.id, pfUser.token, memoMatched);
      expectUserRuleApplied(matched, rule.id, expenseCategory.id);

      await deleteTransactionRule(rule.id, pfUser.token);

      const afterDelete = await importDebitWithMemo(bankAccount.id, pfUser.token, memoAfterDelete);
      expectUserRuleNotApplied(afterDelete, rule.id, expenseCategory.id);

      await deleteTransaction(matched.id, pfUser.token).catch(() => {});
      await deleteTransaction(afterDelete.id, pfUser.token).catch(() => {});
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-15: ruleCreationMode NEVER does not prevent rules on import', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('nevermode');
    const memo = uniqueLabel(marker);
    const rule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      await updateBankingUserPreferences(RuleCreationMode.Never, pfUser.token);
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, expenseCategory.id);
      expect(tx.categorySource).toBe(CategorySource.Rule);
    } finally {
      await updateBankingUserPreferences(RuleCreationMode.AlwaysPrompt, pfUser.token).catch(() => {});
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-16: lower priority number wins when multiple rules match', async ({
    pfUser,
    bankAccount,
  }) => {
    const primaryCategory = await findExpenseCategory(pfUser.token);
    const secondaryCategory = await findAnotherExpenseCategory(pfUser.token, primaryCategory.id);
    const marker = uniqueTestMarker('priority');
    const memo = uniqueLabel(marker);

    // pattern+patternType must be unique per account — use different types that both match memo.
    const highPriorityRule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: primaryCategory.id,
        priority: 10,
      },
      pfUser.token,
    );
    const lowPriorityRule = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.StartsWith,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: secondaryCategory.id,
        priority: 20,
      },
      pfUser.token,
    );

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, highPriorityRule.id, primaryCategory.id);
    } finally {
      await deleteTransactionRule(highPriorityRule.id, pfUser.token).catch(() => {});
      await deleteTransactionRule(lowPriorityRule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-17: reorderTransactionRules changes evaluation order', async ({ pfUser, bankAccount }) => {
    const firstCategory = await findExpenseCategory(pfUser.token);
    const secondCategory = await findAnotherExpenseCategory(pfUser.token, firstCategory.id);
    const marker = uniqueTestMarker('reorder');
    const memo = uniqueLabel(marker);

    const ruleA = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: firstCategory.id,
      },
      pfUser.token,
    );
    const ruleB = await createTransactionRule(
      {
        pattern: marker,
        patternType: PatternType.StartsWith,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: secondCategory.id,
      },
      pfUser.token,
    );

    try {
      await reorderTransactionRules([ruleB.id, ruleA.id], pfUser.token);

      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, ruleB.id, secondCategory.id);
    } finally {
      await deleteTransactionRule(ruleA.id, pfUser.token).catch(() => {});
      await deleteTransactionRule(ruleB.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-H-18: REGEX rule matches import description', async ({ pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const marker = uniqueTestMarker('regex');
    // REGEX uses Kotlin String.matches — full-string match on normalized description.
    const memo = `${marker} COM BR`;
    const normalizedMemo = normalizeDescription(memo);
    const rule = await createTransactionRule(
      {
        pattern: `^${marker}\\s+com\\s+br$`,
        patternType: PatternType.Regex,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    const previewMatches = await previewTransactionRule(
      rule.pattern,
      PatternType.Regex,
      memo,
      pfUser.token,
    );
    expect(previewMatches).toBe(true);
    expect(normalizedMemo).toMatch(new RegExp(rule.pattern, 'i'));

    try {
      const tx = await importDebitWithMemo(bankAccount.id, pfUser.token, memo);
      expectUserRuleApplied(tx, rule.id, expenseCategory.id);
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });

  test('TR-UI-01: rules management page lists a created rule', async ({ page, pfUser, bankAccount }) => {
    const expenseCategory = await findExpenseCategory(pfUser.token);
    const pattern = uniqueTestMarker('uirule');
    const rule = await createTransactionRule(
      {
        pattern,
        patternType: PatternType.Contains,
        applicableTo: CategoryApplicableTo.Expense,
        categoryId: expenseCategory.id,
      },
      pfUser.token,
    );

    try {
      await signIntoPage(page, pfUser.token);
      await page.goto('/personal-finance/finance-transactions/transaction-rules');
      await waitForRulesManagementPage(page);

      const searchInput = page.getByPlaceholder(RULES_SEARCH_PLACEHOLDER);
      await searchInput.fill(pattern);
      await expect(page.getByText(quotedRulePattern(pattern))).toBeVisible({ timeout: 15_000 });

      const rules = await listUserTransactionRules(pfUser.token, pattern);
      expect(rules.some((r) => r.id === rule.id)).toBe(true);

      // GraphQL + UI agree
      await expect(page.getByRole('button', { name: RULES_NEW_BUTTON })).toBeVisible();
    } finally {
      await deleteTransactionRule(rule.id, pfUser.token).catch(() => {});
      void bankAccount;
    }
  });

  test('TR-UI-02: creating a type override rule via UI reclassifies import as TRANSFER', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const pattern = uniqueTestMarker('uitypeoverride');
    const memo = uniqueLabel(pattern);
    let ruleId: string | undefined;
    const baselineSummary = await getMonthSummary(
      bankAccount.id,
      IMPORT_FIXTURE_YEAR,
      IMPORT_FIXTURE_MONTH,
      pfUser.token,
    );

    try {
      await signIntoPage(page, pfUser.token);
      await page.goto('/personal-finance/finance-transactions/transaction-rules');
      await waitForRulesManagementPage(page);

      await page.getByRole('button', { name: RULES_NEW_BUTTON }).click();
      await expect(page.getByRole('heading', { name: RULES_DRAWER_TITLE })).toBeVisible({
        timeout: 15_000,
      });

      await page.getByRole('textbox', { name: /Texto comparativo|Matching text/i }).fill(pattern);
      await page.getByRole('radio', { name: /Despesa|Expense/i }).check();

      const typeOverrideField = page.getByRole('combobox', { name: RULES_TYPE_OVERRIDE_LABEL });
      await typeOverrideField.click();
      await page.getByRole('option', { name: TRANSFER_OPTION }).click();

      await page.getByRole('button', { name: /Salvar Regra|Save Rule/i }).click();
      await expect(page.getByRole('heading', { name: RULES_DRAWER_TITLE })).toBeHidden({
        timeout: 15_000,
      });

      await expect
        .poll(
          async () => {
            const rules = await listUserTransactionRules(pfUser.token, pattern);
            const createdRule = rules.find((candidate) => candidate.pattern === pattern);
            ruleId = createdRule?.id;
            return createdRule?.typeOverride ?? null;
          },
          { timeout: 15_000 },
        )
        .toBe(CategoryApplicableTo.Transfer);

      const createdRule = (await listUserTransactionRules(pfUser.token, pattern)).find(
        (candidate) => candidate.pattern === pattern,
      );
      expect(createdRule).toBeDefined();
      expect(createdRule?.applicableTo).toBe(CategoryApplicableTo.Expense);
      expect(createdRule?.typeOverride).toBe(CategoryApplicableTo.Transfer);
      ruleId = createdRule?.id;

      const importAmount = 25;
      const importedTx = await importDebitWithMemo(
        bankAccount.id,
        pfUser.token,
        memo,
        `-${importAmount}.00`,
      );
      expect(importedTx.type).toBe(TransactionType.Transfer);
      expectUserRuleApplied(importedTx, ruleId!);

      const afterSummary = await getMonthSummary(
        bankAccount.id,
        IMPORT_FIXTURE_YEAR,
        IMPORT_FIXTURE_MONTH,
        pfUser.token,
      );
      expect(afterSummary.totalExpenses).toBeCloseTo(baselineSummary.totalExpenses, 2);
      expect(afterSummary.totalIncome).toBeCloseTo(baselineSummary.totalIncome, 2);
      expect(afterSummary.closingBalance).toBeCloseTo(
        baselineSummary.closingBalance - importAmount,
        2,
      );

      const pfPage = new FinanceTransactionsPage(page);
      await pfPage.goto();
      await pfPage.selectAccount(bankAccount.name, bankAccount.id);
      await pfPage.navigateToCalendarMonth({
        year: IMPORT_FIXTURE_YEAR,
        month: IMPORT_FIXTURE_MONTH,
      });
      await pfPage.summary.waitForMetricsReady();
      await pfPage.transactionTable.expectTransactionVisible(memo);

      const uiSummary = await pfPage.summary.getAll();
      expect(uiSummary.totalExpenses).toBeCloseTo(afterSummary.totalExpenses, 2);
      expect(uiSummary.totalIncome).toBeCloseTo(afterSummary.totalIncome, 2);
      expect(uiSummary.closingBalance).toBeCloseTo(afterSummary.closingBalance, 2);
    } finally {
      if (ruleId) {
        await deleteTransactionRule(ruleId, pfUser.token).catch(() => {});
      }
      const tx = await findTransactionByDescription(bankAccount.id, memo, pfUser.token);
      if (tx?.id) await deleteTransaction(tx.id, pfUser.token).catch(() => {});
    }
  });
});
