import { test, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import { InlineTransactionForm } from "../pages/personal-finance/InlineTransactionForm";
import { executeGraphQL } from "../helpers/graphql";
import { signIntoPage } from "../helpers/navigation";

function unique(value: string): string {
  return `${value} ${Date.now()}`;
}

test.describe("Merchants", () => {
  test.describe.configure({ mode: "serial" });
  test.setTimeout(180_000);
  test.skip(({ isMobile }) => isMobile, "Merchant picker e2e currently targets desktop layouts.");

  test("creates a merchant from picker and persists normalized website domain", { tag: ["@critical"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const merchantName = unique("Merchant E2E");
    const txDescription = unique("Compra merchant");

    await signIntoPage(page);
    const financePage = new FinanceTransactionsPage(page);
    await financePage.goto();
    await financePage.selectAccount(bankAccount.name, bankAccount.id);
    await financePage.openCreateTransactionForm();

    const form = new InlineTransactionForm(page);
    await form.waitForOpen();
    await form.createMerchant(merchantName, "https://www.netflix.com/br/plans");
    await expect(page.getByTestId("merchant-select-trigger")).toContainText(merchantName);
    expect.soft(await page.locator(".MuiChip-root").count()).toBeGreaterThan(0);
    await page.waitForTimeout(300);

    const descriptionInput = page.getByTestId("tx-description-input");
    await descriptionInput.click();
    await descriptionInput.fill(txDescription);
    await expect(descriptionInput).toHaveValue(txDescription);

    const amountInput = page.getByTestId("tx-amount-input");
    await amountInput.click();
    await amountInput.fill("79");
    await amountInput.press("Tab");
    await expect
      .poll(async () => ((await amountInput.inputValue()) ?? "").trim().length > 0, {
        timeout: 10_000,
      })
      .toBe(true);

    await form.save();
    await form.waitForClose();
    await financePage.transactionTable.expectTransactionVisible(txDescription);

    const result = await executeGraphQL<{
      transactions: {
        edges: Array<{
          node: {
            id: string;
            description: string;
            merchant: {
              id: string;
              name: string;
              domain: string | null;
            } | null;
          };
        }>;
      };
    }>(
      `
        query Transactions($financialAccountId: ID, $search: String, $first: Int) {
          transactions(financialAccountId: $financialAccountId, search: $search, first: $first) {
            edges {
              node {
                id
                description
                merchant {
                  id
                  name
                  domain
                }
              }
            }
          }
        }
      `,
      {
        financialAccountId: bankAccount.id,
        search: txDescription,
        first: 20,
      },
      pfUser.token,
    );

    expect(result.errors).toBeUndefined();
    const persisted = result.data?.transactions.edges.find((edge) => edge.node.description === txDescription)?.node;
    expect(persisted).toBeDefined();
    expect(persisted?.merchant?.name).toBe(merchantName);
    expect(persisted?.merchant?.domain).toBe("netflix.com");
  });
});
