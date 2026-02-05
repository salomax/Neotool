import { useState } from 'react';
import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, Button, TextInput } from 'react-native-paper';
import { Link, router } from 'expo-router';
import { useTranslation } from 'react-i18next';

export default function SignupScreen() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.content}>
        <Text variant="headlineLarge" style={styles.title}>
          {t('auth.signup')}
        </Text>
        <Text variant="bodyLarge" style={styles.subtitle}>
          {t('auth.signupSubtitle')}
        </Text>

        {/* TODO: Implement signup form similar to login */}
        <Text variant="bodyMedium" style={styles.placeholder}>
          Signup form coming soon...
        </Text>

        <View style={styles.loginContainer}>
          <Text variant="bodyMedium">{t('auth.alreadyHaveAccount')} </Text>
          <Link href="/(auth)/login" asChild>
            <Button mode="text">{t('auth.login')}</Button>
          </Link>
        </View>
      </View>
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
  placeholder: {
    textAlign: 'center',
    marginVertical: 32,
    opacity: 0.5,
  },
  loginContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
  },
});
