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

const createLoginSchema = (t: (key: string, defaultValue?: string) => string) =>
  z.object({
    email: z.string().email(t('auth.validation.invalidEmail', 'Invalid email address')),
    password: z
      .string()
      .min(8, t('auth.validation.passwordMinLength', 'Password must be at least 8 characters')),
  });

type LoginFormData = {
  email: string;
  password: string;
};

export default function LoginScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const { login } = useAuth();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const translateWithFallback = (key: string, fallback?: string) => t(key, fallback ?? key);

  const {
    control,
    clearErrors,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(createLoginSchema(translateWithFallback)),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (data: LoginFormData) => {
    try {
      setLoading(true);
      setErrorMessage(null);
      clearErrors();
      await login(data.email, data.password);
      router.replace('/(tabs)');
    } catch (error) {
      console.error('Login failed:', error);
      const translatedError = translateAppError(error, t);

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
          {t('auth.login')}
        </Text>
        <Text variant="bodyLarge" style={styles.subtitle}>
          {t('auth.loginSubtitle')}
        </Text>

        <View style={styles.form}>
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
                secureTextEntry={!showPassword}
                autoCapitalize="none"
                autoComplete="password"
                right={
                  <TextInput.Icon
                    icon={showPassword ? 'eye-off' : 'eye'}
                    onPress={() => setShowPassword(!showPassword)}
                  />
                }
                style={styles.input}
              />
            )}
          />
          {errors.password && (
            <Text variant="bodySmall" style={[styles.error, { color: colors.error }]}>
              {errors.password.message}
            </Text>
          )}

          <Link href="/(auth)/forgot-password" asChild>
            <Button mode="text" style={styles.forgotPassword}>
              {t('auth.forgotPassword')}
            </Button>
          </Link>

          <Button
            mode="contained"
            onPress={handleSubmit(onSubmit)}
            loading={loading}
            disabled={loading}
            style={styles.loginButton}
          >
            {t('auth.login')}
          </Button>

          <View style={styles.signupContainer}>
            <Text variant="bodyMedium">{t('auth.dontHaveAccount')} </Text>
            <Link href="/(auth)/signup" asChild>
              <Button mode="text">{t('auth.signup')}</Button>
            </Link>
          </View>
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
  forgotPassword: {
    alignSelf: 'flex-end',
    marginTop: 8,
  },
  loginButton: {
    marginTop: 24,
    paddingVertical: 8,
  },
  signupContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
  },
});
