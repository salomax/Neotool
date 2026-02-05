import { Link, Stack } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import { Text, Button } from 'react-native-paper';
import { useTranslation } from 'react-i18next';

export default function NotFoundScreen() {
  const { t } = useTranslation();

  return (
    <>
      <Stack.Screen options={{ title: t('common.notFound') }} />
      <View style={styles.container}>
        <Text variant="headlineMedium" style={styles.title}>
          {t('common.notFound')}
        </Text>
        <Text variant="bodyLarge" style={styles.message}>
          {t('common.notFoundMessage')}
        </Text>
        <Link href="/" asChild>
          <Button mode="contained" style={styles.button}>
            {t('common.goHome')}
          </Button>
        </Link>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  title: {
    marginBottom: 16,
  },
  message: {
    marginBottom: 24,
    textAlign: 'center',
  },
  button: {
    marginTop: 16,
  },
});
