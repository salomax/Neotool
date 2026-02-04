"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Container, Box, Typography, Button, CircularProgress } from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { useAuth, useToast } from "@/shared/providers";
import { Logo } from "@/shared/ui/brand";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { Link } from "@/shared/components/ui/navigation/Link";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { verifyEmailLinkTranslations } from "./i18n";
import { extractErrorMessage } from "@/shared/utils/error";
import { useMutation } from "@apollo/client/react";
import { VerifyEmailWithTokenDocument } from "@/lib/graphql/operations/auth/mutations.generated";

export default function VerifyEmailLinkPage() {
  const { t } = useTranslation(verifyEmailLinkTranslations);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setUser } = useAuth();
  const { success: showSuccess, error: showError } = useToast();

  const token = searchParams.get("token");
  const hasValidToken = Boolean(token?.trim());

  const [verifyEmailWithToken, { loading, data, error }] = useMutation(VerifyEmailWithTokenDocument, {
    onCompleted: (result) => {
      if (result?.verifyEmailWithToken?.success) {
        const user = result.verifyEmailWithToken.user;
        if (user) {
          setUser(user);
          if (typeof window !== "undefined") {
            localStorage.setItem("auth_user", JSON.stringify(user));
          }
          sessionStorage.removeItem("pendingVerificationEmail");
        }
        showSuccess(t("success"));
        setTimeout(() => router.push("/"), 3000);
      } else if (result?.verifyEmailWithToken?.message) {
        showError(result.verifyEmailWithToken.message);
      }
    },
    onError: (err) => showError(extractErrorMessage(err, t("errors.unknownError"))),
  });

  const hasVerifiedRef = React.useRef(false);
  React.useEffect(() => {
    if (!hasValidToken || hasVerifiedRef.current) return;
    hasVerifiedRef.current = true;
    verifyEmailWithToken({ variables: { token: token!.trim() } });
  }, [hasValidToken, token, verifyEmailWithToken]);

  const success = data?.verifyEmailWithToken?.success === true;
  const errorMessage =
    error
      ? extractErrorMessage(error, t("errors.unknownError"))
      : data?.verifyEmailWithToken?.success === false
        ? data?.verifyEmailWithToken?.message ?? t("errors.unknownError")
        : null;

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
      data-testid="verify-email-link-screen"
    >
      <Container maxWidth="sm" sx={{ width: "100%" }}>
        <Stack gap={4} align="center" name="verify-email-link-page-stack">
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
            name="verify-email-link-card"
          >
            <Stack gap={3} name="verify-email-link-content-stack">
              <Box sx={{ textAlign: "center", mb: 1 }}>
                <Typography
                  variant="h4"
                  component="h1"
                  gutterBottom
                  sx={{ fontWeight: 600 }}
                >
                  {t("title")}
                </Typography>
              </Box>

              {!hasValidToken ? (
                <>
                  <ErrorAlert error={t("errors.invalidLink")} />
                  <Stack direction="row" gap={2} justify="center" flexWrap="wrap">
                    <Link href="/verify-email" component={Typography}>
                      {t("linkToVerifyEmail")}
                    </Link>
                    <Link href="/signin" component={Typography}>
                      {t("linkToSignIn")}
                    </Link>
                  </Stack>
                </>
              ) : loading && !data ? (
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 2, py: 2 }}>
                  <CircularProgress size={24} />
                  <Typography color="text.secondary">{t("loading")}</Typography>
                </Box>
              ) : success ? (
                <>
                  <Typography color="success.main">{t("success")}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t("redirectNotice")}
                  </Typography>
                </>
              ) : errorMessage ? (
                <>
                  <ErrorAlert error={errorMessage} />
                  <Stack direction="row" gap={2} justify="center" flexWrap="wrap">
                    <Button component={Link} href="/verify-email" variant="outlined" size="small">
                      {t("resendCode")}
                    </Button>
                    <Button component={Link} href="/signin" variant="text" size="small">
                      {t("linkToSignIn")}
                    </Button>
                  </Stack>
                </>
              ) : null}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
