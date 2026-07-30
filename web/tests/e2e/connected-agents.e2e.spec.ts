import { expect, test, type Page } from "@playwright/test";
import { buildAuthStorageState } from "./helpers/storage-state";
import type { AuthSession } from "./helpers/graphql";

const connectedAgentsUi = {
  title: /Connected agents|Agentes conectados/i,
  empty: /No connected agents found|Nenhum agente conectado encontrado/i,
  revokeAction: /Revoke access|Revogar acesso/i,
};

const mockedSession: AuthSession = {
  token: "e2e-mocked-token",
  refreshToken: "e2e-mocked-refresh-token",
  user: {
    id: "user-owner-1",
    email: "owner@example.com",
    displayName: "Owner User",
    roles: [],
    permissions: [],
  },
};

type MockAccount = {
  id: string;
  accountName: string;
  accountType: string;
  accountStatus: string;
  isOwner: boolean;
};

type ConnectedAgent = ReturnType<typeof buildConnectedAgent>;

type GraphQLPostData = {
  operationName?: string;
  query?: string;
  variables?: Record<string, unknown>;
};

function buildConnectedAgent(index = 1) {
  return {
    connectionId: `connection-${index}`,
    clientId: `client-${index}`,
    clientName: `Agent ${index}`,
    connectedAt: "2026-07-30T10:00:00.000Z",
    lastGrantedAt: "2026-07-30T12:00:00.000Z",
    scopes: [
      {
        name: "finance:transactions:read",
        description: "Read transactions",
      },
    ],
    recentActivity: [
      {
        id: `activity-${index}`,
        occurredAt: "2026-07-30T12:05:00.000Z",
        toolName: "finance.transactions.list",
        outcome: "SUCCESS",
        errorCode: null,
        errorMessage: null,
        latencyMs: 42,
      },
    ],
  };
}

function matchesOperation(postData: GraphQLPostData, operationName: string, fieldName: string): boolean {
  if (postData.operationName === operationName) {
    return true;
  }
  const query = postData.query ?? "";
  // Prefer the operation definition to avoid substring collisions
  // (e.g. "userPreferences" inside "bankingUserPreferences").
  return (
    query.includes(`query ${operationName}`) ||
    query.includes(`mutation ${operationName}`) ||
    new RegExp(`\\b${fieldName}\\s*[({]`).test(query)
  );
}

/**
 * Single GraphQL route handler for the profile page.
 * Stacked mockGraphQLOperation + route.fallback() lets unmatched ops hit the real API
 * with the fake storage-state token → Apollo refresh → /signin.
 */
async function mockProfileGraphQL(
  page: Page,
  options: {
    myAccounts: MockAccount[];
    getConnectedAgents: () => ConnectedAgent[];
    onRevoke?: (connectionId: string) => void;
  },
): Promise<void> {
  await page.route("**/graphql", async (route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }

    let postData: GraphQLPostData = {};
    try {
      postData = (route.request().postDataJSON() as GraphQLPostData) ?? {};
    } catch {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          errors: [{ message: "Invalid GraphQL payload", extensions: { code: "MOCK_INVALID_PAYLOAD" } }],
        }),
      });
      return;
    }

    const fulfill = async (body: Record<string, unknown>) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(body),
      });
    };

    if (matchesOperation(postData, "RevokeConnectedAgent", "revokeConnectedAgent")) {
      const connectionId = String(postData.variables?.connectionId ?? "");
      const agents = options.getConnectedAgents();
      const revoked = agents.find((agent) => agent.connectionId === connectionId);
      options.onRevoke?.(connectionId);
      await fulfill({
        data: {
          revokeConnectedAgent: {
            connectionId,
            clientId: revoked?.clientId ?? "client-1",
            accountId: options.myAccounts[0]?.id ?? "account-owner",
            revokedAt: "2026-07-30T12:06:00.000Z",
          },
        },
      });
      return;
    }

    if (matchesOperation(postData, "ConnectedAgents", "connectedAgents")) {
      await fulfill({
        data: {
          connectedAgents: options.getConnectedAgents(),
        },
      });
      return;
    }

    if (matchesOperation(postData, "CurrentUser", "currentUser")) {
      await fulfill({
        data: {
          currentUser: {
            id: mockedSession.user.id,
            email: mockedSession.user.email,
            displayName: mockedSession.user.displayName,
            avatarUrl: null,
            avatarProvider: null,
            roles: [],
            permissions: [],
          },
        },
      });
      return;
    }

    // Banking before UserPreferences: field name is a suffix of bankingUserPreferences.
    if (matchesOperation(postData, "BankingUserPreferences", "bankingUserPreferences")) {
      await fulfill({
        data: {
          bankingUserPreferences: {
            ruleCreationMode: "ALWAYS_PROMPT",
          },
        },
      });
      return;
    }

    if (matchesOperation(postData, "UserPreferences", "userPreferences")) {
      await fulfill({
        data: {
          userPreferences: {
            language: "ENGLISH",
            theme: "SYSTEM",
            blurBalances: false,
            firstDayOfWeek: 1,
            keepMeConnected: true,
          },
        },
      });
      return;
    }

    if (matchesOperation(postData, "AccountPreferences", "accountPreferences")) {
      await fulfill({
        data: {
          accountPreferences: {
            defaultTransactionStatus: "POSTED",
          },
        },
      });
      return;
    }

    if (matchesOperation(postData, "MyAccounts", "myAccounts")) {
      await fulfill({
        data: {
          myAccounts: options.myAccounts,
        },
      });
      return;
    }

    if (matchesOperation(postData, "AccountMembers", "accountMembers")) {
      const accountId = String(postData.variables?.accountId ?? options.myAccounts[0]?.id ?? "account-mock");
      const owner = options.myAccounts.find((account) => account.isOwner) ?? options.myAccounts[0];
      await fulfill({
        data: {
          accountMembers: owner
            ? [
                {
                  membershipId: "membership-owner",
                  accountId,
                  userId: mockedSession.user.id,
                  email: mockedSession.user.email,
                  displayName: mockedSession.user.displayName,
                  avatarUrl: null,
                  role: "OWNER",
                  status: "ACTIVE",
                  isDefault: true,
                  isCurrentUser: true,
                },
              ]
            : [],
        },
      });
      return;
    }

    if (matchesOperation(postData, "RefreshAccessToken", "refreshAccessToken")) {
      await fulfill({
        data: {
          refreshAccessToken: {
            token: mockedSession.token,
            refreshToken: mockedSession.refreshToken,
            user: mockedSession.user,
          },
        },
      });
      return;
    }

    // Never fall through to the real API with a fake token (that redirects to /signin).
    await fulfill({
      errors: [
        {
          message: `Unmocked GraphQL operation: ${postData.operationName ?? "unknown"}`,
          extensions: { code: "MOCK_MISSING" },
        },
      ],
    });
  });
}

test.describe("Connected agents (profile)", () => {
  test("owner can revoke a connected agent from the profile page", async ({ browser }) => {
    let connectedAgents = [buildConnectedAgent(1)];
    const context = await browser.newContext({ storageState: buildAuthStorageState(mockedSession) });
    const page = await context.newPage();

    await mockProfileGraphQL(page, {
      myAccounts: [
        {
          id: "account-owner",
          accountName: "Owner account",
          accountType: "BUSINESS",
          accountStatus: "ACTIVE",
          isOwner: true,
        },
      ],
      getConnectedAgents: () => connectedAgents,
      onRevoke: (connectionId) => {
        connectedAgents = connectedAgents.filter((agent) => agent.connectionId !== connectionId);
      },
    });

    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByRole("heading", { name: connectedAgentsUi.title })).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByTestId("connected-agent-row-connection-1")).toBeVisible();

    await page.getByTestId("revoke-connected-agent-connection-1").click();
    await page
      .getByTestId("revoke-connected-agent-confirm-dialog")
      .getByRole("button", { name: connectedAgentsUi.revokeAction })
      .click();

    const section = page.getByTestId("connected-agents-section");
    await expect(page).toHaveURL(/\/profile/);
    await expect(page.getByTestId("connected-agent-row-connection-1")).toHaveCount(0);
    await expect(section.getByText(connectedAgentsUi.empty)).toBeVisible();

    await context.close();
  });

  test("non-owner users do not see the connected agents section", async ({ browser }) => {
    const context = await browser.newContext({ storageState: buildAuthStorageState(mockedSession) });
    const page = await context.newPage();

    await mockProfileGraphQL(page, {
      myAccounts: [
        {
          id: "account-shared",
          accountName: "Shared account",
          accountType: "BUSINESS",
          accountStatus: "ACTIVE",
          isOwner: false,
        },
      ],
      getConnectedAgents: () => [],
    });

    await page.goto("/profile");
    await page.waitForLoadState("domcontentloaded");

    await expect(page.getByTestId("connected-agents-section")).toHaveCount(0);
    await expect(page.getByRole("heading", { name: connectedAgentsUi.title })).toHaveCount(0);

    await context.close();
  });
});
