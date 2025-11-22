"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Container, Box, Typography } from "@mui/material";
import { SignInForm } from "@/shared/components/auth/SignInForm";
import { useAuth } from "@/shared/providers";
import { Logo } from "@/shared/ui/brand";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { signinTranslations } from "./i18n";

export default function SignInPage() {
  const { t } = useTranslation(signinTranslations);
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

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
      data-testid="signin-screen"
    >
      <Container maxWidth="sm" sx={{ width: "100%" }}>
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

              <SignInForm
                onSuccess={() => {
                  router.push("/");
                }}
              />
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}

