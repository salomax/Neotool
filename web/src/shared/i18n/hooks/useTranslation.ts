"use client";

import { useTranslation as useReactI18nTranslation } from 'react-i18next';
import { useMemo } from 'react';
import i18n from 'i18next';
import { DomainTranslations, TranslationFunction } from '../types';

// Simple registry to prevent duplicate registrations
const registeredDomains = new Set<string>();

/**
 * Register domain translations with i18next
 */
function registerDomain(translations: DomainTranslations) {
  const domain = translations.domain;
  
  if (registeredDomains.has(domain)) {
    return; // Already registered
  }

  // Register each language
  Object.entries(translations).forEach(([language, translationData]) => {
    if (language !== 'domain' && typeof translationData === 'object') {
      // Use i18next's addResourceBundle
      i18n.addResourceBundle(language, domain, translationData, true, true);
    }
  });

  registeredDomains.add(domain);
}

/**
 * Smart i18n hook with method overloading
 * Supports both single domain and multiple domains with automatic fallback to common
 */
/* eslint-disable no-redeclare -- TypeScript function overloads are valid */
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
/* eslint-enable no-redeclare */
  // Register domains immediately (synchronously) - only if not already registered
  const isArray = Array.isArray(domainTranslations);
  if (isArray) {
    domainTranslations.forEach(registerDomain);
  } else {
    registerDomain(domainTranslations);
  }

  // Always call hooks unconditionally - determine which domains to use
  const domains = isArray ? domainTranslations : [domainTranslations];
  
  // Always call hooks for up to 10 domains (pad with last domain or 'common' to ensure same number of hooks)
  // This ensures hooks are called unconditionally at the top level
  const domain0 = domains[0]?.domain || 'common';
  const domain1 = domains[1]?.domain || domain0 || 'common';
  const domain2 = domains[2]?.domain || domain1 || 'common';
  const domain3 = domains[3]?.domain || domain2 || 'common';
  const domain4 = domains[4]?.domain || domain3 || 'common';
  const domain5 = domains[5]?.domain || domain4 || 'common';
  const domain6 = domains[6]?.domain || domain5 || 'common';
  const domain7 = domains[7]?.domain || domain6 || 'common';
  const domain8 = domains[8]?.domain || domain7 || 'common';
  const domain9 = domains[9]?.domain || domain8 || 'common';
  
  const hook0 = useReactI18nTranslation(domain0);
  const hook1 = useReactI18nTranslation(domain1);
  const hook2 = useReactI18nTranslation(domain2);
  const hook3 = useReactI18nTranslation(domain3);
  const hook4 = useReactI18nTranslation(domain4);
  const hook5 = useReactI18nTranslation(domain5);
  const hook6 = useReactI18nTranslation(domain6);
  const hook7 = useReactI18nTranslation(domain7);
  const hook8 = useReactI18nTranslation(domain8);
  const hook9 = useReactI18nTranslation(domain9);
  
  // Only use hooks for domains that actually exist
  const domainHooks = domains.slice(0, 10).map((_, i) => [hook0, hook1, hook2, hook3, hook4, hook5, hook6, hook7, hook8, hook9][i]!);
  const { t: tCommon } = useReactI18nTranslation('common');

  // Always call useMemo unconditionally - use the result conditionally
  const singleDomainResult = useMemo(() => {
    if (isArray) return null;
    const { t: tDomain } = domainHooks[0]!;
    return {
      t: (key: string, options?: any) => {
        const domainResult = tDomain(key, options);
        return domainResult === key ? tCommon(key, options) : domainResult;
      },
      tDomain,
      tCommon
    };
  }, [isArray, domainHooks, tCommon]);

  const multipleDomainResult = useMemo(() => {
    if (!isArray) return null;
    return {
      t: (key: string, options?: any) => {
        for (const hook of domainHooks) {
          const result = hook.t(key, options);
          if (result !== key) return result;
        }
        return tCommon(key, options);
      },
      getDomain: (domainName: string) => {
        const domainIndex = domainTranslations.findIndex(d => d.domain === domainName);
        return domainIndex >= 0 ? domainHooks[domainIndex] : null;
      },
      common: tCommon
    };
  }, [isArray, domainHooks, tCommon, domainTranslations]);

  // Return the appropriate result based on the input type
  return isArray ? multipleDomainResult : singleDomainResult;
}