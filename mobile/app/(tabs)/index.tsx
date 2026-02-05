import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, Card } from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';

export default function DashboardScreen() {
  const { t } = useTranslation();
  const { user } = useAuth();

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text variant="headlineLarge" style={styles.title}>
          {t('dashboard.welcome', { name: user?.fullName || user?.email })}
        </Text>

        <Card style={styles.card}>
          <Card.Content>
            <Text variant="titleLarge">{t('dashboard.overview')}</Text>
            <Text variant="bodyMedium" style={styles.placeholder}>
              Dashboard content coming soon...
            </Text>
          </Card.Content>
        </Card>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 16,
  },
  title: {
    marginBottom: 24,
  },
  card: {
    marginBottom: 16,
  },
  placeholder: {
    marginTop: 16,
    opacity: 0.7,
  },
});
