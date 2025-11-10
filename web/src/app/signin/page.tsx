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
      <Box
        sx={{
          width: "100vw",
          height: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          bgcolor: "background.default",
        }}
      >
        <Typography>Loading...</Typography>
      </Box>
    );
  }

  if (isAuthenticated) {
    return null; // Will redirect
  }

  return (
    <Box
      sx={{
        width: "100vw",
        height: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        bgcolor: "background.default",
        overflow: "hidden",
      }}
      data-testid="signin-screen"
    >
      <Container 
        maxWidth="sm" 
        sx={{ 
          width: "100%",
          px: { xs: 2, sm: 3 },
        }}
      >
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


