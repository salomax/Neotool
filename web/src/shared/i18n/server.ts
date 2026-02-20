import en from './locales/en/common.json';
import pt from './locales/pt/common.json';

type Locale = 'en' | 'pt';

const dictionaries = {
  en,
  pt,
};

export const getDictionary = (lang: Locale = 'pt') => {
  return dictionaries[lang];
};

/**
 * Interpolates variables into a translation string.
 * Usage: interpolate("Hello {{name}}", { name: "John" }) -> "Hello John"
 */
export const interpolate = (text: string, params?: Record<string, string | number>) => {
  if (!params) return text;
  
  return Object.entries(params).reduce((result, [key, value]) => {
    return result.replace(new RegExp(`{{${key}}}`, 'g'), String(value));
  }, text);
};
