import { test, expect } from '../fixtures/personal-finance';
import type { Page, Route } from '@playwright/test';
import { CreditDebitType, getTransactions } from '../helpers/banking-graphql';
import { executeGraphQL } from '../helpers/graphql';
import { signIntoPage } from '../helpers/navigation';

type WishlistItemInput = {
  name: string;
  estimatedPrice?: number | null;
  currencyCode?: string;
  categoryId?: string | null;
  priority?: 'HIGH' | 'MEDIUM' | 'LOW';
  notes?: string | null;
  targetDate?: string | null;
  productUrl?: string | null;
  productImageUrl?: string | null;
};

function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now()} ${Math.random().toString(36).slice(2, 6)}`;
}

async function createWishlistItem(token: string, input: WishlistItemInput): Promise<{ id: string }> {
  const result = await executeGraphQL<{
    createWishlistItem: { id: string };
  }>(
    `
      mutation CreateWishlistItemForE2E($input: CreateWishlistItemInput!) {
        createWishlistItem(input: $input) {
          id
        }
      }
    `,
    {
      input: {
        currencyCode: 'BRL',
        priority: 'MEDIUM',
        ...input,
      },
    },
    token,
  );

  if (result.errors || !result.data?.createWishlistItem.id) {
    throw new Error(`Failed to create wishlist item: ${JSON.stringify(result.errors)}`);
  }

  return result.data.createWishlistItem;
}

async function listExpenseCategories(token: string): Promise<Array<{ id: string; name: string }>> {
  const result = await executeGraphQL<{
    transactionCategories: Array<{ id: string; name: string; applicableTo: 'EXPENSE' | 'INCOME' | 'ANY' | 'TRANSFER' }>;
  }>(
    `
      query WishlistE2ECategories {
        transactionCategories {
          id
          name
          applicableTo
        }
      }
    `,
    undefined,
    token,
  );

  const categories = result.data?.transactionCategories ?? [];
  return categories
    .filter((category) => category.applicableTo === 'EXPENSE' || category.applicableTo === 'ANY')
    .map((category) => ({ id: category.id, name: category.name }));
}

async function waitForWishlistReady(page: Page): Promise<void> {
  const itemButton = page.getByRole('button', { name: /^Item$/ });
  const retryButton = page.getByRole('button', { name: /tentar novamente|try again/i });

  await expect
    .poll(
      async () => {
        if (await itemButton.isVisible().catch(() => false)) {
          return 'ready';
        }

        if (await retryButton.first().isVisible().catch(() => false)) {
          await retryButton.first().click().catch(() => {});
          return 'retrying';
        }

        return 'loading';
      },
      { timeout: 30_000 },
    )
    .toBe('ready');

  await expect(page.getByRole('tab', { name: /Ativos \(\d+\)/ })).toBeVisible({ timeout: 30_000 });
}

async function gotoWishlist(page: Page): Promise<void> {
  const onPurchaseWishlist = /\/personal-finance\/purchase-wishlist(?:\/|$)/.test(page.url());
  if (!onPurchaseWishlist) {
    await signIntoPage(page);
    await page.goto('/personal-finance/purchase-wishlist', { waitUntil: 'domcontentloaded' });
  } else {
    await page.goto('/personal-finance/purchase-wishlist', { waitUntil: 'domcontentloaded' });
  }

  await waitForWishlistReady(page);
}

function wishlistRow(page: Page, name: string) {
  return page.locator('.MuiPaper-root').filter({ has: page.getByText(name, { exact: true }) }).first();
}

function purchaseDialogAction(page: Page, label: string) {
  return page.locator('button').filter({ has: page.getByText(label, { exact: true }) }).first();
}

test.describe('Purchase wishlist', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ page }) => {
    await page.unrouteAll({ behavior: 'ignoreErrors' });
  });

  test.afterEach(async ({ page }) => {
    await page.keyboard.press('Escape').catch(() => {});
    await page.unrouteAll({ behavior: 'ignoreErrors' });
  });

  test('creates an item from URL preview and persists grid view preference on refresh', async ({ page }) => {
    const itemName = uniqueName('Nintendo Switch 2');

    const previewRouteHandler = async (route: Route) => {
      const postData = route.request().postData();
      let body: { operationName?: string } | null = null;
      try {
        body = postData ? JSON.parse(postData) : null;
      } catch {
        await route.continue();
        return;
      }

      if (body?.operationName === 'PurchaseWishlistProductPreview') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              wishlistProductMetadataPreview: {
                name: itemName,
                estimatedPrice: 3999.9,
                currencyCode: 'BRL',
                imageUrl: 'https://cdn.example.com/switch-2.png',
              },
            },
          }),
        });
        return;
      }

      await route.continue();
    };

    await page.route('**/graphql', previewRouteHandler);

    try {
      await gotoWishlist(page);
      await page.getByRole('button', { name: /^Item$/ }).click();

      await page.getByLabel('URL do produto').fill('https://shop.example.com/switch-2');
      await expect(page.getByLabel('Nome')).toHaveValue(itemName, { timeout: 15_000 });
      await expect(page.getByLabel('Preço estimado')).toHaveValue(/3\.999,90|3999,90|3999.90/, { timeout: 15_000 });
      await expect(page.locator('img[alt="' + itemName + '"]')).toBeVisible();

      await page.getByRole('button', { name: 'Salvar' }).click();
      await expect(wishlistRow(page, itemName)).toBeVisible();

      await page.getByRole('button', { name: 'Visualização em grade' }).click();
      await expect
        .poll(() => page.evaluate(() => window.localStorage.getItem('personal-finance.purchase-wishlist.view-mode')))
        .toBe('grid');

      await page.reload();
      await expect
        .poll(() => page.evaluate(() => window.localStorage.getItem('personal-finance.purchase-wishlist.view-mode')))
        .toBe('grid');
      await expect(page.getByRole('button', { name: 'Visualização em grade' })).toHaveAttribute('aria-pressed', 'true');
      await expect(wishlistRow(page, itemName)).toBeVisible();
    } finally {
      await page.unroute('**/graphql', previewRouteHandler);
    }
  });

  test('supports inline category editing in the grid and simple purchase flow', async ({ page, pfUser }) => {
    const itemName = uniqueName('Office Chair');
    await createWishlistItem(pfUser.token, {
      name: itemName,
      estimatedPrice: 899.9,
      productUrl: 'https://shop.example.com/office-chair',
    });
    const [firstExpenseCategory] = await listExpenseCategories(pfUser.token);
    expect(firstExpenseCategory).toBeDefined();

    await gotoWishlist(page);
    await page.getByRole('button', { name: 'Visualização em grade' }).click();

    const row = wishlistRow(page, itemName);
    await expect(row).toBeVisible();
    await row.getByText('Sem categoria').click();
    await page.getByRole('button', { name: new RegExp(firstExpenseCategory!.name, 'i') }).click();
    await expect(row.getByText(firstExpenseCategory!.name, { exact: true })).toBeVisible();

    await row.locator('button:has(svg[data-testid="TaskAltIcon"])').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await purchaseDialogAction(page, 'Marcar como comprado').click();

    await expect(page.getByRole('tab', { name: /Histórico \(\d+\)/ })).toBeVisible();
    await expect(wishlistRow(page, itemName)).toBeVisible();
  });

  test('marks an item as purchased and creates a transaction from the drawer with prefilled values', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const itemName = uniqueName('PlayStation 5');
    await createWishlistItem(pfUser.token, {
      name: itemName,
      estimatedPrice: 3499.9,
      productUrl: 'https://shop.example.com/playstation-5',
    });

    await gotoWishlist(page);
    const row = wishlistRow(page, itemName);
    await expect(row).toBeVisible();

    await row.locator('button:has(svg[data-testid="TaskAltIcon"])').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await purchaseDialogAction(page, 'Marcar como comprado e criar movimentação').click();

    const transactionDrawer = page.getByTestId('transaction-drawer');
    await expect(transactionDrawer).toBeVisible();
    await expect(page.getByRole('heading', { name: /criar movimentação/i })).toBeVisible();
    await expect(transactionDrawer.getByTestId('tx-credit-debit-type-select')).toHaveCount(0);
    await expect(transactionDrawer.getByTestId('tx-credit-debit-type-carousel')).toHaveCount(0);
    await expect(transactionDrawer.getByTestId('tx-description-input')).toHaveValue(itemName);
    await expect(transactionDrawer.getByTestId('tx-amount-input')).toHaveValue(/3\.499,90|3499,90|3499.90/);

    const saveButton = page.getByTestId('tx-save-btn');
    await expect(saveButton).toBeEnabled({ timeout: 15_000 });

    const purchaseResponsePromise = page.waitForResponse(
      (response) => {
        const request = response.request();
        if (request.method() !== 'POST' || !/graphql/i.test(request.url())) {
          return false;
        }

        const body = request.postData() ?? '';
        return body.includes('PurchaseWishlistItemWithTransaction') && body.includes('mutation');
      },
      { timeout: 30_000 },
    );

    await saveButton.click();
    const purchaseResponse = await purchaseResponsePromise;
    if (!purchaseResponse.ok()) {
      throw new Error(`PurchaseWishlistItemWithTransaction HTTP ${purchaseResponse.status()}`);
    }

    const purchasePayload = (await purchaseResponse.json()) as { errors?: { message?: string }[] };
    if (purchasePayload.errors?.length) {
      throw new Error(
        `PurchaseWishlistItemWithTransaction GraphQL: ${purchasePayload.errors.map((error) => error.message ?? JSON.stringify(error)).join('; ')}`,
      );
    }

    await expect(page.getByRole('heading', { name: /criar movimentação/i })).not.toBeVisible({ timeout: 30_000 });

    await expect(page.getByRole('tab', { name: /Histórico \(\d+\)/ })).toBeVisible({ timeout: 30_000 });
    await expect(wishlistRow(page, itemName)).toBeVisible();

    const transactions = await getTransactions(bankAccount.id, pfUser.token);
    expect(transactions.some((transaction) => transaction.description === itemName && transaction.creditDebitType === CreditDebitType.Debit)).toBe(true);
  });
});
