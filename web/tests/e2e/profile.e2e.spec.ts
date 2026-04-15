import { test, expect } from "./fixtures/personal-finance";
import { executeGraphQL } from "./helpers/graphql";
import { currentConfig } from "./config/environments";

type AccountItem = {
  id: string;
  accountName: string;
  isOwner: boolean;
};

type ProfileFeatureSupport = {
  hasMyAccounts: boolean;
  hasUpdateAccount: boolean;
  hasKeepMeConnected: boolean;
};

async function detectProfileFeatureSupport(): Promise<ProfileFeatureSupport> {
  const fallback: ProfileFeatureSupport = {
    hasMyAccounts: false,
    hasUpdateAccount: false,
    hasKeepMeConnected: false,
  };

  try {
    const response = await fetch(currentConfig.graphqlEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        query: `
          query ProfileFeatureContract {
            queryType: __type(name: "Query") {
              fields {
                name
              }
            }
            mutationType: __type(name: "Mutation") {
              fields {
                name
              }
            }
            userPreferencesType: __type(name: "UserPreferences") {
              fields {
                name
              }
            }
          }
        `,
      }),
    });

    if (!response.ok) {
      return fallback;
    }

    const payload = (await response.json()) as {
      data?: {
        queryType?: { fields?: Array<{ name?: string | null }> | null } | null;
        mutationType?: { fields?: Array<{ name?: string | null }> | null } | null;
        userPreferencesType?: { fields?: Array<{ name?: string | null }> | null } | null;
      };
    };

    const queryFields = new Set(
      (payload.data?.queryType?.fields ?? [])
        .map((field) => field.name ?? "")
        .filter((name): name is string => Boolean(name)),
    );
    const mutationFields = new Set(
      (payload.data?.mutationType?.fields ?? [])
        .map((field) => field.name ?? "")
        .filter((name): name is string => Boolean(name)),
    );
    const userPreferencesFields = new Set(
      (payload.data?.userPreferencesType?.fields ?? [])
        .map((field) => field.name ?? "")
        .filter((name): name is string => Boolean(name)),
    );

    return {
      hasMyAccounts: queryFields.has("myAccounts"),
      hasUpdateAccount: mutationFields.has("updateAccount"),
      hasKeepMeConnected:
        mutationFields.has("updateUserPreferences") &&
        userPreferencesFields.has("keepMeConnected"),
    };
  } catch {
    return fallback;
  }
}

async function getMyAccounts(token: string): Promise<AccountItem[]> {
  const result = await executeGraphQL<{
    myAccounts: AccountItem[];
  }>(
    `
      query MyAccounts {
        myAccounts {
          id
          accountName
          isOwner
        }
      }
    `,
    {},
    token,
  );

  expect(result.errors).toBeUndefined();
  return result.data?.myAccounts ?? [];
}

test.describe("Profile Features", () => {
  let support: ProfileFeatureSupport = {
    hasMyAccounts: false,
    hasUpdateAccount: false,
    hasKeepMeConnected: false,
  };

  test.beforeAll(async () => {
    support = await detectProfileFeatureSupport();
  });

  test("owner can update account name via updateAccount", async ({ pfUser }) => {
    test.skip(
      !support.hasMyAccounts || !support.hasUpdateAccount,
      "GraphQL runtime does not expose myAccounts/updateAccount yet.",
    );

    const accounts = await getMyAccounts(pfUser.token);
    const ownerAccount = accounts.find((account) => account.isOwner);

    expect(ownerAccount).toBeDefined();

    const renamed = `Conta E2E ${Date.now()}`;
    const updateResult = await executeGraphQL<{
      updateAccount: AccountItem;
    }>(
      `
        mutation UpdateAccount($accountId: ID!, $input: UpdateAccountInput!) {
          updateAccount(accountId: $accountId, input: $input) {
            id
            accountName
            isOwner
          }
        }
      `,
      {
        accountId: ownerAccount!.id,
        input: { accountName: renamed },
      },
      pfUser.token,
    );

    expect(updateResult.errors).toBeUndefined();
    expect(updateResult.data?.updateAccount.accountName).toBe(renamed);
    expect(updateResult.data?.updateAccount.isOwner).toBe(true);
  });

  test("non-owner cannot update another user's account", async ({ twoTenants }) => {
    test.skip(
      !support.hasMyAccounts || !support.hasUpdateAccount,
      "GraphQL runtime does not expose myAccounts/updateAccount yet.",
    );

    const accountsA = await getMyAccounts(twoTenants.userA.token);
    const ownerAccountA = accountsA.find((account) => account.isOwner);
    expect(ownerAccountA).toBeDefined();

    const result = await executeGraphQL(
      `
        mutation UpdateAccount($accountId: ID!, $input: UpdateAccountInput!) {
          updateAccount(accountId: $accountId, input: $input) {
            id
            accountName
            isOwner
          }
        }
      `,
      {
        accountId: ownerAccountA!.id,
        input: { accountName: `Hacked ${Date.now()}` },
      },
      twoTenants.userB.token,
    );

    expect(result.errors).toBeDefined();
    expect(result.errors![0].message).toMatch(
      /role|owner|forbidden|denied|permission|allow/i,
    );
  });

  test("signIn uses keepMeConnected when rememberMe is omitted", async ({ pfUser }) => {
    test.skip(
      !support.hasKeepMeConnected,
      "GraphQL runtime does not expose keepMeConnected preferences yet.",
    );

    const setTrue = await executeGraphQL(
      `
        mutation SetKeepConnected($input: UpdateUserPreferencesInput!) {
          updateUserPreferences(input: $input) {
            keepMeConnected
          }
        }
      `,
      {
        input: {
          keepMeConnected: true,
        },
      },
      pfUser.token,
    );
    expect(setTrue.errors).toBeUndefined();
    expect(setTrue.data?.updateUserPreferences.keepMeConnected).toBe(true);

    const signInTrue = await executeGraphQL<{
      signIn: { token: string; refreshToken: string | null };
    }>(
      `
        mutation SignIn($input: SignInInput!) {
          signIn(input: $input) {
            token
            refreshToken
          }
        }
      `,
      {
        input: {
          email: pfUser.email,
          password: pfUser.password,
        },
      },
    );
    expect(signInTrue.errors).toBeUndefined();
    expect(signInTrue.data?.signIn.token).toBeTruthy();
    expect(signInTrue.data?.signIn.refreshToken).toBeTruthy();

    const setFalse = await executeGraphQL(
      `
        mutation SetKeepConnected($input: UpdateUserPreferencesInput!) {
          updateUserPreferences(input: $input) {
            keepMeConnected
          }
        }
      `,
      {
        input: {
          keepMeConnected: false,
        },
      },
      pfUser.token,
    );
    expect(setFalse.errors).toBeUndefined();
    expect(setFalse.data?.updateUserPreferences.keepMeConnected).toBe(false);

    const signInFalse = await executeGraphQL<{
      signIn: { token: string; refreshToken: string | null };
    }>(
      `
        mutation SignIn($input: SignInInput!) {
          signIn(input: $input) {
            token
            refreshToken
          }
        }
      `,
      {
        input: {
          email: pfUser.email,
          password: pfUser.password,
        },
      },
    );
    expect(signInFalse.errors).toBeUndefined();
    expect(signInFalse.data?.signIn.token).toBeTruthy();
    expect(signInFalse.data?.signIn.refreshToken).toBeNull();
  });

  test("updateUserAvatar accepts non-whitelisted avatar URL", async ({ pfUser }) => {
    const untrustedUrl = `https://evil.example.com/avatar-e2e-${Date.now()}.png`;
    const mutationResult = await executeGraphQL<{
      updateUserAvatar: { id: string; avatarUrl: string | null };
    }>(
      `
        mutation UpdateUserAvatar($input: UpdateUserAvatarInput!) {
          updateUserAvatar(input: $input) {
            id
            avatarUrl
          }
        }
      `,
      {
        input: {
          avatarUrl: untrustedUrl,
        },
      },
      pfUser.token,
    );

    expect(mutationResult.errors).toBeUndefined();
    expect(mutationResult.data?.updateUserAvatar.id).toBe(pfUser.id);
    expect(mutationResult.data?.updateUserAvatar.avatarUrl).toBe(untrustedUrl);

    const queryResult = await executeGraphQL<{
      currentUser: { id: string; avatarUrl: string | null };
    }>(
      `
        query CurrentUserAvatar {
          currentUser {
            id
            avatarUrl
          }
        }
      `,
      {},
      pfUser.token,
    );

    expect(queryResult.errors).toBeUndefined();
    expect(queryResult.data?.currentUser.id).toBe(pfUser.id);
    expect(queryResult.data?.currentUser.avatarUrl).toBe(untrustedUrl);
  });
});
