import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  isValidCPF,
  isValidCNPJ,
  isValidCEP,
  zCPF,
  zCNPJ,
  zCEP,
  zCPFOptional,
  zCNPJOptional,
  zCEPOptional,
} from '../validators';

describe('Brazilian validators', () => {
  describe('isValidCPF', () => {
    it('should return false for empty string', () => {
      expect(isValidCPF('')).toBe(false);
    });

    it('should return false for CPF with wrong length', () => {
      expect(isValidCPF('1234567890')).toBe(false); // 10 digits
      expect(isValidCPF('123456789012')).toBe(false); // 12 digits
    });

    it('should return false for CPF with all same digits', () => {
      expect(isValidCPF('11111111111')).toBe(false);
      expect(isValidCPF('00000000000')).toBe(false);
      expect(isValidCPF('22222222222')).toBe(false);
    });

    it('should return true for valid CPF', () => {
      // Valid CPF: 11144477735
      expect(isValidCPF('11144477735')).toBe(true);
    });

    it('should return true for valid CPF with formatting', () => {
      expect(isValidCPF('111.444.777-35')).toBe(true);
    });

    it('should return false for invalid CPF', () => {
      expect(isValidCPF('12345678901')).toBe(false);
    });

    it('should return false for CPF with invalid check digits', () => {
      expect(isValidCPF('11144477734')).toBe(false); // Wrong last digit
    });

    it('should handle CPF with non-digit characters', () => {
      expect(isValidCPF('111.444.777-35')).toBe(true);
      expect(isValidCPF('11144477735abc')).toBe(true); // digitsOnly extracts digits
    });

    it('should validate multiple known valid CPFs', () => {
      // These are known valid CPF numbers
      expect(isValidCPF('11144477735')).toBe(true);
      expect(isValidCPF('12345678909')).toBe(true); // Valid CPF
      expect(isValidCPF('12345678901')).toBe(false); // Invalid check digit
    });
  });

  describe('isValidCNPJ', () => {
    it('should return false for empty string', () => {
      expect(isValidCNPJ('')).toBe(false);
    });

    it('should return false for CNPJ with wrong length', () => {
      expect(isValidCNPJ('1234567800019')).toBe(false); // 13 digits
      expect(isValidCNPJ('123456780001901')).toBe(false); // 15 digits
    });

    it('should return false for CNPJ with all same digits', () => {
      expect(isValidCNPJ('11111111111111')).toBe(false);
      expect(isValidCNPJ('00000000000000')).toBe(false);
    });

    it('should return true for valid CNPJ', () => {
      // Valid CNPJ: 11222333000181
      expect(isValidCNPJ('11222333000181')).toBe(true);
    });

    it('should return true for valid CNPJ with formatting', () => {
      expect(isValidCNPJ('11.222.333/0001-81')).toBe(true);
    });

    it('should return false for invalid CNPJ', () => {
      expect(isValidCNPJ('12345678000190')).toBe(false);
    });

    it('should return false for CNPJ with invalid check digits', () => {
      expect(isValidCNPJ('11222333000180')).toBe(false); // Wrong last digit
    });

    it('should handle CNPJ with non-digit characters', () => {
      expect(isValidCNPJ('11.222.333/0001-81')).toBe(true);
      expect(isValidCNPJ('11222333000181abc')).toBe(true); // digitsOnly extracts digits
    });

    it('should validate multiple known valid CNPJs', () => {
      // These are known valid CNPJ numbers
      expect(isValidCNPJ('11222333000181')).toBe(true);
    });
  });

  describe('isValidCEP', () => {
    it('should return false for empty string', () => {
      expect(isValidCEP('')).toBe(false);
    });

    it('should return false for CEP with wrong length', () => {
      expect(isValidCEP('1234567')).toBe(false); // 7 digits
      expect(isValidCEP('123456789')).toBe(false); // 9 digits
    });

    it('should return true for valid CEP with 8 digits', () => {
      expect(isValidCEP('12345678')).toBe(true);
      expect(isValidCEP('01310100')).toBe(true);
    });

    it('should return true for valid CEP with formatting', () => {
      expect(isValidCEP('12345-678')).toBe(true);
    });

    it('should return false for CEP with non-digits', () => {
      expect(isValidCEP('12345-67a')).toBe(false);
    });

    it('should handle CEP with formatting characters', () => {
      expect(isValidCEP('12345-678')).toBe(true);
      expect(isValidCEP('12345.678')).toBe(true);
    });

    it('should validate various CEP formats', () => {
      expect(isValidCEP('01310100')).toBe(true);
      expect(isValidCEP('01310-100')).toBe(true);
      expect(isValidCEP('01310.100')).toBe(true);
    });
  });

  describe('zCPF', () => {
    it('should validate valid CPF', async () => {
      const result = await zCPF.safeParseAsync('111.444.777-35');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('11144477735');
      }
    });

    it('should reject invalid CPF', async () => {
      const result = await zCPF.safeParseAsync('12345678901');
      expect(result.success).toBe(false);
    });

    it('should reject CPF with wrong length', async () => {
      const result = await zCPF.safeParseAsync('1234567890');
      expect(result.success).toBe(false);
    });

    it('should preprocess and extract digits', async () => {
      const result = await zCPF.safeParseAsync('111.444.777-35');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('11144477735');
      }
    });

    it('should handle null input', async () => {
      const result = await zCPF.safeParseAsync(null);
      expect(result.success).toBe(false);
    });
  });

  describe('zCNPJ', () => {
    it('should validate valid CNPJ', async () => {
      const result = await zCNPJ.safeParseAsync('11.222.333/0001-81');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('11222333000181');
      }
    });

    it('should reject invalid CNPJ', async () => {
      const result = await zCNPJ.safeParseAsync('12345678000190');
      expect(result.success).toBe(false);
    });

    it('should reject CNPJ with wrong length', async () => {
      const result = await zCNPJ.safeParseAsync('1234567800019');
      expect(result.success).toBe(false);
    });

    it('should preprocess and extract digits', async () => {
      const result = await zCNPJ.safeParseAsync('11.222.333/0001-81');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('11222333000181');
      }
    });
  });

  describe('zCEP', () => {
    it('should validate valid CEP', async () => {
      const result = await zCEP.safeParseAsync('12345-678');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('12345678');
      }
    });

    it('should reject CEP with wrong length', async () => {
      const result = await zCEP.safeParseAsync('1234567');
      expect(result.success).toBe(false);
    });

    it('should preprocess and extract digits', async () => {
      const result = await zCEP.safeParseAsync('12345-678');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('12345678');
      }
    });
  });

  describe('zCPFOptional', () => {
    it('should accept empty string', async () => {
      const result = await zCPFOptional.safeParseAsync('');
      expect(result.success).toBe(true);
    });

    it('should accept valid CPF', async () => {
      const result = await zCPFOptional.safeParseAsync('111.444.777-35');
      expect(result.success).toBe(true);
    });

    it('should reject invalid CPF', async () => {
      const result = await zCPFOptional.safeParseAsync('12345678901');
      expect(result.success).toBe(false);
    });
  });

  describe('zCNPJOptional', () => {
    it('should accept empty string', async () => {
      const result = await zCNPJOptional.safeParseAsync('');
      expect(result.success).toBe(true);
    });

    it('should accept valid CNPJ', async () => {
      const result = await zCNPJOptional.safeParseAsync('11.222.333/0001-81');
      expect(result.success).toBe(true);
    });

    it('should reject invalid CNPJ', async () => {
      const result = await zCNPJOptional.safeParseAsync('12345678000190');
      expect(result.success).toBe(false);
    });
  });

  describe('zCEPOptional', () => {
    it('should accept empty string', async () => {
      const result = await zCEPOptional.safeParseAsync('');
      expect(result.success).toBe(true);
    });

    it('should accept valid CEP', async () => {
      const result = await zCEPOptional.safeParseAsync('12345-678');
      expect(result.success).toBe(true);
    });

    it('should reject invalid CEP', async () => {
      const result = await zCEPOptional.safeParseAsync('1234567');
      expect(result.success).toBe(false);
    });
  });
});

