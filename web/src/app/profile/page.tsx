"use client";

import * as React from "react";
import Avatar from "@mui/material/Avatar";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Typography from "@mui/material/Typography";
import { Page, Container, Paper, Stack } from "@/shared/components/ui/layout";
import { Box } from "@/shared/components/ui/layout/Box";
import { useAuth, usePreferences, useToast } from "@/shared/providers";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "react-i18next";
import { Language, RuleApplicationMode, Theme, TransactionStatus } from "@/lib/graphql/types/__generated__/graphql";

export default function ProfilePage() {
  const { t } = useTranslation("common");
  const { user } = useAuth();
  const { userPreferences, accountPreferences, updateUserPreferences, updateAccountPreferences, isOwner, isLoading } = usePreferences();
  const toast = useToast();

  usePageTitle(t("routes.profile"));

  const displayName = user?.displayName || t("appHeader.defaultUser");
  const ownerOnlyHelper = t(
    "profile.preferences.ownerOnly",
    "Only the account owner can change this setting",
  );

  const onThemeChange = async (nextTheme: Theme) => {
    try {
      await updateUserPreferences({ theme: nextTheme });
    } catch {
      toast.error(t("profile.preferences.updateError", "Could not save preference. Please try again."));
    }
  };

  const onLanguageChange = async (nextLanguage: Language) => {
    try {
      await updateUserPreferences({ language: nextLanguage });
    } catch {
      toast.error(t("profile.preferences.updateError", "Could not save preference. Please try again."));
    }
  };

  const onRuleModeChange = async (nextMode: RuleApplicationMode) => {
    try {
      await updateAccountPreferences({ ruleApplicationMode: nextMode });
    } catch {
      toast.error(t("profile.preferences.updateError", "Could not save preference. Please try again."));
    }
  };

  const onDefaultStatusChange = async (nextStatus: TransactionStatus) => {
    try {
      await updateAccountPreferences({ defaultTransactionStatus: nextStatus });
    } catch {
      toast.error(t("profile.preferences.updateError", "Could not save preference. Please try again."));
    }
  };

  return (
    <Page>
      <Container maxWidth="md" sx={{ py: { xs: 3, sm: 6 } }}>
        <Stack gap={3}>
          <Paper elevation={1} name="profile-user-card">
            <Stack gap={2}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 2, minWidth: 0 }}>
                <Avatar
                  variant="rounded"
                  src={user?.avatarUrl || undefined}
                  sx={(theme) => ({
                    width: 64,
                    height: 64,
                    flexShrink: 0,
                    borderRadius: `${theme.custom.radius.lg}px`,
                  })}
                />
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="h6" noWrap>
                    {displayName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {user?.email || ""}
                  </Typography>
                </Box>
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
                    {t("profile.emailVerified", "Email verified")}
                  </Typography>
                  <Typography variant="body2">
                    {user?.emailVerified ? t("profile.yes", "Yes") : t("profile.no", "No")}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {t("profile.plan", "Plan")}
                  </Typography>
                  <Typography variant="body2">{user?.plan || "—"}</Typography>
                </Box>
              </Box>
            </Stack>
          </Paper>
          <Paper elevation={1} name="profile-preferences">
            <Stack gap={2}>
              <Typography variant="h6">{t("profile.preferences.title")}</Typography>
              {isLoading ? (
                <Typography variant="body2" color="text.secondary">
                  {t("profile.preferences.loading", "Loading preferences...")}
                </Typography>
              ) : (
                <Stack gap={3}>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      {t("profile.preferences.appearance", "Appearance")}
                    </Typography>
                    <Stack gap={2}>
                      <FormControl fullWidth>
                        <InputLabel id="profile-theme-label">
                          {t("profile.preferences.theme", "Theme")}
                        </InputLabel>
                        <Select
                          labelId="profile-theme-label"
                          value={userPreferences.theme}
                          label={t("profile.preferences.theme", "Theme")}
                          onChange={(event) => onThemeChange(event.target.value as Theme)}
                        >
                          <MenuItem value={Theme.Light}>
                            {t("profile.preferences.themeLight", "Light")}
                          </MenuItem>
                          <MenuItem value={Theme.Dark}>
                            {t("profile.preferences.themeDark", "Dark")}
                          </MenuItem>
                          <MenuItem value={Theme.System}>
                            {t("profile.preferences.themeSystem", "System")}
                          </MenuItem>
                        </Select>
                      </FormControl>

                      <FormControl fullWidth>
                        <InputLabel id="profile-language-label">
                          {t("profile.preferences.language", "Language")}
                        </InputLabel>
                        <Select
                          labelId="profile-language-label"
                          value={userPreferences.language}
                          label={t("profile.preferences.language", "Language")}
                          onChange={(event) => onLanguageChange(event.target.value as Language)}
                        >
                          <MenuItem value={Language.English}>English</MenuItem>
                          <MenuItem value={Language.Portuguese}>Português</MenuItem>
                        </Select>
                      </FormControl>
                    </Stack>
                  </Box>

                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      {t("profile.preferences.banking", "Banking")}
                    </Typography>
                    <Stack gap={2}>
                      <FormControl fullWidth disabled={!isOwner}>
                        <InputLabel id="profile-rule-mode-label">
                          {t("profile.preferences.ruleApplicationMode", "Apply transaction rules")}
                        </InputLabel>
                        <Select
                          labelId="profile-rule-mode-label"
                          value={accountPreferences.ruleApplicationMode}
                          label={t("profile.preferences.ruleApplicationMode", "Apply transaction rules")}
                          onChange={(event) => onRuleModeChange(event.target.value as RuleApplicationMode)}
                        >
                          <MenuItem value={RuleApplicationMode.Never}>
                            {t("profile.preferences.ruleNever", "Never")}
                          </MenuItem>
                          <MenuItem value={RuleApplicationMode.Always}>
                            {t("profile.preferences.ruleAlways", "Always")}
                          </MenuItem>
                          <MenuItem value={RuleApplicationMode.AlwaysPrompt}>
                            {t("profile.preferences.ruleAlwaysPrompt", "Always ask")}
                          </MenuItem>
                        </Select>
                        {!isOwner ? <FormHelperText>{ownerOnlyHelper}</FormHelperText> : null}
                      </FormControl>

                      <FormControl fullWidth disabled={!isOwner}>
                        <InputLabel id="profile-default-status-label">
                          {t("profile.preferences.defaultTransactionStatus", "Default transaction status")}
                        </InputLabel>
                        <Select
                          labelId="profile-default-status-label"
                          value={accountPreferences.defaultTransactionStatus}
                          label={t("profile.preferences.defaultTransactionStatus", "Default transaction status")}
                          onChange={(event) => onDefaultStatusChange(event.target.value as TransactionStatus)}
                        >
                          <MenuItem value={TransactionStatus.Posted}>
                            {t("profile.preferences.statusPosted", "Posted")}
                          </MenuItem>
                          <MenuItem value={TransactionStatus.Pending}>
                            {t("profile.preferences.statusPending", "Pending")}
                          </MenuItem>
                        </Select>
                        {!isOwner ? <FormHelperText>{ownerOnlyHelper}</FormHelperText> : null}
                      </FormControl>
                    </Stack>
                  </Box>
                </Stack>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Page>
  );
}
