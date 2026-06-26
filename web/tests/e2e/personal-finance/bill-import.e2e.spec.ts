import { test, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import { createFinancialAccount } from "../helpers/banking-graphql";

test.describe.configure({ mode: "serial" });

function uniqueCardName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

async function openBillImportDrawer(
  page: import("@playwright/test").Page,
  account: { id: string; name: string },
): Promise<void> {
  const pfPage = new FinanceTransactionsPage(page);
  await pfPage.goto();
  await pfPage.selectAccount(account.name, account.id);

  await page.getByTestId("btn-open-import-drawer").click();
  await page.getByRole("menuitem", { name: /fatura|card bill/i }).click();
  await expect(page.getByRole("heading", { name: /importar fatura|import statement/i })).toBeVisible();
}

test("BI-H-01: flat assignment review omits empty last-four bucket after reassignment", async ({
  page,
  pfUser,
}) => {
  const multiCardAccount = await createFinancialAccount(
    {
      name: uniqueCardName("Cartão múltiplo E2E"),
      type: "CREDIT",
      subtype: "CREDIT_CARD",
      issuerName: "Banco E2E Bill Import",
      cardData: {
        closingDay: 7,
        dueDay: 15,
        isMultiCard: true,
      },
    },
    pfUser.token,
  );

  const mockedJobId = `bill-import-job-${Date.now()}`;
  await page.route("**/graphql", async (route) => {
    const request = route.request();
    if (request.method() !== "POST" || !/graphql/i.test(request.url())) {
      await route.continue();
      return;
    }

    const rawBody = request.postData();
    if (!rawBody) {
      await route.continue();
      return;
    }

    let operation: { operationName?: string; query?: string } | null = null;
    try {
      operation = JSON.parse(rawBody) as { operationName?: string; query?: string };
    } catch {
      await route.continue();
      return;
    }

    const operationName = typeof operation?.operationName === "string" ? operation.operationName : "";
    const query = typeof operation?.query === "string" ? operation.query : "";

    if (operationName === "SubmitBillImport" || query.includes("submitBillImport")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            submitBillImport: {
              __typename: "BillImportJob",
              id: mockedJobId,
              status: "EXTRACTING",
              fileName: "fatura.csv",
              createdAt: "2026-06-24T12:00:00.000Z",
              errorMessage: null,
              errorCode: null,
              errorParameters: null,
            },
          },
        }),
      });
      return;
    }

    if (operationName === "BillImportJob" || query.includes("billImportJob")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            billImportJob: {
              __typename: "BillImportJob",
              id: mockedJobId,
              status: "AWAITING_REVIEW",
              fileName: "fatura.csv",
              createdAt: "2026-06-24T12:00:00.000Z",
              errorMessage: null,
              errorCode: null,
              errorParameters: null,
              reviewPayload: {
                __typename: "BillImportReview",
                bankName: "Banco E2E Bill Import",
                reviewMode: "TX_CARD_ASSIGNMENT",
                statementPeriod: {
                  __typename: "DateRange",
                  from: "2026-06-01",
                  to: "2026-06-30",
                },
                cards: [
                  {
                    __typename: "BillImportReviewCard",
                    reviewCardId: "review-card-1",
                    lastFourDigits: null,
                    holderName: null,
                    computedTotal: 60,
                    declaredTotal: 60,
                    totalMatchesDeclared: true,
                    matchedFinancialAccountId: null,
                    matchAmbiguous: false,
                    closingDate: "2026-06-07",
                    dueDate: "2026-06-15",
                    matchCandidates: [],
                    transactions: [
                      {
                        __typename: "BillImportReviewTransaction",
                        date: "2026-06-03",
                        description: "Uber BI-H-01",
                        amount: 20,
                        creditDebitType: "DEBIT",
                        isBillPayment: false,
                        categoryId: null,
                        categorySource: null,
                        installment: null,
                      },
                      {
                        __typename: "BillImportReviewTransaction",
                        date: "2026-06-04",
                        description: "iFood BI-H-01",
                        amount: 40,
                        creditDebitType: "DEBIT",
                        isBillPayment: false,
                        categoryId: null,
                        categorySource: null,
                        installment: null,
                      },
                    ],
                  },
                ],
              },
            },
          },
        }),
      });
      return;
    }

    await route.continue();
  });

  await openBillImportDrawer(page, multiCardAccount);

  await page.locator('input[type="file"]').setInputFiles({
    name: "fatura.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("date,description,amount\n2026-06-03,Uber BI-H-01,20\n"),
  });
  await expect(page.getByText("fatura.csv")).toBeVisible();
  await page.getByRole("button", { name: /processar fatura|upload/i }).click();

  await expect(page.getByRole("heading", { name: /associar cartões aos lançamentos|assign cards to entries/i })).toBeVisible();

  const lastFourInput = page.getByLabel(/final \(4 dígitos\)|last 4/i);
  const applyButton = page.getByRole("button", { name: /aplicar|apply/i });
  const reviewButton = page.getByRole("button", { name: /revisar transações|review transactions/i });

  const uberRowCheckbox = page
    .getByText("Uber BI-H-01")
    .locator("xpath=ancestor::li[1]")
    .locator('input[type="checkbox"]');
  await uberRowCheckbox.check();
  await lastFourInput.fill("1234");
  await applyButton.click();

  const ifoodRowCheckbox = page
    .getByText("iFood BI-H-01")
    .locator("xpath=ancestor::li[1]")
    .locator('input[type="checkbox"]');
  await ifoodRowCheckbox.check();
  await lastFourInput.fill("5678");
  await applyButton.click();

  await reviewButton.click();
  await expect(page.getByRole("heading", { name: /revisar transações|review transactions/i })).toBeVisible();
  await expect(page.getByText("•••• 1234")).toBeVisible();
  await expect(page.getByText("•••• 5678")).toBeVisible();

  await page.getByRole("button", { name: /voltar|back/i }).click();
  await expect(page.getByRole("heading", { name: /associar cartões aos lançamentos|assign cards to entries/i })).toBeVisible();

  await ifoodRowCheckbox.check();
  await lastFourInput.fill("1234");
  await applyButton.click();

  await reviewButton.click();
  await expect(page.getByText("•••• 1234")).toBeVisible();
  await expect(page.getByText("•••• 5678")).toHaveCount(0);
  await expect(page.getByText("Uber BI-H-01")).toBeVisible();
  await expect(page.getByText("iFood BI-H-01")).toBeVisible();
});

test.describe("Bill import — mobile viewport", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("BI-M-01: mobile bill import drawer keeps upload actions in footer", async ({
    page,
    pfUser,
  }) => {
    const cardAccount = await createFinancialAccount(
      {
        name: uniqueCardName("Cartão mobile E2E"),
        type: "CREDIT",
        subtype: "CREDIT_CARD",
        issuerName: "Banco E2E Bill Import Mobile",
        cardData: {
          closingDay: 7,
          dueDay: 15,
        },
      },
      pfUser.token,
    );

    await openBillImportDrawer(page, cardAccount);

    await page.locator('input[type="file"]').setInputFiles({
      name: "fatura-mobile.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("date,description,amount\n2026-06-03,Cafe mobile,12\n"),
    });

    await expect(page.getByText("fatura-mobile.csv")).toBeVisible();

    const submit = page.getByTestId("bill-import-submit-button");
    await expect(submit).toBeVisible();
    await expect(submit).toBeEnabled();

    const overflow = await page.evaluate(() => ({
      body: document.body.scrollWidth > document.body.clientWidth,
      dialog: (() => {
        const el = document.querySelector('[role="dialog"]');
        return el ? el.scrollWidth > el.clientWidth : false;
      })(),
    }));
    expect(overflow.body).toBe(false);
    expect(overflow.dialog).toBe(false);

    await page.getByTestId("bill-import-cancel-button").click();
    await expect(page.getByRole("heading", { name: /importar fatura|import statement/i })).toBeHidden();
  });
});
