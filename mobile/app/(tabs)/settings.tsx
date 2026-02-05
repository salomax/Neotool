import { StyleSheet, View, ScrollView } from 'react-native';
import { Text, List, Switch, Button } from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import { router } from 'expo-router';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';

export default function SettingsScreen() {
  const { t, i18n } = useTranslation();
  const { logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();

  const handleLogout = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  const changeLanguage = (lang: string) => {
    i18n.changeLanguage(lang);
  };

  return (
    <ScrollView style={styles.container}>
      <List.Section>
        <List.Subheader>{t('settings.appearance')}</List.Subheader>
        <List.Item
          title={t('settings.darkMode')}
          right={() => <Switch value={isDark} onValueChange={toggleTheme} />}
        />
      </List.Section>

      <List.Section>
        <List.Subheader>{t('settings.language')}</List.Subheader>
        <List.Item
          title="English"
          onPress={() => changeLanguage('en')}
          left={(props) => <List.Icon {...props} icon="translate" />}
          right={() => i18n.language === 'en' ? <List.Icon icon="check" /> : null}
        />
        <List.Item
          title="Português"
          onPress={() => changeLanguage('pt')}
          left={(props) => <List.Icon {...props} icon="translate" />}
          right={() => i18n.language === 'pt' ? <List.Icon icon="check" /> : null}
        />
      </List.Section>

      <View style={styles.logoutContainer}>
        <Button mode="outlined" onPress={handleLogout} style={styles.logoutButton}>
          {t('auth.logout')}
        </Button>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  logoutContainer: {
    padding: 16,
    marginTop: 24,
  },
  logoutButton: {
    borderColor: 'red',
  },
});
