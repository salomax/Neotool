import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, Card, Avatar } from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';

export default function ProfileScreen() {
  const { t } = useTranslation();
  const { user } = useAuth();

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.header}>
          <Avatar.Text size={80} label={user?.fullName?.charAt(0) || 'U'} />
          <Text variant="headlineSmall" style={styles.name}>
            {user?.fullName || user?.email}
          </Text>
          <Text variant="bodyMedium" style={styles.email}>
            {user?.email}
          </Text>
        </View>

        <Card style={styles.card}>
          <Card.Content>
            <Text variant="titleLarge">{t('profile.information')}</Text>
            <Text variant="bodyMedium" style={styles.placeholder}>
              Profile information coming soon...
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
  header: {
    alignItems: 'center',
    marginBottom: 24,
    paddingVertical: 24,
  },
  name: {
    marginTop: 16,
  },
  email: {
    marginTop: 4,
    opacity: 0.7,
  },
  card: {
    marginBottom: 16,
  },
  placeholder: {
    marginTop: 16,
    opacity: 0.7,
  },
});
