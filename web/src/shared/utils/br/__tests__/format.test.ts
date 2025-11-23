import { describe, it, expect } from 'vitest';
import { digitsOnly, formatCPF, formatCNPJ, formatCEP } from '../format';

describe('Brazilian formatting utilities', () => {
  describe('digitsOnly', () => {
    it('should return empty string for null input', () => {
      expect(digitsOnly(null)).toBe('');
    });

    it('should return empty string for undefined input', () => {
      expect(digitsOnly(undefined)).toBe('');
    });

    it('should return empty string for empty string', () => {
      expect(digitsOnly('')).toBe('');
    });

    it('should extract only digits from string', () => {
      expect(digitsOnly('abc123def456')).toBe('123456');
    });

    it('should remove all non-digit characters', () => {
      expect(digitsOnly('1a2b3c4d5e')).toBe('12345');
    });

    it('should preserve all digits', () => {
      expect(digitsOnly('1234567890')).toBe('1234567890');
    });

    it('should remove special characters', () => {
      expect(digitsOnly('123-456.789')).toBe('123456789');
    });

    it('should remove spaces', () => {
      expect(digitsOnly('123 456 789')).toBe('123456789');
    });

    it('should handle string with only non-digits', () => {
      expect(digitsOnly('abc')).toBe('');
    });

    it('should handle string with only digits', () => {
      expect(digitsOnly('123456')).toBe('123456');
    });
  });

  describe('formatCPF', () => {
    it('should format CPF with 11 digits', () => {
      expect(formatCPF('12345678901')).toBe('123.456.789-01');
    });

    it('should format CPF with less than 3 digits', () => {
      expect(formatCPF('12')).toBe('12');
    });

    it('should format CPF with 3 digits', () => {
      expect(formatCPF('123')).toBe('123');
    });

    it('should format CPF with 4-6 digits', () => {
      expect(formatCPF('1234')).toBe('123.4');
      expect(formatCPF('123456')).toBe('123.456');
    });

    it('should format CPF with 7-9 digits', () => {
      expect(formatCPF('1234567')).toBe('123.456.7');
      expect(formatCPF('123456789')).toBe('123.456.789');
    });

    it('should format CPF with more than 11 digits (truncate)', () => {
      expect(formatCPF('12345678901234')).toBe('123.456.789-01');
    });

    it('should remove non-digits before formatting', () => {
      expect(formatCPF('123.456.789-01')).toBe('123.456.789-01');
    });

    it('should handle empty string', () => {
      expect(formatCPF('')).toBe('');
    });

    it('should handle string with only non-digits', () => {
      expect(formatCPF('abc')).toBe('');
    });

    it('should format partial CPF during typing', () => {
      expect(formatCPF('1')).toBe('1');
      expect(formatCPF('12')).toBe('12');
      expect(formatCPF('123')).toBe('123');
      expect(formatCPF('1234')).toBe('123.4');
      expect(formatCPF('12345')).toBe('123.45');
      expect(formatCPF('123456')).toBe('123.456');
      expect(formatCPF('1234567')).toBe('123.456.7');
      expect(formatCPF('12345678')).toBe('123.456.78');
      expect(formatCPF('123456789')).toBe('123.456.789');
      expect(formatCPF('1234567890')).toBe('123.456.789-0');
      expect(formatCPF('12345678901')).toBe('123.456.789-01');
    });
  });

  describe('formatCNPJ', () => {
    it('should format CNPJ with 14 digits', () => {
      expect(formatCNPJ('12345678000190')).toBe('12.345.678/0001-90');
    });

    it('should format CNPJ with less than 2 digits', () => {
      expect(formatCNPJ('1')).toBe('1');
    });

    it('should format CNPJ with 2 digits', () => {
      expect(formatCNPJ('12')).toBe('12');
    });

    it('should format CNPJ with 3-5 digits', () => {
      expect(formatCNPJ('123')).toBe('12.3');
      expect(formatCNPJ('12345')).toBe('12.345');
    });

    it('should format CNPJ with 6-8 digits', () => {
      expect(formatCNPJ('123456')).toBe('12.345.6');
      expect(formatCNPJ('12345678')).toBe('12.345.678');
    });

    it('should format CNPJ with 9-12 digits', () => {
      expect(formatCNPJ('123456789')).toBe('12.345.678/9');
      expect(formatCNPJ('123456780001')).toBe('12.345.678/0001');
    });

    it('should format CNPJ with more than 14 digits (truncate)', () => {
      expect(formatCNPJ('12345678000190123')).toBe('12.345.678/0001-90');
    });

    it('should remove non-digits before formatting', () => {
      expect(formatCNPJ('12.345.678/0001-90')).toBe('12.345.678/0001-90');
    });

    it('should handle empty string', () => {
      expect(formatCNPJ('')).toBe('');
    });

    it('should handle string with only non-digits', () => {
      expect(formatCNPJ('abc')).toBe('');
    });

    it('should format partial CNPJ during typing', () => {
      expect(formatCNPJ('1')).toBe('1');
      expect(formatCNPJ('12')).toBe('12');
      expect(formatCNPJ('123')).toBe('12.3');
      expect(formatCNPJ('1234')).toBe('12.34');
      expect(formatCNPJ('12345')).toBe('12.345');
      expect(formatCNPJ('123456')).toBe('12.345.6');
      expect(formatCNPJ('1234567')).toBe('12.345.67');
      expect(formatCNPJ('12345678')).toBe('12.345.678');
      expect(formatCNPJ('123456789')).toBe('12.345.678/9');
      expect(formatCNPJ('1234567800')).toBe('12.345.678/00');
      expect(formatCNPJ('123456780001')).toBe('12.345.678/0001');
      expect(formatCNPJ('1234567800019')).toBe('12.345.678/0001-9');
      expect(formatCNPJ('12345678000190')).toBe('12.345.678/0001-90');
    });
  });

  describe('formatCEP', () => {
    it('should format CEP with 8 digits', () => {
      expect(formatCEP('12345678')).toBe('12345-678');
    });

    it('should format CEP with less than 5 digits', () => {
      expect(formatCEP('1234')).toBe('1234');
    });

    it('should format CEP with 5 digits', () => {
      expect(formatCEP('12345')).toBe('12345');
    });

    it('should format CEP with 6-8 digits', () => {
      expect(formatCEP('123456')).toBe('12345-6');
      expect(formatCEP('1234567')).toBe('12345-67');
      expect(formatCEP('12345678')).toBe('12345-678');
    });

    it('should format CEP with more than 8 digits (truncate)', () => {
      expect(formatCEP('123456789012')).toBe('12345-678');
    });

    it('should remove non-digits before formatting', () => {
      expect(formatCEP('12345-678')).toBe('12345-678');
    });

    it('should handle empty string', () => {
      expect(formatCEP('')).toBe('');
    });

    it('should handle string with only non-digits', () => {
      expect(formatCEP('abc')).toBe('');
    });

    it('should format partial CEP during typing', () => {
      expect(formatCEP('1')).toBe('1');
      expect(formatCEP('12')).toBe('12');
      expect(formatCEP('123')).toBe('123');
      expect(formatCEP('1234')).toBe('1234');
      expect(formatCEP('12345')).toBe('12345');
      expect(formatCEP('123456')).toBe('12345-6');
      expect(formatCEP('1234567')).toBe('12345-67');
      expect(formatCEP('12345678')).toBe('12345-678');
    });
  });
});

