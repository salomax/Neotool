"use client";

import * as React from "react";
import { useForm, FormProvider, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Box,
  Typography,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { TextField } from "@/shared/components/ui/forms/form/TextField";
import { Button } from "@/shared/components/ui/primitives/Button";
import { Stack } from "@/shared/components/ui/layout/Stack";
import NextLink from "next/link";
import { Link } from "@/shared/components/ui/navigation/Link";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { forgotPasswordTranslations } from "@/app/(authentication)/forgot-password/i18n";
import { extractErrorMessage } from "@/shared/utils/error";
import { useRequestPasswordResetMutation } from "@/lib/graphql/operations/auth/mutations.generated";
import { useRouter } from "next/navigation";

const forgotPasswordSchema = z.object({
  email: z.string().email("errors.invalidEmail").min(1, "errors.required"),
});

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;

export interface ForgotPasswordFormProps {
  onSuccess?: () => void;
}

export const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({ onSuccess }) => {
  const { t, tCommon } = useTranslation(forgotPasswordTranslations);
  const { success: showSuccess, error: showError } = useToast();
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState(false);

  const [requestPasswordReset] = useRequestPasswordResetMutation();

  const methods = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: "",
    },
  });

  const onSubmit = async (data: ForgotPasswordFormData) => {
    try {
      setIsSubmitting(true);
      setError(null);
      setSuccess(false);

      // Trim email before submission
      const trimmedEmail = data.email.trim();

      const result = await requestPasswordReset({
        variables: {
          input: {
            email: trimmedEmail,
            locale: "en", // TODO: Get from i18n context
          },
        },
      });

      if (result.data?.requestPasswordReset?.success) {
        setSuccess(true);
        showSuccess(t("successMessage"));
        onSuccess?.();
      } else {
        throw new Error("Failed to send reset link");
      }
    } catch (err: any) {
      console.error("Forgot password error:", err);
      const errorMessage = extractErrorMessage(err, t("errors.unknownError"));
      setError(errorMessage);
      showError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (success) {
    return (
      <Box>
        <Alert severity="success" sx={{ mb: 3 }}>
          {t("successMessage")}
        </Alert>
        <Box sx={{ textAlign: "center" }}>
          <Link component={NextLink} href="/signin" name="back-to-signin">
            {t("backToSignIn")}
          </Link>
        </Box>
      </Box>
    );
  }

  return (
    <FormProvider {...methods}>
      <Box
        component="form"
        onSubmit={methods.handleSubmit(onSubmit)}
        sx={{ width: "100%" }}
        data-testid="forgot-password-form"
        noValidate
        aria-label={t("sendResetLink")}
      >
        <Stack gap={3} name="forgot-password-form-stack">
          {error && (
            <ErrorAlert error={error} />
          )}

          <Controller
            name="email"
            control={methods.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                type="email"
                label={t("email")}
                error={!!fieldState.error}
                helperText={fieldState.error ? t(fieldState.error.message || "errors.required") : ""}
                fullWidth
                autoComplete="email"
                autoFocus
                data-testid="textfield-email"
                aria-required="true"
                aria-invalid={!!fieldState.error}
                aria-describedby={fieldState.error ? "email-error" : undefined}
              />
            )}
          />

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            loading={isSubmitting}
            loadingText={tCommon("actions.loading")}
            disabled={isSubmitting}
            data-testid="button-send-reset-link"
            name="send-reset-link"
            aria-label={t("sendResetLink")}
          >
            {t("sendResetLink")}
          </Button>

          <Box sx={{ textAlign: "center", mt: 2 }}>
            <Link component={NextLink} href="/signin" name="back-to-signin">
              {t("backToSignIn")}
            </Link>
          </Box>
        </Stack>
      </Box>
    </FormProvider>
  );
};

