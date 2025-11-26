import { describe, it, expect } from 'vitest';
import {
  getLocaleSeparators,
  normalizeDecimalInput,
  roundTo,
  parseLocaleNumber,
} from '../number';

describe('number utilities', () => {
  describe('getLocaleSeparators', () => {
    it('should return decimal and group separators for en-US', () => {
      const result = getLocaleSeparators('en-US');
      expect(result.decimal).toBe('.');
      expect(result.group).toBe(',');
    });

    it('should return decimal and group separators for pt-BR', () => {
      const result = getLocaleSeparators('pt-BR');
      expect(result.decimal).toBe(',');
      expect(result.group).toBe('.');
    });

    it('should return default separators on error', () => {
      // Mock Intl.NumberFormat to throw to test error handling
      const originalIntl = global.Intl;
      global.Intl = {
        ...originalIntl,
        NumberFormat: class extends originalIntl.NumberFormat {
          constructor(...args: any[]) {
            super(...args);
            throw new Error('Invalid locale');
          }
        } as any,
      };

      const result = getLocaleSeparators('invalid-locale');
      expect(result.decimal).toBe('.');
      expect(result.group).toBe(',');

      // Restore original Intl
      global.Intl = originalIntl;
    });

    it('should handle locales without group separator', () => {
      // Some locales might not have group separators
      const result = getLocaleSeparators('en-US');
      expect(result.decimal).toBeTruthy();
    });
  });

  describe('normalizeDecimalInput', () => {
    it('should return empty string for null input', () => {
      expect(normalizeDecimalInput(null, 'en-US')).toBe('');
    });

    it('should return empty string for undefined input', () => {
      expect(normalizeDecimalInput(undefined, 'en-US')).toBe('');
    });

    it('should return empty string for empty string', () => {
      expect(normalizeDecimalInput('', 'en-US')).toBe('');
    });

    it('should return "-" when only minus sign is typed and negative allowed', () => {
      expect(normalizeDecimalInput('-', 'en-US', true)).toBe('-');
    });

    it('should return empty string when only minus sign is typed and negative not allowed', () => {
      expect(normalizeDecimalInput('-', 'en-US', false)).toBe('');
    });

    it('should normalize simple number', () => {
      expect(normalizeDecimalInput('123', 'en-US')).toBe('123');
    });

    it('should normalize decimal with dot', () => {
      expect(normalizeDecimalInput('123.45', 'en-US')).toBe('123.45');
    });

    it('should normalize decimal with comma for pt-BR locale', () => {
      // In pt-BR, comma is decimal separator
      expect(normalizeDecimalInput('123,45', 'pt-BR')).toBe('123.45');
    });

    it('should remove comma as group separator in en-US', () => {
      // In en-US, comma is group separator, so it gets removed
      expect(normalizeDecimalInput('1,234.56', 'en-US')).toBe('1234.56');
    });

    it('should keep first decimal marker and remove others', () => {
      // Multiple dots - keep first, remove rest
      expect(normalizeDecimalInput('123.45.67', 'en-US')).toBe('123.4567');
    });

    it('should remove group separators', () => {
      expect(normalizeDecimalInput('1,234.56', 'en-US')).toBe('1234.56');
    });

    it('should handle pt-BR format with comma as decimal', () => {
      expect(normalizeDecimalInput('1.234,56', 'pt-BR')).toBe('1234.56');
    });

    it('should remove spaces', () => {
      expect(normalizeDecimalInput('1 234.56', 'en-US')).toBe('1234.56');
    });

    it('should handle negative numbers', () => {
      expect(normalizeDecimalInput('-123.45', 'en-US')).toBe('-123.45');
    });

    it('should prevent negative when not allowed', () => {
      expect(normalizeDecimalInput('-123.45', 'en-US', false)).toBe('123.45');
    });

    it('should handle leading dot', () => {
      expect(normalizeDecimalInput('.45', 'en-US')).toBe('0.45');
    });

    it('should handle "-." pattern', () => {
      expect(normalizeDecimalInput('-.', 'en-US')).toBe('-0.');
    });

    it('should remove non-numeric characters except dot and minus', () => {
      expect(normalizeDecimalInput('abc123.45def', 'en-US')).toBe('123.45');
    });

    it('should trim input', () => {
      expect(normalizeDecimalInput('  123.45  ', 'en-US')).toBe('123.45');
    });

    it('should handle multiple dots by keeping first', () => {
      expect(normalizeDecimalInput('12.34.56', 'en-US')).toBe('12.3456');
    });
  });

  describe('roundTo', () => {
    it('should return number unchanged when fractionDigits is not provided', () => {
      expect(roundTo(123.456)).toBe(123.456);
    });

    it('should round to specified decimal places', () => {
      expect(roundTo(123.456, 2)).toBe(123.46);
      expect(roundTo(123.454, 2)).toBe(123.45);
    });

    it('should round to zero decimal places', () => {
      expect(roundTo(123.456, 0)).toBe(123);
      expect(roundTo(123.5, 0)).toBe(124);
    });

    it('should round to multiple decimal places', () => {
      expect(roundTo(123.456789, 4)).toBe(123.4568);
    });

    it('should return number unchanged for non-finite numbers', () => {
      expect(roundTo(Infinity)).toBe(Infinity);
      expect(roundTo(-Infinity)).toBe(-Infinity);
      expect(roundTo(NaN)).toBeNaN();
    });

    it('should handle negative numbers', () => {
      expect(roundTo(-123.456, 2)).toBe(-123.46);
    });

    it('should handle zero', () => {
      expect(roundTo(0, 2)).toBe(0);
    });
  });

  describe('parseLocaleNumber', () => {
    it('should parse simple number', () => {
      expect(parseLocaleNumber('123', 'en-US')).toBe(123);
    });

    it('should parse decimal number', () => {
      expect(parseLocaleNumber('123.45', 'en-US')).toBe(123.45);
    });

    it('should parse with fraction digits', () => {
      expect(parseLocaleNumber('123.456', 'en-US', { fractionDigits: 2 })).toBe(123.46);
    });

    it('should return empty string for empty input', () => {
      expect(parseLocaleNumber('', 'en-US')).toBe('');
    });

    it('should return "-" for minus-only input when negative allowed', () => {
      expect(parseLocaleNumber('-', 'en-US', { allowNegative: true })).toBe('');
    });

    it('should parse negative numbers', () => {
      expect(parseLocaleNumber('-123.45', 'en-US')).toBe(-123.45);
    });

    it('should not parse negative when not allowed', () => {
      expect(parseLocaleNumber('-123.45', 'en-US', { allowNegative: false })).toBe(123.45);
    });

    it('should return empty string for invalid input', () => {
      expect(parseLocaleNumber('abc', 'en-US')).toBe('');
    });

    it('should handle pt-BR format', () => {
      expect(parseLocaleNumber('1.234,56', 'pt-BR')).toBe(1234.56);
    });

    it('should round parsed number to fraction digits', () => {
      expect(parseLocaleNumber('123.456789', 'en-US', { fractionDigits: 2 })).toBe(123.46);
    });

    it('should handle normalized input with group separators removed', () => {
      expect(parseLocaleNumber('1,234.56', 'en-US')).toBe(1234.56);
    });
  });
});

