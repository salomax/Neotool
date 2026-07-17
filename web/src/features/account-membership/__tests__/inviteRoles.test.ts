import { describe, expect, it } from "vitest";
import { AccountRole } from "@/lib/graphql/types/__generated__/graphql";
import { INVITEABLE_ACCOUNT_ROLES } from "../inviteRoles";

describe("INVITEABLE_ACCOUNT_ROLES (interim invite policy)", () => {
  it("only allows MEMBER until per-membership RBAC lands", () => {
    expect(INVITEABLE_ACCOUNT_ROLES).toEqual([AccountRole.Member]);
    expect(INVITEABLE_ACCOUNT_ROLES).not.toContain(AccountRole.Owner);
    expect(INVITEABLE_ACCOUNT_ROLES).not.toContain(AccountRole.Admin);
    expect(INVITEABLE_ACCOUNT_ROLES).not.toContain(AccountRole.Viewer);
  });
});
