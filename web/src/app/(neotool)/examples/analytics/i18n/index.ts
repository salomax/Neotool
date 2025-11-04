import en from './locales/en.json';
import pt from './locales/pt.json';
import { DomainTranslations } from '@/shared/i18n/types';

// This will enforce the DomainTranslations contract at compile time
export const analyticsTranslations: DomainTranslations = {
  domain: 'analytics',
  en,
  pt,
};

export default analyticsTranslations;
