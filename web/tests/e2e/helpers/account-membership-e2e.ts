/**
 * GraphQL helpers and GraphQL-route mocks for account membership E2E.
 *
 * Full invite→accept against a live API is not possible from the browser alone:
 * - `inviteMember` does not return the invitation token (email-only)
 * - `createAccount` (FAMILY/BUSINESS) is not exposed on the public GraphQL schema
 *   and PERSONAL accounts reject invites (`MEMBER_LIMIT_REACHED`)
 */

import type { Page, Route } from "@playwright/test";
import { executeGraphQL } from "./graphql";

export type AccountMemberRow = {
  membershipId: string;
  accountId: string;
  userId: string | null;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";
  status: "PENDING" | "ACTIVE" | "REMOVED";
  isDefault: boolean;
  isCurrentUser: boolean;
};

export type AccountMembershipFeatureSupport = {
  hasAccountMembers: boolean;
  hasInviteMember: boolean;
  hasAcceptInvitation: boolean;
  hasLeaveAccount: boolean;
};

function isUnknownFieldError(blob: string, fieldName: string): boolean {
  const escaped = fieldName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(
    `Cannot query field ['"]${escaped}['"]|Field ['"]${escaped}['"]|Unknown field ['"]${escaped}['"]|FieldUndefined[^\\n]*${escaped}`,
    "i",
  ).test(blob);
}

/**
 * Detect membership ops without schema introspection (often disabled in local/staging).
 * An unknown-field validation error means the runtime does not expose the op yet;
 * auth/business errors mean the field exists.
 */
export async function detectAccountMembershipFeatureSupport(): Promise<AccountMembershipFeatureSupport> {
  const probeAccountId = "00000000-0000-0000-0000-000000000001";

  const [membersProbe, inviteProbe, acceptProbe, leaveProbe] = await Promise.all([
    executeGraphQL(
      `
        query AccountMembersProbe($accountId: ID!) {
          accountMembers(accountId: $accountId) {
            membershipId
          }
        }
      `,
      { accountId: probeAccountId },
    ),
    executeGraphQL(
      `
        mutation InviteMemberProbe($input: InviteMemberInput!) {
          inviteMember(input: $input) {
            membershipId
          }
        }
      `,
      {
        input: {
          accountId: probeAccountId,
          email: "probe@example.com",
          role: "MEMBER",
        },
      },
    ),
    executeGraphQL(
      `
        mutation AcceptInvitationProbe($token: String!) {
          acceptInvitation(token: $token) {
            membershipId
          }
        }
      `,
      { token: "probe-token" },
    ),
    executeGraphQL(
      `
        mutation LeaveAccountProbe($accountId: ID!) {
          leaveAccount(accountId: $accountId)
        }
      `,
      { accountId: probeAccountId },
    ),
  ]);

  const membersBlob = JSON.stringify(membersProbe);
  const inviteBlob = JSON.stringify(inviteProbe);
  const acceptBlob = JSON.stringify(acceptProbe);
  const leaveBlob = JSON.stringify(leaveProbe);

  return {
    hasAccountMembers: !isUnknownFieldError(membersBlob, "accountMembers"),
    hasInviteMember: !isUnknownFieldError(inviteBlob, "inviteMember"),
    hasAcceptInvitation: !isUnknownFieldError(acceptBlob, "acceptInvitation"),
    hasLeaveAccount: !isUnknownFieldError(leaveBlob, "leaveAccount"),
  };
}

export async function getMyAccounts(
  token: string,
): Promise<Array<{ id: string; accountName: string; accountType: string; isOwner: boolean }>> {
  const result = await executeGraphQL<{
    myAccounts: Array<{
      id: string;
      accountName: string;
      accountType: string;
      isOwner: boolean;
    }>;
  }>(
    `
      query MyAccounts {
        myAccounts {
          id
          accountName
          accountType
          isOwner
        }
      }
    `,
    {},
    token,
  );

  if (result.errors) {
    throw new Error(result.errors.map((error) => error.message).join("; "));
  }

  return result.data?.myAccounts ?? [];
}

export async function getAccountMembers(
  token: string,
  accountId: string,
): Promise<AccountMemberRow[]> {
  const result = await executeGraphQL<{ accountMembers: AccountMemberRow[] }>(
    `
      query AccountMembers($accountId: ID!) {
        accountMembers(accountId: $accountId) {
          membershipId
          accountId
          userId
          email
          displayName
          avatarUrl
          role
          status
          isDefault
          isCurrentUser
        }
      }
    `,
    { accountId },
    token,
  );

  if (result.errors) {
    throw new Error(result.errors.map((error) => error.message).join("; "));
  }

  return result.data?.accountMembers ?? [];
}

export async function inviteMember(
  token: string,
  input: { accountId: string; email: string; role?: "ADMIN" | "MEMBER" | "VIEWER" },
) {
  return executeGraphQL(
    `
      mutation InviteMember($input: InviteMemberInput!) {
        inviteMember(input: $input) {
          membershipId
          email
          role
          status
        }
      }
    `,
    { input },
    token,
  );
}

function operationMatches(query: string | undefined, needle: string): boolean {
  return Boolean(query && query.includes(needle));
}

export async function mockGraphQLOperation(
  page: Page,
  matcher: (query: string) => boolean,
  payload: Record<string, unknown> | ((postData: { query?: string; variables?: Record<string, unknown> }) => Record<string, unknown>),
): Promise<void> {
  await page.route("**/graphql", async (route: Route) => {
    const request = route.request();
    if (request.method() !== "POST") {
      await route.fallback();
      return;
    }

    let postData: { query?: string; variables?: Record<string, unknown> } = {};
    try {
      postData = (request.postDataJSON() as typeof postData) ?? {};
    } catch {
      await route.fallback();
      return;
    }

    if (!matcher(postData.query ?? "")) {
      // Fall through to other handlers / the network (do not use continue()).
      await route.fallback();
      return;
    }

    const body = typeof payload === "function" ? payload(postData) : payload;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });
}

/** True when the GraphQL document includes the given operation name or field. */
export function graphqlIncludes(query: string | undefined, needle: string): boolean {
  return operationMatches(query, needle);
}

export function buildMockMembers(accountId: string, count: number): AccountMemberRow[] {
  return Array.from({ length: count }, (_, index) => {
    const isCurrent = index === 0;
    return {
      membershipId: `membership-${index + 1}`,
      accountId,
      userId: isCurrent ? "current-user" : `user-${index + 1}`,
      email: isCurrent ? "owner@example.com" : `member-${index + 1}@example.com`,
      displayName: isCurrent ? "Owner User" : `Member ${index + 1}`,
      avatarUrl: null,
      role: isCurrent ? "OWNER" : "MEMBER",
      status: "ACTIVE",
      isDefault: isCurrent,
      isCurrentUser: isCurrent,
    };
  });
}

export const membershipUi = {
  membersPaper: '[data-testid="paper-profile-account-members"]',
  membersTitle: /^(Members|Membros)$/,
  inviteButton: /^(Invite|Convidar)$/,
  leaveButton: /^(Leave this account|Desvincular desta conta)$/,
  inviteDialogTitle: /^(Invite member|Convidar membro)$/,
  searchEmpty: /^(No members match your search\.|Nenhum membro corresponde à pesquisa\.)$/,
  acceptSuccessTitle: /^(Invitation accepted|Convite aceito)$/,
  acceptErrorTitle: /^(Invitation unavailable|Convite indisponível)$/,
  invalidToken: /^(The invitation token is missing or invalid\.|O token do convite está ausente ou é inválido\.)$/,
};
