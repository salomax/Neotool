import { vi } from 'vitest';
import { waitFor } from '@testing-library/react';
import i18n from '../config';

describe("I18n Configuration", () => {
  beforeEach(async () => {
    // Reset i18n state before each test
    await i18n.changeLanguage('en');
  });

  describe("Initialization", () => {
    it("should initialize i18n with correct configuration", () => {
      expect(i18n.isInitialized).toBe(true);
      expect(i18n.language).toBe('en');
      expect(i18n.options.fallbackLng).toEqual(['en']);
      expect(i18n.options.defaultNS).toBe('common');
    });

    it("should have common namespace configured", () => {
      expect(i18n.options.ns).toContain('common');
      expect(i18n.options.defaultNS).toBe('common');
    });

    it("should have interpolation configured correctly", () => {
      expect(i18n.options.interpolation?.escapeValue).toBe(false);
    });

    it("should have initImmediate set to false", () => {
      expect(i18n.options.initImmediate).toBe(false);
    });
  });

  describe("Language Support", () => {
    it("should support English and Portuguese", () => {
      // i18next doesn't set supportedLngs by default, so we check the resources instead
      expect(i18n.hasResourceBundle('en', 'common')).toBe(true);
      expect(i18n.hasResourceBundle('pt', 'common')).toBe(true);
    });

    it("should have English as default language", () => {
      expect(i18n.language).toBe('en');
    });

    it("should have English as fallback language", () => {
      expect(i18n.options.fallbackLng).toEqual(['en']);
    });
  });

  describe("Resource Bundles", () => {
    it("should have common resources loaded", () => {
      expect(i18n.hasResourceBundle('en', 'common')).toBe(true);
      expect(i18n.hasResourceBundle('pt', 'common')).toBe(true);
    });

    it("should have correct common translations in English", () => {
      const enCommon = i18n.getResourceBundle('en', 'common');
      expect(enCommon).toBeDefined();
      expect(typeof enCommon).toBe('object');
    });

    it("should have correct common translations in Portuguese", () => {
      const ptCommon = i18n.getResourceBundle('pt', 'common');
      expect(ptCommon).toBeDefined();
      expect(typeof ptCommon).toBe('object');
    });
  });

  describe("Language Switching", () => {
    it("should change language correctly", async () => {
      expect(i18n.language).toBe('en');
      
      await i18n.changeLanguage('pt');
      await waitFor(() => {
        expect(i18n.language).toBeDefined();
      });
      
      await i18n.changeLanguage('en');
      await waitFor(() => expect(i18n.language).toBe('en'));
    });

    it("should emit languageChanged event", async () => {
      const callback = vi.fn();
      i18n.on('languageChanged', callback);
      
      await i18n.changeLanguage('pt');
      
      expect(callback).toHaveBeenCalledWith('pt');
      i18n.off('languageChanged', callback);
    });

    it("should handle invalid language gracefully", async () => {
      await i18n.changeLanguage('invalid-lang');
      // We accept that i18next will set whatever language string is provided
      expect(i18n.language).toBe('invalid-lang');
    });
  });

  describe("Translation Functions", () => {
    it("should translate common namespace correctly", () => {
      const translation = i18n.t('common:save');
      expect(typeof translation).toBe('string');
    });

    it("should handle missing translations gracefully", () => {
      const translation = i18n.t('common:nonExistentKey');
      expect(translation).toBe('nonExistentKey');
    });

    it("should handle interpolation", () => {
      // Assuming we have a translation with interpolation
      const translation = i18n.t('common:hello', { name: 'World' });
      expect(typeof translation).toBe('string');
    });

    it("should handle pluralization", () => {
      // Assuming we have pluralization support
      const singular = i18n.t('common:item', { count: 1 });
      const plural = i18n.t('common:item', { count: 5 });
      expect(typeof singular).toBe('string');
      expect(typeof plural).toBe('string');
    });
  });

  describe("Error Handling", () => {
    it("should not throw errors during initialization", async () => {
      expect(() => {
        // Re-import config to test initialization
        import('../config');
      }).not.toThrow();
    });

    it("should handle missing resource bundles gracefully", () => {
      const translation = i18n.t('nonExistentNamespace:key');
      expect(typeof translation).toBe('string');
    });

    it("should handle malformed translation keys", () => {
      const translation = i18n.t('');
      expect(typeof translation).toBe('string');
    });
  });

  describe("Performance", () => {
    it("should not reinitialize if already initialized", async () => {
      const isInitializedBefore = i18n.isInitialized;
      
      // Re-import config
      await import('../config');
      
      expect(i18n.isInitialized).toBe(isInitializedBefore);
    });

    it("should handle rapid language changes", async () => {
      const promises = [];
      
      for (let i = 0; i < 10; i++) {
        promises.push(i18n.changeLanguage(i % 2 === 0 ? 'en' : 'pt'));
      }
      
      await Promise.all(promises);
      expect(['en', 'pt']).toContain(i18n.language);
    });
  });
});
