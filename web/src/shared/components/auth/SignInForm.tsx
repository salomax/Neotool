"use client";

import * as React from "react";
import { useForm, FormProvider, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Box,
  Typography,
  Button as MUIButton,
  Divider,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import GoogleIcon from "@mui/icons-material/Google";
import { TextField } from "@/shared/components/ui/forms/form/TextField";
import { PasswordField } from "@/shared/components/ui/forms/form/PasswordField";
import { CheckboxField } from "@/shared/components/ui/forms/form/CheckboxField";
import { Button } from "@/shared/components/ui/primitives/Button";
import { Stack } from "@/shared/components/ui/layout/Stack";
import NextLink from "next/link";
import { Link } from "@/shared/components/ui/navigation/Link";
import { useAuth } from "@/shared/providers";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { signinTranslations } from "@/app/(authentication)/signin/i18n";
import { extractErrorMessage } from "@/shared/utils/error";
import { useOAuth } from "@/shared/hooks/auth";

const signInSchema = z.object({
  email: z.string().email("errors.invalidEmail").min(1, "errors.required"),
  password: z.string().min(1, "errors.required"),
  rememberMe: z.boolean().optional(),
});

type SignInFormData = z.infer<typeof signInSchema>;

export interface SignInFormProps {
  onSuccess?: () => void;
}

export const SignInForm: React.FC<SignInFormProps> = ({ onSuccess }) => {
  const { t, tCommon } = useTranslation(signinTranslations);
  const { signIn, signInWithOAuth } = useAuth();
  const { error: showError } = useToast();
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [isOAuthLoading, setIsOAuthLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const { signIn: signInOAuth } = useOAuth({
    onSuccess: async (idToken) => {
      try {
        setIsOAuthLoading(true);
        setError(null);
        await signInWithOAuth("google", idToken, false);
        onSuccess?.();
      } catch (err: any) {
        const errorMessage = extractErrorMessage(err, t("errors.unknownError"));
        setError(errorMessage);
        showError(errorMessage);
      } finally {
        setIsOAuthLoading(false);
      }
    },
    onError: (err) => {
      const errorMessage = extractErrorMessage(err, t("errors.unknownError"));
      setError(errorMessage);
      showError(errorMessage);
      setIsOAuthLoading(false);
    },
  });

  const methods = useForm<SignInFormData>({
    resolver: zodResolver(signInSchema),
    defaultValues: {
      email: "",
      password: "",
      rememberMe: false,
    },
  });

  const onSubmit = async (data: SignInFormData) => {
    try {
      setIsSubmitting(true);
      setError(null);
      await signIn(data.email, data.password, data.rememberMe);
      onSuccess?.();
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err, t("errors.invalidCredentials"));
      setError(errorMessage);
      showError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGoogleSignIn = async () => {
    try {
      setIsOAuthLoading(true);
      setError(null);
      await signInOAuth("google");
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err, t("errors.unknownError"));
      setError(errorMessage);
      showError(errorMessage);
      setIsOAuthLoading(false);
    }
  };

  return (
    <FormProvider {...methods}>
      <Box
        component="form"
        onSubmit={methods.handleSubmit(onSubmit)}
        sx={{ width: "100%" }}
        data-testid="signin-form"
        noValidate
        aria-label={t("signInButton")}
      >
        <Stack gap={3} name="signin-form-stack">
          <ErrorAlert error={error} data-testid="signin-error" />

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

          <Box>
            <PasswordField
              name="password"
              label={t("password")}
              data-testid="textfield-password"
            />
            <Box sx={{ mt: 1, textAlign: "right" }}>
              <Link
                component={NextLink}
                href="/forgot-password"
                name="forgot-password"
                sx={{ fontSize: "0.875rem" }}
              >
                {t("forgotPassword")}
              </Link>
            </Box>
          </Box>

          <CheckboxField
            name="rememberMe"
            label={t("rememberMe")}
            data-testid="checkbox-remember-me"
          />

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            loading={isSubmitting}
            loadingText={tCommon("actions.loading")}
            disabled={isSubmitting}
            data-testid="button-signin"
            name="signin-submit"
            aria-label={t("signInButton")}
          >
            {t("signInButton")}
          </Button>

          <Divider sx={{ my: 1 }}>
            <Typography variant="body2" color="text.secondary" component="span">
              {t("or")}
            </Typography>
          </Divider>

          <MUIButton
            variant="outlined"
            fullWidth
            size="large"
            onClick={handleGoogleSignIn}
            disabled={isSubmitting || isOAuthLoading}
            startIcon={<GoogleIcon />}
            data-testid="button-google-signin"
            name="google-signin"
            aria-label={t("continueWithGoogle")}
            sx={{
              textTransform: "none",
              py: 1.5,
            }}
          >
            {isOAuthLoading ? tCommon("actions.loading") : t("continueWithGoogle")}
          </MUIButton>

          <Box sx={{ textAlign: "center", mt: 2 }}>
            <Typography variant="body2" color="text.secondary" component="span">
              {t("dontHaveAccount")}{" "}
            </Typography>
            <Link component={NextLink} href="/signup" name="signup-link">
              {t("signUp")}
            </Link>
          </Box>
        </Stack>
      </Box>
    </FormProvider>
  );
};

