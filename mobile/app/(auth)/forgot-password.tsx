import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, Button } from 'react-native-paper';
import { Link } from 'expo-router';
import { useTranslation } from 'react-i18next';

export default function ForgotPasswordScreen() {
  const { t } = useTranslation();

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.content}>
        <Text variant="headlineLarge" style={styles.title}>
          {t('auth.forgotPassword')}
        </Text>
        <Text variant="bodyLarge" style={styles.subtitle}>
          {t('auth.forgotPasswordSubtitle')}
        </Text>

        {/* TODO: Implement forgot password form */}
        <Text variant="bodyMedium" style={styles.placeholder}>
          Forgot password form coming soon...
        </Text>

        <Link href="/(auth)/login" asChild>
          <Button mode="text" style={styles.backButton}>
            {t('common.backToLogin')}
          </Button>
        </Link>
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
  backButton: {
    marginTop: 24,
  },
});
