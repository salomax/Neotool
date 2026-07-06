import React from 'react';
import { StyleSheet, View, Linking, TouchableOpacity, ScrollView } from 'react-native';
import { Text, Button, useTheme as usePaperTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { router } from 'expo-router';

import { Wordmark, Logo } from '@/shared/components/brand';
import { useTranslation } from '@/shared/i18n';
import { landingTranslations } from '../i18n';
import { useTheme } from '@/shared/hooks/useTheme';

export function LandingScreen() {
  const { t } = useTranslation(landingTranslations);
  const { isDark } = useTheme();
  const theme = usePaperTheme();

  const handleEmailPress = () => {
    Linking.openURL('mailto:contato@invistus.com.br');
  };

  const handleLinkedInPress = () => {
    Linking.openURL('https://www.linkedin.com/company/invistus');
  };

  const handleLoginPress = () => {
    // Navigate to auth screen or tabs if already logged in (logic handled by router/guard usually)
    // For now, let's assume we go to auth
    router.push('/(auth)/login');
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.content}>
          <View style={styles.header}>
            <Wordmark variant={isDark ? 'white' : 'blue'} size="medium" width={280} />
          </View>

          <View style={styles.heroSection}>
            <Text
              variant="displayMedium"
              style={[styles.heroTitle, { color: theme.colors.onBackground }]}
            >
              {t('title.line1')}
              {'\n'}
              {t('title.line2')}
            </Text>

            <Text
              variant="titleMedium"
              style={[styles.heroSubtitle, { color: theme.colors.onSurfaceVariant }]}
            >
              {t('subtitle.line1')}
              {'\n'}
              {t('subtitle.line2')}
            </Text>
          </View>

          <View style={styles.actionSection}>
            <Button
              mode="contained"
              onPress={handleLoginPress}
              style={styles.button}
              contentStyle={styles.buttonContent}
            >
              Login
            </Button>
          </View>
        </View>

        <View style={[styles.footer, { borderTopColor: theme.colors.outlineVariant }]}>
          <View style={styles.footerLogo}>
            <Logo variant={isDark ? 'white' : 'blue'} size="small" />
          </View>

          <View style={styles.footerLinks}>
            <TouchableOpacity onPress={handleEmailPress}>
              <Text style={[styles.linkText, { color: theme.colors.onSurfaceVariant }]}>
                contato@invistus.com.br
              </Text>
            </TouchableOpacity>

            <TouchableOpacity onPress={handleLinkedInPress} style={styles.linkedinLink}>
              <MaterialCommunityIcons
                name="linkedin"
                size={24}
                color={theme.colors.onSurfaceVariant}
              />
              <Text
                style={[styles.linkText, styles.boldLink, { color: theme.colors.onSurfaceVariant }]}
              >
                LinkedIn
              </Text>
            </TouchableOpacity>
          </View>

          <Text
            variant="bodySmall"
            style={[styles.copyright, { color: theme.colors.onSurfaceVariant }]}
          >
            © 2026 Invistus — Todos os direitos reservados
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'space-between',
  },
  content: {
    flex: 1,
    paddingHorizontal: 24,
    paddingTop: 60,
    alignItems: 'center',
  },
  header: {
    marginBottom: 48,
  },
  heroSection: {
    alignItems: 'center',
    marginBottom: 48,
  },
  heroTitle: {
    textAlign: 'center',
    fontWeight: '500',
    marginBottom: 16,
    letterSpacing: -0.5,
  },
  heroSubtitle: {
    textAlign: 'center',
    maxWidth: 300,
    lineHeight: 24,
  },
  actionSection: {
    width: '100%',
    maxWidth: 300,
  },
  button: {
    borderRadius: 8,
  },
  buttonContent: {
    height: 48,
  },
  footer: {
    padding: 24,
    alignItems: 'center',
    borderTopWidth: 1,
    width: '100%',
  },
  footerLogo: {
    marginBottom: 16,
  },
  footerLinks: {
    flexDirection: 'column',
    alignItems: 'center',
    gap: 12,
    marginBottom: 16,
  },
  linkText: {
    fontSize: 14,
    textDecorationLine: 'underline',
  },
  boldLink: {
    fontWeight: 'bold',
    textDecorationLine: 'none',
  },
  linkedinLink: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  copyright: {
    textAlign: 'center',
    opacity: 0.7,
  },
});
