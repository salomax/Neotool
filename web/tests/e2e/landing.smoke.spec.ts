import { expect, test } from "@playwright/test";

test.describe("Landing page smoke", () => {
  test("renders all showcase CTAs and stays within the mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");

    const ctas = [
      { testId: "personal-finance-cta", href: "/personal-finance" },
      { testId: "personal-finance-automation-cta", href: "/personal-finance/finance-transactions" },
      { testId: "recurring-payments-cta", href: "/personal-finance/recurring-payments" },
      { testId: "wishlist-cta", href: "/personal-finance/purchase-wishlist" },
      { testId: "institutions-cta", href: "/financial-data/bacen-institutions" },
      { testId: "indicators-cta", href: "/financial-data/national-indicators" },
      { testId: "fixed-income-comparison-cta", href: "/investments/fixed-income-comparison" },
    ];

    for (const cta of ctas) {
      const link = page.getByTestId(cta.testId);
      await expect(link).toBeVisible();
      await expect(link).toHaveAttribute("href", cta.href);
    }

    const hasHorizontalOverflow = await page.evaluate(() => {
      const doc = document.documentElement;
      return doc.scrollWidth > window.innerWidth;
    });

    expect(hasHorizontalOverflow).toBe(false);
  });
});
