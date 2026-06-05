import fs from "fs";
import path from "path";
import { test as basePfTest, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import {
  createFinancialAccount,
  getTransactions,
} from "../helpers/banking-graphql";
import { executeGraphQL } from "../helpers/graphql";
import { signIntoPage } from "../helpers/navigation";
import { E2E_HEAVY_DESCRIBE_TIMEOUT_MS } from "../helpers/timeouts";

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

type ImportJobSnapshot = {
  id: string;
  status: string;
  importedCount: number | null;
};

const test = basePfTest.extend({
  bankAccount: async ({ pfUser }, provideAccount) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName("Conta Corrente E2E Regras"),
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

const CSV_VALID = path.join(__dirname, "../fixtures/files/valid-transactions.csv");

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
  expect(result.data?.importTransactionsFile.import.id).toBeTruthy();
  return result.data!.importTransactionsFile.import;
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
        return latest?.status;
      },
      { timeout: timeoutMs },
    )
    .toMatch(/COMPLETED|FAILED|REVIEW_REQUIRED/);

  return latest!;
}

test.describe("Transaction rules — historical apply", () => {
  test.describe.configure({ mode: "serial" });

  test("TR-H-01: creating an exact rule prompts to apply it to matching imported transactions and updates history", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const importJob = await importTransactionsFileViaGraphql(
      bankAccount.id,
      pfUser.token,
      CSV_VALID,
    );
    const terminalJob = await waitForImportJobTerminal(importJob.id, pfUser.token);

    expect(terminalJob.status).toBe("COMPLETED");

    await expect
      .poll(
        async () => {
          const marchTransactions = await getTransactions(bankAccount.id, pfUser.token, {
            year: 2026,
            month: 3,
            first: 100,
          });
          return marchTransactions.some((tx) => tx.description === "Uber");
        },
        { timeout: 30_000 },
      )
      .toBe(true);

    await signIntoPage(page);
    const financePage = new FinanceTransactionsPage(page);
    await financePage.goto();
    await financePage.selectAccount(bankAccount.name, bankAccount.id);

    await page.goto("/personal-finance/finance-transactions/transaction-rules");
    await expect(
      page.getByRole("button", { name: /nova regra|new rule/i }),
    ).toBeVisible();

    await page.getByRole("button", { name: /nova regra|new rule/i }).click();
    const ruleDrawer = page.getByRole("dialog", { name: /nova regra|new rule/i });
    await expect(ruleDrawer).toBeVisible();

    await ruleDrawer.getByRole("combobox").first().click();
    await page.getByRole("option", { name: /exato|exact/i }).click();
    await ruleDrawer.getByRole("textbox", { name: /texto comparativo|comparison text/i }).fill("UBER");
    await ruleDrawer.getByRole("textbox", { name: /nova descrição|new description/i }).fill("Uber Corrida E2E");
    await ruleDrawer.getByRole("button", { name: /salvar regra|save rule/i }).click();

    const applyDialog = page.getByTestId("apply-rule-to-history-dialog");
    await expect(applyDialog).toBeVisible();
    await expect(applyDialog).toContainText("1");
    await applyDialog.getByTestId("apply-rule-to-history-confirm").click();

    await expect(applyDialog).toBeHidden({ timeout: 15_000 });

    await expect
      .poll(
        async () => {
          const marchTransactions = await getTransactions(bankAccount.id, pfUser.token, {
            year: 2026,
            month: 3,
            first: 100,
          });
          return marchTransactions.some((tx) => tx.description === "Uber Corrida E2E");
        },
        { timeout: 30_000 },
      )
      .toBe(true);
  });
});
