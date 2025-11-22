"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Container, Box, Typography, Alert } from "@mui/material";
import { ResetPasswordForm } from "@/shared/components/auth/ResetPasswordForm";
import { useAuth } from "@/shared/providers";
import { Logo } from "@/shared/ui/brand";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { resetPasswordTranslations } from "./i18n";

export default function ResetPasswordPage() {
  const { t } = useTranslation(resetPasswordTranslations);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  const [token, setToken] = React.useState<string | null>(null);
  const [tokenError, setTokenError] = React.useState<string | null>(null);

  // Extract token from URL
  React.useEffect(() => {
    const tokenParam = searchParams.get("token");
    if (!tokenParam) {
      setTokenError(t("errors.invalidToken"));
    } else {
      setToken(tokenParam);
    }
  }, [searchParams, t]);

  // Redirect if already authenticated
  React.useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.push("/");
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Box sx={{ textAlign: "center" }}>
          <Typography>Loading...</Typography>
        </Box>
      </Container>
    );
  }

  if (isAuthenticated) {
    return null; // Will redirect
  }

  return (
    <Box
      sx={{
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        bgcolor: "background.default",
        py: { xs: 4, sm: 8 },
        px: { xs: 2, sm: 3 },
      }}
      data-testid="reset-password-screen"
    >
      <Container maxWidth="sm" sx={{ width: "100%" }}>
        <Stack gap={4} align="center" name="reset-password-page-stack">
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
            name="reset-password-card"
          >
            <Stack gap={3} name="reset-password-content-stack">
              <Box sx={{ textAlign: "center", mb: 1 }}>
                <Typography
                  variant="h4"
                  component="h1"
                  gutterBottom
                  sx={{ fontWeight: 600 }}
                >
                  {t("title")}
                </Typography>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mt: 1 }}
                >
                  {t("subtitle")}
                </Typography>
              </Box>

              {tokenError ? (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {tokenError}
                </Alert>
              ) : token ? (
                <ResetPasswordForm
                  token={token}
                  onSuccess={() => {
                    // Success is handled in the form component
                  }}
                />
              ) : (
                <Box sx={{ textAlign: "center" }}>
                  <Typography>Loading...</Typography>
                </Box>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}

