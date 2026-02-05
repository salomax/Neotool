import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import * as Localization from 'expo-localization';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Import translations
import en from '@/locales/en.json';
import pt from '@/locales/pt.json';

const LANGUAGE_KEY = '@app_language';

// Get device language
const deviceLanguage = Localization.locale.split('-')[0];

i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      pt: { translation: pt },
    },
    lng: deviceLanguage,
    fallbackLng: 'en',
    compatibilityJSON: 'v3',
    interpolation: {
      escapeValue: false, // React already escapes values
    },
    react: {
      useSuspense: false, // Important for React Native
    },
  });

// Load saved language preference
AsyncStorage.getItem(LANGUAGE_KEY)
  .then((lang) => {
    if (lang && i18n.language !== lang) {
      i18n.changeLanguage(lang);
    }
  })
  .catch((error) => {
    console.error('Failed to load language preference:', error);
  });

// Save language preference when changed
i18n.on('languageChanged', (lng) => {
  AsyncStorage.setItem(LANGUAGE_KEY, lng).catch((error) => {
    console.error('Failed to save language preference:', error);
  });
});

export default i18n;
