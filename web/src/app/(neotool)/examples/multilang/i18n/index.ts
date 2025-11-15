import en from './locales/en.json';
import pt from './locales/pt.json';
import es from './locales/es.json';
import fr from './locales/fr.json';
import de from './locales/de.json';
import { DomainTranslations } from '@/shared/i18n/types';

// Dynamic language support - any number of languages
export const multilangTranslations: DomainTranslations = {
  domain: 'multilang',
  en,
  pt,
  es,
  fr,
  de,
  // You can easily add more languages here
};

export default multilangTranslations;
