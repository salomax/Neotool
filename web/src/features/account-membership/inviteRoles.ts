import { AccountRole } from "@/lib/graphql/types/__generated__/graphql";

/**
 * Interim invite policy: only MEMBER until per-membership RBAC (#398).
 * Backend rejects OWNER / ADMIN / VIEWER on `inviteMember`.
 */
export const INVITEABLE_ACCOUNT_ROLES: AccountRole[] = [AccountRole.Member];
