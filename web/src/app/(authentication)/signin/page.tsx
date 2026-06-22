"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Container, Box, Typography, Alert, AlertTitle } from "@mui/material";
import VerifiedUserOutlinedIcon from "@mui/icons-material/VerifiedUserOutlined";
import { SignInForm } from "@/shared/components/auth/SignInForm";
import { LoadingSpinner } from "@/shared/components/ui/primitives/LoadingSpinner";
import { useAuth } from "@/shared/providers";
import { Logo } from "@/shared/ui/brand";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { resolvePostLoginRedirect } from "@/shared/utils/auth/redirect-target";
import { signinTranslations } from "./i18n";

export default function SignInPage() {
  const { t } = useTranslation(signinTranslations);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading, setPostLoginRedirect } = useAuth();
  const resolvedTarget = React.useMemo(
    () => resolvePostLoginRedirect(searchParams, "/"),
    [searchParams]
  );

  // Set post-login redirect so AuthProvider can send user to intended target after sign-in
  React.useEffect(() => {
    setPostLoginRedirect(resolvedTarget);
  }, [resolvedTarget, setPostLoginRedirect]);

  // Redirect if already authenticated
  React.useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.push(resolvedTarget);
    }
  }, [isAuthenticated, isLoading, router, resolvedTarget]);

  if (isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }} data-testid="signin-loading">
        <LoadingSpinner />
      </Container>
    );
  }

  if (isAuthenticated) {
    return null; // Will redirect
  }

  return (
    <Box
      sx={{
        minHeight: "100%",
        display: "flex",
        alignItems: { xs: "stretch", sm: "center" },
        justifyContent: "center",
        bgcolor: "background.default",
        py: { xs: 4, sm: 8 },
        px: { xs: 2, sm: 3 },
      }}
      data-testid="signin-screen"
    >
      <Container maxWidth="sm" sx={{ width: "100%", pb: { xs: 3, sm: 0 } }}>
        <Stack gap={4} align="center" name="signin-page-stack">
          <Box sx={{ mb: 2 }}>
            <Logo variant="blue" size="large" />
          </Box>

          <Paper
            elevation={0}
            sx={{
              width: "100%",
              maxWidth: 440,
              p: { xs: 3, sm: 4, md: 5 },
              borderRadius: 2,
              border: 1,
              borderColor: "divider",
            }}
            name="signin-card"
          >
            <Stack gap={3} name="signin-content-stack">
              {resolvedTarget !== "/" && (
                <Alert
                  icon={<VerifiedUserOutlinedIcon fontSize="inherit" />}
                  severity="info"
                  sx={{
                    alignItems: "flex-start",
                    bgcolor: (theme) => theme.custom.palette.infoLightBg,
                    border: 1,
                    borderColor: "info.light", // Or a specific color like 'rgba(2, 132, 199, 0.2)'
                    "& .MuiAlert-icon": {
                      color: "info.main",
                      mt: "4px",
                    },
                  }}
                  data-testid="signin-login-required-message"
                >
                  <AlertTitle sx={{ fontWeight: 600, color: "text.primary" }}>
                    {t("restrictedAccessTitle")}
                  </AlertTitle>
                  <Typography variant="body2" color="text.secondary">
                    {t("loginRequiredMessage")}
                  </Typography>
                </Alert>
              )}
              <Box sx={{ textAlign: "center", mb: 1 }}>
              <Typography
                  variant="h4"
                  component="h1"
                  gutterBottom
                  sx={{ fontWeight: 600 }}
                  data-testid="signin-title"
                >
                  {t("title")}
                </Typography>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mt: 1 }}
                  data-testid="signin-subtitle"
                >
                  {t("subtitle")}
                </Typography>
              </Box>
              <SignInForm />
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
