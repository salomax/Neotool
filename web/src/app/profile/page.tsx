"use client";

import * as React from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import Avatar from "@mui/material/Avatar";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import IconButton from "@mui/material/IconButton";
import Link from "@mui/material/Link";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import RuleIcon from "@mui/icons-material/Rule";
import PersonIcon from "@mui/icons-material/Person";
import ColorLensIcon from "@mui/icons-material/ColorLens";
import EditIcon from "@mui/icons-material/Edit";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import LogoutIcon from "@mui/icons-material/Logout";
import { AccountMembersSection } from "@/features/account-membership/components/AccountMembersSection";
import { ConnectedAgentsSection } from "@/features/connected-agents/components/ConnectedAgentsSection";
import { AccountMembersDocument } from "@/lib/graphql/operations/auth/accountMembership.generated";
import { LEAVE_ACCOUNT } from "@/lib/graphql/operations/auth/accountMembership";
import { Page, Container, Paper, Stack } from "@/shared/components/ui/layout";
import { Box } from "@/shared/components/ui/layout/Box";
import { SegmentedControl } from "@/shared/components/ui/inputs";
import { Label } from "@/shared/components/ui/primitives";
import { Button } from "@/shared/components/ui/primitives/Button";
import { useAuth, usePreferences, useToast } from "@/shared/providers";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "react-i18next";
import { extractErrorMessage } from "@/shared/utils/error";
import { MY_ACCOUNTS } from "@/lib/graphql/operations/auth/queries";
import {
  REMOVE_USER_AVATAR,
  UPDATE_ACCOUNT,
  UPDATE_USER_AVATAR,
} from "@/lib/graphql/operations/auth/mutations";
import {
  CONFIRM_ASSET_UPLOAD,
  CREATE_ASSET_UPLOAD,
} from "@/lib/graphql/operations/banking/mutations";
import {
  Language,
  RuleCreationMode,
  Theme,
} from "@/lib/graphql/types/__generated__/graphql";

const MAX_AVATAR_BYTES = 2 * 1024 * 1024;
const AVATAR_ACCEPT_MIME_TYPES = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
] as const;

type AccountItem = {
  id: string;
  accountName: string;
  accountType: string;
  accountStatus: string;
  isOwner: boolean;
};

type MyAccountsQueryData = {
  myAccounts: AccountItem[];
};

type UpdateAccountMutationData = {
  updateAccount: AccountItem;
};

type CreateAssetUploadMutationData = {
  createAssetUpload: {
    id: string;
    uploadUrl: string | null;
    visibility: "PUBLIC" | "PRIVATE";
  };
};

type ConfirmAssetUploadMutationData = {
  confirmAssetUpload: {
    id: string;
    visibility: "PUBLIC" | "PRIVATE";
    publicUrl: string | null;
  };
};

type UpdateUserAvatarMutationData = {
  updateUserAvatar: {
    id: string;
    avatarUrl: string | null;
    avatarProvider: string | null;
  };
};

type RemoveUserAvatarMutationData = {
  removeUserAvatar: {
    id: string;
    avatarUrl: string | null;
    avatarProvider: string | null;
  };
};

export default function ProfilePage() {
  const { t } = useTranslation("common");
  const { user, setUser } = useAuth();
  const {
    userPreferences,
    bankingUserPreferences,
    updateUserPreferences,
    updateBankingUserPreferences,
    isLoading,
  } = usePreferences();
  const toast = useToast();
  const apolloClient = useApolloClient();

  const fileInputRef = React.useRef<HTMLInputElement | null>(null);
  const [editingAccountId, setEditingAccountId] = React.useState<string | null>(
    null,
  );
  const [editingAccountName, setEditingAccountName] = React.useState("");
  const [avatarUploading, setAvatarUploading] = React.useState(false);
  const [avatarRemoving, setAvatarRemoving] = React.useState(false);
  const isAvatarBusy = avatarUploading || avatarRemoving;

  const { data: accountsData, loading: accountsLoading, refetch: refetchAccounts } =
    useQuery<MyAccountsQueryData>(MY_ACCOUNTS, {
      skip: !user,
    });
  const accounts = accountsData?.myAccounts ?? [];
  const primaryAccount = accounts[0] ?? null;

  const [updateAccountMutation] = useMutation<
    UpdateAccountMutationData,
    { accountId: string; input: { accountName: string } }
  >(UPDATE_ACCOUNT);
  const [createAssetUpload] = useMutation<
    CreateAssetUploadMutationData,
    {
      input: {
        namespace: string;
        filename: string;
        mimeType: string;
        sizeBytes: number;
      };
    }
  >(CREATE_ASSET_UPLOAD);
  const [confirmAssetUpload] = useMutation<
    ConfirmAssetUploadMutationData,
    { input: { assetId: string } }
  >(CONFIRM_ASSET_UPLOAD);
  const [updateUserAvatar] = useMutation<
    UpdateUserAvatarMutationData,
    { input: { avatarUrl: string } }
  >(UPDATE_USER_AVATAR);
  const [removeUserAvatar] = useMutation<RemoveUserAvatarMutationData>(
    REMOVE_USER_AVATAR,
  );
  const [leaveAccountMutation, { loading: leaveAccountLoading }] = useMutation(
    LEAVE_ACCOUNT,
  );

  usePageTitle(t("routes.profile"));

  const displayName = user?.displayName || t("appHeader.defaultUser");
  const isEmailUser = !user?.avatarProvider;

  const showUpdateError = React.useCallback(() => {
    toast.error(t("profile.preferences.updateError"));
  }, [t, toast]);

  const syncAvatarInCache = React.useCallback(
    (avatarUrl: string | null, avatarProvider?: string | null) => {
      apolloClient.cache.modify({
        fields: {
          currentUser(existing) {
            if (!existing) {
              return existing;
            }
            return {
              ...existing,
              avatarUrl,
              avatarProvider:
                avatarProvider !== undefined
                  ? avatarProvider
                  : existing.avatarProvider ?? null,
            };
          },
        },
      });
    },
    [apolloClient.cache],
  );

  const onThemeChange = React.useCallback(
    async (nextTheme: Theme) => {
      try {
        await updateUserPreferences({ theme: nextTheme });
      } catch {
        showUpdateError();
      }
    },
    [showUpdateError, updateUserPreferences],
  );

  const onLanguageChange = React.useCallback(
    async (nextLanguage: Language) => {
      try {
        await updateUserPreferences({ language: nextLanguage });
      } catch {
        showUpdateError();
      }
    },
    [showUpdateError, updateUserPreferences],
  );

  const onKeepConnectedChange = React.useCallback(
    async (nextValue: boolean) => {
      try {
        await updateUserPreferences({ keepMeConnected: nextValue });
      } catch {
        showUpdateError();
      }
    },
    [showUpdateError, updateUserPreferences],
  );

  const onRuleModeChange = React.useCallback(
    async (nextMode: RuleCreationMode) => {
      try {
        await updateBankingUserPreferences({ ruleCreationMode: nextMode });
      } catch {
        showUpdateError();
      }
    },
    [showUpdateError, updateBankingUserPreferences],
  );

  const startEditAccount = React.useCallback((account: AccountItem) => {
    setEditingAccountId(account.id);
    setEditingAccountName(account.accountName);
  }, []);

  const finishEditAccount = React.useCallback(() => {
    setEditingAccountId(null);
    setEditingAccountName("");
  }, []);

  const commitAccountName = React.useCallback(
    async (account: AccountItem) => {
      const nextName = editingAccountName.trim();
      if (!nextName || nextName === account.accountName) {
        finishEditAccount();
        return;
      }

      try {
        await updateAccountMutation({
          variables: {
            accountId: account.id,
            input: { accountName: nextName },
          },
        });
        await refetchAccounts();
      } catch {
        toast.error(t("profile.accounts.updateError"));
      } finally {
        finishEditAccount();
      }
    },
    [
      editingAccountName,
      finishEditAccount,
      refetchAccounts,
      t,
      toast,
      updateAccountMutation,
    ],
  );

  const onAvatarFileSelected = React.useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      if (!file || !isEmailUser || !user) {
        return;
      }

      if (!AVATAR_ACCEPT_MIME_TYPES.includes(file.type as (typeof AVATAR_ACCEPT_MIME_TYPES)[number])) {
        toast.error(t("profile.avatar.invalidTypeError"));
        return;
      }
      if (file.size > MAX_AVATAR_BYTES) {
        toast.error(t("profile.avatar.maxSizeError"));
        return;
      }

      setAvatarUploading(true);
      try {
        const createResponse = await createAssetUpload({
          variables: {
            input: {
              namespace: "user-profiles",
              filename: file.name,
              mimeType: file.type,
              sizeBytes: file.size,
            },
          },
        });
        const assetId = createResponse.data?.createAssetUpload.id;
        const uploadUrl = createResponse.data?.createAssetUpload.uploadUrl;
        const createVisibility = createResponse.data?.createAssetUpload.visibility;
        if (createVisibility && createVisibility !== "PUBLIC") {
          throw new Error("ASSET_VISIBILITY_PRIVATE");
        }
        if (!assetId || !uploadUrl) {
          throw new Error("asset upload init failed");
        }

        const uploadResponse = await fetch(uploadUrl, {
          method: "PUT",
          body: file,
          headers: {
            "Content-Type": file.type,
          },
        });
        if (!uploadResponse.ok) {
          throw new Error("asset upload failed");
        }

        const confirmResponse = await confirmAssetUpload({
          variables: {
            input: { assetId },
          },
        });
        const confirmVisibility = confirmResponse.data?.confirmAssetUpload.visibility;
        const publicUrl = confirmResponse.data?.confirmAssetUpload.publicUrl;
        if (!publicUrl) {
          if (confirmVisibility === "PRIVATE") {
            throw new Error("ASSET_VISIBILITY_PRIVATE");
          }
          throw new Error("asset public url missing");
        }

        const updateResponse = await updateUserAvatar({
          variables: {
            input: {
              avatarUrl: publicUrl,
            },
          },
        });

        const updatedUser = updateResponse.data?.updateUserAvatar;
        if (updatedUser) {
          setUser({
            ...(user ?? {}),
            avatarUrl: updatedUser.avatarUrl,
            avatarProvider: updatedUser.avatarProvider,
          });
          syncAvatarInCache(updatedUser.avatarUrl, updatedUser.avatarProvider);
        }
      } catch (error) {
        if (error instanceof Error && error.message === "ASSET_VISIBILITY_PRIVATE") {
          toast.error(t("profile.avatar.publicAccessRequiredError"));
          return;
        }
        toast.error(t("profile.avatar.uploadError"));
      } finally {
        setAvatarUploading(false);
      }
    },
    [
      confirmAssetUpload,
      createAssetUpload,
      isEmailUser,
      setUser,
      syncAvatarInCache,
      t,
      toast,
      updateUserAvatar,
      user,
    ],
  );

  const onRemoveAvatar = React.useCallback(async () => {
    if (!isEmailUser || !user || !user.avatarUrl) {
      return;
    }
    setAvatarRemoving(true);
    try {
      const response = await removeUserAvatar();
      const updatedUser = response.data?.removeUserAvatar;
      const nextAvatarUrl = updatedUser?.avatarUrl ?? null;
      const nextAvatarProvider = updatedUser?.avatarProvider ?? user.avatarProvider ?? null;
      setUser({
        ...user,
        avatarUrl: nextAvatarUrl,
        avatarProvider: nextAvatarProvider,
      });
      syncAvatarInCache(nextAvatarUrl, nextAvatarProvider);
    } catch {
      toast.error(t("profile.avatar.removeError"));
    } finally {
      setAvatarRemoving(false);
    }
  }, [isEmailUser, removeUserAvatar, setUser, syncAvatarInCache, t, toast, user]);

  const handleLeaveAccount = React.useCallback(async () => {
    if (!primaryAccount) return;
    try {
      await leaveAccountMutation({ variables: { accountId: primaryAccount.id } });
      toast.success(t("profile.members.leaveSuccess"));
      await Promise.all([
        refetchAccounts(),
        apolloClient.refetchQueries({ include: [AccountMembersDocument] }),
      ]);
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.members.actionError")));
    }
  }, [
    apolloClient,
    leaveAccountMutation,
    primaryAccount,
    refetchAccounts,
    t,
    toast,
  ]);

  const themeOptions = React.useMemo(
    () => [
      { value: Theme.Light, label: t("profile.preferences.themeLight") },
      { value: Theme.Dark, label: t("profile.preferences.themeDark") },
      { value: Theme.System, label: t("profile.preferences.themeSystem") },
    ],
    [t],
  );

  return (
    <Page>
      <Container maxWidth="md" sx={{ py: { xs: 3, sm: 6 } }}>
        <Stack gap={4}>
          <Paper elevation={0} variant="outlined" name="profile-user-card">
            <Stack gap={2}>
              <Box
                sx={{
                  display: "flex",
                  alignItems: "flex-start",
                  justifyContent: "space-between",
                  gap: 2,
                }}
              >
                <Box sx={{ display: "flex", alignItems: "center", gap: 2, minWidth: 0 }}>
                <Box sx={{ position: "relative", width: 64, height: 64, flexShrink: 0 }}>
                  <Avatar
                    variant="rounded"
                    src={user?.avatarUrl || undefined}
                    sx={(theme) => ({
                      width: 64,
                      height: 64,
                      borderRadius: `${theme.custom.radius.lg}px`,
                    })}
                  />

                  {isEmailUser && (
                    <IconButton
                      size="small"
                      aria-label={t("profile.avatar.changePhoto")}
                      onClick={() => fileInputRef.current?.click()}
                      disabled={isAvatarBusy}
                      sx={{
                        position: "absolute",
                        bottom: -8,
                        right: -8,
                        width: 28,
                        height: 28,
                        bgcolor: "background.paper",
                        border: 1,
                        borderColor: "divider",
                        "&:hover": { bgcolor: "background.paper" },
                      }}
                    >
                      <PhotoCameraIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                  )}

                  {isAvatarBusy && (
                    <Box
                      sx={{
                        position: "absolute",
                        inset: 0,
                        borderRadius: 2,
                        bgcolor: "rgba(0,0,0,0.35)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <CircularProgress size={22} thickness={5} />
                    </Box>
                  )}
                </Box>

                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="h6" noWrap>
                    {displayName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {user?.email || ""}
                  </Typography>
                  {isEmailUser && user?.avatarUrl && (
                    <Link
                      component="button"
                      variant="caption"
                      underline="hover"
                      onClick={() => {
                        void onRemoveAvatar();
                      }}
                      disabled={isAvatarBusy}
                      sx={{ mt: 0.75 }}
                    >
                      {t("profile.avatar.removePhoto")}
                    </Link>
                  )}
                </Box>
                </Box>

                {primaryAccount && !primaryAccount.isOwner ? (
                  <Button
                    color="inherit"
                    variant="outlined"
                    startIcon={<LogoutIcon />}
                    loading={leaveAccountLoading}
                    onClick={() => {
                      void handleLeaveAccount();
                    }}
                    sx={{
                      flexShrink: 0,
                      color: "text.secondary",
                      borderColor: "divider",
                      "&:hover": {
                        borderColor: "text.secondary",
                        bgcolor: "action.hover",
                      },
                    }}
                  >
                    {t("profile.members.leaveAction")}
                  </Button>
                ) : null}
              </Box>

              <Divider />

              <Box
                sx={{
                  display: "grid",
                  gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                  gap: 2,
                }}
              >
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {t("profile.emailVerified")}
                  </Typography>
                  <Typography variant="body2">
                    {user?.emailVerified ? t("profile.yes") : t("profile.no")}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {t("profile.plan")}
                  </Typography>
                  <Typography variant="body2">{user?.plan || "—"}</Typography>
                </Box>
              </Box>

              <input
                ref={fileInputRef}
                type="file"
                accept={AVATAR_ACCEPT_MIME_TYPES.join(",")}
                hidden
                onChange={(event) => {
                  void onAvatarFileSelected(event);
                }}
              />
            </Stack>
          </Paper>

          <Stack gap={1}>
            <Label sx={{ mb: 0, pl: 2, fontSize: (theme) => theme.typography.subtitle1.fontSize }}>
              {t("profile.accounts.title")}
            </Label>
            <Paper elevation={0} variant="outlined" name="profile-accounts">
              <Stack gap={1.5}>
                {accountsLoading ? (
                  <Typography variant="body2" color="text.secondary">
                    {t("profile.accounts.loading")}
                  </Typography>
                ) : accounts.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    {t("profile.accounts.empty")}
                  </Typography>
                ) : (
                  accounts.map((account) => {
                    const isEditing = editingAccountId === account.id;
                    return (
                      <Box
                        key={account.id}
                        sx={{
                          display: "grid",
                          gridTemplateColumns: { xs: "1fr auto", sm: "1fr auto auto" },
                          gap: 1,
                          alignItems: "center",
                          py: 0.5,
                        }}
                      >
                        <Box sx={{ minWidth: 0 }}>
                          {isEditing ? (
                            <TextField
                              fullWidth
                              size="small"
                              autoFocus
                              value={editingAccountName}
                              onChange={(event) => setEditingAccountName(event.target.value)}
                              onBlur={() => {
                                void commitAccountName(account);
                              }}
                              onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                  event.preventDefault();
                                  void commitAccountName(account);
                                } else if (event.key === "Escape") {
                                  finishEditAccount();
                                }
                              }}
                            />
                          ) : (
                            <Typography variant="body2" noWrap>
                              {account.accountName}
                            </Typography>
                          )}
                        </Box>

                        <Typography variant="caption" color="text.secondary">
                          {account.isOwner
                            ? t("profile.accounts.owner")
                            : t("profile.accounts.readOnly")}
                        </Typography>

                        {account.isOwner && !isEditing ? (
                          <IconButton
                            size="small"
                            aria-label={t("profile.accounts.editLabel")}
                            onClick={() => startEditAccount(account)}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        ) : (
                          <Box sx={{ width: 32 }} />
                        )}
                      </Box>
                    );
                  })
                )}
              </Stack>
            </Paper>
          </Stack>

          {primaryAccount?.isOwner ? (
            <ConnectedAgentsSection accountId={primaryAccount.id} />
          ) : null}

          {primaryAccount ? (
            <AccountMembersSection accountId={primaryAccount.id} />
          ) : null}

          <Stack gap={1}>
            <Label sx={{ mb: 0, pl: 2, fontSize: (theme) => theme.typography.subtitle1.fontSize }}>
              {t("profile.preferences.title")}
            </Label>
            <Paper elevation={0} variant="outlined" name="profile-preferences">
              <Stack gap={2}>
                {isLoading ? (
                  <Typography variant="body2" color="text.secondary">
                    {t("profile.preferences.loading")}
                  </Typography>
                ) : (
                  <Stack gap={3}>
                    <Box>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <ColorLensIcon fontSize="small" />
                        <Label sx={{ mb: 0 }}>{t("profile.preferences.interface")}</Label>
                      </Box>
                      <Box
                        sx={{
                          display: "grid",
                          gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                          gap: 2,
                          py: 2,
                        }}
                      >
                        <Box>
                          <Typography variant="subtitle2" gutterBottom>
                            {t("profile.preferences.theme")}
                          </Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                            {t("profile.preferences.themeHelper")}
                          </Typography>
                          <SegmentedControl
                            fullWidth
                            value={userPreferences.theme}
                            aria-label={t("profile.preferences.theme")}
                            onChange={(value) => {
                              void onThemeChange(value as Theme);
                            }}
                            sx={{
                              "& .MuiToggleButtonGroup-grouped": {
                                height: 40,
                              },
                            }}
                            options={themeOptions}
                          />
                        </Box>

                        <Box>
                          <Typography variant="subtitle2" gutterBottom>
                            {t("profile.preferences.language")}
                          </Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                            {t("profile.preferences.languageHelper")}
                          </Typography>
                          <FormControl fullWidth>
                            <Select
                              value={userPreferences.language}
                              onChange={(event) => {
                                void onLanguageChange(event.target.value as Language);
                              }}
                              inputProps={{ "aria-label": t("profile.preferences.language") }}
                            >
                              <MenuItem value={Language.English}>
                                {t("language.english")}
                              </MenuItem>
                              <MenuItem value={Language.Portuguese}>
                                {t("language.portuguese")}
                              </MenuItem>
                            </Select>
                          </FormControl>
                        </Box>
                      </Box>
                    </Box>
                    <Divider />

                    <Box>
                      <Stack gap={1}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <PersonIcon fontSize="small" />
                          <Label sx={{ mb: 0 }}>
                            {t("profile.preferences.session")}
                          </Label>
                        </Box>
                        <Box
                          sx={{
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between",
                            py: 0.5,
                            gap: 2,
                          }}
                        >
                          <Box>
                            <Typography variant="subtitle2">
                              {t("profile.preferences.keepMeConnected")}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {t("profile.preferences.keepMeConnectedHelper")}
                            </Typography>
                          </Box>
                          <Switch
                            checked={userPreferences.keepMeConnected}
                            onChange={(_, checked) => {
                              void onKeepConnectedChange(checked);
                            }}
                            inputProps={{
                              "aria-label": t("profile.preferences.keepMeConnected"),
                            }}
                          />
                        </Box>
                      </Stack>
                    </Box>
                    <Divider />

                    <Box>
                      <Stack gap={1}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <RuleIcon fontSize="small" />
                          <Label sx={{ mb: 0 }}>
                            {t("profile.preferences.banking")}
                          </Label>
                        </Box>

                        <Box
                          sx={{
                            display: "grid",
                            gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                            gap: 2,
                            py: 2,
                          }}
                        >
                          <Box>
                            <Typography variant="subtitle2" gutterBottom>
                              {t("profile.preferences.ruleCreationMode")}
                            </Typography>
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                              {t("profile.preferences.ruleCreationModeHelper")}
                            </Typography>
                            <FormControl fullWidth>
                              <Select
                                value={bankingUserPreferences.ruleCreationMode}
                                onChange={(event) => {
                                  void onRuleModeChange(
                                    event.target.value as RuleCreationMode,
                                  );
                                }}
                                inputProps={{
                                  "aria-label": t(
                                    "profile.preferences.ruleCreationMode",
                                  ),
                                }}
                              >
                                <MenuItem value={RuleCreationMode.Never}>
                                  {t("profile.preferences.ruleNever")}
                                </MenuItem>
                                <MenuItem value={RuleCreationMode.Always}>
                                  {t("profile.preferences.ruleAlways")}
                                </MenuItem>
                                <MenuItem value={RuleCreationMode.AlwaysPrompt}>
                                  {t("profile.preferences.ruleAlwaysPrompt")}
                                </MenuItem>
                              </Select>
                            </FormControl>
                          </Box>
                          <Box sx={{ display: { xs: "none", sm: "block" } }} />
                        </Box>
                      </Stack>
                    </Box>
                  </Stack>
                )}
              </Stack>
            </Paper>
          </Stack>
        </Stack>
      </Container>
    </Page>
  );
}
