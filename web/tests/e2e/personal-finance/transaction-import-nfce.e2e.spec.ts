import { test as basePfTest, expect } from "../fixtures/personal-finance";
import {
  createFinancialAccount,
  createTransaction,
  deleteTransaction,
  getTransactions,
  CreditDebitType,
  TransactionStatus,
} from "../helpers/banking-graphql";
import { executeGraphQL } from "../helpers/graphql";
import { nfceTxQuery } from "../helpers/finance-transactions-e2e";
import {
  E2E_HEAVY_DESCRIBE_TIMEOUT_MS,
  E2E_PENDING_ACTION_POLL_MS,
} from "../helpers/timeouts";

const DEFAULT_NFCE_QR_URL =
  "https://www.nfce.fazenda.sp.gov.br/qrcode?p=35260447508411273985651030000996671971986307|3|1";
const DEFAULT_NFCE_ACCESS_KEY = "35260447508411273985651030000996671971986307";

const E2E_NFCE_QR_URL = process.env.E2E_NFCE_QR_URL ?? DEFAULT_NFCE_QR_URL;
const E2E_NFCE_ACCESS_KEY =
  process.env.E2E_NFCE_ACCESS_KEY ?? DEFAULT_NFCE_ACCESS_KEY;

function uniqueBankAccountName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

const test = basePfTest.extend({
  bankAccount: async ({ pfUser }, provideAccount) => {
    const account = await createFinancialAccount(
      {
        name: uniqueBankAccountName("Conta Corrente E2E NFCe"),
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

test.describe.configure({ mode: "serial", timeout: E2E_HEAVY_DESCRIBE_TIMEOUT_MS });

interface ImportNfceResult {
  id: string;
  status: string;
  importedCount: number | null;
  skippedCount: number | null;
  completedAt: string | null;
  dateFrom: string | null;
  dateTo: string | null;
}

async function importNfceViaGraphql(
  financialAccountId: string,
  qrCodeUrl: string,
  token: string,
): Promise<ImportNfceResult> {
  const result = await executeGraphQL<{
    importNfce: {
      import: ImportNfceResult;
    };
  }>(
    `
      mutation ImportNfce($financialAccountId: ID!, $qrCodeUrl: String!) {
        importNfce(financialAccountId: $financialAccountId, qrCodeUrl: $qrCodeUrl) {
          import {
            id
            status
            importedCount
            skippedCount
            completedAt
            dateFrom
            dateTo
          }
        }
      }
    `,
    { financialAccountId, qrCodeUrl },
    token,
  );

  expect(result.errors).toBeUndefined();
  expect(result.data?.importNfce.import.id).toBeTruthy();
  return result.data!.importNfce.import;
}

async function listTransactionImports(
  financialAccountId: string,
  token: string,
): Promise<Array<{ id: string; status: string; source: string }>> {
  const result = await executeGraphQL<{
    transactionImports: Array<{ id: string; status: string; source: string }>;
  }>(
    `
      query TransactionImports($financialAccountId: ID) {
        transactionImports(financialAccountId: $financialAccountId) {
          id
          status
          source
        }
      }
    `,
    { financialAccountId },
    token,
  );
  expect(result.errors).toBeUndefined();
  return result.data?.transactionImports ?? [];
}

test.describe("Transaction Import NFC-e — happy/alternative/edge/negative", () => {
  test.beforeEach(async () => {
    test.skip(
      !E2E_NFCE_QR_URL || !E2E_NFCE_ACCESS_KEY,
      "Set E2E_NFCE_QR_URL and E2E_NFCE_ACCESS_KEY to run NFC-e e2e scenarios (defaults are already configured).",
    );
  });

  test("IM-NFCE-H-01: happy path imports NFC-e and creates one transaction", async ({
    pfUser,
    bankAccount,
  }) => {
    const existing = await getTransactions(bankAccount.id, pfUser.token, { first: 100, year: 2026, month: 4 });
    await Promise.all(
      existing.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})),
    );

    const txsBefore = await getTransactions(bankAccount.id, pfUser.token, { first: 100, year: 2026, month: 4 });
    const result = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(result.status).toBe("COMPLETED");
    expect(result.importedCount ?? 0).toBeGreaterThanOrEqual(1);

    const txQuery = nfceTxQuery({ dateFrom: result.dateFrom });

    let newTxs: Awaited<ReturnType<typeof getTransactions>> = [];
    await expect
      .poll(
        async () => {
          const txsAfter = await getTransactions(bankAccount.id, pfUser.token, txQuery);
          newTxs = txsAfter.filter((t) => !txsBefore.some((b) => b.id === t.id));
          return newTxs.length;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(1);

    await Promise.all(newTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test("IM-NFCE-A-01: re-import with alive transaction short-circuits to existing import", async ({
    pfUser,
    bankAccount,
  }) => {
    const first = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    const second = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(second.id).toBe(first.id);

    const txs = await getTransactions(bankAccount.id, pfUser.token, nfceTxQuery({ dateFrom: first.dateFrom }));
    await Promise.all(txs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test("IM-NFCE-E-01: re-import after deletion allows fresh import again", async ({
    pfUser,
    bankAccount,
  }) => {
    const txQuery = nfceTxQuery();

    const txsBeforeFirst = await getTransactions(bankAccount.id, pfUser.token, txQuery);
    const first = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(first.status).toBe("COMPLETED");

    let firstNewTxs: Awaited<ReturnType<typeof getTransactions>> = [];
    await expect
      .poll(
        async () => {
          const txsAfterFirst = await getTransactions(bankAccount.id, pfUser.token, txQuery);
          firstNewTxs = txsAfterFirst.filter((t) => !txsBeforeFirst.some((b) => b.id === t.id));
          return firstNewTxs.length;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(1);

    await deleteTransaction(firstNewTxs[0]!.id, pfUser.token);

    const txsBeforeSecond = await getTransactions(bankAccount.id, pfUser.token, txQuery);
    const second = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(second.status).toBe("COMPLETED");
    expect(second.importedCount ?? 0).toBeGreaterThanOrEqual(1);

    let secondNewTxs: Awaited<ReturnType<typeof getTransactions>> = [];
    await expect
      .poll(
        async () => {
          const txsAfterSecond = await getTransactions(bankAccount.id, pfUser.token, txQuery);
          secondNewTxs = txsAfterSecond.filter((t) => !txsBeforeSecond.some((b) => b.id === t.id));
          return secondNewTxs.length;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBe(1);

    const imports = await listTransactionImports(bankAccount.id, pfUser.token);
    const nfceImports = imports.filter((imp) => imp.source === "NFC_E");
    expect(nfceImports.length).toBeGreaterThan(0);

    await Promise.all(secondNewTxs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test("IM-NFCE-E-02: re-import with merged/alive transaction remains deduped", async ({
    pfUser,
    bankAccount,
  }) => {
    const first = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(first.status).toBe("COMPLETED");
    const txQuery = nfceTxQuery({ dateFrom: first.dateFrom });

    let txs: Awaited<ReturnType<typeof getTransactions>> = [];
    await expect
      .poll(
        async () => {
          txs = await getTransactions(bankAccount.id, pfUser.token, txQuery);
          return txs.length;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBeGreaterThan(0);

    const second = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(second.id).toBe(first.id);

    const after = await getTransactions(bankAccount.id, pfUser.token, txQuery);
    expect(after.length).toBe(txs.length);

    await Promise.all(after.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test("IM-NFCE-E-03: manual candidate + original deleted still allows linking on re-import", async ({
    pfUser,
    bankAccount,
  }) => {
    const first = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(first.status).toBe("COMPLETED");

    const txQuery = nfceTxQuery({ dateFrom: first.dateFrom });
    let imported: Awaited<ReturnType<typeof getTransactions>> = [];
    await expect
      .poll(
        async () => {
          imported = await getTransactions(bankAccount.id, pfUser.token, txQuery);
          return imported.length;
        },
        { timeout: E2E_PENDING_ACTION_POLL_MS },
      )
      .toBeGreaterThan(0);
    const importedTx = imported[0];
    expect(importedTx).toBeDefined();
    await deleteTransaction(importedTx!.id, pfUser.token);

    const manualSeed = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: "NFCe manual candidate",
        amount: importedTx!.amount,
        creditDebitType: importedTx!.creditDebitType ?? CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: importedTx!.date,
      },
      pfUser.token,
    );

    const second = await importNfceViaGraphql(bankAccount.id, E2E_NFCE_QR_URL!, pfUser.token);
    expect(["REVIEW_REQUIRED", "COMPLETED"]).toContain(second.status);

    await deleteTransaction(manualSeed.id, pfUser.token).catch(() => {});
    const txs = await getTransactions(bankAccount.id, pfUser.token, { first: 100 });
    await Promise.all(txs.map((tx) => deleteTransaction(tx.id, pfUser.token).catch(() => {})));
  });

  test("IM-NFCE-N-01: invalid qrCodeUrl is rejected with validation/business error", async ({
    pfUser,
    bankAccount,
  }) => {
    const result = await executeGraphQL<{
      importNfce: {
        import: { id: string };
      };
    }>(
      `
        mutation ImportNfce($financialAccountId: ID!, $qrCodeUrl: String!) {
          importNfce(financialAccountId: $financialAccountId, qrCodeUrl: $qrCodeUrl) {
            import {
              id
            }
          }
        }
      `,
      {
        financialAccountId: bankAccount.id,
        qrCodeUrl: "not-a-valid-url:::",
      },
      pfUser.token,
    );

    expect(result.errors).toBeDefined();
    const errBlob = JSON.stringify(result.errors);
    expect(/invalid|validation|url|nfce|nfc-e/i.test(errBlob)).toBe(true);
  });

  test("IM-NFCE-N-02: cannot import NFC-e into another tenant account", async ({
    twoTenants,
  }) => {
    const { userA, userB } = twoTenants;
    const accountA = await createFinancialAccount(
      {
        name: `Conta NFCe E2E ${Date.now()}`,
        type: "BANK",
        subtype: "CONTA_CORRENTE",
      },
      userA.token,
    );

    const result = await executeGraphQL(
      `
        mutation ImportNfce($financialAccountId: ID!, $qrCodeUrl: String!) {
          importNfce(financialAccountId: $financialAccountId, qrCodeUrl: $qrCodeUrl) {
            import {
              id
              status
            }
          }
        }
      `,
      {
        financialAccountId: accountA.id,
        qrCodeUrl: E2E_NFCE_QR_URL,
      },
      userB.token,
    );

    expect(result.errors).toBeDefined();
    const blob = JSON.stringify(result.errors);
    expect(/not found|resource_not_found|invalid input/i.test(blob)).toBe(true);
  });
});
