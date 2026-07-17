/**
 * Account membership / invitation E2E coverage for GH-312.
 *
 * Real API (PERSONAL accounts created by signup):
 * - owner sees members table with self; no "Leave this account"
 * - accountMembers GraphQL returns the owner row
 * - inviteMember hits MEMBER_LIMIT_REACHED on PERSONAL
 * - cross-tenant accountMembers is denied
 *
 * Browser UI (mocked GraphQL where live data cannot exercise the journey):
 * - invite dialog + toast on PERSONAL limit
 * - client-side member search empty state
 * - client-side pagination across mocked members
 * - accept invitation: missing token, unauth redirect, success, error
 *
 * Gaps (not coverable from Playwright alone):
 * - live invite→email→accept: invitation token is email-only; createAccount
 *   FAMILY/BUSINESS is not on the public GraphQL schema
 */

import { expect } from "@playwright/test";
import { test } from "./fixtures/personal-finance";
import { executeGraphQL } from "./helpers/graphql";
import { signIntoPage } from "./helpers/navigation";
import {
  buildMockMembers,
  detectAccountMembershipFeatureSupport,
  getAccountMembers,
  getMyAccounts,
  inviteMember,
  membershipUi,
  mockGraphQLOperation,
  graphqlIncludes,
  type AccountMembershipFeatureSupport,
} from "./helpers/account-membership-e2e";

test.describe("Account membership (profile + invitations)", () => {
  let support: AccountMembershipFeatureSupport = {
    hasAccountMembers: false,
    hasInviteMember: false,
    hasAcceptInvitation: false,
    hasLeaveAccount: false,
  };

  test.beforeAll(async () => {
    support = await detectAccountMembershipFeatureSupport();
  });

  test.beforeEach(() => {
    test.skip(
      !support.hasAccountMembers || !support.hasInviteMember || !support.hasAcceptInvitation,
      "GraphQL runtime does not expose account membership operations yet.",
    );
  });

  test("owner sees members section with self and without leave action", async ({
    page,
    pfUser,
  }) => {
    const accounts = await getMyAccounts(pfUser.token);
    const ownerAccount = accounts.find((account) => account.isOwner);
    expect(ownerAccount).toBeDefined();

    const members = await getAccountMembers(pfUser.token, ownerAccount!.id);
    expect(members.length).toBeGreaterThanOrEqual(1);
    expect(members.some((member) => member.isCurrentUser && member.role === "OWNER")).toBe(true);

    await signIntoPage(page);
    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    const membersPaper = page.locator(membershipUi.membersPaper);
    await expect(page.getByText(membershipUi.membersTitle)).toBeVisible({ timeout: 20_000 });
    await expect(membersPaper).toBeVisible();

    const table = membersPaper.getByRole("table", {
      name: /Account members table|Tabela de membros da conta/i,
    });
    await expect(table).toBeVisible();
    await expect(table.getByRole("row").filter({ hasText: pfUser.email })).toBeVisible();
    await expect(table.getByText(/^(Owner|Proprietário)$/)).toBeVisible();
    await expect(table.getByText(/^(Active|Ativo)$/)).toBeVisible();

    await expect(page.getByRole("button", { name: membershipUi.leaveButton })).toHaveCount(0);
    await expect(page.getByRole("button", { name: membershipUi.inviteButton })).toBeVisible();
  });

  test("inviteMember on PERSONAL account returns MEMBER_LIMIT_REACHED", async ({ pfUser }) => {
    const accounts = await getMyAccounts(pfUser.token);
    const ownerAccount = accounts.find((account) => account.isOwner);
    expect(ownerAccount).toBeDefined();
    expect(ownerAccount!.accountType).toBe("PERSONAL");

    const result = await inviteMember(pfUser.token, {
      accountId: ownerAccount!.id,
      email: `invite-e2e-${Date.now()}@example.com`,
      role: "MEMBER",
    });

    expect(result.errors).toBeDefined();
    const blob = JSON.stringify(result.errors);
    expect(blob).toMatch(/MEMBER_LIMIT_REACHED|member limit|limite de membros/i);
  });

  test("invite dialog surfaces member-limit error toast", async ({ page, pfUser }) => {
    await signIntoPage(page);
    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByRole("button", { name: membershipUi.inviteButton })).toBeVisible({
      timeout: 20_000,
    });
    await page.getByRole("button", { name: membershipUi.inviteButton }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText(membershipUi.inviteDialogTitle)).toBeVisible();
    // Invite role is fixed to MEMBER — no role dropdown in the dialog.
    await expect(dialog.getByTestId("invite-member-role-select")).toHaveCount(0);

    await dialog.getByLabel(/^(Email|E-mail)$/).fill(`invite-ui-${Date.now()}@example.com`);
    await dialog.getByRole("button", { name: membershipUi.inviteButton }).click();

    await expect(page.getByRole("alert").filter({ hasText: /member limit|limite de membros/i })).toBeVisible({
      timeout: 15_000,
    });
  });

  test("other tenant cannot list account members", async ({ twoTenants }) => {
    const accountsA = await getMyAccounts(twoTenants.userA.token);
    const ownerAccountA = accountsA.find((account) => account.isOwner);
    expect(ownerAccountA).toBeDefined();

    const listResult = await executeGraphQL(
      `
        query AccountMembers($accountId: ID!) {
          accountMembers(accountId: $accountId) {
            membershipId
          }
        }
      `,
      { accountId: ownerAccountA!.id },
      twoTenants.userB.token,
    );

    expect(listResult.errors).toBeDefined();
    expect(JSON.stringify(listResult.errors)).toMatch(
      /NOT_ACCOUNT_MEMBER|no active membership|forbidden|denied|permission|unauthorized|insufficient/i,
    );
  });

  test("member search filters client-side and shows empty state", async ({ page }) => {
    await mockGraphQLOperation(
      page,
      (query) => graphqlIncludes(query, "AccountMembers") || graphqlIncludes(query, "accountMembers"),
      (postData) => {
        const accountId = String(postData.variables?.accountId ?? "account-mock");
        return {
          data: {
            accountMembers: buildMockMembers(accountId, 3),
          },
        };
      },
    );

    await signIntoPage(page);
    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    const membersPaper = page.locator(membershipUi.membersPaper);
    await expect(membersPaper).toBeVisible({ timeout: 20_000 });
    await expect(membersPaper.getByText("member-2@example.com")).toBeVisible();

    const search = page.getByPlaceholder(/Search by name or email|Pesquisar por nome ou e-mail/i);
    await search.click();
    await search.fill("zzz-no-match");

    await expect(membersPaper.getByText(membershipUi.searchEmpty)).toBeVisible();
    await expect(membersPaper.getByText("member-2@example.com")).toHaveCount(0);
  });

  test("members table paginates when more than page size", async ({ page }) => {
    await mockGraphQLOperation(
      page,
      (query) => graphqlIncludes(query, "AccountMembers") || graphqlIncludes(query, "accountMembers"),
      (postData) => {
        const accountId = String(postData.variables?.accountId ?? "account-mock");
        return {
          data: {
            accountMembers: buildMockMembers(accountId, 7),
          },
        };
      },
    );

    await signIntoPage(page);
    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    const membersPaper = page.locator(membershipUi.membersPaper);
    await expect(membersPaper).toBeVisible({ timeout: 20_000 });

    // Default page size is 5 — first page shows owner + members 2-5 (emails member-2..5);
    // member-6/7 are on page 2. buildMockMembers: index 0 owner, 1..6 => member-2..member-7 emails.
    await expect(membersPaper.getByText("member-2@example.com")).toBeVisible();
    await expect(membersPaper.getByText("member-6@example.com")).toHaveCount(0);

    const pagination = membersPaper.getByRole("navigation");
    await expect(pagination).toBeVisible();
    await pagination.getByRole("button", { name: /2/ }).click();

    await expect(membersPaper.getByText("member-6@example.com")).toBeVisible();
    await expect(membersPaper.getByText("member-2@example.com")).toHaveCount(0);
  });

  test("accept invitation page shows error when token is missing", async ({ page }) => {
    await signIntoPage(page);
    await page.goto("/invitations/accept");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByRole("heading", { name: membershipUi.acceptErrorTitle })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(membershipUi.invalidToken)).toBeVisible();
  });

  test("accept invitation redirects unauthenticated users to signin with next", async ({
    browser,
  }) => {
    const context = await browser.newContext({ storageState: undefined });
    const page = await context.newPage();

    await page.goto("/invitations/accept?token=e2e-pending-token");
    await page.waitForURL(/\/signin\?/, { timeout: 20_000 });

    const url = new URL(page.url());
    expect(url.pathname).toBe("/signin");
    expect(url.searchParams.get("next")).toBe("/invitations/accept?token=e2e-pending-token");

    await context.close();
  });

  test("accept invitation shows success when mutation succeeds", async ({ page }) => {
    await mockGraphQLOperation(
      page,
      (query) => graphqlIncludes(query, "AcceptInvitation") || graphqlIncludes(query, "acceptInvitation"),
      {
        data: {
          acceptInvitation: {
            membershipId: "membership-accepted",
            accountId: "account-accepted",
            accountName: "Family E2E",
          },
        },
      },
    );

    await signIntoPage(page);
    await page.goto("/invitations/accept?token=e2e-valid-mock-token");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByRole("heading", { name: membershipUi.acceptSuccessTitle })).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.getByTestId("stack").getByRole("alert").filter({ hasText: /Family E2E|acesso a Family E2E/i }),
    ).toBeVisible();
  });

  test("accept invitation shows error when mutation fails", async ({ page }) => {
    await mockGraphQLOperation(
      page,
      (query) => graphqlIncludes(query, "AcceptInvitation") || graphqlIncludes(query, "acceptInvitation"),
      {
        errors: [
          {
            message: "Invitation token is invalid or expired",
            extensions: { code: "INVITATION_INVALID" },
          },
        ],
      },
    );

    await signIntoPage(page);
    await page.goto("/invitations/accept?token=e2e-invalid-mock-token");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByRole("heading", { name: membershipUi.acceptErrorTitle })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByTestId("stack").getByRole("alert")).toBeVisible();
  });
});
