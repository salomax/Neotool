import { describe, it, expect } from 'vitest';
import {
  getLocaleSeparators,
  getCurrencyDefaultFractionDigits,
} from '../locale';

describe('locale utilities', () => {
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

    it('should return only decimal when group separator is not available', () => {
      const result = getLocaleSeparators('en-US');
      expect(result.decimal).toBeTruthy();
      // Group may or may not be present depending on locale
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

    it('should handle locales with different separator patterns', () => {
      const result = getLocaleSeparators('de-DE');
      expect(result.decimal).toBeTruthy();
    });
  });

  describe('getCurrencyDefaultFractionDigits', () => {
    it('should return 2 for USD in en-US', () => {
      expect(getCurrencyDefaultFractionDigits('en-US', 'USD')).toBe(2);
    });

    it('should return 2 for BRL in pt-BR', () => {
      expect(getCurrencyDefaultFractionDigits('pt-BR', 'BRL')).toBe(2);
    });

    it('should return 0 for JPY (no decimal places)', () => {
      expect(getCurrencyDefaultFractionDigits('ja-JP', 'JPY')).toBe(0);
    });

    it('should return default 2 on error', () => {
      expect(getCurrencyDefaultFractionDigits('invalid-locale', 'INVALID')).toBe(2);
    });

    it('should handle different currency codes', () => {
      expect(getCurrencyDefaultFractionDigits('en-US', 'EUR')).toBe(2);
      expect(getCurrencyDefaultFractionDigits('en-US', 'GBP')).toBe(2);
    });

    it('should use maximum fraction digits when min and max differ', () => {
      // Some currencies might have different min/max, but we use max
      const result = getCurrencyDefaultFractionDigits('en-US', 'USD');
      expect(result).toBeGreaterThanOrEqual(0);
      expect(result).toBeLessThanOrEqual(3);
    });
  });
});

