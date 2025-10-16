import { DomainTranslations, TranslationFunction } from '../types';

describe("I18n Types", () => {
  describe("DomainTranslations", () => {
    it("should accept valid domain translations", () => {
      const validTranslations: DomainTranslations = {
        domain: 'test',
        en: { title: 'Test Title' },
        pt: { title: 'Título de Teste' },
        es: { title: 'Título de Prueba' }
      };

      expect(validTranslations.domain).toBe('test');
      expect(validTranslations.en.title).toBe('Test Title');
      expect(validTranslations.pt.title).toBe('Título de Teste');
      expect(validTranslations.es.title).toBe('Título de Prueba');
    });

    it("should accept custom language codes", () => {
      const customTranslations: DomainTranslations = {
        domain: 'custom',
        'en-US': { title: 'US English' },
        'pt-BR': { title: 'Português do Brasil' },
        'zh-CN': { title: '简体中文' }
      };

      expect(customTranslations.domain).toBe('custom');
      expect(customTranslations['en-US'].title).toBe('US English');
      expect(customTranslations['pt-BR'].title).toBe('Português do Brasil');
      expect(customTranslations['zh-CN'].title).toBe('简体中文');
    });

    it("should accept nested translation objects", () => {
      const nestedTranslations: DomainTranslations = {
        domain: 'nested',
        en: {
          buttons: {
            save: 'Save',
            cancel: 'Cancel'
          },
          messages: {
            success: 'Success!',
            error: 'Error occurred'
          }
        },
        pt: {
          buttons: {
            save: 'Salvar',
            cancel: 'Cancelar'
          },
          messages: {
            success: 'Sucesso!',
            error: 'Erro ocorreu'
          }
        }
      };

      expect(nestedTranslations.en.buttons.save).toBe('Save');
      expect(nestedTranslations.pt.buttons.save).toBe('Salvar');
      expect(nestedTranslations.en.messages.success).toBe('Success!');
      expect(nestedTranslations.pt.messages.success).toBe('Sucesso!');
    });

    it("should accept empty language objects", () => {
      const emptyTranslations: DomainTranslations = {
        domain: 'empty',
        en: {},
        pt: {},
        es: {}
      };

      expect(emptyTranslations.domain).toBe('empty');
      expect(Object.keys(emptyTranslations.en)).toHaveLength(0);
      expect(Object.keys(emptyTranslations.pt)).toHaveLength(0);
    });

    it("should accept mixed data types in translations", () => {
      const mixedTranslations: DomainTranslations = {
        domain: 'mixed',
        en: {
          string: 'Hello',
          number: 42,
          boolean: true,
          array: ['item1', 'item2'],
          object: { nested: 'value' },
          null: null,
          undefined: undefined
        }
      };

      expect(mixedTranslations.en.string).toBe('Hello');
      expect(mixedTranslations.en.number).toBe(42);
      expect(mixedTranslations.en.boolean).toBe(true);
      expect(mixedTranslations.en.array).toEqual(['item1', 'item2']);
      expect(mixedTranslations.en.object).toEqual({ nested: 'value' });
      expect(mixedTranslations.en.null).toBeNull();
      expect(mixedTranslations.en.undefined).toBeUndefined();
    });
  });

  describe("TranslationFunction", () => {
    it("should accept valid translation function signatures", () => {
      const mockTranslationFunction: TranslationFunction = (key: string, options?: any) => {
        return `translated_${key}`;
      };

      expect(mockTranslationFunction('test')).toBe('translated_test');
      expect(mockTranslationFunction('test', { count: 5 })).toBe('translated_test');
    });

    it("should handle interpolation options", () => {
      const interpolationFunction: TranslationFunction = (key: string, options?: any) => {
        if (options && options.name) {
          return `Hello ${options.name}`;
        }
        return key;
      };

      expect(interpolationFunction('greeting')).toBe('greeting');
      expect(interpolationFunction('greeting', { name: 'World' })).toBe('Hello World');
    });

    it("should handle pluralization options", () => {
      const pluralizationFunction: TranslationFunction = (key: string, options?: any) => {
        if (options && options.count !== undefined) {
          return options.count === 1 ? `${key}_singular` : `${key}_plural`;
        }
        return key;
      };

      expect(pluralizationFunction('item')).toBe('item');
      expect(pluralizationFunction('item', { count: 1 })).toBe('item_singular');
      expect(pluralizationFunction('item', { count: 5 })).toBe('item_plural');
    });

    it("should handle complex options", () => {
      const complexFunction: TranslationFunction = (key: string, options?: any) => {
        if (options) {
          const { name, count, gender } = options;
          return `${key}_${name}_${count}_${gender}`;
        }
        return key;
      };

      const result = complexFunction('message', { 
        name: 'John', 
        count: 3, 
        gender: 'male' 
      });
      
      expect(result).toBe('message_John_3_male');
    });
  });

  describe("Type Safety", () => {
    it("should enforce domain property requirement", () => {
      // This test verifies TypeScript compilation
      // The following would cause a TypeScript error:
      // const invalid: DomainTranslations = {
      //   en: { title: 'Test' }
      //   // Missing 'domain' property
      // };

      // Valid implementation
      const valid: DomainTranslations = {
        domain: 'test',
        en: { title: 'Test' }
      };

      expect(valid.domain).toBeDefined();
    });

    it("should allow any number of language properties", () => {
      const manyLanguages: DomainTranslations = {
        domain: 'multilang',
        en: { hello: 'Hello' },
        pt: { hello: 'Olá' },
        es: { hello: 'Hola' },
        fr: { hello: 'Bonjour' },
        de: { hello: 'Hallo' },
        it: { hello: 'Ciao' },
        ja: { hello: 'こんにちは' },
        ko: { hello: '안녕하세요' },
        zh: { hello: '你好' },
        ru: { hello: 'Привет' },
        ar: { hello: 'مرحبا' }
      };

      expect(Object.keys(manyLanguages)).toHaveLength(12); // domain + 11 languages
      expect(manyLanguages.domain).toBe('multilang');
    });
  });
});
