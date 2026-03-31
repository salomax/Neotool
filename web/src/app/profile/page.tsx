"use client";

import * as React from "react";
import Avatar from "@mui/material/Avatar";
import Divider from "@mui/material/Divider";
import Typography from "@mui/material/Typography";
import { Page, Container, Paper, Stack } from "@/shared/components/ui/layout";
import { Box } from "@/shared/components/ui/layout/Box";
import { useAuth } from "@/shared/providers";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "react-i18next";

export default function ProfilePage() {
  const { t } = useTranslation("common");
  const { user } = useAuth();

  usePageTitle(t("routes.profile"));

  const displayName = user?.displayName || t("appHeader.defaultUser");

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
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Page>
  );
}
