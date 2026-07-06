import { useState } from 'react';
import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, Button, TextInput, Snackbar } from 'react-native-paper';
import { Link, router } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/shared/hooks/useAuth';
import { useTheme } from '@/shared/hooks/useTheme';
import { translateAppError } from '@/lib/error-handling';

const createSignupSchema = (t: (key: string, defaultValue?: string) => string) =>
  z
    .object({
      name: z
        .string()
        .min(2, t('auth.validation.nameMinLength', 'Name must be at least 2 characters')),
      email: z.string().email(t('auth.validation.invalidEmail', 'Invalid email address')),
      password: z
        .string()
        .min(8, t('auth.validation.passwordMinLength', 'Password must be at least 8 characters')),
      confirmPassword: z
        .string()
        .min(8, t('auth.validation.passwordMinLength', 'Password must be at least 8 characters')),
    })
    .refine((data) => data.password === data.confirmPassword, {
      message: t('auth.validation.passwordsDoNotMatch', 'Passwords do not match'),
      path: ['confirmPassword'],
    });

type SignupFormData = {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
};

export default function SignupScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const { signup } = useAuth();
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const translateWithFallback = (key: string, fallback?: string) => t(key, fallback ?? key);

  const {
    control,
    clearErrors,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<SignupFormData>({
    resolver: zodResolver(createSignupSchema(translateWithFallback)),
    defaultValues: {
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
  });

  const onSubmit = async (data: SignupFormData) => {
    try {
      setLoading(true);
      setErrorMessage(null);
      clearErrors();
      await signup(data.name, data.email, data.password);
      router.replace('/(tabs)');
    } catch (error) {
      console.error('Signup failed:', error);
      const translatedError = translateAppError(error, t);

      if (translatedError.fieldErrors?.name) {
        setError('name', {
          message: t(translatedError.fieldErrors.name, translatedError.message),
        });
      }

      if (translatedError.fieldErrors?.email) {
        setError('email', {
          message: t(translatedError.fieldErrors.email, translatedError.message),
        });
      }

      if (translatedError.fieldErrors?.password) {
        setError('password', {
          message: t(translatedError.fieldErrors.password, translatedError.message),
        });
      }

      setErrorMessage(translatedError.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.content}>
        <Text variant="headlineLarge" style={styles.title}>
          {t('auth.signup')}
        </Text>
        <Text variant="bodyLarge" style={styles.subtitle}>
          {t('auth.signupSubtitle')}
        </Text>

        <View style={styles.form}>
          <Controller
            control={control}
            name="name"
            render={({ field: { onChange, value } }) => (
              <TextInput
                label={t('auth.name')}
                value={value}
                onChangeText={onChange}
                error={!!errors.name}
                autoCapitalize="words"
                autoComplete="name"
                style={styles.input}
              />
            )}
          />
          {errors.name && (
            <Text variant="bodySmall" style={[styles.error, { color: colors.error }]}>
              {errors.name.message}
            </Text>
          )}

          <Controller
            control={control}
            name="email"
            render={({ field: { onChange, value } }) => (
              <TextInput
                label={t('auth.email')}
                value={value}
                onChangeText={onChange}
                error={!!errors.email}
                autoCapitalize="none"
                keyboardType="email-address"
                autoComplete="email"
                style={styles.input}
              />
            )}
          />
          {errors.email && (
            <Text variant="bodySmall" style={[styles.error, { color: colors.error }]}>
              {errors.email.message}
            </Text>
          )}

          <Controller
            control={control}
            name="password"
            render={({ field: { onChange, value } }) => (
              <TextInput
                label={t('auth.password')}
                value={value}
                onChangeText={onChange}
                error={!!errors.password}
                secureTextEntry
                autoCapitalize="none"
                autoComplete="password-new"
                style={styles.input}
              />
            )}
          />
          {errors.password && (
            <Text variant="bodySmall" style={[styles.error, { color: colors.error }]}>
              {errors.password.message}
            </Text>
          )}

          <Controller
            control={control}
            name="confirmPassword"
            render={({ field: { onChange, value } }) => (
              <TextInput
                label={t('auth.confirmPassword')}
                value={value}
                onChangeText={onChange}
                error={!!errors.confirmPassword}
                secureTextEntry
                autoCapitalize="none"
                autoComplete="password-new"
                style={styles.input}
              />
            )}
          />
          {errors.confirmPassword && (
            <Text variant="bodySmall" style={[styles.error, { color: colors.error }]}>
              {errors.confirmPassword.message}
            </Text>
          )}

          <Button
            mode="contained"
            onPress={handleSubmit(onSubmit)}
            loading={loading}
            disabled={loading}
            style={styles.signupButton}
          >
            {t('auth.signup')}
          </Button>
        </View>

        <View style={styles.loginContainer}>
          <Text variant="bodyMedium">{t('auth.alreadyHaveAccount')} </Text>
          <Link href="/(auth)/login" asChild>
            <Button mode="text">{t('auth.login')}</Button>
          </Link>
        </View>
      </View>
      <Snackbar
        visible={errorMessage !== null}
        onDismiss={() => setErrorMessage(null)}
        duration={4000}
      >
        {errorMessage}
      </Snackbar>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  content: {
    padding: 24,
  },
  title: {
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    marginBottom: 32,
    textAlign: 'center',
    opacity: 0.7,
  },
  form: {
    width: '100%',
  },
  input: {
    marginBottom: 8,
  },
  error: {
    marginBottom: 8,
  },
  signupButton: {
    marginTop: 24,
    paddingVertical: 8,
  },
  loginContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
  },
});
