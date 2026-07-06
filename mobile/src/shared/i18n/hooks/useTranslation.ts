import { useTranslation as useReactI18nTranslation } from 'react-i18next';
import { useMemo } from 'react';
import i18n from '@/shared/config/i18n';
import { DomainTranslations, TranslationFunction } from '../types';

/**
 * Register domain translations with i18next
 */
function registerDomain(translations: DomainTranslations) {
  const domain = translations.domain;

  // Register each language
  Object.entries(translations).forEach(([language, translationData]) => {
    if (language !== 'domain' && typeof translationData === 'object') {
      // Check if resource bundle exists before adding
      if (!i18n.hasResourceBundle(language, domain)) {
        i18n.addResourceBundle(language, domain, translationData, true, true);
      }
    }
  });
}

/**
 * Smart i18n hook with method overloading
 * Supports both single domain and multiple domains with automatic fallback to common
 */

export function useTranslation(domainTranslations: DomainTranslations): {
  t: TranslationFunction;
  tDomain: TranslationFunction;
  tCommon: TranslationFunction;
};

export function useTranslation(domainTranslations: DomainTranslations[]): {
  t: TranslationFunction;
  getDomain: (domainName: string) => { t: TranslationFunction } | null;
  common: TranslationFunction;
};

export function useTranslation(domainTranslations: DomainTranslations | DomainTranslations[]): any {
  const isArray = Array.isArray(domainTranslations);
  if (isArray) {
    domainTranslations.forEach(registerDomain);
  } else {
    registerDomain(domainTranslations);
  }

  const domains = isArray ? domainTranslations : [domainTranslations];

  // Call hooks unconditionally
  const domain0 = domains[0]?.domain || 'common';
  const domain1 = domains[1]?.domain || domain0 || 'common';
  const domain2 = domains[2]?.domain || domain1 || 'common';
  const domain3 = domains[3]?.domain || domain2 || 'common';
  const domain4 = domains[4]?.domain || domain3 || 'common';

  const hook0 = useReactI18nTranslation(domain0);
  const hook1 = useReactI18nTranslation(domain1);
  const hook2 = useReactI18nTranslation(domain2);
  const hook3 = useReactI18nTranslation(domain3);
  const hook4 = useReactI18nTranslation(domain4);

  const domainHooks = domains.slice(0, 5).map((_, i) => [hook0, hook1, hook2, hook3, hook4][i]!);
  const { t: tCommon } = useReactI18nTranslation('common');

  const singleDomainResult = useMemo(() => {
    if (isArray) return null;
    const { t: tDomain } = domainHooks[0]!;
    return {
      t: (key: string, options?: any) => {
        const domainResult = tDomain(key, options);
        return domainResult === key ? tCommon(key, options) : domainResult;
      },
      tDomain,
      tCommon,
    };
  }, [isArray, domainHooks, tCommon]);

  const multipleDomainResult = useMemo(() => {
    if (!isArray) return null;
    return {
      t: (key: string, options?: any) => {
        // Try all domains in order
        for (const hook of domainHooks) {
          const result = hook.t(key, options);
          if (result !== key) return result;
        }
        return tCommon(key, options);
      },
      getDomain: (domainName: string) => {
        const index = domains.findIndex((d) => d.domain === domainName);
        return index >= 0 ? { t: domainHooks[index]!.t } : null;
      },
      common: tCommon,
    };
  }, [isArray, domains, domainHooks, tCommon]);

  return isArray ? multipleDomainResult : singleDomainResult;
}
