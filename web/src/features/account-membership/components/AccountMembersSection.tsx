"use client";

import * as React from "react";
import {
  Alert,
  Avatar,
  Box,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  MenuItem,
  Pagination,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import GroupAddIcon from "@mui/icons-material/GroupAdd";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import CancelScheduleSendIcon from "@mui/icons-material/CancelScheduleSend";
import type { Theme } from "@mui/material/styles";
import { useTheme } from "@mui/material/styles";
import { useMutation, useQuery } from "@apollo/client/react";
import {
  AccountMembersDocument,
  type AccountMembersQuery,
  type AccountMembersQueryVariables,
  type CancelInvitationMutation,
  type CancelInvitationMutationVariables,
  type ChangeMemberRoleMutation,
  type ChangeMemberRoleMutationVariables,
  type InviteMemberMutation,
  type InviteMemberMutationVariables,
  type RemoveMemberMutation,
  type RemoveMemberMutationVariables,
} from "@/lib/graphql/operations/auth/accountMembership.generated";
import { AccountRole, MembershipStatus } from "@/lib/graphql/types/__generated__/graphql";
import {
  CANCEL_INVITATION,
  CHANGE_MEMBER_ROLE,
  INVITE_MEMBER,
  REMOVE_MEMBER,
} from "@/lib/graphql/operations/auth/accountMembership";
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { useToast } from "@/shared/providers";
import { useTranslation } from "react-i18next";
import { extractErrorMessage } from "@/shared/utils/error";
import { Label } from "@/shared/components/ui/primitives";
import { Button } from "@/shared/components/ui/primitives/Button";
import { Paper } from "@/shared/components/ui/layout";
import { ExpandableSearchInput } from "@/shared/components/ui/forms/ExpandableSearchInput";
import {
  ManagementHeaderActionButton,
  ManagementHeaderActions,
} from "@/shared/components/management/ManagementHeaderActions";
import {
  financeTransactionBodyRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from "@/app/personal-finance/utils/financeTransactionTableStyles";
import { INVITEABLE_ACCOUNT_ROLES } from "@/features/account-membership/inviteRoles";
import { ConfirmDialog } from "@/shared/components/ui/feedback/dialogs";

type AccountMembersSectionProps = {
  accountId: string;
  /** Client-side page size. Pagination shows when member count exceeds this. */
  pageSize?: number;
};

/** Default page size for the members table (frontend-only pagination). */
export const DEFAULT_ACCOUNT_MEMBERS_PAGE_SIZE = 5;

/** Roles an OWNER may assign via change-role (not via invite). */
const CHANGE_ROLE_OPTIONS_BY_ACTOR: Record<AccountRole, AccountRole[]> = {
  [AccountRole.Owner]: [AccountRole.Admin, AccountRole.Member, AccountRole.Viewer],
  [AccountRole.Admin]: [AccountRole.Member, AccountRole.Viewer],
  [AccountRole.Member]: [],
  [AccountRole.Viewer]: [],
};

const DEFAULT_INVITE_ROLE = INVITEABLE_ACCOUNT_ROLES[0] ?? AccountRole.Member;

type MemberRow = AccountMembersQuery["accountMembers"][number];

const EMPTY_ACCOUNT_MEMBERS: AccountMembersQuery["accountMembers"] = [];

const getMemberInitials = (member: MemberRow) => {
  const source = member.displayName || member.email;
  const parts = source.trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0]?.[0] ?? ""}${parts[1]?.[0] ?? ""}`.toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
};

const memberAvatarSx = (theme: Theme) => ({
  width: 36,
  height: 36,
  fontSize: theme.typography.caption.fontSize,
  borderRadius: `${theme.custom.radius.lg}px`,
  bgcolor: "primary.light",
  color: "primary.main",
});

const memberHeadCellSx = (theme: Theme) => ({
  ...financeTransactionHeadCellSx(theme),
  px: 2,
});

const memberBodyCellSx = {
  px: 2,
  py: 1.75,
};

const statusChipSx = {
  borderRadius: 999,
  fontWeight: 400,
};

function InviteMemberDialog({
  open,
  onClose,
  onSubmit,
  loading,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (email: string, role: AccountRole) => Promise<void>;
  loading: boolean;
}) {
  const { t } = useTranslation("common");
  const [email, setEmail] = React.useState("");

  React.useEffect(() => {
    if (open) {
      setEmail("");
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="xs">
      <DialogTitle>{t("profile.members.inviteTitle")}</DialogTitle>
      <DialogContent>
        <Stack gap={2} sx={{ pt: 1 }}>
          <TextField
            autoFocus
            label={t("profile.members.emailLabel")}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            type="email"
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} variant="outlined" disabled={loading}>
          {t("actions.cancel")}
        </Button>
        <Button
          variant="contained"
          onClick={() => {
            void onSubmit(email, DEFAULT_INVITE_ROLE);
          }}
          disabled={loading || email.trim().length === 0}
          loading={loading}
        >
          {t("profile.members.inviteAction")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function AccountMembersSection({
  accountId,
  pageSize = DEFAULT_ACCOUNT_MEMBERS_PAGE_SIZE,
}: AccountMembersSectionProps) {
  const { t } = useTranslation("common");
  const theme = useTheme();
  const toast = useToast();
  const [inviteOpen, setInviteOpen] = React.useState(false);
  const [inviteLoading, setInviteLoading] = React.useState(false);
  const [memberToRemove, setMemberToRemove] = React.useState<{
    membershipId: string;
    name: string;
  } | null>(null);
  const [page, setPage] = React.useState(0);
  const [searchQuery, setSearchQuery] = React.useState("");

  const { data, loading, error, refetch } = useQuery<AccountMembersQuery, AccountMembersQueryVariables>(
    AccountMembersDocument,
    {
      variables: { accountId },
      skip: !accountId,
    },
  );

  const members = data?.accountMembers ?? EMPTY_ACCOUNT_MEMBERS;
  const effectivePageSize = Math.max(1, pageSize);
  const normalizedSearchQuery = searchQuery.trim().toLowerCase();
  const filteredMembers = React.useMemo(() => {
    if (!normalizedSearchQuery) return members;
    return members.filter((member) => {
      const name = member.displayName?.toLowerCase() ?? "";
      const email = member.email.toLowerCase();
      return name.includes(normalizedSearchQuery) || email.includes(normalizedSearchQuery);
    });
  }, [members, normalizedSearchQuery]);

  const pageCount = Math.max(1, Math.ceil(filteredMembers.length / effectivePageSize));
  const showPagination = filteredMembers.length > effectivePageSize;
  const safePage = Math.min(page, pageCount - 1);
  const pagedMembers = showPagination
    ? filteredMembers.slice(safePage * effectivePageSize, safePage * effectivePageSize + effectivePageSize)
    : filteredMembers;

  React.useEffect(() => {
    setPage(0);
  }, [normalizedSearchQuery]);

  React.useEffect(() => {
    if (page !== safePage) {
      setPage(safePage);
    }
  }, [page, safePage]);

  const currentMembership =
    members.find((member) => member.isCurrentUser && member.status === MembershipStatus.Active) ?? null;
  const currentRole = currentMembership?.role ?? null;
  const canManage =
    currentRole === AccountRole.Owner || currentRole === AccountRole.Admin;
  const allowedInviteRoles = canManage ? INVITEABLE_ACCOUNT_ROLES : [];

  const [inviteMemberMutation] = useMutation<InviteMemberMutation, InviteMemberMutationVariables>(INVITE_MEMBER);
  const [cancelInvitationMutation] = useMutation<CancelInvitationMutation, CancelInvitationMutationVariables>(CANCEL_INVITATION);
  const [removeMemberMutation] = useMutation<RemoveMemberMutation, RemoveMemberMutationVariables>(REMOVE_MEMBER);
  const [changeMemberRoleMutation] = useMutation<ChangeMemberRoleMutation, ChangeMemberRoleMutationVariables>(CHANGE_MEMBER_ROLE);

  const { executeMutation, isMutationInFlight } = useMutationWithRefetch<unknown, AccountMembersQueryVariables>({
    refetchQuery: AccountMembersDocument,
    refetchVariables: { accountId },
    onRefetch: () => {
      void refetch();
    },
    errorMessage: t("profile.members.actionError"),
  });

  const handleInvite = async (email: string, role: AccountRole) => {
    try {
      setInviteLoading(true);
      await executeMutation(
        inviteMemberMutation,
        {
          input: {
            accountId,
            email: email.trim(),
            role,
          },
        },
        `invite-${email.trim().toLowerCase()}`,
      );
      toast.success(t("profile.members.inviteSuccess"));
      setInviteOpen(false);
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.members.actionError")));
    } finally {
      setInviteLoading(false);
    }
  };

  const handleCancelInvitation = async (membershipId: string) => {
    try {
      await executeMutation(
        cancelInvitationMutation,
        { accountId, membershipId },
        `cancel-${membershipId}`,
      );
      toast.success(t("profile.members.cancelSuccess"));
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.members.actionError")));
    }
  };

  const handleRemoveMember = async (membershipId: string) => {
    try {
      await executeMutation(
        removeMemberMutation,
        { accountId, membershipId },
        `remove-${membershipId}`,
      );
      toast.success(t("profile.members.removeSuccess"));
      setMemberToRemove(null);
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.members.actionError")));
    }
  };

  const handleChangeRole = async (membershipId: string, role: AccountRole) => {
    try {
      await executeMutation(
        changeMemberRoleMutation,
        { accountId, membershipId, role },
        `role-${membershipId}`,
      );
      toast.success(t("profile.members.roleChangeSuccess"));
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.members.actionError")));
    }
  };

  return (
    <Stack gap={1}>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
        <Label sx={{ mb: 0, pl: 2, fontSize: (theme) => theme.typography.subtitle1.fontSize }}>
          {t("profile.members.title")}
        </Label>
        <ManagementHeaderActions alignItems="center" gap={1.5}>
          <ExpandableSearchInput
            searchQuery={searchQuery}
            onSearchQueryChange={setSearchQuery}
            placeholder={t("profile.members.searchPlaceholder")}
            ariaClearLabel={t("profile.members.clearSearchAriaLabel")}
            height={51}
            collapsedSearchIconMarginLeft="1.5rem"
            sx={(theme) => ({
              "& .MuiOutlinedInput-root": {
                borderRadius: `${theme.custom.radius.md}px`,
              },
              "& .MuiOutlinedInput-root .MuiOutlinedInput-notchedOutline": {
                borderRadius: `${theme.custom.radius.md}px`,
              },
            })}
          />
          {canManage && allowedInviteRoles.length > 0 ? (
            <ManagementHeaderActionButton
              variant="outlined"
              startIcon={<GroupAddIcon />}
              onClick={() => setInviteOpen(true)}
              offsetBottom={0}
            >
              {t("profile.members.inviteAction")}
            </ManagementHeaderActionButton>
          ) : null}
        </ManagementHeaderActions>
      </Box>
      <Paper
        elevation={0}
        variant="outlined"
        name="profile-account-members"
        sx={{
          p: 0,
          overflow: "hidden",
        }}
      >
        <Stack gap={0}>
          {loading ? (
            <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
              <Typography variant="body2" color="text.secondary">
                {t("profile.members.loading")}
              </Typography>
            </Box>
          ) : error ? (
            <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
              <Alert severity="error">{extractErrorMessage(error, t("profile.members.loadError"))}</Alert>
            </Box>
          ) : filteredMembers.length === 0 ? (
            <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
              <Typography variant="body2" color="text.secondary">
                {normalizedSearchQuery
                  ? t("profile.members.searchEmpty")
                  : t("profile.members.empty")}
              </Typography>
            </Box>
          ) : (
            <>
            <TableContainer sx={{ overflowX: "auto" }}>
              <Table sx={getFinanceTransactionTableSx(880)} aria-label={t("profile.members.table.ariaLabel")}>
                <TableHead sx={financeTransactionTableHeadSx}>
                  <TableRow sx={financeTransactionTableHeadRowSx}>
                    <TableCell sx={memberHeadCellSx(theme)}>
                      <Label sx={{ mb: 0 }}>{t("profile.members.table.member")}</Label>
                    </TableCell>
                    <TableCell sx={memberHeadCellSx(theme)}>
                      <Label sx={{ mb: 0 }}>{t("profile.members.table.role")}</Label>
                    </TableCell>
                    <TableCell sx={memberHeadCellSx(theme)}>
                      <Label sx={{ mb: 0 }}>{t("profile.members.table.status")}</Label>
                    </TableCell>
                    <TableCell sx={memberHeadCellSx(theme)}>
                      <Label sx={{ mb: 0 }}>{t("profile.members.table.permission")}</Label>
                    </TableCell>
                    <TableCell align="right" sx={memberHeadCellSx(theme)} />
                  </TableRow>
                </TableHead>
                <TableBody sx={financeTransactionTableBodySx}>
                  {pagedMembers.map((member, index) => {
                    const rowBusy =
                      isMutationInFlight(`cancel-${member.membershipId}`) ||
                      isMutationInFlight(`remove-${member.membershipId}`) ||
                      isMutationInFlight(`role-${member.membershipId}`);
                    const canEditRole =
                      currentRole === AccountRole.Owner &&
                      member.status === MembershipStatus.Active &&
                      !member.isCurrentUser &&
                      member.role !== AccountRole.Owner;
                    const canRemove =
                      canManage &&
                      member.status === MembershipStatus.Active &&
                      !member.isCurrentUser &&
                      !(
                        currentRole === AccountRole.Admin &&
                        (member.role === AccountRole.Owner || member.role === AccountRole.Admin)
                      );
                    const canCancel =
                      canManage &&
                      member.status === MembershipStatus.Pending &&
                      !member.isCurrentUser;
                    const actionLabel = canCancel
                      ? t("profile.members.cancelAction")
                      : t("profile.members.removeAction");
                    const ActionIcon = canCancel ? CancelScheduleSendIcon : DeleteOutlineIcon;

                    return (
                      <TableRow
                        key={member.membershipId}
                        sx={{
                          ...financeTransactionBodyRowSx(theme, {
                            isFirst: index === 0,
                            isLast: index === pagedMembers.length - 1,
                          }),
                          "&:hover .MuiTableCell-root": {
                            bgcolor: "action.hover",
                          },
                        }}
                      >
                        <TableCell sx={memberBodyCellSx}>
                          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 240 }}>
                            <Avatar
                              variant="rounded"
                              src={member.avatarUrl || undefined}
                              sx={memberAvatarSx(theme)}
                            >
                              {getMemberInitials(member)}
                            </Avatar>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" noWrap>
                                {member.displayName || member.email}
                              </Typography>
                              {member.displayName && member.displayName.trim() !== member.email ? (
                                <Typography variant="caption" color="text.secondary" noWrap display="block">
                                  {member.email}
                                </Typography>
                              ) : null}
                            </Box>
                          </Stack>
                        </TableCell>
                        <TableCell sx={memberBodyCellSx}>
                          <Chip
                            label={t(`profile.members.roles.${member.role}`)}
                            size="small"
                            sx={{ borderRadius: 999, fontWeight: 400 }}
                          />
                        </TableCell>
                        <TableCell sx={memberBodyCellSx}>
                          <Chip
                            label={t(`profile.members.status.${member.status}`)}
                            size="small"
                            color={
                              member.status === MembershipStatus.Active
                                ? "success"
                                : member.status === MembershipStatus.Pending
                                  ? "warning"
                                  : "default"
                            }
                            variant={member.status === MembershipStatus.Removed ? "outlined" : "filled"}
                            sx={statusChipSx}
                          />
                        </TableCell>
                        <TableCell sx={{ ...memberBodyCellSx, minWidth: 170 }}>
                          {canEditRole ? (
                            <FormControl size="small" sx={{ minWidth: 156 }}>
                              <Select
                                value={member.role}
                                disabled={rowBusy}
                                onChange={(event) => {
                                  void handleChangeRole(member.membershipId, event.target.value as AccountRole);
                                }}
                                inputProps={{ "aria-label": t("profile.members.roleLabel") }}
                                sx={{
                                  borderRadius: `${theme.custom.radius.button}px`,
                                  bgcolor: "background.paper",
                                  "& .MuiSelect-select": {
                                    py: 1.25,
                                  },
                                }}
                              >
                                {CHANGE_ROLE_OPTIONS_BY_ACTOR[AccountRole.Owner].map((role) => (
                                  <MenuItem key={role} value={role}>
                                    {t(`profile.members.roles.${role}`)}
                                  </MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                          ) : (
                            <Typography variant="body2" color="text.secondary">
                              {t("profile.members.table.noPermissionAction")}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={{ ...memberBodyCellSx, width: 64 }}>
                          {canCancel || canRemove ? (
                            <Tooltip title={actionLabel}>
                              <span>
                                <IconButton
                                  color={canCancel ? "warning" : "error"}
                                  aria-label={actionLabel}
                                  disabled={rowBusy}
                                  onClick={() => {
                                    if (canCancel) {
                                      void handleCancelInvitation(member.membershipId);
                                    } else {
                                      setMemberToRemove({
                                        membershipId: member.membershipId,
                                        name: member.displayName || member.email,
                                      });
                                    }
                                  }}
                                >
                                  <ActionIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            {showPagination ? (
              <Stack
                direction="row"
                justifyContent="center"
                alignItems="center"
                sx={{ px: { xs: 2, sm: 3 }, py: 1.5 }}
              >
                <Pagination
                  page={safePage + 1}
                  count={pageCount}
                  onChange={(_, nextPage) => setPage(nextPage - 1)}
                  color="primary"
                  size="small"
                  siblingCount={0}
                  boundaryCount={1}
                />
              </Stack>
            ) : null}
            </>
          )}
        </Stack>
      </Paper>

      <InviteMemberDialog
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        onSubmit={handleInvite}
        loading={inviteLoading}
      />

      <ConfirmDialog
        open={memberToRemove !== null}
        title={t("profile.members.removeConfirmTitle")}
        description={t("profile.members.removeConfirmDescription", {
          name: memberToRemove?.name ?? "",
        })}
        confirmText={t("profile.members.removeAction")}
        cancelText={t("actions.cancel")}
        confirmColor="error"
        loading={
          memberToRemove !== null && isMutationInFlight(`remove-${memberToRemove.membershipId}`)
        }
        onConfirm={() => {
          if (memberToRemove) {
            void handleRemoveMember(memberToRemove.membershipId);
          }
        }}
        onCancel={() => setMemberToRemove(null)}
        data-testid="remove-member-confirm-dialog"
      />
    </Stack>
  );
}
