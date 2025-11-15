/**
 * Base interface for domain translations
 * All domain translation objects must follow this contract
 */
export interface DomainTranslations {
  /** The domain name (e.g., 'customers', 'products', 'inventory') */
  domain: string;
  /** Dynamic language support - any number of languages */
  [language: string]: any;
}

/**
 * Type for the translation function returned by useTranslation
 */
export type TranslationFunction = (key: string, options?: any) => string;