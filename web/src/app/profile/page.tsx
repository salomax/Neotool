"use client";

import * as React from "react";
import Avatar from "@mui/material/Avatar";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Typography from "@mui/material/Typography";
import RuleIcon from "@mui/icons-material/Rule";
import ColorLensIcon from "@mui/icons-material/ColorLens";
import { Page, Container, Paper, Stack } from "@/shared/components/ui/layout";
import { Box } from "@/shared/components/ui/layout/Box";
import { SegmentedControl } from "@/shared/components/ui/inputs";
import { Label } from "@/shared/components/ui/primitives";
import { useAuth, usePreferences, useToast } from "@/shared/providers";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "react-i18next";
import { Language, RuleApplicationMode, Theme } from "@/lib/graphql/types/__generated__/graphql";

export default function ProfilePage() {
  const { t } = useTranslation("common");
  const { user } = useAuth();
  const { userPreferences, bankingUserPreferences, updateUserPreferences, updateBankingUserPreferences, isLoading } = usePreferences();
  const toast = useToast();

  usePageTitle(t("routes.profile"));

  const displayName = user?.displayName || t("appHeader.defaultUser");
  const showUpdateError = React.useCallback(() => {
    toast.error(t("profile.preferences.updateError"));
  }, [t, toast]);

  const onThemeChange = React.useCallback(async (nextTheme: Theme) => {
    try {
      await updateUserPreferences({ theme: nextTheme });
    } catch {
      showUpdateError();
    }
  }, [showUpdateError, updateUserPreferences]);

  const onLanguageChange = React.useCallback(async (nextLanguage: Language) => {
    try {
      await updateUserPreferences({ language: nextLanguage });
    } catch {
      showUpdateError();
    }
  }, [showUpdateError, updateUserPreferences]);

  const onRuleModeChange = React.useCallback(async (nextMode: RuleApplicationMode) => {
    try {
      await updateBankingUserPreferences({ ruleApplicationMode: nextMode });
    } catch {
      showUpdateError();
    }
  }, [showUpdateError, updateBankingUserPreferences]);

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
            </Stack>
          </Paper>
          <Stack gap={1}>
            <Label sx={{ mb: 0, pl: 2, fontSize: (theme) => theme.typography.subtitle1.fontSize }}>
              {t("profile.preferences.title")}
            </Label>
            <Paper elevation={1} name="profile-preferences">
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
                              <MenuItem value={Language.English}>{t("language.english")}</MenuItem>
                              <MenuItem value={Language.Portuguese}>{t("language.portuguese")}</MenuItem>
                            </Select>
                          </FormControl>
                        </Box>
                      </Box>
                    </Box>
                    <Divider />

                    <Box>
                      <Stack gap={1}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <RuleIcon fontSize="small" />
                          <Label sx={{ mb: 0 }}>{t("profile.preferences.banking")}</Label>
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
                              {t("profile.preferences.ruleApplicationMode")}
                            </Typography>
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                              {t("profile.preferences.ruleApplicationModeHelper")}
                            </Typography>
                            <FormControl fullWidth>
                              <Select
                                value={bankingUserPreferences.ruleApplicationMode}
                                onChange={(event) => {
                                  void onRuleModeChange(event.target.value as RuleApplicationMode);
                                }}
                                inputProps={{ "aria-label": t("profile.preferences.ruleApplicationMode") }}
                              >
                                <MenuItem value={RuleApplicationMode.Never}>{t("profile.preferences.ruleNever")}</MenuItem>
                                <MenuItem value={RuleApplicationMode.Always}>{t("profile.preferences.ruleAlways")}</MenuItem>
                                <MenuItem value={RuleApplicationMode.AlwaysPrompt}>
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
