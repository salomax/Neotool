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
  // Register domains immediately (synchronously) - only if not already registered
  if (Array.isArray(domainTranslations)) {
    domainTranslations.forEach(registerDomain);
  } else {
    registerDomain(domainTranslations);
  }

  // Single domain case
  if (!Array.isArray(domainTranslations)) {
    const { t: tDomain } = useReactI18nTranslation(domainTranslations.domain);
    const { t: tCommon } = useReactI18nTranslation('common');

    return useMemo(() => ({
      t: (key: string, options?: any) => {
        const domainResult = tDomain(key, options);
        return domainResult === key ? tCommon(key, options) : domainResult;
      },
      tDomain,
      tCommon
    }), [tDomain, tCommon]);
  }

  // Multiple domains case
  const domainHooks = domainTranslations.map(domain => useReactI18nTranslation(domain.domain));
  const { t: tCommon } = useReactI18nTranslation('common');

  return useMemo(() => ({
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
  }), [domainHooks, tCommon, domainTranslations]);
}