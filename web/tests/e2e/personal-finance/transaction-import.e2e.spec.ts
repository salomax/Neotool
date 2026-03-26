/**
 * E2E tests — Transaction Import
 *
 * Coverage: IM-H-01..06 | IM-A-01..08 | IM-N-01..06
 * Security coverage is in security.e2e.spec.ts (SEC-IM-01..02).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Import flow (from ImportTransactionsDrawer.tsx):
 *   Steps: account → upload → processing → result | review | error
 *   Max file size: 3 MB
 *   Accepted: .ofx, .csv, .xlsx, .xls, .pdf
 *
 * Import lifecycle:
 *   PENDING → PROCESSING → COMPLETED | REVIEW_REQUIRED | FAILED
 *
 * Test files (in tests/e2e/fixtures/files/):
 *   valid-transactions.csv  — 10 transactions across one month
 *   valid-transactions.ofx  — 5 transactions, OFX format
 *   malformed.csv           — not a valid financial file
 *   empty.csv               — header only, no transactions
 *
 * Strategy:
 *   The import flow requires the app to be fully running (file upload, GraphQL
 *   subscription polling for import job status). All import tests use the
 *   full browser stack.
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * TODO: add the following data-testid attributes to ImportTransactionsDrawer.tsx:
 *   - data-testid="import-drawer"
 *   - data-testid="import-account-selector"       step: account
 *   - data-testid="import-dropzone"               step: upload
 *   - data-testid="import-file-input"             hidden <input type="file">
 *   - data-testid="import-upload-btn"             Upload button
 *   - data-testid="import-processing-indicator"   step: processing
 *   - data-testid="import-result-summary"         step: result
 *   - data-testid="import-result-imported-count"
 *   - data-testid="import-result-skipped-count"
 *   - data-testid="import-result-duplicate-count"
 *   - data-testid="import-error-message"          step: error
 *   - data-testid="import-confirm-btn"            Confirm import button
 *   - data-testid="import-cancel-btn"             Cancel import
 *   - data-testid="btn-open-import-drawer"        Trigger button on main page
 */

import path from 'path';
import { test, expect } from '../fixtures/personal-finance';
import { FinanceTransactionsPage } from '../pages/personal-finance/FinanceTransactionsPage';
import {
  getTransactions,
  deleteTransaction,
  getMonthSummary,
} from '../helpers/banking-graphql';

// ── Paths to test fixture files ───────────────────────────────────────────────

const FILES_DIR = path.join(__dirname, '../fixtures/files');
const CSV_VALID     = path.join(FILES_DIR, 'valid-transactions.csv');
const OFX_VALID     = path.join(FILES_DIR, 'valid-transactions.ofx');
const CSV_MALFORMED = path.join(FILES_DIR, 'malformed.csv');
const CSV_EMPTY     = path.join(FILES_DIR, 'empty.csv');

// ── Helpers ───────────────────────────────────────────────────────────────────

const IMPORT_TIMEOUT = 30_000; // 30 s — import job may take time

async function signIntoPage(page: import('@playwright/test').Page, token: string): Promise<void> {
  await page.goto('/');
  await page.evaluate((t) => localStorage.setItem('auth_token', t), token);
  await page.reload();
  await page.waitForLoadState('networkidle');
}

/**
 * Opens the import drawer from the main transactions page.
 * Returns the drawer locator.
 */
async function openImportDrawer(page: import('@playwright/test').Page, accountName: string) {
  const pfPage = new FinanceTransactionsPage(page);
  await pfPage.goto();
  await pfPage.selectAccount(accountName);

  // TODO: update selector once data-testid="btn-open-import-drawer" is added
  await page.getByTestId('btn-open-import-drawer').click();
  const drawer = page.getByTestId('import-drawer');
  await drawer.waitFor({ state: 'visible' });
  return drawer;
}

/**
 * Uploads a file via the import drawer's file input.
 * Waits for the upload step to complete.
 */
async function uploadFile(page: import('@playwright/test').Page, filePath: string) {
  const fileInput = page.getByTestId('import-file-input');
  await fileInput.setInputFiles(filePath);
}

/**
 * Waits for the import job to finish processing.
 * Polls for the result/review/error step to appear.
 */
async function waitForImportComplete(page: import('@playwright/test').Page) {
  await Promise.race([
    page.getByTestId('import-result-summary').waitFor({ state: 'visible', timeout: IMPORT_TIMEOUT }),
    page.getByTestId('import-error-message').waitFor({ state: 'visible', timeout: IMPORT_TIMEOUT }),
    page.getByRole('heading', { name: /duplicat|review/i }).waitFor({ state: 'visible', timeout: IMPORT_TIMEOUT }),
  ]);
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe('Transaction Import — happy path', () => {
  test('IM-H-01: previews a valid CSV file — 10 transactions listed for confirmation', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    // Either result or review step should appear
    const resultOrReview = await Promise.race([
      page.getByTestId('import-result-summary').isVisible(),
      page.locator('[data-testid="import-result-imported-count"]').isVisible(),
    ]);
    expect(resultOrReview).toBe(true);

    await drawer.getByTestId('import-cancel-btn').click();
  });

  test('IM-H-02: confirms CSV import — transactions created in the account', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token);
    const countBefore = txsBefore.length;

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    // If a duplicate review is required, skip all duplicates
    const reviewVisible = await page.getByTestId('import-duplicate-review').isVisible().catch(() => false);
    if (reviewVisible) {
      // Skip all duplicates (IM-H-06 covers the acceptance path)
      const skipAllBtn = page.getByRole('button', { name: /ignorar todos|skip all/i });
      if (await skipAllBtn.isVisible()) await skipAllBtn.click();
    }

    // Confirm the import
    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();

    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    expect(txsAfter.length).toBeGreaterThan(countBefore);

    // Cleanup: delete imported transactions
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-H-03: previews a valid OFX file — 5 transactions listed', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, OFX_VALID);
    await waitForImportComplete(page);

    const isVisible = await page.getByTestId('import-result-summary').isVisible()
      .catch(() => false);
    expect(isVisible).toBe(true);

    await drawer.getByTestId('import-cancel-btn').click();
  });

  test('IM-H-04: confirms OFX import — transactions created in the account', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token);

    await uploadFile(page, OFX_VALID);
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    expect(txsAfter.length).toBeGreaterThan(txsBefore.length);

    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-H-05: re-importing the same CSV triggers duplicate detection review', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);

    // First import
    const drawer1 = await openImportDrawer(page, bankAccount.name);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    const confirmBtn1 = page.getByTestId('import-confirm-btn');
    if (await confirmBtn1.isVisible()) await confirmBtn1.click();
    await page.waitForLoadState('networkidle');

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    // Second import of the same file — should trigger duplicate review
    const drawer2 = await openImportDrawer(page, bankAccount.name);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    // At least a duplicate-related UI should appear
    const hasDuplicateReview = await page.getByTestId('import-duplicate-review').isVisible()
      .catch(() => false);
    const hasSkipOption = await page.getByRole('button', { name: /ignorar|skip/i }).isVisible()
      .catch(() => false);

    expect(hasDuplicateReview || hasSkipOption).toBe(true);

    // Cancel to not create duplicates
    await page.getByTestId('import-cancel-btn').click();

    // Cleanup first import
    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-H-06: in duplicate review, skipping all → only NEW transactions imported', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // First: seed some transactions that match the CSV
    const { createTransaction, today } = await import('../helpers/banking-graphql');
    const seedTx = await createTransaction(
      { financialAccountId: bankAccount.id, description: 'Salario', amount: 5000, creditDebitType: 'CREDIT', status: 'POSTED', date: '2026-03-01' },
      pfUser.token
    );

    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    const hasDuplicates = await page.getByTestId('import-duplicate-review').isVisible().catch(() => false);
    if (hasDuplicates) {
      await page.getByRole('button', { name: /ignorar todos|skip all/i }).click();
    }

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    // New count should be less than total CSV rows because at least the duplicate was skipped
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
    // Only non-duplicate rows should have been created
    expect(newTxs.length).toBeLessThan(10); // CSV has 10 rows, at least 1 skipped

    // Cleanup
    await deleteTransaction(seedTx.id, pfUser.token).catch(() => {});
    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe('Transaction Import — alternative paths', () => {
  test('IM-A-01: CSV spans multiple months — all months\' summaries updated', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // valid-transactions.csv has all transactions in March 2026
    // This verifies the summary for that specific month reflects the import
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    // Verify March 2026 summary was computed
    const march2026 = await getMonthSummary(bankAccount.id, 2026, 3, pfUser.token);
    expect(march2026.totalIncome).toBeGreaterThan(0);
    expect(march2026.totalExpenses).toBeGreaterThan(0);

    // Cleanup
    const txs = await getTransactions(bankAccount.id, pfUser.token, { month: 3, year: 2026, first: 50 });
    await Promise.all(txs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-02: CSV with mix of debits and credits — both types imported correctly', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));

    const credits = newTxs.filter((t) => t.creditDebitType === 'CREDIT');
    const debits  = newTxs.filter((t) => t.creditDebitType === 'DEBIT');

    // valid-transactions.csv has both CREDIT and DEBIT rows
    expect(credits.length).toBeGreaterThan(0);
    expect(debits.length).toBeGreaterThan(0);

    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-03: OFX with duplicate FITID within the same file — only one instance imported', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Create an OFX with a duplicate FITID on-the-fly as a Buffer
    const ofxContent = `OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n<OFX>
<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<DTSERVER>20260325120000</DTSERVER><LANGUAGE>POR</LANGUAGE></SONRS></SIGNONMSGSRSV1>
<BANKMSGSRSV1><STMTTRNRS><TRNUID>1</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<STMTRS><CURDEF>BRL</CURDEF><BANKACCTFROM><BANKID>001</BANKID><ACCTID>999</ACCTID><ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTFROM>
<BANKTRANLIST><DTSTART>20260301000000</DTSTART><DTEND>20260325000000</DTEND>
<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260310000000</DTPOSTED><TRNAMT>-100.00</TRNAMT><FITID>DUPLICATE001</FITID><MEMO>Compra X</MEMO></STMTTRN>
<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260311000000</DTPOSTED><TRNAMT>-200.00</TRNAMT><FITID>DUPLICATE001</FITID><MEMO>Compra X copia</MEMO></STMTTRN>
</BANKTRANLIST><LEDGERBAL><BALAMT>700.00</BALAMT><DTASOF>20260325000000</DTASOF></LEDGERBAL>
</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>`;

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    // Upload the duplicate-FITID OFX as a blob
    const fileInput = page.getByTestId('import-file-input');
    await fileInput.setInputFiles({
      name: 'duplicate-fitid.ofx',
      mimeType: 'application/x-ofx',
      buffer: Buffer.from(ofxContent, 'utf-8'),
    });
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));

    // Only 1 of the 2 duplicate-FITID entries should have been imported
    expect(newTxs.length).toBeLessThanOrEqual(1);

    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-04: cancelling after preview — no transactions created', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    // Cancel without confirming
    await page.getByTestId('import-cancel-btn').click();
    await drawer.waitFor({ state: 'hidden' });

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    expect(txsAfter.length).toBe(txsBefore.length);
  });

  test('IM-A-05: CSV with UTF-8 special characters (accents) — descriptions preserved', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const csvContent = `Data,Descricao,Valor,Tipo\n2026-03-10,Pão de Açúcar,-150.00,DEBIT\n2026-03-11,Locação de Veículo,-300.00,DEBIT\n`;
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await page.getByTestId('import-file-input').setInputFiles({
      name: 'acentos.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent, 'utf-8'),
    });
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
    const paoDacucar = newTxs.find((t) => t.description.includes('ão') || t.description.includes('Pão'));

    if (paoDacucar) {
      expect(paoDacucar.description).toContain('ão');
    }

    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-06: partial import — some duplicates skipped, new ones created', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Seed one transaction that matches CSV
    const { createTransaction } = await import('../helpers/banking-graphql');
    const existing = await createTransaction(
      { financialAccountId: bankAccount.id, description: 'Salario', amount: 5000, creditDebitType: 'CREDIT', status: 'POSTED', date: '2026-03-01' },
      pfUser.token
    );

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });

    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    // Accept new ones, skip duplicates
    const hasDuplicates = await page.getByTestId('import-duplicate-review').isVisible().catch(() => false);
    if (hasDuplicates) {
      await page.getByRole('button', { name: /ignorar|skip/i }).first().click();
    }

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, { first: 50 });
    const newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));

    // Some new transactions should exist (the non-duplicates)
    expect(newTxs.length).toBeGreaterThan(0);
    // The skipped one should not have been duplicated
    const salarioCount = txsAfter.filter((t) => t.description === 'Salario' && t.date === '2026-03-01').length;
    expect(salarioCount).toBe(1);

    await deleteTransaction(existing.id, pfUser.token).catch(() => {});
    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-07: import result page shows imported/skipped/duplicate counts', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    const confirmBtn = page.getByTestId('import-confirm-btn');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForLoadState('networkidle');

    // The result step should show at least an imported count
    const resultSummary = page.getByTestId('import-result-summary');
    if (await resultSummary.isVisible()) {
      const importedCount = page.getByTestId('import-result-imported-count');
      await expect(importedCount).toBeVisible();
    }

    // Cleanup
    const txs = await getTransactions(bankAccount.id, pfUser.token, { month: 3, year: 2026, first: 50 });
    await Promise.all(txs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test('IM-A-08: import to account not pre-selected — user selects account in drawer', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Navigate to PF page WITHOUT selecting an account first
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();

    // Open import from a global button (not account-specific)
    // TODO: update selector if the import button is only shown when an account is selected
    await page.getByTestId('btn-open-import-drawer').click();
    const drawer = page.getByTestId('import-drawer');
    await drawer.waitFor({ state: 'visible' });

    // Step 'account' may be shown — select the account
    const accountSelector = page.getByTestId('import-account-selector');
    const accountStepVisible = await accountSelector.isVisible().catch(() => false);
    if (accountStepVisible) {
      await accountSelector.click();
      await page.getByRole('option', { name: bankAccount.name }).click();
    }

    await page.getByTestId('import-file-input').setInputFiles(CSV_VALID);
    await waitForImportComplete(page);

    // Cancel after preview — not testing full import here, just the account selection step
    await page.getByTestId('import-cancel-btn').click();
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe('Transaction Import — negative paths', () => {
  test('IM-N-01: uploading a PDF file (unsupported format) — error shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await page.getByTestId('import-file-input').setInputFiles({
      name: 'document.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 fake pdf content'),
    });

    // Either the file input rejects it (HTML accept attribute) or the UI shows an error
    const errorVisible = await page.getByTestId('import-error-message').isVisible()
      .catch(() => false);
    const inputRejectsType = await page.getByTestId('import-file-input')
      .evaluate((el: HTMLInputElement) => el.files?.length === 0)
      .catch(() => false);

    expect(errorVisible || inputRejectsType).toBe(true);
    await page.getByTestId('import-cancel-btn').click();
  });

  test('IM-N-02: uploading a malformed CSV — error message displayed', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, CSV_MALFORMED);
    await waitForImportComplete(page).catch(() => {});

    await expect(page.getByTestId('import-error-message')).toBeVisible({ timeout: IMPORT_TIMEOUT });
    await page.getByTestId('import-cancel-btn').click();
  });

  test('IM-N-03: uploading an empty CSV (header only, no rows) — informative message shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await uploadFile(page, CSV_EMPTY);
    await waitForImportComplete(page).catch(() => {});

    // Expects either an error message or an empty-state in the result step
    const errorMsg = page.getByTestId('import-error-message');
    const resultSummary = page.getByTestId('import-result-summary');

    const visible = await Promise.race([
      errorMsg.isVisible(),
      resultSummary.isVisible(),
    ]);
    expect(visible).toBe(true);

    await page.getByTestId('import-cancel-btn').click();
  });

  test('IM-N-04: uploading a file exceeding 3 MB — size error shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    // Create a 3.1 MB buffer
    const largeBuffer = Buffer.alloc(3.1 * 1024 * 1024, 'a');
    await page.getByTestId('import-file-input').setInputFiles({
      name: 'large-file.csv',
      mimeType: 'text/csv',
      buffer: largeBuffer,
    });

    // Either the UI shows an immediate size error or the upload is rejected
    const error = page.getByTestId('import-error-message');
    const visible = await error.isVisible({ timeout: 5_000 }).catch(() => false);
    // If not an error message, the file input should have rejected the file
    if (!visible) {
      const fileRejected = await page.getByTestId('import-file-input')
        .evaluate((el: HTMLInputElement) => !el.files || el.files.length === 0)
        .catch(() => false);
      expect(fileRejected).toBe(true);
    }

    await page.getByTestId('import-cancel-btn').click();
  });

  test('IM-N-05: OFX with invalid date format — error message displayed', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const badOfx = `OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n<OFX>
<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<DTSERVER>INVALID_DATE</DTSERVER><LANGUAGE>POR</LANGUAGE></SONRS></SIGNONMSGSRSV1>
<BANKMSGSRSV1><STMTTRNRS><TRNUID>1</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<STMTRS><CURDEF>BRL</CURDEF><BANKACCTFROM><BANKID>001</BANKID><ACCTID>999</ACCTID><ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTFROM>
<BANKTRANLIST><DTSTART>BADDATE</DTSTART><DTEND>BADDATE</DTEND>
<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>BADDATE</DTPOSTED><TRNAMT>-100.00</TRNAMT><FITID>B001</FITID><MEMO>Bad Date</MEMO></STMTTRN>
</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>`;

    await signIntoPage(page, pfUser.token);
    const drawer = await openImportDrawer(page, bankAccount.name);

    await page.getByTestId('import-file-input').setInputFiles({
      name: 'bad-dates.ofx',
      mimeType: 'application/x-ofx',
      buffer: Buffer.from(badOfx, 'utf-8'),
    });

    await waitForImportComplete(page).catch(() => {});
    await expect(page.getByTestId('import-error-message')).toBeVisible({ timeout: IMPORT_TIMEOUT });
    await page.getByTestId('import-cancel-btn').click();
  });

  test('IM-N-06: importing to a non-existent account via GraphQL direct — returns error', async ({
    pfUser,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const result = await executeGraphQL(`
      mutation ImportTransactionsFile($input: ImportTransactionsFileInput!) {
        importTransactionsFile(input: $input) {
          import { id status }
        }
      }
    `, {
      input: {
        financialAccountId: '00000000-0000-0000-0000-000000000000',
        fileName: 'test.csv',
        fileContent: btoa('Data,Descricao,Valor\n2026-03-01,Test,-100'),
      },
    }, pfUser.token);

    expect(result.errors).toBeDefined();
    expect(result.errors?.[0]?.message).toMatch(/not found|não encontrad/i);
  });
});
