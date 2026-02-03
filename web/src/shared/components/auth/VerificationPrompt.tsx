"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Box, Typography, Button, CircularProgress } from "@mui/material";
import { useMutation } from "@apollo/client/react";
import { RESEND_VERIFICATION_EMAIL } from "@/lib/graphql/operations/auth/mutations";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { verifyEmailTranslations } from "@/app/(authentication)/verify-email/i18n";
import { extractErrorMessage } from "@/shared/utils/error";

export interface VerificationPromptProps {
  email: string;
  onSuccess?: () => void;
}

export function VerificationPrompt({ email }: VerificationPromptProps) {
  const { t } = useTranslation(verifyEmailTranslations);
  const router = useRouter();
  const { success: showSuccess, error: showError } = useToast();
  const [resendVerification, { loading: resending }] = useMutation(RESEND_VERIFICATION_EMAIL, {
    onCompleted: (data) => {
      if (data?.resendVerificationEmail?.success) {
        showSuccess(t("resendSuccess"));
      } else if (data?.resendVerificationEmail?.message) {
        showError(data.resendVerificationEmail.message);
      }
    },
    onError: (err) => showError(extractErrorMessage(err, t("errors.unknownError"))),
  });

  const handleResend = () => {
    resendVerification();
  };

  return (
    <Box sx={{ width: "100%" }}>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
        {t("description", { email })}
      </Typography>

      <Button
        type="button"
        variant="contained"
        fullWidth
        onClick={handleResend}
        disabled={resending}
        sx={{ mb: 2 }}
      >
        {resending ? <CircularProgress size={24} color="inherit" /> : t("resend")}
      </Button>

      <Button type="button" variant="text" fullWidth onClick={() => router.push("/")} disabled={resending}>
        {t("skip")}
      </Button>
    </Box>
  );
}
