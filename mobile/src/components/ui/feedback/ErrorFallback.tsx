import { StyleSheet, View } from 'react-native';
import { Text, Button } from 'react-native-paper';
import { useTranslation } from 'react-i18next';

interface ErrorFallbackProps {
  error: Error;
  resetErrorBoundary: () => void;
}

export function ErrorFallback({ error, resetErrorBoundary }: ErrorFallbackProps) {
  const { t } = useTranslation();

  return (
    <View style={styles.container}>
      <Text variant="headlineMedium" style={styles.title}>
        {t('errors.generic')}
      </Text>
      <Text variant="bodyMedium" style={styles.message}>
        {error.message}
      </Text>
      {__DEV__ && (
        <Text variant="bodySmall" style={styles.stack}>
          {error.stack}
        </Text>
      )}
      <Button mode="contained" onPress={resetErrorBoundary} style={styles.button}>
        {t('common.retry')}
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  title: {
    marginBottom: 16,
    textAlign: 'center',
  },
  message: {
    marginBottom: 24,
    textAlign: 'center',
    color: 'red',
  },
  stack: {
    marginBottom: 24,
    opacity: 0.5,
    fontSize: 10,
  },
  button: {
    marginTop: 16,
  },
});
