"use client";

import * as React from "react";
import { useForm, FormProvider, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Box,
  Alert,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { PasswordField } from "@/shared/components/ui/forms/form/PasswordField";
import { Button } from "@/shared/components/ui/primitives/Button";
import { Stack } from "@/shared/components/ui/layout/Stack";
import NextLink from "next/link";
import { Link } from "@/shared/components/ui/navigation/Link";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { resetPasswordTranslations } from "@/app/(authentication)/reset-password/i18n";
import { extractErrorMessage } from "@/shared/utils/error";
import { useMutation } from "@apollo/client/react";
import {
  ResetPasswordDocument,
  type ResetPasswordMutation,
  type ResetPasswordMutationVariables,
} from "@/lib/graphql/operations/auth/mutations.generated";
import { useRouter } from "next/navigation";

const resetPasswordSchema = z.object({
  newPassword: z.string().min(8, "errors.weakPassword").refine(
    (password) => {
      const hasUppercase = /[A-Z]/.test(password);
      const hasLowercase = /[a-z]/.test(password);
      const hasNumber = /[0-9]/.test(password);
      const hasSpecialChar = /[^A-Za-z0-9]/.test(password);
      return hasUppercase && hasLowercase && hasNumber && hasSpecialChar;
    },
    { message: "errors.weakPassword" }
  ),
  confirmPassword: z.string().min(1, "errors.required"),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: "errors.passwordMismatch",
  path: ["confirmPassword"],
});

type ResetPasswordFormData = z.infer<typeof resetPasswordSchema>;

export interface ResetPasswordFormProps {
  token: string;
  onSuccess?: () => void;
}

export const ResetPasswordForm: React.FC<ResetPasswordFormProps> = ({ token, onSuccess }) => {
  const { t, tCommon } = useTranslation(resetPasswordTranslations);
  const { success: showSuccess, error: showError } = useToast();
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState(false);

  const [resetPassword] = useMutation<
    ResetPasswordMutation,
    ResetPasswordMutationVariables
  >(ResetPasswordDocument);

  const methods = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      newPassword: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (data: ResetPasswordFormData) => {
    try {
      setIsSubmitting(true);
      setError(null);
      setSuccess(false);

      const result = await resetPassword({
        variables: {
          input: {
            token,
            newPassword: data.newPassword,
          },
        },
      });

      if (result.data?.resetPassword?.success) {
        setSuccess(true);
        showSuccess(t("successMessage"));
        setTimeout(() => {
          router.push("/signin");
        }, 2000);
        onSuccess?.();
      } else {
        throw new Error("Failed to reset password");
      }
    } catch (err: any) {
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
        data-testid="reset-password-form"
        noValidate
        aria-label={t("resetPassword")}
      >
        <Stack gap={3} name="reset-password-form-stack">
          <ErrorAlert error={error} data-testid="reset-password-error" />

          <PasswordField
            name="newPassword"
            label={t("newPassword")}
            translateError={(key) => t(key)}
            data-testid="textfield-new-password"
          />

          <PasswordField
            name="confirmPassword"
            label={t("confirmPassword")}
            translateError={(key) => t(key)}
            data-testid="textfield-confirm-password"
          />

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            loading={isSubmitting}
            loadingText={tCommon("actions.loading")}
            disabled={isSubmitting}
            data-testid="button-reset-password"
            name="reset-password-submit"
            aria-label={t("resetPassword")}
          >
            {t("resetPassword")}
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
