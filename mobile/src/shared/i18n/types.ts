export interface DomainTranslations {
  domain: string;
  [language: string]: any;
}

export type TranslationFunction = (key: string, options?: any) => string;
