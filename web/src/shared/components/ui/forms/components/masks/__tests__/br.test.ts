import { describe, it, expect } from 'vitest';
import {
  onlyDigits,
  maskCPF,
  maskCNPJ,
  maskCEP,
  maskPhoneBR,
  parseLocaleNumber,
  formatCurrency,
} from '../br';

describe('br masks', () => {
  describe('onlyDigits', () => {
    it('should extract only digits from string', () => {
      expect(onlyDigits('123abc456')).toBe('123456');
      expect(onlyDigits('(11) 98765-4321')).toBe('11987654321');
      expect(onlyDigits('111.444.777-35')).toBe('11144477735');
    });

    it('should handle numbers', () => {
      expect(onlyDigits(12345)).toBe('12345');
      expect(onlyDigits(0)).toBe('0');
    });

    it('should handle null and undefined', () => {
      expect(onlyDigits(null)).toBe('');
      expect(onlyDigits(undefined)).toBe('');
    });

    it('should handle empty string', () => {
      expect(onlyDigits('')).toBe('');
    });

    it('should handle string with no digits', () => {
      expect(onlyDigits('abc-def')).toBe('');
      expect(onlyDigits('!@#$%')).toBe('');
    });
  });

  describe('maskCPF', () => {
    it('should format CPF correctly', () => {
      expect(maskCPF('11144477735')).toBe('111.444.777-35');
      expect(maskCPF('111.444.777-35')).toBe('111.444.777-35');
    });

    it('should limit to 11 digits', () => {
      expect(maskCPF('11144477735123')).toBe('111.444.777-35');
    });

    it('should handle partial input', () => {
      expect(maskCPF('111')).toBe('111');
      expect(maskCPF('1114')).toBe('111.4');
      expect(maskCPF('11144')).toBe('111.44');
      expect(maskCPF('111444')).toBe('111.444');
      expect(maskCPF('1114447')).toBe('111.444.7');
      expect(maskCPF('11144477')).toBe('111.444.77');
      expect(maskCPF('111444777')).toBe('111.444.777');
      expect(maskCPF('1114447773')).toBe('111.444.777-3');
    });

    it('should handle empty string', () => {
      expect(maskCPF('')).toBe('');
    });
  });

  describe('maskCNPJ', () => {
    it('should format CNPJ correctly', () => {
      expect(maskCNPJ('11222333000181')).toBe('11.222.333/0001-81');
      expect(maskCNPJ('11.222.333/0001-81')).toBe('11.222.333/0001-81');
    });

    it('should limit to 14 digits', () => {
      expect(maskCNPJ('11222333000181123')).toBe('11.222.333/0001-81');
    });

    it('should handle partial input', () => {
      expect(maskCNPJ('11')).toBe('11');
      expect(maskCNPJ('112')).toBe('11.2');
      expect(maskCNPJ('11222')).toBe('11.222');
      expect(maskCNPJ('1122233')).toBe('11.222.33');
      expect(maskCNPJ('11222333')).toBe('11.222.333');
      expect(maskCNPJ('11222333000')).toBe('11.222.333/000');
      expect(maskCNPJ('112223330001')).toBe('11.222.333/0001');
      expect(maskCNPJ('1122233300018')).toBe('11.222.333/0001-8');
    });

    it('should handle empty string', () => {
      expect(maskCNPJ('')).toBe('');
    });
  });

  describe('maskCEP', () => {
    it('should format CEP correctly', () => {
      expect(maskCEP('12345678')).toBe('12345-678');
      expect(maskCEP('12345-678')).toBe('12345-678');
    });

    it('should limit to 8 digits', () => {
      expect(maskCEP('123456789')).toBe('12345-678');
    });

    it('should handle partial input', () => {
      expect(maskCEP('12345')).toBe('12345');
      expect(maskCEP('123456')).toBe('12345-6');
      expect(maskCEP('1234567')).toBe('12345-67');
    });

    it('should handle empty string', () => {
      expect(maskCEP('')).toBe('');
    });
  });

  describe('maskPhoneBR', () => {
    it('should format 10-digit phone correctly', () => {
      expect(maskPhoneBR('1198765432')).toBe('(11) 9876-5432');
      expect(maskPhoneBR('(11) 9876-5432')).toBe('(11) 9876-5432');
    });

    it('should format 11-digit phone correctly', () => {
      expect(maskPhoneBR('11987654321')).toBe('(11) 98765-4321');
      expect(maskPhoneBR('(11) 98765-4321')).toBe('(11) 98765-4321');
    });

    it('should limit to 11 digits', () => {
      expect(maskPhoneBR('11987654321123')).toBe('(11) 98765-4321');
    });

    it('should handle partial input', () => {
      expect(maskPhoneBR('11')).toBe('(11)');
      expect(maskPhoneBR('119')).toBe('(11) 9');
      expect(maskPhoneBR('11987')).toBe('(11) 987');
      expect(maskPhoneBR('119876')).toBe('(11) 9876');
      expect(maskPhoneBR('1198765')).toBe('(11) 9876-5');
      expect(maskPhoneBR('11987654')).toBe('(11) 9876-54');
      expect(maskPhoneBR('119876543')).toBe('(11) 9876-543');
      expect(maskPhoneBR('1198765432')).toBe('(11) 9876-5432');
      expect(maskPhoneBR('11987654321')).toBe('(11) 98765-4321');
    });

    it('should handle empty string', () => {
      expect(maskPhoneBR('')).toBe('');
    });
  });

  describe('parseLocaleNumber', () => {
    it('should parse number with default locale', () => {
      expect(parseLocaleNumber('1000.5')).toBe(1000.5);
      expect(parseLocaleNumber('1234.56')).toBe(1234.56);
    });

    it('should parse number with pt-BR locale', () => {
      expect(parseLocaleNumber('1.000,5', 'pt-BR')).toBe(1000.5);
      expect(parseLocaleNumber('1.234,56', 'pt-BR')).toBe(1234.56);
    });

    it('should parse number with en-US locale', () => {
      expect(parseLocaleNumber('1,000.5', 'en-US')).toBe(1000.5);
      expect(parseLocaleNumber('1,234.56', 'en-US')).toBe(1234.56);
    });

    it('should return null for invalid input', () => {
      expect(parseLocaleNumber('abc')).toBe(null);
      expect(parseLocaleNumber('')).toBe(null);
      expect(parseLocaleNumber('not a number')).toBe(null);
    });

    it('should handle edge cases', () => {
      expect(parseLocaleNumber('0')).toBe(0);
      expect(parseLocaleNumber('0.0')).toBe(0);
      expect(parseLocaleNumber('-100')).toBe(-100);
    });
  });

  describe('formatCurrency', () => {
    it('should format currency with default BRL', () => {
      expect(formatCurrency(1000.5)).toContain('1.000');
      expect(formatCurrency(1000.5)).toContain('50');
    });

    it('should format currency with pt-BR locale', () => {
      const formatted = formatCurrency(1000.5, 'BRL', 'pt-BR');
      expect(formatted).toContain('1.000');
      expect(formatted).toContain('50');
    });

    it('should format currency with en-US locale', () => {
      const formatted = formatCurrency(1000.5, 'USD', 'en-US');
      expect(formatted).toContain('1,000');
      expect(formatted).toContain('50');
    });

    it('should handle zero', () => {
      const formatted = formatCurrency(0);
      expect(formatted).toBeDefined();
    });

    it('should handle null and undefined', () => {
      expect(formatCurrency(null)).toContain('0');
      expect(formatCurrency(undefined)).toContain('0');
    });

    it('should always show 2 decimal places', () => {
      const formatted1 = formatCurrency(100);
      expect(formatted1).toContain('00');
      
      const formatted2 = formatCurrency(100.1);
      expect(formatted2).toContain('10');
    });
  });
});
