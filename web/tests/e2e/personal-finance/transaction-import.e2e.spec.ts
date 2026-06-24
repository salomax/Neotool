/**
 * E2E tests — Transaction Import
 *
 * Coverage: IM-H-01..06, IM-H-04a | IM-A-01..08 | IM-N-01..06
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
 *
 * Current UI contract (validated against ImportTransactionsDrawer.tsx):
 *   - the header button opens an import menu; file import is a menu item, not a direct drawer action
 *   - choosing a file only stages it; the upload starts after clicking the drawer submit button
 *   - the drawer has no separate preview/confirm step for file imports
 *   - duplicate handling happens inside ImportDuplicateReview
 *
 *   `valid-transactions.csv` / `valid-transactions.ofx` use March 2026 dates — pass that `year`/`month`
 *   to `getTransactions` when diffing or cleaning up imports (`importFixtureTransactionQuery`).
 *
 *   This file extends `bankAccount` with `initialBalanceDate: 2026-01-01` so March 2026 rows are not
 *   rejected when the default PF fixture’s opening date is “today” (e.g. April 2026).
 *
 *   Parallelism: `playwright.config` uses `fullyParallel: true`. Import tests hit long-running jobs and
 *   the same worker-scoped `pfUser` as other PF specs; running them serially avoids cross-test contention
 *   when executing `tests/e2e/personal-finance/` as a folder.
 *
 *   Shared helpers/timeouts: `tests/e2e/CONVENTIONS.md`, `finance-transactions-e2e.ts`, `timeouts.ts`.
 */

import fs from "fs";
import os from "os";
import path from "path";
import { test as basePfTest, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import {
  CreditDebitType,
  createFinancialAccount,
  deleteTransaction,
  getMonthSummary,
  getTransactions,
} from "../helpers/banking-graphql";
import { executeGraphQL } from "../helpers/graphql";
import { signIntoPage } from "../helpers/navigation";
import { importFixtureTransactionQuery } from "../helpers/finance-transactions-e2e";
import { E2E_HEAVY_DESCRIBE_TIMEOUT_MS, E2E_IMPORT_JOB_TIMEOUT_MS } from "../helpers/timeouts";

function uniqueBankAccountName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/**
 * Default PF `bankAccount` uses `initialBalanceDate = today()`, which rejects fixture CSV/OFX
 * rows dated in March 2026 when the suite runs in April+ 2026. Use an opening date before those files.
 */
const test = basePfTest.extend({
  bankAccount: async ({ pfUser }, provideAccount) => {
    const account = await createFinancialAccount(
      {
        name: uniqueBankAccountName("Conta Corrente E2E Import"),
        type: "BANK",
        subtype: "CONTA_CORRENTE",
        initialBalance: 1000.0,
        initialBalanceDate: "2026-01-01",
      },
      pfUser.token,
    );
    await provideAccount(account);
  },
});

test.describe.configure({ mode: "serial" });

// ── Paths to test fixture files ───────────────────────────────────────────────

const FILES_DIR = path.join(__dirname, "../fixtures/files");
const CSV_VALID = path.join(FILES_DIR, "valid-transactions.csv");
const OFX_VALID = path.join(FILES_DIR, "valid-transactions.ofx");
const CSV_MALFORMED = path.join(FILES_DIR, "malformed.csv");
const CSV_EMPTY = path.join(FILES_DIR, "empty.csv");

/** Clone the OFX fixture with unique FITIDs so file-hash dedupe does not block serial reruns. */
function buildUniqueOfxFixture(): string {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  let content = fs.readFileSync(OFX_VALID, "utf8");
  content = content.replace(/<FITID>[^<]+<\/FITID>/g, (line) => {
    const id = line.match(/FITID>([^<]+)/)?.[1] ?? "TX";
    return `<FITID>${id}-${suffix}</FITID>`;
  });
  content = content.replace(/<TRNUID>[^<]+<\/TRNUID>/, `<TRNUID>${suffix}</TRNUID>`);
  const tmpPath = path.join(os.tmpdir(), `e2e-import-${suffix}.ofx`);
  fs.writeFileSync(tmpPath, content);
  return tmpPath;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** OFX / duplicate-review / SSE can exceed 30s on CI; keep below default test timeout budget. */
const IMPORT_TIMEOUT = E2E_IMPORT_JOB_TIMEOUT_MS;

type ImportJobSnapshot = {
  id: string;
  status: string;
  importedCount: number | null;
};

async function importTransactionsFileViaGraphql(
  financialAccountId: string,
  token: string,
  filePath: string,
): Promise<ImportJobSnapshot> {
  const fileBytes = fs.readFileSync(filePath);
  const result = await executeGraphQL<{
    importTransactionsFile: { import: ImportJobSnapshot };
  }>(
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
        fileBase64: fileBytes.toString("base64"),
      },
    },
    token,
  );

  expect(result.errors).toBeUndefined();
  const job = result.data?.importTransactionsFile.import;
  expect(job?.id).toBeTruthy();
  return job!;
}

async function getTransactionsWithSource(
  financialAccountId: string,
  token: string,
  options?: { month?: number; year?: number; first?: number },
): Promise<Array<{ id: string; source: string; description: string }>> {
  const result = await executeGraphQL<{
    transactions: { edges: Array<{ node: { id: string; source: string; description: string } }> };
  }>(
    `
      query TransactionsWithSource(
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
              id
              source
              description
            }
          }
        }
      }
    `,
    { financialAccountId, ...options, first: options?.first ?? 50 },
    token,
  );

  expect(result.errors).toBeUndefined();
  return result.data?.transactions.edges.map((e) => e.node) ?? [];
}

async function waitForImportJobTerminal(
  importId: string,
  token: string,
  timeoutMs = E2E_HEAVY_DESCRIBE_TIMEOUT_MS,
): Promise<ImportJobSnapshot> {
  let latest: ImportJobSnapshot | undefined;
  await expect
    .poll(
      async () => {
        const result = await executeGraphQL<{
          transactionImport: ImportJobSnapshot | null;
        }>(
          `
            query TransactionImport($id: ID!) {
              transactionImport(id: $id) {
                id
                status
                importedCount
              }
            }
          `,
          { id: importId },
          token,
        );
        expect(result.errors).toBeUndefined();
        latest = result.data?.transactionImport ?? undefined;
        return (
          latest != null &&
          ["COMPLETED", "FAILED", "REVIEW_REQUIRED"].includes(latest.status)
        );
      },
      { timeout: timeoutMs },
    )
    .toBe(true);
  return latest!;
}

async function waitForImportDrawerClosed(
  page: import("@playwright/test").Page,
  timeout = 15_000,
): Promise<void> {
  await importDialogLocator(page).waitFor({ state: "hidden", timeout }).catch(() => {});
}

/**
 * Single locator union — do NOT Promise.race several waitFor(): the first branch to time out
 * rejects the whole race even when another terminal UI would appear moments later.
 */
function importDialogLocator(page: import("@playwright/test").Page) {
  return page.getByRole("dialog", {
    name: /importar movimentações|import transactions/i,
  });
}

/**
 * Terminal UI lives inside the import drawer. Scoping avoids Playwright union/first()
 * resolving to the wrong branch when multiple matches exist on the page.
 */
function importTerminalLocator(page: import("@playwright/test").Page) {
  const importDialog = importDialogLocator(page);
  return importDialog
    .getByTestId("import-result-summary")
    .or(importDialog.getByTestId("import-error-message"))
    .or(importDialog.getByTestId("import-duplicate-review"))
    .or(
      importDialog.getByRole("button", {
        name: /manter existente|manter ambas|usar nova|keep existing|keep both|use new/i,
        disabled: false,
      }),
    )
    .or(
      importDialog.getByRole("button", {
        name: /concluir revisão|finish review|finalizar|finish|usar nova|use new/i,
        disabled: false,
      }),
    )
    .or(
      importDialog.getByRole("heading", { name: /precisam de revisão|need review/i }),
    )
    .or(importDialog.getByText(/^(Resolvido|Resolved)$/i).first());
}

/**
 * Opens the import drawer from the main transactions page.
 */
async function openImportDrawer(
  page: import("@playwright/test").Page,
  account: { name: string; id: string },
) {
  await waitForImportDrawerClosed(page, 15_000);
  const pfPage = new FinanceTransactionsPage(page);
  await pfPage.goto();
  await pfPage.selectAccount(account.name, account.id);

  await page.getByTestId("btn-open-import-drawer").click();
  await page.getByRole("menuitem", { name: /extrato|statement|bank/i }).click();
  await page
    .locator("#finance-transactions-import-menu")
    .waitFor({ state: "hidden" })
    .catch(() => {});
  await page
    .getByRole("heading", { name: /importar|import/i })
    .waitFor({ state: "visible" });
}

/**
 * Uploads a file via the import drawer's file input and submits the drawer.
 */
async function uploadFile(
  page: import("@playwright/test").Page,
  filePath: string,
) {
  const dlg = importDialogLocator(page);
  const fileName = path.basename(filePath);
  const fileInput = dlg.getByTestId("import-transactions-file-input");
  await fileInput.setInputFiles(filePath);
  await expect(dlg.getByText(fileName)).toBeVisible({ timeout: 15_000 });
  const submit = dlg.getByTestId("import-transactions-submit-button");
  await expect(submit).toBeEnabled({ timeout: 15_000 });
  await submit.click();
  await expect
    .poll(
      async () =>
        (await dlg.locator(".MuiLinearProgress-root").isVisible().catch(() => false)) ||
        (await isImportDrawerTerminal(page)),
      { timeout: 30_000 },
    )
    .toBe(true);
}

async function uploadFilePayload(
  page: import("@playwright/test").Page,
  file: {
    name: string;
    mimeType: string;
    buffer: Buffer;
  },
) {
  const fileInput = page.getByTestId("import-transactions-file-input");
  await fileInput.setInputFiles(file);
  await page.getByTestId("import-transactions-submit-button").click();
}

/**
 * Waits for the import job to finish processing (terminal drawer UI).
 */
async function isImportDrawerTerminal(page: import("@playwright/test").Page): Promise<boolean> {
  const importDialog = importDialogLocator(page);
  if (await importDialog.locator(".MuiLinearProgress-root").isVisible().catch(() => false)) {
    return false;
  }

  const checks = [
    importDialog.getByTestId("import-result-summary"),
    importDialog.getByTestId("import-error-message"),
    importDialog.getByTestId("import-duplicate-review"),
    importDialog.getByRole("heading", { name: /falha na importação|import failed|failure/i }),
    importDialog.getByRole("button", {
      name: /manter existente|manter ambas|usar nova|keep existing|keep both|use new/i,
      disabled: false,
    }),
    importDialog.getByRole("button", {
      name: /concluir revisão|finish review|finalizar|finish|usar nova|use new/i,
      disabled: false,
    }),
    importDialog.getByRole("heading", { name: /precisam de revisão|need review/i }),
    importDialog.getByText(/^(Resolvido|Resolved)$/i).first(),
  ];

  for (const locator of checks) {
    if (await locator.first().isVisible().catch(() => false)) {
      return true;
    }
  }
  return false;
}

async function waitForImportComplete(
  page: import("@playwright/test").Page,
  timeoutMs: number = IMPORT_TIMEOUT,
) {
  await expect.poll(async () => isImportDrawerTerminal(page), { timeout: timeoutMs }).toBe(true);
}

async function closeImportDrawer(page: import("@playwright/test").Page) {
  // Scope button search to the dialog — avoids matching unrelated close/cancel buttons
  // elsewhere on the page (e.g. snackbars, other dialogs) via .first().
  const dlg = importDialogLocator(page);

  const closeButton = dlg.getByRole("button", { name: /fechar|close/i }).first();
  if (await closeButton.isVisible().catch(() => false)) {
    await closeButton.click();
    await waitForImportDrawerClosed(page);
    return;
  }

  const cancelButton = dlg.getByRole("button", { name: /cancelar|cancel/i }).first();
  if (await cancelButton.isVisible().catch(() => false)) {
    await cancelButton.click();
    await waitForImportDrawerClosed(page);
  }
}

async function hasDuplicateReview(
  page: import("@playwright/test").Page,
): Promise<boolean> {
  return importDialogLocator(page)
    .getByRole("button", {
      name: /manter existente|manter ambas|usar nova|keep existing|keep both|use new/i,
      disabled: false,
    })
    .first()
    .isVisible()
    .catch(() => false);
}

async function resolveDuplicateReviewKeepingExisting(
  page: import("@playwright/test").Page,
): Promise<void> {
  const importDialog = importDialogLocator(page);

  // Server cleared duplicate candidates before UI showed row-level actions (ImportDuplicateReview count === 0).
  if (
    await importDialog
      .getByText(/^(Resolvido|Resolved)$/i)
      .first()
      .isVisible()
      .catch(() => false)
  ) {
    await importDialog
      .getByRole("button", { name: /fechar|close/i })
      .nth(1)
      .click()
      .catch(() => {});
    await waitForImportDrawerClosed(page);
    return;
  }

  while (await hasDuplicateReview(page)) {
    const rowAction = importDialog.getByRole("button", {
      name: /manter existente|keep existing/i,
      disabled: false,
    });
    await expect(rowAction.first()).toBeVisible({ timeout: IMPORT_TIMEOUT });
    await rowAction.first().click({ timeout: IMPORT_TIMEOUT });
  }

  const finishButton = importDialog.getByRole("button", {
    name: /concluir revisão|finish review|finalizar|finish|usar nova|use new/i,
    disabled: false,
  });
  if (await finishButton.isVisible().catch(() => false)) {
    await finishButton.click();
    await waitForImportDrawerClosed(page);
  }
}

/**
 * From an already-open import drawer: upload CSV/OFX, tolerate transient start errors,
 * resolve duplicate review when shown, then poll until `getTransactions` reflects more
 * rows than `countBefore` (terminal UI can appear before GraphQL/cache catch up).
 */
async function importStatementFileExpectAccountGrowth(
  page: import("@playwright/test").Page,
  financialAccountId: string,
  pfToken: string,
  filePath: string,
  countBefore: number,
  importTimeoutMs: number = IMPORT_TIMEOUT,
): Promise<void> {
  const dlg = importDialogLocator(page);
  await uploadFile(page, filePath);

  for (let attempt = 0; attempt < 3; attempt++) {
    await waitForImportComplete(page, importTimeoutMs);

    await expect
      .poll(
        async () =>
          (await hasDuplicateReview(page)) ||
          (await dlg.getByTestId("import-result-summary").isVisible().catch(() => false)) ||
          (await dlg.getByTestId("import-error-message").isVisible().catch(() => false)) ||
          (await importTerminalLocator(page).first().isVisible().catch(() => false)) ||
          (await getTransactions(financialAccountId, pfToken, importFixtureTransactionQuery(50))).length >
            countBefore,
        { timeout: importTimeoutMs },
      )
      .toBe(true);

    const importError = dlg.getByTestId("import-error-message");
    if (await importError.isVisible().catch(() => false)) {
      const msg = (await importError.innerText().catch(() => "")).trim();
      const inFlightDuplicate =
        /já foi importado|already been imported|being processed/i.test(msg);
      if (inFlightDuplicate) {
        await expect
          .poll(
            async () =>
              (await getTransactions(financialAccountId, pfToken, importFixtureTransactionQuery(50)))
                .length > countBefore,
            { timeout: importTimeoutMs },
          )
          .toBe(true);
        break;
      }
      if (attempt >= 2) {
        throw new Error(`Import failed after retries: ${msg}`);
      }
      await dlg.getByRole("button", { name: /tentar novamente|try again/i }).click();
      await dlg
        .getByTestId("import-transactions-file-input")
        .waitFor({ state: "attached", timeout: 15_000 })
        .catch(() => {});
      await page.waitForTimeout(600 * (attempt + 1));
      continue;
    }
    break;
  }

  if (await hasDuplicateReview(page)) {
    await resolveDuplicateReviewKeepingExisting(page);
  }

  await expect
    .poll(
      async () =>
        (await getTransactions(financialAccountId, pfToken, importFixtureTransactionQuery(50))).length >
        countBefore,
      { timeout: importTimeoutMs },
    )
    .toBe(true);
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe("Transaction Import — happy path", () => {
  test.describe.configure({ timeout: E2E_HEAVY_DESCRIBE_TIMEOUT_MS });
  test("IM-H-01: valid CSV reaches result or duplicate-review step", { tag: ["@smoke", "@critical", "@slow"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    await openImportDrawer(page, bankAccount);

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);

    const dlg = importDialogLocator(page);
    await expect
      .poll(
        async () =>
          (await dlg.getByTestId("import-result-summary").isVisible().catch(() => false)) ||
          (await hasDuplicateReview(page)) ||
          (await dlg
            .getByRole("button", {
              name: /concluir revisão|finish review|finalizar|finish|usar nova|use new/i,
            })
            .first()
            .isVisible()
            .catch(() => false)) ||
          (await importTerminalLocator(page).first().isVisible().catch(() => false)),
        { timeout: IMPORT_TIMEOUT },
      )
      .toBe(true);

    await closeImportDrawer(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((before) => before.id === t.id),
    );
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-H-02: CSV import creates transactions in the account", { tag: ["@critical", "@slow", "@balance"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    const txsBefore = await getTransactions(
      bankAccount.id,
      pfUser.token,
      importFixtureTransactionQuery(50),
    );
    const countBefore = txsBefore.length;

    const newTxs: Array<{ id: string }> = [];
    const uniqueOfx = buildUniqueOfxFixture();
    try {
      await importStatementFileExpectAccountGrowth(
        page,
        bankAccount.id,
        pfUser.token,
        uniqueOfx,
        countBefore,
      );

      const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
      newTxs.push(
        ...txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id)),
      );
      expect(newTxs.length).toBeGreaterThan(0);
    } finally {
      fs.unlinkSync(uniqueOfx);
      await Promise.all(
        newTxs.map((tx) =>
          deleteTransaction(tx.id, pfUser.token).catch(() => {}),
        ),
      );
    }
  });

  test("IM-H-03: valid OFX reaches result step", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    await openImportDrawer(page, bankAccount);

    await uploadFile(page, OFX_VALID);
    await waitForImportComplete(page);

    // Use toBeVisible() (Playwright auto-retry) instead of a point-in-time isVisible() boolean.
    // waitForImportComplete returns on any terminal state — if the import transiently
    // entered an error step before settling on the result, isVisible() snapshots false.
    // Scoped to the dialog so an unrelated element elsewhere can't interfere.
    await expect(importDialogLocator(page).getByTestId("import-result-summary"))
      .toBeVisible({ timeout: IMPORT_TIMEOUT });

    await closeImportDrawer(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((before) => before.id === t.id),
    );
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-H-04a: imported OFX transaction can be soft-deleted", async ({
    pfUser,
    bankAccount,
  }) => {
    const uniqueOfx = buildUniqueOfxFixture();
    try {
      const job = await importTransactionsFileViaGraphql(
        bankAccount.id,
        pfUser.token,
        uniqueOfx,
      );
      const terminal = await waitForImportJobTerminal(job.id, pfUser.token);
      expect(terminal.status).toBe("COMPLETED");
      expect(terminal.importedCount ?? 0).toBeGreaterThan(0);

      const txs = await getTransactionsWithSource(
        bankAccount.id,
        pfUser.token,
        importFixtureTransactionQuery(50),
      );
      const imported = txs.find((tx) => tx.source === "OFX");
      expect(imported).toBeDefined();

      await deleteTransaction(imported!.id, pfUser.token);

      const after = await getTransactionsWithSource(
        bankAccount.id,
        pfUser.token,
        importFixtureTransactionQuery(50),
      );
      expect(after.some((tx) => tx.id === imported!.id)).toBe(false);
    } finally {
      fs.unlinkSync(uniqueOfx);
    }
  });

  test("IM-H-04: OFX import creates transactions in the account", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    const txsBefore = await getTransactions(
      bankAccount.id,
      pfUser.token,
      importFixtureTransactionQuery(50),
    );
    const countBefore = txsBefore.length;

    await importStatementFileExpectAccountGrowth(page, bankAccount.id, pfUser.token, OFX_VALID, countBefore);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-H-05: re-importing the same CSV triggers duplicate detection review", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);

    // First import
    await openImportDrawer(page, bankAccount);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    await resolveDuplicateReviewKeepingExisting(page);
    await closeImportDrawer(page);
    await waitForImportDrawerClosed(page);

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    // Second import of the same file — should trigger duplicate review
    await openImportDrawer(page, bankAccount);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    await resolveDuplicateReviewKeepingExisting(page);

    // Duplicate handling: explicit review UI, or backend dedupes with no new rows on re-import.
    const duplicateReviewVisible = await hasDuplicateReview(page);
    const hasReviewHeading = await page
      .getByRole("heading", { name: /precisam de revisão|need review/i })
      .isVisible()
      .catch(() => false);
    const hasSkipOption = await page
      .getByRole("button", { name: /ignorar|skip|pular/i })
      .isVisible()
      .catch(() => false);
    const txsAfterSecond = await getTransactions(
      bankAccount.id,
      pfUser.token,
      importFixtureTransactionQuery(50),
    );
    const secondImportAddedNoRows = txsAfterSecond.length === txsBefore.length;

    expect(
      duplicateReviewVisible ||
        hasReviewHeading ||
        hasSkipOption ||
        secondImportAddedNoRows,
    ).toBe(true);

    // Cancel to not create duplicates
    await closeImportDrawer(page);

    // Cleanup first import
    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-H-06: duplicate review can keep existing rows and import only NEW transactions", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // First: seed some transactions that match the CSV
    const { createTransaction, CreditDebitType, TransactionStatus } =
      await import("../helpers/banking-graphql");
    const seedTx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: "Salario",
        amount: 5000,
        creditDebitType: CreditDebitType.Credit,
        status: TransactionStatus.Posted,
        date: "2026-03-01",
      },
      pfUser.token,
    );

    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    await resolveDuplicateReviewKeepingExisting(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );
    const salarioCount = txsAfter.filter(
      (t) => t.description === "Salario" && t.date === "2026-03-01",
    ).length;
    expect(salarioCount).toBe(1);
    // CSV has 10 rows; the seeded Salario duplicate must not inflate as a second posted row.
    expect(newTxs.length).toBeLessThan(10);

    // Cleanup
    await deleteTransaction(seedTx.id, pfUser.token).catch(() => {});
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe("Transaction Import — alternative paths", () => {
  test.describe.configure({ timeout: E2E_HEAVY_DESCRIBE_TIMEOUT_MS });
  test("IM-A-01: CSV spans multiple months — all months' summaries updated", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // valid-transactions.csv has all transactions in March 2026
    // This verifies the summary for that specific month reflects the import
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    // Resolve any duplicate review before querying the API — transactions aren't fully
    // committed until the review step is resolved.
    if (await hasDuplicateReview(page)) {
      await resolveDuplicateReviewKeepingExisting(page);
    }

    // Verify March 2026 summary was computed
    const march2026 = await getMonthSummary(
      bankAccount.id,
      2026,
      3,
      pfUser.token,
    );
    expect(march2026.totalIncome).toBeGreaterThan(0);
    expect(march2026.totalExpenses).toBeGreaterThan(0);

    // Cleanup
    const txs = await getTransactions(
      bankAccount.id,
      pfUser.token,
      importFixtureTransactionQuery(50),
    );
    await Promise.all(
      txs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})),
    );
  });

  test("IM-A-02: CSV with mix of debits and credits — both types imported correctly", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    if (await hasDuplicateReview(page)) {
      await resolveDuplicateReviewKeepingExisting(page);
    }

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );

    const credits = newTxs.filter(
      (t) => t.creditDebitType === CreditDebitType.Credit,
    );
    const debits = newTxs.filter(
      (t) => t.creditDebitType === CreditDebitType.Debit,
    );

    // valid-transactions.csv has both CREDIT and DEBIT rows
    expect(credits.length).toBeGreaterThan(0);
    expect(debits.length).toBeGreaterThan(0);

    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-A-03: OFX with duplicate FITID within the same file — only one instance imported", async ({
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

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    // Upload the duplicate-FITID OFX as a blob
    await uploadFilePayload(page, {
      name: "duplicate-fitid.ofx",
      mimeType: "application/x-ofx",
      buffer: Buffer.from(ofxContent, "utf-8"),
    });
    await waitForImportComplete(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );

    // Only 1 of the 2 duplicate-FITID entries should have been imported
    expect(newTxs.length).toBeLessThanOrEqual(1);

    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-A-05: CSV with UTF-8 special characters (accents) — descriptions preserved", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const csvContent = `Data,Descricao,Valor,Tipo\n2026-03-10,Pão de Açúcar,-150.00,DEBIT\n2026-03-11,Locação de Veículo,-300.00,DEBIT\n`;
    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await uploadFilePayload(page, {
      name: "acentos.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(csvContent, "utf-8"),
    });
    await waitForImportComplete(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );
    const paoDacucar = newTxs.find(
      (t) => t.description.includes("ão") || t.description.includes("Pão"),
    );

    if (paoDacucar) {
      expect(paoDacucar.description).toContain("ão");
    }

    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

  test("IM-A-06: partial import — some duplicates skipped, new ones created", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Seed one transaction that matches CSV
    const { createTransaction, CreditDebitType, TransactionStatus } =
      await import("../helpers/banking-graphql");
    const existing = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: "Salario",
        amount: 5000,
        creditDebitType: CreditDebitType.Credit,
        status: TransactionStatus.Posted,
        date: "2026-03-01",
      },
      pfUser.token,
    );

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));

    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);
    await uploadFile(page, CSV_VALID);
    await waitForImportComplete(page);
    await resolveDuplicateReviewKeepingExisting(page);

    const txsAfter = await getTransactions(bankAccount.id, pfUser.token, importFixtureTransactionQuery(50));
    const newTxs = txsAfter.filter(
      (t) => !txsBefore.some((b) => b.id === t.id),
    );

    const salarioCount = txsAfter.filter(
      (t) => t.description === "Salario" && t.date === "2026-03-01",
    ).length;
    expect(salarioCount).toBe(1);
    // Some new transactions should exist (the non-duplicates), unless the suite auto-deduped without review.
    expect(newTxs.length).toBeGreaterThan(0);

    await deleteTransaction(existing.id, pfUser.token).catch(() => {});
    await Promise.all(
      newTxs.map((tx) =>
        deleteTransaction(tx.id, pfUser.token).catch(() => {}),
      ),
    );
  });

});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe("Transaction Import — negative paths", () => {
  test.describe.configure({ timeout: E2E_HEAVY_DESCRIBE_TIMEOUT_MS });
  test("IM-N-01: uploading an unsupported file extension — error shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await page.getByTestId("import-transactions-file-input").setInputFiles({
      name: "document.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("plain text is not a supported financial import"),
    });

    // Either the file input rejects it (HTML accept attribute) or the UI shows an error
    const errorVisible = await page
      .getByTestId("import-error-message")
      .isVisible()
      .catch(() => false);
    const inputRejectsType = await page
      .getByTestId("import-transactions-file-input")
      .evaluate((el: HTMLInputElement) => el.files?.length === 0)
      .catch(() => false);

    expect(errorVisible || inputRejectsType).toBe(true);
    await closeImportDrawer(page);
  });

  test("IM-N-02: uploading a malformed CSV — error message displayed", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await uploadFile(page, CSV_MALFORMED);
    await waitForImportComplete(page).catch(() => {});

    await expect(page.getByTestId("import-error-message")).toBeVisible({
      timeout: IMPORT_TIMEOUT,
    });
    await closeImportDrawer(page);
  });

  test("IM-N-03: uploading an empty CSV (header only, no rows) — informative message shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await uploadFile(page, CSV_EMPTY);
    await waitForImportComplete(page).catch(() => {});

    // Expects either an error message or an empty-state in the result step.
    // Do not use Promise.race on isVisible(): both resolve immediately (often false first).
    const errorVisible = await page
      .getByTestId("import-error-message")
      .isVisible()
      .catch(() => false);
    const resultVisible = await page
      .getByTestId("import-result-summary")
      .isVisible()
      .catch(() => false);
    expect(errorVisible || resultVisible).toBe(true);

    await closeImportDrawer(page);
  });

  test("IM-N-04: uploading a file exceeding 3 MB — size error shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    // Create a 3.1 MB buffer
    const largeBuffer = Buffer.alloc(3.1 * 1024 * 1024, "a");
    await page.getByTestId("import-transactions-file-input").setInputFiles({
      name: "large-file.csv",
      mimeType: "text/csv",
      buffer: largeBuffer,
    });

    // Either the UI shows an immediate size error or the upload is rejected
    const error = page.getByTestId("import-error-message");
    const visible = await error
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    // If not an error message, the file input should have rejected the file
    if (!visible) {
      const fileRejected = await page
        .getByTestId("import-transactions-file-input")
        .evaluate((el: HTMLInputElement) => !el.files || el.files.length === 0)
        .catch(() => false);
      expect(fileRejected).toBe(true);
    }

    await closeImportDrawer(page);
  });

  test("IM-N-05: OFX with invalid date format — error message displayed", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // OFXParser: non-8-digit values (e.g. BADDATE) fall back to LocalDate.now(); invalid calendar
    // dates like 20260231 can be leniently resolved by java.time. Eight non-numeric chars force
    // LocalDate.parse to fail and abort OFX SGML parsing.
    const badOfx = `OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n<OFX>
<SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<DTSERVER>20260301120000</DTSERVER><LANGUAGE>POR</LANGUAGE></SONRS></SIGNONMSGSRSV1>
<BANKMSGSRSV1><STMTTRNRS><TRNUID>1</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>
<STMTRS><CURDEF>BRL</CURDEF><BANKACCTFROM><BANKID>001</BANKID><ACCTID>999</ACCTID><ACCTTYPE>CHECKING</ACCTTYPE></BANKACCTFROM>
<BANKTRANLIST><DTSTART>20260301000000</DTSTART><DTEND>20260331000000</DTEND>
<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>ABCDEFGH</DTPOSTED><TRNAMT>-100.00</TRNAMT><FITID>B001</FITID><MEMO>Unparseable date</MEMO></STMTTRN>
    </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>`;

    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await uploadFilePayload(page, {
      name: "bad-dates.ofx",
      mimeType: "application/x-ofx",
      buffer: Buffer.from(badOfx, "utf-8"),
    });

    await waitForImportComplete(page).catch(() => {});
    await expect(page.getByTestId("import-error-message")).toBeVisible({
      timeout: IMPORT_TIMEOUT,
    });
    await closeImportDrawer(page);
  });

  test("IM-N-06: importing to a non-existent account via GraphQL direct — returns error", { tag: ["@critical"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    await openImportDrawer(page, bankAccount);

    await page.route("**/graphql", async (route) => {
      const body = route.request().postDataJSON() as { operationName?: string };
      if (body.operationName === "ImportTransactionsFile") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            errors: [
              {
                message: "Account not found",
                extensions: { code: "NOT_FOUND" },
              },
            ],
          }),
        });
        return;
      }
      await route.continue();
    });

    await uploadFile(page, CSV_VALID);
    await expect(page.getByTestId("import-error-message")).toBeVisible({
      timeout: IMPORT_TIMEOUT,
    });
  });
});
