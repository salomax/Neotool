"use client";

import * as React from "react";
import { useForm, FormProvider, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Box,
  Typography,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
} from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import { TextField } from "@/shared/components/ui/forms/form/TextField";
import { PasswordField } from "@/shared/components/ui/forms/form/PasswordField";
import { Button } from "@/shared/components/ui/primitives/Button";
import { Stack } from "@/shared/components/ui/layout/Stack";
import NextLink from "next/link";
import { Link } from "@/shared/components/ui/navigation/Link";
import { useAuth } from "@/shared/providers";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { signupTranslations } from "@/app/signup/i18n";
import { validatePassword, passwordValidationRules } from "@/shared/utils/passwordValidation";
import { extractErrorMessage } from "@/shared/utils/error";

const signUpSchema = z.object({
  name: z.string().min(1, "errors.required"),
  email: z.string().email("errors.invalidEmail").min(1, "errors.required"),
  password: z
    .string()
    .min(1, "errors.required")
    .refine(
      (password) => {
        const validation = validatePassword(password);
        return validation.isValid;
      },
      {
        message: "errors.invalidPassword",
      }
    ),
});

type SignUpFormData = z.infer<typeof signUpSchema>;

export interface SignUpFormProps {
  onSuccess?: () => void;
}

export const SignUpForm: React.FC<SignUpFormProps> = ({ onSuccess }) => {
  const { t, tCommon } = useTranslation(signupTranslations);
  const { signUp } = useAuth();
  const { error: showError } = useToast();
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const methods = useForm<SignUpFormData>({
    resolver: zodResolver(signUpSchema),
    defaultValues: {
      name: "",
      email: "",
      password: "",
    },
  });

  const passwordValue = methods.watch("password");
  const passwordValidation = React.useMemo(() => {
    if (!passwordValue || passwordValue.length === 0) {
      return null;
    }
    return validatePassword(passwordValue);
  }, [passwordValue]);

  const onSubmit = async (data: SignUpFormData) => {
    try {
      setIsSubmitting(true);
      setError(null);
      // Trim name and email before submission
      const trimmedName = data.name.trim();
      const trimmedEmail = data.email.trim();
      await signUp(trimmedName, trimmedEmail, data.password);
      onSuccess?.();
    } catch (err: any) {
      console.error("Sign up error:", err);
      const errorMessage = extractErrorMessage(err, t("errors.unknownError"));
      setError(errorMessage);
      showError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <FormProvider {...methods}>
      <Box
        component="form"
        onSubmit={methods.handleSubmit(onSubmit)}
        sx={{ width: "100%" }}
        data-testid="signup-form"
        noValidate
        aria-label={t("signUpButton")}
      >
        <Stack gap={3} name="signup-form-stack">
          {error && (
            <Alert
              severity="error"
              data-testid="signup-error"
              role="alert"
              aria-live="assertive"
            >
              {error}
            </Alert>
          )}

          <Controller
            name="name"
            control={methods.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                type="text"
                label={t("name")}
                error={!!fieldState.error}
                helperText={
                  fieldState.error
                    ? t(fieldState.error.message || "errors.required")
                    : ""
                }
                fullWidth
                autoComplete="name"
                autoFocus
                data-testid="textfield-name"
                aria-required="true"
                aria-invalid={!!fieldState.error}
                aria-describedby={fieldState.error ? "name-error" : undefined}
              />
            )}
          />

          <Controller
            name="email"
            control={methods.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                type="email"
                label={t("email")}
                error={!!fieldState.error}
                helperText={
                  fieldState.error
                    ? t(fieldState.error.message || "errors.required")
                    : ""
                }
                fullWidth
                autoComplete="email"
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
              translateError={(key) => t(key)}
              data-testid="textfield-password"
            />
            {passwordValidation && (
              <Box
                sx={{
                  mt: 1.5,
                  p: 1.5,
                  borderRadius: 1,
                  bgcolor: "background.paper",
                  border: 1,
                  borderColor: "divider",
                }}
                data-testid="password-validation"
              >
                <Typography
                  variant="caption"
                  sx={{ fontWeight: 600, mb: 1, display: "block" }}
                >
                  {t("passwordRequirements")}
                </Typography>
                <List dense sx={{ py: 0 }}>
                  {passwordValidation.rules.map((rule, index) => {
                    const ruleKey = `passwordRules.${index}`;
                    const translatedLabel = t(ruleKey);
                    // Fallback to original label if translation not found
                    const displayLabel = translatedLabel !== ruleKey ? translatedLabel : rule.label;
                    
                    return (
                      <ListItem
                        key={index}
                        sx={{ py: 0.5, px: 0 }}
                        data-testid={`password-rule-${index}`}
                      >
                        <ListItemIcon sx={{ minWidth: 32 }}>
                          {rule.isValid ? (
                            <CheckCircleIcon
                              sx={{ color: "success.main", fontSize: 18 }}
                              aria-label="Valid"
                            />
                          ) : (
                            <CancelIcon
                              sx={{ color: "error.main", fontSize: 18 }}
                              aria-label="Invalid"
                            />
                          )}
                        </ListItemIcon>
                        <ListItemText
                          primary={
                            <Typography
                              variant="caption"
                              sx={{
                                color: rule.isValid ? "success.main" : "error.main",
                              }}
                            >
                              {displayLabel}
                            </Typography>
                          }
                        />
                      </ListItem>
                    );
                  })}
                </List>
              </Box>
            )}
          </Box>

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            loading={isSubmitting}
            loadingText={tCommon("actions.loading")}
            disabled={isSubmitting}
            data-testid="button-signup"
            name="signup-submit"
            aria-label={t("signUpButton")}
          >
            {t("signUpButton")}
          </Button>

          <Box sx={{ textAlign: "center", mt: 2 }}>
            <Typography variant="body2" color="text.secondary" component="span">
              {t("alreadyHaveAccount")}{" "}
            </Typography>
            <Link component={NextLink} href="/signin" name="signin-link">
              {t("signIn")}
            </Link>
          </Box>
        </Stack>
      </Box>
    </FormProvider>
  );
};

