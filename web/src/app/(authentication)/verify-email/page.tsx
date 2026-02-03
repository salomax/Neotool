"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Container, Box, Typography } from "@mui/material";
import { VerificationPrompt } from "@/shared/components/auth/VerificationPrompt";
import { useAuth } from "@/shared/providers";
import { Logo } from "@/shared/ui/brand";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { verifyEmailTranslations } from "./i18n";

export default function VerifyEmailPage() {
  const { t } = useTranslation(verifyEmailTranslations);
  const router = useRouter();
  const { isAuthenticated, isLoading, user } = useAuth();
  const [email, setEmail] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (typeof window !== "undefined") {
      const pending = sessionStorage.getItem("pendingVerificationEmail");
      setEmail(pending);
    }
  }, []);

  // Redirect if not authenticated and no pending verification
  React.useEffect(() => {
    if (!isLoading && !isAuthenticated && email === null) {
      const pending = sessionStorage.getItem("pendingVerificationEmail");
      if (!pending) {
        router.push("/signin");
      } else {
        setEmail(pending);
      }
    }
  }, [isLoading, isAuthenticated, email, router]);

  if (isLoading || (email === null && !isAuthenticated)) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Box sx={{ textAlign: "center" }}>
          <Typography>Loading...</Typography>
        </Box>
      </Container>
    );
  }

  const displayEmail = email || user?.email || "";

  if (!displayEmail) {
    router.push("/signin");
    return null;
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
      data-testid="verify-email-screen"
    >
      <Container maxWidth="sm" sx={{ width: "100%" }}>
        <Stack gap={4} align="center" name="verify-email-page-stack">
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
            name="verify-email-card"
          >
            <Stack gap={3} name="verify-email-content-stack">
              <Box sx={{ textAlign: "center", mb: 1 }}>
                <Typography variant="h4" component="h1" gutterBottom sx={{ fontWeight: 600 }}>
                  {t("title")}
                </Typography>
              </Box>

              <VerificationPrompt email={displayEmail} />
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
